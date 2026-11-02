package app.zerno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Чистая тёмная тема: чёрный фон, серые карточки, белый текст и один акцент из знака.
 * В интерфейсе нет ни зерна, ни текстур: зерно — в снимках, и на чистом фоне его лучше видно.
 * Камере нужна тёмная тема всегда: светлый интерфейс мешает оценивать снимок.
 */

/** Фон (совпадает с @color/zerno_ink — окно и заставка). */
val Bg = Color(0xFF000000)

/** Карточки, листы. */
val Panel = Color(0xFF1C1C1E)

/** Кнопки и элементы на карточках. */
val SurfaceHigh = Color(0xFF2C2C2E)

/** Разделители в списках. */
val Stroke = Color(0xFF38383A)

/** Основной текст. */
val OnBg = Color(0xFFF5F5F7)

/** Второстепенный текст. */
val Muted = Color(0xFF8E8E93)

/** Акцент — киноварь из знака: выбранные элементы, переключатели. */
val Accent = Color(0xFFFF6A3D)

/** Цвета градиента знака (только для логотипа). */
val SigAmber = Color(0xFFFFC454)
val SigVermilion = Color(0xFFFF5C30)
val SigMagenta = Color(0xFFEC2A70)

/** Ошибки и удаление. */
val Danger = Color(0xFFFF453A)

private val Scheme = darkColorScheme(
  primary = Accent,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF4A1A0E),
  onPrimaryContainer = Color(0xFFFFDBD0),
  secondary = Color(0xFFE5E5EA),
  secondaryContainer = SurfaceHigh,
  onSecondaryContainer = OnBg,
  background = Bg,
  onBackground = OnBg,
  surface = Bg,
  onSurface = OnBg,
  surfaceVariant = Panel,
  onSurfaceVariant = Color(0xFFC7C7CC),
  surfaceContainer = Panel,
  surfaceContainerHigh = SurfaceHigh,
  surfaceContainerHighest = Color(0xFF3A3A3C),
  outline = Stroke,
  outlineVariant = Stroke,
  error = Danger,
)

@Composable
fun ZernoTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = Scheme, typography = Typography, content = content)
}
