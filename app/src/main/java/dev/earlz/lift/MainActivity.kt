package dev.earlz.lift

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.earlz.lift.ui.LiftScreen
import dev.earlz.lift.ui.theme.LiftTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiftTheme {
                LiftScreen()
            }
        }
    }
}
