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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.raw.ColorStrategy
import app.zerno.ui.theme.Accent
import app.zerno.ui.theme.Bg
import app.zerno.ui.theme.Danger
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.OnBg
import app.zerno.ui.theme.Panel
import app.zerno.ui.theme.SurfaceHigh

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

  Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
    ScreenHeader(title = null, onBack = onBack) {
      TextAction("Удалить", { confirmDelete = true }, color = Danger)
    }

    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
      // Пропорции рамки — по самому кадру (вертикальный, горизонтальный), до загрузки — 3:4.
      val ratio = editor?.preview?.let { it.width.toFloat() / it.height } ?: (3f / 4f)
      Box(
        Modifier.padding(horizontal = 6.dp).fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(20.dp)).background(Panel),
        contentAlignment = Alignment.Center,
      ) {
        val bmp = editor?.preview
        when {
          editor?.error != null -> Text(editor.error, color = OnBg, modifier = Modifier.padding(24.dp))
          bmp != null -> Image(bmp.asImageBitmap(), "Снимок", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
          else -> CircularProgressIndicator(Modifier.size(28.dp), color = Muted, strokeWidth = 2.dp)
        }
      }

      val settings = editor?.settings
      if (editor != null && settings != null && editor.error == null) {
        ProfileRow(
          settings.profile,
          editor.profilePreviews.ifEmpty { state.profilePreviews },
          { p -> onUpdate { it.copy(profile = p) } },
          Modifier.padding(top = 16.dp),
        )
        Column(Modifier.padding(horizontal = 12.dp)) {
          Spacer(Modifier.height(16.dp))
          ExposureRuler(settings.evOffset, { v -> onUpdate { it.copy(evOffset = v) } })

          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
            PrimaryButton("Сохранить копию", onSave, Modifier.weight(1f), enabled = state.busy == null && editor.shot != null)
            SecondaryButton(if (advanced) "Скрыть" else "Детали", { advanced = !advanced })
          }
          state.busy?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }
          state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Accent, modifier = Modifier.padding(top = 8.dp, start = 4.dp)) }

          if (advanced) {
            SectionHeader("Цвет — для проверки устройства", Modifier.padding(start = 0.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ColorStrategy.entries.forEach { s -> Chip(s.title, s == settings.strategy) { onUpdate { it.copy(strategy = s) } } }
            }
            Spacer(Modifier.height(12.dp))
            GroupCard {
              GroupRow("Коррекция виньетирования") {
                ZernoSwitch(settings.shading) { v -> onUpdate { it.copy(shading = v) } }
              }
            }
            val report = (editor.shot?.captureInfo.orEmpty() + "\n" + editor.info).trim()
            Row(verticalAlignment = Alignment.CenterVertically) {
              SectionHeader("Данные кадра", Modifier.weight(1f))
              TextAction("Копировать", { copy(context, report) })
              TextAction("Отправить", { share(context, report) })
            }
            SelectionContainer {
              Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFFC7C7CC))
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
      title = { Text("Удалить снимок?", style = MaterialTheme.typography.titleLarge) },
      text = { Text("RAW и миниатюра удалятся из приложения. JPEG в галерее телефона останется.", color = Muted) },
      confirmButton = { TextAction("Удалить", { confirmDelete = false; onDelete() }, color = Danger) },
      dismissButton = { TextAction("Отмена", { confirmDelete = false }, color = OnBg) },
      containerColor = SurfaceHigh,
    )
  }
}
