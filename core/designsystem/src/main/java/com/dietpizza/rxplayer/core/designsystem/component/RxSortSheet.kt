package com.dietpizza.rxplayer.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons

/** One field in an [RxSortButton] sheet. */
@Immutable
class RxSortOption<F>(val field: F, val label: String, val icon: ImageVector)

/** Labels for [RxSortButton]; resolved by the caller (designsystem has no strings). */
@Immutable
class RxSortLabels(
    val title: String,
    val ascending: String,
    val descending: String,
    val button: String,
)

/**
 * Top-bar sort action. Opens a full-height M3 Expressive modal sheet: the fields as a
 * segmented, single-select list and the direction as a connected button group. Choices
 * apply immediately (the caller persists them); swipe down or tap outside to close.
 * Picking a new field applies [naturalDescending] for it, like the rest of the app.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { open = false },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                labels.title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 20.dp),
            )
            Column(
                Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                options.forEachIndexed { index, option ->
                    val selected = option.field == current
                    SegmentedListItem(
                        selected = selected,
                        onClick = {
                            if (!selected) onSortChange(option.field, naturalDescending(option.field))
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = options.size),
                        verticalAlignment = Alignment.CenterVertically,
                        leadingContent = { Icon(option.icon, contentDescription = null) },
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                    ) {
                        Text(option.label)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                DirectionButton(
                    label = labels.ascending,
                    icon = RxIcons.ArrowUpward,
                    checked = !descending,
                    leading = true,
                    onClick = { if (descending) onSortChange(current, false) },
                    modifier = Modifier.weight(1f),
                )
                DirectionButton(
                    label = labels.descending,
                    icon = RxIcons.ArrowDownward,
                    checked = descending,
                    leading = false,
                    onClick = { if (!descending) onSortChange(current, true) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DirectionButton(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    leading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = if (leading) {
            ButtonGroupDefaults.connectedLeadingButtonShapes()
        } else {
            ButtonGroupDefaults.connectedTrailingButtonShapes()
        },
        modifier = modifier
            .height(56.dp)
            .semantics { role = Role.RadioButton },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
    }
}

/** One value in an [RxChoiceSheet]. */
@Immutable
class RxChoice<T>(val value: T, val label: String)

/**
 * Full-height M3 Expressive single-choice sheet (same look as the sort sheet). Picking a
 * value calls [onSelect] and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> RxChoiceSheet(
    title: String,
    choices: List<RxChoice<T>>,
    current: T,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 20.dp),
            )
            Column(
                Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                choices.forEachIndexed { index, choice ->
                    val selected = choice.value == current
                    SegmentedListItem(
                        selected = selected,
                        onClick = {
                            onDismissRequest()
                            onSelect(choice.value)
                        },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = choices.size),
                        verticalAlignment = Alignment.CenterVertically,
                        trailingContent = { RadioButton(selected = selected, onClick = null) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                    ) {
                        Text(choice.label)
                    }
                }
            }
        }
    }
}
