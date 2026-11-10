package com.dietpizza.rxplayer.core.ui.slideshow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.designsystem.component.RxBars
import com.dietpizza.rxplayer.core.designsystem.component.RxSheet
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import com.dietpizza.rxplayer.core.model.SlideshowCounts
import com.dietpizza.rxplayer.core.model.SlideshowMediaType
import com.dietpizza.rxplayer.core.model.SlideshowOptions
import com.dietpizza.rxplayer.core.ui.R
import java.text.NumberFormat

/**
 * The slideshow options sheet, opened before a slideshow starts (Gallery and album FABs, the
 * photo viewer's overflow). Same look as the sort sheet: **Include** (photos / videos / GIFs,
 * at least one stays on), **Playback** (shuffle, loop), **Seconds per photo**, then **Start**.
 *
 * Choices are local to the sheet until **Start**, which calls [onStart] with them; the caller
 * persists them ([com.dietpizza.rxplayer.core.datastore.SlideshowSettingsRepository]), filters
 * its items ([com.dietpizza.rxplayer.core.model.filterForSlideshow]) and launches
 * [com.dietpizza.rxplayer.core.ui.RxIntents.slideshow]. The sheet doesn't close itself on
 * Start: [onStart] should also hide it.
 *
 * @param initial the options to open with (the last used ones).
 * @param counts items of each type in the source, shown per toggle; Start is disabled with a
 *   short message when the choices match none. Null while unknown (Start stays enabled).
 */
@Composable
fun SlideshowOptionsSheet(
    initial: SlideshowOptions,
    counts: SlideshowCounts?,
    onStart: (SlideshowOptions) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var options by remember(initial) { mutableStateOf(initial.sanitized()) }
    RxSheet(title = stringResource(R.string.slideshow_sheet_title), onDismissRequest = onDismissRequest) {
        SectionLabel(stringResource(R.string.slideshow_sheet_include), top = 0.dp)
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            TypeRows.forEachIndexed { index, row ->
                val checked = options.includes(row.type)
                // The last type left on can't be turned off.
                val locked = checked && options.withType(row.type, false) == options
                SegmentedListItem(
                    colors = RxBars.sheetListItemColors(),
                    selected = checked,
                    onClick = { options = options.withType(row.type, !checked) },
                    enabled = !locked,
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = TypeRows.size),
                    verticalAlignment = Alignment.CenterVertically,
                    leadingContent = { Icon(row.icon, contentDescription = null) },
                    supportingContent = counts?.let { c ->
                        {
                            val n = c.of(row.type)
                            Text(pluralStringResource(R.plurals.slideshow_sheet_item_count, n, formatCount(n)))
                        }
                    },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null, enabled = !locked) },
                    modifier = Modifier.semantics { role = Role.Checkbox },
                ) {
                    Text(stringResource(row.label))
                }
            }
        }

        SectionLabel(stringResource(R.string.slideshow_sheet_playback))
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            SwitchRow(
                index = 0,
                icon = RxIcons.Shuffle,
                label = stringResource(R.string.slideshow_sheet_shuffle),
                supporting = stringResource(if (options.shuffle) R.string.slideshow_sheet_shuffle_on else R.string.slideshow_sheet_shuffle_off),
                checked = options.shuffle,
                onCheckedChange = { options = options.copy(shuffle = it) },
            )
            SwitchRow(
                index = 1,
                icon = RxIcons.Repeat,
                label = stringResource(R.string.slideshow_sheet_loop),
                supporting = stringResource(if (options.loop) R.string.slideshow_sheet_loop_on else R.string.slideshow_sheet_loop_off),
                checked = options.loop,
                onCheckedChange = { options = options.copy(loop = it) },
            )
        }

        SectionLabel(stringResource(R.string.slideshow_sheet_seconds_per_photo))
        val choices = SlideshowOptions.SECONDS_CHOICES
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(RxBars.ButtonGroupGap),
        ) {
            choices.forEachIndexed { index, seconds ->
                ToggleButton(
                    colors = RxBars.toggleButtonColors(),
                    checked = options.secondsPerPhoto == seconds,
                    onCheckedChange = { options = options.copy(secondsPerPhoto = seconds) },
                    shapes = RxBars.connectedShapes(index, lastIndex = choices.lastIndex),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .semantics { role = Role.RadioButton },
                ) {
                    Text(stringResource(R.string.slideshow_sheet_seconds, seconds), maxLines = 1)
                }
            }
        }

        val nothing = counts != null && counts.matching(options) == 0
        Spacer(Modifier.height(24.dp))
        if (nothing) {
            Text(
                stringResource(R.string.slideshow_sheet_nothing_to_show),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }
        Button(
            onClick = { onStart(options) },
            enabled = !nothing,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(RxIcons.Slideshow, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.slideshow_sheet_start), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private class TypeRow(val type: SlideshowMediaType, val label: Int, val icon: ImageVector)

private val TypeRows = listOf(
    TypeRow(SlideshowMediaType.PHOTO, R.string.slideshow_sheet_photos, RxIcons.Image),
    TypeRow(SlideshowMediaType.VIDEO, R.string.slideshow_sheet_videos, RxIcons.Videocam),
    TypeRow(SlideshowMediaType.GIF, R.string.slideshow_sheet_gifs, RxIcons.Gif),
)

@Composable
private fun SwitchRow(
    index: Int,
    icon: ImageVector,
    label: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SegmentedListItem(
        colors = RxBars.sheetListItemColors(),
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = 2),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = { Text(supporting) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    ) {
        Text(label)
    }
}

@Composable
private fun SectionLabel(text: String, top: Dp = 24.dp) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = top, bottom = 12.dp),
    )
}

private fun formatCount(n: Int): String = NumberFormat.getIntegerInstance().format(n)
