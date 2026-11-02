package app.zerno.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zerno.R
import app.zerno.raw.Profile
import app.zerno.ui.theme.Accent
import app.zerno.ui.theme.Bg
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.OnBg
import app.zerno.ui.theme.Sans
import app.zerno.ui.theme.Stroke
import app.zerno.ui.theme.Panel
import app.zerno.ui.theme.SurfaceHigh
import app.zerno.ui.theme.Wordmark
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- Знак и логотип ----------

/** Знак — камера из рассыпанных точек-зёрен (вектор, тот же рисунок, что на иконке). */
@Composable
fun ZernoMark(height: Dp, modifier: Modifier = Modifier) {
  val painter = painterResource(R.drawable.zerno_mark)
  val ratio = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 1f }
  Image(painter, contentDescription = null, modifier = modifier.height(height).aspectRatio(ratio))
}

/** Логотип: знак + «зерно». */
@Composable
fun ZernoLogo(modifier: Modifier = Modifier, markHeight: Dp = 36.dp, textSize: TextUnit = 26.sp) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    ZernoMark(markHeight)
    Spacer(Modifier.width(10.dp))
    Text("зерно", fontFamily = Wordmark, fontWeight = FontWeight.SemiBold, fontSize = textSize, color = OnBg)
  }
}

/** Короткое описание профиля — для выбора. */
val Profile.tagline: String
  get() = when (this) {
    Profile.NATURAL -> "Близко к тому, что видит глаз"
    Profile.WARM_FILM -> "Тёплые тона, мягкий контраст, зерно"
    Profile.DIGICAM -> "Сочно и контрастно, как мыльница 2000-х"
    Profile.MONO -> "Чёрно-белый с зерном"
  }

// ---------- Иконки (рисуем сами, без библиотеки иконок) ----------

@Composable
fun TuneIcon(modifier: Modifier = Modifier, color: Color = OnBg) {
  Canvas(modifier.size(22.dp)) {
    val w = size.width
    val stroke = 1.7.dp.toPx()
    listOf(0.24f to 0.32f, 0.5f to 0.68f, 0.76f to 0.42f).forEach { (y, knob) ->
      drawLine(color, Offset(w * 0.06f, size.height * y), Offset(w * 0.94f, size.height * y), stroke, StrokeCap.Round)
      drawCircle(SurfaceHigh, 3.6.dp.toPx(), Offset(w * knob, size.height * y))
      drawCircle(color, 3.6.dp.toPx(), Offset(w * knob, size.height * y), style = Stroke(stroke))
    }
  }
}

@Composable
fun BackIcon(modifier: Modifier = Modifier, color: Color = OnBg) {
  Canvas(modifier.size(22.dp)) {
    val s = size.width
    val st = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    drawLine(color, Offset(s * 0.18f, s / 2), Offset(s * 0.85f, s / 2), st.width, StrokeCap.Round)
    drawPath(Path().apply { moveTo(s * 0.5f, s * 0.16f); lineTo(s * 0.16f, s / 2); lineTo(s * 0.5f, s * 0.84f) }, color, style = st)
  }
}

