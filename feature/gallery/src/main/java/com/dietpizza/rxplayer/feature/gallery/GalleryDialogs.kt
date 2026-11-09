package com.dietpizza.rxplayer.feature.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

// File-op dialogs, matching the Videos tab's (feature/videos LibraryDialogs.kt) so both libraries
// look and behave the same. Features can't depend on each other, hence the small copy.

/** Move/copy destination row. */
@Immutable
data class FolderChoice(val path: String, val name: String)

/** "Add to album" row (a custom album). */
@Immutable
data class AlbumChoice(val id: Long, val name: String, val count: String)

/**
 * Single text field dialog (rename, new folder, new album). When [selectBaseName] is true the
 * initial selection covers the name without its extension, like the system Files app.
 */
@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
    selectBaseName: Boolean = false,
) {
    var value by remember {
        val dot = initial.lastIndexOf('.')
        val end = if (selectBaseName && dot > 0) dot else initial.length
        mutableStateOf(TextFieldValue(initial, TextRange(0, end)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = value.text.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(value.text.trim()) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text.trim()) }, enabled = valid) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
internal fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                } else {
                    ButtonDefaults.textButtonColors()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** A choice from a list, with a "new …" row on top. */
@Composable
private fun <T> PickerDialog(
    title: String,
    newLabel: String,
    newIcon: ImageVector,
    itemIcon: ImageVector,
    items: List<T>,
    key: (T) -> Any,
    label: (T) -> String,
    supporting: (T) -> String?,
    onNew: () -> Unit,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item(key = "new", contentType = "new") {
                    PickerRow(icon = newIcon, title = newLabel, supporting = null, onClick = onNew)
                }
                items(items, key = key, contentType = { "item" }) { item ->
                    PickerRow(icon = itemIcon, title = label(item), supporting = supporting(item), onClick = { onPick(item) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun PickerRow(icon: ImageVector, title: String, supporting: String?, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Move/copy destination: the gallery's folder albums plus "New folder". */
@Composable
internal fun FolderPickerDialog(
    title: String,
    folders: List<FolderChoice>,
    onPick: (FolderChoice) -> Unit,
    onNewFolder: () -> Unit,
    onDismiss: () -> Unit,
) = PickerDialog(
    title = title,
    newLabel = stringResource(R.string.folder_new),
    newIcon = Icons.Rounded.CreateNewFolder,
    itemIcon = Icons.Rounded.Folder,
    items = folders,
    key = { it.path },
    label = { it.name },
    supporting = { it.path },
    onNew = onNewFolder,
    onPick = onPick,
    onDismiss = onDismiss,
)

/** "Add to album": existing custom albums plus "New album". */
@Composable
internal fun AlbumPickerDialog(
    albums: List<AlbumChoice>,
    onPick: (AlbumChoice) -> Unit,
    onNewAlbum: () -> Unit,
    onDismiss: () -> Unit,
) = PickerDialog(
    title = stringResource(R.string.album_add_to),
    newLabel = stringResource(R.string.gallery_new_album),
    newIcon = Icons.Rounded.Add,
    itemIcon = Icons.Rounded.PhotoAlbum,
    items = albums,
    key = { it.id },
    label = { it.name },
    supporting = { it.count },
    onNew = onNewAlbum,
    onPick = onPick,
    onDismiss = onDismiss,
)

/** Copy/move progress. Not dismissable by tapping outside; [onCancel] null hides Cancel. */
@Composable
internal fun ProgressDialog(title: String, fraction: Float, onCancel: (() -> Unit)?) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(title) },
        text = { LinearWavyProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {},
        dismissButton = onCancel?.let { { TextButton(onClick = it) { Text(stringResource(R.string.action_cancel)) } } },
    )
}

/** Which dialog the album screen is showing; saved across rotation. */
internal enum class AlbumDialog {
    NONE, RENAME, DELETE, MOVE, COPY, NEW_FOLDER_MOVE, NEW_FOLDER_COPY, ADD_TO_ALBUM, NEW_ALBUM,
    RENAME_ALBUM, DELETE_ALBUM,
}

@Composable
internal fun rememberAlbumDialogState() = rememberSaveable { mutableStateOf(AlbumDialog.NONE) }
