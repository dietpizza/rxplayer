package com.dietpizza.rxplayer.feature.gallery

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.VirtualAlbum
import com.dietpizza.rxplayer.core.ui.RxActivity
import com.dietpizza.rxplayer.core.ui.RxIntents
import dagger.hilt.android.AndroidEntryPoint

/**
 * One album's media grid (plan §4 #3). Its own Activity, so open/close use the system transition.
 * Opened for a folder ([EXTRA_ALBUM_PATH]), a built-in album ([EXTRA_VIRTUAL_ALBUM]), Favourites
 * ([EXTRA_FAVOURITES]) or a custom album ([EXTRA_CUSTOM_ALBUM_ID]).
 */
@AndroidEntryPoint
class AlbumActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRxContent {
            AlbumScreen(
                onBack = ::finish,
                onOpenItem = { keys, index -> startActivity(RxIntents.viewer(this, keys, index)) },
                enabledSelectionActions = EnabledSelectionActions,
                onSelectionAction = ::onSelectionAction,
            )
        }
    }

    /**
     * Selection-bar actions the Activity handles itself (share needs the chooser). Everything else
     * (favourite, albums, file ops) is handled by [AlbumScreen] with its dialogs. Return true to
     * clear the selection.
     */
    private fun onSelectionAction(action: SelectionAction, items: List<MediaCell>): Boolean {
        if (action == SelectionAction.SHARE) share(items)
        return false
    }

    private fun share(items: List<MediaCell>) {
        if (items.isEmpty()) return
        val uris = ArrayList<Uri>(items.size)
        for (item in items) uris += Uri.parse(item.contentUri)
        val type = shareMimeType(items.map { it.mimeType })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.setType(type)
        // ClipData carries the read grant for every URI through the chooser to the target.
        val clip = ClipData.newRawUri(null, uris[0])
        for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
        send.clipData = clip
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, null))
    }

    companion object {
        /** Absolute folder path. Required unless [EXTRA_VIRTUAL_ALBUM] is set. */
        const val EXTRA_ALBUM_PATH = "rx.extra.ALBUM_PATH"

        /** Display name, so the app bar has a title on the first frame (optional). */
        const val EXTRA_ALBUM_NAME = "rx.extra.ALBUM_NAME"

        /** [VirtualAlbum.id] for built-in albums (optional). */
        const val EXTRA_VIRTUAL_ALBUM = "rx.extra.VIRTUAL_ALBUM"

        /** Opens Favourites (a DB-only album over every favourite photo/video). */
        const val EXTRA_FAVOURITES = "rx.extra.FAVOURITES"

        /** Custom album id (a DB-only album, decision 12). */
        const val EXTRA_CUSTOM_ALBUM_ID = "rx.extra.CUSTOM_ALBUM_ID"

        /** Actions with a real implementation; the others show disabled. */
        val EnabledSelectionActions: Set<SelectionAction> = SelectionAction.entries.toSet()

        fun intent(context: Context, albumPath: String, albumName: String?, virtual: VirtualAlbum? = null): Intent =
            Intent(context, AlbumActivity::class.java)
                .putExtra(EXTRA_ALBUM_PATH, albumPath)
                .putExtra(EXTRA_ALBUM_NAME, albumName)
                .putExtra(EXTRA_VIRTUAL_ALBUM, virtual?.id)

        fun favouritesIntent(context: Context, title: String?): Intent =
            Intent(context, AlbumActivity::class.java)
                .putExtra(EXTRA_FAVOURITES, true)
                .putExtra(EXTRA_ALBUM_NAME, title)

        fun customAlbumIntent(context: Context, albumId: Long, albumName: String?): Intent =
            Intent(context, AlbumActivity::class.java)
                .putExtra(EXTRA_CUSTOM_ALBUM_ID, albumId)
                .putExtra(EXTRA_ALBUM_NAME, albumName)
    }
}

/** Multi-select actions (plan §5). */
enum class SelectionAction { SHARE, FAVOURITE, ADD_TO_ALBUM, MOVE, COPY, RENAME, DELETE, REMOVE_FROM_ALBUM }

/** Viewer launch: media keys in display order + the tapped index. */
internal typealias OpenItem = (keys: List<MediaKey>, index: Int) -> Unit
