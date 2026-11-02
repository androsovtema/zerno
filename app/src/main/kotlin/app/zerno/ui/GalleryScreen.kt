package app.zerno.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.zerno.ShotEntry
import app.zerno.ShotRepository
import app.zerno.ui.theme.Muted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(vm: ZernoViewModel, onOpen: (String) -> Unit, onBack: () -> Unit) {
  val state by vm.state.collectAsStateWithLifecycle()
  Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      TextButton(onClick = onBack) { Text("← Камера", color = Color.White) }
      Spacer(Modifier.weight(1f))
      Text("${state.gallery.size} снимков", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(end = 16.dp))
    }
    if (state.gallery.isEmpty()) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Здесь будут ваши снимки.\nJPEG сохраняются в галерею телефона, в папку Zerno.",
          color = Muted, textAlign = TextAlign.Center)
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        items(state.gallery, key = { it.name }) { entry -> Cell(vm, entry, onOpen) }
      }
      Text(
        "Перепроявить можно последние кадры (до ${ShotRepository.MAX_SHOTS}), пока хватает места.",
        color = Muted, fontSize = 11.sp, modifier = Modifier.padding(12.dp),
      )
    }
  }
}

@Composable
private fun Cell(vm: ZernoViewModel, entry: ShotEntry, onOpen: (String) -> Unit) {
  val bmp by produceState<Bitmap?>(null, entry.name) { value = withContext(Dispatchers.IO) { vm.loadThumb(entry) } }
  Box(
    Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF1C1C1F))
      .clickable(enabled = entry.canRedevelop) { onOpen(entry.name) },
  ) {
    bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    if (!entry.canRedevelop) {
      Text("только JPEG", color = Color.White, fontSize = 10.sp,
        modifier = Modifier.align(Alignment.BottomStart).background(Color(0x99000000)).padding(4.dp))
    }
  }
}
