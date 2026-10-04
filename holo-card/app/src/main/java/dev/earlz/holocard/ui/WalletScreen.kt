package dev.earlz.holocard.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.earlz.holocard.model.BankCard
import dev.earlz.holocard.model.DemoBank
import kotlinx.coroutines.delay

/** На сколько карта в стопке выглядывает из-под следующей. */
private val STACK_STEP = 72.dp

/** Ключ общего элемента: карта в стопке и карта на своём экране — «одна и та же». */
fun cardSharedKey(card: BankCard) = "card-${card.id}"

/**
 * Главный экран: стопка карт, как в кошельке.
 *
 * @param intro играть вступление (карты въезжают по очереди) — только при первом показе,
 *   чтобы при возврате с экрана карты оно не мешало переходу
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.WalletScreen(
    tilt: () -> Offset,
    intro: Boolean,
    animatedScope: AnimatedVisibilityScope,
    onOpen: (BankCard) -> Unit,
) {
    val cards = DemoBank.cards
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Jetpack Compose Bank", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${cards.size} cards", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
            ) {
                Text("E", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))
        // Стопка карт: каждая следующая лежит на предыдущей, видна полоска сверху
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(STACK_STEP * (cards.size - 1) + BANK_SIZE.height),
            contentAlignment = Alignment.TopCenter,
        ) {
            cards.forEachIndexed { i, card ->
                StackedCard(i, intro) {
                    HoloCard(
                        card = card,
                        tilt = tilt,
                        interactive = false,
                        // В стопке карта лишь слегка «дышит» от наклона
                        tiltScale = 0.35f,
                        modifier = Modifier
                            .offset(y = STACK_STEP * i)
                            .sharedElement(
                                rememberSharedContentState(cardSharedKey(card)),
                                animatedVisibilityScope = animatedScope,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onOpen(card) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Tap a card to open it",
            color = Color.White.copy(alpha = 0.35f),
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp),
        )
    }
}

/** Карта въезжает в стопку снизу с пружиной — каждая чуть позже предыдущей. */
@Composable
private fun StackedCard(index: Int, intro: Boolean, content: @Composable () -> Unit) {
    val enter = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!intro) return@LaunchedEffect
        delay(120L * index)
        enter.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier.graphicsLayer {
            translationY = (1f - enter.value) * 300.dp.toPx()
            alpha = enter.value.coerceIn(0f, 1f)
        },
    ) { content() }
}
