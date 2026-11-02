package app.zerno.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.raw.ColorStrategy
import app.zerno.ui.theme.Amber
import app.zerno.ui.theme.Cream
import app.zerno.ui.theme.Mono
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.PanelHigh
import app.zerno.ui.theme.Safelight
import app.zerno.ui.theme.rememberGrainFrame
import app.zerno.ui.theme.zernoBackground

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
  val developing = editor != null && editor.preview == null && editor.error == null
  val frame = rememberGrainFrame(developing || state.busy != null)

  Column(Modifier.fillMaxSize().zernoBackground(frame).safeDrawingPadding()) {
    ScreenBar(title = null, onBack = onBack) {
      EdgeButton("Удалить", { confirmDelete = true }, color = Muted)
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
      // Пропорции рамки — по самому кадру (вертикальный, горизонтальный), до загрузки — 3:4.
      val ratio = editor?.preview?.let { it.width.toFloat() / it.height } ?: (3f / 4f)
      Box(
        Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
      ) {
        val bmp = editor?.preview
        when {
          editor?.error != null -> Text(editor.error, color = Cream, modifier = Modifier.padding(24.dp))
          bmp != null -> Image(bmp.asImageBitmap(), "Снимок", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
          else -> EdgeText("Проявляю…", color = Muted, size = 11.sp)
        }
      }

      val settings = editor?.settings
      if (editor != null && settings != null && editor.error == null) {
        FilmStrip(
          settings.profile,
          editor.profilePreviews.ifEmpty { state.profilePreviews },
          { p -> onUpdate { it.copy(profile = p) } },
          Modifier.padding(top = 14.dp),
        )
        Column(Modifier.padding(horizontal = 16.dp)) {
          Spacer(Modifier.height(16.dp))
          ExposureScale(settings.evOffset, { v -> onUpdate { it.copy(evOffset = v) } })

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)) {
            PaperButton("Сохранить копию", onSave, enabled = state.busy == null && editor.shot != null)
            GhostButton(if (advanced) "Скрыть детали" else "Детали", { advanced = !advanced })
          }
          state.busy?.let { EdgeText(it, color = Muted, size = 10.sp, modifier = Modifier.padding(top = 6.dp)) }
          state.message?.let { Text(it, color = Amber, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }

          if (advanced) {
            SectionTitle("Цвет — для проверки устройства")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ColorStrategy.entries.forEach { s ->
                MonoChip(s.title, s == settings.strategy) { onUpdate { it.copy(strategy = s) } }
              }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
              Text("Коррекция виньетирования", Modifier.weight(1f), color = Cream)
              Switch(checked = settings.shading, onCheckedChange = { v -> onUpdate { it.copy(shading = v) } })
            }
            val report = (editor.shot?.captureInfo.orEmpty() + "\n" + editor.info).trim()
            Row(verticalAlignment = Alignment.CenterVertically) {
              SectionTitle("Данные кадра", Modifier.weight(1f))
              EdgeButton("Копировать", { copy(context, report) }, color = Amber)
              EdgeButton("Отправить", { share(context, report) }, color = Amber)
            }
            SelectionContainer {
              Text(report, fontFamily = Mono, fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFFCDBFAE))
            }
          }
          Spacer(Modifier.height(28.dp))
        }
      }
    }
  }

  if (confirmDelete) {
    AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text("Удалить из Зерна?", style = MaterialTheme.typography.titleLarge) },
      text = { Text("RAW и миниатюра удалятся из приложения. JPEG в галерее телефона останется.", color = Muted) },
      confirmButton = { EdgeButton("Удалить", { confirmDelete = false; onDelete() }, color = Safelight) },
      dismissButton = { EdgeButton("Отмена", { confirmDelete = false }) },
      containerColor = PanelHigh,
    )
  }
}
