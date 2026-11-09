package com.dietpizza.rxplayer.feature.search

import com.dietpizza.rxplayer.core.designsystem.component.RxChoice
import com.dietpizza.rxplayer.core.designsystem.component.RxChoiceSheet
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.ui.thumbnail.MediaThumbnail
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected

@Composable
internal fun SearchScreen(
    onBack: () -> Unit,
    onOpen: (keys: List<MediaKey>, index: Int, allVideos: Boolean) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        query = query,
        filters = filters,
        uiState = uiState,
        onQueryChange = viewModel::setQuery,
        onFiltersChange = viewModel::setFilters,
        onBack = onBack,
        onOpen = onOpen,
    )
}

@Composable
internal fun SearchContent(
    query: String,
    filters: SearchFilters,
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onFiltersChange: (SearchFilters) -> Unit,
    onBack: () -> Unit,
    onOpen: (keys: List<MediaKey>, index: Int, allVideos: Boolean) -> Unit,
) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    // Focus once per screen (not again after rotation / returning from the viewer).
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focused) {
            focus.requestFocus()
            focused = true
        }
    }
    var pickDates by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = background,
        topBar = {
            Column(Modifier.background(background)) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(RxIcons.ArrowBack, contentDescription = stringResource(R.string.search_back))
                        }
                    },
                    title = {
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1) },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focus),
                        )
                    },
                    actions = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                onQueryChange("")
                                focus.requestFocus()
                                keyboard?.show()
                            }) {
                                Icon(RxIcons.Close, contentDescription = stringResource(R.string.search_clear))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background),
                )
                FilterRow(filters, onFiltersChange, onPickDates = { pickDates = true })
            }
        },
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .consumeWindowInsets(innerPadding)
            .imePadding()
        when (uiState) {
            SearchUiState.Loading -> Box(modifier)
            SearchUiState.Idle -> RxEmptyState(
                icon = RxIcons.Search,
                title = stringResource(R.string.search_idle_title),
                body = stringResource(R.string.search_idle_body),
                modifier = modifier,
            )
            is SearchUiState.Results -> if (uiState.tiles.isEmpty()) {
                RxEmptyState(
                    icon = RxIcons.SearchOff,
                    title = stringResource(R.string.search_no_results_title),
                    body = stringResource(R.string.search_no_results_body),
                    modifier = modifier,
                )
            } else {
                ResultsGrid(
                    results = uiState,
                    onOpen = { index -> onOpen(uiState.keys, index, uiState.allVideos) },
                    onScroll = { keyboard?.hide() },
                    modifier = modifier,
                )
            }
        }
    }

    if (pickDates) {
        DateRangeDialog(
            filters = filters,
            onConfirm = { start, end ->
                onFiltersChange(filters.copy(date = DateFilter.CUSTOM, customStartDay = start, customEndDay = end))
                pickDates = false
            },
            onDismiss = { pickDates = false },
        )
    }
}

// ---- Filters -----------------------------------------------------------------------------------

private class Choice<T>(val value: T, @param:StringRes val label: Int)

private val TypeChoices = listOf(
    Choice(TypeFilter.ALL, R.string.filter_type_all),
    Choice(TypeFilter.PHOTOS, R.string.filter_type_photos),
    Choice(TypeFilter.VIDEOS, R.string.filter_type_videos),
)

private val DateChoices = listOf(
    Choice(DateFilter.ANY, R.string.filter_date_any),
    Choice(DateFilter.LAST_7_DAYS, R.string.filter_date_7),
    Choice(DateFilter.LAST_30_DAYS, R.string.filter_date_30),
    Choice(DateFilter.THIS_YEAR, R.string.filter_date_year),
    Choice(DateFilter.CUSTOM, R.string.filter_date_custom),
)

private val SizeChoices = listOf(
    Choice(SizeFilter.ANY, R.string.filter_size_any),
    Choice(SizeFilter.UNDER_10_MB, R.string.filter_size_under_10),
    Choice(SizeFilter.MB_10_TO_100, R.string.filter_size_10_100),
    Choice(SizeFilter.MB_100_TO_1000, R.string.filter_size_100_1000),
    Choice(SizeFilter.OVER_1_GB, R.string.filter_size_over_1g),
)

private val DurationChoices = listOf(
    Choice(DurationFilter.ANY, R.string.filter_duration_any),
    Choice(DurationFilter.UNDER_1_MIN, R.string.filter_duration_under_1),
    Choice(DurationFilter.MIN_1_TO_10, R.string.filter_duration_1_10),
    Choice(DurationFilter.MIN_10_TO_60, R.string.filter_duration_10_60),
    Choice(DurationFilter.OVER_1_HOUR, R.string.filter_duration_over_60),
)

