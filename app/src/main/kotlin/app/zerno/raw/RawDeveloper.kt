package app.zerno.raw

/** Проявленный кадр: пиксели ARGB_8888, построчно. */
class DevelopedImage(val width: Int, val height: Int, val argb: IntArray)

/**
 * Эталонный проявщик RAW на CPU. Медленный, зато простой и проверяемый тестами;
 * позже его повторит GPU-версия, а этот останется эталоном для сравнения.
 *
 * Шаги: чёрный/белый уровень → затенение объектива → баланс белого → клиппинг →
 * демозаик «суперпиксель» (блок 2×2 → 1 пиксель) → цветовая матрица → профиль → sRGB.
 */
object RawDeveloper {

  /**
   * @param step шаг по сенсору: 2 — половина разрешения (каждый блок 2×2),
   *   4 — четверть (каждый второй блок, для быстрого превью). Только чётные значения.
   * @param checkCancelled вызывается раз в несколько строк; может бросить исключение, чтобы прервать работу.
   */
  fun develop(
    raw: RawImage,
    pipeline: ColorPipeline,
    shading: ShadingMap?,
    profile: Profile,
    exposureEv: Double,
    step: Int,
    checkCancelled: () -> Unit = {},
  ): DevelopedImage {
    require(step >= 2 && step % 2 == 0) { "step must be even and >= 2" }
    val outW = raw.width / step
    val outH = raw.height / step
    require(outW > 0 && outH > 0) { "image is too small for step $step" }

    val lut = profile.buildLut(exposureEv)
    val lutScale = (Profile.LUT_SIZE - 1) / Profile.LUT_RANGE
    val m = pipeline.srgbFromCamera.m
    val wb = pipeline.wbGains
    val sat = profile.saturation

    val cfaColor = IntArray(4) { raw.cfa.colorAt(it).ordinal }
    val shadingChannel = IntArray(4) { raw.cfa.shadingChannel(it) }
    val black = FloatArray(4) { raw.blackLevel[it] }
    val range = FloatArray(4) { raw.whiteLevel - raw.blackLevel[it] }

    val out = IntArray(outW * outH)
    val sum = DoubleArray(3)
    val count = IntArray(3)

    for (oy in 0 until outH) {
      if (oy and 31 == 0) checkCancelled()
      val by = oy * step
      val v = (by + 1f) / raw.height
      for (ox in 0 until outW) {
        val bx = ox * step
        val u = (bx + 1f) / raw.width
        sum.fill(0.0)
        count.fill(0)
        for (i in 0..3) {
          val x = bx + (i and 1)
          val y = by + (i shr 1)
          var value = ((raw.valueAt(x, y) - black[i]) / range[i]).coerceAtLeast(0f)
          if (shading != null) value *= shading.gain(shadingChannel[i], u, v)
          sum[cfaColor[i]] += value.toDouble()
          count[cfaColor[i]]++
        }
        // Баланс белого и клиппинг: множители нормированы к минимуму 1, поэтому
        // пересвеченный пиксель после обрезки до 1 становится белым, а не розовым.
        val r = (sum[0] / count[0] * wb[0]).coerceAtMost(1.0)
        val g = (sum[1] / count[1] * wb[1]).coerceAtMost(1.0)
        val b = (sum[2] / count[2] * wb[2]).coerceAtMost(1.0)

        var sr = m[0] * r + m[1] * g + m[2] * b
        var sg = m[3] * r + m[4] * g + m[5] * b
        var sb = m[6] * r + m[7] * g + m[8] * b
        if (sat != 1.0) {
          val lum = 0.2126 * sr + 0.7152 * sg + 0.0722 * sb
          sr = lum + (sr - lum) * sat
          sg = lum + (sg - lum) * sat
          sb = lum + (sb - lum) * sat
        }
        out[oy * outW + ox] = (0xFF shl 24) or
          (lut[index(sr, lutScale)] shl 16) or
          (lut[index(sg, lutScale)] shl 8) or
          lut[index(sb, lutScale)]
      }
    }
    return DevelopedImage(outW, outH, out)
  }

  private fun index(linear: Double, scale: Double): Int =
    (linear * scale + 0.5).toInt().coerceIn(0, Profile.LUT_SIZE - 1)
}
