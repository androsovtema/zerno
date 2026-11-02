package app.zerno.ui

import android.app.Application
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureResult
import android.view.Surface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.zerno.CrashReporter
import app.zerno.EventLog
import app.zerno.Prefs
import app.zerno.ShotEntry
import app.zerno.ShotRepository
import app.zerno.camera.Camera2Mapping
import app.zerno.camera.CameraOption
import app.zerno.camera.CameraScan
import app.zerno.camera.CameraScanner
import app.zerno.camera.MediaSaver
import app.zerno.camera.RawCameraController
import app.zerno.raw.AutoExposure
import app.zerno.raw.ColorPipelineResolver
import app.zerno.raw.ColorStrategy
import app.zerno.raw.DevelopOptions
import app.zerno.raw.Profile
import app.zerno.raw.RawDeveloper
import app.zerno.raw.RawImage
import app.zerno.raw.Shot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Настройки перепроявки в редакторе. */
data class EditSettings(
  val profile: Profile,
  /** Поправка к автоэкспозиции, EV. */
  val evOffset: Double = 0.0,
  val strategy: ColorStrategy = ColorStrategy.AUTO,
  val shading: Boolean = true,
)

data class EditorState(
  val name: String,
  val shot: Shot? = null,
  val settings: EditSettings,
  val autoEv: Double = 0.0,
  val preview: Bitmap? = null,
  val info: String = "",
  val error: String? = null,
)

data class UiState(
  val scan: CameraScan? = null,
  val selected: CameraOption? = null,
  val profile: Profile = Profile.DEFAULT,
  val capturing: Boolean = false,
  /** Кадров в обработке (снятых, но ещё не сохранённых). */
  val processing: Int = 0,
  val lastThumb: Bitmap? = null,
  val lastShotName: String? = null,
  val message: String? = null,
  /** Растёт с каждым снимком — для вспышки на экране. */
  val shutterTick: Int = 0,
  val gallery: List<ShotEntry> = emptyList(),
  val editor: EditorState? = null,
  val busy: String? = null,
  val saveDng: Boolean = false,
  val shadingCorrection: Boolean = true,
  val crashReport: String? = null,
  val showTip: Boolean = false,
)

private class Pending(
  val shot: Shot,
  val characteristics: CameraCharacteristics,
  val result: CaptureResult,
  val profile: Profile,
  val saveDng: Boolean,
  val shading: Boolean,
)

class ZernoViewModel(app: Application) : AndroidViewModel(app) {

  private val manager = app.getSystemService(CameraManager::class.java)
  private val controller = RawCameraController(manager)
  private val prefs = Prefs(app)
  private val repo = ShotRepository(app)
  private val queue = Channel<Pending>(Channel.UNLIMITED)
  private val inFlight = AtomicInteger(0)
  private var editorJob: Job? = null

  /** Как держат телефон: 0, 90, 180, 270 (по датчику, интерфейс при этом остаётся портретным). */
  @Volatile var deviceOrientation: Int = 0

  /** Последний снятый кадр держим в памяти, чтобы редактор открывался мгновенно. */
  @Volatile private var lastShot: Shot? = null

  private val _state = MutableStateFlow(
    UiState(
      profile = prefs.profile,
      saveDng = prefs.saveDng,
      shadingCorrection = prefs.shadingCorrection,
      crashReport = CrashReporter.pending(app),
      showTip = !prefs.tipShown,
    ),
  )
  val state: StateFlow<UiState> = _state.asStateFlow()

  init {
    controller.onCameraLost = { reason ->
      EventLog.add(reason)
      _state.update { it.copy(message = "$reason. Вернитесь в приложение, чтобы переподключить.") }
    }
    viewModelScope.launch {
      val scan = withContext(Dispatchers.IO) { runCatching { CameraScanner(manager).scan() } }
      scan.onSuccess { s ->
        EventLog.add("Камеры для RAW: ${s.options.joinToString { "${it.zoomLabel}=${it.key}" }.ifEmpty { "нет" }}")
        val saved = s.options.firstOrNull { it.key == prefs.cameraKey }
        val main = s.options.firstOrNull { it.zoomLabel == "1×" } ?: s.options.firstOrNull()
        _state.update { it.copy(scan = s, selected = saved ?: main) }
      }.onFailure { e ->
        EventLog.add("Сканирование камер не удалось", e)
        _state.update { it.copy(message = "Не удалось опросить камеры: ${e.message}") }
      }
    }
    viewModelScope.launch { refreshGallery() }
    viewModelScope.launch(Dispatchers.Default) { for (p in queue) process(p) }
  }

  // ---------- Камера ----------

  fun select(option: CameraOption) {
    prefs.cameraKey = option.key
    _state.update { it.copy(selected = option) }
  }

