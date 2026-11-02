package app.zerno.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.net.Uri
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.OrientationEventListener
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.camera.CameraOption
import app.zerno.raw.Profile
import app.zerno.ui.theme.Amber
import app.zerno.ui.theme.Muted

@Composable
fun CameraScreen(
  vm: ZernoViewModel,
  onOpenShot: (String) -> Unit,
  onGallery: () -> Unit,
  onSettings: () -> Unit,
) {
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val view = LocalView.current
  var granted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  var asked by rememberSaveable { mutableStateOf(false) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    granted = it
    asked = true
  }
  LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

  DisposableEffect(Unit) {
    val listener = object : OrientationEventListener(context) {
      override fun onOrientationChanged(orientation: Int) {
        if (orientation != ORIENTATION_UNKNOWN) vm.deviceOrientation = ((orientation + 45) / 90 * 90) % 360
      }
    }
    if (listener.canDetectOrientation()) listener.enable()
    onDispose { listener.disable() }
  }

  val flash = remember { Animatable(0f) }
  LaunchedEffect(state.shutterTick) {
    if (state.shutterTick > 0) {
      flash.snapTo(0.85f)
      flash.animateTo(0f, tween(260))
    }
  }

  CameraContent(
    state = state,
    granted = granted,
    asked = asked,
    flashAlpha = flash.value,
    onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
    onOpenAppSettings = {
      context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    },
    onSettings = onSettings,
    onGallery = onGallery,
    onOpenShot = onOpenShot,
    onSelect = vm::select,
    onProfile = vm::selectProfile,
    onShutter = {
      view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
      vm.capture()
    },
    onDismissMessage = vm::dismissMessage,
    onDismissTip = vm::dismissTip,
    preview = { option -> key(option.key) { CameraPreview(vm, option) } },
  )
}

/** Вёрстка экрана камеры без ViewModel — так её можно показать в превью и скриншот-тестах. */
@Composable
fun CameraContent(
  state: UiState,
  granted: Boolean,
  asked: Boolean,
  flashAlpha: Float,
  onRequestPermission: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onSettings: () -> Unit,
  onGallery: () -> Unit,
  onOpenShot: (String) -> Unit,
  onSelect: (CameraOption) -> Unit,
  onProfile: (Profile) -> Unit,
  onShutter: () -> Unit,
  onDismissMessage: () -> Unit,
  onDismissTip: () -> Unit,
  preview: @Composable (CameraOption) -> Unit,
) {
  Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
    // Верхняя панель
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Text("ZERNO", color = Color.White, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 15.sp)
      Spacer(Modifier.width(10.dp))
      if (state.processing > 0) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Amber)
        Text("  проявляю ${state.processing}", color = Muted, fontSize = 12.sp)
      }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onSettings) { Text("Настройки", color = Muted) }
    }

    // Видоискатель
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
      val selected = state.selected
      when {
        !granted -> PermissionBlock(asked, onRequest = onRequestPermission, onSettings = onOpenAppSettings)
        state.scan == null -> CircularProgressIndicator(color = Amber)
        selected == null -> Text(
          "Этот телефон не отдаёт RAW сторонним приложениям ни с одной камеры.\n\n" +
            "Откройте «Настройки» → «Диагностика камер» и отправьте отчёт разработчику.",
          color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp),
        )
        // key: при смене камеры пересоздаём TextureView, чтобы получить новую поверхность.
        else -> preview(selected)
      }
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flashAlpha)))
      if (state.showTip && state.selected != null && granted) {
        Column(
          Modifier.align(Alignment.TopCenter).padding(12.dp)
            .background(Color(0xE61C1C1F), RoundedCornerShape(12.dp)).padding(14.dp),
        ) {
          Text("Как пользоваться", color = Amber, fontWeight = FontWeight.SemiBold)
          Text(
            "• Внизу выберите профиль — он применится к следующим снимкам.\n" +
              "• Нажмите на миниатюру, чтобы перепроявить кадр другим профилем.\n" +
              "• Видоискатель показывает сцену без профиля — итог будет мягче и натуральнее.",
            color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
          )
          TextButton(onClick = onDismissTip, modifier = Modifier.align(Alignment.End)) { Text("Понятно") }
        }
      }
      state.message?.let { msg ->
        Text(
          msg, color = Color.White, fontSize = 13.sp,
          modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
            .background(Color(0xCC2B1700), RoundedCornerShape(10.dp))
            .clickable(onClick = onDismissMessage).padding(horizontal = 12.dp, vertical = 8.dp),
        )
      }
    }

    // Объективы
    val options = state.scan?.options.orEmpty()
    if (options.size > 1) {
      Row(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
      ) {
        options.forEach { o -> LensButton(o.zoomLabel, o == state.selected) { onSelect(o) } }
      }
    }

    // Профили
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
    ) {
      Profile.entries.forEach { p ->
        val sel = p == state.profile
        Text(
          p.title.uppercase(), fontSize = 11.sp, letterSpacing = 0.5.sp, maxLines = 1,
          fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
          color = if (sel) Amber else Muted,
          modifier = Modifier.clickable { onProfile(p) }.padding(vertical = 6.dp),
        )
      }
    }

    // Нижняя панель: миниатюра, спуск, галерея
    Row(
      Modifier.fillMaxWidth().height(110.dp).padding(horizontal = 28.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1F))
        .clickable { state.lastShotName?.let(onOpenShot) ?: onGallery() }) {
        state.lastThumb?.let {
          Image(it.asImageBitmap(), "Последний снимок", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
      }
      Spacer(Modifier.weight(1f))
      val enabled = granted && state.selected != null && !state.capturing && state.processing < 2
      ShutterButton(enabled = enabled, busy = state.capturing, onClick = onShutter)
      Spacer(Modifier.weight(1f))
      Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        TextButton(onClick = onGallery) { Text("Все", color = Muted) }
      }
    }
  }
}

@Composable
private fun LensButton(label: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    Modifier.size(44.dp).clip(CircleShape)
      .background(if (selected) Color(0xFF2C2C2E) else Color(0x661C1C1F))
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = if (selected) Amber else Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun ShutterButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutter")
  Box(
    Modifier.size(78.dp).scale(scale).border(4.dp, if (enabled) Color.White else Color.DarkGray, CircleShape).padding(7.dp)
      .clip(CircleShape).background(if (enabled) Color.White else Color.DarkGray)
      .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.Black, strokeWidth = 3.dp)
  }
}

@Composable
private fun PermissionBlock(asked: Boolean, onRequest: () -> Unit, onSettings: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
    Text("Zerno нужен доступ к камере, чтобы снимать RAW.", color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onRequest) { Text("Разрешить камеру") }
    if (asked) {
      Spacer(Modifier.height(8.dp))
      OutlinedButton(onClick = onSettings) { Text("Открыть настройки телефона") }
      Text("Если запрос не появляется — включите доступ в настройках.", color = Muted, fontSize = 12.sp,
        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
  }
}

@Composable
private fun CameraPreview(vm: ZernoViewModel, option: CameraOption) {
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
    modifier = Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(4.dp)),
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
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(text, style = MaterialTheme.typography.titleSmall, color = Muted, modifier = modifier.padding(top = 16.dp, bottom = 6.dp))
}