@Composable
fun ChevronIcon(modifier: Modifier = Modifier, color: Color = Muted, down: Boolean = false) {
  Canvas(modifier.size(16.dp)) {
    val s = size.width
    val p = if (down) Path().apply { moveTo(s * 0.2f, s * 0.35f); lineTo(s * 0.5f, s * 0.65f); lineTo(s * 0.8f, s * 0.35f) }
    else Path().apply { moveTo(s * 0.35f, s * 0.2f); lineTo(s * 0.65f, s * 0.5f); lineTo(s * 0.35f, s * 0.8f) }
    drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
}

/** Круглая кнопка-иконка на тёмной подложке. */
@Composable
fun CircleButton(onClick: () -> Unit, label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(
    modifier.size(44.dp).clip(CircleShape).background(SurfaceHigh.copy(alpha = 0.85f))
      .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { content() }
}

// ---------- Кнопки и выбор ----------

/** Главная кнопка: белая «пилюля». */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  Box(
    modifier.height(48.dp).clip(RoundedCornerShape(50)).background(if (enabled) OnBg else SurfaceHigh)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 22.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, color = if (enabled) Bg else Muted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

/** Второстепенная кнопка: серая «пилюля». */
@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  Box(
    modifier.height(48.dp).clip(RoundedCornerShape(50)).background(SurfaceHigh)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 22.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, color = if (enabled) OnBg else Muted, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

/** Текстовая кнопка. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Accent) {
  Box(
    modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
  ) {
    Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

/** Переключатель-«пилюля»: выбранная — белая. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
  Box(
    Modifier.height(36.dp).clip(RoundedCornerShape(50)).background(if (selected) OnBg else SurfaceHigh)
      .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, color = if (selected) Bg else OnBg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
  }
}

@Composable
fun ZernoSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
  Switch(
    checked = checked, onCheckedChange = onChange,
    colors = SwitchDefaults.colors(
      checkedThumbColor = Color.White, checkedTrackColor = Accent, checkedBorderColor = Accent,
      uncheckedThumbColor = Color(0xFFB0B0B5), uncheckedTrackColor = SurfaceHigh, uncheckedBorderColor = SurfaceHigh,
    ),
  )
}

// ---------- Экраны и списки ----------

/** Шапка экрана: «назад» слева, заголовок по центру, действия справа. */
@Composable
fun ScreenHeader(title: String?, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
  Box(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp)) {
    if (onBack != null) {
      CircleButton(onBack, "Назад", Modifier.align(Alignment.CenterStart)) { BackIcon() }
    }
    if (title != null) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = OnBg, modifier = Modifier.align(Alignment.Center))
    }
    Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
  }
}

/** Заголовок группы настроек. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
  Text(
    text.uppercase(), style = MaterialTheme.typography.titleSmall, color = Muted,
    modifier = modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
  )
}

/** Карточка-группа со строками и разделителями. */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
  Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel), content = content)
}

@Composable
fun GroupDivider() {
  HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = Stroke)
}

/** Строка в карточке: заголовок, подпись, справа — переключатель или стрелка. */
@Composable
fun GroupRow(
  title: String,
  subtitle: String? = null,
  onClick: (() -> Unit)? = null,
  titleColor: Color = OnBg,
  trailing: @Composable () -> Unit = {},
) {
  Row(
    Modifier.fillMaxWidth().heightIn(min = 56.dp)
      .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
      if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 2.dp))
    }
    trailing()
  }
}

// ---------- Профили ----------

/** Горизонтальный ряд профилей с превью — в редакторе. */
@Composable
fun ProfileRow(
  selected: Profile,
  previews: Map<Profile, Bitmap>,
  onSelect: (Profile) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Profile.entries.forEach { p ->
      Column(Modifier.width(92.dp).clickable(role = Role.RadioButton, onClick = { onSelect(p) })) {
        ProfilePreview(p, previews[p], p == selected, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
          p.title, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = if (p == selected) OnBg else Muted,
          maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
      }
    }
  }
}

/** Превью профиля в скруглённой рамке; выбранное — с акцентной обводкой. */
@Composable
fun ProfilePreview(profile: Profile, preview: Bitmap?, selected: Boolean, modifier: Modifier = Modifier) {
  val shape = RoundedCornerShape(14.dp)
  Box(
    modifier.clip(shape).background(SurfaceHigh)
      .then(if (selected) Modifier.border(2.dp, Accent, shape) else Modifier),
  ) {
    preview?.let {
      Image(it.asImageBitmap(), profile.title, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().padding(if (selected) 2.dp else 0.dp).clip(RoundedCornerShape(12.dp)))
    }
  }
}

