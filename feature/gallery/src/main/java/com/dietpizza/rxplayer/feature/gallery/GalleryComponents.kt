package com.dietpizza.rxplayer.feature.gallery

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
internal fun gridPadding(
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

/** One sort field in a [SortMenuButton]. */
internal class SortOption<F>(val field: F, @param:StringRes val label: Int)

/**
 * Top-bar sort action: a menu of fields (radio-style check on the current one), then the
 * direction. Picking a new field applies that field's natural direction via [onFieldSelected].
 */
@Composable
internal fun <F> SortMenuButton(
    options: List<SortOption<F>>,
    current: F,
    descending: Boolean,
    onFieldSelected: (F) -> Unit,
    onDirectionSelected: (descending: Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.gallery_sort))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (option in options) {
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        expanded = false
                        if (option.field != current) onFieldSelected(option.field)
                    },
                    trailingIcon = checkIf(option.field == current),
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gallery_sort_ascending)) },
                onClick = {
                    expanded = false
                    if (descending) onDirectionSelected(false)
                },
                trailingIcon = checkIf(!descending),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.gallery_sort_descending)) },
                onClick = {
                    expanded = false
                    if (!descending) onDirectionSelected(true)
                },
                trailingIcon = checkIf(descending),
            )
        }
    }
}

private fun checkIf(checked: Boolean): (@Composable () -> Unit)? =
    if (checked) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null
