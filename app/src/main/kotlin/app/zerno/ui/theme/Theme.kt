package app.zerno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Палитра «проявочной»: тёплый почти чёрный, кремовая бумага, янтарь надписей на краю плёнки.
 * Камере нужна тёмная тема всегда: светлый интерфейс мешает оценивать снимок.
 */

/** Основной фон (совпадает с @color/zerno_ink — окно и заставка). */
val Ink = Color(0xFF15100D)

/** Низ фонового градиента. */
val InkDeep = Color(0xFF0D0A08)

/** Верх фонового градиента — тёплый отсвет. */
val Umber = Color(0xFF261B14)

/** Подложка плёнки (лента профилей, контактный лист). */
val FilmBase = Color(0xFF0B0807)

/** Панели и карточки. */
val Panel = Color(0xFF1F1712)
val PanelHigh = Color(0xFF2A1F18)

/** Волосяные линии и рамки. */
val Hairline = Color(0xFF3D2F25)

/** Текст — цвет фотобумаги. */
val Cream = Color(0xFFF3E6D0)

/** Второстепенный текст. */
val Muted = Color(0xFF9C8B7A)

/** Акцент — как надписи на краю плёнки. */
val Amber = Color(0xFFFFB061)

/** Тёплый конец градиента знака. */
val Ember = Color(0xFFE0683A)

/** Красный фонарь проявочной — для ошибок и удаления. */
val Safelight = Color(0xFFD9573F)

private val Scheme = darkColorScheme(
  primary = Amber,
  onPrimary = Color(0xFF2B1700),
  primaryContainer = Color(0xFF4A3000),
  onPrimaryContainer = Color(0xFFFFDDB3),
  secondary = Color(0xFFD6C4AE),
  // Выбранные элементы и неактивная часть слайдера — в тёплой гамме, а не фиолетовые по умолчанию.
  secondaryContainer = Color(0xFF4A3000),
  onSecondaryContainer = Color(0xFFFFDDB3),
  background = Ink,
  onBackground = Cream,
  surface = Ink,
  onSurface = Cream,
  surfaceVariant = Panel,
  onSurfaceVariant = Color(0xFFD2C3B2),
  surfaceContainer = Panel,
  surfaceContainerHigh = PanelHigh,
  surfaceContainerHighest = Color(0xFF35281F),
  outline = Hairline,
  outlineVariant = Hairline,
  error = Safelight,
)

@Composable
fun ZernoTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = Scheme, typography = Typography, content = content)
}
