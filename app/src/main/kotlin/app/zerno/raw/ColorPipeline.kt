package app.zerno.raw

/** Источник illuminant — коды EXIF/DNG, как в CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_*. */
object Illuminant {
  const val DAYLIGHT = 1
  const val STANDARD_A = 17
  const val D55 = 20
  const val D65 = 21
  const val D75 = 22
  const val D50 = 23

  fun name(code: Int): String = when (code) {
    DAYLIGHT -> "Daylight"
    STANDARD_A -> "A (лампа накаливания)"
    D55 -> "D55"
    D65 -> "D65"
    D75 -> "D75"
    D50 -> "D50"
    else -> "код $code"
  }

  /** Цветовая температура источника, К (значения как в DNG SDK); null — неизвестный код. */
  fun temperature(code: Int): Double? = when (code) {
    DAYLIGHT, 4, 9 -> 5500.0 // дневной свет, вспышка, ясно
    2, 14 -> 4150.0 // флуоресцентная, холодная белая
    3, STANDARD_A -> 2856.0 // лампа накаливания
    10 -> 6500.0 // облачно
    11 -> 7500.0 // тень
    12 -> 6430.0
    13 -> 5000.0
    15 -> 3450.0
    16 -> 2940.0
    18 -> 4871.0 // B
    19 -> 6774.0 // C
    D55 -> 5503.0
    D65 -> 6504.0
    D75 -> 7504.0
    D50 -> 5003.0
    24 -> 3200.0 // студийная лампа
    else -> null
  }
}

/** Калибровка сенсора для одного источника света (аналог ColorMatrix/ForwardMatrix в DNG). */
class SensorCalibration(
  val illuminant: Int,
  /** XYZ → цвет сенсора (Camera2 SENSOR_COLOR_TRANSFORM, DNG ColorMatrix). */
  val colorMatrix: Mat3,
  /** Поправка конкретного экземпляра сенсора (SENSOR_CALIBRATION_TRANSFORM), если есть. */
  val calibration: Mat3?,
  /** Цвет сенсора после баланса белого → XYZ D50 (SENSOR_FORWARD_MATRIX), если есть. */
  val forwardMatrix: Mat3?,
)

/** Цветовые данные кадра, собранные из CameraCharacteristics и CaptureResult. */
class ColorMetadata(
  /** Цвет нейтрального серого в пространстве сенсора (SENSOR_NEUTRAL_COLOR_POINT). */
  val neutral: DoubleArray?,
  /** Усиления баланса белого от ISP (COLOR_CORRECTION_GAINS), R G B. */
  val ispGains: DoubleArray?,
  /** Матрица ISP: сенсор после усилений → линейный sRGB (COLOR_CORRECTION_TRANSFORM). */
  val ispMatrix: Mat3?,
  val calibrations: List<SensorCalibration>,
)

enum class ColorStrategy(val title: String) {
  AUTO("Авто"),
  SENSOR_MATRIX("Матрица сенсора"),
  ISP_MATRIX("Матрица ISP"),
  GENERIC("Типовая матрица"),
  NO_MATRIX("Без матрицы"),
}

/** Итог: чем умножать каналы и какой матрицей переводить в линейный sRGB. */
class ColorPipeline(
  /** Множители R, G, B; минимальный равен 1, чтобы пересвет оставался белым. */
  val wbGains: DoubleArray,
  val srgbFromCamera: Mat3,
  val used: ColorStrategy,
  val notes: List<String>,
  /** Оценка цветовой температуры сцены по нейтрали, К (если её удалось посчитать). */
  val sceneTemperature: Double? = null,
)

object ColorPipelineResolver {

