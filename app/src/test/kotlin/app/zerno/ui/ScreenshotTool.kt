package app.zerno.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.zerno.ShotEntry
import app.zerno.camera.CameraOption
import app.zerno.camera.CameraScan
import app.zerno.raw.Profile
import app.zerno.raw.SwatchScene
import app.zerno.ui.theme.Panel
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
    CameraOption("3", null, "3 · задняя ≈13 мм", Size(4096, 3072), false, 13, "0.6×"),
    CameraOption("2", null, "2 · задняя ≈23 мм", Size(4080, 3064), false, 23, "1×"),
    CameraOption("4", null, "4 · задняя ≈69 мм", Size(4096, 3072), false, 69, "3×"),
    CameraOption("5", null, "5 · задняя ≈229 мм", Size(4096, 3072), false, 229, "10×"),
  )

  private val swatches: Map<Profile, Bitmap> by lazy {
    Profile.entries.associateWith { p ->
      val img = SwatchScene.develop(p)
      Bitmap.createBitmap(img.argb, img.width, img.height, Bitmap.Config.ARGB_8888)
    }
  }

  private val fakeGallery: List<ShotEntry> by lazy {
    val tmp = java.nio.file.Files.createTempDirectory("zerno-shots").toFile()
    List(14) { i ->
      val raw = File(tmp, "s$i.zraw").apply { if (i != 4) writeText("x") }
      ShotEntry("ZERNO_$i", 1_000L * i, File(tmp, "s$i.jpg"), raw, null)
    }
  }

  private fun shoot(name: String, content: @Composable () -> Unit) {
    assumeTrue("ZERNO_SHOTS_DIR не задан", dir != null)
    compose.setContent { ZernoTheme { content() } }
    compose.waitForIdle()
    val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  private val fakePreview: @Composable (CameraOption) -> Unit = {
    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp))) {
      sample?.let {
        Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      } ?: Box(Modifier.fillMaxSize().background(Color(0xFF3A4A3A)))
    }
  }

  @Test
  fun cameraWithTip() = shoot("01_camera_tip") {
    CameraContent(
      UiState(scan = CameraScan(options, ""), selected = options[1], showTip = true, profilePreviews = swatches),
      granted = true, asked = false, flashAlpha = 0f, preview = fakePreview,
    )
  }

  @Test
  fun cameraShooting() = shoot("02_camera_processing") {
    CameraContent(
      UiState(
        scan = CameraScan(options, ""), selected = options[1], profile = Profile.WARM_FILM, saveDng = true,
        processing = 1, lastThumb = sample, lastShotName = "x", profilePreviews = swatches,
      ),
      granted = true, asked = false, flashAlpha = 0f, preview = fakePreview,
    )
  }

  @Test
  fun cameraNoPermission() = shoot("03_camera_permission") {
    CameraContent(
      UiState(scan = CameraScan(options, ""), selected = options[1], profilePreviews = swatches),
      granted = false, asked = true, flashAlpha = 0f, preview = fakePreview,
    )
  }

  @Test
  fun cameraNoRaw() = shoot("04_camera_no_raw") {
    CameraContent(UiState(scan = CameraScan(emptyList(), ""), selected = null), granted = true, asked = false,
      flashAlpha = 0f, preview = fakePreview)
  }

  @Test
  fun editor() = shoot("05_editor") {
    EditorContent(
      UiState(
        editor = EditorState(
          name = "x", settings = EditSettings(Profile.NATURAL, evOffset = 0.5),
          preview = sample, info = "Превью 1000×750\nЦвет: Типовая матрица",
        ),
        profilePreviews = swatches,
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

  @Test
  fun gallery() = shoot("07_gallery") {
    GalleryContent(fakeGallery, { e -> if (e.name.endsWith("3")) swatches[Profile.MONO] else sample }, {}, {})
  }

  @Test
  fun galleryEmpty() = shoot("08_gallery_empty") { GalleryContent(emptyList(), { null }, {}, {}) }

  @Test
  fun settings() = shoot("09_settings") {
    SettingsContent(UiState(saveDng = true), "0.3.0", {}, {}, {}, {}, {}, {})
  }

  @Test
  fun editorDetails() = shoot("10_editor_details") {
    EditorContent(
      UiState(editor = EditorState(name = "x", settings = EditSettings(Profile.MONO), preview = sample, profilePreviews = swatches)),
      name = "x", onBack = {}, onDelete = {}, onUpdate = {}, onSave = {},
    )
  }

  /** Лист выбора профиля — отдельно: ModalBottomSheet живёт в своём окне и в снимок экрана не попадает. */
  @Test
  fun profileSheet() = shoot("11_profile_sheet") {
    Box(Modifier.background(Panel).padding(top = 24.dp)) {
      ProfileSheetContent(Profile.WARM_FILM, swatches) {}
    }
  }
}
