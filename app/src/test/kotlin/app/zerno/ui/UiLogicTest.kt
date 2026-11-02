package app.zerno.ui

import app.zerno.raw.Profile
import app.zerno.raw.SwatchScene
import app.zerno.ui.theme.GrainNoise
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {

  @Test
  fun grainNoise_isNormalized_andSeamless() {
    val n = GrainNoise.SIZE
    val f = GrainNoise.field()
    val mean = f.average()
    val sd = kotlin.math.sqrt(f.sumOf { (it - mean) * (it - mean) } / f.size)
    assertEquals(0.0, mean, 1e-3)
    assertEquals(1.0, sd, 1e-3)
    // Шов плитки (последний столбец → первый) не должен отличаться от соседних столбцов внутри.
    fun colDiff(a: Int, b: Int) = (0 until n).sumOf { y -> abs(f[y * n + a] - f[y * n + b]).toDouble() } / n
    val inner = (0 until n - 1).map { colDiff(it, it + 1) }.average()
    assertTrue("шов ${colDiff(n - 1, 0)} против $inner", colDiff(n - 1, 0) < inner * 1.25)
  }

  @Test
  fun grainArgb_lightAndDarkSpecks() {
    val px = GrainNoise.argb(floatArrayOf(2f, -2f, 0f))
    assertEquals(0xFFF6EA, px[0] and 0xFFFFFF)
    assertEquals(0x000000, px[1] and 0xFFFFFF)
    assertEquals(0, px[2] ushr 24)
  }

  @Test
  fun framesLabel_russianPlurals() {
    assertEquals("1 кадр", framesLabel(1))
    assertEquals("3 кадра", framesLabel(3))
    assertEquals("5 кадров", framesLabel(5))
    assertEquals("11 кадров", framesLabel(11))
    assertEquals("22 кадра", framesLabel(22))
    assertEquals("114 кадров", framesLabel(114))
  }

  @Test
  fun swatchScene_differsBetweenProfiles() {
    val natural = SwatchScene.develop(Profile.NATURAL).argb
    val mono = SwatchScene.develop(Profile.MONO).argb
    assertEquals(natural.size, mono.size)
    // В Ч/Б каналы равны, в «Натуральном» небо цветное.
    assertTrue(mono.all { px -> ((px shr 16) and 0xFF) == (px and 0xFF) || abs(((px shr 16) and 0xFF) - (px and 0xFF)) < 3 })
    assertTrue(natural.any { px -> abs(((px shr 16) and 0xFF) - (px and 0xFF)) > 40 })
  }
}
