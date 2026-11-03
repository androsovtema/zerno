package app.zerno.raw

import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Маленькая синтетическая сцена «закат над холмами» в виде RAW. Пока нет своего снимка,
 * лента профилей показывает её, проявленную каждым профилем: видно, что делает профиль
 * с небом, светом, тенями и насыщенностью.
 */
object SwatchScene {
  private const val BLACK = 64f
  private const val WHITE = 1023f

  fun raw(width: Int = 160, height: Int = 120): RawImage {
    val data = ShortArray(width * height)
    val rgb = FloatArray(3)
    for (y in 0 until height) for (x in 0 until width) {
      color(x / width.toFloat(), y / height.toFloat(), rgb)
      val c = Cfa.RGGB.colorAt((y and 1) * 2 + (x and 1))
      val v = when (c) { CfaColor.RED -> rgb[0]; CfaColor.GREEN -> rgb[1]; CfaColor.BLUE -> rgb[2] }
      data[y * width + x] = (BLACK + v.coerceIn(0f, 1f) * (WHITE - BLACK) + 0.5f).toInt().toShort()
    }
    return RawImage(width, height, data, Cfa.RGGB, FloatArray(4) { BLACK }, WHITE)
  }

  /** Линейный свет сцены в точке (u, v), 0..1. */
  private fun color(u: Float, v: Float, out: FloatArray) {
    val horizon = 0.60f + 0.05f * sin(u * 7.5f) + 0.03f * sin(u * 17f + 1.3f)
    // Небо: сверху сине-сиреневое, к горизонту тёплое; солнце с ореолом.
    val ts = (v / horizon).coerceIn(0f, 1f)
    val d = sqrt((u - 0.66f) * (u - 0.66f) + (v - 0.47f) * (v - 0.47f) * 1.8f)
    val sun = smooth(0.075f, 0.06f, d) + exp(-(d - 0.07f).coerceAtLeast(0f) * 14f) * 0.55f
    // Солнце тёплое и без пересвета каналов (иначе белый диск на тёплом небе кажется голубым).
    val sky0 = lerp(0.07f, 0.62f, ts * ts) + sun * 0.34f
    val sky1 = lerp(0.09f, 0.34f, ts * ts) + sun * 0.36f
    val sky2 = lerp(0.20f, 0.14f, ts) + sun * 0.22f
    // Холмы в тени и тёплая трава на переднем плане.
    val tg = ((v - horizon) / (1f - horizon)).coerceIn(0f, 1f)
    val g0 = lerp(0.025f, 0.16f, tg)
    val g1 = lerp(0.035f, 0.10f, tg)
    val g2 = lerp(0.03f, 0.035f, tg)
    // Мягкая граница: резкий край на мозаике Байера дал бы цветную «молнию» после демозаика.
    val k = smooth(horizon - 0.012f, horizon + 0.012f, v)
    out[0] = lerp(sky0, g0, k)
    out[1] = lerp(sky1, g1, k)
    out[2] = lerp(sky2, g2, k)
  }

  /** Плавная ступенька от 0 (x ≤ a) к 1 (x ≥ b); при a > b — наоборот. */
  private fun smooth(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3 - 2 * t)
  }

  private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

  /** Проявить сцену профилем (без матрицы и баланса белого: данные уже в линейном sRGB). */
  fun develop(profile: Profile, width: Int = 160, height: Int = 120): DevelopedImage {
    val pipeline = ColorPipelineResolver.resolve(ColorMetadata(null, null, null, emptyList()), ColorStrategy.NO_MATRIX)
    return RawDeveloper.develop(raw(width, height), pipeline, null, DevelopOptions(profile, 0.6, 1))
  }
}
