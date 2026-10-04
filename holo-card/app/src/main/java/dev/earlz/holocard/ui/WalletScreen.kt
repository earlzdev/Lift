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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.earlz.holocard.model.BankCard
import dev.earlz.holocard.model.DemoBank
import dev.earlz.holocard.model.Transaction
import dev.earlz.holocard.model.formatRub
import kotlinx.coroutines.delay

/** На сколько карта в стопке выглядывает из-под следующей. */
private val STACK_STEP = 62.dp

/** Ключ общего элемента: карта в стопке и карта на своём экране — «одна и та же». */
fun cardSharedKey(card: BankCard) = "card-${card.id}"

/**
 * Главный экран: общий баланс, стопка карт как в кошельке и последние операции.
 *
 * @param intro играть вступление (карты въезжают, баланс насчитывается) — только при первом показе,
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
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Text("Compose Bank", color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape),
            ) {
                Text("E", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Общий баланс", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
        RollingNumber(
            value = cards.sumOf { it.balance },
            countUp = intro,
            style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(Modifier.height(28.dp))

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

        Spacer(Modifier.height(36.dp))
        Text("Последние операции", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        DemoBank.transactions.forEachIndexed { i, t -> TransactionRow(t, index = i, intro = intro) }
        Spacer(Modifier.height(24.dp))
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

/** Строка операции; строки появляются лесенкой — каждая чуть позже предыдущей. */
@Composable
private fun TransactionRow(t: Transaction, index: Int, intro: Boolean) {
    val enter = remember { Animatable(if (intro) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!intro) return@LaunchedEffect
        delay(500L + 70L * index)
        enter.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = (1f - enter.value) * 60.dp.toPx()
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .padding(vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp)),
        ) {
            Text(t.emoji, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(t.title, color = Color.White, fontSize = 15.sp)
            Text(t.subtitle, color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
        }
        Text(
            text = formatRub(t.amount, sign = true),
            color = if (t.amount > 0) Color(0xFF6EE7A8) else Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
