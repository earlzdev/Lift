package dev.earlz.lift.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.earlz.lift.gallery.Photo
import dev.earlz.lift.gallery.PhotoRepository
import dev.earlz.lift.segmentation.MlKitSubjectSegmenter
import dev.earlz.lift.segmentation.SubjectSegmenter

/** Галерея и просмотр фото; миниатюра плавно разворачивается в полноэкранное фото и обратно. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun LiftApp() {
    val context = LocalContext.current
    // Один сегментатор на всё приложение: модель ML Kit грузится один раз
    val segmenter: SubjectSegmenter = remember { MlKitSubjectSegmenter(context.applicationContext) }
    DisposableEffect(segmenter) { onDispose { segmenter.close() } }

    var access by remember { mutableStateOf(galleryAccess(context)) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var reloads by remember { mutableIntStateOf(0) }
    // null — открыта сетка, иначе — индекс фото в просмотрщике
    var opened by remember { mutableStateOf<Int?>(null) }
    val gridState = rememberLazyGridState()

    val requestAccess = rememberGalleryPermissionLauncher {
        access = galleryAccess(context)
        reloads++
    }
    // Доступ могли поменять в настройках, а фото — добавить, пока нас не было
    LifecycleResumeEffect(Unit) {
        access = galleryAccess(context)
        reloads++
        onPauseOrDispose {}
    }
    LaunchedEffect(access, reloads) {
        photos = if (access == GalleryAccess.None) emptyList() else PhotoRepository.load(context)
    }

    SharedTransitionLayout {
        AnimatedContent(
            targetState = opened,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "gallery",
        ) { index ->
            if (index == null || index !in photos.indices) {
                GalleryScreen(
                    photos = photos,
                    access = access,
                    gridState = gridState,
                    animatedScope = this,
                    onRequestAccess = { requestAccess.launch(galleryPermissions) },
                    onOpen = { opened = it },
                )
            } else {
                val pagerState = remember { PagerState(currentPage = index) { photos.size } }
                ViewerScreen(
                    photos = photos,
                    pagerState = pagerState,
                    segmenter = segmenter,
                    animatedScope = this,
                    onBack = {
                        // Возвращаемся к тому фото, которое листали, — переход полетит в его миниатюру
                        opened = null
                    },
                )
                LaunchedEffect(pagerState.currentPage) {
                    // Сетка прокручивается вслед за просмотрщиком, чтобы миниатюра была на экране
                    gridState.scrollToItem(pagerState.currentPage + 1)
                }
            }
        }
    }
}
