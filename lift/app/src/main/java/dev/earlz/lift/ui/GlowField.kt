package dev.earlz.lift.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * «Поле свечения»: размытая маска объекта, посчитанная один раз заранее.
 *
 * Раньше шейдер для каждого пикселя каждого кадра заново искал край объекта, читая
 * десятки соседних пикселей. Теперь край «размазан» в этой картинке заранее, и шейдеру
 * достаточно одного чтения: значение около 0.5 — край, ближе к 1 — внутри, к 0 — снаружи.
 * Из этой же картинки рисуется тень — без размытия на каждом кадре.
 *
 * Поле в [DOWNSCALE] раз меньше слоя: размытой картинке высокое разрешение не нужно,
 * а считать её так в 16 раз быстрее.
 */
class GlowField(val bitmap: Bitmap) {
    companion object {
        const val DOWNSCALE = 4

        /**
         * @param cutout вырезанный объект (прозрачный фон)
         * @param width ширина объекта на экране, px
         * @param height высота объекта на экране, px
         * @param pad запас вокруг объекта на экране, px — там будет свечение и тень
         * @param blurRadius радиус размытия на экране, px
         */
        fun compute(cutout: Bitmap, width: Float, height: Float, pad: Float, blurRadius: Float): GlowField {
            val w = max(1, ceil((width + 2 * pad) / DOWNSCALE).toInt())
            val h = max(1, ceil((height + 2 * pad) / DOWNSCALE).toInt())
            val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val p = pad / DOWNSCALE
            Canvas(small).drawBitmap(
                cutout, null,
                RectF(p, p, p + width / DOWNSCALE, p + height / DOWNSCALE),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
            val alpha = IntArray(w * h)
            small.getPixels(alpha, 0, w, 0, 0, w, h)
            for (i in alpha.indices) alpha[i] = alpha[i] ushr 24

            // Три прохода box-размытия дают почти гауссово размытие, но гораздо дешевле
            val r = max(1, (blurRadius / DOWNSCALE / 2).roundToInt())
            val tmp = IntArray(w * h)
            repeat(3) {
                boxBlur(alpha, tmp, w, h, r, horizontal = true)
                boxBlur(tmp, alpha, w, h, r, horizontal = false)
            }

            for (i in alpha.indices) alpha[i] = alpha[i].coerceIn(0, 255) shl 24
            small.setPixels(alpha, 0, w, 0, 0, w, h)
            return GlowField(small)
        }

        /** Среднее по окну 2r+1 вдоль строки или столбца, скользящей суммой — O(1) на пиксель. */
        private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
            val lines = if (horizontal) h else w
            val len = if (horizontal) w else h
            val window = 2 * r + 1
            for (line in 0 until lines) {
                fun at(i: Int): Int {
                    val c = i.coerceIn(0, len - 1)
                    return if (horizontal) src[line * w + c] else src[c * w + line]
                }
                var sum = 0
                for (i in -r..r) sum += at(i)
                for (i in 0 until len) {
                    val out = if (horizontal) line * w + i else i * w + line
                    dst[out] = sum / window
                    sum += at(i + r + 1) - at(i - r)
                }
            }
        }
    }
}
