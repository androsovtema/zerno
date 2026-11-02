package app.zerno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Камере нужна тёмная тема всегда: светлый интерфейс мешает оценивать снимок. */
val Amber = Color(0xFFFFB74D)
val Ink = Color(0xFF0B0B0C)
val Graphite = Color(0xFF1C1C1F)
val Muted = Color(0xFF8E8E93)

private val Scheme = darkColorScheme(
  primary = Amber,
  onPrimary = Color(0xFF2B1700),
  primaryContainer = Color(0xFF4A3000),
  onPrimaryContainer = Color(0xFFFFDDB3),
  secondary = Color(0xFFD6C4AE),
  // Выбранные чипы и неактивная часть слайдера — в тёплой гамме, а не фиолетовые по умолчанию.
  secondaryContainer = Color(0xFF4A3000),
  onSecondaryContainer = Color(0xFFFFDDB3),
  background = Ink,
  onBackground = Color(0xFFF2F2F2),
  surface = Ink,
  onSurface = Color(0xFFF2F2F2),
  surfaceVariant = Graphite,
  onSurfaceVariant = Color(0xFFC7C7CC),
  surfaceContainer = Graphite,
  surfaceContainerHigh = Color(0xFF26262A),
  outline = Color(0xFF48484A),
)

@Composable
fun ZernoTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = Scheme, typography = Typography, content = content)
}
