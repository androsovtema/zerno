package app.zerno.raw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawDeveloperTest {

  private val black = 64f
  private val white = 1023f

  /** Однородный кадр: в каждой ячейке значение по её цвету. */
  private fun uniform(cfa: Cfa, r: Float, g: Float, b: Float, w: Int = 8, h: Int = 8): RawImage {
    val data = ShortArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      val linear = when (cfa.colorAt((y and 1) * 2 + (x and 1))) {
        CfaColor.RED -> r
        CfaColor.GREEN -> g
        CfaColor.BLUE -> b
      }
      data[y * w + x] = (black + linear * (white - black)).toInt().toShort()
    }
    return RawImage(w, h, data, cfa, FloatArray(4) { black }, white)
  }

  private fun channels(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

  private fun meta(neutral: DoubleArray?, isp: Mat3? = null, cal: List<SensorCalibration> = emptyList()) =
    ColorMetadata(neutral = neutral, ispGains = null, ispMatrix = isp, calibrations = cal)

  @Test
  fun grayCard_isNeutralAfterWhiteBalance_forEveryCfa() {
    // Серый под тёплым светом: сенсор видит R=0.30, G=0.20, B=0.10.
    val neutral = doubleArrayOf(0.30 / 0.20, 1.0, 0.10 / 0.20)
    for (cfa in Cfa.entries) {
      val raw = uniform(cfa, 0.30f, 0.20f, 0.10f)
      val pipeline = ColorPipelineResolver.resolve(meta(neutral), ColorStrategy.AUTO)
      val img = RawDeveloper.develop(raw, pipeline, null, Profile.NEUTRAL, 0.0, 2)
      val (r, g, b) = channels(img.argb[0])
      assertTrue("$cfa: $r $g $b", r - g in -2..2 && b - g in -2..2)
    }
  }

  @Test
  fun outputSize_followsStep() {
    val raw = uniform(Cfa.RGGB, 0.2f, 0.2f, 0.2f, w = 16, h = 12)
    val p = ColorPipelineResolver.resolve(meta(null), ColorStrategy.NO_MATRIX)
    val half = RawDeveloper.develop(raw, p, null, Profile.NEUTRAL, 0.0, 2)
    val quarter = RawDeveloper.develop(raw, p, null, Profile.NEUTRAL, 0.0, 4)
    assertEquals(8, half.width); assertEquals(6, half.height)
    assertEquals(4, quarter.width); assertEquals(3, quarter.height)
  }

  @Test
  fun blackLevel_givesBlack_andWhiteLevel_givesWhite() {
    val p = ColorPipelineResolver.resolve(meta(null), ColorStrategy.NO_MATRIX)
    val dark = RawDeveloper.develop(uniform(Cfa.RGGB, 0f, 0f, 0f), p, null, Profile.NEUTRAL, 0.0, 2)
    val bright = RawDeveloper.develop(uniform(Cfa.RGGB, 1f, 1f, 1f), p, null, Profile.NEUTRAL, 0.0, 2)
    assertEquals(Triple(0, 0, 0), channels(dark.argb[0]))
    assertEquals(Triple(255, 255, 255), channels(bright.argb[0]))
  }

  @Test
  fun clippedHighlights_stayWhite_notMagenta() {
    // Сенсор пересвечен во всех каналах, а баланс белого сильно тянет красный и синий.
    val neutral = doubleArrayOf(0.5, 1.0, 0.6)
    val p = ColorPipelineResolver.resolve(meta(neutral), ColorStrategy.NO_MATRIX)
    val img = RawDeveloper.develop(uniform(Cfa.RGGB, 1f, 1f, 1f), p, null, Profile.NEUTRAL, 0.0, 2)
    assertEquals(Triple(255, 255, 255), channels(img.argb[0]))
  }

  @Test
  fun shadingMap_brightensCorners() {
    val gains = FloatArray(2 * 2 * 4) { 2f }
    val shading = ShadingMap(2, 2, gains)
    val p = ColorPipelineResolver.resolve(meta(null), ColorStrategy.NO_MATRIX)
    val raw = uniform(Cfa.RGGB, 0.1f, 0.1f, 0.1f)
    val plain = channels(RawDeveloper.develop(raw, p, null, Profile.NEUTRAL, 0.0, 2).argb[0]).first
    val shaded = channels(RawDeveloper.develop(raw, p, shading, Profile.NEUTRAL, 0.0, 2).argb[0]).first
    assertTrue("$shaded > $plain", shaded > plain)
  }

  @Test
  fun shadingMap_interpolatesBilinearly() {
    // Канал R: 1 слева, 3 справа → в центре 2.
    val g = FloatArray(2 * 2 * 4)
    for (row in 0..1) for (col in 0..1) g[(row * 2 + col) * 4] = if (col == 0) 1f else 3f
    assertEquals(2f, ShadingMap(2, 2, g).gain(0, 0.5f, 0.5f), 1e-5f)
  }

  @Test
  fun cfa_shadingChannels_matchRggbVectorOrder() {
    assertEquals(listOf(0, 1, 2, 3), (0..3).map { Cfa.RGGB.shadingChannel(it) })
    assertEquals(listOf(3, 2, 1, 0), (0..3).map { Cfa.BGGR.shadingChannel(it) })
    assertEquals(listOf(1, 0, 3, 2), (0..3).map { Cfa.GRBG.shadingChannel(it) })
  }

  @Test
  fun sensorMatrix_mapsWhiteToWhite() {
    // Произвольная правдоподобная ColorMatrix (XYZ → сенсор).
    val cm = Mat3(doubleArrayOf(0.9, -0.3, -0.1, -0.4, 1.3, 0.1, -0.05, 0.2, 0.6))
    val p = ColorPipelineResolver.resolve(
      meta(doubleArrayOf(0.5, 1.0, 0.7), cal = listOf(SensorCalibration(Illuminant.D65, cm, null, null))),
      ColorStrategy.AUTO,
    )
    assertEquals(ColorStrategy.SENSOR_MATRIX, p.used)
    val white = p.srgbFromCamera.apply(doubleArrayOf(1.0, 1.0, 1.0))
    white.forEach { assertEquals(1.0, it, 1e-9) }
  }

  @Test
  fun identityIspMatrix_isIgnored_likeOppoReports() {
    val p = ColorPipelineResolver.resolve(meta(doubleArrayOf(0.5, 1.0, 0.7), isp = Mat3.IDENTITY), ColorStrategy.ISP_MATRIX)
    assertEquals(ColorStrategy.NO_MATRIX, p.used)
    assertTrue(p.notes.any { it.contains("единичная") })
  }

  @Test
  fun pickCalibration_prefersDaylight() {
    val a = SensorCalibration(Illuminant.STANDARD_A, Mat3(doubleArrayOf(1.0, 0.1, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)), null, null)
    val d65 = SensorCalibration(Illuminant.D65, Mat3(doubleArrayOf(1.0, 0.2, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)), null, null)
    assertEquals(Illuminant.D65, ColorPipelineResolver.pickCalibration(listOf(a, d65))?.illuminant)
    assertEquals(Illuminant.STANDARD_A, ColorPipelineResolver.pickCalibration(listOf(a))?.illuminant)
    assertNull(ColorPipelineResolver.pickCalibration(emptyList()))
  }

  @Test
  fun placeholderForwardMatrix_isDetected() {
    assertTrue(ColorPipelineResolver.looksLikePlaceholderForwardMatrix(Mat3.XYZ_D50_FROM_SRGB))
    assertFalse(ColorPipelineResolver.looksLikePlaceholderForwardMatrix(Mat3.IDENTITY))
  }

  @Test
  fun mat3_inverse_roundTrips() {
    val m = Mat3(doubleArrayOf(2.0, 1.0, 0.0, 0.0, 1.0, 3.0, 1.0, 0.0, 1.0))
    val inv = assertNotNullAndGet(m.inverse())
    assertTrue((m * inv).isIdentity(1e-9))
  }

  @Test
  fun srgbEncode_knownValues() {
    assertEquals(0.0, Profile.srgbEncode(0.0), 1e-12)
    assertEquals(1.0, Profile.srgbEncode(1.0), 1e-9)
    assertEquals(0.7354, Profile.srgbEncode(0.5), 1e-3)
  }

  @Test
  fun exposure_plusOneEv_brightens() {
    val p = ColorPipelineResolver.resolve(meta(null), ColorStrategy.NO_MATRIX)
    val raw = uniform(Cfa.RGGB, 0.1f, 0.1f, 0.1f)
    val base = channels(RawDeveloper.develop(raw, p, null, Profile.NEUTRAL, 0.0, 2).argb[0]).second
    val plus = channels(RawDeveloper.develop(raw, p, null, Profile.NEUTRAL, 1.0, 2).argb[0]).second
    assertTrue(plus > base)
  }

  private fun <T> assertNotNullAndGet(v: T?): T {
    assertNotNull(v)
    return v!!
  }
}
