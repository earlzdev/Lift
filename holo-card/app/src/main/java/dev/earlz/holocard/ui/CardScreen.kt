package dev.earlz.holocard.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.earlz.holocard.segmentation.MlKitSubjectSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private enum class CardKind(val title: String) { Collectible("Коллекционная"), Bank("Банковская") }

/** Фото больше этого по длинной стороне уменьшаем: для карты хватит, а сегментация быстрее. */
private const val MAX_PHOTO_SIDE = 1600

@Composable
fun CardScreen() {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val tilt = rememberTilt()
    val segmenter = remember { MlKitSubjectSegmenter(context.applicationContext) }
    DisposableEffect(segmenter) { onDispose { segmenter.close() } }

    var kind by remember { mutableStateOf(CardKind.Collectible) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var art by remember { mutableStateOf<CardArt?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri
    }

    LaunchedEffect(photoUri) {
        val uri = photoUri ?: return@LaunchedEffect
        status = "Вырезаю человека…"
        val photo = withContext(Dispatchers.IO) { decodePhoto(context, uri) }
        art = try {
            // Самый крупный объект на фото — он и будет «выходить» из рамки
            val subject = segmenter.segment(photo).maxByOrNull { it.bounds.width() * it.bounds.height() }
            val cutout = subject?.let { withContext(Dispatchers.Default) { it.cutOut(photo) } }
            val b = subject?.bounds
            status = if (subject == null) "Человека не нашлось — фото будет просто в рамке" else null
            CardArt(
                photo = photo.asImageBitmap(),
                subject = cutout?.asImageBitmap(),
                subjectBounds = b?.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) },
            )
        } catch (e: Exception) {
            Log.e("HoloCard", "Segmentation failed", e)
            status = "Не удалось вырезать: ${e.message}"
            CardArt(photo.asImageBitmap(), null, null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Color(0xFF1C1F2A), Color(0xFF07080B)))),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp),
        ) {
            CardKind.entries.forEach { k ->
                val selected = k == kind
                Text(
                    text = k.title,
                    color = if (selected) Color.Black else Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                    modifier = Modifier
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                        .clickable { kind = k }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }

        AnimatedContent(
            targetState = kind,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.9f)) togetherWith fadeOut() },
            label = "card",
            modifier = Modifier.align(Alignment.Center),
        ) { k ->
            when (k) {
                CardKind.Collectible -> HoloCard(
                    cardSize = COLLECTIBLE_SIZE,
                    overflowTop = COLLECTIBLE_OVERFLOW,
                    tilt = { tilt.value },
                    foil = { card, _ -> collectibleFoil(card, density) },
                    cornerRadius = 18.dp,
                ) { t ->
                    CollectibleFace(art, t, COLLECTIBLE_OVERFLOW, COLLECTIBLE_SIZE)
                }
                CardKind.Bank -> HoloCard(
                    cardSize = BANK_SIZE,
                    overflowTop = 0.dp,
                    tilt = { tilt.value },
                    foil = { _, back -> bankFoil(density, back) },
                    back = { BankBack(BANK_SIZE) },
                ) {
                    BankFace(BANK_SIZE)
                }
            }
        }

        if (kind == CardKind.Collectible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilledTonalButton(onClick = {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Text(if (art == null) "Выбрать фото" else "Другое фото")
                }
            }
        }
        status?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 84.dp),
            )
        }
    }
}

private fun decodePhoto(context: Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
        // Software-битмап нужен, чтобы читать пиксели и вырезать человека по маске
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longSide = max(info.size.width, info.size.height)
        if (longSide > MAX_PHOTO_SIDE) {
            val k = MAX_PHOTO_SIDE.toFloat() / longSide
            decoder.setTargetSize((info.size.width * k).roundToInt(), (info.size.height * k).roundToInt())
        }
    }
