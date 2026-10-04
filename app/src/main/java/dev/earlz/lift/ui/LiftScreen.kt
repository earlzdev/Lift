package dev.earlz.lift.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.earlz.lift.segmentation.MlKitSubjectSegmenter
import dev.earlz.lift.segmentation.Subject
import dev.earlz.lift.segmentation.SubjectSegmenter
import dev.earlz.lift.sticker.StickerExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Фото больше этого по длинной стороне уменьшаем: и сегментация быстрее, и памяти меньше. */
private const val MAX_PHOTO_SIDE = 2048

/** Объект с заранее вырезанной картинкой, чтобы подъём начинался без задержки. */
private class SubjectCutout(val subject: Subject, val image: ImageBitmap)

/** Поднятый объект: где он был на экране и где его коснулись. */
private class Lifted(val cutout: SubjectCutout, val rect: Rect, val touch: Offset)

/** Готовый стикер, который показываем в финальном кадре. */
private class Revealed(val sticker: ImageBitmap, val uri: Uri, val from: Offset)

@Composable
fun LiftScreen() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val segmenter: SubjectSegmenter = remember { MlKitSubjectSegmenter(context.applicationContext) }
    DisposableEffect(segmenter) { onDispose { segmenter.close() } }

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    // null — сегментация ещё идёт (или фото нет)
    var cutouts by remember { mutableStateOf<List<SubjectCutout>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var lifted by remember { mutableStateOf<Lifted?>(null) }
    var revealed by remember { mutableStateOf<Revealed?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val lift = remember { Animatable(0f) }
    val wave = remember { Animatable(0f) }
    val shrink = remember { Animatable(0f) }
    // Подсказка после сегментации: по всем найденным объектам один раз пробегает свет
    val hint = remember { Animatable(0f) }
    var showHint by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dropJob by remember { mutableStateOf<Job?>(null) }

    var pocketRect by remember { mutableStateOf<Rect?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val pocketSlop = with(LocalDensity.current) { 24.dp.toPx() }
    fun isOverPocket(l: Lifted?): Boolean =
        l != null && pocketRect?.inflate(pocketSlop)?.contains(l.touch + drag) == true
    val overPocket = isOverPocket(lifted)
    LaunchedEffect(overPocket) {
        if (overPocket) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Системный Photo Picker: разрешения на доступ к галерее не нужны
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }
    fun openPicker() = pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(photoUri) {
        val uri = photoUri ?: return@LaunchedEffect
        lifted = null
        cutouts = null
        showHint = false
        status = "Ищу объекты…"
        val bitmap = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
        photo = bitmap
        // Сегментируем сразу после загрузки, чтобы долгое нажатие срабатывало без задержки
        val found = try {
            val subjects = segmenter.segment(bitmap)
            withContext(Dispatchers.Default) {
                subjects.map { SubjectCutout(it, it.cutOut(bitmap).asImageBitmap()) }
            }
        } catch (e: Exception) {
            Log.e("Lift", "Segmentation failed", e)
            status = "Ошибка сегментации: ${e.message}"
            cutouts = emptyList()
            return@LaunchedEffect
        }
        cutouts = found
        if (found.isEmpty()) {
            status = "Объектов не нашлось — попробуй другое фото"
            return@LaunchedEffect
        }
        status = "Зажми объект"
        showHint = true
        hint.snapTo(0f)
        hint.animateTo(1f, tween(durationMillis = 1400, easing = FastOutSlowInEasing))
        showHint = false
    }

    fun screenRect(bitmap: Bitmap, subject: Subject): Rect {
        val fit = fitRect(bitmap.width, bitmap.height, viewSize.width, viewSize.height)
        val scale = fit.width / bitmap.width
        val b = subject.bounds
        return Rect(
            left = fit.left + b.left * scale,
            top = fit.top + b.top * scale,
            right = fit.left + b.right * scale,
            bottom = fit.top + b.bottom * scale,
        )
    }

    fun pickUp(cutout: SubjectCutout, rect: Rect, touch: Offset) {
        dropJob?.cancel()
        showHint = false
        status = null
        drag = Offset.Zero
        lifted = Lifted(cutout, rect, touch)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            shrink.snapTo(0f)
            lift.snapTo(0f)
            // Низкое затухание — объект слегка «пружинит», когда отрывается
            lift.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        }
        scope.launch {
            wave.snapTo(0f)
            wave.animateTo(1f, tween(durationMillis = 1100, easing = FastOutSlowInEasing))
        }
    }

    fun putInPocket(l: Lifted, pocket: Rect) {
        dropJob = scope.launch {
            // Стикер рендерим параллельно с анимацией полёта
            val sticker = async(Dispatchers.Default) { StickerExporter.render(l.cutout.image.asAndroidBitmap()) }
            launch {
                animate(
                    typeConverter = Offset.VectorConverter,
                    initialValue = drag,
                    targetValue = pocket.center - l.touch,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> drag = value }
            }
            shrink.animateTo(1f, tween(durationMillis = 420, easing = FastOutSlowInEasing))
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            val uri = try {
                withContext(Dispatchers.IO) { StickerExporter.save(context, sticker.await()) }
            } catch (e: Exception) {
                Log.e("Lift", "Sticker save failed", e)
                lifted = null
                lift.snapTo(0f)
                snackbar.showSnackbar("Не удалось сохранить стикер: ${e.message}")
                return@launch
            }
            // Небольшая пауза: карман «переваривает» объект, потом выплёвывает стикер
            delay(120)
            revealed = Revealed(sticker.await().asImageBitmap(), uri, pocket.center)
            lifted = null
            lift.snapTo(0f)
        }
    }

    fun drop() {
        val l = lifted ?: return
        val pocket = pocketRect
        if (pocket != null && isOverPocket(l)) {
            putInPocket(l, pocket)
            return
        }
        dropJob = scope.launch {
            val back = launch {
                animate(
                    typeConverter = Offset.VectorConverter,
                    initialValue = drag,
                    targetValue = Offset.Zero,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                ) { value, _ -> drag = value }
            }
            lift.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
            back.join()
            lifted = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it },
    ) {
        val bitmap = photo
        if (bitmap == null) {
            EmptyState(onPick = ::openPicker, modifier = Modifier.align(Alignment.Center))
        } else {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap, cutouts) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { pos ->
                                if (revealed != null) return@detectDragGesturesAfterLongPress
                                val fit = fitRect(bitmap.width, bitmap.height, size.width, size.height)
                                val scale = fit.width / bitmap.width
                                val x = ((pos.x - fit.left) / scale).toInt()
                                val y = ((pos.y - fit.top) / scale).toInt()
                                val hit = cutouts?.firstOrNull { it.subject.contains(x, y) }
                                if (hit != null) pickUp(hit, screenRect(bitmap, hit.subject), pos)
                            },
                            onDrag = { change, amount ->
                                if (lifted != null && dropJob?.isActive != true) {
                                    change.consume()
                                    drag += amount
                                }
                            },
                            onDragEnd = { drop() },
                            onDragCancel = { drop() },
                        )
                    },
            ) {
                val fit = fitRect(bitmap.width, bitmap.height, size.width.toInt(), size.height.toInt())
                drawImage(
                    image = image,
                    dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()),
                    dstSize = IntSize(fit.width.roundToInt(), fit.height.roundToInt()),
                )
                // Затемнение фона следует за подъёмом объекта
                val dim = 0.5f * lift.value.coerceIn(0f, 1f)
                if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
            }

            if (showHint && viewSize != IntSize.Zero) {
                cutouts?.forEach { c ->
                    val rect = screenRect(bitmap, c.subject)
                    LiftedSubjectLayer(
                        cutout = c.image,
                        rect = rect,
                        touch = rect.center,
                        drag = { Offset.Zero },
                        lift = { 0f },
                        wave = { hint.value },
                    )
                }
            }
        }

        lifted?.let { l ->
            LiftedSubjectLayer(
                cutout = l.cutout.image,
                rect = l.rect,
                touch = l.touch,
                drag = { drag },
                lift = { lift.value },
                wave = { wave.value },
                shrink = { shrink.value },
            )
        }

        // Карман поверх объекта: надпись должна читаться, когда объект над ним
        if (lifted != null) {
            StickerPocket(
                visibility = { lift.value * (1f - shrink.value) },
                hovered = overPocket,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    .onGloballyPositioned { pocketRect = it.boundsInRoot() },
            )
        }

        AnimatedVisibility(
            visible = status != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                text = status.orEmpty(),
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (photo != null && revealed == null) {
            FilledTonalButton(
                onClick = ::openPicker,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp)
                    // Пока объект поднят, на этом месте карман
                    .graphicsLayer { alpha = 1f - lift.value.coerceIn(0f, 1f) },
                enabled = lifted == null,
            ) {
                Text("Другое фото")
            }
        }

        revealed?.let { r ->
            StickerReveal(
                sticker = r.sticker,
                from = r.from,
                onShare = { shareSticker(context, r.uri) },
                onDismiss = { revealed = null },
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp),
        )
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 32.dp),
    ) {
        Text("Lift", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Зажми объект на фото — он оторвётся,\nи из него получится стикер",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onPick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
        ) {
            Text("Выбрать фото", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
}

private fun shareSticker(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = context.contentResolver.getType(uri) ?: "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Отправить стикер"))
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
