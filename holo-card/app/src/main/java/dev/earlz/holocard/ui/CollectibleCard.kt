package dev.earlz.holocard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Фото для карты: целиком и вырезанный человек (координаты [subjectBounds] — в пикселях фото). */
class CardArt(val photo: ImageBitmap, val subject: ImageBitmap?, val subjectBounds: Rect?)

// Раскладка карты, dp от левого верхнего угла самой карты
private val PAD = 14.dp
private val HEADER = 30.dp
private const val ART_HEIGHT_FRACTION = 0.5f

/** Человек выше окна картинки во столько раз — его голова «вылезает» из рамки. */
private const val POP_OUT = 1.3f

val COLLECTIBLE_SIZE = DpSize(300.dp, 419.dp)   // пропорции настоящей карты 63×88 мм
val COLLECTIBLE_OVERFLOW = 64.dp

/** Окно картинки внутри карты, px. */
fun collectibleArtRect(cardWidth: Float, cardHeight: Float, density: Float): Rect {
    val pad = PAD.value * density
    val top = pad + HEADER.value * density
    return Rect(pad, top, cardWidth - pad, top + cardHeight * ART_HEIGHT_FRACTION)
}

/** Где фольга: сильно на рамке, слабо на картинке. */
fun collectibleFoil(cardPx: Rect, density: Float) = FoilSpec(
    rect = collectibleArtRect(cardPx.width, cardPx.height, density),
    inside = 0.35f,
    outside = 1f,
    metal = 0f,
)

/** Лицевая сторона коллекционной карты; рисуется внутри [HoloCard]. */
@Composable
fun CollectibleFace(art: CardArt?, tilt: () -> Offset, overflowTop: Dp, cardSize: DpSize) {
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(18.dp)
    val artHeight = cardSize.height * ART_HEIGHT_FRACTION

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .offset(y = overflowTop)
                .size(cardSize)
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFE9ECF2), Color(0xFFB9C0CC), Color(0xFFF4F1EA), Color(0xFFAEB6C2)),
                    ),
                )
                .border(3.dp, Color(0xFF2A2D35), shape)
                .padding(horizontal = PAD),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = PAD)
                    .height(HEADER)
                    .fillMaxWidth(),
            ) {
                Text("Earl", color = Color(0xFF1B1D22), fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                Text("HP ", color = Color(0xFF8A1C2B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("120", color = Color(0xFF8A1C2B), fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
            // Окно картинки: фон рисует холст ниже, здесь только рамка окна
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(artHeight)
                    .border(2.dp, Color(0xFF2A2D35), RoundedCornerShape(4.dp)),
            )
            Text(
                text = "Android Developer · Lv. 99",
                color = Color(0xFF3B3F48),
                fontSize = 11.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
            )
            Ability("Jetpack Compose", "Рисует интерфейсы быстрее, чем дизайнер их придумывает.", 120)
            Spacer(Modifier.height(8.dp))
            Ability("Kotlin Coroutines", "Ждёт сеть, не блокируя главный поток.", 90)
            Spacer(Modifier.weight(1f))
            Text(
                text = "★ Rare Holo · 001/151",
                color = Color(0xFF5A606B),
                fontSize = 9.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        // Картинка: фон в окне и человек, который из окна выходит. Один холст на весь слой,
        // чтобы человек мог рисоваться поверх рамки и выше карты
        Canvas(Modifier.fillMaxSize()) {
            val top = overflowTop.toPx()
            val artLocal = collectibleArtRect(cardSize.width.toPx(), cardSize.height.toPx(), density)
            val window = artLocal.translate(Offset(0f, top))
            drawArt(art, window, tilt())
        }
    }
}

@Composable
private fun Ability(name: String, text: String, damage: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, color = Color(0xFF1B1D22), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text, color = Color(0xFF4A4F59), fontSize = 9.sp, lineHeight = 11.sp)
        }
        Text("$damage", color = Color(0xFF1B1D22), fontSize = 20.sp, fontWeight = FontWeight.Black)
    }
}

private fun DrawScope.drawArt(art: CardArt?, window: Rect, tilt: Offset) {
    if (art == null) {
        clipRect(window.left, window.top, window.right, window.bottom) {
            drawRect(Brush.linearGradient(listOf(Color(0xFF3A2E6E), Color(0xFF0E7C86), Color(0xFFD2557A)), window.topLeft, window.bottomRight))
        }
        return
    }
    val bounds = art.subjectBounds
    val photoW = art.photo.width.toFloat()
    val photoH = art.photo.height.toFloat()

    // Масштаб: человек ростом в POP_OUT окна, ногами (низом) — по низу окна.
    // Без человека — фото просто заполняет окно
    val scale: Float
    val origin: Offset
    if (bounds != null) {
        scale = window.height * POP_OUT / bounds.height
        origin = Offset(
            x = window.center.x - bounds.center.x * scale,
            y = window.bottom - bounds.bottom * scale,
        )
    } else {
        scale = maxOf(window.width / photoW, window.height / photoH)
        origin = Offset(window.center.x - photoW * scale / 2, window.center.y - photoH * scale / 2)
    }
    // Параллакс: фон и человек сдвигаются в разные стороны — так видна глубина
    val depth = 8.dp.toPx()
    val back = origin + tilt * depth
    val front = origin - tilt * depth

    clipRect(window.left, window.top, window.right, window.bottom) {
        drawRect(Color(0xFF15171C), window.topLeft, window.size)
        drawImage(
            image = art.photo,
            dstOffset = IntOffset(back.x.roundToInt(), back.y.roundToInt()),
            dstSize = IntSize((photoW * scale).roundToInt(), (photoH * scale).roundToInt()),
        )
    }
    if (art.subject != null && bounds != null) {
        // Человек обрезан только по бокам и снизу окна — сверху он выходит из рамки
        clipRect(window.left, 0f, window.right, window.bottom) {
            drawImage(
                image = art.subject,
                dstOffset = IntOffset(
                    (front.x + bounds.left * scale).roundToInt(),
                    (front.y + bounds.top * scale).roundToInt(),
                ),
                dstSize = IntSize((bounds.width * scale).roundToInt(), (bounds.height * scale).roundToInt()),
            )
        }
    }
}