  fun selectProfile(profile: Profile) {
    prefs.profile = profile
    _state.update { it.copy(profile = profile) }
  }

  fun previewSize(option: CameraOption) = controller.previewSize(option)

  fun openCamera(option: CameraOption, surface: Surface) {
    viewModelScope.launch {
      runCatching { controller.open(option, surface) }
        .onSuccess { EventLog.add("Открыта камера ${option.key} (${option.zoomLabel}), RAW ${option.rawSize}") }
        .onFailure { e ->
          EventLog.add("Не открылась камера ${option.key}", e)
          _state.update { it.copy(message = "Камера ${option.zoomLabel}: ${e.message}") }
        }
    }
  }

  fun closeCamera() = controller.close()

  fun dismissMessage() = _state.update { it.copy(message = null) }

  /** Не больше двух кадров в очереди: RAW 50 Мп занимает около 100 МБ памяти. */
  val canShoot: Boolean get() = inFlight.get() < 2

  fun capture() {
    val s = _state.value
    val option = s.selected ?: return
    if (s.capturing || !canShoot) return
    _state.update { it.copy(capturing = true, message = null) }
    viewModelScope.launch {
      try {
        val raw = controller.capture()
        _state.update { it.copy(shutterTick = it.shutterTick + 1) }
        val now = System.currentTimeMillis()
        val name = "ZERNO_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(now))
        val rotation = imageRotation(controller.sensorOrientation(option), option.front, deviceOrientation)
        val shot = withContext(Dispatchers.Default) {
          try {
            val image = Camera2Mapping.rawImage(raw.image, raw.characteristics, raw.result)
            val meta = Camera2Mapping.colorMetadata(raw.characteristics, raw.result, raw.latchedIspMatrix)
            val shading = Camera2Mapping.shadingMap(raw.result)
            Shot(name, now, image, meta, shading, rotation, captureInfo(option, raw.result, image, shading))
          } finally {
            raw.image.close()
          }
        }
        inFlight.incrementAndGet()
        _state.update { it.copy(processing = inFlight.get()) }
        queue.send(Pending(shot, raw.characteristics, raw.result, s.profile, s.saveDng, s.shadingCorrection))
        EventLog.add("Снят ${shot.name}: ${shot.raw.width}×${shot.raw.height}")
      } catch (e: Throwable) {
        EventLog.add("Снимок не получился", e)
        _state.update { it.copy(message = "Снимок не получился: ${e.message}") }
      } finally {
        _state.update { it.copy(capturing = false) }
      }
    }
  }

  private suspend fun process(p: Pending) {
    val shot = p.shot
    val started = System.nanoTime()
    try {
      val pipeline = ColorPipelineResolver.resolve(shot.meta, ColorStrategy.AUTO)
      val shading = shot.shading.takeIf { p.shading }
      val ev = AutoExposure.compute(shot.raw, pipeline, shading, p.profile)

      val thumb = toBitmap(RawDeveloper.develop(shot.raw, pipeline, shading,
        DevelopOptions(p.profile, ev, stepFor(shot.raw, 480), shot.rotation)))
      _state.update { it.copy(lastThumb = thumb, lastShotName = shot.name) }

      val bitmap = developFull(shot, pipeline, shading, DevelopOptions(p.profile, ev, 1, shot.rotation))
      val uri = MediaSaver.saveJpeg(getApplication(), shot.name, bitmap, shot.timestamp)
      bitmap.recycle()
      if (p.saveDng) {
        runCatching {
          MediaSaver.saveDng(getApplication(), shot.name, shot.raw, p.characteristics, p.result,
            exifOrientation(shot.rotation), shot.timestamp)
        }.onFailure { EventLog.add("DNG не сохранён", it) }
      }
      lastShot = shot
      runCatching { repo.save(shot, thumb, uri) }.onFailure { EventLog.add("Копия для перепроявки не сохранена", it) }
      EventLog.add("Готов ${shot.name}: ${p.profile.title}, ${"%+.2f".format(ev)} EV, " +
        "${pipeline.used.title}, ${(System.nanoTime() - started) / 1_000_000} мс")
      EventLog.add(colorReport(shot, pipeline))
      refreshGallery()
    } catch (e: Throwable) {
      EventLog.add("Обработка ${shot.name} не удалась", e)
      _state.update { it.copy(message = "Кадр не обработан: ${e.message}") }
    } finally {
      inFlight.decrementAndGet()
      _state.update { it.copy(processing = inFlight.get()) }
    }
  }

