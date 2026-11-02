package app.zerno.raw

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Минимальный читатель DNG для проверки проявки на настоящих снимках телефонов.
 * Понимает то, что пишет Android DngCreator: несжатый CFA в IFD0, цветовые теги, GainMap.
 * Только для тестов и отладки — в приложении не используется.
 */
object DngReader {

  private class Entry(val type: Int, val count: Int, val valueOffset: Int, val inline: ByteBuffer)

  fun read(file: File): Shot {
    val bytes = file.readBytes()
    val order = when (String(bytes, 0, 2)) {
      "II" -> ByteOrder.LITTLE_ENDIAN
      "MM" -> ByteOrder.BIG_ENDIAN
      else -> throw IOException("Не TIFF")
    }
    val buf = ByteBuffer.wrap(bytes).order(order)
    val ifd = parseIfd(buf, buf.getInt(4))

    fun ints(tag: Int): IntArray? = ifd[tag]?.let { e -> IntArray(e.count) { i -> intAt(buf, e, i) } }
    fun doubles(tag: Int): DoubleArray? = ifd[tag]?.let { e -> DoubleArray(e.count) { i -> numberAt(buf, e, i) } }

    val width = ints(256)!![0]
    val height = ints(257)!![0]
    check(ints(258)!![0] == 16) { "Нужен 16-битный DNG" }
    check(ints(259)!![0] == 1) { "Сжатые DNG не поддерживаются" }
    val photometric = ints(262)!![0]
    check(photometric == 32803) { "IFD0 не CFA (photometric $photometric) — вероятно, RAW лежит в SubIFD" }

    val pattern = ifd[33422]!!.let { e -> IntArray(4) { byteAt(buf, e, it) } }
    val cfa = when (pattern.joinToString("")) {
      "0112" -> Cfa.RGGB
      "1021" -> Cfa.GRBG
      "1201" -> Cfa.GBRG
      "2110" -> Cfa.BGGR
      else -> throw IOException("Неизвестный CFA ${pattern.joinToString()}")
    }

    val offsets = ints(273)!!
    val counts = ints(279)!!
    val data = ShortArray(width * height)
    var pos = 0
    for (s in offsets.indices) {
      val sb = ByteBuffer.wrap(bytes, offsets[s], counts[s]).slice().order(order).asShortBuffer()
      val n = minOf(sb.remaining(), data.size - pos)
      sb.get(data, pos, n)
      pos += n
    }

    val blackRaw = doubles(50714) ?: doubleArrayOf(0.0)
    val black = FloatArray(4) { blackRaw[if (blackRaw.size == 4) it else 0].toFloat() }
    val white = (doubles(50717)?.get(0) ?: 1023.0).toFloat()

    fun mat(tag: Int) = doubles(tag)?.takeIf { it.size == 9 }?.let { Mat3(it) }
    val calibrations = listOfNotNull(
      mat(50721)?.let { SensorCalibration(ints(50778)?.get(0) ?: Illuminant.STANDARD_A, it, mat(50723), mat(50964)) },
      mat(50722)?.let { SensorCalibration(ints(50779)?.get(0) ?: Illuminant.D65, it, mat(50724), mat(50965)) },
    )
    val neutral = doubles(50728)
    val shading = ifd[51009]?.let { gainMap(bytes, it.valueOffset, it.count, cfa) }
    val orientation = when (ints(274)?.get(0)) { 6 -> 90; 3 -> 180; 8 -> 270; else -> 0 }

    val raw = RawImage(width, height, data, cfa, black, white)
    return Shot(file.nameWithoutExtension, file.lastModified(), raw,
      ColorMetadata(neutral, null, null, calibrations), shading, orientation, "DNG ${file.name}")
  }

  /** OpcodeList2 → карта затенения (DngCreator пишет 4 GainMap, по одной на ячейку CFA). */
  private fun gainMap(bytes: ByteArray, offset: Int, length: Int, cfa: Cfa): ShadingMap? {
    val b = ByteBuffer.wrap(bytes, offset, length).slice().order(ByteOrder.BIG_ENDIAN)
    val n = b.int
    var rows = 0; var cols = 0
    var gains: FloatArray? = null
    repeat(n) {
      val id = b.int; b.int; b.int
      val size = b.int
      val start = b.position()
      if (id == 9) {
        val top = b.int; val left = b.int; b.int; b.int // bottom, right
        b.int; b.int // plane, planes
        val rowPitch = b.int; val colPitch = b.int
        val v = b.int; val h = b.int
        repeat(4) { b.double } // spacing, origin
        b.int // map planes
        if (rowPitch == 2 && colPitch == 2) {
          if (gains == null) { rows = v; cols = h; gains = FloatArray(v * h * 4) }
          val ch = cfa.shadingChannel((top and 1) * 2 + (left and 1))
          for (i in 0 until v * h) gains!![i * 4 + ch] = b.float
        }
      }
      b.position(start + size)
    }
    return gains?.takeIf { rows >= 2 && cols >= 2 }?.let { ShadingMap(rows, cols, it) }
  }

  private fun parseIfd(buf: ByteBuffer, offset: Int): Map<Int, Entry> {
    val n = buf.getShort(offset).toInt() and 0xFFFF
    val map = HashMap<Int, Entry>()
    for (i in 0 until n) {
      val p = offset + 2 + i * 12
      val tag = buf.getShort(p).toInt() and 0xFFFF
      val type = buf.getShort(p + 2).toInt() and 0xFFFF
      val count = buf.getInt(p + 4)
      val inline = buf.duplicate().order(buf.order()).position(p + 8) as ByteBuffer
      map[tag] = Entry(type, count, buf.getInt(p + 8), inline.slice().order(buf.order()))
    }
    return map
  }

  private fun typeSize(type: Int) = when (type) { 1, 2, 6, 7 -> 1; 3, 8 -> 2; 4, 9, 11 -> 4; 5, 10, 12 -> 8; else -> 1 }

  private fun at(buf: ByteBuffer, e: Entry, index: Int): Pair<ByteBuffer, Int> {
    val size = typeSize(e.type)
    return if (size * e.count <= 4) e.inline to index * size else buf to e.valueOffset + index * size
  }

  private fun byteAt(buf: ByteBuffer, e: Entry, i: Int): Int { val (b, p) = at(buf, e, i); return b.get(p).toInt() and 0xFF }

  private fun intAt(buf: ByteBuffer, e: Entry, i: Int): Int {
    val (b, p) = at(buf, e, i)
    return when (e.type) {
      1, 7 -> b.get(p).toInt() and 0xFF
      3 -> b.getShort(p).toInt() and 0xFFFF
      8 -> b.getShort(p).toInt()
      else -> b.getInt(p)
    }
  }

  private fun numberAt(buf: ByteBuffer, e: Entry, i: Int): Double {
    val (b, p) = at(buf, e, i)
    return when (e.type) {
      5 -> (b.getInt(p).toLong() and 0xFFFFFFFFL).toDouble() / (b.getInt(p + 4).toLong() and 0xFFFFFFFFL)
      10 -> b.getInt(p).toDouble() / b.getInt(p + 4)
      11 -> b.getFloat(p).toDouble()
      12 -> b.getDouble(p)
      else -> intAt(buf, e, i).toDouble()
    }
  }
}
