package app.zerno.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/**
 * Плёночное зерно интерфейса: бесшовная плитка шума, светлые и тёмные «зёрна» с прозрачностью.
 * Два масштаба (мелкое + крупное), как у настоящей плёнки. Генерируется один раз при запуске.
 */
object GrainNoise {
  const val SIZE = 192

  /** Нормированный шум (ст. откл. 1), бесшовный по краям. Чистый Kotlin — тестируется на JVM. */
  fun field(size: Int = SIZE, seed: Long = 1919): FloatArray {
    val rnd = java.util.Random(seed)
    val white = FloatArray(size * size) { rnd.nextGaussian().toFloat() }
    val fine = normalize(blurWrapped(white, size, 1))
    val coarse = normalize(blurWrapped(blurWrapped(white, size, 2), size, 2))
    val mixed = FloatArray(size * size) { 0.62f * fine[it] + 0.38f * coarse[it] }
    return normalize(mixed)
  }

  /** ARGB-пиксели: светлое зерно — белое, тёмное — чёрное, непрозрачность ∝ |шум|. */
  fun argb(field: FloatArray, gain: Float = 0.42f): IntArray = IntArray(field.size) { i ->
    val n = field[i]
    val a = (abs(n) * gain).coerceAtMost(1f)
    val alpha = (a * 255).toInt()
    if (n >= 0) (alpha shl 24) or 0xFFF6EA else (alpha shl 24)
  }

  val bitmap: Bitmap by lazy {
    Bitmap.createBitmap(argb(field()), SIZE, SIZE, Bitmap.Config.ARGB_8888)
  }

  private fun normalize(a: FloatArray): FloatArray {
    val mean = a.average().toFloat()
    val sd = sqrt(a.sumOf { ((it - mean) * (it - mean)).toDouble() } / a.size).toFloat().coerceAtLeast(1e-6f)
    return FloatArray(a.size) { (a[it] - mean) / sd }
  }

  /** Биномиальное размытие радиуса r с переносом через край — плитка остаётся бесшовной. */
  private fun blurWrapped(src: FloatArray, size: Int, r: Int): FloatArray {
    val k = FloatArray(2 * r + 1) { binom(2 * r, it).toFloat() }
    val sum = k.sum()
    val tmp = FloatArray(src.size)
    val out = FloatArray(src.size)
    for (y in 0 until size) for (x in 0 until size) {
      var s = 0f
      for (i in -r..r) s += src[y * size + Math.floorMod(x + i, size)] * k[i + r]
      tmp[y * size + x] = s / sum
    }
    for (y in 0 until size) for (x in 0 until size) {
      var s = 0f
      for (i in -r..r) s += tmp[Math.floorMod(y + i, size) * size + x] * k[i + r]
      out[y * size + x] = s / sum
    }
    return out
  }

  private fun binom(n: Int, k: Int): Long {
    var r = 1L
    for (i in 1..k) r = r * (n - k + i) / i
    return r
  }
}

/** Сила зерна по умолчанию на фоне и панелях. */
const val GRAIN_ALPHA = 0.095f

/** Размер зерна: пикселей экрана на пиксель плитки — чуть крупнее физического пикселя. */
private fun grainScale(density: Float) = (density / 2.2f).coerceAtLeast(1f)

/** false — зерно не анимируется (скриншот-тесты: бесконечная анимация не даёт дождаться покоя). */
val LocalLiveGrain = staticCompositionLocalOf { true }

/** Кадр «живого» зерна: пока что-то проявляется, зерно шевелится, как в проекторе (12 кадров/с). */
@Composable
fun rememberGrainFrame(running: Boolean): State<Int> {
  val frame = remember { mutableIntStateOf(0) }
  val live = LocalLiveGrain.current
  LaunchedEffect(running, live) {
    while (running && live) {
      delay(83)
      frame.intValue++
    }
  }
  return frame
}

private class GrainPainter(private val density: Float) {
  private val shader = BitmapShader(GrainNoise.bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
  private val matrix = Matrix()
  val brush = ShaderBrush(shader)

  fun update(frame: Int) {
    val s = grainScale(density)
    matrix.setScale(s, s)
    // Сдвиги взаимно простые с размером плитки — кадры не повторяются подряд.
    matrix.postTranslate((frame * 73 % GrainNoise.SIZE) * s, (frame * 131 % GrainNoise.SIZE) * s)
    shader.setLocalMatrix(matrix)
  }
}

private fun DrawScope.drawGrain(painter: GrainPainter, shape: Shape, alpha: Float, frame: Int) {
  painter.update(frame)
  if (shape == RectangleShape) {
    drawRect(painter.brush, alpha = alpha)
  } else {
    drawOutline(shape.createOutline(size, layoutDirection, this), painter.brush, alpha = alpha)
  }
}

/** Зерно поверх содержимого (для кнопок, панелей, знака). */
fun Modifier.grain(alpha: Float = GRAIN_ALPHA, shape: Shape = RectangleShape, frame: State<Int>? = null): Modifier =
  drawWithCache {
    val painter = GrainPainter(density)
    onDrawWithContent {
      drawContent()
      drawGrain(painter, shape, alpha, frame?.value ?: 0)
    }
  }

/** Подложка с зерном: заливка (цвет или градиент), поверх неё зерно, затем содержимое без зерна. */
fun Modifier.grainyFill(
  brush: Brush,
  shape: Shape = RectangleShape,
  alpha: Float = GRAIN_ALPHA,
  frame: State<Int>? = null,
): Modifier = drawWithCache {
  val painter = GrainPainter(density)
  val outline = shape.createOutline(size, layoutDirection, this)
  onDrawBehind {
    if (shape == RectangleShape) drawRect(brush) else drawOutline(outline, brush)
    drawGrain(painter, shape, alpha, frame?.value ?: 0)
  }
}

fun Modifier.grainyFill(color: Color, shape: Shape = RectangleShape, alpha: Float = GRAIN_ALPHA, frame: State<Int>? = null) =
  grainyFill(androidx.compose.ui.graphics.SolidColor(color), shape, alpha, frame)

/**
 * Фон экранов: вертикальный градиент от тёплого отсвета к почти чёрному, мягкое свечение сверху
 * и зерно. Используется на всех экранах — зерно проходит через всё приложение.
 */
fun Modifier.zernoBackground(frame: State<Int>? = null): Modifier = drawWithCache {
  val painter = GrainPainter(density)
  val vertical = Brush.verticalGradient(listOf(Umber, Ink, InkDeep))
  val glow = Brush.radialGradient(
    listOf(Amber.copy(alpha = 0.07f), Color.Transparent),
    center = Offset(size.width * 0.35f, 0f),
    radius = size.maxDimension * 0.6f,
  )
  onDrawBehind {
    drawRect(vertical)
    drawRect(glow)
    drawGrain(painter, RectangleShape, GRAIN_ALPHA, frame?.value ?: 0)
  }
}
