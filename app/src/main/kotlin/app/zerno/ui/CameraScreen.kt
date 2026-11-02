package app.zerno.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
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
import app.zerno.ui.theme.Amber
import app.zerno.ui.theme.Cream
import app.zerno.ui.theme.Display
import app.zerno.ui.theme.Hairline
import app.zerno.ui.theme.Mono
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.grainyFill
import app.zerno.ui.theme.rememberGrainFrame
import app.zerno.ui.theme.zernoBackground

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
  // Зерно фона «оживает», пока кадр проявляется или ищутся камеры.
  val frame = rememberGrainFrame(state.processing > 0 || (granted && state.scan == null))
  Column(Modifier.fillMaxSize().zernoBackground(frame).safeDrawingPadding()) {
    // Верхняя панель: логотип, состояние проявки, настройки
    Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      ZernoLogo()
      Spacer(Modifier.width(12.dp))
      if (state.processing > 0) EdgeText("● проявка · ${state.processing}", size = 10.sp)
      Spacer(Modifier.weight(1f))
      Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Настройки", onClick = onSettings),
        contentAlignment = Alignment.Center,
      ) { TuneIcon() }
    }

    // Видоискатель
    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
      val selected = state.selected
      when {
        !granted -> PermissionBlock(asked, onRequest = onRequestPermission, onSettings = onOpenAppSettings)
        state.scan == null -> EdgeText("Ищу камеры…", color = Muted, size = 11.sp)
        selected == null -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
          Text("Этот телефон не отдаёт RAW", style = MaterialTheme.typography.titleLarge, color = Cream, textAlign = TextAlign.Center)
          Spacer(Modifier.height(10.dp))
          Text(
            "Ни одна камера не даёт сторонним приложениям сырые данные. Откройте настройки → " +
              "«Диагностика камер» и отправьте отчёт разработчику.",
            color = Muted, textAlign = TextAlign.Center,
          )
        }
        // key: при смене камеры пересоздаём TextureView, чтобы получить новую поверхность.
        else -> preview(selected)
      }
      Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = flashAlpha)))

      val options = state.scan?.options.orEmpty()
      Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        state.message?.let { msg ->
          Text(
            msg, color = Cream, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).panel(RoundedCornerShape(12.dp))
              .clickable(onClick = onDismissMessage).padding(horizontal = 14.dp, vertical = 10.dp),
          )
        }
        if (granted && options.size > 1) LensSelector(options, state.selected, onSelect)
      }

      if (state.showTip && state.selected != null && granted) {
        Column(Modifier.align(Alignment.TopCenter).padding(10.dp).panel().padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 6.dp)) {
          Text("Как снимать", style = MaterialTheme.typography.titleMedium, color = Cream)
          Text(
            "Внизу — плёнка с профилями: выберите кадр, и следующие снимки проявятся в этом стиле.\n\n" +
              "Нажмите на отпечаток слева, чтобы перепроявить снимок другим профилем.\n\n" +
              "Видоискатель показывает сцену без профиля — итог будет мягче и «плёночнее».",
            color = Color(0xFFD9CBB8), fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp, end = 10.dp),
          )
          EdgeButton("Понятно", onDismissTip, Modifier.align(Alignment.End), color = Amber)
        }
      }
    }

    // Без RAW снимать нечем — лента и спуск только мешают.
    if (granted && state.scan != null && state.selected == null) return@Column

    // Профили — кадры на плёнке
    FilmStrip(state.profile, state.profilePreviews, onProfile, Modifier.padding(top = 10.dp))

    // Нижняя панель: отпечаток последнего кадра, спуск, плёнка (галерея)
    Row(
      Modifier.fillMaxWidth().height(116.dp).padding(horizontal = 28.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      LastPrint(state.lastThumb) { state.lastShotName?.let(onOpenShot) ?: onGallery() }
      Spacer(Modifier.weight(1f))
      val enabled = granted && state.selected != null && !state.capturing && state.processing < 2
      ShutterButton(enabled = enabled, busy = state.capturing, tick = state.shutterTick, onClick = onShutter)
      Spacer(Modifier.weight(1f))
      Column(
        Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onGallery),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
      ) {
        Text("${state.gallery.size}", fontFamily = Display, fontSize = 22.sp, color = Cream)
        EdgeText("плёнка", color = Muted, size = 8.sp, spacing = 1.sp)
      }
    }
  }
}