  fun resolve(meta: ColorMetadata, requested: ColorStrategy): ColorPipeline {
    val notes = mutableListOf<String>()
    val (sensorMatrix, temperature) = sensorMatrix(meta, notes)
    val ispMatrix = meta.ispMatrix?.takeUnless { it.isIdentity() }
    if (meta.ispMatrix != null && ispMatrix == null) {
      notes += "Матрица ISP единичная — устройство её не передало (известно для Oppo)"
    }

    val strategy = when (requested) {
      ColorStrategy.AUTO -> when {
        sensorMatrix != null -> ColorStrategy.SENSOR_MATRIX
        ispMatrix != null -> ColorStrategy.ISP_MATRIX
        else -> ColorStrategy.GENERIC
      }
      ColorStrategy.SENSOR_MATRIX -> if (sensorMatrix != null) requested else fallback(requested, notes)
      ColorStrategy.ISP_MATRIX -> if (ispMatrix != null) requested else fallback(requested, notes)
      ColorStrategy.GENERIC, ColorStrategy.NO_MATRIX -> requested
    }

    val neutralGains = meta.neutral?.let { n ->
      if (n.all { it > 1e-6 }) doubleArrayOf(1 / n[0], 1 / n[1], 1 / n[2]) else null
    }
    val rawGains = when (strategy) {
      ColorStrategy.ISP_MATRIX -> meta.ispGains ?: neutralGains
      else -> neutralGains ?: meta.ispGains
    } ?: doubleArrayOf(1.0, 1.0, 1.0).also { notes += "Нет данных баланса белого — без коррекции" }

    val matrix = when (strategy) {
      ColorStrategy.SENSOR_MATRIX -> sensorMatrix!!
      ColorStrategy.ISP_MATRIX -> ispMatrix!!
      ColorStrategy.GENERIC -> GENERIC_PHONE
      else -> Mat3.IDENTITY
    }
    return ColorPipeline(normalizeGains(rawGains), matrix, strategy, notes, temperature)
  }

  /**
   * Матрица «сенсор после баланса белого → линейный sRGB» из ColorMatrix, как в dcraw:
   * cam_rgb = cam_xyz · xyz_rgb, нормируем строки, обращаем. ForwardMatrix сознательно не
   * используем: у Oppo/realme в ней бывает заглушка вместо калибровки.
   *
   * Если калибровок две (обычно D65 и лампа накаливания), смешиваем их по цветовой температуре
   * сцены, как требует спецификация DNG: при тёплом свете матрица D65 заметно искажает красные.
   */
  private fun sensorMatrix(meta: ColorMetadata, notes: MutableList<String>): Pair<Mat3?, Double?> {
    val usable = meta.calibrations.filter { !it.colorMatrix.isIdentity() && it.colorMatrix.determinant() != 0.0 }
    if (usable.isEmpty()) return null to null
    if (usable.any { looksLikePlaceholderColorMatrix(it.camFromXyz()) }) {
      // Так пишут OnePlus/Xiaomi/Oppo: ColorMatrix = XYZ→sRGB, т. е. «сенсор видит как sRGB».
      // Это не калибровка, а заглушка: цвета с ней блёклые. Если заглушка хоть в одной из пар —
      // вторая тоже не заслуживает доверия (у логической камеры Oppo вторая вообще бессмысленная).
      notes += "Калибровка сенсора — заглушка (сенсор = sRGB), не используем"
      return null to null
    }
    val blend = blendForNeutral(usable, meta.neutral)
    val camFromXyz = if (blend != null) {
      notes += "Калибровка сенсора: ${"%.0f".format(blend.temperature)} K, " +
        "${Illuminant.name(blend.first.illuminant)} ${"%.0f".format(blend.weight * 100)} % / " +
        "${Illuminant.name(blend.second.illuminant)} ${"%.0f".format((1 - blend.weight) * 100)} %"
      blend.matrix
    } else {
      val cal = pickCalibration(usable) ?: return null to null
      notes += "Калибровка сенсора: ${Illuminant.name(cal.illuminant)}"
      cal.camFromXyz()
    }
    val result = (camFromXyz * Mat3.XYZ_FROM_SRGB).normalizeRows()?.inverse()
    if (result == null) notes += "Матрица сенсора вырождена — пропускаем"
    return result to blend?.temperature
  }

  /** ColorMatrix с поправкой экземпляра сенсора: XYZ → цвет сенсора. */
  private fun SensorCalibration.camFromXyz(): Mat3 = calibration?.let { it * colorMatrix } ?: colorMatrix

  class Blend(
    val first: SensorCalibration,
    val second: SensorCalibration,
    /** Доля первой калибровки, 0..1. */
    val weight: Double,
    val temperature: Double,
    val matrix: Mat3,
  )

