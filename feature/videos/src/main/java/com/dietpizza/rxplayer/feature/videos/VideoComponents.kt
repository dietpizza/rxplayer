package com.dietpizza.rxplayer.feature.videos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.ui.thumbnail.MediaThumbnail
import kotlinx.coroutines.delay
import com.dietpizza.rxplayer.core.designsystem.component.RxLayoutChoice
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt

private val ChipShape = RoundedCornerShape(4.dp)
private val ChipScrim = Color.Black.copy(alpha = 0.62f)
private val ResumeTrack = Color.White.copy(alpha = 0.35f)
private val ResumeBarHeight = 3.dp

/**
 * Video thumbnail with the duration chip (bottom-end) and, when [resumeFraction] > 0, a resume
 * bar along the bottom edge. The caller sets size / aspect ratio through [modifier]; the bar is
 * drawn in the draw phase with no allocations, and clipped by [shape] like the image.
 */
@Composable
internal fun VideoThumbnail(
    contentUri: String?,
    duration: String?,
    resumeFraction: Float,
    shape: Shape,
    requestWidth: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val barColor = MaterialTheme.colorScheme.primary
    // The resume bar and the watched dimming are drawn, so say them; merged into the row.
    val progress = when {
        resumeFraction > 0f -> stringResource(R.string.video_progress, (resumeFraction * 100).roundToInt())
        dimmed -> stringResource(R.string.video_watched)
        else -> null
    }
    Box(
        modifier
            .then(if (progress != null) Modifier.semantics { stateDescription = progress } else Modifier)
            .clip(shape)
            .drawWithContent {
                drawContent()
                if (resumeFraction > 0f) {
                    val h = ResumeBarHeight.toPx()
                    val top = size.height - h
                    drawRect(ResumeTrack, topLeft = Offset(0f, top), size = Size(size.width, h))
                    drawRect(barColor, topLeft = Offset(0f, top), size = Size(size.width * resumeFraction, h))
                }
            },
    ) {
        MediaThumbnail(
            contentUri = contentUri,
            requestWidth = requestWidth,
            modifier = Modifier.fillMaxSize(),
        )
        if (dimmed) Box(Modifier.fillMaxSize().background(WatchedScrim))
        if (duration != null) {
            Text(
                text = duration,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = if (resumeFraction > 0f) 7.dp else 4.dp)
                    .background(ChipScrim, ChipShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

private val WatchedScrim = Color.Black.copy(alpha = 0.35f)

/** "1080p" as a small outlined-looking tonal tag. */
@Composable
internal fun ResolutionTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, ChipShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Unplayed marker. */
@Composable
internal fun NewDot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.video_new)
    Box(
        modifier
            .semantics { contentDescription = description }
            .size(8.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

/**
 * Loading state that only appears if loading takes noticeably long, so a fast MediaStore query
 * never flashes an indicator for a frame or two.
 */
@Composable
internal fun DelayedLoadingIndicator(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_GRACE_MS)
        visible = true
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visible) LoadingIndicator()
    }
}

private const val LOADING_GRACE_MS = 300L

/** Scaffold padding + shell padding + the list's own gutter, combined once. */
@Composable
internal fun listPadding(
    innerPadding: PaddingValues,
    extra: PaddingValues,
    gutter: Dp = 16.dp,
    top: Dp = 8.dp,
    bottom: Dp = 24.dp,
): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = innerPadding.calculateStartPadding(dir) + extra.calculateStartPadding(dir) + gutter,
        end = innerPadding.calculateEndPadding(dir) + extra.calculateEndPadding(dir) + gutter,
        top = innerPadding.calculateTopPadding() + extra.calculateTopPadding() + top,
        bottom = innerPadding.calculateBottomPadding() + extra.calculateBottomPadding() + bottom,
    )
}

/** Strings for the shared sort sheet. */
@Composable
internal fun sortLabels() = com.dietpizza.rxplayer.core.designsystem.component.RxSortLabels(
    title = stringResource(R.string.sort_title),
    ascending = stringResource(R.string.sort_ascending),
    descending = stringResource(R.string.sort_descending),
    button = stringResource(R.string.sort),
)

/** The list/grid section of the sort & view dialog. */
@Composable
internal fun layoutChoice(grid: Boolean, onGridChange: (Boolean) -> Unit) = RxLayoutChoice(
    title = stringResource(R.string.view_layout),
    listLabel = stringResource(R.string.view_list),
    gridLabel = stringResource(R.string.view_grid),
    grid = grid,
    onGridChange = onGridChange,
)
