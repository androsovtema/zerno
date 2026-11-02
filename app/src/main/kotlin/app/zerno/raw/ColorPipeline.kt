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
  NO_MATRIX("Без матрицы"),
}

/** Итог: чем умножать каналы и какой матрицей переводить в линейный sRGB. */
class ColorPipeline(
  /** Множители R, G, B; минимальный равен 1, чтобы пересвет оставался белым. */
  val wbGains: DoubleArray,
  val srgbFromCamera: Mat3,
  val used: ColorStrategy,
  val notes: List<String>,
)

object ColorPipelineResolver {

  fun resolve(meta: ColorMetadata, requested: ColorStrategy): ColorPipeline {
    val notes = mutableListOf<String>()
    val sensorMatrix = sensorMatrix(meta, notes)
    val ispMatrix = meta.ispMatrix?.takeUnless { it.isIdentity() }
    if (meta.ispMatrix != null && ispMatrix == null) {
      notes += "Матрица ISP единичная — устройство её не передало (известно для Oppo)"
    }

    val strategy = when (requested) {
      ColorStrategy.AUTO -> when {
        sensorMatrix != null -> ColorStrategy.SENSOR_MATRIX
        ispMatrix != null -> ColorStrategy.ISP_MATRIX
        else -> ColorStrategy.NO_MATRIX
      }
      ColorStrategy.SENSOR_MATRIX ->
        if (sensorMatrix != null) requested else fallback(requested, ispMatrix, ColorStrategy.ISP_MATRIX, notes)
      ColorStrategy.ISP_MATRIX ->
        if (ispMatrix != null) requested else fallback(requested, sensorMatrix, ColorStrategy.SENSOR_MATRIX, notes)
      ColorStrategy.NO_MATRIX -> requested
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
      else -> Mat3.IDENTITY
    }
    return ColorPipeline(normalizeGains(rawGains), matrix, strategy, notes)
  }

  /**
   * Матрица «сенсор после баланса белого → линейный sRGB» из ColorMatrix, как в dcraw:
   * cam_rgb = cam_xyz · xyz_rgb, нормируем строки, обращаем. ForwardMatrix сознательно не
   * используем: у Oppo/realme в ней бывает заглушка вместо калибровки.
   */
  private fun sensorMatrix(meta: ColorMetadata, notes: MutableList<String>): Mat3? {
    val cal = pickCalibration(meta.calibrations) ?: return null
    notes += "Калибровка сенсора: ${Illuminant.name(cal.illuminant)}"
    val camFromXyz = cal.calibration?.let { it * cal.colorMatrix } ?: cal.colorMatrix
    val camFromSrgb = (camFromXyz * Mat3.XYZ_FROM_SRGB).normalizeRows()
    val result = camFromSrgb?.inverse()
    if (result == null) notes += "Матрица сенсора вырождена — пропускаем"
    return result
  }

  /** Предпочитаем дневной свет (D65 и близкие), иначе — вторую матрицу. */
  fun pickCalibration(list: List<SensorCalibration>): SensorCalibration? {
    val usable = list.filter { !it.colorMatrix.isIdentity() && it.colorMatrix.determinant() != 0.0 }
    val daylight = listOf(Illuminant.D65, Illuminant.D55, Illuminant.D50, Illuminant.D75, Illuminant.DAYLIGHT)
    return daylight.firstNotNullOfOrNull { code -> usable.firstOrNull { it.illuminant == code } }
      ?: usable.lastOrNull()
  }

  /** Совпадает ли ForwardMatrix с типичной заглушкой sRGB→XYZ(D50). */
  fun looksLikePlaceholderForwardMatrix(m: Mat3): Boolean = m.maxAbsDiff(Mat3.XYZ_D50_FROM_SRGB) < 0.01

  private fun fallback(
    requested: ColorStrategy,
    alternative: Mat3?,
    alternativeStrategy: ColorStrategy,
    notes: MutableList<String>,
  ): ColorStrategy {
    notes += "«${requested.title}» недоступна для этого кадра"
    return if (alternative != null) alternativeStrategy else ColorStrategy.NO_MATRIX
  }

  private fun normalizeGains(g: DoubleArray): DoubleArray {
    val min = g.min()
    return if (min > 1e-9) DoubleArray(3) { g[it] / min } else doubleArrayOf(1.0, 1.0, 1.0)
  }
}
