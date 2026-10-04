package dev.earlz.lift.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * «Карман» внизу экрана: если бросить в него поднятый объект, получится стикер.
 *
 * @param visibility 0..1 — карман выезжает снизу вместе с подъёмом объекта
 * @param hovered палец с объектом сейчас над карманом
 */
@Composable
fun StickerPocket(
    visibility: () -> Float,
    hovered: Boolean,
    modifier: Modifier = Modifier,
) {
    val hover by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "pocketHover",
    )
    val shape = RoundedCornerShape(28.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .graphicsLayer {
                val v = visibility().coerceIn(0f, 1f)
                alpha = v
                translationY = (1f - v) * 120.dp.toPx()
                val s = 1f + 0.12f * hover
                scaleX = s
                scaleY = s
            }
            .background(Color.White.copy(alpha = 0.10f + 0.15f * hover), shape)
            .border(1.5.dp, Color.White.copy(alpha = 0.35f + 0.5f * hover), shape)
            .padding(horizontal = 28.dp, vertical = 18.dp),
    ) {
        Text(
            text = if (hovered) "Отпусти — будет стикер" else "Брось сюда → стикер",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
