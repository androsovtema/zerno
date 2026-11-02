package app.zerno.raw

import kotlin.math.abs

/** Матрица 3×3, хранится по строкам. */
class Mat3(val m: DoubleArray) {
  init {
    require(m.size == 9) { "Mat3 needs 9 values" }
  }

  operator fun get(row: Int, col: Int): Double = m[row * 3 + col]

  operator fun times(o: Mat3): Mat3 {
    val r = DoubleArray(9)
    for (i in 0..2) for (j in 0..2) {
      r[i * 3 + j] = this[i, 0] * o[0, j] + this[i, 1] * o[1, j] + this[i, 2] * o[2, j]
    }
    return Mat3(r)
  }

  fun apply(v: DoubleArray): DoubleArray =
    DoubleArray(3) { i -> this[i, 0] * v[0] + this[i, 1] * v[1] + this[i, 2] * v[2] }

  fun determinant(): Double =
    m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6])

  /** Обратная матрица или null, если матрица вырождена. */
  fun inverse(): Mat3? {
    val det = determinant()
    if (abs(det) < 1e-12) return null
    val inv = doubleArrayOf(
      m[4] * m[8] - m[5] * m[7], m[2] * m[7] - m[1] * m[8], m[1] * m[5] - m[2] * m[4],
      m[5] * m[6] - m[3] * m[8], m[0] * m[8] - m[2] * m[6], m[2] * m[3] - m[0] * m[5],
      m[3] * m[7] - m[4] * m[6], m[1] * m[6] - m[0] * m[7], m[0] * m[4] - m[1] * m[3],
    )
    return Mat3(DoubleArray(9) { inv[it] / det })
  }

  /** Нормирует каждую строку так, чтобы её сумма была 1 (белый остаётся белым). */
  fun normalizeRows(): Mat3? {
    val r = m.copyOf()
    for (i in 0..2) {
      val s = r[i * 3] + r[i * 3 + 1] + r[i * 3 + 2]
      if (abs(s) < 1e-12) return null
      for (j in 0..2) r[i * 3 + j] /= s
    }
    return Mat3(r)
  }

  fun maxAbsDiff(o: Mat3): Double = m.indices.maxOf { abs(m[it] - o.m[it]) }

  fun isIdentity(tolerance: Double = 1e-3): Boolean = maxAbsDiff(IDENTITY) < tolerance

  override fun toString(): String =
    (0..2).joinToString(" | ") { i -> (0..2).joinToString(" ") { j -> "%.4f".format(this[i, j]) } }

  companion object {
    val IDENTITY = Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))

    /** Линейный sRGB (D65) → XYZ. */
    val XYZ_FROM_SRGB = Mat3(doubleArrayOf(
      0.4124564, 0.3575761, 0.1804375,
      0.2126729, 0.7151522, 0.0721750,
      0.0193339, 0.1191920, 0.9503041,
    ))

    /**
     * Линейный sRGB → XYZ с адаптацией к D50 (Bradford). Такую «заглушку» вместо настоящей
     * калибровки пишут в ForwardMatrix некоторые устройства Oppo/realme/OnePlus.
     */
    val XYZ_D50_FROM_SRGB = Mat3(doubleArrayOf(
      0.4360747, 0.3850649, 0.1430804,
      0.2225045, 0.7168786, 0.0606169,
      0.0139322, 0.0971045, 0.7141733,
    ))
  }
}
