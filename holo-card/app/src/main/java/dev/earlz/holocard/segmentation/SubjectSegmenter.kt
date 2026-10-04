package dev.earlz.holocard.segmentation

import android.graphics.Bitmap

/**
 * Находит на фото отдельные объекты (людей, животных, предметы).
 *
 * Остальной код знает только про этот интерфейс, поэтому ML Kit можно
 * заменить своей моделью на LiteRT, не трогая UI и анимации.
 */
interface SubjectSegmenter {
    /** [photo] должен быть software-битмапом (ARGB_8888). */
    suspend fun segment(photo: Bitmap): List<Subject>

    fun close() {}
}
