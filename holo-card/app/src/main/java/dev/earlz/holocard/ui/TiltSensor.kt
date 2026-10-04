package dev.earlz.holocard.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext

/** Наклон телефона дальше этого угла (в радианах, ~18°) считаем максимальным. */
private const val MAX_TILT = 0.32f

/** Как быстро «нейтраль» догоняет телефон: доля за одно показание (~50 в секунду) — около 10 с. */
private const val RECENTER = 0.002f

/**
 * Наклон телефона относительно «нейтрального» положения: x — влево/вправо, y — к себе/от себя,
 * оба в диапазоне -1..1.
 *
 * «Нейтраль» медленно подтягивается к текущему положению: если держать телефон под углом,
 * карта постепенно выпрямится, и любой новый наклон снова будет заметен.
 */
@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        // Game rotation vector — ориентация без магнитометра: не «плывёт» рядом с железом
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        val matrix = FloatArray(9)
        val angles = FloatArray(3)
        var basePitch = Float.NaN
        var baseRoll = Float.NaN
        var smooth = Offset.Zero

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, angles)
                val pitch = angles[1]
                val roll = angles[2]
                if (basePitch.isNaN()) {
                    basePitch = pitch
                    baseRoll = roll
                }
                // Нейтраль медленно догоняет текущее положение
                basePitch += (pitch - basePitch) * RECENTER
                baseRoll += (roll - baseRoll) * RECENTER

                val raw = Offset(
                    x = ((roll - baseRoll) / MAX_TILT).coerceIn(-1f, 1f),
                    y = ((pitch - basePitch) / MAX_TILT).coerceIn(-1f, 1f),
                )
                // Сглаживание: датчик дрожит, а карта должна двигаться плавно
                smooth += (raw - smooth) * 0.25f
                tilt.value = smooth
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { manager.unregisterListener(listener) }
    }
    return tilt
}
