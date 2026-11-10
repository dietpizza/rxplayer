package com.dietpizza.rxplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons

/**
 * One field in an [RxSortButton] dialog. [ascending]/[descending] name the two directions for
 * this field ("A–Z"/"Z–A", "Oldest"/"Newest"…); null falls back to [RxSortLabels].
 */
@Immutable
class RxSortOption<F>(
    val field: F,
    val label: String,
    val icon: ImageVector,
    val ascending: String? = null,
    val descending: String? = null,
)

/** Labels for [RxSortButton]; resolved by the caller (designsystem has no strings). */
@Immutable
class RxSortLabels(
    val title: String,
    val ascending: String,
    val descending: String,
    val button: String,
)

/**
 * Top-bar sort action. Opens a centred M3 Expressive dialog: the fields as rounded icon tiles
 * (the current one filled), then the direction as a two-part segmented button named for that
 * field. Choices apply immediately (the caller persists them); tap outside or back to close.
 * Picking a new field applies [naturalDescending] for it.
 */
@Composable
fun <F> RxSortButton(
    options: List<RxSortOption<F>>,
    current: F,
    descending: Boolean,
    labels: RxSortLabels,
    naturalDescending: (F) -> Boolean,
    onSortChange: (field: F, descending: Boolean) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(RxIcons.Sort, contentDescription = labels.button)
    }
    if (!open) return

    val currentOption = options.firstOrNull { it.field == current }
    RxDialog(title = labels.button, onDismissRequest = { open = false }) {
        SectionLabel(labels.title)
        Row(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            options.forEach { option ->
                val selected = option.field == current
                OptionTile(
                    icon = option.icon,
                    label = option.label,
                    selected = selected,
                    onClick = { if (!selected) onSortChange(option.field, naturalDescending(option.field)) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !descending,
                onClick = { if (descending) onSortChange(current, false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(RxIcons.KeyboardArrowUp, contentDescription = null, Modifier.size(18.dp)) },
            ) { Text(currentOption?.ascending ?: labels.ascending, maxLines = 1) }
            SegmentedButton(
                selected = descending,
                onClick = { if (!descending) onSortChange(current, true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(RxIcons.KeyboardArrowDown, contentDescription = null, Modifier.size(18.dp)) },
            ) { Text(currentOption?.descending ?: labels.descending, maxLines = 1) }
        }
    }
}

/** One value in an [RxChoiceSheet]. */
@Immutable
class RxChoice<T>(val value: T, val label: String)

/**
 * Single-choice dialog in the same style as the sort dialog: the values as chips. Picking one
 * calls [onSelect] and closes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> RxChoiceSheet(
    title: String,
    choices: List<RxChoice<T>>,
    current: T,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    RxDialog(title = title, onDismissRequest = onDismissRequest) {
        FlowRow(
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            choices.forEach { choice ->
                val selected = choice.value == current
                FilterChip(
                    selected = selected,
                    onClick = {
                        onDismissRequest()
                        onSelect(choice.value)
                    },
                    label = { Text(choice.label) },
                    modifier = Modifier.semantics {
                        role = Role.RadioButton
                        this.selected = selected
                    },
                )
            }
        }
    }
}

/** The shared dialog frame: large rounded surface, headline title, scrollable body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RxDialog(title: String, onDismissRequest: () -> Unit, content: @Composable () -> Unit) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            modifier = Modifier.widthIn(max = 420.dp),
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp),
            ) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(20.dp))
                content()
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/** Rounded-square icon tile with its label underneath; filled with primaryContainer when selected. */
@Composable
private fun OptionTile(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .background(
                    if (selected) colors.primaryContainer else colors.surfaceContainerHighest,
                    MaterialTheme.shapes.large,
                ),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.widthIn(max = 72.dp),
        )
    }
}
