package app.zerno.ui

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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.appVersion
import app.zerno.ui.theme.Amber
import app.zerno.ui.theme.Cream
import app.zerno.ui.theme.Mono
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.zernoBackground

@Composable
fun SettingsScreen(vm: ZernoViewModel, onBack: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  SettingsContent(
    state = state,
    version = appVersion(context),
    onBack = onBack,
    onSaveDng = vm::setSaveDng,
    onShading = vm::setShading,
    onSendLog = { share(context, vm.eventLog(), "Журнал Зерна") },
    onShareReport = { share(context, it, "Отчёт о камерах") },
    onCopyReport = { copy(context, it) },
  )
}

/** Вёрстка настроек без ViewModel — для скриншот-тестов. */
@Composable
fun SettingsContent(
  state: UiState,
  version: String,
  onBack: () -> Unit,
  onSaveDng: (Boolean) -> Unit,
  onShading: (Boolean) -> Unit,
  onSendLog: () -> Unit,
  onShareReport: (String) -> Unit,
  onCopyReport: (String) -> Unit,
) {
  var showReport by rememberSaveable { mutableStateOf(false) }
  Column(Modifier.fillMaxSize().zernoBackground().safeDrawingPadding()) {
    ScreenBar(title = "Настройки", onBack = onBack, backLabel = "Камера")
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
      SectionTitle("Съёмка")
      Column(Modifier.panel().padding(horizontal = 16.dp, vertical = 4.dp)) {
        ToggleRow(
          "Сохранять DNG",
          "Дублировать каждый кадр файлом RAW в галерее — для Lightroom и сравнения. Файлы большие.",
          state.saveDng, onSaveDng,
        )
        ToggleRow(
          "Коррекция виньетирования",
          "Выравнивает яркость и цвет по краям кадра по данным объектива.",
          state.shadingCorrection, onShading,
        )
      }

      SectionTitle("Для тестирования")
      Text("Если что-то работает не так — отправьте журнал или отчёт о камерах разработчику.",
        color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
        GhostButton("Отправить журнал", onSendLog)
        GhostButton(if (showReport) "Скрыть отчёт" else "Диагностика камер", { showReport = !showReport })
      }
      val report = state.scan?.report
      if (showReport) {
        if (report == null) {
          EdgeText("Опрос камер ещё идёт…", color = Muted)
        } else {
          Row {
            EdgeButton("Отправить", { onShareReport(report) }, color = Amber)
            EdgeButton("Копировать", { onCopyReport(report) }, color = Amber)
          }
          SelectionContainer {
            Text(report, fontFamily = Mono, fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFFCDBFAE))
          }
        }
      }

      SectionTitle("О приложении")
      Column(Modifier.fillMaxWidth().panel().padding(18.dp)) {
        ZernoLogo(markSize = 28.dp, textSize = 28.sp)
        EdgeText("версия $version", color = Muted, modifier = Modifier.padding(top = 6.dp))
        Text(
          "Камера, которая снимает RAW и проявляет его своими профилями — без HDR и «улучшений» ИИ. " +
            "Профиль выбирается перед съёмкой; любой из последних кадров можно перепроявить: нажмите на отпечаток.",
          color = Cream, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 12.dp),
        )
        Text("Шрифты Prata и IBM Plex Mono — SIL Open Font License 1.1.",
          color = Muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp))
      }
      Spacer(Modifier.height(32.dp))
    }
  }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, color = Cream, fontSize = 16.sp)
      Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 2.dp))
    }
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
