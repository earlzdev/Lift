package dev.earlz.lift.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.earlz.lift.gallery.Photo

/** Насколько приложению открыта галерея. */
enum class GalleryAccess { Full, Partial, None }

fun galleryAccess(context: Context): GalleryAccess {
    fun granted(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    return when {
        granted(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.Full
        // Android 14+: пользователь выбрал «Разрешить доступ к выбранным фото»
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.Partial
        else -> GalleryAccess.None
    }
}

/** Что просить у системы: на Android 14+ нужно явно разрешить и частичный доступ. */
val galleryPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    } else {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    }

@Composable
fun rememberGalleryPermissionLauncher(onResult: () -> Unit) =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }

/** Ключ общего элемента: миниатюра в сетке и фото в просмотрщике — «один и тот же» объект. */
fun photoSharedKey(photo: Photo) = "photo-${photo.id}"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.GalleryScreen(
    photos: List<Photo>,
    access: GalleryAccess,
    gridState: LazyGridState,
    animatedScope: AnimatedVisibilityScope,
    onRequestAccess: () -> Unit,
    onOpen: (index: Int) -> Unit,
) {
    if (access == GalleryAccess.None) {
        AccessRequest(onRequestAccess)
        return
    }
    val bars = WindowInsets.navigationBars.asPaddingValues()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = bars.calculateBottomPadding()),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header(count = photos.size, partial = access == GalleryAccess.Partial, onRequestAccess)
        }
        items(photos, key = { it.id }) { photo ->
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .sharedElement(
                        rememberSharedContentState(photoSharedKey(photo)),
                        animatedVisibilityScope = animatedScope,
                    )
                    .clickable { onOpen(photos.indexOf(photo)) },
            )
        }
    }
}

@Composable
private fun Header(count: Int, partial: Boolean, onRequestAccess: () -> Unit) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = top + 24.dp, bottom = 12.dp)) {
        Text("Lift", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "$count фото · открой и зажми объект",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
        )
        if (partial) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = "Открыт доступ только к части фото",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRequestAccess) { Text("Изменить") }
            }
        }
    }
}

@Composable
private fun AccessRequest(onRequestAccess: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 32.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                onClick = onRequestAccess,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) {
                Text("Открыть галерею", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
    }
}
