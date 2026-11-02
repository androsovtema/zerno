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
    EventLog.add("Запуск ${appVersion(this)} на ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
  }
}

fun appVersion(context: Context): String = runCatching {
  context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"

/** Журнал событий в памяти (последние 300 строк): уходит в отчёты об ошибках. */
object EventLog {
  private val lines = ArrayDeque<String>()
  private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

  @Synchronized
  fun add(message: String, error: Throwable? = null) {
    val line = "${time.format(Date())} $message" + (error?.let { " — ${it.javaClass.simpleName}: ${it.message}" } ?: "")
    Log.i("Zerno", line, error)
    lines.addLast(line)
    while (lines.size > 300) lines.removeFirst()
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
