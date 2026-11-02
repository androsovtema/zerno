package app.zerno.camera

import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Size
import app.zerno.camera.Camera2Mapping.toMat3
import app.zerno.raw.Cfa
import app.zerno.raw.ColorPipelineResolver
import app.zerno.raw.Illuminant

/** Камера, которую можно выбрать для съёмки RAW: логическая или физическая внутри логической. */
data class CameraOption(
  val logicalId: String,
  val physicalId: String?,
  /** Техническое имя для отчётов: id, направление, фокус. */
  val label: String,
  val rawSize: Size,
  val front: Boolean,
  /** Эквивалентное фокусное расстояние, мм (0 — неизвестно). */
  val focalEq: Int,
  /** Подпись для пользователя: «1×», «3×», «Фронт». */
  val zoomLabel: String = "",
) {
  val key: String get() = physicalId?.let { "$logicalId/$it" } ?: logicalId
}

class CameraScan(val options: List<CameraOption>, val report: String)

/**
 * Собирает всё, что устройство отдаёт сторонним приложениям: уровни Camera2, RAW, физические
 * модули и цветовые матрицы. Отчёт нужен, чтобы понять поведение конкретного телефона.
 */
class CameraScanner(private val manager: CameraManager) {

  fun scan(): CameraScan {
    val options = mutableListOf<CameraOption>()
    val sb = StringBuilder()
    sb.appendLine("Устройство: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
    sb.appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), сборка ${Build.DISPLAY}")
    val ids = manager.cameraIdList
    sb.appendLine("Камер в cameraIdList: ${ids.size}")

    for (id in ids) {
      val chars = manager.getCameraCharacteristics(id)
      sb.appendLine()
      sb.appendLine("=== Камера $id")
      describe(chars, sb, indent = "")
      rawSize(chars)?.let { options += option(id, null, chars, it) }

      val physical = chars.physicalCameraIds
      if (physical.isNotEmpty()) {
        sb.appendLine("Физические модули: ${physical.joinToString()}")
        for (pid in physical) {
          val pc = runCatching { manager.getCameraCharacteristics(pid) }.getOrNull()
          sb.appendLine("  --- Физическая $pid")
          if (pc == null) {
            sb.appendLine("  характеристики недоступны")
            continue
          }
          describe(pc, sb, indent = "  ")
          if (pid !in ids) {
            rawSize(pc)?.let { options += option(id, pid, pc, it) }
          }
        }
      }
    }
    val final = withZoomLabels(dedupe(options))
    sb.appendLine()
    sb.appendLine("Доступно для RAW-съёмки: ${final.joinToString { "${it.zoomLabel} (${it.label}, ${it.rawSize})" }.ifEmpty { "ничего" }}")
    return CameraScan(final, sb.toString())
  }

  private fun option(logical: String, physical: String?, chars: CameraCharacteristics, size: Size): CameraOption {
    val front = chars.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT
    val id = physical?.let { "$logical/$it" } ?: logical
    return CameraOption(logical, physical, "$id · ${lensLabel(chars)}", size, front, focalEq(chars))
  }

  /** Один и тот же модуль бывает виден и как отдельная камера, и как физическая — оставляем один. */
  private fun dedupe(list: List<CameraOption>): List<CameraOption> {
    val result = mutableListOf<CameraOption>()
    for (o in list.sortedBy { if (it.physicalId == null) 0 else 1 }) {
      val dup = result.any {
        it.front == o.front && it.rawSize == o.rawSize && kotlin.math.abs(it.focalEq - o.focalEq) <= 1
      }
      if (!dup) result += o
    }
    return result
  }

  private fun withZoomLabels(list: List<CameraOption>): List<CameraOption> {
    val back = list.filter { !it.front }
    // Основная камера — с фокусом, ближайшим к 24 мм (так считают и производители).
    val main = back.filter { it.focalEq > 0 }.minByOrNull { kotlin.math.abs(it.focalEq - 24) }
    val labeled = list.map { o ->
      val label = when {
        o.front -> "Фронт"
        main == null || o.focalEq <= 0 -> o.logicalId
        else -> {
          val z = o.focalEq.toDouble() / main.focalEq
          if (z >= 1.5 && kotlin.math.abs(z - Math.round(z)) < 0.15) "${Math.round(z)}×"
          else if (kotlin.math.abs(z - 1) < 0.08) "1×"
          else "%.1f×".format(z).replace(',', '.')
        }
      }
      o.copy(zoomLabel = label)
    }
    return labeled.sortedWith(compareBy({ it.front }, { it.focalEq }))
  }

