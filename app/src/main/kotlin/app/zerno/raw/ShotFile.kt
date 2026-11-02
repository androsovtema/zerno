package app.zerno.raw

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Всё, что нужно, чтобы перепроявить кадр: сырые данные и цветовые метаданные. */
class Shot(
  val name: String,
  val timestamp: Long,
  val raw: RawImage,
  val meta: ColorMetadata,
  val shading: ShadingMap?,
  /** Поворот по часовой стрелке до вертикали. */
  val rotation: Int,
  val captureInfo: String,
)

/**
 * Компактный файл снимка (.zraw): заголовок + метаданные + сжатые Deflate 16-битные данные.
 * Формат свой и версионный: при изменении увеличиваем VERSION и не читаем старые файлы.
 */
object ShotFile {
  private const val MAGIC = 0x5A524157 // "ZRAW"
  private const val VERSION = 1

  fun write(shot: Shot, file: File) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    DataOutputStream(BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { out ->
      out.writeInt(MAGIC)
      out.writeInt(VERSION)
      out.writeUTF(shot.name)
      out.writeLong(shot.timestamp)
      out.writeInt(shot.rotation)
      writeLongString(out, shot.captureInfo)

      val r = shot.raw
      out.writeInt(r.width); out.writeInt(r.height); out.writeInt(r.cfa.ordinal)
      r.blackLevel.forEach { out.writeFloat(it) }
      out.writeFloat(r.whiteLevel)

      writeDoubles(out, shot.meta.neutral)
      writeDoubles(out, shot.meta.ispGains)
      writeDoubles(out, shot.meta.ispMatrix?.m)
      out.writeInt(shot.meta.calibrations.size)
      shot.meta.calibrations.forEach { c ->
        out.writeInt(c.illuminant)
        writeDoubles(out, c.colorMatrix.m)
        writeDoubles(out, c.calibration?.m)
        writeDoubles(out, c.forwardMatrix?.m)
      }
      val s = shot.shading
      if (s == null) out.writeInt(0) else {
        out.writeInt(s.rows); out.writeInt(s.cols)
        s.gains.forEach { out.writeFloat(it) }
      }
      out.flush()

      // Сами пиксели: little-endian, сжатие с приоритетом скорости.
      val deflater = Deflater(Deflater.BEST_SPEED)
      val z = DeflaterOutputStream(out, deflater, 1 shl 16)
      val chunk = ByteBuffer.allocate(1 shl 16).order(ByteOrder.LITTLE_ENDIAN)
      var i = 0
      val total = r.width * r.height
      while (i < total) {
        chunk.clear()
        val n = minOf(chunk.capacity() / 2, total - i)
        chunk.asShortBuffer().put(r.data, i, n)
        z.write(chunk.array(), 0, n * 2)
        i += n
      }
      z.finish()
      deflater.end()
    }
    if (!tmp.renameTo(file)) {
      tmp.delete()
      throw IOException("Не удалось сохранить ${file.name}")
    }
  }

  fun read(file: File): Shot {
    DataInputStream(BufferedInputStream(file.inputStream(), 1 shl 16)).use { input ->
      if (input.readInt() != MAGIC) throw IOException("Не файл снимка Zerno")
      val version = input.readInt()
      if (version != VERSION) throw IOException("Неподдерживаемая версия файла: $version")
      val name = input.readUTF()
      val timestamp = input.readLong()
      val rotation = input.readInt()
      val info = readLongString(input)

      val w = input.readInt(); val h = input.readInt()
      val cfa = Cfa.entries[input.readInt()]
      val black = FloatArray(4) { input.readFloat() }
      val white = input.readFloat()

      val neutral = readDoubles(input)
      val gains = readDoubles(input)
      val isp = readDoubles(input)?.let { Mat3(it) }
      val calibrations = List(input.readInt()) {
        SensorCalibration(
          input.readInt(),
          Mat3(readDoubles(input) ?: throw IOException("Повреждена калибровка")),
          readDoubles(input)?.let { Mat3(it) },
          readDoubles(input)?.let { Mat3(it) },
        )
      }
      val rows = input.readInt()
      val shading = if (rows == 0) null else {
        val cols = input.readInt()
        ShadingMap(rows, cols, FloatArray(rows * cols * 4) { input.readFloat() })
      }

      val data = ShortArray(w * h)
      val z = InflaterInputStream(input, java.util.zip.Inflater(), 1 shl 16)
      val bytes = ByteArray(1 shl 16)
      val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
      var i = 0
      var pending = 0
      while (i < data.size) {
        val read = z.read(bytes, pending, bytes.size - pending)
        if (read < 0) throw IOException("Файл снимка обрезан")
        val avail = pending + read
        val shorts = avail / 2
        buf.clear()
        val n = minOf(shorts, data.size - i)
        buf.asShortBuffer().get(data, i, n)
        i += n
        pending = avail - n * 2
        if (pending > 0) System.arraycopy(bytes, n * 2, bytes, 0, pending)
      }
      val raw = RawImage(w, h, data, cfa, black, white)
      return Shot(name, timestamp, raw, ColorMetadata(neutral, gains, isp, calibrations), shading, rotation, info)
    }
  }

  private fun writeDoubles(out: DataOutputStream, v: DoubleArray?) {
    if (v == null) { out.writeInt(-1); return }
    out.writeInt(v.size)
    v.forEach { out.writeDouble(it) }
  }

  private fun readDoubles(input: DataInputStream): DoubleArray? {
    val n = input.readInt()
    return if (n < 0) null else DoubleArray(n) { input.readDouble() }
  }

  private fun writeLongString(out: DataOutputStream, s: String) {
    val b = s.toByteArray(Charsets.UTF_8)
    out.writeInt(b.size); out.write(b)
  }

  private fun readLongString(input: DataInputStream): String {
    val b = ByteArray(input.readInt())
    input.readFully(b)
    return String(b, Charsets.UTF_8)
  }
}
