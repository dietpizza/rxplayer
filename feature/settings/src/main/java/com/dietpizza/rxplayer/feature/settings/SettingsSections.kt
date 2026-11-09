package com.dietpizza.rxplayer.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.SwipeDown
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.ZoomIn
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
import androidx.compose.ui.semantics.Role
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
    SectionHeader("Library")
    SettingsGroup {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.ViewList, contentDescription = null) },
            supportingContent = {
                ConnectedChoices(
                    options = VideoViewMode.entries,
                    selected = library.videoViewMode,
                    label = { if (it == VideoViewMode.LIST) "List" else "Grid" },
                    icon = { if (it == VideoViewMode.LIST) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView },
                    onSelect = actions.onVideoViewMode,
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text("Video list view") }

        SegmentedListItem(
            onClick = { sortDialog = true },
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null) },
            supportingContent = { Text(sortLabel(library.videoSort)) },
            trailingContent = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
        ) { Text("Default video sort") }

        SegmentedListItem(
            onClick = { columnsDialog = true },
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = count),
            leadingContent = { Icon(Icons.Rounded.GridView, contentDescription = null) },
            supportingContent = { Text(columnsLabel(library.galleryGridColumns) + " · pinch an album to change it") },
            trailingContent = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null) },
        ) { Text("Default grid columns") }

        SwitchRow(
            index = 3,
            count = count,
            icon = Icons.Rounded.Visibility,
            title = "Show hidden folders",
            supporting = "Include folders with a .nomedia file or a name starting with a dot",
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
            title = "Default grid columns",
            options = listOf(LibrarySettings.GRID_COLUMNS_AUTO) +
                (LibrarySettings.MIN_GRID_COLUMNS..LibrarySettings.MAX_GRID_COLUMNS).toList(),
            selected = library.galleryGridColumns,
            label = ::columnsLabel,
            onSelect = {
                actions.onGridColumns(it)
                columnsDialog = false
            },
            onDismiss = { columnsDialog = false },
        )
    }
}

private fun columnsLabel(columns: Int): String =
    if (columns == LibrarySettings.GRID_COLUMNS_AUTO) "Automatic" else "$columns columns"

private fun VideoSortField.label(): String = when (this) {
    VideoSortField.NAME -> "Name"
    VideoSortField.DATE_MODIFIED -> "Date modified"
    VideoSortField.SIZE -> "Size"
    VideoSortField.DURATION -> "Duration"
}

private fun sortLabel(sort: VideoSort): String {
    val direction = when (sort.field) {
        VideoSortField.NAME -> if (sort.descending) "Z to A" else "A to Z"
        VideoSortField.DATE_MODIFIED -> if (sort.descending) "newest first" else "oldest first"
        VideoSortField.SIZE -> if (sort.descending) "largest first" else "smallest first"
        VideoSortField.DURATION -> if (sort.descending) "longest first" else "shortest first"
    }
    return sort.field.label() + ", " + direction
}