/** Последний снимок — как отпечаток на кремовой бумаге, чуть повёрнутый. */
@Composable
private fun LastPrint(thumb: Bitmap?, onClick: () -> Unit) {
  Box(
    Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClickLabel = "Открыть снимок", onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    if (thumb != null) {
      Box(Modifier.size(48.dp).rotate(-5f).grainyFill(Cream, alpha = 0.12f).padding(3.dp)) {
        Image(thumb.asImageBitmap(), "Последний снимок", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      }
    } else {
      Box(Modifier.size(46.dp).rotate(-5f).border(1.dp, Hairline, RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
        ZernoMark(18.dp, Modifier.alpha(0.35f))
      }
    }
  }
}

/** Объективы — «пилюля» поверх видоискателя. */
@Composable
private fun LensSelector(options: List<CameraOption>, selected: CameraOption?, onSelect: (CameraOption) -> Unit) {
  Row(
    Modifier.clip(RoundedCornerShape(50)).background(Color(0xB30D0A08)).padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    options.forEach { o ->
      val sel = o == selected
      Box(
        Modifier.size(40.dp).clip(CircleShape)
          .then(if (sel) Modifier.background(Amber.copy(alpha = 0.18f)).border(1.dp, Amber, CircleShape) else Modifier)
          .clickable(role = Role.RadioButton, onClick = { onSelect(o) }),
        contentAlignment = Alignment.Center,
      ) {
        Text(o.zoomLabel, fontFamily = Mono, fontSize = 11.sp, color = if (sel) Amber else Cream, maxLines = 1)
      }
    }
  }
}

/** Рамка видоискателя: уголки, как в дальномере. */
@Composable
fun ViewfinderMarks(modifier: Modifier = Modifier) {
  Canvas(modifier.fillMaxSize()) {
    val inset = 14.dp.toPx()
    val len = 18.dp.toPx()
    val stroke = 1.5.dp.toPx()
    val c = Cream.copy(alpha = 0.7f)
    val (w, h) = size.width to size.height
    listOf(
      Offset(inset, inset) to Offset(1f, 1f), Offset(w - inset, inset) to Offset(-1f, 1f),
      Offset(inset, h - inset) to Offset(1f, -1f), Offset(w - inset, h - inset) to Offset(-1f, -1f),
    ).forEach { (p, d) ->
      drawLine(c, p, Offset(p.x + len * d.x, p.y), stroke)
      drawLine(c, p, Offset(p.x, p.y + len * d.y), stroke)
    }
  }
}

@Composable
private fun PermissionBlock(asked: Boolean, onRequest: () -> Unit, onSettings: () -> Unit) {
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
    ZernoMark(72.dp)
    Spacer(Modifier.height(20.dp))
    Text("Нужна камера", style = MaterialTheme.typography.headlineSmall, color = Cream)
    Spacer(Modifier.height(8.dp))
    Text("Зерно снимает RAW и проявляет его само — для этого нужен доступ к камере.",
      color = Muted, textAlign = TextAlign.Center, lineHeight = 22.sp)
    Spacer(Modifier.height(20.dp))
    PaperButton("Разрешить камеру", onRequest)
    if (asked) {
      Spacer(Modifier.height(10.dp))
      GhostButton("Открыть настройки телефона", onSettings)
      Text("Если запрос не появляется — включите доступ в настройках.", color = Muted, fontSize = 12.sp, lineHeight = 16.sp,
        textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
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
  Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(14.dp))) {
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
    ViewfinderMarks()
  }
}
