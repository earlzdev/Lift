package dev.earlz.lift.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.earlz.lift.gallery.Photo
import dev.earlz.lift.segmentation.SubjectSegmenter

/** Фото на весь экран с листанием, как в «Галерее». На каждом фото можно оторвать объект. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.ViewerScreen(
    photos: List<Photo>,
    pagerState: PagerState,
    segmenter: SubjectSegmenter,
    animatedScope: AnimatedVisibilityScope,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var lifting by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            // Соседние страницы собираются заранее — и сегментируются, пока их не видно
            beyondViewportPageCount = 1,
            // Пока объект поднят, палец тащит объект, а не листает фото
            userScrollEnabled = !lifting,
            key = { photos[it].id },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val photo = photos[page]
            LiftablePhoto(
                photo = photo,
                segmenter = segmenter,
                active = page == pagerState.currentPage && !pagerState.isScrollInProgress,
                onLiftingChange = { lifting = it },
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedElement(
                            rememberSharedContentState(photoSharedKey(photo)),
                            animatedVisibilityScope = animatedScope,
                        ),
                )
            }
        }

        AnimatedVisibility(
            visible = !lifting,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
                Text(
                    text = "${pagerState.currentPage + 1} из ${photos.size}",
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
