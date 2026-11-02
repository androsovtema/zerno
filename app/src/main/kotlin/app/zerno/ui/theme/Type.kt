package app.zerno.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.zerno.R

/** Антиква для логотипа и заголовков — интонация фотожурналов плёночной эпохи. */
val Display = FontFamily(Font(R.font.prata_regular, FontWeight.Normal))

/** Моноширинный — как надписи на краю плёнки: номера кадров, экспозиция, подписи. */
val Mono = FontFamily(
  Font(R.font.plex_mono_regular, FontWeight.Normal),
  Font(R.font.plex_mono_medium, FontWeight.Medium),
)

/** Подпись «на краю плёнки»: моноширинная, прописная, с разрядкой. */
val EdgePrint = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.2.sp)

val Typography = Typography(
  headlineSmall = TextStyle(fontFamily = Display, fontSize = 26.sp, lineHeight = 32.sp),
  titleLarge = TextStyle(fontFamily = Display, fontSize = 22.sp, lineHeight = 28.sp),
  titleMedium = TextStyle(fontFamily = Display, fontSize = 18.sp, lineHeight = 24.sp),
  bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
  bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
  bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
  labelLarge = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 1.sp),
  labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp),
  labelSmall = EdgePrint,
)
