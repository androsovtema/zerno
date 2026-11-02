package app.zerno.raw

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2

/**
 * Подбирает экспозицию проявки (в EV) по самому RAW: сырые данные темнее того, что показывает
 * штатная камера, потому что та добавляет цифровое усиление и тональную кривую.
 *
 * Логика: средняя по логарифму яркость сцены должна стать «средне-серой» после профиля,
 * но так, чтобы светлые 3 % кадра не уходили в чистый белый сильнее, чем на 1.5 EV.
 */
object AutoExposure {
  const val TARGET_MID = 0.46
  const val MIN_EV = -2.0
  const val MAX_EV = 5.0

  fun compute(raw: RawImage, pipeline: ColorPipeline, shading: ShadingMap?, profile: Profile): Double {
    val lum = sampleLuminance(raw, pipeline, shading)
    if (lum.isEmpty()) return 0.0
    lum.sort()
    val eps = 1e-4
    val geoMean = exp(lum.sumOf { ln(it + eps) } / lum.size) - eps
    val p97 = lum[((lum.size - 1) * 0.97).toInt()]

    val evMid = solveEv(geoMean.coerceAtLeast(1e-5), profile)
    val evGuard = if (p97 > 1e-5) log2(profile.shoulderWhite / p97) else MAX_EV
    val ev = minOf(evMid, maxOf(evGuard, evMid - 1.5))
    return ev.coerceIn(MIN_EV, MAX_EV)
  }

  /** EV, при котором яркость [linear] после профиля станет TARGET_MID (бинарный поиск). */
  fun solveEv(linear: Double, profile: Profile): Double {
    var lo = MIN_EV
    var hi = MAX_EV
    repeat(40) {
      val mid = (lo + hi) / 2
      if (profile.tone(linear * Math.pow(2.0, mid)) < TARGET_MID) lo = mid else hi = mid
    }
    return (lo + hi) / 2
  }

  /** Яркость примерно 40 000 точек кадра после баланса белого и цветовой матрицы. */
  private fun sampleLuminance(raw: RawImage, p: ColorPipeline, shading: ShadingMap?): DoubleArray {
    val blocksX = raw.width / 2
    val blocksY = raw.height / 2
    val stride = maxOf(1, kotlin.math.sqrt(blocksX.toDouble() * blocksY / 40_000).toInt())
    val m = p.srgbFromCamera.m
    val out = ArrayList<Double>((blocksX / stride + 1) * (blocksY / stride + 1))
    val rgb = DoubleArray(3)
    val n = IntArray(3)
    var by = 0
    while (by < blocksY) {
      var bx = 0
      while (bx < blocksX) {
        rgb.fill(0.0); n.fill(0)
        for (i in 0..3) {
          val x = bx * 2 + (i and 1)
          val y = by * 2 + (i shr 1)
          var v = ((raw.valueAt(x, y) - raw.blackLevel[i]) / (raw.whiteLevel - raw.blackLevel[i])).coerceAtLeast(0f)
          if (shading != null) v *= shading.gain(raw.cfa.shadingChannel(i), x.toFloat() / raw.width, y.toFloat() / raw.height)
          val c = raw.cfa.colorAt(i).ordinal
          rgb[c] += v.toDouble(); n[c]++
        }
        val r = (rgb[0] / n[0] * p.wbGains[0]).coerceAtMost(1.0)
        val g = (rgb[1] / n[1] * p.wbGains[1]).coerceAtMost(1.0)
        val b = (rgb[2] / n[2] * p.wbGains[2]).coerceAtMost(1.0)
        val sr = m[0] * r + m[1] * g + m[2] * b
        val sg = m[3] * r + m[4] * g + m[5] * b
        val sb = m[6] * r + m[7] * g + m[8] * b
        out += (0.2126 * sr + 0.7152 * sg + 0.0722 * sb).coerceAtLeast(0.0)
        bx += stride
      }
      by += stride
    }
    return out.toDoubleArray()
  }
}
