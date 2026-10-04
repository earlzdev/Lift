package dev.earlz.holocard.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.earlz.holocard.model.BankCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.roundToInt

/** Где на карте фольга сильнее и насколько. Координаты — в px внутри самой карты. */
class FoilSpec(val rect: Rect, val inside: Float, val outside: Float, val metal: Float)

/** Максимальный поворот карты, градусов. */
private const val MAX_ROTATION = 26f

/** Свайп быстрее этого, px/с, переворачивает карту, даже если она не дошла до 90°. */
private const val FLIP_VELOCITY = 800f

private val SHADOW_BLUR = 28.dp

/**
 * Металлическая банковская карта: поворачивается в 3D за наклоном телефона, переливается
 * голограммой, может покрыться инеем.
 *
 * @param tilt наклон телефона, -1..1
 * @param interactive true — палец наклоняет карту, горизонтальный свайп её переворачивает;
 *   false — карта только следит за наклоном (например, в стопке кошелька)
 * @param tiltScale насколько сильно карта реагирует на наклон телефона (1 — полностью)
 * @param frost 0..1 — насколько карта покрыта инеем (заморожена)
 */
@Composable
fun HoloCard(
    card: BankCard,
    tilt: () -> Offset,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    tiltScale: Float = 1f,
    frost: () -> Float = { 0f },
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val holo = remember { HoloShader() }
    val ice = remember { FrostShader() }
    // Палец «перехватывает» наклон: тянешь — карта поворачивается, отпустил — пружинит обратно.
    // Пока палец на карте — простое состояние, без корутин на каждое касание;
    // анимация запускается один раз, когда палец отпустили
    var drag by remember { mutableStateOf(Offset.Zero) }
    // Угол переворота, градусы: 0 — лицом, 180 — оборотом (360 — снова лицом)
    var flip by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    // Какую сторону видно: «за 90°» — уже оборот. Читается только при отрисовке,
    // поэтому переворот не пересобирает интерфейс
    val backVisible = { cos(Math.toRadians(flip.toDouble())) < 0 }
    val clock = rememberInfiniteTransition(label = "holoClock")
    val time by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_000_000, easing = LinearEasing)),
        label = "holoTime",
    )
    val totalTilt = {
        // Знак развёрнут: так карта ведёт себя как предмет в руке — наклонил телефон,
        // карта «осталась» на месте; потянул пальцем — край карты пошёл за пальцем
        val t = -(tilt() * tiltScale + drag)
        Offset(t.x.coerceIn(-1.2f, 1.2f), t.y.coerceIn(-1.2f, 1.2f))
    }
    val cardPx = with(density) { Rect(0f, 0f, BANK_SIZE.width.toPx(), BANK_SIZE.height.toPx()) }
    val dragRange = with(density) { 160.dp.toPx() }
    val flipRange = cardPx.width
    val velocity = remember { VelocityTracker() }
    // Сторона, с которой начался свайп: от неё считаем «на одну сторону дальше»
    var flipStart by remember { mutableFloatStateOf(0f) }

    fun settle() {
        // Быстрый свайп — ровно на одну сторону в его направлении; медленный — к ближайшей стороне
        val fling = velocity.calculateVelocity().x
        val target = when {
            fling < -FLIP_VELOCITY -> flipStart + 180f
            fling > FLIP_VELOCITY -> flipStart - 180f
            else -> Math.round(flip / 180f) * 180f
        }
        settleJob = scope.launch {
            launch {
                animate(
                    typeConverter = Offset.VectorConverter,
                    initialValue = drag,
                    targetValue = Offset.Zero,
                    animationSpec = spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow),
                ) { value, _ -> drag = value }
            }
            animate(flip, target, animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow)) { value, _ ->
                flip = value
            }
        }
    }

    val gestures = if (!interactive) Modifier else Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                // Палец снова на карте — недоигранную пружину останавливаем
                settleJob?.cancel()
                velocity.resetTracking()
                flipStart = Math.round(flip / 180f) * 180f
            },
            onDrag = { change, amount ->
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                // По горизонтали палец крутит карту целиком, по вертикали — наклоняет
                flip -= amount.x / flipRange * 180f
                drag += Offset(0f, amount.y) / dragRange
            },
            onDragEnd = { settle() },
            onDragCancel = { settle() },
        )
    }

    Box(modifier = modifier.size(BANK_SIZE).then(gestures)) {
        // Тень на «столе»: размыта один раз в картинку, а на кадрах только сдвигается против наклона
        val shadow = remember(density) { shadowBitmap(density) }
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .size(BANK_SIZE.width + SHADOW_BLUR * 2, BANK_SIZE.height + SHADOW_BLUR * 2)
                .graphicsLayer {
                    val t = totalTilt()
                    translationX = -t.x * 18.dp.toPx()
                    translationY = 16.dp.toPx() - t.y * 14.dp.toPx()
                },
        ) {
            drawImage(shadow, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
        }

        Box(
            modifier = Modifier
                .size(BANK_SIZE)
                .graphicsLayer {
                    val t = totalTilt()
                    cameraDistance = 14f * density.density
                    rotationY = flip + t.x * MAX_ROTATION
                    rotationX = -t.y * MAX_ROTATION
                },
        ) {
            // Обе стороны собраны всегда и лежат каждая в своём слое: их рисунок записан один раз
            // и кешируется, а при перевороте меняется только прозрачность слоя (0 или 1) —
            // ни пересборки интерфейса, ни перерисовки текста и металла на каждом кадре
            Box(Modifier.graphicsLayer { alpha = if (backVisible()) 0f else 1f }) {
                BankFace(card)
            }
            Box(
                Modifier.graphicsLayer {
                    // Повёрнутый на 180° слой показывает содержимое зеркально — отражаем ещё раз
                    scaleX = -1f
                    alpha = if (backVisible()) 1f else 0f
                },
            ) { BankBack(card) }

            // Свет фольги поверх обеих сторон: прибавляется к пикселям карты (BlendMode.Plus),
            // а иней — обычным наложением сверху. Читают состояние только при отрисовке —
            // каждый кадр перезаписывается лишь этот холст
            Canvas(Modifier.fillMaxSize()) {
                val t = totalTilt()
                val back = backVisible()
                val spec = bankFoil(density.density, back)
                val f = frost()
                val corner = CornerRadius(BANK_CORNER.toPx())
                drawRoundRect(
                    brush = holo.brush(
                        card = cardPx,
                        foilRect = spec.rect,
                        foilInside = spec.inside,
                        foilOutside = spec.outside,
                        metal = spec.metal,
                        // На обороте карта отражена — и блик должен ехать в другую сторону
                        tilt = if (back) Offset(-t.x, t.y) else t,
                        time = time,
                    ),
                    size = cardPx.size,
                    cornerRadius = corner,
                    // Подо льдом металл не блестит; светлый металл и так светлый — блик на нём слабее,
                    // иначе он выгорает в белое пятно
                    alpha = (1f - 0.7f * f) * if (card.ink == Color.White) 1f else 0.4f,
                    blendMode = BlendMode.Plus,
                )
                if (f > 0f) {
                    drawRoundRect(brush = ice.brush(cardPx, f), size = cardPx.size, cornerRadius = corner)
                }
            }
        }
    }
}

/** Мягкая тень карты: тёмный скруглённый прямоугольник, размытый один раз заранее. */
private fun shadowBitmap(density: Density): ImageBitmap = with(density) {
    val blur = SHADOW_BLUR.toPx()
    val w = (BANK_SIZE.width.toPx() + blur * 2).roundToInt()
    val h = (BANK_SIZE.height.toPx() + blur * 2).roundToInt()
    val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(170, 0, 0, 0)
        maskFilter = android.graphics.BlurMaskFilter(blur / 2, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val inset = 12.dp.toPx()
    android.graphics.Canvas(bitmap).drawRoundRect(
        blur + inset, blur + inset, w - blur - inset, h - blur - inset,
        24.dp.toPx(), 24.dp.toPx(), paint,
    )
    bitmap.asImageBitmap()
}
