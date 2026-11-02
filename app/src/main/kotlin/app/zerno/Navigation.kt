package app.zerno

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.zerno.ui.CameraScreen
import app.zerno.ui.DiagnosticsScreen
import app.zerno.ui.ResultScreen
import app.zerno.ui.ZernoViewModel

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(CameraKey)
  // Одна ViewModel на Activity: снимок живёт между экранами.
  val vm: ZernoViewModel = viewModel()

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
      entry<CameraKey> {
        CameraScreen(vm, onShot = { backStack.add(ResultKey) }, onDiagnostics = { backStack.add(DiagnosticsKey) })
      }
      entry<ResultKey> { ResultScreen(vm, onBack = { backStack.removeLastOrNull() }) }
      entry<DiagnosticsKey> { DiagnosticsScreen(vm, onBack = { backStack.removeLastOrNull() }) }
    },
  )
}
