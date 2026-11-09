package com.dietpizza.rxplayer.feature.videos

import android.content.res.Resources
import com.dietpizza.rxplayer.core.model.FileOpError

enum class FileOp { MOVE, COPY, DELETE }

/** One-off snackbar messages from the library ViewModels. Resolved to text in the UI. */
internal sealed interface LibraryMessage {
    data class FileOpDone(val op: FileOp, val succeeded: Int, val failed: Int) : LibraryMessage
    data class AddedToPlaylist(val count: Int, val playlist: String) : LibraryMessage
    data class Favourited(val count: Int, val favourite: Boolean) : LibraryMessage
}

internal fun LibraryMessage.resolve(res: Resources): String = when (this) {
    is LibraryMessage.FileOpDone -> {
        val done = when (op) {
            FileOp.MOVE -> R.plurals.msg_moved
            FileOp.COPY -> R.plurals.msg_copied
            FileOp.DELETE -> R.plurals.msg_deleted
        }
        val total = succeeded + failed
        when {
            failed == 0 -> res.getQuantityString(done, succeeded, succeeded)
            else -> res.getQuantityString(R.plurals.msg_failed, total, failed, total)
        }
    }
    is LibraryMessage.AddedToPlaylist -> if (count == 0) {
        res.getString(R.string.msg_already_in_playlist, playlist)
    } else {
        res.getQuantityString(R.plurals.msg_added_to_playlist, count, count, playlist)
    }
    is LibraryMessage.Favourited -> res.getQuantityString(
        if (favourite) R.plurals.msg_favourited else R.plurals.msg_unfavourited,
        count,
        count,
    )
}

/** Inline error under a name field. */
internal fun FileOpError.nameErrorRes(): Int = when (this) {
    FileOpError.TARGET_EXISTS -> R.string.error_name_exists
    FileOpError.INVALID_NAME -> R.string.error_name_invalid
    FileOpError.NOT_FOUND -> R.string.error_not_found
    else -> R.string.error_generic
}
