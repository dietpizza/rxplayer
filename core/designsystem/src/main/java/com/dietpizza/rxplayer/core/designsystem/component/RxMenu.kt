package com.dietpizza.rxplayer.core.designsystem.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons

/** One row of an [RxMenu]. [selected] non-null makes it a selectable (radio-style) item. */
@Immutable
class RxMenuItem(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val selected: Boolean? = null,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * M3 Expressive dropdown menu: items are laid out in rounded, gapped groups (vertical menu
 * spec), selected items get the selected shape + tonal container and a check. The menu closes
 * itself before running an item's action. Place it in a `Box` with its anchor.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RxMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<RxMenuItem>>,
    modifier: Modifier = Modifier,
) {
    val visible = groups.filter { it.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        visible.forEachIndexed { g, group ->
            if (g > 0) Spacer(Modifier.height(MenuGroupGap))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(g, visible.size)) {
                group.forEachIndexed { i, item ->
                    val color = if (item.destructive) MaterialTheme.colorScheme.error else null
                    val select = item.selected
                    val shapes = MenuDefaults.itemShape(i, group.size)
                    val text: @Composable () -> Unit = {
                        Text(item.label, color = color ?: MaterialTheme.colorScheme.onSurface)
                    }
                    val leading: (@Composable () -> Unit)? = item.icon?.let { icon ->
                        { Icon(icon, contentDescription = null, tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    val action = {
                        onDismissRequest()
                        item.onClick()
                    }
                    if (select != null) {
                        SelectableDropdownMenuItem(
                            selected = select,
                            onClick = action,
                            text = text,
                            shapes = shapes,
                            enabled = item.enabled,
                            leadingIcon = leading,
                            selectedLeadingIcon = { Icon(RxIcons.Check, contentDescription = null) },
                            modifier = Modifier.semantics { selected = select },
                        )
                    } else {
                        DropdownMenuItem(
                            onClick = action,
                            text = text,
                            shape = shapes.shape,
                            enabled = item.enabled,
                            leadingIcon = leading,
                        )
                    }
                }
            }
        }
    }
}

private val MenuGroupGap = 2.dp
