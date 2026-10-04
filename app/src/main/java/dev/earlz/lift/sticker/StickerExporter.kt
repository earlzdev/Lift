package dev.earlz.lift.sticker

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Делает из вырезки стикер для Telegram и сохраняет его в галерею (Pictures/Lift).
 *
 * Требования Telegram к статичному стикеру: прозрачный фон, одна сторона ровно 512 px,
 * другая не больше 512, файл не больше 512 КБ.
 */
object StickerExporter {

    private const val SIDE = 512
    private const val STROKE = 12f
    private const val MAX_BYTES = 512 * 1024
    private const val ALBUM = "Lift"

    /** [cutout] — объект на прозрачном фоне. */
    fun render(cutout: Bitmap): Bitmap {
        val content = trimTransparent(cutout)
        val pad = STROKE + 4f
        val scale = (SIDE - 2 * pad) / max(content.width, content.height)
        val w = (content.width * scale + 2 * pad).roundToInt().coerceAtMost(SIDE)
        val h = (content.height * scale + 2 * pad).roundToInt().coerceAtMost(SIDE)
        val dst = RectF(pad, pad, w - pad, h - pad)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

        // Белая обводка: белый силуэт объекта, нарисованный со сдвигами по кругу
        paint.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
        val steps = 36
        for (ring in listOf(STROKE, STROKE * 0.5f)) {
            for (i in 0 until steps) {
                val a = 2 * Math.PI * i / steps
                val dx = (cos(a) * ring).toFloat()
                val dy = (sin(a) * ring).toFloat()
                canvas.drawBitmap(content, null, RectF(dst).apply { offset(dx, dy) }, paint)
            }
        }
        paint.colorFilter = null
        canvas.drawBitmap(content, null, dst, paint)
        return out
    }

    /** Сохраняет стикер в Pictures/Lift. Разрешения не нужны: свои файлы в MediaStore можно писать с Android 10. */
    fun save(context: Context, sticker: Bitmap): Uri {
        val (bytes, mime, ext) = encode(sticker)
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "lift_sticker_${System.currentTimeMillis()}.$ext")
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать файл в галерее")
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    /** PNG, а если он тяжелее лимита Telegram (бывает на «шумных» фото) — WEBP, его Telegram тоже принимает. */
    private fun encode(sticker: Bitmap): Triple<ByteArray, String, String> {
        val png = ByteArrayOutputStream().also { sticker.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (png.size() <= MAX_BYTES) return Triple(png.toByteArray(), "image/png", "png")
        var quality = 95
        while (true) {
            val webp = ByteArrayOutputStream().also { sticker.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, it) }
            if (webp.size() <= MAX_BYTES || quality <= 50) return Triple(webp.toByteArray(), "image/webp", "webp")
            quality -= 10
        }
    }

    /** Обрезает прозрачные поля, чтобы объект занял весь стикер. */
    private fun trimTransparent(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if ((pixels[y * w + x] ushr 24) > 8) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < left) return src
        val r = Rect(left, top, right + 1, bottom + 1)
        return Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height())
    }
}
