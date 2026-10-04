package dev.earlz.holocard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.earlz.holocard.model.BankCard
import dev.earlz.holocard.model.CardPattern
import dev.earlz.holocard.model.HoloShape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val BANK_SIZE = DpSize(340.dp, 214.dp)   // пропорции настоящей карты 85.6×54 мм
val BANK_CORNER = 16.dp

// Голограмма на обороте — квадрат справа внизу (dp внутри стороны)
private val HOLO_SIZE = 50.dp
private val BACK_HOLO_LEFT = 266.dp
private val BACK_HOLO_TOP = 142.dp

/** Где голограмма на лицевой стороне, в dp: квадрат и круг — справа вверху, полоска — поперёк карты. */
private fun frontHoloDp(card: BankCard): Rect = when (card.hologram) {
    HoloShape.Square, HoloShape.Circle -> Rect(266f, 22f, 316f, 72f)
    HoloShape.Stripe -> Rect(0f, 58f, BANK_SIZE.width.value, 68f)
}

/** Где фольга: сильно на голограмме, на металле — едва заметно. */
fun bankFoil(card: BankCard, density: Float, back: Boolean): FoilSpec {
    val dp = if (back) {
        // Оборот в слое отражён по горизонтали, поэтому и голограмма — с другой стороны
        val left = (BANK_SIZE.width - BACK_HOLO_LEFT - HOLO_SIZE).value
        Rect(left, BACK_HOLO_TOP.value, left + HOLO_SIZE.value, BACK_HOLO_TOP.value + HOLO_SIZE.value)
    } else {
        frontHoloDp(card)
    }
    val px = Rect(dp.left * density, dp.top * density, dp.right * density, dp.bottom * density)
    return FoilSpec(rect = px, inside = 1f, outside = 0.12f, metal = 1f)
}

/** Лицевая сторона металлической карты. */
@Composable
fun BankFace(card: BankCard) {
    val strokes = rememberBrushStrokes()
    Box(
        modifier = Modifier
            .size(BANK_SIZE)
            .clip(RoundedCornerShape(BANK_CORNER)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawMetal(card, strokes)

            // Чип: золотая пластинка с контактами
            val chip = Rect(Offset(26.dp.toPx(), 78.dp.toPx()), Size(46.dp.toPx(), 36.dp.toPx()))
            drawRoundRect(
                Brush.linearGradient(listOf(Color(0xFFE8D08A), Color(0xFFB8913E), Color(0xFFF2E2A8)), chip.topLeft, chip.bottomRight),
                chip.topLeft, chip.size, CornerRadius(6.dp.toPx()),
            )
            val line = Color(0xFF8A6A2A).copy(alpha = 0.7f)
            listOf(0.33f, 0.66f).forEach { f ->
                drawLine(line, Offset(chip.left, chip.top + chip.height * f), Offset(chip.right, chip.top + chip.height * f), 1.2f)
            }
            drawLine(line, Offset(chip.center.x, chip.top), Offset(chip.center.x, chip.bottom), 1.2f)

            // Значок бесконтактной оплаты: три дуги
            val wave = Offset(chip.right + 18.dp.toPx(), chip.center.y)
            for (i in 1..3) {
                val r = (5 + i * 5).dp.toPx()
                drawArc(
                    color = card.ink.copy(alpha = 0.8f),
                    startAngle = -45f, sweepAngle = 90f, useCenter = false,
                    topLeft = Offset(wave.x - r, wave.y - r), size = Size(r * 2, r * 2),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
            val holo = frontHoloDp(card)
            drawHologram(
                Rect(holo.left.dp.toPx(), holo.top.dp.toPx(), holo.right.dp.toPx(), holo.bottom.dp.toPx()),
                card.hologram,
            )
        }

        Text(
            text = card.brand,
            color = card.ink.copy(alpha = 0.9f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp),
        )
        Text(
            text = card.number.replace(" ", "  "),
            color = card.ink,
            fontSize = 21.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            modifier = Modifier.offset(x = 24.dp, y = 136.dp),
        )
        Text(
            text = card.holder,
            color = card.ink.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 22.dp),
        )
        Text(
            text = card.expiry,
            color = card.ink.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 22.dp),
        )
    }
}