@Composable
private fun SortDialog(current: VideoSort, onSelect: (VideoSort) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default video sort") },
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
                    label = { if (it) "Descending" else "Ascending" },
                    icon = null,
                    onSelect = { onSelect(current.copy(descending = it)) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

// ---- Player ------------------------------------------------------------------------------------

private val SeekSteps = listOf(5, 10, 15, 30)
private val LongPressSpeeds = listOf(1.5f, 2f, 3f)

@Composable
internal fun PlayerSection(player: PlayerSettings, actions: SettingsActions) {
    val edit = actions.onPlayer
    SectionHeader("Player")
    // Gestures
    SettingsGroup {
        val count = 7
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(Icons.Rounded.FastForward, contentDescription = null) },
            supportingContent = {
                ConnectedChoices(
                    options = SeekSteps,
                    selected = player.seekStepSeconds,
                    label = { "${it}s" },
                    icon = null,
                    onSelect = { step -> edit { it.copy(seekStepSeconds = step) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text("Seek step") }

        SliderRow(
            index = 1,
            count = count,
            icon = Icons.Rounded.Swipe,
            title = "Swipe-seek sensitivity",
            value = player.swipeSeekSensitivity,
            range = 0.5f..2f,
            steps = 5,
            valueLabel = { String.format(Locale.getDefault(), "%.2f×", it) },
            onChange = { v -> edit { it.copy(swipeSeekSensitivity = v) } },
        )

        SwitchRow(
            index = 2,
            count = count,
            icon = Icons.Rounded.TouchApp,
            title = "Double-tap to play/pause",
            supporting = "Double-tap the sides to seek",
            checked = player.doubleTapPlayPause,
            onChange = { v -> edit { it.copy(doubleTapPlayPause = v) } },
        )

        SwitchRow(
            index = 3,
            count = count,
            icon = Icons.Rounded.Speed,
            title = "Long-press for speed",
            supporting = "Hold to play faster",
            checked = player.longPressSpeedEnabled,
            onChange = { v -> edit { it.copy(longPressSpeedEnabled = v) } },
        )

        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 4, count = count),
            enabled = player.longPressSpeedEnabled,
            leadingContent = { Icon(Icons.Rounded.Speed, contentDescription = null) },
            supportingContent = {
                ConnectedChoices(
                    options = LongPressSpeeds,
                    selected = player.longPressSpeed,
                    label = ::speedLabel,
                    icon = null,
                    enabled = player.longPressSpeedEnabled,
                    onSelect = { speed -> edit { it.copy(longPressSpeed = speed) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text("Long-press speed") }

        SwitchRow(
            index = 5,
            count = count,
            icon = Icons.Rounded.ZoomIn,
            title = "Pinch to zoom",
            supporting = null,
            checked = player.pinchToZoom,
            onChange = { v -> edit { it.copy(pinchToZoom = v) } },
        )

        SwitchRow(
            index = 6,
            count = count,
            icon = Icons.Rounded.SwipeDown,
            title = "Swipe down to close",
            supporting = null,
            checked = player.swipeDownToClose,
            onChange = { v -> edit { it.copy(swipeDownToClose = v) } },
        )
    }

    Spacer16()

    // Playback
    SettingsGroup {
        val count = 4
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(Icons.Rounded.Restore, contentDescription = null) },
            supportingContent = {
                ConnectedChoices(
                    options = ResumeMode.entries,
                    selected = player.resumeMode,
                    label = {
                        when (it) {
                            ResumeMode.ASK -> "Ask"
                            ResumeMode.AUTO -> "Always"
                            ResumeMode.NEVER -> "Never"
                        }
                    },
                    icon = null,
                    onSelect = { mode -> edit { it.copy(resumeMode = mode) } },
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text("Resume where you left off") }

        SwitchRow(
            index = 1,
            count = count,
            icon = Icons.Rounded.Image,
            title = "Seek-bar thumbnails",
            supporting = "Preview frames while scrubbing",
            checked = player.seekBarThumbnails,
            onChange = { v -> edit { it.copy(seekBarThumbnails = v) } },
        )

        SwitchRow(
            index = 2,
            count = count,
            icon = Icons.Rounded.SkipNext,
            title = "Auto-play next",
            supporting = "Play the next video in the folder or playlist",
            checked = player.autoPlayNext,
            onChange = { v -> edit { it.copy(autoPlayNext = v) } },
        )

        SwitchRow(
            index = 3,
            count = count,
            icon = Icons.Rounded.PictureInPictureAlt,
            title = "Picture-in-picture",
            supporting = "Keep playing in a small window when you leave the app",
            checked = player.autoPip,
            onChange = { v -> edit { it.copy(autoPip = v) } },
        )
    }

    Spacer16()

    // Interruptions
    SettingsGroup {
        val count = 3
        SwitchRow(
            index = 0,
            count = count,
            icon = Icons.Rounded.Headphones,
            title = "Pause when headphones disconnect",
            supporting = null,
            checked = player.pauseOnHeadphonesUnplugged,
            onChange = { v -> edit { it.copy(pauseOnHeadphonesUnplugged = v) } },
        )
        SwitchRow(
            index = 1,
            count = count,
            icon = Icons.Rounded.Call,
            title = "Pause during calls",
            supporting = null,
            checked = player.pauseDuringCalls,
            onChange = { v -> edit { it.copy(pauseDuringCalls = v) } },
        )
        SwitchRow(
            index = 2,
            count = count,
            icon = Icons.Rounded.PhoneCallback,
            title = "Resume after a call",
            supporting = null,
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
    SectionHeader("Subtitles")
    SettingsGroup {
        SwitchRow(
            index = 0,
            count = count,
            icon = Icons.Rounded.Subtitles,
            title = "Load subtitle files automatically",
            supporting = "Same-name .srt, .ass, .ssa or .vtt next to the video",
            checked = subtitles.autoLoadSidecar,
            onChange = { v -> edit { it.copy(autoLoadSidecar = v) } },
        )
        SliderRow(
            index = 1,
            count = count,
            icon = Icons.Rounded.FormatSize,
            title = "Text size",
            value = subtitles.textScale,
            range = SubtitleSettings.MIN_SCALE..SubtitleSettings.MAX_SCALE,
            steps = 7,
            valueLabel = { "${(it * 100).roundToInt()}%" },
            onChange = { v -> edit { it.copy(textScale = v) } },
        )
        SwitchRow(
            index = 2,
            count = count,
            icon = Icons.Rounded.ClosedCaption,
            title = "Background box",
            supporting = "Draw a dark box behind the text",
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
    SectionHeader("Storage")
    SettingsGroup {
        SegmentedListItem(
            onClick = actions.onClearCache,
            enabled = cacheBytes != null && cacheBytes > 0,
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
            supportingContent = {
                Text(
                    if (cacheBytes == null) "Calculating…" else "Thumbnails and seek previews · " + formatBytes(cacheBytes),
                )
            },
        ) { Text("Clear thumbnail cache") }
        SegmentedListItem(
            onClick = { confirmHistory = true },
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
            leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
            supportingContent = { Text("Resume positions are kept") },
        ) { Text("Clear history") }
    }

    if (confirmHistory) {
        AlertDialog(
            onDismissRequest = { confirmHistory = false },
            title = { Text("Clear history?") },
            text = { Text("Every video is removed from History. Resume positions and watched marks are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        actions.onClearHistory()
                        confirmHistory = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmHistory = false }) { Text("Cancel") } },
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

@Composable
private fun Spacer16() {
    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
}

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
    valueLabel: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableFloatStateOf(value) }
    val shown = if (dragging) local else value
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Text(valueLabel(shown), style = MaterialTheme.typography.labelLarge) },
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
                modifier = Modifier.padding(top = 4.dp),
            )
        },
    ) { Text(title) }
}

/** A connected button group (M3 Expressive) for 2–4 short choices. */
@Composable
private fun <T> ConnectedChoices(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    icon: ((T) -> ImageVector)?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.Check else icon(option),
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
    label: (T) -> String,
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 10.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