  /**
   * Алгоритм DNG: по нейтрали сенсора итеративно находим цветовую температуру сцены
   * (нейтраль → XYZ через смешанную матрицу → xy → температура по МакКами) и смешиваем
   * две ColorMatrix линейно по обратной температуре (майредам). null — смешивать нечего.
   */
  fun blendForNeutral(calibrations: List<SensorCalibration>, neutral: DoubleArray?): Blend? {
    if (neutral == null || neutral.any { it <= 1e-6 }) return null
    val known = calibrations.mapNotNull { c -> Illuminant.temperature(c.illuminant)?.let { c to it } }
      .sortedByDescending { it.second }
    if (known.size < 2) return null
    val (first, t1) = known.first()
    val (second, t2) = known.last()
    if (t1 - t2 < 500) return null
    val m1 = first.camFromXyz()
    val m2 = second.camFromXyz()

    fun weightFor(t: Double) = ((1 / t - 1 / t2) / (1 / t1 - 1 / t2)).coerceIn(0.0, 1.0)
    fun mix(w: Double) = Mat3(DoubleArray(9) { w * m1.m[it] + (1 - w) * m2.m[it] })

    var weight = 0.5
    var temperature = 5000.0
    repeat(20) {
      val xyz = mix(weight).inverse()?.apply(neutral) ?: return null
      val sum = xyz.sum()
      if (sum <= 1e-9) return null
      temperature = mccamy(xyz[0] / sum, xyz[1] / sum).coerceIn(2000.0, 12000.0)
      val next = weightFor(temperature)
      if (kotlin.math.abs(next - weight) < 1e-4) {
        weight = next
        return Blend(first, second, weight, temperature, mix(weight))
      }
      weight = next
    }
    return Blend(first, second, weight, temperature, mix(weight))
  }

  /** Коррелированная цветовая температура по координатам xy (формула МакКами, 2000–12500 K). */
  fun mccamy(x: Double, y: Double): Double {
    val n = (x - 0.3320) / (0.1858 - y)
    return 449 * n * n * n + 3525 * n * n + 6823.3 * n + 5520.33
  }

  /** Предпочитаем дневной свет (D65 и близкие), иначе — вторую матрицу. */
  fun pickCalibration(list: List<SensorCalibration>): SensorCalibration? {
    val usable = list.filter { !it.colorMatrix.isIdentity() && it.colorMatrix.determinant() != 0.0 }
    val daylight = listOf(Illuminant.D65, Illuminant.D55, Illuminant.D50, Illuminant.D75, Illuminant.DAYLIGHT)
    return daylight.firstNotNullOfOrNull { code -> usable.firstOrNull { it.illuminant == code } }
      ?: usable.lastOrNull()
  }

  /** ColorMatrix, который на деле означает «сенсор = sRGB» (заглушка OnePlus/Xiaomi/Oppo). */
  fun looksLikePlaceholderColorMatrix(colorMatrix: Mat3): Boolean =
    (colorMatrix * Mat3.XYZ_FROM_SRGB).normalizeRows()?.isIdentity(0.03) == true

  /** Совпадает ли ForwardMatrix с типичной заглушкой sRGB→XYZ(D50). */
  fun looksLikePlaceholderForwardMatrix(m: Mat3): Boolean = m.maxAbsDiff(Mat3.XYZ_D50_FROM_SRGB) < 0.01

  private fun fallback(requested: ColorStrategy, notes: MutableList<String>): ColorStrategy {
    notes += "«${requested.title}» недоступна для этого кадра — типовая матрица"
    return ColorStrategy.GENERIC
  }

  /**
   * Типовая матрица «сенсор смартфона → sRGB» при дневном свете — когда устройство не дало
   * настоящей калибровки. Наша оценка порядка величин для CMOS-сенсоров телефонов; строки
   * в сумме дают 1, поэтому белый остаётся белым. Уточнять по снимкам цветовой мишени.
   */
  val GENERIC_PHONE = Mat3(doubleArrayOf(
    1.70, -0.55, -0.15,
    -0.28, 1.52, -0.24,
    0.02, -0.62, 1.60,
  ))

  private fun normalizeGains(g: DoubleArray): DoubleArray {
    val min = g.min()
    return if (min > 1e-9) DoubleArray(3) { g[it] / min } else doubleArrayOf(1.0, 1.0, 1.0)
  }
}
