package dev.earlz.lift.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Финальный кадр: готовый стикер вылетает из кармана и «шлёпается» в центр экрана.
 *
 * @param from центр кармана на экране — оттуда стикер вылетает и туда же улетает при закрытии
 */
@Composable
fun StickerReveal(
    sticker: ImageBitmap,
    from: Offset,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val appear = remember { Animatable(0f) }
    val scrim = remember { Animatable(0f) }
    // Где стикер окажется в итоге — нужно, чтобы считать путь из кармана
    var target by remember { mutableStateOf(Offset.Unspecified) }

    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(250)) }
        appear.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow))
    }

    fun close(then: () -> Unit) = scope.launch {
        launch { scrim.animateTo(0f, tween(250)) }
        appear.animateTo(0f, tween(260))
        then()
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scrim.value }
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                close(onDismiss)
            },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                bitmap = sticker,
                contentDescription = "Стикер",
                modifier = Modifier
                    .size(260.dp)
                    .onGloballyPositioned { if (!target.isSpecified) target = it.boundsInRoot().center }
                    .graphicsLayer {
                        val t = appear.value
                        val end = target
                        if (end.isSpecified) {
                            // Летим из кармана в центр; пружина даёт лёгкий перелёт
                            translationX = (from.x - end.x) * (1f - t)
                            translationY = (from.y - end.y) * (1f - t)
                        }
                        val s = 0.15f + 0.85f * t
                        scaleX = s
                        scaleY = s
                        rotationZ = -8f * (1f - t)
                        alpha = t.coerceIn(0f, 1f)
                    },
            )
            Spacer(Modifier.height(24.dp))
            Text("Стикер готов", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("Сохранён в Pictures/Lift", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
                .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) },
        ) {
            OutlinedButton(onClick = { close(onDismiss) }) {
                Text("Ещё", color = Color.White)
            }
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Text("Отправить")
            }
        }
    }
}
