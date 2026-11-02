package app.zerno.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.zerno.R
import app.zerno.raw.Profile
import app.zerno.ui.theme.Amber
import app.zerno.ui.theme.Cream
import app.zerno.ui.theme.Display
import app.zerno.ui.theme.EdgePrint
import app.zerno.ui.theme.FilmBase
import app.zerno.ui.theme.Hairline
import app.zerno.ui.theme.Ink
import app.zerno.ui.theme.Mono
import app.zerno.ui.theme.Muted
import app.zerno.ui.theme.Panel
import app.zerno.ui.theme.grain
import app.zerno.ui.theme.grainyFill
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- Знак и логотип ----------

/** Знак — зерно с плёночной текстурой (тот же рисунок, что на иконке). */
@Composable
fun ZernoMark(size: Dp, modifier: Modifier = Modifier) {
  Image(painterResource(R.drawable.zerno_mark), contentDescription = null, modifier = modifier.size(size))
}

/** Логотип: знак + «зерно» антиквой. */
@Composable
fun ZernoLogo(modifier: Modifier = Modifier, markSize: Dp = 26.dp, textSize: TextUnit = 22.sp) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    ZernoMark(markSize)
    Spacer(Modifier.width(4.dp))
    Text("зерно", fontFamily = Display, fontSize = textSize, color = Cream)
  }
}

// ---------- Текст и кнопки ----------

/** Надпись «на краю плёнки»: моноширинная, прописная, с разрядкой. */
@Composable
fun EdgeText(
  text: String,
  modifier: Modifier = Modifier,
  color: Color = Amber,
  size: TextUnit = 10.sp,
  spacing: TextUnit = 1.2.sp,
) {
  Text(
    text.uppercase(), modifier = modifier, color = color, maxLines = 1, softWrap = false,
    overflow = TextOverflow.Clip, style = EdgePrint.copy(fontSize = size, letterSpacing = spacing),
  )
}

/** Текстовая кнопка в стиле надписей на плёнке (вместо Material TextButton). */
@Composable
fun EdgeButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Cream, enabled: Boolean = true) {
  Box(
    modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 10.dp),
  ) {
    EdgeText(text, color = if (enabled) color else Muted, size = 11.sp)
  }
}

/** Главная кнопка: кремовая «фотобумага» с зерном. */
@Composable
fun PaperButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
  val shape = RoundedCornerShape(50)
  Box(
    modifier.height(44.dp).clip(shape)
      .grainyFill(if (enabled) Cream else Panel, shape, alpha = 0.16f)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 20.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, color = if (enabled) Ink else Muted, fontSize = 15.sp, maxLines = 1)
  }
}

/** Второстепенная кнопка: волосяной контур. */
@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Cream, enabled: Boolean = true) {
  val shape = RoundedCornerShape(50)
  Box(
    modifier.height(44.dp).clip(shape).border(1.dp, Hairline, shape)
      .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, color = if (enabled) color else Muted, fontSize = 15.sp, maxLines = 1)
  }
}

/** Переключатель-«пилюля» с моноширинной подписью (вместо FilterChip). */
@Composable
fun MonoChip(text: String, selected: Boolean, onClick: () -> Unit) {
  val shape = RoundedCornerShape(50)
  Box(
    Modifier.height(34.dp).clip(shape)
      .then(if (selected) Modifier.grainyFill(Amber.copy(alpha = 0.16f), shape) else Modifier)
      .border(1.dp, if (selected) Amber else Hairline, shape)
      .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    EdgeText(text, color = if (selected) Amber else Cream, size = 11.sp, spacing = 0.6.sp)
  }
}

/** Панель-карточка: тёплая подложка с зерном и волосяной рамкой. */
fun Modifier.panel(shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(18.dp)): Modifier =
  clip(shape).grainyFill(Panel.copy(alpha = 0.92f), shape).border(1.dp, Hairline, shape)

