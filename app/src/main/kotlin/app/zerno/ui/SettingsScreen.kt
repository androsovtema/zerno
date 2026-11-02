package app.zerno.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.appVersion
import app.zerno.ui.theme.Muted

@Composable
fun SettingsScreen(vm: ZernoViewModel, onBack: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var showReport by rememberSaveable { mutableStateOf(false) }

  Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack) { Text("← Камера", color = Color.White) }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
      ToggleRow(
        "Сохранять DNG",
        "Дублировать каждый кадр файлом RAW в галерее — для Lightroom и сравнения. Файлы большие.",
        state.saveDng, vm::setSaveDng,
      )
      ToggleRow(
        "Коррекция виньетирования",
        "Выравнивает яркость и цвет по краям кадра по данным объектива.",
        state.shadingCorrection, vm::setShading,
      )
      HorizontalDivider(Modifier.padding(vertical = 12.dp))

      SectionTitle("Для тестирования")
      Text("Если что-то работает не так — отправьте журнал или отчёт о камерах разработчику.",
        color = Muted, fontSize = 13.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedButton(onClick = { share(context, vm.eventLog(), "Журнал Zerno") }) { Text("Отправить журнал") }
        OutlinedButton(onClick = { showReport = !showReport }) { Text("Диагностика камер") }
      }
      val report = state.scan?.report
      if (showReport) {
        if (report == null) {
          Text("Опрос камер ещё идёт…", color = Muted)
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { share(context, report, "Отчёт о камерах") }) { Text("Отправить") }
            TextButton(onClick = { copy(context, report) }) { Text("Копировать") }
          }
          SelectionContainer {
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xFFC7C7CC))
          }
        }
      }
      HorizontalDivider(Modifier.padding(vertical = 12.dp))
      SectionTitle("О приложении")
      Text(
        "Zerno ${appVersion(context)} — камера, которая снимает RAW и проявляет его своими профилями, " +
          "без HDR и «улучшений» ИИ. Профиль выбирается перед съёмкой; любой из последних кадров можно " +
          "перепроявить другим профилем: нажмите на миниатюру.",
        color = Muted, fontSize = 13.sp,
      )
      Spacer(Modifier.height(32.dp))
    }
  }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, color = Color.White)
      Text(subtitle, color = Muted, fontSize = 12.sp)
    }
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
