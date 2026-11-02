package app.zerno.raw

/** Цвет фильтра в ячейке Байера. */
enum class CfaColor { RED, GREEN, BLUE }

/**
 * Раскладка фильтра Байера: цвета ячеек (0,0), (0,1), (1,0), (1,1) — строка, столбец.
 * Порядок констант совпадает с CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT (0..3).
 */
enum class Cfa(private val colors: Array<CfaColor>) {
  RGGB(arrayOf(CfaColor.RED, CfaColor.GREEN, CfaColor.GREEN, CfaColor.BLUE)),
  GRBG(arrayOf(CfaColor.GREEN, CfaColor.RED, CfaColor.BLUE, CfaColor.GREEN)),
  GBRG(arrayOf(CfaColor.GREEN, CfaColor.BLUE, CfaColor.RED, CfaColor.GREEN)),
  BGGR(arrayOf(CfaColor.BLUE, CfaColor.GREEN, CfaColor.GREEN, CfaColor.RED));

  /** Цвет ячейки по индексу 0..3 = row * 2 + col. */
  fun colorAt(index: Int): CfaColor = colors[index]

  /**
   * Канал карты затенения объектива (как в RggbChannelVector): 0 = R, 1 = G в строке с R,
   * 2 = G в строке с B, 3 = B.
   */
  fun shadingChannel(index: Int): Int = when (colors[index]) {
    CfaColor.RED -> 0
    CfaColor.BLUE -> 3
    CfaColor.GREEN -> {
      val row = index / 2
      val rowHasRed = colors[row * 2] == CfaColor.RED || colors[row * 2 + 1] == CfaColor.RED
      if (rowHasRed) 1 else 2
    }
  }

  companion object {
    fun fromCamera2(value: Int): Cfa? = entries.getOrNull(value)
  }
}

/**
 * Сырой кадр с сенсора. [data] — 16-битные значения без знака, построчно, без отступов.
 * [blackLevel] — уровень чёрного для 4 ячеек 2×2 (индекс row * 2 + col).
 */
class RawImage(
  val width: Int,
  val height: Int,
  val data: ShortArray,
  val cfa: Cfa,
  val blackLevel: FloatArray,
  val whiteLevel: Float,
) {
  init {
    require(data.size >= width * height) { "data is smaller than width*height" }
    require(blackLevel.size == 4) { "blackLevel needs 4 values" }
    require(whiteLevel > blackLevel.max()) { "whiteLevel must be above black level" }
  }

  fun valueAt(x: Int, y: Int): Int = data[y * width + x].toInt() and 0xFFFF
}

/**
 * Карта затенения объектива (виньетирование и цветовой сдвиг по краям).
 * [gains] — rows × cols точек по 4 канала (R, Gчёт, Gнечёт, B), растянутых на весь кадр.
 */
class ShadingMap(val rows: Int, val cols: Int, val gains: FloatArray) {
  init {
    require(rows >= 2 && cols >= 2) { "shading map must be at least 2x2" }
    require(gains.size == rows * cols * 4) { "gains size mismatch" }
  }

  /** Билинейная интерполяция усиления канала в точке (u, v), где u и v в диапазоне 0..1. */
  fun gain(channel: Int, u: Float, v: Float): Float {
    val fx = (u * (cols - 1)).coerceIn(0f, (cols - 1).toFloat())
    val fy = (v * (rows - 1)).coerceIn(0f, (rows - 1).toFloat())
    val x0 = fx.toInt().coerceAtMost(cols - 2)
    val y0 = fy.toInt().coerceAtMost(rows - 2)
    val tx = fx - x0
    val ty = fy - y0
    fun g(x: Int, y: Int) = gains[(y * cols + x) * 4 + channel]
    val top = g(x0, y0) * (1 - tx) + g(x0 + 1, y0) * tx
    val bottom = g(x0, y0 + 1) * (1 - tx) + g(x0 + 1, y0 + 1) * tx
    return top * (1 - ty) + bottom * ty
  }
}
