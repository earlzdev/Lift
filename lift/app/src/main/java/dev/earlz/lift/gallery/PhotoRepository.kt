package dev.earlz.lift.gallery

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Фото из галереи устройства. */
data class Photo(val id: Long, val uri: Uri)

/** Читает список фото из MediaStore — системной базы всех медиафайлов на устройстве. */
object PhotoRepository {

    /** Свои стикеры в галерее не показываем: они лежат в Pictures/Lift. */
    private val STICKERS_PATH = "${Environment.DIRECTORY_PICTURES}/Lift/"

    suspend fun load(context: Context): List<Photo> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.RELATIVE_PATH} NOT LIKE ?"
        val args = arrayOf("$STICKERS_PATH%")
        val order = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(collection, projection, selection, args, order)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    add(Photo(id, ContentUris.withAppendedId(collection, id)))
                }
            }
        }.orEmpty()
    }
}
