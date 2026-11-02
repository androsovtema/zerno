package app.zerno.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CameraScreen(vm: ZernoViewModel, onShot: () -> Unit, onDiagnostics: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  var granted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
  LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

  Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("Zerno · RAW-прототип", color = Color.White, modifier = Modifier.weight(1f).padding(start = 8.dp))
      TextButton(onClick = onDiagnostics) { Text("Диагностика") }
    }

    val options = state.scan?.options.orEmpty()
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      options.forEach { o ->
        FilterChip(selected = o == state.selected, onClick = { vm.select(o) }, label = { Text(o.label) })
      }
    }

    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      val selected = state.selected
      when {
        !granted -> Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Разрешить камеру") }
        state.scan == null -> CircularProgressIndicator()
        selected == null -> Text(
          "Ни одна камера не отдаёт RAW сторонним приложениям.\nОткройте «Диагностику» и пришлите отчёт.",
          color = Color.White, modifier = Modifier.padding(24.dp),
        )
        // key: при смене камеры пересоздаём TextureView, чтобы получить новую поверхность.
        else -> key(selected.key) { CameraPreview(vm, selected) }
      }
      state.busy?.let { BusyOverlay(it) }
    }

    state.message?.let { msg ->
      Text(msg, color = Color(0xFFFFC107), modifier = Modifier.fillMaxWidth().clickable { vm.dismissMessage() }.padding(12.dp))
    }

    Box(Modifier.fillMaxWidth().height(112.dp), contentAlignment = Alignment.Center) {
      Box(
        Modifier.size(72.dp).border(4.dp, Color.White, CircleShape).padding(8.dp)
          .background(if (state.busy == null && state.selected != null && granted) Color.White else Color.Gray, CircleShape)
          .clickable(enabled = state.busy == null && state.selected != null && granted) { vm.capture(onShot) },
      )
    }
  }
}

@Composable
private fun CameraPreview(vm: ZernoViewModel, option: app.zerno.camera.CameraOption) {
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val previewSize = remember { vm.previewSize(option) }
  var surface by remember { mutableStateOf<Surface?>(null) }
  var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

  DisposableEffect(lifecycle) {
    val observer = LifecycleEventObserver { _, e ->
      if (e == Lifecycle.Event.ON_RESUME) resumed = true
      if (e == Lifecycle.Event.ON_PAUSE) resumed = false
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }
  val s = surface
  DisposableEffect(option, s, resumed) {
    if (s != null && resumed) vm.openCamera(option, s)
    onDispose { vm.closeCamera() }
  }

  // Сенсор лежит «горизонтально», экран портретный — меняем стороны местами.
  val ratio = minOf(previewSize.width, previewSize.height).toFloat() / maxOf(previewSize.width, previewSize.height)
  AndroidView(
    modifier = Modifier.fillMaxWidth().aspectRatio(ratio),
    factory = { ctx ->
      TextureView(ctx).apply {
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
          override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            surface = Surface(st)
          }
          override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
          override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            vm.closeCamera()
            surface?.release()
            surface = null
            return true
          }
          override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
        }
      }
    },
  )
}

@Composable
fun BusyOverlay(text: String) {
  Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      CircularProgressIndicator()
      Spacer(Modifier.height(12.dp))
      Text(text, color = Color.White)
    }
  }
}
