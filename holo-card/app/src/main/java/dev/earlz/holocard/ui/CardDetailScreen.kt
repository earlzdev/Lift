package dev.earlz.holocard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.earlz.holocard.model.BankCard
import dev.earlz.holocard.model.formatRub
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Pay { Idle, Waiting, Done }

private val ICE = Color(0xFF9FD8FF)
private val SUCCESS = Color(0xFF6EE7A8)

/** Экран одной карты: карта крупно (наклон, переворот), баланс, заморозка и оплата. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.CardDetailScreen(
    card: BankCard,
    tilt: () -> Offset,
    animatedScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var frozen by remember { mutableStateOf(false) }
    val frost = remember { Animatable(0f) }
    var pay by remember { mutableStateOf(Pay.Idle) }
    // Подъём карты при оплате и «потряхивание», если карта заморожена
    val lift = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val done = remember { Animatable(0f) }
    var status by remember { mutableStateOf<String?>(null) }

    fun toggleFreeze() {
        frozen = !frozen
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        status = if (frozen) "❄  Карта заморожена" else null
        scope.launch {
            // Лёд нарастает медленно, а тает быстрее
            frost.animateTo(if (frozen) 1f else 0f, tween(if (frozen) 1400 else 900, easing = FastOutSlowInEasing))
        }
    }

    fun startPay() {
        if (pay != Pay.Idle) return
        if (frozen) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            status = "Сначала разморозьте карту"
            scope.launch {
                shake.animateTo(0f, keyframes {
                    durationMillis = 420
                    -18f at 60; 16f at 130; -12f at 200; 8f at 270; -4f at 340; 0f at 420
                })
            }
            return
        }
        scope.launch {
            pay = Pay.Waiting
            status = "Поднесите телефон к терминалу"
            lift.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow))
            delay(2200)
            pay = Pay.Done
            status = "Оплачено ${formatRub(340)} · Кофейня"
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            done.snapTo(0f)
            done.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            delay(1200)
            lift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow))
            pay = Pay.Idle
            status = null
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
            }
            Text(card.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(340.dp)) {
            // Волны оплаты — позади карты
            PayRings(pay = pay, done = { done.value })
            HoloCard(
                card = card,
                tilt = tilt,
                frost = { frost.value },
                modifier = Modifier
                    .sharedElement(
                        rememberSharedContentState(cardSharedKey(card)),
                        animatedVisibilityScope = animatedScope,
                    )
                    .graphicsLayer {
                        val l = lift.value
                        translationY = -24.dp.toPx() * l
                        scaleX = 1f + 0.05f * l
                        scaleY = 1f + 0.05f * l
                        translationX = shake.value.dp.toPx()
                    },
            )
            SuccessCheck(done = { done.value }, visible = pay == Pay.Done)
        }
        Text(
            "Свайпни карту, чтобы перевернуть",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(28.dp))
        Text("Баланс карты", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        RollingNumber(
            value = card.balance,
            style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
        )

        Spacer(Modifier.height(16.dp))
        AnimatedContent(
            targetState = status,
            transitionSpec = { (fadeIn() + slideInVertically { it / 2 }) togetherWith fadeOut() },
            label = "status",
            modifier = Modifier.height(24.dp),
        ) { s ->
            Text(
                text = s.orEmpty(),
                color = when {
                    pay == Pay.Done -> SUCCESS
                    frozen -> ICE
                    else -> Color.White.copy(alpha = 0.8f)
                },
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(36.dp),
            modifier = Modifier.padding(bottom = 32.dp),
        ) {
            ActionButton(icon = "📲", label = "Оплатить", active = pay != Pay.Idle, activeColor = SUCCESS, onClick = ::startPay)
            ActionButton(
                icon = "❄",
                label = if (frozen) "Разморозить" else "Заморозить",
                active = frozen,
                activeColor = ICE,
                onClick = ::toggleFreeze,
            )
        }
    }
}

@Composable
private fun ActionButton(icon: String, label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) activeColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f), label = "bg")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(bg, CircleShape)
                .clickable(onClick = onClick),
        ) {
            Text(icon, fontSize = 26.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
    }
}

/** Волны от карты, пока ждём терминал; при успехе — одна зелёная вспышка. */
@Composable
private fun PayRings(pay: Pay, done: () -> Float) {
    val waves = rememberInfiniteTransition(label = "waves")
    val phase by waves.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        val card = Size(BANK_SIZE.width.toPx(), BANK_SIZE.height.toPx())
        fun ring(progress: Float, color: Color) {
            val grow = 70.dp.toPx() * progress
            drawRoundRect(
                color = color.copy(alpha = color.alpha * (1f - progress)),
                topLeft = Offset((size.width - card.width) / 2 - grow, (size.height - card.height) / 2 - grow),
                size = Size(card.width + grow * 2, card.height + grow * 2),
                cornerRadius = CornerRadius(BANK_CORNER.toPx() + grow),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        when (pay) {
            Pay.Waiting -> repeat(3) { i -> ring((phase + i / 3f) % 1f, Color.White.copy(alpha = 0.5f)) }
            Pay.Done -> ring(done(), SUCCESS)
            Pay.Idle -> Unit
        }
    }
}

/** Зелёный кружок с галочкой, которая «рисуется» по линии. */
@Composable
private fun SuccessCheck(done: () -> Float, visible: Boolean) {
    if (!visible) return
    Canvas(Modifier.size(84.dp)) {
        val p = done()
        val appear = (p * 3f).coerceIn(0f, 1f)
        drawCircle(SUCCESS.copy(alpha = 0.95f), radius = size.minDimension / 2 * (0.6f + 0.4f * appear), alpha = appear)
        val check = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.67f)
            lineTo(size.width * 0.73f, size.height * 0.36f)
        }
        // Рисуем только начало линии: от 0 до доли progress — так галочка «прорисовывается»
        val measure = PathMeasure().apply { setPath(check, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * ((p - 0.25f) / 0.5f).coerceIn(0f, 1f), partial, true)
        drawPath(partial, Color(0xFF0B3D24), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
    }
}
