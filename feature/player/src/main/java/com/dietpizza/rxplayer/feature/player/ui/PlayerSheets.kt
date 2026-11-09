package com.dietpizza.rxplayer.feature.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.player.MediaInfo
import com.dietpizza.rxplayer.core.player.PlaybackController
import com.dietpizza.rxplayer.core.player.tracks.TrackOption
import com.dietpizza.rxplayer.core.player.zoom.AspectMode
import java.util.Locale
import kotlin.math.roundToInt

private val SPEED_PRESETS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
private const val MIN_SPEED = 0.25f
private const val MAX_SPEED = 4f

/** Subtitle size presets (multiplier on the base size). */
private val SUBTITLE_SIZES = listOf("S" to 0.75f, "M" to 1f, "L" to 1.3f, "XL" to 1.65f)

internal fun formatSpeed(speed: Float): String {
    val r = (speed * 100).roundToInt() / 100f
    val s = if (r % 1f == 0f) r.toInt().toString() else String.format(Locale.ROOT, "%.2f", r).trimEnd('0')
    return "$s×"
}

@Composable
private fun PlayerSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
    )
}

@Composable
private fun TrackRow(title: String, detail: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail.isNotEmpty()) {
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun TrackOption.rowDetail(): String =
    if (supported) detail else listOf(detail, "Unsupported").filter { it.isNotEmpty() }.joinToString(" · ")

@Composable
internal fun AudioTrackSheet(controller: PlaybackController, onDismiss: () -> Unit) {
    val menu by controller.trackMenu.collectAsStateWithLifecycle()
    PlayerSheet(onDismiss) {
        SheetTitle("Audio")
        for (option in menu.audio) {
            TrackRow(option.title, option.rowDetail(), option.selected, enabled = option.supported) {
                controller.selectAudioTrack(option)
            }
        }
    }
}

@Composable
internal fun SubtitleSheet(controller: PlaybackController, onLoadFile: () -> Unit, onDismiss: () -> Unit) {
    val menu by controller.trackMenu.collectAsStateWithLifecycle()
    val settings by controller.subtitleSettings.collectAsStateWithLifecycle()
    val off = !settings.enabled || menu.textOff
    PlayerSheet(onDismiss) {
        SheetTitle("Subtitles")
        TrackRow("Off", "", selected = off) { controller.selectTextTrack(null) }
        for (option in menu.text) {
            TrackRow(option.title, option.rowDetail(), option.selected && settings.enabled, enabled = option.supported) {
                controller.selectTextTrack(option)
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onLoadFile)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.FileOpen, contentDescription = null, Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.width(8.dp))
            Text("Load from file…", style = MaterialTheme.typography.bodyLarge)
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SectionLabel("Size")
        val sizeIndex = SUBTITLE_SIZES.indices.minByOrNull { kotlin.math.abs(SUBTITLE_SIZES[it].second - settings.textScale) } ?: 1
        ConnectedChoice(
            labels = SUBTITLE_SIZES.map { it.first },
            selected = sizeIndex,
            onSelect = { controller.setSubtitleScale(SUBTITLE_SIZES[it].second) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { controller.setSubtitleBackground(!settings.background) }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Background box", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = settings.background, onCheckedChange = { controller.setSubtitleBackground(it) })
        }
    }
}

/** Expressive connected button group (single choice). */
@Composable
private fun ConnectedChoice(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        labels.forEachIndexed { i, label ->
            ToggleButton(
                checked = i == selected,
                onCheckedChange = { onSelect(i) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    labels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}

/** ⋮ sheet: speed, aspect, orientation, actions (screenshot, PiP) and info (plan §6.3). */
@Composable
internal fun MoreSheet(
    controller: PlaybackController,
    ui: FullPlayerState,
    host: FullPlayerHost,
    onAspect: (AspectMode) -> Unit,
    onScreenshot: () -> Unit,
    onDismiss: () -> Unit,
) {
    val speed by controller.speed.collectAsStateWithLifecycle()
    var showInfo by remember { mutableStateOf(false) }
    PlayerSheet(onDismiss) {
        // Speed: presets + a fine slider (0.25–4×).
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Speed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(formatSpeed(speed), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        ConnectedChoice(
            labels = SPEED_PRESETS.map { formatSpeed(it) },
            selected = SPEED_PRESETS.indexOfFirst { kotlin.math.abs(it - speed) < 0.001f },
            onSelect = { controller.setSpeed(SPEED_PRESETS[it]) },
        )
        // The slider tracks the finger locally and applies on every step (cheap: one param set).
        var sliderValue by remember { mutableFloatStateOf(speed) }
        LaunchedEffect(speed) { sliderValue = speed }
        Slider(
            value = sliderValue,
            onValueChange = {
                val v = (it * 20).roundToInt() / 20f // 0.05 steps
                sliderValue = v
                controller.setSpeed(v)
            },
            valueRange = MIN_SPEED..MAX_SPEED,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        SectionLabel("Aspect")
        val aspectIndex = if (ui.customZoom > 0f) -1 else ui.aspectMode.ordinal
        ConnectedChoice(
            labels = AspectMode.entries.map { it.label },
            selected = aspectIndex,
            onSelect = { onAspect(AspectMode.entries[it]) },
        )

        SectionLabel("Orientation")
        ConnectedChoice(
            labels = OrientationMode.entries.map { it.label },
            selected = host.orientationMode.ordinal,
            onSelect = { host.chooseOrientationMode(OrientationMode.entries[it]) },
        )

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onScreenshot, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Screenshot", maxLines = 1)
            }
            FilledTonalButton(
                onClick = { onDismiss(); host.enterPip() },
                enabled = host.canEnterPip,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pop out", maxLines = 1)
            }
            FilledTonalButton(onClick = { showInfo = !showInfo }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Info, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Info", maxLines = 1)
            }
        }
        if (showInfo) InfoSection(controller)
    }
}

@Composable
private fun InfoSection(controller: PlaybackController) {
    val info by produceState<MediaInfo?>(null, controller) { value = controller.mediaInfo() }
    val rows = info?.rows ?: return
    Spacer(Modifier.height(8.dp))
    SelectionContainer {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, value) in rows) {
                Row {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
