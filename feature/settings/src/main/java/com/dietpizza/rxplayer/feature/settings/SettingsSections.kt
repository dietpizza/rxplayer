package com.dietpizza.rxplayer.feature.settings

import com.dietpizza.rxplayer.core.designsystem.component.RxBars
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.datastore.SubtitleSettings
import com.dietpizza.rxplayer.core.model.LibrarySettings
import com.dietpizza.rxplayer.core.model.PlayerSettings
import com.dietpizza.rxplayer.core.model.ResumeMode
import com.dietpizza.rxplayer.core.model.VideoSort
import com.dietpizza.rxplayer.core.model.VideoSortField
import com.dietpizza.rxplayer.core.model.VideoViewMode
import java.util.Locale
import kotlin.math.roundToInt

/** Callbacks for every non-appearance setting. */
@Immutable
internal class SettingsActions(
    val onVideoViewMode: (VideoViewMode) -> Unit,
    val onVideoSort: (VideoSort) -> Unit,
    val onGridColumns: (Int) -> Unit,
    val onShowHidden: (Boolean) -> Unit,
    val onPlayer: ((PlayerSettings) -> PlayerSettings) -> Unit,
    val onSubtitles: ((SubtitleSettings) -> SubtitleSettings) -> Unit,
    val onClearCache: () -> Unit,
    val onClearHistory: () -> Unit,
) {
    companion object {
        val None = SettingsActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}

// ---- Library -----------------------------------------------------------------------------------

@Composable
internal fun LibrarySection(library: LibrarySettings, actions: SettingsActions) {
    var sortDialog by rememberSaveable { mutableStateOf(false) }
    var columnsDialog by rememberSaveable { mutableStateOf(false) }
    val count = 4
    SectionHeader(stringResource(R.string.settings_section_library))
    SettingsGroup {
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(RxIcons.ViewList, contentDescription = null) },
            supportingContent = {
                ConnectedChoices(
                    options = VideoViewMode.entries,
                    selected = library.videoViewMode,
                    label = { stringResource(if (it == VideoViewMode.LIST) R.string.settings_view_list else R.string.settings_view_grid) },
                    icon = { if (it == VideoViewMode.LIST) RxIcons.ViewList else RxIcons.GridView },
                    onSelect = actions.onVideoViewMode,
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text(stringResource(R.string.settings_video_view)) }

        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            onClick = { sortDialog = true },
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
            leadingContent = { Icon(RxIcons.Sort, contentDescription = null) },
            supportingContent = { Text(sortLabel(library.videoSort)) },
            trailingContent = { Icon(RxIcons.KeyboardArrowDown, contentDescription = null) },
        ) { Text(stringResource(R.string.settings_default_sort)) }

        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            onClick = { columnsDialog = true },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = count),
            leadingContent = { Icon(RxIcons.GridView, contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.settings_grid_columns_summary, columnsLabel(library.galleryGridColumns))) },
            trailingContent = { Icon(RxIcons.KeyboardArrowDown, contentDescription = null) },
        ) { Text(stringResource(R.string.settings_grid_columns)) }

        SwitchRow(
            index = 3,
            count = count,
            icon = RxIcons.Visibility,
            title = stringResource(R.string.settings_show_hidden),
            supporting = stringResource(R.string.settings_show_hidden_summary),
            checked = library.showHidden,
            onChange = actions.onShowHidden,
        )
    }

    if (sortDialog) {
        SortDialog(
            current = library.videoSort,
            onSelect = actions.onVideoSort,
            onDismiss = { sortDialog = false },
        )
    }
    if (columnsDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_grid_columns),
            options = listOf(LibrarySettings.GRID_COLUMNS_AUTO) +
                (LibrarySettings.MIN_GRID_COLUMNS..LibrarySettings.MAX_GRID_COLUMNS).toList(),
            selected = library.galleryGridColumns,
            label = { columnsLabel(it) },
            onSelect = {
                actions.onGridColumns(it)
                columnsDialog = false
            },
            onDismiss = { columnsDialog = false },
        )
    }
}

@Composable
private fun columnsLabel(columns: Int): String =
    if (columns == LibrarySettings.GRID_COLUMNS_AUTO) {
        stringResource(R.string.settings_grid_columns_auto)
    } else {
        pluralStringResource(R.plurals.settings_grid_columns_count, columns, columns)
    }

