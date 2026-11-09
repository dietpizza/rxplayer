package com.dietpizza.rxplayer.feature.network

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.netfs.RemotePath
import com.dietpizza.rxplayer.core.netfs.model.MediaType
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction

@Composable
internal fun RemoteBrowserScreen(
    onBack: () -> Unit,
    onOpenMedia: (BrowserListing.Playlist) -> Unit,
    viewModel: RemoteBrowserViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message = when (effect) {
                is BrowserEffect.Failed -> context.getString(
                    if (effect.rename) R.string.browser_rename_failed else R.string.browser_delete_failed,
                    context.getString(effect.kind.titleRes()),
                )
                is BrowserEffect.RefreshFailed -> context.getString(effect.kind.titleRes())
            }
            snackbar.showSnackbar(message)
        }
    }
    BackHandler(enabled = state.canGoUp) { viewModel.up() }

    BrowserContent(
        state = state,
        snackbar = snackbar,
        onBack = onBack,
        onOpenFolder = viewModel::open,
        onOpenFile = { entry ->
            val protocol = state.protocol ?: return@BrowserContent
            val playlist = BrowserListing.playlistFor(state.rows, entry, protocol, viewModel.serverId)
            if (playlist != null) {
                onOpenMedia(playlist)
            } else {
                // Not media: say so instead of doing nothing.
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(context.getString(R.string.browser_cant_open))
                }
            }
        },
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onShowAll = viewModel::setShowAll,
        onRename = viewModel::rename,
        onDelete = viewModel::delete,
    )
}

@Composable
internal fun BrowserContent(
    state: BrowserUiState,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (RemoteEntry) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onShowAll: (Boolean) -> Unit,
    onRename: (RemoteEntry, String) -> Unit,
    onDelete: (RemoteEntry) -> Unit,
) {
    var renaming by remember { mutableStateOf<RemoteEntry?>(null) }
    var deleting by remember { mutableStateOf<RemoteEntry?>(null) }
    val background = MaterialTheme.colorScheme.surfaceContainer
    // One scroll position per folder, so going back up lands where you were.
    val listStates = remember { HashMap<String, LazyListState>() }

    Scaffold(
        containerColor = background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.serverName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.browser_back))
                        }
                    },
                    actions = { OverflowMenu(state.showAll, onShowAll, onRefresh) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
                )
                Breadcrumbs(state.crumbs, onOpenFolder)
            }
        },
    ) { innerPadding ->
        val pullState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            state = pullState,
            enabled = state.listing is Listing.Loaded,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding()),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = state.refreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            val bottomOnly = PaddingValues(bottom = innerPadding.calculateBottomPadding())
            when (val listing = state.listing) {
                Listing.Loading -> DelayedLoadingIndicator()
                is Listing.Failed -> RxEmptyState(
                    icon = Icons.Rounded.CloudOff,
                    title = stringResource(if (state.serverMissing) R.string.browser_server_missing else listing.kind.titleRes()),
                    body = listing.detail,
                    modifier = Modifier.padding(bottomOnly),
                    action = if (state.serverMissing) {
                        null
                    } else {
                        { FilledTonalButton(onClick = onRetry) { Text(stringResource(R.string.browser_retry)) } }
                    },
                )
                is Listing.Loaded -> {
                    val rows = state.rows
                    if (rows.isEmpty()) {
                        RxEmptyState(
                            icon = Icons.Rounded.FolderOpen,
                            title = stringResource(if (state.hidesFiles) R.string.browser_no_media_title else R.string.browser_empty_title),
                            body = stringResource(if (state.hidesFiles) R.string.browser_no_media_body else R.string.browser_empty_body),
                            modifier = Modifier.padding(bottomOnly),
                            action = if (state.hidesFiles) {
                                { FilledTonalButton(onClick = { onShowAll(true) }) { Text(stringResource(R.string.browser_show_all)) } }
                            } else {
                                null
                            },
                        )
                    } else {
                        EntryList(
                            rows = rows,
                            state = listStates.getOrPut(state.path) { LazyListState() },
                            contentPadding = listPadding(bottomOnly),
                            onOpen = { if (it.isDirectory) onOpenFolder(it.path) else onOpenFile(it) },
                            onRename = { renaming = it },
                            onDelete = { deleting = it },
                        )
                    }
                }
            }
        }
    }

    renaming?.let { entry ->
        RenameDialog(
            entry = entry,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                renaming = null
                onRename(entry, name)
            },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.browser_delete_title, entry.name)) },
            text = {
                Text(stringResource(if (entry.isDirectory) R.string.browser_delete_folder_body else R.string.browser_delete_file_body))
            },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    onDelete(entry)
                }) { Text(stringResource(R.string.browser_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.browser_cancel)) }
            },
        )
    }
}

