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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.earlz.holocard.model.BankCard
import kotlin.random.Random

val BANK_SIZE = DpSize(340.dp, 214.dp)   // пропорции настоящей карты 85.6×54 мм
val BANK_CORNER = 16.dp

// Голограмма-наклейка: на лице — справа вверху, на обороте — справа внизу (dp внутри стороны)
private val HOLO_LEFT = 266.dp
private val HOLO_TOP = 22.dp
private val HOLO_SIZE = 50.dp
private val BACK_HOLO_LEFT = 266.dp
private val BACK_HOLO_TOP = 142.dp

/** Где фольга: сильно на голограмме, на металле — едва заметно. */
fun bankFoil(density: Float, back: Boolean): FoilSpec {
    val s = HOLO_SIZE.value * density
    val t = (if (back) BACK_HOLO_TOP else HOLO_TOP).value * density
    val l = if (back) {
        // Оборот в слое отражён по горизонтали, поэтому и голограмма — с другой стороны
        (BANK_SIZE.width - BACK_HOLO_LEFT - HOLO_SIZE).value * density
    } else {
        HOLO_LEFT.value * density
    }
    return FoilSpec(rect = Rect(l, t, l + s, t + s), inside = 1f, outside = 0.12f, metal = 1f)
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
            drawHologram(Rect(Offset(HOLO_LEFT.toPx(), HOLO_TOP.toPx()), Size(HOLO_SIZE.toPx(), HOLO_SIZE.toPx())))
        }

        Text(
            text = "COMPOSE",
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
            drawHologram(Rect(Offset(BACK_HOLO_LEFT.toPx(), BACK_HOLO_TOP.toPx()), Size(HOLO_SIZE.toPx(), HOLO_SIZE.toPx())))
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
            text = "Карта выпущена в Jetpack Compose.\nНайдёте — верните автору: он дебажит recomposition.",
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
    // На светлом металле шлифовка тёмная, на тёмном — светлая
    val streak = if (card.ink == Color.White) Color.White else Color.Black
    strokes.forEach { (y, a) ->
        drawLine(
            color = streak.copy(alpha = 0.015f + a * 0.035f),
            start = Offset(0f, y * size.height),
            end = Offset(size.width, y * size.height),
            strokeWidth = 1f,
        )
    }
}

/** Голограмма-наклейка: светлое серебро — фольга шейдера на нём особенно видна. */
private fun DrawScope.drawHologram(r: Rect) {
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0xFFDADDE3), Color(0xFF9DA3AD), Color(0xFFE9EBEF)), r.topLeft, r.bottomRight),
        r.topLeft, r.size, CornerRadius(8.dp.toPx()),
    )
    drawCircle(Color.White.copy(alpha = 0.5f), radius = r.width * 0.28f, center = r.center, style = Stroke(2.dp.toPx()))
}
