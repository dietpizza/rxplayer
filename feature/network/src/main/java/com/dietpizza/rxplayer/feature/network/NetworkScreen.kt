package com.dietpizza.rxplayer.feature.network

import com.dietpizza.rxplayer.core.designsystem.component.RxMenu
import com.dietpizza.rxplayer.core.designsystem.component.RxMenuItem
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.dietpizza.rxplayer.core.ui.RxIntents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.component.RxEmptyState
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol

/**
 * Network tab root: saved servers (plan §4 #9). Tap opens the remote browser; long-press or ⋮ for
 * edit/delete. Add/edit is [ServerActivity].
 *
 * Insets: the navigation suite consumes the side it occupies (bottom bar / start rail), so the
 * Scaffold's default content insets only add what is still unconsumed (status bar, cutout).
 * [contentPadding] is extra padding the shell may add on top of that.
 */
@Composable
fun NetworkScreen(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val viewModel: NetworkViewModel = hiltViewModel()
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    val context = LocalContext.current
    NetworkContent(
        rows = rows,
        onOpenSettings = onOpenSettings,
        onAdd = { context.startActivity(ServerActivity.intent(context)) },
        onOpen = { context.startActivity(RemoteBrowserActivity.intent(context, it.id, it.name)) },
        onEdit = { context.startActivity(ServerActivity.intent(context, it.id)) },
        onDelete = { viewModel.delete(it.id) },
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
internal fun NetworkContent(
    rows: List<ServerRow>?,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (ServerRow) -> Unit,
    onEdit: (ServerRow) -> Unit,
    onDelete: (ServerRow) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.network_title)) },
                actions = {
                    IconButton(onClick = onAdd) {
                        Icon(RxIcons.Add, contentDescription = stringResource(R.string.network_add_server))
                    }
                    val context = LocalContext.current
                    IconButton(onClick = { context.startActivity(RxIntents.search(context)) }) {
                        Icon(RxIcons.Search, contentDescription = stringResource(R.string.network_search))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(RxIcons.Settings, contentDescription = stringResource(R.string.network_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                ),
            )
        },
    ) { innerPadding ->
        when {
            rows == null -> Unit // Room answers within a frame or two; no indicator flash.
            rows.isEmpty() -> RxEmptyState(
                icon = RxIcons.Lan,
                title = stringResource(R.string.network_empty_title),
                body = stringResource(R.string.network_empty_body),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(contentPadding),
                action = {
                    FilledTonalButton(onClick = onAdd) {
                        Icon(RxIcons.Add, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.network_add_server))
                    }
                },
            )
            else -> LazyColumn(
                contentPadding = listPadding(innerPadding, contentPadding),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(rows, key = { _, r -> r.id }, contentType = { _, _ -> "server" }) { index, row ->
                    ServerListItem(
                        row = row,
                        index = index,
                        count = rows.size,
                        onClick = { onOpen(row) },
                        onEdit = { onEdit(row) },
                        onDelete = { pendingDelete = row.id },
                    )
                }
            }
        }
    }

    val deleting = pendingDelete?.let { id -> rows?.firstOrNull { it.id == id } }
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.network_delete_server_title, deleting.name)) },
            text = { Text(stringResource(R.string.network_delete_server_body)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleting)
                    pendingDelete = null
                }) { Text(stringResource(R.string.network_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.network_cancel)) }
            },
        )
    }
}

@Composable
private fun ServerListItem(
    row: ServerRow,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SegmentedListItem(
        onClick = onClick,
        onLongClick = { menu = true },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { ProtocolIcon(row.protocol) },
        supportingContent = {
            Column {
                Text(
                    "${row.protocol.label} · ${row.address}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (row.lastUsedAt > 0) formatRelative(row.lastUsedAt) else stringResource(R.string.network_never_used),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(RxIcons.MoreVert, contentDescription = stringResource(R.string.network_more))
                }
                RxMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    groups = listOf(
                        listOf(RxMenuItem(stringResource(R.string.network_edit), onEdit, RxIcons.Edit)),
                        listOf(RxMenuItem(stringResource(R.string.network_delete), onDelete, RxIcons.Delete, destructive = true)),
                    ),
                )
            }
        },
    ) {
        Text(row.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkScreenPreview() {
    RxTheme {
        NetworkContent(
            rows = listOf(
                ServerRow(1, "NAS", NetProtocol.SMB, "nas.local/Media", System.currentTimeMillis() - 3_600_000),
                ServerRow(2, "Seedbox", NetProtocol.SFTP, "seed.example.com:2222/home/me", 0),
                ServerRow(3, "Nextcloud", NetProtocol.WEBDAV, "cloud.example.com/remote.php/dav", 0),
            ),
            onOpenSettings = {}, onAdd = {}, onOpen = {}, onEdit = {}, onDelete = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NetworkScreenEmptyPreview() {
    RxTheme {
        NetworkContent(rows = emptyList(), onOpenSettings = {}, onAdd = {}, onOpen = {}, onEdit = {}, onDelete = {})
    }
}
