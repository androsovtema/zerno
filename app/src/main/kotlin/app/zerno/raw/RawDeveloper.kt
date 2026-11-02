package app.zerno.raw

import java.util.stream.IntStream

/** Проявленный кадр: пиксели ARGB_8888, построчно. */
class DevelopedImage(val width: Int, val height: Int, val argb: IntArray)

data class DevelopOptions(
  val profile: Profile,
  val exposureEv: Double,
  /**
   * 1 — полное разрешение (демозаик Malvar–He–Cutler); 2 — половина (блок 2×2 → пиксель);
   * 4, 6, … — каждый (step/2)-й блок, для превью и миниатюр.
   */
  val step: Int,
  /** Поворот по часовой стрелке: 0, 90, 180, 270 (как SENSOR_ORIENTATION). */
  val rotation: Int = 0,
  val grainSeed: Int = 0,
) {
  init {
    require(step == 1 || (step >= 2 && step % 2 == 0)) { "step must be 1 or even" }
    require(rotation in setOf(0, 90, 180, 270)) { "rotation must be 0/90/180/270" }
  }
}

/**
 * Проявщик RAW на CPU, параллельно по строкам. Шаги: чёрный/белый уровень → затенение объектива →
 * демозаик → баланс белого → клиппинг → матрица в sRGB → профиль → зерно → поворот.
 */
object RawDeveloper {

  fun outputSize(raw: RawImage, step: Int, rotation: Int): Pair<Int, Int> {
    val w = if (step == 1) raw.width else raw.width / step
    val h = if (step == 1) raw.height else raw.height / step
    return if (rotation == 90 || rotation == 270) h to w else w to h
  }

  fun develop(
    raw: RawImage,
    pipeline: ColorPipeline,
    shading: ShadingMap?,
    options: DevelopOptions,
    checkCancelled: () -> Unit = {},
  ): DevelopedImage {
    val step = options.step
    val sw = if (step == 1) raw.width else raw.width / step
    val sh = if (step == 1) raw.height else raw.height / step
    require(sw > 0 && sh > 0) { "image is too small for step $step" }
    val finisher = Finisher(pipeline, options)
    val out = IntArray(sw * sh)
    val rotation = options.rotation
    val bands = minOf(sh, Runtime.getRuntime().availableProcessors() * 4)

    IntStream.range(0, bands).parallel().forEach { band ->
      val y0 = sh * band / bands
      val y1 = sh * (band + 1) / bands
      val sampler = Sampler(raw)
      val rgb = DoubleArray(3)
      for (sy in y0 until y1) {
        if ((sy - y0) and 15 == 0) checkCancelled()
        for (sx in 0 until sw) {
          if (step == 1) {
            sampler.mhc(sx, sy, rgb)
            if (shading != null) {
              val u = sx.toFloat() / raw.width
              val v = sy.toFloat() / raw.height
              rgb[0] *= shading.gain(0, u, v)
              rgb[1] *= (shading.gain(1, u, v) + shading.gain(2, u, v)) * 0.5
              rgb[2] *= shading.gain(3, u, v)
            }
          } else {
            sampler.superpixel(sx * step, sy * step, shading, rgb)
          }
          val idx = when (rotation) {
            90 -> sx * sh + (sh - 1 - sy)
            180 -> (sh - 1 - sy) * sw + (sw - 1 - sx)
            270 -> (sw - 1 - sx) * sh + sy
            else -> sy * sw + sx
          }
          out[idx] = finisher.pixel(rgb, sx, sy)
        }
      }
    }
    val (ow, oh) = outputSize(raw, step, rotation)
    return DevelopedImage(ow, oh, out)
  }

  /** Чтение нормированных значений сенсора (0..1 после вычета чёрного). */
  private class Sampler(private val raw: RawImage) {
    private val w = raw.width
    private val h = raw.height
    private val data = raw.data
    private val black = FloatArray(4) { raw.blackLevel[it] }
    private val inv = FloatArray(4) { 1f / (raw.whiteLevel - raw.blackLevel[it]) }
    private val color = IntArray(4) { raw.cfa.colorAt(it).ordinal }
    private val shadingChannel = IntArray(4) { raw.cfa.shadingChannel(it) }
    private val sum = DoubleArray(3)
    private val count = IntArray(3)

    /** Значение с зеркальным отражением на краях (сохраняет чётность, а значит и цвет ячейки). */
    fun s(xIn: Int, yIn: Int): Double {
      val x = if (xIn < 0) -xIn else if (xIn >= w) 2 * (w - 1) - xIn else xIn
      val y = if (yIn < 0) -yIn else if (yIn >= h) 2 * (h - 1) - yIn else yIn
      val p = ((y and 1) shl 1) or (x and 1)
      val v = ((data[y * w + x].toInt() and 0xFFFF) - black[p]) * inv[p]
      return if (v > 0f) v.toDouble() else 0.0
    }

    private fun colorAt(x: Int, y: Int): Int = color[((y and 1) shl 1) or (x and 1)]