  private fun focalEq(chars: CameraCharacteristics): Int {
    val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: return 0
    val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 0
    val diag = kotlin.math.sqrt(sensor.width * sensor.width + sensor.height * sensor.height)
    return if (diag > 0) (focal * 43.27f / diag).toInt() else 0
  }

  private fun rawSize(chars: CameraCharacteristics): Size? {
    val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: return null
    if (CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW !in caps) return null
    if (chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)?.let { Cfa.fromCamera2(it) } == null) return null
    val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
    return map.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height }
  }

  private fun lensLabel(chars: CameraCharacteristics): String {
    val facing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
      CameraMetadata.LENS_FACING_BACK -> "задняя"
      CameraMetadata.LENS_FACING_FRONT -> "фронтальная"
      else -> "внешняя"
    }
    val focal = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()
    val sensor = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    // Эквивалент 35 мм по диагонали сенсора.
    val eq = if (focal != null && sensor != null) {
      val diag = kotlin.math.sqrt(sensor.width * sensor.width + sensor.height * sensor.height)
      if (diag > 0) " ≈${(focal * 43.27f / diag).toInt()} мм" else ""
    } else ""
    return "$facing$eq"
  }

  private fun describe(c: CameraCharacteristics, sb: StringBuilder, indent: String) {
    fun line(s: String) = sb.appendLine(indent + s)
    line("Объектив: ${lensLabel(c)}, фокус ${c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString()} мм")
    line("Hardware level: ${hardwareLevel(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))}")
    val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
    line("Возможности: ${caps.joinToString { capability(it) }}")
    val raw = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
      ?.getOutputSizes(ImageFormat.RAW_SENSOR)?.joinToString { "${it.width}×${it.height}" }
    line("RAW_SENSOR размеры: ${raw ?: "нет"}")
    line("Сенсор: ${c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)}, " +
      "${c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)} мм, " +
      "CFA ${c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)?.let { Cfa.fromCamera2(it)?.name ?: "код $it" }}")
    line("Белый уровень: ${c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)}, " +
      "чёрный: ${c.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)}")
    line("ISO: ${c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)}, " +
      "выдержка: ${c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { "${it.lower} … ${it.upper} нс" }}")
    line("Режимы шумодава: ${c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.joinToString()}, " +
      "резкости: ${c.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)?.joinToString()}")
    val ill1 = c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)
    val ill2 = c.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.toInt()
    line("Источник 1: ${ill1?.let { Illuminant.name(it) }}, источник 2: ${ill2?.let { Illuminant.name(it) }}")
    listOf(
      "ColorMatrix1" to CameraCharacteristics.SENSOR_COLOR_TRANSFORM1,
      "ColorMatrix2" to CameraCharacteristics.SENSOR_COLOR_TRANSFORM2,
      "Calibration1" to CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1,
      "Calibration2" to CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2,
      "ForwardMatrix1" to CameraCharacteristics.SENSOR_FORWARD_MATRIX1,
      "ForwardMatrix2" to CameraCharacteristics.SENSOR_FORWARD_MATRIX2,
    ).forEach { (name, key) ->
      val m = c.get(key)?.toMat3()
      val flag = when {
        m == null -> ""
        m.isIdentity() -> "  ⚠ единичная"
        name.startsWith("Forward") && ColorPipelineResolver.looksLikePlaceholderForwardMatrix(m) -> "  ⚠ похоже на заглушку sRGB"
        name.startsWith("ColorMatrix") && ColorPipelineResolver.looksLikePlaceholderColorMatrix(m) -> "  ⚠ заглушка: сенсор = sRGB"
        else -> ""
      }
      line("$name: ${m ?: "нет"}$flag")
    }
  }

  private fun hardwareLevel(v: Int?) = when (v) {
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
    CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
    else -> "неизвестно ($v)"
  }

  private fun capability(v: Int) = when (v) {
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE -> "BASIC"
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR -> "MANUAL_SENSOR"
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_POST_PROCESSING -> "MANUAL_POST"
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW -> "RAW"
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS -> "READ_SENSOR"
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE -> "BURST"
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA -> "LOGICAL_MULTI"
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR -> "ULTRA_HIGH_RES"
    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT -> "10BIT"
    else -> "#$v"
  }
}
