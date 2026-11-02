package app.zerno.raw

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RawDeveloperTest {

  private val black = 64f
  private val white = 1023f

  /** Кадр, где значение каждой ячейки задаёт функция (линейный свет 0..1 по цвету). */
  private fun synth(cfa: Cfa, w: Int = 8, h: Int = 8, f: (x: Int, y: Int, c: CfaColor) -> Float): RawImage {
    val data = ShortArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      val c = cfa.colorAt((y and 1) * 2 + (x and 1))
      data[y * w + x] = (black + f(x, y, c).coerceIn(0f, 1f) * (white - black) + 0.5f).toInt().toShort()
    }
    return RawImage(w, h, data, cfa, FloatArray(4) { black }, white)
  }

  private fun uniform(cfa: Cfa, r: Float, g: Float, b: Float, w: Int = 8, h: Int = 8) =
    synth(cfa, w, h) { _, _, c -> when (c) { CfaColor.RED -> r; CfaColor.GREEN -> g; CfaColor.BLUE -> b } }

  private fun ch(argb: Int) = Triple((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

  private fun meta(neutral: DoubleArray?, isp: Mat3? = null, cal: List<SensorCalibration> = emptyList()) =
    ColorMetadata(neutral = neutral, ispGains = null, ispMatrix = isp, calibrations = cal)

  private fun opts(step: Int, profile: Profile = Profile.NATURAL, ev: Double = 0.0, rotation: Int = 0) =
    DevelopOptions(profile, ev, step, rotation)

  private val plain = ColorPipelineResolver.resolve(ColorMetadata(null, null, null, emptyList()), ColorStrategy.NO_MATRIX)

  @Test
  fun grayCard_isNeutral_forEveryCfa_andBothDemosaics() {
    // Серый под тёплым светом: сенсор видит R=0.30, G=0.20, B=0.10.
    val neutral = doubleArrayOf(0.30 / 0.20, 1.0, 0.10 / 0.20)
    for (cfa in Cfa.entries) for (step in listOf(1, 2)) {
      val raw = uniform(cfa, 0.30f, 0.20f, 0.10f)
      val p = ColorPipelineResolver.resolve(meta(neutral), ColorStrategy.AUTO)
      val img = RawDeveloper.develop(raw, p, null, opts(step))
      img.argb.forEach { px ->
        val (r, g, b) = ch(px)
        assertTrue("$cfa step $step: $r $g $b", r - g in -2..2 && b - g in -2..2)
      }
    }
  }

  @Test
  fun outputSize_followsStepAndRotation() {
    val raw = uniform(Cfa.RGGB, 0.2f, 0.2f, 0.2f, w = 16, h = 12)
    assertEquals(16 to 12, RawDeveloper.outputSize(raw, 1, 0))
    assertEquals(8 to 6, RawDeveloper.outputSize(raw, 2, 0))
    assertEquals(3 to 4, RawDeveloper.outputSize(raw, 4, 90))
    val img = RawDeveloper.develop(raw, plain, null, opts(2, rotation = 270))
    assertEquals(6, img.width); assertEquals(8, img.height)
  }

  @Test
  fun rotation90_movesTopLeftToTopRight() {
    // Яркий только левый верхний блок 2×2.
    val raw = synth(Cfa.RGGB, 8, 4) { x, y, _ -> if (x < 2 && y < 2) 0.8f else 0f }
    val img = RawDeveloper.develop(raw, plain, null, opts(2, rotation = 90))
    // Кадр 4×2 → после поворота 2×4; левый верх уходит в правый верх.
    assertEquals(2, img.width)
    assertTrue(ch(img.argb[0 * 2 + 1]).second > 100)
    assertEquals(0, ch(img.argb[0]).second)
  }

  @Test
  fun mhc_reproducesHorizontalRampInInterior() {
    // Линейный градиент одинаков во всех каналах — MHC обязан восстановить его без ошибок.
    val w = 32
    val raw = synth(Cfa.RGGB, w, 16) { x, _, _ -> 0.05f + 0.02f * x }
    val img = RawDeveloper.develop(raw, plain, null, opts(1, Profile.NATURAL))
    for (y in 4 until 12) for (x in 4 until w - 4) {
      val (r, g, b) = ch(img.argb[y * w + x])
      assertTrue("($x,$y) $r $g $b", kotlin.math.abs(r - g) <= 2 && kotlin.math.abs(b - g) <= 2)
    }
  }

  @Test
  fun blackLevel_givesBlack_andWhite_givesWhite() {
    val dark = RawDeveloper.develop(uniform(Cfa.RGGB, 0f, 0f, 0f), plain, null, opts(2))
    val bright = RawDeveloper.develop(uniform(Cfa.RGGB, 1f, 1f, 1f), plain, null, opts(2, ev = 3.0))
    assertEquals(Triple(0, 0, 0), ch(dark.argb[0]))
    assertEquals(Triple(255, 255, 255), ch(bright.argb[0]))
  }

  @Test
  fun clippedHighlights_stayWhite_notMagenta() {
    val p = ColorPipelineResolver.resolve(meta(doubleArrayOf(0.5, 1.0, 0.6)), ColorStrategy.NO_MATRIX)
    val img = RawDeveloper.develop(uniform(Cfa.RGGB, 1f, 1f, 1f), p, null, opts(2, ev = 3.0))
    assertEquals(Triple(255, 255, 255), ch(img.argb[0]))
  }

  @Test
  fun shadingMap_brightens() {
    val shading = ShadingMap(2, 2, FloatArray(16) { 2f })
    val raw = uniform(Cfa.RGGB, 0.1f, 0.1f, 0.1f)
    for (step in listOf(1, 2)) {
      val a = ch(RawDeveloper.develop(raw, plain, null, opts(step)).argb[0]).first
      val b = ch(RawDeveloper.develop(raw, plain, shading, opts(step)).argb[0]).first
      assertTrue("step $step: $b > $a", b > a)
    }
  }

  @Test
  fun shadingMap_interpolatesBilinearly() {
    val g = FloatArray(16)
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
  fun mono_isGray_andGrain_isDeterministic() {
    val raw = uniform(Cfa.RGGB, 0.3f, 0.2f, 0.1f, 16, 16)
    val a = RawDeveloper.develop(raw, plain, null, opts(2, Profile.MONO))
    val b = RawDeveloper.develop(raw, plain, null, opts(2, Profile.MONO))
    a.argb.forEach { val (r, g, bl) = ch(it); assertTrue(r == g && g == bl) }
    assertArrayEquals(a.argb, b.argb)
    assertTrue("зерно даёт разброс", a.argb.toSet().size > 1)
  }

  @Test
  fun profiles_areMonotonic() {
    for (p in Profile.entries) {
      var prev = -1.0
      for (i in 0..400) {
        val v = p.tone(i / 100.0)
        assertTrue("${p.name} at ${i / 100.0}", v >= prev - 1e-12)
        prev = v
      }
      assertEquals(1.0, p.tone(p.shoulderWhite), 1e-9)
    }
  }

  @Test
  fun autoExposure_bringsDarkSceneToMidGray() {
    val raw = uniform(Cfa.RGGB, 0.03f, 0.03f, 0.03f, 64, 64)
    val ev = AutoExposure.compute(raw, plain, null, Profile.NATURAL)
    assertTrue("ev=$ev", ev > 1.5)
    val img = RawDeveloper.develop(raw, plain, null, opts(2, ev = ev))
    val g = ch(img.argb[0]).second / 255.0
    assertEquals(AutoExposure.TARGET_MID, g, 0.03)
  }

  @Test
  fun autoExposure_protectsBrightScene() {
    val raw = uniform(Cfa.RGGB, 0.9f, 0.9f, 0.9f, 64, 64)
    assertTrue(AutoExposure.compute(raw, plain, null, Profile.NATURAL) < 1.0)
  }

  @Test
  fun sensorMatrix_mapsWhiteToWhite() {
    val cm = Mat3(doubleArrayOf(0.9, -0.3, -0.1, -0.4, 1.3, 0.1, -0.05, 0.2, 0.6))
    val p = ColorPipelineResolver.resolve(
      meta(doubleArrayOf(0.5, 1.0, 0.7), cal = listOf(SensorCalibration(Illuminant.D65, cm, null, null))),
      ColorStrategy.AUTO,
    )
    assertEquals(ColorStrategy.SENSOR_MATRIX, p.used)
    p.srgbFromCamera.apply(doubleArrayOf(1.0, 1.0, 1.0)).forEach { assertEquals(1.0, it, 1e-9) }
  }

  @Test
  fun identityIspMatrix_isIgnored_likeOppoReports() {
    val p = ColorPipelineResolver.resolve(meta(doubleArrayOf(0.5, 1.0, 0.7), isp = Mat3.IDENTITY), ColorStrategy.ISP_MATRIX)
    assertEquals(ColorStrategy.GENERIC, p.used)
    assertTrue(p.notes.any { it.contains("единичная") })
  }

  @Test
  fun placeholderColorMatrix_fromRealOnePlusDng_isRejected() {
    // ColorMatrix (D55) из DNG OnePlus 6 (raw.pixls.us): по сути XYZ→sRGB с масштабом каналов.
    val cm = Mat3(doubleArrayOf(0.6094, -0.2891, -0.0938, -0.9688, 1.8750, 0.0391, 0.0547, -0.1875, 0.9922))
    assertTrue(ColorPipelineResolver.looksLikePlaceholderColorMatrix(cm))
    val p = ColorPipelineResolver.resolve(
      meta(doubleArrayOf(0.514, 1.0, 0.539), cal = listOf(SensorCalibration(Illuminant.D55, cm, null, null))),
      ColorStrategy.AUTO,
    )
    assertEquals(ColorStrategy.GENERIC, p.used)
  }

  @Test
  fun genericMatrix_keepsWhite() {
    ColorPipelineResolver.GENERIC_PHONE.apply(doubleArrayOf(1.0, 1.0, 1.0)).forEach { assertEquals(1.0, it, 1e-9) }
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
    val inv = m.inverse()
    assertNotNull(inv)
    assertTrue((m * inv!!).isIdentity(1e-9))
  }

  @Test
  fun srgbEncode_knownValues() {
    assertEquals(0.0, Profile.srgbEncode(0.0), 1e-12)
    assertEquals(1.0, Profile.srgbEncode(1.0), 1e-9)
    assertEquals(0.7354, Profile.srgbEncode(0.5), 1e-3)
  }

  @Test
  fun shotFile_roundTrips() {
    val raw = synth(Cfa.GRBG, 301, 77) { x, y, _ -> ((x * 7 + y * 13) % 100) / 100f }
    val meta = ColorMetadata(
      doubleArrayOf(0.5, 1.0, 0.7), doubleArrayOf(2.0, 1.0, 1.5), Mat3.XYZ_FROM_SRGB,
      listOf(SensorCalibration(Illuminant.D65, Mat3.XYZ_D50_FROM_SRGB, null, Mat3.IDENTITY)),
    )
    val shading = ShadingMap(3, 4, FloatArray(48) { 1f + it / 100f })
    val shot = Shot("ZERNO_1", 1234L, raw, meta, shading, 90, "инфо\nстрока")
    val dir = Files.createTempDirectory("zraw").toFile()
    val f = File(dir, "a.zraw")
    ShotFile.write(shot, f)
    val back = ShotFile.read(f)
    assertEquals("ZERNO_1", back.name)
    assertEquals(90, back.rotation)
    assertEquals("инфо\nстрока", back.captureInfo)
    assertEquals(Cfa.GRBG, back.raw.cfa)
    assertArrayEquals(raw.data, back.raw.data)
    assertArrayEquals(meta.neutral, back.meta.neutral, 0.0)
    assertEquals(1, back.meta.calibrations.size)
    assertArrayEquals(shading.gains, back.shading!!.gains, 0f)
    dir.deleteRecursively()
  }
}
