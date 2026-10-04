package dev.earlz.lift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Объект, «оторванный» от фото: рисуется поверх фото вместе с тенью и свечением.
 *
 * Значения анимаций передаются лямбдами и читаются только в фазе отрисовки,
 * поэтому каждый кадр анимации не вызывает рекомпозицию.
 *
 * @param rect где объект лежит на экране, пока его не сдвинули
 * @param touch точка касания в координатах экрана — от неё расходится волна и идёт подъём
 * @param drag насколько объект утащили пальцем
 * @param lift 0 — лежит на фото, 1 — поднят (пружина может ненадолго выходить за 1)
 * @param wave 0..1 — прогресс волны света по контуру
 * @param shrink 0..1 — объект сжимается в точку касания (улетает в карман)
 */
@Composable
fun LiftedSubjectLayer(
    cutout: ImageBitmap,
    rect: Rect,
    touch: Offset,
    drag: () -> Offset,
    lift: () -> Float,
    wave: () -> Float,
    shrink: () -> Float = { 0f },
) {
    val density = LocalDensity.current
    // Запас вокруг объекта: свечение и тень рисуются за его границами
    val pad = with(density) { 48.dp.toPx() }
    val glowRadius = with(density) { 14.dp.toPx() }
    val shadowDrop = with(density) { 18.dp.toPx() }
    val shadowBlur = with(density) { 14.dp.toPx() }
    val glow = remember { GlowShader() }
    // Бесконечные «часы» для перелива ободка; читаются только при отрисовке
    val clock = rememberInfiniteTransition(label = "glowClock")
    val time by clock.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 100_000, easing = LinearEasing)),
        label = "glowTime",
    )

    val layer = rect.inflate(pad)
    val local = touch - layer.topLeft
    val origin = TransformOrigin(local.x / layer.width, local.y / layer.height)
    val layerSize = with(density) { androidx.compose.ui.unit.DpSize(layer.width.toDp(), layer.height.toDp()) }

    fun DrawScope.drawCutout(colorFilter: ColorFilter? = null) = drawImage(
        image = cutout,
        dstOffset = IntOffset(pad.roundToInt(), pad.roundToInt()),
        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
        colorFilter = colorFilter,
    )

    Box(
        modifier = Modifier
            .offset {
                val d = drag()
                IntOffset((layer.left + d.x).roundToInt(), (layer.top + d.y).roundToInt())
            }
            .requiredSize(layerSize),
    ) {
        // Тень: силуэт объекта, залитый чёрным и размытый; при подъёме уходит вниз
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val l = lift().coerceAtLeast(0f)
                    val k = 1f - 0.9f * shrink()
                    transformOrigin = origin
                    translationY = shadowDrop * l * k
                    scaleX = (1f + 0.03f * l) * k
                    scaleY = (1f + 0.03f * l) * k
                    alpha = (0.55f * l * (1f - shrink())).coerceIn(0f, 1f)
                    renderEffect = BlurEffect(shadowBlur, shadowBlur)
                },
        ) {
            drawCutout(ColorFilter.tint(Color.Black))
        }

        // Сам объект: приподнимается (масштаб) и светится по контуру
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val l = lift()
                    val k = 1f - 0.9f * shrink()
                    transformOrigin = origin
                    scaleX = (1f + 0.06f * l) * k
                    scaleY = (1f + 0.06f * l) * k
                    // В самом конце полёта в карман объект растворяется
                    alpha = ((1f - shrink()) / 0.3f).coerceIn(0f, 1f)
                    renderEffect = glow.renderEffect(
                        width = size.width,
                        height = size.height,
                        origin = local,
                        progress = wave(),
                        rim = (0.6f * l).coerceIn(0f, 0.6f),
                        radius = glowRadius,
                        time = time,
                    )
                },
        ) {
            drawCutout()
        }
    }
}
