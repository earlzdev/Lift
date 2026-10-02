package dev.earlz.lift.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.earlz.lift.segmentation.MlKitSubjectSegmenter
import dev.earlz.lift.segmentation.Subject
import dev.earlz.lift.segmentation.SubjectSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Фото больше этого по длинной стороне уменьшаем: и сегментация быстрее, и памяти меньше. */
private const val MAX_PHOTO_SIDE = 2048

private class LiftedSubject(val subject: Subject, val cutout: ImageBitmap)

@Composable
fun LiftScreen() {
    val context = LocalContext.current
    val segmenter: SubjectSegmenter = remember { MlKitSubjectSegmenter(context.applicationContext) }
    DisposableEffect(segmenter) { onDispose { segmenter.close() } }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    // null — сегментация ещё идёт (или фото нет)
    var subjects by remember { mutableStateOf<List<Subject>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var lifted by remember { mutableStateOf<LiftedSubject?>(null) }

    // Системный Photo Picker: разрешения на доступ к галерее не нужны
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }

    LaunchedEffect(photoUri) {
        val uri = photoUri ?: return@LaunchedEffect
        lifted = null
        subjects = null
        status = "Ищу объекты…"
        val bitmap = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
        photo = bitmap
        // Сегментируем сразу после загрузки, чтобы долгое нажатие срабатывало без задержки
        subjects = try {
            segmenter.segment(bitmap).also {
                status = if (it.isEmpty()) "Объектов не нашлось" else null
            }
        } catch (e: Exception) {
            Log.e("Lift", "Segmentation failed", e)
            status = "Ошибка сегментации: ${e.message}"
            emptyList()
        }
    }

    val dim by animateFloatAsState(
        targetValue = if (lifted != null) 0.6f else 0f,
        animationSpec = tween(300),
        label = "dim",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        photo?.let { bitmap ->
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap, subjects) {
                        detectTapGestures(
                            onLongPress = { pos ->
                                val fit = fitRect(bitmap.width, bitmap.height, size.width, size.height)
                                val x = ((pos.x - fit.left) / fit.width * bitmap.width).toInt()
                                val y = ((pos.y - fit.top) / fit.height * bitmap.height).toInt()
                                val hit = subjects?.firstOrNull { it.contains(x, y) }
                                lifted = hit?.let { LiftedSubject(it, it.cutOut(bitmap).asImageBitmap()) }
                            },
                            onTap = { lifted = null },
                        )
                    },
            ) {
                val fit = fitRect(bitmap.width, bitmap.height, size.width.toInt(), size.height.toInt())
                drawImage(
                    image = image,
                    dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                    dstSize = IntSize(fit.width.roundToInt(), fit.height.roundToInt()),
                )
                if (dim > 0f) {
                    drawRect(Color.Black.copy(alpha = dim))
                }
                lifted?.let { l ->
                    val scale = fit.width / bitmap.width
                    val b = l.subject.bounds
                    drawImage(
                        image = l.cutout,
                        dstOffset = IntOffset(
                            (fit.left + b.left * scale).roundToInt(),
                            (fit.top + b.top * scale).roundToInt(),
                        ),
                        dstSize = IntSize(
                            (b.width() * scale).roundToInt(),
                            (b.height() * scale).roundToInt(),
                        ),
                    )
                }
            }
        }

        status?.let {
            Text(
                text = it,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp),
            )
        }

        FilledTonalButton(
            onClick = {
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            Text(if (photo == null) "Выбрать фото" else "Другое фото")
        }
    }
}

private fun decodePhoto(context: Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        // Software-битмап нужен, чтобы читать пиксели и вырезать объект по маске
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longSide = max(info.size.width, info.size.height)
        if (longSide > MAX_PHOTO_SIDE) {
            val k = MAX_PHOTO_SIDE.toFloat() / longSide
            decoder.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
        }
    }

/** Где на экране окажется фото, вписанное целиком по центру (как ContentScale.Fit). */
private fun fitRect(imageW: Int, imageH: Int, viewW: Int, viewH: Int): Rect {
    val scale = min(viewW.toFloat() / imageW, viewH.toFloat() / imageH)
    val size = Size(imageW * scale, imageH * scale)
    return Rect(Offset((viewW - size.width) / 2f, (viewH - size.height) / 2f), size)
}
