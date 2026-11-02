package app.zerno.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.raw.ColorStrategy
import app.zerno.raw.Profile
import app.zerno.ui.theme.Amber
import app.zerno.ui.theme.Muted
import kotlin.math.roundToInt

@Composable
fun EditorScreen(vm: ZernoViewModel, name: String, onBack: () -> Unit) {
  LaunchedEffect(name) { vm.openEditor(name) }
  val state by vm.state.collectAsStateWithLifecycle()
  EditorContent(
    state = state,
    name = name,
    onBack = { vm.closeEditor(); onBack() },
    onDelete = { vm.deleteShot(name); onBack() },
    onUpdate = vm::updateEdit,
    onSave = vm::saveEdit,
  )
}

/** Вёрстка редактора без ViewModel — для превью и скриншот-тестов. */
@Composable
fun EditorContent(
  state: UiState,
  name: String,
  onBack: () -> Unit,
  onDelete: () -> Unit,
  onUpdate: ((EditSettings) -> EditSettings) -> Unit,
  onSave: () -> Unit,
) {
  val context = LocalContext.current
  val editor = state.editor?.takeIf { it.name == name }
  var advanced by rememberSaveable { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }

  Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack) { Text("← Назад", color = Color.White) }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = { confirmDelete = true }) { Text("Удалить", color = Muted) }
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
      // Пропорции рамки — по самому кадру (вертикальный, горизонтальный), до загрузки — 3:4.
      val ratio = editor?.preview?.let { it.width.toFloat() / it.height } ?: (3f / 4f)
      Box(Modifier.fillMaxWidth().aspectRatio(ratio), contentAlignment = Alignment.Center) {
        val bmp = editor?.preview
        when {
          editor?.error != null -> Text(editor.error, color = Color.White, modifier = Modifier.padding(24.dp))
          bmp != null -> Image(bmp.asImageBitmap(), "Снимок", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
          else -> CircularProgressIndicator(color = Amber)
        }
      }

      val settings = editor?.settings
      if (editor != null && settings != null && editor.error == null) {
        Column(Modifier.padding(horizontal = 16.dp)) {
          SectionTitle("Профиль")
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Profile.entries.forEach { p ->
              FilterChip(selected = p == settings.profile, onClick = { onUpdate { it.copy(profile = p) } },
                label = { Text(p.title) })
            }
          }

          SectionTitle("Экспозиция ${"%+.1f".format(settings.evOffset)} EV")
          Slider(
            value = settings.evOffset.toFloat(), valueRange = -2f..2f, steps = 7,
            onValueChange = { v -> onUpdate { it.copy(evOffset = (v * 2).roundToInt() / 2.0) } },
          )

          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = onSave, enabled = state.busy == null && editor.shot != null) { Text("Сохранить копию") }
            OutlinedButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Скрыть детали" else "Детали") }
          }
          state.busy?.let { Text(it, color = Muted) }
          state.message?.let { Text(it, color = Amber, fontSize = 13.sp) }

          if (advanced) {
            SectionTitle("Цвет (для проверки устройства)")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ColorStrategy.entries.forEach { s ->
                FilterChip(selected = s == settings.strategy, onClick = { onUpdate { it.copy(strategy = s) } },
                  label = { Text(s.title) })
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
              Text("Коррекция виньетирования", Modifier.weight(1f), color = Color.White)
              Switch(checked = settings.shading, onCheckedChange = { v -> onUpdate { it.copy(shading = v) } })
            }
            val report = (editor.shot?.captureInfo.orEmpty() + "\n" + editor.info).trim()
            Row(verticalAlignment = Alignment.CenterVertically) {
              SectionTitle("Данные кадра", Modifier.weight(1f))
              TextButton(onClick = { copy(context, report) }) { Text("Копировать") }
              TextButton(onClick = { share(context, report) }) { Text("Отправить") }
            }
            SelectionContainer {
              Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = Color(0xFFC7C7CC))
            }
            Spacer(Modifier.width(1.dp).padding(bottom = 24.dp))
          }
        }
      }
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Удалить из Zerno?") },
      text = { Text("RAW и миниатюра удалятся из приложения. JPEG в галерее телефона останется.") },
      confirmButton = {
        TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить") }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
  }
}