/** Оборот карты: магнитная полоса, полоса для подписи, CVV и голограмма. */
@Composable
fun BankBack(card: BankCard) {
    val strokes = rememberBrushStrokes()
    Box(
        modifier = Modifier
            .size(BANK_SIZE)
            .clip(RoundedCornerShape(BANK_CORNER)),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawMetal(card, strokes)
            // Магнитная полоса
            drawRect(Color(0xFF050506), Offset(0f, 22.dp.toPx()), Size(size.width, 42.dp.toPx()))
            // Полоса для подписи: светлая, в мелкую косую штриховку
            val sign = Rect(Offset(20.dp.toPx(), 84.dp.toPx()), Size(214.dp.toPx(), 34.dp.toPx()))
            drawRect(Color(0xFFE9E6DD), sign.topLeft, sign.size)
            var x = sign.left - sign.height
            while (x < sign.right) {
                val from = maxOf(x, sign.left)
                val to = minOf(x + sign.height, sign.right)
                drawLine(Color(0xFFCFCAB9), Offset(from, sign.top + (from - x)), Offset(to, sign.top + (to - x)), 1f)
                x += 6.dp.toPx()
            }
            // Окошко CVV
            drawRect(Color.White, Offset(242.dp.toPx(), 84.dp.toPx()), Size(56.dp.toPx(), 34.dp.toPx()))
            drawHologram(
                Rect(Offset(BACK_HOLO_LEFT.toPx(), BACK_HOLO_TOP.toPx()), Size(HOLO_SIZE.toPx(), HOLO_SIZE.toPx())),
                HoloShape.Square,
            )
        }
        Text(
            text = "Earl",
            color = Color(0xFF2B3A8C),
            fontSize = 20.sp,
            fontStyle = FontStyle.Italic,
            fontFamily = FontFamily.Cursive,
            modifier = Modifier.offset(x = 30.dp, y = 86.dp),
        )
        Text(
            text = card.cvv,
            color = Color.Black,
            fontSize = 15.sp,
            fontFamily = FontFamily.Monospace,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.offset(x = 255.dp, y = 91.dp),
        )
        Text(
            text = "Issued by Jetpack Compose Bank.\nIf found, please return to the author — busy debugging recomposition.",
            color = card.ink.copy(alpha = 0.55f),
            fontSize = 8.sp,
            lineHeight = 11.sp,
            modifier = Modifier.offset(x = 20.dp, y = 146.dp),
        )
    }
}

/** Шлифовка металла: тонкие горизонтальные штрихи случайной яркости, одни и те же каждый кадр. */
@Composable
private fun rememberBrushStrokes(): List<Pair<Float, Float>> = remember {
    val rnd = Random(42)
    List(220) { rnd.nextFloat() to rnd.nextFloat() }
}

private fun DrawScope.drawMetal(card: BankCard, strokes: List<Pair<Float, Float>>) {
    drawRect(Brush.linearGradient(card.metal, start = Offset.Zero, end = Offset(size.width, size.height)))
    // На светлом металле узор тёмный, на тёмном — светлый
    val ink = if (card.ink == Color.White) Color.White else Color.Black
    when (card.pattern) {
        // Шлифовка: тонкие горизонтальные штрихи случайной яркости
        CardPattern.Brushed -> strokes.forEach { (y, a) ->
            drawLine(
                color = ink.copy(alpha = 0.015f + a * 0.035f),
                start = Offset(0f, y * size.height),
                end = Offset(size.width, y * size.height),
                strokeWidth = 1f,
            )
        }
        // Сияние: плавные волны поперёк карты, каждая чуть сдвинута по фазе
        CardPattern.Waves -> repeat(18) { k ->
            val path = Path()
            val base = size.height * (k + 0.5f) / 18f
            var x = 0f
            while (x <= size.width) {
                val y = base + sin(x / size.width * 2f * PI.toFloat() * 1.3f + k * 0.45f) * size.height * 0.09f
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += 6f
            }
            drawPath(path, ink.copy(alpha = 0.06f + 0.03f * (k % 3)), style = Stroke(1.dp.toPx()))
        }
        // Гильош: вложенные «розетки», как на купюрах
        CardPattern.Guilloche -> {
            val c = Offset(size.width * 0.72f, size.height * 0.58f)
            repeat(9) { k ->
                val radius = size.height * (0.62f - k * 0.055f)
                val path = Path()
                val steps = 360
                for (i in 0..steps) {
                    val t = i / steps.toFloat() * 2f * PI.toFloat()
                    val r = radius * (0.82f + 0.18f * cos(14f * t + k * 0.6f))
                    val p = Offset(c.x + r * cos(t), c.y + r * sin(t))
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, ink.copy(alpha = 0.09f), style = Stroke(0.8.dp.toPx()))
            }
        }
    }
}

/** Голограмма: светлое серебро — фольга шейдера на нём особенно видна. */
private fun DrawScope.drawHologram(r: Rect, shape: HoloShape) {
    val silver = Brush.linearGradient(listOf(Color(0xFFDADDE3), Color(0xFF9DA3AD), Color(0xFFE9EBEF)), r.topLeft, r.bottomRight)
    when (shape) {
        HoloShape.Square -> {
            drawRoundRect(silver, r.topLeft, r.size, CornerRadius(8.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.5f), radius = r.width * 0.28f, center = r.center, style = Stroke(2.dp.toPx()))
        }
        HoloShape.Circle -> {
            drawCircle(silver, radius = r.width / 2, center = r.center)
            drawCircle(Color.White.copy(alpha = 0.55f), radius = r.width * 0.3f, center = r.center, style = Stroke(1.5.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.35f), radius = r.width * 0.15f, center = r.center, style = Stroke(1.5.dp.toPx()))
        }
        HoloShape.Stripe -> drawRect(silver, r.topLeft, r.size)
    }
}
