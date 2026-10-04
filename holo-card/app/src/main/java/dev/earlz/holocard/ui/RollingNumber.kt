package dev.earlz.holocard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import dev.earlz.holocard.model.formatRub

/**
 * Сумма, у которой каждая цифра — отдельный «барабан»: при изменении цифры прокручиваются вверх.
 * С [countUp] сумма при показе насчитывается с нуля — барабаны крутятся, как в слот-машине.
 */
@Composable
fun RollingNumber(value: Long, style: TextStyle, modifier: Modifier = Modifier, countUp: Boolean = true) {
    val shown = remember { Animatable(if (countUp) 0f else value.toFloat()) }
    LaunchedEffect(value) {
        shown.animateTo(value.toFloat(), tween(durationMillis = 1400, easing = FastOutSlowInEasing))
    }
    val text = formatRub(shown.value.toLong())
    Row(modifier.clipToBounds()) {
        // Ключ — позиция справа: так единицы всегда остаются единицами, даже когда число растёт
        text.forEachIndexed { i, ch ->
            val fromRight = text.length - i
            key(fromRight) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                    },
                    label = "digit$fromRight",
                ) { c ->
                    Text(c.toString(), style = style)
                }
            }
        }
    }
}
