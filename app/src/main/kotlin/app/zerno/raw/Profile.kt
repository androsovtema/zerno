package app.zerno.raw

import kotlin.math.pow

/**
 * Профиль проявки: тональная кривая по линейному свету и насыщенность.
 * Прототип: два простых профиля, чтобы проверить пайплайн. Настоящие профили — следующий шаг.
 */
enum class Profile(val title: String, val saturation: Double) {
  NEUTRAL("Нейтральный", 1.0) {
    override fun tone(x: Double): Double = x
  },
  SOFT("Мягкий", 0.9) {
    // Плавное плечо в светах (Reinhard с белой точкой) и чуть приподнятые тени.
    override fun tone(x: Double): Double {
      val v = x * 1.6
      val white = 2.5
      val y = v * (1 + v / (white * white)) / (1 + v)
      return 0.015 + 0.985 * y
    }
  };

  /** Линейный свет 0..1+ → линейный свет 0..1. */
  abstract fun tone(x: Double): Double

  /** Таблица: линейное значение (0..1, LUT_SIZE шагов) → 8-битный sRGB. */
  fun buildLut(exposureEv: Double): IntArray {
    val gain = 2.0.pow(exposureEv)
    return IntArray(LUT_SIZE) { i ->
      val linear = i.toDouble() / (LUT_SIZE - 1) * LUT_RANGE * gain
      (srgbEncode(tone(linear).coerceIn(0.0, 1.0)) * 255 + 0.5).toInt().coerceIn(0, 255)
    }
  }

  companion object {
    const val LUT_SIZE = 8192

    /** LUT покрывает линейный диапазон 0..LUT_RANGE, чтобы кривые с плечом не теряли света. */
    const val LUT_RANGE = 2.0

    fun srgbEncode(x: Double): Double =
      if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055
  }
}
