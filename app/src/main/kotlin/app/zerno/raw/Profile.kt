package app.zerno.raw

import kotlin.math.pow

/**
 * Профиль проявки. Все профили — собственные (не копии чужих), параметры подобраны «на глаз»
 * и будут уточняться по снимкам с реальных устройств.
 *
 * Порядок применения: тёплый/холодный сдвиг → насыщенность (или ч/б) в линейном свете →
 * плечо в светах → кодирование sRGB → S-контраст → подъём теней → зерно.
 */
enum class Profile(
  val title: String,
  /** Белая точка плеча: линейное значение, которое становится чистым белым. */
  val shoulderWhite: Double,
  /** 0 — без S-кривой, 1 — полная smoothstep-кривая. */
  val contrast: Double,
  /** Насколько поднять чёрный (0..0.1). */
  val blackLift: Double,
  val saturation: Double,
  /** Множители R и B в линейном свете: «теплее» — R больше, B меньше. */
  val warmR: Double,
  val warmB: Double,
  val monochrome: Boolean,
  /** Сила зерна: 0 — без зерна. */
  val grain: Double,
) {
  NATURAL("Натуральный", 5.0, 0.42, 0.0, 1.05, 1.0, 1.0, false, 0.0),
  WARM_FILM("Тёплая плёнка", 3.0, 0.34, 0.035, 0.95, 1.05, 0.92, false, 0.045),
  DIGICAM("Цифромыльница", 8.0, 0.55, 0.0, 1.18, 0.98, 1.04, false, 0.015),
  MONO("Ч/Б", 4.0, 0.55, 0.02, 0.0, 1.0, 1.0, true, 0.06);

  /** Линейный свет (после экспозиции) → 0..1 до кодирования sRGB. */
  fun shoulder(x: Double): Double {
    if (x <= 0) return 0.0
    val w = shoulderWhite
    // Reinhard с белой точкой: f(0)=0, f'(0)=1, f(w)=1.
    return (x * (1 + x / (w * w)) / (1 + x)).coerceAtMost(1.0)
  }

  /** Закодированное sRGB-значение 0..1 → итоговое 0..1. */
  fun finish(e: Double): Double {
    val s = e * e * (3 - 2 * e)
    val c = e + (s - e) * contrast
    return blackLift + (1 - blackLift) * c
  }

  /** Полная тональная функция для линейного значения после экспозиции (без зерна). */
  fun tone(x: Double): Double = finish(srgbEncode(shoulder(x)))

  /** Таблица: линейное значение 0..LUT_RANGE → 8-битный результат, с учётом экспозиции. */
  fun buildLut(exposureEv: Double): IntArray {
    val gain = 2.0.pow(exposureEv)
    return IntArray(LUT_SIZE) { i ->
      val linear = i.toDouble() / (LUT_SIZE - 1) * LUT_RANGE * gain
      (tone(linear) * 255 + 0.5).toInt().coerceIn(0, 255)
    }
  }

  companion object {
    val DEFAULT = NATURAL
    const val LUT_SIZE = 8192

    /** Диапазон входа LUT: после баланса белого и матрицы значения чуть выше 1 возможны. */
    const val LUT_RANGE = 2.0

    fun srgbEncode(x: Double): Double =
      if (x <= 0.0031308) 12.92 * x else 1.055 * x.pow(1 / 2.4) - 0.055

    fun byName(name: String?): Profile = entries.firstOrNull { it.name == name } ?: DEFAULT
  }
}
