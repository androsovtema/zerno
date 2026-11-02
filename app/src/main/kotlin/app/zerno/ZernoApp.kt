package app.zerno

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class ZernoApp : Application() {
  override fun onCreate() {
    super.onCreate()
    CrashReporter.install(this)
    EventLog.attach(File(filesDir, "events.log"))
    EventLog.add("Запуск ${appVersion(this)} на ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
  }
}

fun appVersion(context: Context): String = runCatching {
  context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"

/**
 * Журнал событий: последние [KEEP] строк, в памяти и в файле. Файл переживает перезапуск —
 * иначе журнал, отправленный после повторного запуска, не содержит самих снимков.
 */
object EventLog {
  private const val KEEP = 600
  private val lines = ArrayDeque<String>()
  private val time = SimpleDateFormat("dd.MM HH:mm:ss.SSS", Locale.US)
  private var file: File? = null

  @Synchronized
  fun attach(target: File) {
    file = target
    runCatching {
      if (target.exists()) {
        val previous = target.readLines().takeLast(KEEP)
        lines.clear()
        lines.addAll(previous)
        target.writeText(previous.joinToString("\n", postfix = if (previous.isEmpty()) "" else "\n"))
      }
      append("— — —")
    }.onFailure { Log.w("Zerno", "Журнал на диске недоступен", it) }
  }

  @Synchronized
  fun add(message: String, error: Throwable? = null) {
    val line = "${time.format(Date())} $message" + (error?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: "")
    Log.i("Zerno", line, error)
    append(line)
  }

  private fun append(line: String) {
    lines.addLast(line)
    while (lines.size > KEEP) lines.removeFirst()
    file?.let { f -> runCatching { f.appendText(line + "\n") } }
  }

  @Synchronized
  fun dump(): String = lines.joinToString("\n")
}

/** Сохраняет падение в файл и показывает его при следующем запуске. */
object CrashReporter {
  private const val FILE = "last_crash.txt"

  fun install(context: Context) {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
      runCatching {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
          appendLine("Падение Zerno ${appVersion(context)}, ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
          appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.DISPLAY}")
          appendLine("Поток: ${thread.name}")
          appendLine()
          appendLine(trace)
          appendLine("Журнал:")
          appendLine(EventLog.dump())
        }
        File(context.filesDir, FILE).writeText(text)
      }
      previous?.uncaughtException(thread, error)
    }
  }

  fun pending(context: Context): String? = File(context.filesDir, FILE).takeIf { it.exists() }?.readText()

  fun clear(context: Context) {
    File(context.filesDir, FILE).delete()
  }
}
