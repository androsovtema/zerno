package app.zerno.ui

import app.zerno.raw.Profile
import app.zerno.raw.SwatchScene
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLogicTest {

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
