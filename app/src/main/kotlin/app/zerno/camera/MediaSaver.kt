package app.zerno.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream

/** Сохранение в галерею: Pictures/Zerno. Разрешение на запись не нужно (Android 10+). */
object MediaSaver {
  private val folder = Environment.DIRECTORY_PICTURES + "/Zerno"

  fun saveDng(context: Context, name: String, write: (OutputStream) -> Unit): Uri =
    save(context, "$name.dng", "image/x-adobe-dng", write)

  fun saveJpeg(context: Context, name: String, bitmap: Bitmap): Uri =
    save(context, "$name.jpg", "image/jpeg") { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

  private fun save(context: Context, fileName: String, mime: String, write: (OutputStream) -> Unit): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
      put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
      put(MediaStore.Images.Media.MIME_TYPE, mime)
      put(MediaStore.Images.Media.RELATIVE_PATH, folder)
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
    } catch (e: Exception) {
      resolver.delete(uri, null, null)
      throw e
    }
  }
}