@Composable
private fun VideoSortField.label(): String = stringResource(
    when (this) {
        VideoSortField.NAME -> R.string.settings_sort_name
        VideoSortField.DATE_MODIFIED -> R.string.settings_sort_date_modified
        VideoSortField.SIZE -> R.string.settings_sort_size
        VideoSortField.DURATION -> R.string.settings_sort_duration
    },
)

@Composable
private fun sortLabel(sort: VideoSort): String {
    val direction = when (sort.field) {
        VideoSortField.NAME -> if (sort.descending) R.string.settings_sort_z_a else R.string.settings_sort_a_z
        VideoSortField.DATE_MODIFIED -> if (sort.descending) R.string.settings_sort_newest else R.string.settings_sort_oldest
        VideoSortField.SIZE -> if (sort.descending) R.string.settings_sort_largest else R.string.settings_sort_smallest
        VideoSortField.DURATION -> if (sort.descending) R.string.settings_sort_longest else R.string.settings_sort_shortest
    }
    return stringResource(R.string.settings_sort_summary, sort.field.label(), stringResource(direction))
}

@Composable
private fun SortDialog(current: VideoSort, onSelect: (VideoSort) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_default_sort)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    for (field in VideoSortField.entries) {
                        RadioRow(
                            text = field.label(),
                            selected = field == current.field,
                            onClick = { onSelect(VideoSort.defaultFor(field)) },
                        )
                    }
                }
                ConnectedChoices(
                    options = listOf(false, true),
                    selected = current.descending,
                    label = { stringResource(if (it) R.string.settings_sort_descending else R.string.settings_sort_ascending) },
                    icon = null,
                    onSelect = { onSelect(current.copy(descending = it)) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_done)) } },
    )
}

// ---- Player ------------------------------------------------------------------------------------

private val SeekSteps = listOf(5, 10, 15, 30)
private val LongPressSpeeds = listOf(1.5f, 2f, 3f)