    fun superpixel(bx: Int, by: Int, shading: ShadingMap?, out: DoubleArray) {
      sum.fill(0.0)
      count.fill(0)
      val u = (bx + 1f) / w
      val v = (by + 1f) / h
      for (i in 0..3) {
        var value = s(bx + (i and 1), by + (i shr 1))
        if (shading != null) value *= shading.gain(shadingChannel[i], u, v)
        sum[color[i]] += value
        count[color[i]]++
      }
      out[0] = sum[0] / count[0]
      out[1] = sum[1] / count[1]
      out[2] = sum[2] / count[2]
    }

    /** Демозаик Malvar–He–Cutler (2004): линейная интерполяция с поправкой по градиенту. */
    fun mhc(x: Int, y: Int, out: DoubleArray) {
      val c = colorAt(x, y)
      val s0 = s(x, y)
      val red = CfaColor.RED.ordinal
      val green = CfaColor.GREEN.ordinal
      val blue = CfaColor.BLUE.ordinal
      if (c == green) {
        val diag = s(x - 1, y - 1) + s(x + 1, y - 1) + s(x - 1, y + 1) + s(x + 1, y + 1)
        val hor = (5 * s0 + 4 * (s(x - 1, y) + s(x + 1, y)) - (s(x - 2, y) + s(x + 2, y)) - diag +
          0.5 * (s(x, y - 2) + s(x, y + 2))) / 8
        val ver = (5 * s0 + 4 * (s(x, y - 1) + s(x, y + 1)) - (s(x, y - 2) + s(x, y + 2)) - diag +
          0.5 * (s(x - 2, y) + s(x + 2, y))) / 8
        val redIsHorizontal = colorAt(x + 1, y) == red
        out[red] = if (redIsHorizontal) hor else ver
        out[blue] = if (redIsHorizontal) ver else hor
        out[green] = s0
      } else {
        val cross = s(x - 1, y) + s(x + 1, y) + s(x, y - 1) + s(x, y + 1)
        val far = s(x - 2, y) + s(x + 2, y) + s(x, y - 2) + s(x, y + 2)
        val diag = s(x - 1, y - 1) + s(x + 1, y - 1) + s(x - 1, y + 1) + s(x + 1, y + 1)
        out[c] = s0
        out[green] = (4 * s0 + 2 * cross - far) / 8
        out[if (c == red) blue else red] = (6 * s0 + 2 * diag - 1.5 * far) / 8
      }
      for (i in 0..2) if (out[i] < 0) out[i] = 0.0
    }
  }

  /** Баланс белого → матрица → профиль → 8 бит. Без состояния между пикселями, потокобезопасен. */
  private class Finisher(pipeline: ColorPipeline, private val o: DevelopOptions) {
    private val m = pipeline.srgbFromCamera.m
    private val wb = pipeline.wbGains
    private val p = o.profile
    private val lut = p.buildLut(o.exposureEv)
    private val lutScale = (Profile.LUT_SIZE - 1) / Profile.LUT_RANGE
    private val grainAmp = p.grain * 255

    fun pixel(cam: DoubleArray, x: Int, y: Int): Int {
      // Множители нормированы к минимуму 1, поэтому пересвет после обрезки до 1 — белый, а не розовый.
      val r = minOf(cam[0] * wb[0], 1.0)
      val g = minOf(cam[1] * wb[1], 1.0)
      val b = minOf(cam[2] * wb[2], 1.0)
      var sr = (m[0] * r + m[1] * g + m[2] * b) * p.warmR
      var sg = m[3] * r + m[4] * g + m[5] * b
      var sb = (m[6] * r + m[7] * g + m[8] * b) * p.warmB
      if (p.monochrome) {
        val l = 0.30 * sr + 0.59 * sg + 0.11 * sb
        sr = l; sg = l; sb = l
      } else if (p.saturation != 1.0) {
        val l = 0.2126 * sr + 0.7152 * sg + 0.0722 * sb
        sr = l + (sr - l) * p.saturation
        sg = l + (sg - l) * p.saturation
        sb = l + (sb - l) * p.saturation
      }
      var or = lut[index(sr)]
      var og = lut[index(sg)]
      var ob = lut[index(sb)]
      if (grainAmp > 0) {
        val n = noise(x, y, o.grainSeed)
        // Зерно сильнее в полутонах, слабее в глубоких тенях и светах — как у плёнки.
        val e = (or + og + ob) / (3 * 255.0)
        val d = (n * grainAmp * 4 * e * (1 - e)).toInt()
        or = (or + d).coerceIn(0, 255)
        og = (og + d).coerceIn(0, 255)
        ob = (ob + d).coerceIn(0, 255)
      }
      return (0xFF shl 24) or (or shl 16) or (og shl 8) or ob
    }

    private fun index(linear: Double): Int {
      val i = (linear * lutScale + 0.5).toInt()
      return if (i < 0) 0 else if (i >= Profile.LUT_SIZE) Profile.LUT_SIZE - 1 else i
    }

    /** Детерминированный шум −0.5..0.5 (хеш координат), примерно гауссов за счёт суммы двух. */
    private fun noise(x: Int, y: Int, seed: Int): Double {
      var h = x * 374761393 + y * 668265263 + seed * 1442695041
      h = (h xor (h ushr 13)) * 1274126177
      h = h xor (h ushr 16)
      val a = (h and 0xFFFF) / 65535.0
      val b = ((h ushr 16) and 0xFFFF) / 65535.0
      return (a + b) / 2 - 0.5
    }
  }
}
