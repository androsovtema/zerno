package app.zerno.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.zerno.R

/** Только для слова «зерно» в логотипе (Unbounded, сделан под кириллицу). */
val Wordmark = FontFamily(Font(R.font.unbounded_semibold, FontWeight.SemiBold))

/** Шрифт интерфейса — нейтральный современный гротеск Onest. */
val Sans = FontFamily(
  Font(R.font.onest_regular, FontWeight.Normal),
  Font(R.font.onest_medium, FontWeight.Medium),
  Font(R.font.onest_semibold, FontWeight.SemiBold),
)

val Typography = Typography(
  headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
  titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
  titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
  titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp),
  bodyLarge = TextStyle(fontFamily = Sans, fontSize = 17.sp, lineHeight = 22.sp),
  bodyMedium = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 20.sp),
  bodySmall = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 18.sp),
  labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp),
  labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp),
  labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
