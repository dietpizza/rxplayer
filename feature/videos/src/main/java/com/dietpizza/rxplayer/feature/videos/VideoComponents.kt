package com.dietpizza.rxplayer.feature.videos

import kotlin.math.sqrt
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.draw.alpha
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

/** One detail ("1080p", "1.4 GB", "12 videos"…) as a small tonal chip. */
@Composable
internal fun FieldChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, ChipShape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * New/unplayed badge for a folder or video thumbnail, like an online-status dot: a filled dot with a
 * [ring] in the colour behind the thumbnail so it reads as cut out. Put it in a Box with the
 * thumbnail and pass the thumbnail's [cornerRadius]: the dot is centred on the rounded corner's
 * curve, so about half of it hangs outside the image.
 */
@Composable
internal fun BoxScope.NewBadge(cornerRadius: Dp, ring: Color) {
    val description = stringResource(R.string.video_new)
    // Where the corner arc crosses the diagonal, measured in from each edge.
    val inset = cornerRadius * (1f - 1f / sqrt(2f))
    val shift = NewBadgeSize / 2 - inset
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .offset(x = shift, y = -shift)
            .semantics { contentDescription = description }
            .size(NewBadgeSize)
            .background(ring, CircleShape)
            .padding(NewBadgeRing)
            .background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

private val NewBadgeSize = 15.dp
private val NewBadgeRing = 2.5.dp

/** A card/row title: the grid's titleSmall everywhere. */
@Composable
internal fun ItemTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    maxLines: Int = 1,
    minLines: Int = 1,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        minLines = minLines,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Field chips that wrap onto further lines when they don't fit, after any [leading] markers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FieldChips(
    chips: List<String>,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    leading: @Composable () -> Unit = {},
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        // Grid cards: one line only (chips that don't fit are left out), so every card in a
        // row is the same height.
        maxLines = if (singleLine) 1 else Int.MAX_VALUE,
    ) {
        leading()
        chips.forEach { FieldChip(it) }
        // Keep the line's height even when this item has no chips (e.g. unknown resolution).
        if (singleLine && chips.isEmpty()) FieldChip("0", Modifier.alpha(0f))
    }
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
