package app.zerno.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import app.zerno.raw.RawImage
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Сохранение в галерею: Pictures/Zerno. Разрешение на запись не нужно (Android 10+). */
object MediaSaver {
  private val folder = Environment.DIRECTORY_PICTURES + "/Zerno"

  fun saveJpeg(context: Context, name: String, bitmap: Bitmap, takenAt: Long): Uri =
    save(context, "$name.jpg", "image/jpeg", takenAt) { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

  /** DNG из уже скопированных данных: буфер камеры к этому моменту освобождён. */
  fun saveDng(
    context: Context,
    name: String,
    raw: RawImage,
    characteristics: CameraCharacteristics,
    result: CaptureResult,
    exifOrientation: Int,
    takenAt: Long,
  ): Uri = save(context, "$name.dng", "image/x-adobe-dng", takenAt) { out ->
    // Прямой буфер живёт вне кучи Java — не давит на память приложения.
    val buffer = ByteBuffer.allocateDirect(raw.width * raw.height * 2).order(ByteOrder.nativeOrder())
    buffer.asShortBuffer().put(raw.data, 0, raw.width * raw.height)
    DngCreator(characteristics, result).use { dng ->
      dng.setOrientation(exifOrientation)
      dng.writeByteBuffer(out, Size(raw.width, raw.height), buffer, 0)
    }
  }

  private fun save(context: Context, fileName: String, mime: String, takenAt: Long, write: (OutputStream) -> Unit): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
      put(MediaStore.Images.Media.MIME_TYPE, mime)
      put(MediaStore.Images.Media.RELATIVE_PATH, folder)
      put(MediaStore.Images.Media.DATE_TAKEN, takenAt)
      put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val uri = resolver.insert(collection, values) ?: error("MediaStore не создал файл $fileName")
    try {
      resolver.openOutputStream(uri)?.use(write) ?: error("Не открылся поток для $fileName")
      values.clear()
      values.put(MediaStore.Images.Media.IS_PENDING, 0)
      resolver.update(uri, values, null, null)
      return uri
    } catch (e: Throwable) {
      runCatching { resolver.delete(uri, null, null) }
      throw e
    }
  }
}
