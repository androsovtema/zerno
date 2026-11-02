package app.zerno.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.params.ColorSpaceTransform
import android.media.Image
import app.zerno.raw.Cfa
import app.zerno.raw.ColorMetadata
import app.zerno.raw.Mat3
import app.zerno.raw.RawImage
import app.zerno.raw.SensorCalibration
import app.zerno.raw.ShadingMap
import java.nio.ByteOrder

/** Перевод метаданных Camera2 в чистые типы пакета raw (их можно тестировать без Android). */
object Camera2Mapping {

  fun ColorSpaceTransform.toMat3(): Mat3 =
    Mat3(DoubleArray(9) { i -> getElement(i % 3, i / 3).toDouble() })

  fun colorMetadata(chars: CameraCharacteristics, result: CaptureResult, latchedIsp: Mat3?): ColorMetadata {
    val calibrations = buildList {
      val cm1 = chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)
      val ill1 = chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)
      if (cm1 != null && ill1 != null) {
        add(SensorCalibration(
          ill1, cm1.toMat3(),
          chars.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)?.toMat3(),
          chars.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)?.toMat3(),
        ))
      }
      val cm2 = chars.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)
      val ill2 = chars.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)
      if (cm2 != null && ill2 != null) {
        add(SensorCalibration(
          ill2.toInt(), cm2.toMat3(),
          chars.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)?.toMat3(),
          chars.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)?.toMat3(),
        ))
      }
    }
    val neutral = result.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)?.map { it.toDouble() }?.toDoubleArray()
    val gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS)?.let {
      doubleArrayOf(it.red.toDouble(), (it.greenEven + it.greenOdd) / 2.0, it.blue.toDouble())
    }
    val frameIsp = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.toMat3()
    // Oppo отдаёт настоящую матрицу ISP только в первых кадрах, дальше — единичную.
    val isp = frameIsp?.takeUnless { it.isIdentity() } ?: latchedIsp ?: frameIsp
    return ColorMetadata(neutral, gains, isp, calibrations)
  }

  fun shadingMap(result: CaptureResult): ShadingMap? {
    val map = result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP) ?: return null
    if (map.rowCount < 2 || map.columnCount < 2) return null
    val gains = FloatArray(map.rowCount * map.columnCount * 4)
    for (row in 0 until map.rowCount) for (col in 0 until map.columnCount) for (ch in 0..3) {
      gains[(row * map.columnCount + col) * 4 + ch] = map.getGainFactor(ch, col, row)
    }
    return ShadingMap(map.rowCount, map.columnCount, gains)
  }

  /** Копирует RAW_SENSOR из Image в RawImage. Бросает исключение, если формат не поддержан. */
  fun rawImage(image: Image, chars: CameraCharacteristics, result: CaptureResult): RawImage {
    val cfa = chars.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT)
      ?.let { Cfa.fromCamera2(it) }
      ?: error("Сенсор не Байер (RGB/моно/NIR) — прототип такое не поддерживает")
    val plane = image.planes[0]
    check(plane.pixelStride == 2) { "Неожиданный pixelStride ${plane.pixelStride}" }
    val w = image.width
    val h = image.height
    val data = ShortArray(w * h)
    val buffer = plane.buffer.duplicate().order(ByteOrder.nativeOrder())
    for (y in 0 until h) {
      buffer.position(y * plane.rowStride)
      buffer.asShortBuffer().get(data, y * w, w)
    }
    val black = result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL)?.takeIf { it.size == 4 }
      ?: chars.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)?.let { p ->
        FloatArray(4) { i -> p.getOffsetForIndex(i % 2, i / 2).toFloat() }
      }
      ?: FloatArray(4)
    val white = (result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL)
      ?: chars.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)
      ?: 1023).toFloat()
    return RawImage(w, h, data, cfa, black, white)
  }
}