@Composable
internal fun PlayerSection(player: PlayerSettings, actions: SettingsActions) {
    val edit = actions.onPlayer
    SectionHeader(stringResource(R.string.settings_section_gestures))
    SettingsGroup {
        val count = 7
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(RxIcons.FastForward, contentDescription = null) },
            supportingContent = {
                ConnectedChoices(
                    options = SeekSteps,
                    selected = player.seekStepSeconds,
                    label = { stringResource(R.string.settings_seek_step_seconds, it) },
                    icon = null,
                    onSelect = { step -> edit { it.copy(seekStepSeconds = step) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text(stringResource(R.string.settings_seek_step)) }

        SliderRow(
            index = 1,
            count = count,
            icon = RxIcons.Swipe,
            title = stringResource(R.string.settings_swipe_sensitivity),
            value = player.swipeSeekSensitivity,
            range = 0.5f..2f,
            steps = 5,
            valueLabel = { String.format(Locale.getDefault(), "%.2f×", it) },
            onChange = { v -> edit { it.copy(swipeSeekSensitivity = v) } },
        )

        SwitchRow(
            index = 2,
            count = count,
            icon = RxIcons.TouchApp,
            title = stringResource(R.string.settings_double_tap),
            supporting = stringResource(R.string.settings_double_tap_summary),
            checked = player.doubleTapPlayPause,
            onChange = { v -> edit { it.copy(doubleTapPlayPause = v) } },
        )

        SwitchRow(
            index = 3,
            count = count,
            icon = RxIcons.Speed,
            title = stringResource(R.string.settings_long_press),
            supporting = stringResource(R.string.settings_long_press_summary),
            checked = player.longPressSpeedEnabled,
            onChange = { v -> edit { it.copy(longPressSpeedEnabled = v) } },
        )

        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            shapes = ListItemDefaults.segmentedShapes(index = 4, count = count),
            enabled = player.longPressSpeedEnabled,
            leadingContent = { Icon(RxIcons.Speed, contentDescription = null) },
            supportingContent = {
                ConnectedChoices(
                    options = LongPressSpeeds,
                    selected = player.longPressSpeed,
                    label = { speedLabel(it) },
                    icon = null,
                    enabled = player.longPressSpeedEnabled,
                    onSelect = { speed -> edit { it.copy(longPressSpeed = speed) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text(stringResource(R.string.settings_long_press_speed)) }

        SwitchRow(
            index = 5,
            count = count,
            icon = RxIcons.ZoomIn,
            title = stringResource(R.string.settings_pinch_zoom),
            supporting = stringResource(R.string.settings_pinch_zoom_summary),
            checked = player.pinchToZoom,
            onChange = { v -> edit { it.copy(pinchToZoom = v) } },
        )

        SwitchRow(
            index = 6,
            count = count,
            icon = RxIcons.SwipeDown,
            title = stringResource(R.string.settings_swipe_down_close),
            supporting = stringResource(R.string.settings_swipe_down_close_summary),
            checked = player.swipeDownToClose,
            onChange = { v -> edit { it.copy(swipeDownToClose = v) } },
        )
    }

    SectionHeader(stringResource(R.string.settings_section_playback))
    SettingsGroup {
        val count = 4
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(RxIcons.Restore, contentDescription = null) },
            supportingContent = {
                ConnectedChoices(
                    options = ResumeMode.entries,
                    selected = player.resumeMode,
                    label = {
                        stringResource(
                            when (it) {
                                ResumeMode.ASK -> R.string.settings_resume_ask
                                ResumeMode.AUTO -> R.string.settings_resume_always
                                ResumeMode.NEVER -> R.string.settings_resume_never
                            },
                        )
                    },
                    icon = null,
                    onSelect = { mode -> edit { it.copy(resumeMode = mode) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text(stringResource(R.string.settings_resume)) }

        SwitchRow(
            index = 1,
            count = count,
            icon = RxIcons.Image,
            title = stringResource(R.string.settings_seek_thumbnails),
            supporting = stringResource(R.string.settings_seek_thumbnails_summary),
            checked = player.seekBarThumbnails,
            onChange = { v -> edit { it.copy(seekBarThumbnails = v) } },
        )

        SwitchRow(
            index = 2,
            count = count,
            icon = RxIcons.SkipNext,
            title = stringResource(R.string.settings_auto_next),
            supporting = stringResource(R.string.settings_auto_next_summary),
            checked = player.autoPlayNext,
            onChange = { v -> edit { it.copy(autoPlayNext = v) } },
        )

        SwitchRow(
            index = 3,
            count = count,
            icon = RxIcons.PictureInPictureAlt,
            title = stringResource(R.string.settings_pip),
            supporting = stringResource(R.string.settings_pip_summary),
            checked = player.autoPip,
            onChange = { v -> edit { it.copy(autoPip = v) } },
        )
    }

    SectionHeader(stringResource(R.string.settings_section_interruptions))
    SettingsGroup {
        val count = 3
        SwitchRow(
            index = 0,
            count = count,
            icon = RxIcons.Headphones,
            title = stringResource(R.string.settings_pause_headphones),
            supporting = stringResource(R.string.settings_pause_headphones_summary),
            checked = player.pauseOnHeadphonesUnplugged,
            onChange = { v -> edit { it.copy(pauseOnHeadphonesUnplugged = v) } },
        )
        SwitchRow(
            index = 1,
            count = count,
            icon = RxIcons.Call,
            title = stringResource(R.string.settings_pause_calls),
            supporting = null,
            checked = player.pauseDuringCalls,
            onChange = { v -> edit { it.copy(pauseDuringCalls = v) } },
        )
        SwitchRow(
            index = 2,
            count = count,
            icon = RxIcons.PhoneCallback,
            title = stringResource(R.string.settings_resume_after_call),
            supporting = stringResource(R.string.settings_resume_after_call_summary),
            checked = player.resumeAfterCall,
            enabled = player.pauseDuringCalls,
            onChange = { v -> edit { it.copy(resumeAfterCall = v) } },
        )
    }
}

private fun speedLabel(speed: Float): String =
    if (speed == speed.roundToInt().toFloat()) "${speed.roundToInt()}×" else "$speed×"

// ---- Subtitles ---------------------------------------------------------------------------------

@Composable
internal fun SubtitlesSection(subtitles: SubtitleSettings, actions: SettingsActions) {
    val edit = actions.onSubtitles
    val count = 3
    SectionHeader(stringResource(R.string.settings_section_subtitles))
    SettingsGroup {
        SwitchRow(
            index = 0,
            count = count,
            icon = RxIcons.Subtitles,
            title = stringResource(R.string.settings_subs_autoload),
            supporting = stringResource(R.string.settings_subs_autoload_summary),
            checked = subtitles.autoLoadSidecar,
            onChange = { v -> edit { it.copy(autoLoadSidecar = v) } },
        )
        SliderRow(
            index = 1,
            count = count,
            icon = RxIcons.FormatSize,
            title = stringResource(R.string.settings_subs_text_size),
            value = subtitles.textScale,
            range = SubtitleSettings.MIN_SCALE..SubtitleSettings.MAX_SCALE,
            steps = 7,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            onChange = { v -> edit { it.copy(textScale = v) } },
        )
        SwitchRow(
            index = 2,
            count = count,
            icon = RxIcons.ClosedCaption,
            title = stringResource(R.string.settings_subs_background),
            supporting = stringResource(R.string.settings_subs_background_summary),
            checked = subtitles.background,
            onChange = { v -> edit { it.copy(background = v) } },
        )
    }
}

// ---- Storage -----------------------------------------------------------------------------------

@Composable
internal fun StorageSection(cacheBytes: Long?, actions: SettingsActions) {
    var confirmHistory by rememberSaveable { mutableStateOf(false) }
    val count = 2
    SectionHeader(stringResource(R.string.settings_section_storage))
    SettingsGroup {
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            onClick = actions.onClearCache,
            enabled = cacheBytes != null && cacheBytes > 0,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(RxIcons.DeleteSweep, contentDescription = null) },
            supportingContent = {
                Text(
                    if (cacheBytes == null) {
                        stringResource(R.string.settings_cache_calculating)
                    } else {
                        stringResource(R.string.settings_cache_summary, formatBytes(cacheBytes))
                    },
                )
            },
        ) { Text(stringResource(R.string.settings_clear_cache)) }
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            onClick = { confirmHistory = true },
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
            leadingContent = { Icon(RxIcons.History, contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.settings_clear_history_summary)) },
        ) { Text(stringResource(R.string.settings_clear_history)) }
    }

    if (confirmHistory) {
        AlertDialog(
            onDismissRequest = { confirmHistory = false },
            title = { Text(stringResource(R.string.settings_clear_history_title)) },
            text = { Text(stringResource(R.string.settings_clear_history_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onClearHistory()
                        confirmHistory = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.settings_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmHistory = false }) { Text(stringResource(R.string.settings_cancel)) } },
        )
    }
}

/** Decimal units like the system Files app: "820 KB", "4.2 MB". */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 1_000) return "$bytes B"
    var value = bytes / 1_000.0
    var unit = 0
    val units = arrayOf("KB", "MB", "GB", "TB")
    while (value >= 1_000 && unit < units.lastIndex) {
        value /= 1_000
        unit++
    }
    return if (value < 10) {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    } else {
        String.format(Locale.getDefault(), "%d %s", value.toLong(), units[unit])
    }
}

// ---- Building blocks ---------------------------------------------------------------------------

/**
 * A toggle row. `onClick` (not `checked`), so the row never takes a selected highlight; the Switch
 * alone shows state (decision 34).
 */
@Composable
internal fun SwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    supporting: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    SegmentedListItem(
        colors = RxBars.cardListItemColors(),
        onClick = { onChange(!checked) },
        modifier = Modifier.switchSemantics(checked),
        enabled = enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
    ) { Text(title) }
}

