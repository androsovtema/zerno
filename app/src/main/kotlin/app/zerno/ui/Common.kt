package app.zerno.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent

fun copy(context: Context, text: String) {
  context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Zerno", text))
}

fun share(context: Context, text: String, title: String = "Отправить") {
  val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
  context.startActivity(Intent.createChooser(send, title))
}
