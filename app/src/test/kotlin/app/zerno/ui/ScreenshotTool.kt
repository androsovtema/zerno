package app.zerno.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.zerno.camera.CameraOption
import app.zerno.raw.ColorStrategy
import app.zerno.raw.Profile
import app.zerno.ui.theme.ZernoTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Скриншоты экранов на JVM (Robolectric, нативная графика) — чтобы проверять вёрстку без телефона.
 * Запуск: ZERNO_SHOTS_DIR=/куда/сохранить ./gradlew testDebugUnitTest --tests '*ScreenshotTool*'
 * Если в папке лежит sample.png — он подставляется как снимок. Без переменной тест пропускается.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class ScreenshotTool {
  @get:Rule val compose = createComposeRule()

  private val dir: File? = System.getenv("ZERNO_SHOTS_DIR")?.let(::File)

  private val sample: Bitmap? by lazy {
    dir?.let { File(it, "sample.png") }?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
  }

  private val options = listOf(
    CameraOption("2", null, "2 · задняя ≈14 мм", Size(4096, 3072), false, 14, "0.6×"),
    CameraOption("0", null, "0 · задняя ≈23 мм", Size(8192, 6144), false, 23, "1×"),
    CameraOption("3", null, "3 · задняя ≈70 мм", Size(4096, 3072), false, 70, "3×"),
    CameraOption("4", null, "4 · задняя ≈230 мм", Size(4096, 3072), false, 230, "10×"),
  )

  private fun shoot(name: String, content: @Composable () -> Unit) {
    assumeTrue("ZERNO_SHOTS_DIR не задан", dir != null)
    compose.setContent { ZernoTheme { content() } }
    compose.waitForIdle()
    val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private val fakePreview: @Composable (CameraOption) -> Unit = {
    Box(Modifier.fillMaxSize().background(Color(0xFF3A4A3A)))
  }


  @Test
  fun cameraWithTip() = shoot("01_camera_tip") {
    CameraContent(
      UiState(scan = null, selected = options[1], showTip = true, lastThumb = sample, lastShotName = "x"),
      granted = true, asked = false, flashAlpha = 0f,
      {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, preview = fakePreview,
    )
  }

  @Test
  fun cameraShooting() = shoot("02_camera_processing") {
    CameraContent(
      UiState(
        scan = app.zerno.camera.CameraScan(options, ""), selected = options[1], profile = Profile.WARM_FILM,
        processing = 1, lastThumb = sample, lastShotName = "x", message = "DNG не сохранён: пример сообщения",
      ),
      granted = true, asked = false, flashAlpha = 0f,
      {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, preview = fakePreview,
    )
  }

  @Test
  fun cameraNoPermission() = shoot("03_camera_permission") {
    CameraContent(
      UiState(scan = app.zerno.camera.CameraScan(options, ""), selected = options[1]),
      granted = false, asked = true, flashAlpha = 0f,
      {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, preview = fakePreview,
    )
  }

  @Test
  fun cameraNoRaw() = shoot("04_camera_no_raw") {
    CameraContent(
      UiState(scan = app.zerno.camera.CameraScan(emptyList(), ""), selected = null),
      granted = true, asked = false, flashAlpha = 0f,
      {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, preview = fakePreview,
    )
  }

  @Test
  fun editor() = shoot("05_editor") {
    EditorContent(
      UiState(
        editor = EditorState(
          name = "x", settings = EditSettings(Profile.NATURAL, evOffset = 0.5),
          preview = sample, info = "Превью 1000×750\nЦвет: Типовая матрица",
        ),
      ),
      name = "x", onBack = {}, onDelete = {}, onUpdate = {}, onSave = {},
    )
  }

  @Test
  fun editorError() = shoot("06_editor_error") {
    EditorContent(
      UiState(editor = EditorState(name = "x", settings = EditSettings(Profile.NATURAL), error = "RAW этого кадра уже удалён или повреждён")),
      name = "x", onBack = {}, onDelete = {}, onUpdate = {}, onSave = {},
    )
  }

  @Suppress("unused")
  private val keep = ColorStrategy.AUTO
}
