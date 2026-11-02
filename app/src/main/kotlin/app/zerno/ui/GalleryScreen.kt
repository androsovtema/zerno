package app.zerno.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.ShotEntry
import app.zerno.ShotRepository
import app.zerno.ui.theme.Bg
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.OnBg
import app.zerno.ui.theme.Panel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(vm: ZernoViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  GalleryContent(state.gallery, vm::loadThumb, onOpen, onBack)
}

/** Галерея: сетка снимков, как в «Фото». */
@Composable
fun GalleryContent(
  gallery: List<ShotEntry>,
  loadThumb: (ShotEntry) -> Bitmap?,
  onOpen: (String) -> Unit,
  onBack: () -> Unit,
) {
  Column(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
    ScreenHeader(title = "Снимки", onBack = onBack) {
      if (gallery.isNotEmpty()) {
        Text(framesLabel(gallery.size), style = MaterialTheme.typography.labelMedium, color = Muted, modifier = Modifier.padding(end = 6.dp))
      }
    }
    if (gallery.isEmpty()) {
      Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
      ) {
        ZernoMark(44.dp)
        Spacer(Modifier.height(20.dp))
        Text("Пока пусто", style = MaterialTheme.typography.titleLarge, color = OnBg)
        Spacer(Modifier.height(8.dp))
        Text("Снимки появятся здесь, а JPEG — в галерее телефона, в папке Zerno.",
          style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        items(gallery, key = { it.name }) { entry -> Cell(entry, loadThumb, onOpen) }
        item(span = { GridItemSpan(maxLineSpan) }) {
          Text(
            "Перепроявить можно последние ${ShotRepository.MAX_SHOTS} снимков, пока хватает места.",
            style = MaterialTheme.typography.bodySmall, color = Muted, textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 20.dp, horizontal = 16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun Cell(entry: ShotEntry, loadThumb: (ShotEntry) -> Bitmap?, onOpen: (String) -> Unit) {
  val bmp by produceState<Bitmap?>(null, entry.name) { value = withContext(Dispatchers.IO) { loadThumb(entry) } }
  Box(
    Modifier.fillMaxWidth().aspectRatio(1f).background(Panel)
      .clickable(enabled = entry.canRedevelop) { onOpen(entry.name) },
  ) {
    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    if (!entry.canRedevelop) {
      Text(
        "JPEG", style = MaterialTheme.typography.labelSmall, color = OnBg,
        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
          .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp),
      )
    }
  }
}
