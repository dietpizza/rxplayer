package com.dietpizza.rxplayer.feature.videos

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.dietpizza.rxplayer.core.ui.RxActivity
import com.dietpizza.rxplayer.core.ui.RxIntents
import dagger.hilt.android.AndroidEntryPoint

/** One video playlist (plan §4 #8): play all, drag to reorder, remove, rename, delete. */
@AndroidEntryPoint
class PlaylistActivity : RxActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setRxContent {
            PlaylistScreen(
                onBack = ::finish,
                onPlay = { playlist, index -> startActivity(RxIntents.player(this, playlist, index)) },
            )
        }
    }

    companion object {
        /** Playlist id (required). Read by [PlaylistViewModel] through SavedStateHandle. */
        const val EXTRA_PLAYLIST_ID = "rx.extra.PLAYLIST_ID"

        /** Display name, so the app bar has a title on the first frame (optional). */
        const val EXTRA_PLAYLIST_NAME = "rx.extra.PLAYLIST_NAME"

        fun intent(context: Context, playlistId: Long, name: String? = null): Intent =
            Intent(context, PlaylistActivity::class.java)
                .putExtra(EXTRA_PLAYLIST_ID, playlistId)
                .putExtra(EXTRA_PLAYLIST_NAME, name)
    }
}
