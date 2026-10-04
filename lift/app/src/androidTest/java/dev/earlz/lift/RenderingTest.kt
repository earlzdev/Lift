package dev.earlz.lift

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.earlz.lift.sticker.StickerExporter
import dev.earlz.lift.ui.GlowShader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Проверки, которые без устройства не сделать: AGSL компилируется только на Android. */
@RunWith(AndroidJUnit4::class)
class RenderingTest {

    @Test
    fun glowShaderCompilesAndAcceptsUniforms() {
        // RuntimeShader бросает исключение при ошибке в AGSL или неверном имени uniform-а
        GlowShader().renderEffect(
            width = 200f, height = 200f, origin = Offset(100f, 100f),
            progress = 0.5f, rim = 0.6f, radius = 14f, time = 1f,
        )
    }

    @Test
    fun stickerFitsTelegramLimitsAndHasWhiteOutline() {
        // Красный круг радиусом 80 на прозрачном фоне 300×200
        val src = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        Canvas(src).drawCircle(150f, 100f, 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED })

        val sticker = StickerExporter.render(src)

        assertEquals(512, maxOf(sticker.width, sticker.height))
        assertTrue(minOf(sticker.width, sticker.height) <= 512)
        // Угол — прозрачный фон
        assertEquals(0, Color.alpha(sticker.getPixel(0, 0)))
        // Центр — сам объект
        assertEquals(Color.RED, sticker.getPixel(256, 256))
        // Чуть за краем круга — белая обводка
        val rim = sticker.getPixel(256, 256 - 240 - 6)
        assertEquals(255, Color.alpha(rim))
        assertTrue(Color.red(rim) > 240 && Color.green(rim) > 240 && Color.blue(rim) > 240)

        val png = ByteArrayOutputStream().also { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("PNG ${png.size()} байт", png.size() <= 512 * 1024)
    }
}
