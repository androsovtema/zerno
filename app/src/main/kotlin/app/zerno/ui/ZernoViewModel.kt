package app.zerno.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zerno.camera.Camera2Mapping
import app.zerno.camera.CameraOption
import app.zerno.camera.CameraScan
import app.zerno.camera.CameraScanner
import app.zerno.camera.MediaSaver
import app.zerno.camera.RawCameraController
import app.zerno.raw.ColorMetadata
import app.zerno.raw.ColorPipelineResolver
import app.zerno.raw.ColorStrategy
import app.zerno.raw.DevelopedImage
import app.zerno.raw.Profile
import app.zerno.raw.RawDeveloper
import app.zerno.raw.RawImage
import app.zerno.raw.ShadingMap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Настройки проявки, которые можно менять после съёмки. */
data class DevelopSettings(
  val strategy: ColorStrategy = ColorStrategy.AUTO,
  val profile: Profile = Profile.NEUTRAL,
  val exposureEv: Double = 0.0,
  val useShading: Boolean = true,
)

class Shot(
  val name: String,
  val raw: RawImage,
  val meta: ColorMetadata,
  val shading: ShadingMap?,
  val rotation: Int,
  val captureInfo: String,
)

data class UiState(
  val scan: CameraScan? = null,
  val selected: CameraOption? = null,
  val busy: String? = null,
  val message: String? = null,
  val shot: Shot? = null,
  val settings: DevelopSettings = DevelopSettings(),
  val preview: Bitmap? = null,
  val developInfo: String = "",
)

class ZernoViewModel(app: Application) : AndroidViewModel(app) {

  private val manager = app.getSystemService(CameraManager::class.java)
  private val controller = RawCameraController(manager)
  private val _state = MutableStateFlow(UiState())
  val state: StateFlow<UiState> = _state.asStateFlow()
  private var developJob: Job? = null

  init {
    viewModelScope.launch {
      val scan = withContext(Dispatchers.IO) { runCatching { CameraScanner(manager).scan() } }
      scan.onSuccess { s -> _state.update { it.copy(scan = s, selected = it.selected ?: s.options.firstOrNull()) } }
        .onFailure { e -> _state.update { it.copy(message = "Не удалось опросить камеры: ${e.message}") } }
    }
  }

  fun select(option: CameraOption) = _state.update { it.copy(selected = option) }

  fun previewSize(option: CameraOption) = controller.previewSize(option)

  fun openCamera(option: CameraOption, surface: Surface) {
    viewModelScope.launch {
      runCatching { controller.open(option, surface) }
        .onFailure { e -> _state.update { it.copy(message = "Камера ${option.label}: ${e.message}") } }
    }
  }

  fun closeCamera() = controller.close()

  fun dismissMessage() = _state.update { it.copy(message = null) }

  fun capture(onDone: () -> Unit) {
    val option = _state.value.selected ?: return
    if (_state.value.busy != null) return
    viewModelScope.launch {
      _state.update { it.copy(busy = "Снимаю RAW…", message = null) }
      try {
        val shot = controller.capture()
        val name = "ZERNO_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val rotation = controller.sensorOrientation(option)
        val (prepared, dngNote) = withContext(Dispatchers.Default) {
          try {
            val raw = Camera2Mapping.rawImage(shot.image, shot.characteristics, shot.result)
            val meta = Camera2Mapping.colorMetadata(shot.characteristics, shot.result, shot.latchedIspMatrix)
            val shading = Camera2Mapping.shadingMap(shot.result)
            val dng = runCatching {
              MediaSaver.saveDng(getApplication(), name) { out ->
                DngCreator(shot.characteristics, shot.result).use { c ->
                  c.setOrientation(exifOrientation(rotation))
                  c.writeImage(out, shot.image)
                }
              }
              "DNG сохранён: Pictures/Zerno/$name.dng"
            }.getOrElse { "DNG не сохранён: ${it.message}" }
            Shot(name, raw, meta, shading, rotation, captureInfo(option, shot.result, raw, meta, shading)) to dng
          } finally {
            shot.image.close()
          }
        }
        _state.update { it.copy(shot = prepared, message = dngNote) }
        onDone()
        redevelop()
      } catch (e: Exception) {
        _state.update { it.copy(message = "Снимок не получился: ${e.message}") }
      } finally {
        _state.update { it.copy(busy = null) }
      }
    }
  }

  fun updateSettings(transform: (DevelopSettings) -> DevelopSettings) {
    _state.update { it.copy(settings = transform(it.settings)) }
    redevelop()
  }

