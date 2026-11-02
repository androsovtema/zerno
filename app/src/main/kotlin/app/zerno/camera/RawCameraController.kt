package app.zerno.camera

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import app.zerno.camera.Camera2Mapping.toMat3
import app.zerno.raw.Mat3
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** Снимок, ещё не скопированный из буфера камеры. Вызывающий обязан закрыть [image]. */
class RawShot(
  val image: Image,
  val result: CaptureResult,
  val characteristics: CameraCharacteristics,
  val latchedIspMatrix: Mat3?,
)

/** Минимальная RAW-камера на Camera2: превью в SurfaceTexture и снимок RAW_SENSOR. */
class RawCameraController(private val manager: CameraManager) {

  private val thread = HandlerThread("zerno-camera").apply { start() }
  private val handler = Handler(thread.looper)
  private val executor = Executor { handler.post(it) }

  private var device: CameraDevice? = null
  private var session: CameraCaptureSession? = null
  private var reader: ImageReader? = null
  private var option: CameraOption? = null
  private var characteristics: CameraCharacteristics? = null

  @Volatile private var latchedIsp: Mat3? = null
  @Volatile private var pendingImage: CompletableDeferred<Image>? = null

  /** Размер превью: то же соотношение сторон, что у RAW, не больше 1920 по длинной стороне. */
  fun previewSize(option: CameraOption): Size {
    val chars = characteristicsFor(option)
    val sizes = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
      ?.getOutputSizes(SurfaceTexture::class.java).orEmpty()
    val target = option.rawSize.width.toFloat() / option.rawSize.height
    return sizes.filter { maxOf(it.width, it.height) <= 1920 }
      .minWithOrNull(compareBy<Size>({ kotlin.math.abs(it.width.toFloat() / it.height - target) }, { -it.width }))
      ?: Size(1280, 960)
  }

  fun sensorOrientation(option: CameraOption): Int =
    characteristicsFor(option).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

  private fun characteristicsFor(option: CameraOption): CameraCharacteristics =
    manager.getCameraCharacteristics(option.physicalId ?: option.logicalId)

  @SuppressLint("MissingPermission") // разрешение проверяет UI до вызова
  suspend fun open(option: CameraOption, preview: Surface) {
    close()
    this.option = option
    latchedIsp = null
    val chars = characteristicsFor(option)
    characteristics = chars

    val cam = suspendCancellableCoroutine { cont ->
      manager.openCamera(option.logicalId, executor, object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) = cont.resume(camera)
        override fun onDisconnected(camera: CameraDevice) {
          camera.close()
          if (cont.isActive) cont.resumeWithException(IllegalStateException("Камера отключена"))
        }
        override fun onError(camera: CameraDevice, error: Int) {
          camera.close()
          if (cont.isActive) cont.resumeWithException(IllegalStateException("Ошибка камеры $error"))
        }
      })
    }
    device = cam

    val raw = ImageReader.newInstance(option.rawSize.width, option.rawSize.height, ImageFormat.RAW_SENSOR, 2)
    raw.setOnImageAvailableListener({ r ->
      val img = r.acquireNextImage() ?: return@setOnImageAvailableListener
      val waiting = pendingImage
      if (waiting != null && !waiting.isCompleted) waiting.complete(img) else img.close()
    }, handler)
    reader = raw

    val outputs = listOf(OutputConfiguration(preview), OutputConfiguration(raw.surface)).onEach { out ->
      option.physicalId?.let { out.setPhysicalCameraId(it) }
    }
    val s = suspendCancellableCoroutine { cont ->
      cam.createCaptureSession(SessionConfiguration(
        SessionConfiguration.SESSION_REGULAR, outputs, executor,
        object : CameraCaptureSession.StateCallback() {
          override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)
          override fun onConfigureFailed(session: CameraCaptureSession) {
            cont.resumeWithException(IllegalStateException("Устройство не настроило сессию с RAW для ${option.label}"))
          }
        },
      ))
    }
    session = s

    val request = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
      addTarget(preview)
      set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
      set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
      set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
    }.build()
    s.setSingleRepeatingRequest(request, executor, object : CameraCaptureSession.CaptureCallback() {
      override fun onCaptureCompleted(session: CameraCaptureSession, req: CaptureRequest, result: TotalCaptureResult) {
        if (latchedIsp != null) return
        val m = resultFor(result)?.get(CaptureResult.COLOR_CORRECTION_TRANSFORM)?.toMat3()
        if (m != null && !m.isIdentity()) latchedIsp = m
      }
    })
  }

  suspend fun capture(): RawShot {
    val cam = device ?: error("Камера не открыта")
    val s = session ?: error("Сессия не готова")
    val raw = reader ?: error("Нет RAW-потока")
    val chars = characteristics ?: error("Нет характеристик")

    val imageDeferred = CompletableDeferred<Image>().also { pendingImage = it }
    val resultDeferred = CompletableDeferred<CaptureResult>()
    val request = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
      addTarget(raw.surface)
      set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
      set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
      set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
      set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, CameraMetadata.STATISTICS_LENS_SHADING_MAP_MODE_ON)
    }.build()
    s.captureSingleRequest(request, executor, object : CameraCaptureSession.CaptureCallback() {
      override fun onCaptureCompleted(session: CameraCaptureSession, req: CaptureRequest, result: TotalCaptureResult) {
        resultDeferred.complete(resultFor(result) ?: result)
      }
      override fun onCaptureFailed(session: CameraCaptureSession, req: CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
        resultDeferred.completeExceptionally(IllegalStateException("Снимок не удался, причина ${failure.reason}"))
      }
    })
    return withTimeout(15_000) {
      val result = resultDeferred.await()
      val image = imageDeferred.await()
      RawShot(image, result, chars, latchedIsp)
    }
  }

  /** Для физического модуля берём его собственный результат, иначе — логический. */
  private fun resultFor(result: TotalCaptureResult): CaptureResult? {
    val pid = option?.physicalId ?: return result
    return if (Build.VERSION.SDK_INT >= 31) {
      result.physicalCameraTotalResults[pid]
    } else {
      @Suppress("DEPRECATION")
      result.physicalCameraResults[pid]
    }
  }

  fun close() {
    runCatching { session?.close() }
    runCatching { device?.close() }
    runCatching { reader?.close() }
    session = null
    device = null
    reader = null
  }

  fun release() {
    close()
    thread.quitSafely()
  }
}