/** A slider row; the value is written when the drag ends, not on every frame. */
@Composable
private fun SliderRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: @Composable (Float) -> String,
    onChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value
    val shownLabel = valueLabel(shown)
    SegmentedListItem(
        colors = RxBars.cardListItemColors(),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Text(shownLabel, style = MaterialTheme.typography.labelLarge) },
        supportingContent = {
            Slider(
                value = shown,
                onValueChange = {
                    dragging = true
                    local = it
                },
                onValueChangeFinished = {
                    onChange(local)
                    dragging = false
                },
                valueRange = range,
                steps = steps,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics {
                        contentDescription = title
                        stateDescription = shownLabel
                    },
            )
        },
    ) { Text(title) }
}

/** A connected button group (M3 Expressive) for 2–4 short choices. */
@Composable
private fun <T> ConnectedChoices(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    icon: ((T) -> ImageVector)?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(RxBars.ButtonGroupGap),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                colors = RxBars.toggleButtonColors(),
                checked = checked,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = RxBars.connectedShapes(index, options.lastIndex),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = if (checked) RxIcons.Check else icon(option),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                }
                Text(label(option), maxLines = 1)
            }
        }
    }
}

/** Single choice from a short list, applied on tap. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                for (option in options) {
                    RadioRow(text = label(option), selected = option == selected, onClick = { onSelect(option) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
