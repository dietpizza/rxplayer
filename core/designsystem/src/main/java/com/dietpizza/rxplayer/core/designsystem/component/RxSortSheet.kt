package com.dietpizza.rxplayer.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons

/**
 * One field in an [RxSortButton] sheet. [ascending]/[descending] name the two directions for
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

/** Optional **Layout** section of an [RxSortButton] sheet: list or grid. */
class RxLayoutChoice(
    val title: String,
    val listLabel: String,
    val gridLabel: String,
    val grid: Boolean,
    val onGridChange: (grid: Boolean) -> Unit,
)

/** One chip in the [RxFieldsChoice] section. */
class RxFieldToggle(val label: String, val shown: Boolean, val onShownChange: (Boolean) -> Unit)

/** Optional collapsible **Fields** section of an [RxSortButton] sheet: which details to show. */
class RxFieldsChoice(val title: String, val fields: List<RxFieldToggle>)

/**
 * Top-bar sort action. Opens a full-height M3 Expressive modal sheet: the fields as a
 * segmented, single-select list, the direction as a connected button group named for the
 * current field, then (optionally) the list/grid [layout] and a collapsible [fields] section.
 * Choices apply immediately (the caller persists them); swipe down or tap outside to close.
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
    layout: RxLayoutChoice? = null,
    fields: RxFieldsChoice? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    // Kept while the screen lives, so the section reopens the way it was left.
    var fieldsExpanded by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(RxIcons.Sort, contentDescription = labels.button)
    }
    if (!open) return

    val currentOption = options.firstOrNull { it.field == current }
    RxSheet(title = labels.title, onDismissRequest = { open = false }) {
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
        ConnectedPair(
            first = PairItem(currentOption?.ascending ?: labels.ascending, RxIcons.ArrowUpward, checked = !descending) {
                if (descending) onSortChange(current, false)
            },
            second = PairItem(currentOption?.descending ?: labels.descending, RxIcons.ArrowDownward, checked = descending) {
                if (!descending) onSortChange(current, true)
            },
        )
        if (layout != null) {
            SectionLabel(layout.title)
            ConnectedPair(
                first = PairItem(layout.listLabel, RxIcons.FormatListBulleted, checked = !layout.grid) {
                    if (layout.grid) layout.onGridChange(false)
                },
                second = PairItem(layout.gridLabel, RxIcons.GridView, checked = layout.grid) {
                    if (!layout.grid) layout.onGridChange(true)
                },
            )
        }
        if (fields != null) {
            Spacer(Modifier.height(16.dp))
            FieldsSection(fields, expanded = fieldsExpanded, onExpandedChange = { fieldsExpanded = it })
        }
    }
}

private class PairItem(val label: String, val icon: ImageVector, val checked: Boolean, val onClick: () -> Unit)

/** Two connected single-choice toggle buttons (M3 Expressive button group). */
@Composable
private fun ConnectedPair(first: PairItem, second: PairItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        PairButton(first, leading = true, modifier = Modifier.weight(1f))
        PairButton(second, leading = false, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PairButton(item: PairItem, leading: Boolean, modifier: Modifier = Modifier) {
    ToggleButton(
        checked = item.checked,
        onCheckedChange = { item.onClick() },
        shapes = if (leading) {
            ButtonGroupDefaults.connectedLeadingButtonShapes()
        } else {
            ButtonGroupDefaults.connectedTrailingButtonShapes()
        },
        modifier = modifier
            .height(56.dp)
            .semantics { role = Role.RadioButton },
    ) {
        Icon(item.icon, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
        Text(item.label, maxLines = 1)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 12.dp),
    )
}

/** Header row that expands/collapses the field chips (instant, no animation). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldsSection(fields: RxFieldsChoice, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClickLabel = fields.title) { onExpandedChange(!expanded) }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            fields.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) RxIcons.KeyboardArrowUp else RxIcons.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (expanded) {
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            fields.fields.forEach { field ->
                FilterChip(
                    selected = field.shown,
                    onClick = { field.onShownChange(!field.shown) },
                    label = { Text(field.label) },
                    leadingIcon = if (field.shown) {
                        { Icon(RxIcons.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
                    } else {
                        null
                    },
                    modifier = Modifier.semantics { role = Role.Checkbox },
                )
            }
        }
    }
}

/** One value in an [RxChoiceSheet]. */
@Immutable
class RxChoice<T>(val value: T, val label: String)

/**
 * Full-height M3 Expressive single-choice sheet (same look as the sort sheet). Picking a
 * value calls [onSelect] and closes the sheet.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> RxChoiceSheet(
    title: String,
    choices: List<RxChoice<T>>,
    current: T,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
) {
    RxSheet(title = title, onDismissRequest = onDismissRequest) {
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

/** The shared sheet frame: full-height modal sheet, headline title, scrollable body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RxSheet(title: String, onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
            content()
        }
    }
}
