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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
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
import app.zerno.ui.theme.Accent
import app.zerno.ui.theme.Bg
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.OnBg
import app.zerno.ui.theme.Panel
import app.zerno.ui.theme.SurfaceHigh

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
    onToggleDng = vm::setSaveDng,
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraContent(
  state: UiState,
  granted: Boolean,
  asked: Boolean,
  flashAlpha: Float,
  onRequestPermission: () -> Unit = {},
  onOpenAppSettings: () -> Unit = {},
  onSettings: () -> Unit = {},
  onGallery: () -> Unit = {},
  onOpenShot: (String) -> Unit = {},
  onSelect: (CameraOption) -> Unit = {},
  onProfile: (Profile) -> Unit = {},
  onToggleDng: (Boolean) -> Unit = {},
  onShutter: () -> Unit = {},
  onDismissMessage: () -> Unit = {},
  onDismissTip: () -> Unit = {},
  preview: @Composable (CameraOption) -> Unit,
) {
  var showProfiles by rememberSaveable { mutableStateOf(false) }
  val noRaw = granted && state.scan != null && state.selected == null

  Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
    // Верх: настройки, состояние проявки, DNG
    Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp)) {
      CircleButton(onSettings, "Настройки", Modifier.align(Alignment.CenterStart)) { TuneIcon() }
      if (state.processing > 0) {
        Row(
          Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(SurfaceHigh.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(Modifier.size(12.dp), color = Accent, strokeWidth = 1.5.dp)
          Spacer(Modifier.width(8.dp))
          Text("Проявка · ${state.processing}", style = MaterialTheme.typography.labelMedium, color = OnBg)
        }
      }
      if (!noRaw && granted) {
        Box(
          Modifier.align(Alignment.CenterEnd).height(36.dp).clip(RoundedCornerShape(50))
            .background(SurfaceHigh.copy(alpha = 0.85f))
            .clickable(role = Role.Switch, onClickLabel = "Сохранять DNG") { onToggleDng(!state.saveDng) }
            .padding(horizontal = 14.dp),
          contentAlignment = Alignment.Center,
        ) {
          Text("DNG", style = MaterialTheme.typography.labelMedium, color = if (state.saveDng) Accent else Muted)
        }
      }
    }

    // Видоискатель
    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
      val selected = state.selected
      when {
        !granted -> PermissionBlock(asked, onRequest = onRequestPermission, onSettings = onOpenAppSettings)
        state.scan == null -> CircularProgressIndicator(Modifier.size(28.dp), color = Muted, strokeWidth = 2.dp)
        selected == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
          Text("Этот телефон не отдаёт RAW", style = MaterialTheme.typography.titleLarge, color = OnBg, textAlign = TextAlign.Center)
          Spacer(Modifier.height(10.dp))
          Text(
            "Ни одна камера не даёт сторонним приложениям сырые данные. Откройте настройки → " +
              "«Диагностика камер» и отправьте отчёт разработчику.",
            style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center,
          )
        }
        // key: при смене камеры пересоздаём TextureView, чтобы получить новую поверхность.
        else -> preview(selected)
      }
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flashAlpha)))

      val options = state.scan?.options.orEmpty()
      Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        state.message?.let { msg ->
          Text(
            msg, style = MaterialTheme.typography.bodySmall, color = OnBg,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).clip(RoundedCornerShape(14.dp))
              .background(Panel.copy(alpha = 0.92f)).clickable(onClick = onDismissMessage)
              .padding(horizontal = 14.dp, vertical = 10.dp),
          )
        }
        if (granted && options.size > 1) LensSelector(options, state.selected, onSelect)
      }

      if (state.showTip && state.selected != null && granted) {
        Column(
          Modifier.align(Alignment.TopCenter).padding(12.dp).clip(RoundedCornerShape(20.dp))
            .background(Panel.copy(alpha = 0.95f)).padding(start = 18.dp, end = 6.dp, top = 16.dp, bottom = 4.dp),
        ) {
          Text("Как снимать", style = MaterialTheme.typography.titleMedium, color = OnBg)
          Text(
            "Профиль справа от спуска — стиль проявки следующих снимков.\n" +
              "Миниатюра слева — открыть снимок и перепроявить другим профилем.\n" +
              "Видоискатель показывает сцену без профиля.",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFFC7C7CC),
            modifier = Modifier.padding(top = 6.dp, end = 12.dp),
          )
          TextAction("Понятно", onDismissTip, Modifier.align(Alignment.End))
        }
      }
    }

    if (noRaw) return@Column

    // Низ: последний снимок, спуск, профиль
    Row(
      Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 30.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val thumbShape = RoundedCornerShape(14.dp)
      Box(
        Modifier.size(54.dp).clip(thumbShape).background(SurfaceHigh)
          .clickable(role = Role.Button, onClickLabel = "Открыть снимок") { state.lastShotName?.let(onOpenShot) ?: onGallery() },
      ) {
        state.lastThumb?.let {
          Image(it.asImageBitmap(), "Последний снимок", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
      }
      Spacer(Modifier.weight(1f))
      val enabled = granted && state.selected != null && !state.capturing && state.processing < 2
      ShutterButton(enabled = enabled, busy = state.capturing, onClick = onShutter)
      Spacer(Modifier.weight(1f))
      ProfilePreview(
        state.profile, state.profilePreviews[state.profile], selected = false,
        modifier = Modifier.size(54.dp).clickable(role = Role.Button, onClickLabel = "Выбрать профиль") { showProfiles = true },
      )
    }
    Text(
      state.profile.title, style = MaterialTheme.typography.labelMedium, color = Accent, textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { showProfiles = true }.padding(top = 2.dp, bottom = 14.dp),
    )
  }

  if (showProfiles) {
    ModalBottomSheet(onDismissRequest = { showProfiles = false }, containerColor = Panel) {
      ProfileSheetContent(state.profile, state.profilePreviews) { p ->
        onProfile(p)
        showProfiles = false
      }
    }
  }
}

/** Объективы — поверх видоискателя; у выбранного акцентный цвет и «×». */
@Composable
private fun LensSelector(options: List<CameraOption>, selected: CameraOption?, onSelect: (CameraOption) -> Unit) {
  Row(
    Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.35f)).padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    options.forEach { o ->
      val sel = o == selected
      Box(
        Modifier.height(40.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(50))
          .then(if (sel) Modifier.background(Color.Black.copy(alpha = 0.55f)) else Modifier)
          .clickable(role = Role.RadioButton, onClick = { onSelect(o) }).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          if (sel) o.zoomLabel else o.zoomLabel.removeSuffix("×"),
          style = MaterialTheme.typography.labelMedium, color = if (sel) Accent else OnBg, maxLines = 1,
        )
      }
    }
  }
}

@Composable
private fun PermissionBlock(asked: Boolean, onRequest: () -> Unit, onSettings: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
    ZernoLogo(markHeight = 50.dp, textSize = 34.sp)
    Spacer(Modifier.height(28.dp))
    Text("Нужен доступ к камере", style = MaterialTheme.typography.titleLarge, color = OnBg)
    Spacer(Modifier.height(8.dp))
    Text("Зерно снимает RAW и проявляет его само, без HDR и ИИ.",
      style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    PrimaryButton("Разрешить камеру", onRequest)
    if (asked) {
      Spacer(Modifier.height(10.dp))
      SecondaryButton("Открыть настройки телефона", onSettings)
      Text("Если запрос не появляется — включите доступ в настройках.", style = MaterialTheme.typography.bodySmall,
        color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
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
  Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(20.dp))) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
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
}
