package app.zerno.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun DiagnosticsScreen(vm: ZernoViewModel, onBack: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val report = state.scan?.report

  Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp)) {
    Text("Диагностика камер", style = MaterialTheme.typography.titleLarge)
    Text("Что телефон отдаёт сторонним приложениям. Отправьте отчёт разработчику.",
      style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
      Button(enabled = report != null, onClick = {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
        context.startActivity(Intent.createChooser(send, "Отправить отчёт"))
      }) { Text("Поделиться") }
      OutlinedButton(enabled = report != null, onClick = { copy(context, report.orEmpty()) }) { Text("Копировать") }
      OutlinedButton(onClick = onBack) { Text("Назад") }
    }
    if (report == null) {
      CircularProgressIndicator()
    } else {
      SelectionContainer(Modifier.verticalScroll(rememberScrollState())) {
        Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
      }
    }
  }
}
