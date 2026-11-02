package app.zerno

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.zerno.ui.CameraScreen
import app.zerno.ui.EditorScreen
import app.zerno.ui.GalleryScreen
import app.zerno.ui.SettingsScreen
import app.zerno.ui.ZernoViewModel
import app.zerno.ui.share

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(CameraKey)
  // Одна ViewModel на Activity: камера, очередь обработки и редактор общие для экранов.
  val vm: ZernoViewModel = viewModel()
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
      entry<CameraKey> {
        CameraScreen(
          vm,
          onOpenShot = { backStack.add(EditorKey(it)) },
          onGallery = { backStack.add(GalleryKey) },
          onSettings = { backStack.add(SettingsKey) },
        )
      }
      entry<EditorKey> { key -> EditorScreen(vm, key.name, onBack = { backStack.removeLastOrNull() }) }
      entry<GalleryKey> {
        GalleryScreen(vm, onOpen = { backStack.add(EditorKey(it)) }, onBack = { backStack.removeLastOrNull() })
      }
      entry<SettingsKey> { SettingsScreen(vm, onBack = { backStack.removeLastOrNull() }) }
    },
  )

  state.crashReport?.let { report ->
    AlertDialog(
      onDismissRequest = vm::dismissCrash,
      title = { Text("Зерно закрылось с ошибкой") },
      text = { Text("В прошлый раз приложение упало. Отправьте отчёт разработчику — это поможет быстро исправить.") },
      confirmButton = {
        TextButton(onClick = { share(context, report, "Отчёт об ошибке"); vm.dismissCrash() }) { Text("Отправить") }
      },
      dismissButton = { TextButton(onClick = vm::dismissCrash) { Text("Закрыть") } },
    )
  }
}
