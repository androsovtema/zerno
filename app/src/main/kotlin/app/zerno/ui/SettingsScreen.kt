package app.zerno.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.appVersion
import app.zerno.ui.theme.Bg
import app.zerno.ui.theme.Muted

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
  Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
    ScreenHeader(title = "Настройки", onBack = onBack)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
      SectionHeader("Съёмка")
      GroupCard {
        GroupRow("Сохранять DNG", "Каждый кадр ещё и файлом RAW — для Lightroom. Файлы большие.") {
          ZernoSwitch(state.saveDng, onSaveDng)
        }
        GroupDivider()
        GroupRow("Коррекция виньетирования", "Выравнивает яркость и цвет по краям кадра.") {
          ZernoSwitch(state.shadingCorrection, onShading)
        }
      }

      SectionHeader("Помощь разработчику")
      GroupCard {
        GroupRow("Отправить журнал", onClick = onSendLog) { ChevronIcon() }
        GroupDivider()
        GroupRow("Диагностика камер", onClick = { showReport = !showReport }) { ChevronIcon(down = showReport) }
      }
      val report = state.scan?.report
      if (showReport) {
        if (report == null) {
          Text("Опрос камер ещё идёт…", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(16.dp))
        } else {
          Row(Modifier.padding(top = 8.dp)) {
            TextAction("Отправить", { onShareReport(report) })
            TextAction("Копировать", { onCopyReport(report) })
          }
          SelectionContainer {
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFFC7C7CC),
              modifier = Modifier.padding(horizontal = 4.dp))
          }
        }
      }

      Spacer(Modifier.height(24.dp))
      GroupCard {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          ZernoLogo(markHeight = 44.dp, textSize = 30.sp)
          Text(
            "Снимает RAW и проявляет своими профилями — без HDR и «улучшений» ИИ.",
            style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
          )
        }
      }
      Text(
        "Версия $version · шрифты Onest и Unbounded (SIL OFL 1.1)",
        style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 32.dp),
      )
    }
  }
}
