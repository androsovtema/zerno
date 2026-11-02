package app.zerno

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import app.zerno.raw.Shot
import app.zerno.raw.ShotFile
import java.io.File

/** Запись в галерее приложения: миниатюра, ссылка на JPEG и (если ещё не удалён) RAW для перепроявки. */
data class ShotEntry(
  val name: String,
  val timestamp: Long,
  val thumb: File,
  val raw: File,
  val galleryUri: Uri?,
) {
  val canRedevelop: Boolean get() = raw.exists()
}

/**
 * Хранит последние снимки в памяти приложения (files/shots): .zraw для перепроявки,
 * .jpg-миниатюру и .uri со ссылкой на JPEG в галерее. Старые записи удаляются.
 */
class ShotRepository(context: Context) {
  private val dir = File(context.filesDir, "shots").apply { mkdirs() }

  fun save(shot: Shot, thumb: Bitmap, galleryUri: Uri?) {
    File(dir, "${shot.name}.thumb.jpg").outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    galleryUri?.let { File(dir, "${shot.name}.uri").writeText(it.toString()) }
    ShotFile.write(shot, File(dir, "${shot.name}.zraw"))
    prune()
  }

  fun list(): List<ShotEntry> =
    dir.listFiles { f -> f.name.endsWith(".thumb.jpg") }.orEmpty().map { thumb ->
      val name = thumb.name.removeSuffix(".thumb.jpg")
      val uri = File(dir, "$name.uri").takeIf { it.exists() }?.readText()?.let(Uri::parse)
      ShotEntry(name, thumb.lastModified(), thumb, File(dir, "$name.zraw"), uri)
    }.sortedByDescending { it.timestamp }

  fun load(name: String): Shot = ShotFile.read(File(dir, "$name.zraw"))

  fun thumb(entry: ShotEntry): Bitmap? = runCatching { BitmapFactory.decodeFile(entry.thumb.path) }.getOrNull()

  fun delete(name: String) {
    listOf(".thumb.jpg", ".uri", ".zraw").forEach { File(dir, name + it).delete() }
  }

  /** Не больше MAX_SHOTS записей и MAX_RAW_BYTES на RAW-файлы: лишнее удаляем с самых старых. */
  private fun prune() {
    val entries = list()
    entries.drop(MAX_SHOTS).forEach { delete(it.name) }
    var total = 0L
    entries.take(MAX_SHOTS).forEach { e ->
      total += e.raw.length()
      if (total > MAX_RAW_BYTES) e.raw.delete()
    }
  }

  companion object {
    const val MAX_SHOTS = 40
    const val MAX_RAW_BYTES = 1_500_000_000L
  }
}