@Composable
private fun OverflowMenu(showAll: Boolean, onShowAll: (Boolean) -> Unit, onRefresh: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.browser_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_show_all)) },
                trailingIcon = { Checkbox(checked = showAll, onCheckedChange = null) },
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(showAll)
                },
                onClick = {
                    open = false
                    onShowAll(!showAll)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_refresh)) },
                onClick = {
                    open = false
                    onRefresh()
                },
            )
        }
    }
}

@Composable
private fun Breadcrumbs(crumbs: List<RemotePath.Crumb>, onOpen: (String) -> Unit) {
    val listState = rememberLazyListState()
    // Keep the current folder in view; jump, don't animate.
    LaunchedEffect(crumbs.size) { listState.scrollToItem(crumbs.lastIndex.coerceAtLeast(0)) }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
    ) {
        itemsIndexed(crumbs, key = { _, c -> c.path }) { index, crumb ->
            val last = index == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                TextButton(onClick = { onOpen(crumb.path) }, enabled = !last) {
                    Text(
                        crumb.label,
                        maxLines = 1,
                        fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryList(
    rows: List<RemoteEntry>,
    state: LazyListState,
    contentPadding: PaddingValues,
    onOpen: (RemoteEntry) -> Unit,
    onRename: (RemoteEntry) -> Unit,
    onDelete: (RemoteEntry) -> Unit,
) {
    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(rows, key = { _, e -> e.path }, contentType = { _, e -> e.isDirectory }) { index, entry ->
            EntryItem(
                entry = entry,
                index = index,
                count = rows.size,
                onClick = { onOpen(entry) },
                onRename = { onRename(entry) },
                onDelete = { onDelete(entry) },
            )
        }
    }
}

@Composable
private fun EntryItem(
    entry: RemoteEntry,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val type = entry.type
    val renameLabel = stringResource(R.string.browser_rename)
    val deleteLabel = stringResource(R.string.browser_delete)
    Box {
        SegmentedListItem(
            onClick = onClick,
            onLongClick = { menu = true },
            // The rename/delete menu is long-press only; expose it as TalkBack actions too.
            modifier = Modifier.semantics {
                customActions = listOf(
                    CustomAccessibilityAction(renameLabel) { onRename(); true },
                    CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                )
            },
            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            verticalAlignment = Alignment.CenterVertically,
            leadingContent = {
                val folder = type == MediaType.FOLDER
                LeadingIcon(
                    icon = type.icon(),
                    container = if (folder) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = if (folder) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            supportingContent = {
                val meta = listOfNotNull(
                    if (entry.isDirectory) null else formatSize(context, entry.size),
                    formatDate(context, entry.modifiedMs),
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, maxLines = 1)
            },
        ) {
            Text(entry.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_rename)) },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                onClick = {
                    menu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.browser_delete)) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun RenameDialog(entry: RemoteEntry, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember(entry.path) {
        mutableStateOf(TextFieldValue(entry.name, TextRange(0, BrowserListing.baseNameEnd(entry.name, entry.isDirectory))))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val name = value.text.trim()
    val valid = RemotePath.isValidName(name) && name != entry.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.browser_rename_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(stringResource(R.string.browser_new_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = valid) { Text(stringResource(R.string.browser_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.browser_cancel)) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BrowserPreview() {
    val entries = listOf(
        RemoteEntry("Season 1", "/Media/TV/Season 1", true, -1, 1_793_613_600_000),
        RemoteEntry("Episode 10.mkv", "/Media/TV/Episode 10.mkv", false, 1_400_000_000, 1_793_613_600_000),
        RemoteEntry("Episode 2.mkv", "/Media/TV/Episode 2.mkv", false, 1_300_000_000, 1_793_613_600_000),
        RemoteEntry("poster.jpg", "/Media/TV/poster.jpg", false, 240_000, 1_793_613_600_000),
    )
    RxTheme {
        BrowserContent(
            state = BrowserUiState(
                serverName = "NAS",
                protocol = NetProtocol.SMB,
                root = "/Media",
                path = "/Media/TV",
                listing = Listing.Loaded(entries),
            ),
            snackbar = remember { SnackbarHostState() },
            onBack = {}, onOpenFolder = {}, onOpenFile = {}, onRefresh = {}, onRetry = {},
            onShowAll = {}, onRename = { _, _ -> }, onDelete = {},
        )
    }
}
