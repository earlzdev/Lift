package dev.earlz.lift.segmentation

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Один объект на фото.
 *
 * Маска — это «насколько пиксель принадлежит объекту»: число от 0 (фон) до 1 (объект).
 * Хранится только для прямоугольника [bounds], а не для всего фото, чтобы экономить память.
 * Координаты [bounds] — в пикселях исходного фото.
 */
class Subject(
    val bounds: Rect,
    /** Построчно, размер bounds.width() * bounds.height(). */
    val mask: FloatArray,
) {
    fun confidenceAt(x: Int, y: Int): Float {
        if (!bounds.contains(x, y)) return 0f
        return mask[(y - bounds.top) * bounds.width() + (x - bounds.left)]
    }

    fun contains(x: Int, y: Int): Boolean = confidenceAt(x, y) >= HIT_THRESHOLD

    /**
     * Вырезает объект из [photo]: пиксели внутри [bounds] с прозрачностью из маски.
     * Мягкие края маски дают мягкие края вырезки, без «лесенки».
     */
    fun cutOut(photo: Bitmap): Bitmap {
        val w = bounds.width()
        val h = bounds.height()
        val pixels = IntArray(w * h)
        photo.getPixels(pixels, 0, w, bounds.left, bounds.top, w, h)
        for (i in pixels.indices) {
            val alpha = (mask[i].coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            pixels[i] = (alpha shl 24) or (pixels[i] and 0x00FFFFFF)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val HIT_THRESHOLD = 0.5f
    }
}
