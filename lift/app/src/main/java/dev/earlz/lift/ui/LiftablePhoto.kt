package dev.earlz.lift.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.earlz.lift.gallery.Photo
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

/** Фото, разобранное на объекты. Размеры нужны, чтобы переводить координаты экрана в пиксели фото. */
private class Segmented(val width: Int, val height: Int, val cutouts: List<SubjectCutout>)

/** Поднятый объект: где он был на экране и где его коснулись. */
private class Lifted(val cutout: SubjectCutout, val rect: Rect, val touch: Offset)

/** Готовый стикер, который показываем в финальном кадре. */
private class Revealed(val sticker: ImageBitmap, val uri: Uri, val from: Offset)

/**
 * Фото на весь экран, из которого можно «оторвать» объект долгим нажатием.
 *
 * Само фото рисует [photoContent] (вписанным целиком по центру) — так просмотрщик может
 * повесить на него переход из сетки галереи. Здесь — всё поверх: затемнение, жесты,
 * поднятый объект, карман и финальный кадр со стикером.
 *
 * @param active страница сейчас на экране: только тогда показываем подсказку
 * @param onLiftingChange true, пока объект поднят — просмотрщик на это время запрещает листать
 */
@Composable
fun LiftablePhoto(
    photo: Photo,
    segmenter: SubjectSegmenter,
    active: Boolean,
    onLiftingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    photoContent: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val onLifting by rememberUpdatedState(onLiftingChange)

    // null — сегментация ещё идёт
    var segmented by remember(photo) { mutableStateOf<Segmented?>(null) }
    var status by remember(photo) { mutableStateOf<String?>("Ищу объекты…") }
    var lifted by remember { mutableStateOf<Lifted?>(null) }
    var revealed by remember { mutableStateOf<Revealed?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    val lift = remember { Animatable(0f) }
    val wave = remember { Animatable(0f) }
    val shrink = remember { Animatable(0f) }
    // Подсказка: по всем найденным объектам один раз пробегает свет
    val hint = remember { Animatable(0f) }
    var showHint by remember { mutableStateOf(false) }
    var hintShown by remember(photo) { mutableStateOf(false) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dropJob by remember { mutableStateOf<Job?>(null) }
    // Поля свечения для каждого объекта; считаются, когда известен размер экрана
    var fields by remember(photo) { mutableStateOf<Map<SubjectCutout, GlowField>>(emptyMap()) }
    val density = LocalDensity.current

    var pocketRect by remember { mutableStateOf<Rect?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val pocketSlop = with(LocalDensity.current) { 24.dp.toPx() }
    fun isOverPocket(l: Lifted?): Boolean =
        l != null && pocketRect?.inflate(pocketSlop)?.contains(l.touch + drag) == true
    val overPocket = isOverPocket(lifted)
    LaunchedEffect(overPocket) {
        if (overPocket) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    LaunchedEffect(lifted != null) { onLifting(lifted != null) }

    // Сегментируем сразу, даже для соседних страниц: к моменту долгого нажатия всё готово
    LaunchedEffect(photo) {
        val bitmap = withContext(Dispatchers.IO) { decodePhoto(context, photo.uri) }
        val cutouts = try {
            val subjects = segmenter.segment(bitmap)
            withContext(Dispatchers.Default) {
                subjects.map { SubjectCutout(it, it.cutOut(bitmap).asImageBitmap()) }
            }
        } catch (e: Exception) {
            Log.e("Lift", "Segmentation failed", e)
            status = "Ошибка сегментации: ${e.message}"
            segmented = Segmented(bitmap.width, bitmap.height, emptyList())
            return@LaunchedEffect
        }
        segmented = Segmented(bitmap.width, bitmap.height, cutouts)
        status = if (cutouts.isEmpty()) null else "Зажми объект"
    }

    fun screenRect(s: Segmented, subject: Subject): Rect {
        val fit = fitRect(s.width, s.height, viewSize.width, viewSize.height)
        val scale = fit.width / s.width
        val b = subject.bounds
        return Rect(
            left = fit.left + b.left * scale,
            top = fit.top + b.top * scale,
            right = fit.left + b.right * scale,
            bottom = fit.top + b.bottom * scale,
        )
    }

    LaunchedEffect(segmented, viewSize) {
        val s = segmented ?: return@LaunchedEffect
        if (viewSize == IntSize.Zero) return@LaunchedEffect
        fields = withContext(Dispatchers.Default) {
            s.cutouts.associateWith { c ->
                with(density) { computeGlowField(c.image.asAndroidBitmap(), screenRect(s, c.subject)) }
            }
        }
    }

    // Подсказку показываем один раз и только когда страницу видно
    LaunchedEffect(active, fields) {
        val s = segmented ?: return@LaunchedEffect
        if (!active || hintShown || fields.isEmpty()) return@LaunchedEffect
        hintShown = true
        showHint = true
        hint.snapTo(0f)
        hint.animateTo(1f, tween(durationMillis = 1400, easing = FastOutSlowInEasing))
        showHint = false
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
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { viewSize = it },
    ) {
        photoContent()

        val s = segmented
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(s, fields) {
                    if (s == null) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { pos ->
                            if (revealed != null) return@detectDragGesturesAfterLongPress
                            val fit = fitRect(s.width, s.height, size.width, size.height)
                            val scale = fit.width / s.width
                            val x = ((pos.x - fit.left) / scale).toInt()
                            val y = ((pos.y - fit.top) / scale).toInt()
                            val hit = s.cutouts.firstOrNull { it.subject.contains(x, y) && it in fields }
                            if (hit != null) pickUp(hit, screenRect(s, hit.subject), pos)
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
            // Затемнение фона следует за подъёмом объекта
            val dim = 0.5f * lift.value.coerceIn(0f, 1f)
            if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
        }

        if (showHint && s != null && viewSize != IntSize.Zero) {
            s.cutouts.forEach { c ->
                val field = fields[c] ?: return@forEach
                val rect = screenRect(s, c.subject)
                LiftedSubjectLayer(
                    cutout = c.image,
                    field = field,
                    rect = rect,
                    touch = rect.center,
                    drag = { Offset.Zero },
                    lift = { 0f },
                    wave = { hint.value },
                )
            }
        }

        lifted?.let { l ->
            LiftedSubjectLayer(
                cutout = l.cutout.image,
                field = fields.getValue(l.cutout),
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
            visible = active && status != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
        ) {
            Text(
                text = status.orEmpty(),
                color = Color.White,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
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