@Composable
private fun FilterRow(filters: SearchFilters, onChange: (SearchFilters) -> Unit, onPickDates: () -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        item(key = "type") {
            FilterMenuChip(R.string.filter_type, TypeChoices, filters.type, TypeFilter.ALL) {
                // Photos only rules out a duration bound.
                onChange(filters.copy(type = it, duration = if (it == TypeFilter.PHOTOS) DurationFilter.ANY else filters.duration))
            }
        }
        item(key = "date") {
            val customLabel = if (filters.date == DateFilter.CUSTOM) customRangeLabel(filters) else null
            FilterMenuChip(R.string.filter_date, DateChoices, filters.date, DateFilter.ANY, labelOverride = customLabel) {
                if (it == DateFilter.CUSTOM) onPickDates() else onChange(filters.copy(date = it))
            }
        }
        item(key = "size") {
            FilterMenuChip(R.string.filter_size, SizeChoices, filters.size, SizeFilter.ANY) {
                onChange(filters.copy(size = it))
            }
        }
        if (filters.type != TypeFilter.PHOTOS) {
            item(key = "duration") {
                FilterMenuChip(R.string.filter_duration, DurationChoices, filters.duration, DurationFilter.ANY) {
                    onChange(filters.copy(duration = it))
                }
            }
        }
        if (filters.isActive) {
            item(key = "reset") {
                TextButton(onClick = { onChange(SearchFilters()) }) { Text(stringResource(R.string.filter_reset)) }
            }
        }
    }
}

@Composable
private fun <T> FilterMenuChip(
    @StringRes name: Int,
    choices: List<Choice<T>>,
    current: T,
    default: T,
    labelOverride: String? = null,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = current != default
    Box {
        FilterChip(
            selected = selected,
            onClick = { expanded = true },
            label = {
                Text(
                    labelOverride ?: if (selected) stringResource(choices.first { it.value == current }.label) else stringResource(name),
                    maxLines = 1,
                )
            },
            trailingIcon = { Icon(RxIcons.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
        if (expanded) {
            RxChoiceSheet(
                title = stringResource(name),
                choices = choices.map { RxChoice(it.value, stringResource(it.label)) },
                current = current,
                onSelect = onSelect,
                onDismissRequest = { expanded = false },
            )
        }
    }
}

private fun customRangeLabel(filters: SearchFilters): String? {
    val start = filters.customStartDay ?: return null
    val end = filters.customEndDay ?: start
    val format = java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
    val s = java.time.LocalDate.ofEpochDay(start)
    val e = java.time.LocalDate.ofEpochDay(end)
    return if (start == end) format.format(s) else format.format(s) + " – " + format.format(e)
}

@Composable
private fun DateRangeDialog(
    filters: SearchFilters,
    onConfirm: (startDay: Long, endDay: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // The picker works in UTC-midnight millis; our filter in local epoch days. They map 1:1.
    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = filters.customStartDay?.let { it * DAY_MS },
        initialSelectedEndDateMillis = filters.customEndDay?.let { it * DAY_MS },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedStartDateMillis != null,
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: start
                    onConfirm(Math.floorDiv(start, DAY_MS), Math.floorDiv(end, DAY_MS))
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DateRangePicker(
            state = state,
            title = { Text(stringResource(R.string.filter_date_pick), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
            modifier = Modifier.weight(1f),
        )
    }
}

private const val DAY_MS = 86_400_000L

// ---- Results -----------------------------------------------------------------------------------

private val TileGap = 2.dp
private val ChipShape = RoundedCornerShape(4.dp)
private val ChipScrim = Color.Black.copy(alpha = 0.55f)

@Composable
private fun ResultsGrid(
    results: SearchUiState.Results,
    onOpen: (Int) -> Unit,
    onScroll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyGridState()
    LaunchedEffect(state.isScrollInProgress) { if (state.isScrollInProgress) onScroll() }
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val requestWidth = (screenWidth / 3).dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MinCell),
        state = state,
        horizontalArrangement = Arrangement.spacedBy(TileGap),
        verticalArrangement = Arrangement.spacedBy(TileGap),
        modifier = modifier,
    ) {
        item(key = "count", span = { GridItemSpan(maxLineSpan) }, contentType = "count") {
            Text(
                text = pluralStringResource(R.plurals.search_result_count, results.tiles.size, results.tiles.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(results.tiles.size, key = { results.tiles[it].key }, contentType = { "result" }) { index ->
            ResultTileView(results.tiles[index], requestWidth) { onOpen(index) }
        }
    }
}

private val MinCell = 104.dp

@Composable
private fun ResultTileView(tile: ResultTile, requestWidth: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        MediaThumbnail(
            contentUri = tile.contentUri,
            requestWidth = requestWidth,
            contentDescription = tile.name,
            modifier = Modifier.fillMaxSize(),
        )
        if (tile.duration != null) {
            Text(
                text = tile.duration,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(ChipScrim, ChipShape)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}