/** Верхняя панель экрана: «← НАЗАД» моноширинным, заголовок антиквой, действия справа. */
@Composable
fun ScreenBar(
  title: String?,
  onBack: (() -> Unit)?,
  backLabel: String = "Назад",
  actions: @Composable RowScope.() -> Unit = {},
) {
  Row(
    Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (onBack != null) EdgeButton("←  $backLabel", onBack)
    Spacer(Modifier.weight(1f))
    actions()
  }
  if (title != null) {
    Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall, color = Cream,
      modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
  }
}

/** Заголовок раздела: моноширинный, янтарный. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  EdgeText(text, modifier = modifier.padding(top = 20.dp, bottom = 8.dp), size = 11.sp)
}

/** Иконка настроек: три «ползунка» — рисуем сами, без библиотеки иконок. */
@Composable
fun TuneIcon(modifier: Modifier = Modifier, color: Color = Cream) {
  Canvas(modifier.size(22.dp)) {
    val w = size.width
    val stroke = 1.6.dp.toPx()
    listOf(0.22f to 0.30f, 0.5f to 0.68f, 0.78f to 0.42f).forEach { (y, knob) ->
      drawLine(color.copy(alpha = 0.7f), Offset(w * 0.08f, size.height * y), Offset(w * 0.92f, size.height * y), stroke, StrokeCap.Round)
      drawCircle(Ink, 3.4.dp.toPx(), Offset(w * knob, size.height * y))
      drawCircle(color, 3.4.dp.toPx(), Offset(w * knob, size.height * y), style = Stroke(stroke))
    }
  }
}

// ---------- Лента профилей ----------

/**
 * Профили — как кадры на плёнке: подложка с перфорацией, в каждом кадре — превью
 * (последнего снимка или сцены-образца), подпись на краю плёнки.
 */
@Composable
fun FilmStrip(
  selected: Profile,
  previews: Map<Profile, Bitmap>,
  onSelect: (Profile) -> Unit,
  modifier: Modifier = Modifier,
) {
  val holeColor = Color(0xFF2C221B)
  Row(
    modifier.fillMaxWidth().background(FilmBase).horizontalScroll(rememberScrollState())
      .drawBehind {
        // Перфорация сверху и снизу, движется вместе с лентой.
        val hw = 5.dp.toPx()
        val hh = 6.dp.toPx()
        val pitch = 13.dp.toPx()
        val r = CornerRadius(1.5.dp.toPx())
        var x = 4.dp.toPx()
        while (x < size.width) {
          drawRoundRect(holeColor, Offset(x, 3.dp.toPx()), Size(hw, hh), r)
          drawRoundRect(holeColor, Offset(x, size.height - 3.dp.toPx() - hh), Size(hw, hh), r)
          x += pitch
        }
      }
      .grain(0.08f)
      .padding(horizontal = 12.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Profile.entries.forEachIndexed { i, p ->
      val sel = p == selected
      Column(
        Modifier.width(84.dp).clip(RoundedCornerShape(4.dp))
          .clickable(role = Role.RadioButton, onClick = { onSelect(p) }),
      ) {
        val frameShape = RoundedCornerShape(3.dp)
        Box(
          Modifier.fillMaxWidth().height(58.dp).clip(frameShape).background(Color(0xFF1A1310))
            .border(if (sel) 1.5.dp else 0.5.dp, if (sel) Amber else Hairline, frameShape),
        ) {
          previews[p]?.let {
            Image(
              it.asImageBitmap(), p.title, contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize().alpha(if (sel) 1f else 0.72f),
            )
          }
        }
        Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
          EdgeText(
            "${i + 1}${if (sel) "▸" else " "}${p.title}",
            color = if (sel) Amber else Muted, size = 8.sp, spacing = 0.2.sp,
          )
        }
      }
    }
  }
}

// ---------- Затвор ----------

/**
 * Спуск: кольцо с насечками, как у объектива, поворачивается на каждый кадр («взвод»);
 * внутри — кремовый диск с зерном.
 */