  /** Полное разрешение, если влезает в память; иначе — вдвое меньше. */
  private fun developFull(
    shot: Shot,
    pipeline: app.zerno.raw.ColorPipeline,
    shading: app.zerno.raw.ShadingMap?,
    options: DevelopOptions,
  ): Bitmap {
    val pixels = shot.raw.width.toLong() * shot.raw.height
    val steps = if (pixels <= 26_000_000L) listOf(1, 2, 4) else listOf(2, 4)
    var last: Throwable? = null
    for (step in steps) {
      try {
        return toBitmap(RawDeveloper.develop(shot.raw, pipeline, shading, options.copy(step = step)))
      } catch (e: OutOfMemoryError) {
        last = e
        EventLog.add("Мало памяти для шага $step, пробую меньше")
        System.gc()
      }
    }
    throw last ?: IllegalStateException("Проявка не удалась")
  }

  // ---------- Галерея и редактор ----------

  private suspend fun refreshGallery() {
    val (list, firstThumb) = withContext(Dispatchers.IO) {
      val l = repo.list()
      l to (if (_state.value.lastThumb == null) l.firstOrNull()?.let { repo.thumb(it) } else null)
    }
    _state.update { s ->
      val first = list.firstOrNull()
      s.copy(
        gallery = list,
        lastThumb = s.lastThumb ?: firstThumb,
        lastShotName = s.lastShotName ?: first?.name,
      )
    }
  }

  fun loadThumb(entry: ShotEntry): Bitmap? = repo.thumb(entry)

  fun openEditor(name: String) {
    val defaults = EditSettings(profile = _state.value.profile, shading = _state.value.shadingCorrection)
    _state.update { it.copy(editor = EditorState(name = name, settings = defaults)) }
    viewModelScope.launch {
      val shot = lastShot?.takeIf { it.name == name }
        ?: withContext(Dispatchers.IO) { runCatching { repo.load(name) } }.getOrElse { e ->
          EventLog.add("Не открылся RAW $name", e)
          _state.update { s -> s.copy(editor = s.editor?.copy(error = "RAW этого кадра уже удалён или повреждён")) }
          return@launch
        }
      lastShot = shot
      val ev = withContext(Dispatchers.Default) {
        val p = ColorPipelineResolver.resolve(shot.meta, ColorStrategy.AUTO)
        AutoExposure.compute(shot.raw, p, shot.shading.takeIf { defaults.shading }, defaults.profile)
      }
      _state.update { s -> s.copy(editor = s.editor?.copy(shot = shot, autoEv = ev)) }
      redevelop()
    }
  }

  fun closeEditor() {
    editorJob?.cancel()
    _state.update { it.copy(editor = null) }
  }

  fun updateEdit(transform: (EditSettings) -> EditSettings) {
    _state.update { s -> s.copy(editor = s.editor?.let { e -> e.copy(settings = transform(e.settings)) }) }
    redevelop()
  }

  private fun redevelop() {
    val e = _state.value.editor ?: return
    val shot = e.shot ?: return
    editorJob?.cancel()
    editorJob = viewModelScope.launch {
      val result = withContext(Dispatchers.Default) {
        runCatching {
          val started = System.nanoTime()
          val p = ColorPipelineResolver.resolve(shot.meta, e.settings.strategy)
          val step = stepFor(shot.raw, 2000)
          val img = RawDeveloper.develop(shot.raw, p, shot.shading.takeIf { e.settings.shading },
            DevelopOptions(e.settings.profile, e.autoEv + e.settings.evOffset, step, shot.rotation)) {
            coroutineContext.ensureActive()
          }
          val info = buildString {
            appendLine("Превью ${img.width}×${img.height}, шаг $step, ${(System.nanoTime() - started) / 1_000_000} мс")
            appendLine("Экспозиция: авто ${"%+.2f".format(e.autoEv)} EV, поправка ${"%+.1f".format(e.settings.evOffset)}")
            appendLine("Цвет: ${p.used.title}; баланс ${p.wbGains.joinToString { "%.3f".format(it) }}")
            appendLine("Матрица → sRGB: ${p.srgbFromCamera}")
            p.notes.forEach { appendLine("• $it") }
          }
          toBitmap(img) to info
        }
      }
      result.onSuccess { (bmp, info) ->
        _state.update { s -> s.copy(editor = s.editor?.copy(preview = bmp, info = info)) }
      }.onFailure { err ->
        if (err is kotlinx.coroutines.CancellationException) throw err
        EventLog.add("Перепроявка не удалась", err)
        _state.update { s -> s.copy(editor = s.editor?.copy(error = "Не удалось проявить: ${err.message}")) }
      }
    }
  }