  /** Быстрая проявка для экрана: около 2000 px по длинной стороне. */
  private fun redevelop() {
    val s = _state.value
    val shot = s.shot ?: return
    developJob?.cancel()
    developJob = viewModelScope.launch {
      val step = previewStep(shot.raw)
      val (bitmap, info) = withContext(Dispatchers.Default) {
        developBitmap(shot, s.settings, step) { coroutineContext.ensureActive() }
      }
      _state.update { it.copy(preview = bitmap, developInfo = info) }
    }
  }

  fun saveJpeg() {
    val s = _state.value
    val shot = s.shot ?: return
    if (s.busy != null) return
    viewModelScope.launch {
      _state.update { it.copy(busy = "Проявляю в половинном разрешении…") }
      val msg = withContext(Dispatchers.Default) {
        runCatching {
          val (bitmap, _) = developBitmap(shot, s.settings, step = 2)
          val suffix = "_${s.settings.profile.name.lowercase()}_${s.settings.strategy.name.lowercase()}"
          MediaSaver.saveJpeg(getApplication(), shot.name + suffix, bitmap)
          "JPEG сохранён: Pictures/Zerno/${shot.name}$suffix.jpg (${bitmap.width}×${bitmap.height})"
        }.getOrElse { "JPEG не сохранён: ${it.message}" }
      }
      _state.update { it.copy(busy = null, message = msg) }
    }
  }

  private fun developBitmap(
    shot: Shot,
    settings: DevelopSettings,
    step: Int,
    checkCancelled: () -> Unit = {},
  ): Pair<Bitmap, String> {
    val started = System.nanoTime()
    val pipeline = ColorPipelineResolver.resolve(shot.meta, settings.strategy)
    val img: DevelopedImage = RawDeveloper.develop(
      shot.raw, pipeline, shot.shading.takeIf { settings.useShading }, settings.profile, settings.exposureEv, step,
      checkCancelled,
    )
    val bitmap = Bitmap.createBitmap(img.argb, img.width, img.height, Bitmap.Config.ARGB_8888).rotated(shot.rotation)
    val ms = (System.nanoTime() - started) / 1_000_000
    val info = buildString {
      appendLine("Проявка: ${img.width}×${img.height}, шаг $step, $ms мс")
      appendLine("Цвет: ${pipeline.used.title}")
      appendLine("Баланс белого: " + pipeline.wbGains.joinToString { "%.3f".format(it) })
      appendLine("Матрица → sRGB: ${pipeline.srgbFromCamera}")
      pipeline.notes.forEach { appendLine("• $it") }
    }
    return bitmap to info
  }

  private fun previewStep(raw: RawImage): Int {
    val long = maxOf(raw.width, raw.height)
    val s = ceil(long / 2000.0).toInt().coerceAtLeast(2)
    return if (s % 2 == 0) s else s + 1
  }

  private fun captureInfo(option: CameraOption, r: CaptureResult, raw: RawImage, meta: ColorMetadata, shading: ShadingMap?) =
    buildString {
      appendLine("Камера: ${option.label}")
      appendLine("RAW: ${raw.width}×${raw.height}, CFA ${raw.cfa}, чёрный ${raw.blackLevel.joinToString()}, белый ${raw.whiteLevel}")
      appendLine("Выдержка: ${r.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { "1/%.0f с".format(1e9 / it) }}, " +
        "ISO ${r.get(CaptureResult.SENSOR_SENSITIVITY)}")
      appendLine("Нейтраль сенсора: ${meta.neutral?.joinToString { "%.4f".format(it) } ?: "нет"}")
      appendLine("Усиления ISP: ${meta.ispGains?.joinToString { "%.4f".format(it) } ?: "нет"}")
      appendLine("Матрица ISP: ${meta.ispMatrix ?: "нет"}")
      appendLine("Калибровок сенсора: ${meta.calibrations.size}")
      appendLine("Карта затенения: ${shading?.let { "${it.cols}×${it.rows}" } ?: "нет"}")
    }

  /** Значения тега EXIF Orientation: 1 — норма, 6 — 90°, 3 — 180°, 8 — 270°. */
  private fun exifOrientation(rotation: Int) = when (rotation) {
    90 -> 6
    180 -> 3
    270 -> 8
    else -> 1
  }

  private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees % 360 == 0) return this
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, m, true).also { if (it !== this) recycle() }
  }

  override fun onCleared() {
    controller.release()
  }
}
