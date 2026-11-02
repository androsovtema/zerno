package app.zerno.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.raw.ColorStrategy
import app.zerno.raw.Profile
import kotlin.math.roundToInt

@Composable
fun ResultScreen(vm: ZernoViewModel, onBack: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val settings = state.settings

  Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(12.dp)) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      val bmp = state.preview
      if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = "Проявленный кадр", contentScale = ContentScale.FillWidth,
          modifier = Modifier.fillMaxWidth())
      } else {
        CircularProgressIndicator(Modifier.padding(48.dp))
      }
    }

    Section("Цвет")
    ChipRow(ColorStrategy.entries, settings.strategy, { it.title }) { s -> vm.updateSettings { it.copy(strategy = s) } }
    Section("Профиль")
    ChipRow(Profile.entries, settings.profile, { it.title }) { p -> vm.updateSettings { it.copy(profile = p) } }

    Section("Экспозиция: %+.1f EV".format(settings.exposureEv))
    Slider(
      value = settings.exposureEv.toFloat(), valueRange = -2f..2f, steps = 7,
      onValueChange = { v -> vm.updateSettings { it.copy(exposureEv = (v * 2).roundToInt() / 2.0) } },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Коррекция виньетирования", Modifier.weight(1f))
      Switch(checked = settings.useShading, onCheckedChange = { v -> vm.updateSettings { it.copy(useShading = v) } })
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
      Button(onClick = vm::saveJpeg, enabled = state.busy == null) { Text("Сохранить JPEG") }
      OutlinedButton(onClick = onBack) { Text("Снять ещё") }
    }
    state.busy?.let { Text(it) }
    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

    val report = (state.shot?.captureInfo.orEmpty() + "\n" + state.developInfo).trim()
    Row(verticalAlignment = Alignment.CenterVertically) {
      Section("Данные кадра", Modifier.weight(1f))
      OutlinedButton(onClick = { copy(context, report) }) { Text("Копировать") }
    }
    SelectionContainer {
      Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
    }
  }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier) {
  Text(title, style = MaterialTheme.typography.titleSmall, modifier = modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun <T> ChipRow(items: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items.forEach { item -> FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(label(item)) }) }
  }
}

fun copy(context: Context, text: String) {
  context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Zerno", text))
}
