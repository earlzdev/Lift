package dev.earlz.lift.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LiftColors = darkColorScheme(
    primary = Color(0xFFB8C7FF),
    background = Color.Black,
    surface = Color(0xFF111111),
)

@Composable
fun LiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LiftColors, content = content)
}
