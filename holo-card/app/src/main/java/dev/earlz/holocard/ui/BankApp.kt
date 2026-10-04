package dev.earlz.holocard.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.earlz.holocard.model.BankCard

/** Кошелёк и экран карты; карта из стопки «перелетает» на свой экран и обратно. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BankApp() {
    val tilt = rememberTilt()
    var opened by remember { mutableStateOf<BankCard?>(null) }
    // Вступление кошелька играем один раз — при запуске
    var introPlayed by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1C1F2A), Color(0xFF07080B)))),
    ) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = opened,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "screen",
            ) { card ->
                if (card == null) {
                    WalletScreen(
                        tilt = { tilt.value },
                        intro = !introPlayed,
                        animatedScope = this,
                        onOpen = {
                            introPlayed = true
                            opened = it
                        },
                    )
                } else {
                    CardDetailScreen(
                        card = card,
                        tilt = { tilt.value },
                        animatedScope = this,
                        onBack = { opened = null },
                    )
                }
            }
        }
    }
}
