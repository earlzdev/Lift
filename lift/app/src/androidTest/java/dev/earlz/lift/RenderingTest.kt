package dev.earlz.lift

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.earlz.lift.sticker.StickerExporter
import dev.earlz.lift.ui.GlowField
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
        val field = GlowField(Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888))
        GlowShader().apply { setField(field) }.renderEffect(
            width = 200f, height = 200f, origin = Offset(100f, 100f),
            progress = 0.5f, rim = 0.6f, radius = 14f, time = 1f,
        )
    }

    @Test
    fun glowFieldIsBrightInsideFadesAtEdgeAndDarkFarAway() {
        // Непрозрачный квадрат 200×200, запас 100 px вокруг
        val cutout = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val field = GlowField.compute(cutout, width = 200f, height = 200f, pad = 100f, blurRadius = 40f).bitmap
        val k = GlowField.DOWNSCALE
        fun alphaAt(x: Int, y: Int) = Color.alpha(field.getPixel(x / k, y / k))

        assertTrue("центр", alphaAt(200, 200) > 240)
        assertTrue("край", alphaAt(100, 200) in 80..180)
        assertTrue("далеко снаружи", alphaAt(10, 200) < 10)
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
