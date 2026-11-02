package app.zerno.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.ShotEntry
import app.zerno.ShotRepository
import app.zerno.ui.theme.Cream
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.Panel
import app.zerno.ui.theme.zernoBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(vm: ZernoViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  GalleryContent(state.gallery, vm::loadThumb, onOpen, onBack)
}

/** «Плёнка»: снимки контактным листом, с номерами кадров на краю. */
@Composable
fun GalleryContent(
  gallery: List<ShotEntry>,
  loadThumb: (ShotEntry) -> Bitmap?,
  onOpen: (String) -> Unit,
  onBack: () -> Unit,
) {
  Column(Modifier.fillMaxSize().zernoBackground().safeDrawingPadding()) {
    ScreenBar(title = "Плёнка", onBack = onBack, backLabel = "Камера") {
      if (gallery.isNotEmpty()) EdgeText(framesLabel(gallery.size), color = Muted, modifier = Modifier.padding(end = 12.dp))
    }
    if (gallery.isEmpty()) {
      Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
      ) {
        ZernoMark(56.dp, Modifier.alpha(0.8f))
        Spacer(Modifier.height(16.dp))
        Text("Плёнка пока пустая", style = MaterialTheme.typography.titleLarge, color = Cream)
        Spacer(Modifier.height(8.dp))
        Text("Каждый снимок попадёт сюда, а JPEG — в галерею телефона, в папку Zerno.",
          color = Muted, textAlign = TextAlign.Center)
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        itemsIndexed(gallery, key = { _, e -> e.name }) { index, entry ->
          Frame(entry, number = gallery.size - index, loadThumb, onOpen)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
          Text(
            "Перепроявить можно последние ${ShotRepository.MAX_SHOTS} кадров, пока хватает места.",
            color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 16.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun Frame(entry: ShotEntry, number: Int, loadThumb: (ShotEntry) -> Bitmap?, onOpen: (String) -> Unit) {
  val bmp by produceState<Bitmap?>(null, entry.name) { value = withContext(Dispatchers.IO) { loadThumb(entry) } }
  Column(Modifier.clip(RoundedCornerShape(4.dp)).clickable(enabled = entry.canRedevelop) { onOpen(entry.name) }) {
    Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(3.dp)).background(Panel)) {
      bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      EdgeText("%02d".format(number), size = 9.sp)
      if (!entry.canRedevelop) EdgeText("  только JPEG", color = Muted, size = 8.sp, spacing = 0.4.sp)
    }
  }
}