/** Содержимое листа выбора профиля: карточки два в ряд с превью и описанием. */
@Composable
fun ProfileSheetContent(selected: Profile, previews: Map<Profile, Bitmap>, onSelect: (Profile) -> Unit) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
    Text("Профиль проявки", style = MaterialTheme.typography.titleMedium, color = OnBg,
      modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), textAlign = TextAlign.Center)
    Text("Применится к следующим снимкам", style = MaterialTheme.typography.bodySmall, color = Muted,
      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), textAlign = TextAlign.Center)
    Profile.entries.chunked(2).forEach { pair ->
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 14.dp)) {
        pair.forEach { p ->
          Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(role = Role.RadioButton) { onSelect(p) }) {
            ProfilePreview(p, previews[p], p == selected, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            Text(p.title, style = MaterialTheme.typography.labelLarge, color = OnBg, modifier = Modifier.padding(top = 8.dp, start = 2.dp))
            Text(p.tagline, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(start = 2.dp, top = 2.dp))
          }
        }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
      }
    }
  }
}

// ---------- Затвор ----------

/** Спуск: белое кольцо и белый диск. Простой и узнаваемый, без украшений. */
@Composable
fun ShutterButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.88f else 1f, label = "shutter")
  val color = if (enabled) OnBg else Color(0xFF5A5A5E)
  Box(
    Modifier.size(78.dp).border(3.5.dp, color, CircleShape)
      .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(62.dp).scale(scale).alpha(if (busy) 0.5f else 1f).clip(CircleShape).background(color))
  }
}

// ---------- Шкала экспозиции ----------

/** Поправка экспозиции −2…+2 EV: тёмная «пилюля» с делениями; касание или протяжка, шаг ½ EV. */
@Composable
fun ExposureRuler(value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  val labelStyle = TextStyle(fontFamily = Sans, fontSize = 11.sp, color = Muted)
  fun pick(x: Float, width: Float, pad: Float) {
    val t = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
    val v = ((t * 4 - 2) * 2).roundToInt() / 2.0
    if (abs(v - current) > 1e-6) change(v)
  }
  Column(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).padding(horizontal = 16.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Экспозиция", style = MaterialTheme.typography.labelMedium, color = Muted)
      Spacer(Modifier.weight(1f))
      Text("${"%+.1f".format(value).replace(',', '.')} EV", style = MaterialTheme.typography.labelLarge, color = OnBg)
    }
    Canvas(
      Modifier.fillMaxWidth().height(44.dp)
        .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width.toFloat(), 8.dp.toPx()) } }
        .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> pick(c.position.x, size.width.toFloat(), 8.dp.toPx()) } },
    ) {
      val pad = 8.dp.toPx()
      val w = size.width - 2 * pad
      val mid = 16.dp.toPx()
      for (i in 0..16) {
        val x = pad + w * i / 16f
        val major = i % 4 == 0
        val h = if (major) 16.dp.toPx() else 9.dp.toPx()
        drawLine(OnBg.copy(alpha = if (major) 0.75f else 0.3f), Offset(x, mid - h / 2), Offset(x, mid + h / 2), 1.5.dp.toPx(), StrokeCap.Round)
        if (major) {
          val v = i / 4 - 2
          val m = measurer.measure(if (v > 0) "+$v" else "$v", labelStyle)
          drawText(m, topLeft = Offset(x - m.size.width / 2f, mid + 12.dp.toPx()))
        }
      }
      val nx = pad + w * ((value + 2) / 4).toFloat().coerceIn(0f, 1f)
      drawLine(Accent, Offset(nx, mid - 13.dp.toPx()), Offset(nx, mid + 13.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
    }
  }
}

/** «24 кадра», «1 кадр», «5 кадров». */
fun framesLabel(n: Int): String {
  val mod100 = n % 100
  val mod10 = n % 10
  val word = when {
    mod100 in 11..14 -> "кадров"
    mod10 == 1 -> "кадр"
    mod10 in 2..4 -> "кадра"
    else -> "кадров"
  }
  return "$n $word"
}