  fun saveEdit() {
    val e = _state.value.editor ?: return
    val shot = e.shot ?: return
    if (_state.value.busy != null) return
    _state.update { it.copy(busy = "Сохраняю в полном разрешении…") }
    viewModelScope.launch(Dispatchers.Default) {
      val msg = runCatching {
        val p = ColorPipelineResolver.resolve(shot.meta, e.settings.strategy)
        val bmp = developFull(shot, p, shot.shading.takeIf { e.settings.shading },
          DevelopOptions(e.settings.profile, e.autoEv + e.settings.evOffset, 1, shot.rotation))
        val name = "${shot.name}_${e.settings.profile.name.lowercase()}_${System.currentTimeMillis() % 100000}"
        MediaSaver.saveJpeg(getApplication(), name, bmp, shot.timestamp)
        bmp.recycle()
        EventLog.add("Сохранена копия $name")
        "Сохранено в Pictures/Zerno"
      }.getOrElse {
        EventLog.add("Копия не сохранена", it)
        "Не сохранилось: ${it.message}"
      }
      _state.update { it.copy(busy = null, message = msg) }
    }
  }

  fun deleteShot(name: String) {
    viewModelScope.launch {
      withContext(Dispatchers.IO) { repo.delete(name) }
      if (lastShot?.name == name) lastShot = null
      _state.update { it.copy(lastThumb = null, lastShotName = null, editor = null) }
      refreshGallery()
    }
  }

  // ---------- Настройки ----------

  fun setSaveDng(v: Boolean) { prefs.saveDng = v; _state.update { it.copy(saveDng = v) } }

  fun setShading(v: Boolean) { prefs.shadingCorrection = v; _state.update { it.copy(shadingCorrection = v) } }

  fun dismissCrash() {
    CrashReporter.clear(getApplication())
    _state.update { it.copy(crashReport = null) }
  }

  fun eventLog(): String = EventLog.dump()

  // ---------- Вспомогательное ----------

  private fun toBitmap(img: app.zerno.raw.DevelopedImage): Bitmap =
    Bitmap.createBitmap(img.argb, img.width, img.height, Bitmap.Config.ARGB_8888)

  /** Чётный шаг, при котором длинная сторона результата не больше [target]. */
  private fun stepFor(raw: RawImage, target: Int): Int {
    val s = ceil(maxOf(raw.width, raw.height) / target.toDouble()).toInt().coerceAtLeast(2)
    return if (s % 2 == 0) s else s + 1
  }

  /** Всё о цвете кадра одной записью: по ней разбираем отчёты тестировщиков без самого RAW. */
  private fun colorReport(shot: Shot, pipeline: app.zerno.raw.ColorPipeline) = buildString {
    append("  ${shot.captureInfo.trim().replace("\n", "; ")}")
    append("; цвет: ${pipeline.used.title}")
    pipeline.sceneTemperature?.let { append(", сцена ≈${"%.0f".format(it)} K") }
    append(", WB ${pipeline.wbGains.joinToString("/") { "%.3f".format(it) }}")
    append(", матрица ISP: ${shot.meta.ispMatrix?.let { if (it.isIdentity()) "единичная" else "есть" } ?: "нет"}")
    if (pipeline.notes.isNotEmpty()) append("; ${pipeline.notes.joinToString("; ")}")
    append("; sRGB←сенсор: ${pipeline.srgbFromCamera}")
  }

  private fun captureInfo(option: CameraOption, r: CaptureResult, raw: RawImage, shading: app.zerno.raw.ShadingMap?) =
    buildString {
      appendLine("Камера: ${option.zoomLabel} — ${option.label}")
      appendLine("RAW: ${raw.width}×${raw.height}, CFA ${raw.cfa}, чёрный ${raw.blackLevel.joinToString()}, белый ${raw.whiteLevel}")
      appendLine("Выдержка: ${r.get(CaptureResult.SENSOR_EXPOSURE_TIME)?.let { "1/%.0f с".format(1e9 / it) }}, " +
        "ISO ${r.get(CaptureResult.SENSOR_SENSITIVITY)}")
      appendLine("Нейтраль: ${r.get(CaptureResult.SENSOR_NEUTRAL_COLOR_POINT)?.joinToString() ?: "нет"}")
      appendLine("Усиления ISP: ${r.get(CaptureResult.COLOR_CORRECTION_GAINS) ?: "нет"}")
      appendLine("Карта затенения: ${shading?.let { "${it.cols}×${it.rows}" } ?: "нет"}")
    }

  /** Формула из документации JPEG_ORIENTATION: поворот кадра с учётом того, как держат телефон. */
  private fun imageRotation(sensor: Int, front: Boolean, device: Int): Int {
    val d = if (front) -device else device
    return ((sensor + d) % 360 + 360) % 360
  }

  fun dismissTip() { prefs.tipShown = true; _state.update { it.copy(showTip = false) } }

  private fun exifOrientation(rotation: Int) = when (rotation) {
    90 -> 6
    180 -> 3
    270 -> 8
    else -> 1
  }

  override fun onCleared() {
    controller.release()
    queue.close()
  }
}