@Composable
fun ShutterButton(enabled: Boolean, busy: Boolean, tick: Int, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "shutter")
  val turn by animateFloatAsState(tick * 30f, tween(520), label = "advance")
  val ringColor = if (enabled) Cream else Muted
  Box(
    Modifier.size(84.dp).clickable(interactionSource = interaction, indication = null, enabled = enabled,
      role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.fillMaxSize().rotate(turn)) {
      val r = size.minDimension / 2
      drawCircle(ringColor.copy(alpha = 0.9f), r - 1.dp.toPx(), style = Stroke(1.4.dp.toPx()))
      for (i in 0 until 60) {
        val long = i % 5 == 0
        rotate(i * 6f) {
          drawLine(
            ringColor.copy(alpha = if (long) 0.85f else 0.4f),
            Offset(center.x, 4.dp.toPx()),
            Offset(center.x, (if (long) 9.5f else 7f).dp.toPx()),
            1.dp.toPx(),
          )
        }
      }
    }
    val disc = Brush.radialGradient(
      listOf(Color(0xFFFFF4E0), Cream, Color(0xFFE2BE8E)),
      center = Offset(22f, 18f), radius = 220f,
    )
    Box(
      Modifier.size(62.dp).scale(scale).alpha(if (busy) 0.55f else 1f).clip(CircleShape)
        .grainyFill(if (enabled) disc else Brush.linearGradient(listOf(Panel, Panel)), CircleShape, alpha = 0.09f),
    )
  }
}

// ---------- Шкала экспозиции ----------

/** Шкала поправки экспозиции −2…+2 EV, как на диске фотоаппарата: касание или протяжка, шаг ½ EV. */
@Composable
fun ExposureScale(value: Double, onChange: (Double) -> Unit, modifier: Modifier = Modifier) {
  val measurer = rememberTextMeasurer()
  val current by rememberUpdatedState(value)
  val change by rememberUpdatedState(onChange)
  val labelStyle = TextStyle(fontFamily = Mono, fontSize = 10.sp, color = Muted)
  Column(modifier.fillMaxWidth()) {
    Row(verticalAlignment = Alignment.Bottom) {
      EdgeText("Экспозиция", color = Muted, size = 11.sp)
      Spacer(Modifier.weight(1f))
      Text("${"%+.1f".format(value).replace(',', '.')} EV", fontFamily = Mono, fontSize = 15.sp, color = Amber)
    }
    fun pick(x: Float, width: Float, pad: Float) {
      val t = ((x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
      val v = ((t * 4 - 2) * 2).roundToInt() / 2.0
      if (abs(v - current) > 1e-6) change(v)
    }
    Canvas(
      Modifier.fillMaxWidth().height(54.dp)
        .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width.toFloat(), 12.dp.toPx()) } }
        .pointerInput(Unit) {
          detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat(), 12.dp.toPx()) }
        },
    ) {
      val pad = 12.dp.toPx()
      val w = size.width - 2 * pad
      val baseY = 20.dp.toPx()
      for (i in 0..40) {
        val x = pad + w * i / 40f
        val major = i % 10 == 0
        val half = i % 5 == 0
        val h = when { major -> 14.dp.toPx(); half -> 9.dp.toPx(); else -> 5.dp.toPx() }
        drawLine(Cream.copy(alpha = if (major) 0.8f else 0.35f), Offset(x, baseY - h), Offset(x, baseY), 1.dp.toPx())
        if (major) {
          val v = i / 10 - 2
          val label = if (v > 0) "+$v" else "$v"
          val m = measurer.measure(label, labelStyle)
          drawText(m, topLeft = Offset(x - m.size.width / 2f, baseY + 8.dp.toPx()))
        }
      }
      val nx = pad + w * ((value + 2) / 4).toFloat().coerceIn(0f, 1f)
      drawLine(Amber, Offset(nx, 0f), Offset(nx, baseY + 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
      val tri = Path().apply {
        moveTo(nx - 5.dp.toPx(), 0f); lineTo(nx + 5.dp.toPx(), 0f); lineTo(nx, 6.dp.toPx()); close()
      }
      drawPath(tri, Amber)
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
