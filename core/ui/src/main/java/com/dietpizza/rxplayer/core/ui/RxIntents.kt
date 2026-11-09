package com.dietpizza.rxplayer.core.ui

import android.content.Context
import android.content.Intent
import com.dietpizza.rxplayer.core.model.MediaKey

/**
 * Cross-feature Activity launches. Features never depend on each other (plan §3.2a), so
 * activities in other modules are addressed by class name here.
 */
object RxIntents {
    const val PLAYER_ACTIVITY = "com.dietpizza.rxplayer.feature.player.PlayerActivity"

    /** Ordered playlist of media keys (strings); next/prev and auto-next follow this order. */
    const val EXTRA_PLAYLIST = "rx.extra.PLAYLIST"
    const val EXTRA_START_INDEX = "rx.extra.START_INDEX"

    /** Opens the full player on [playlist] at [startIndex]. */
    fun player(context: Context, playlist: List<MediaKey>, startIndex: Int): Intent =
        Intent().setClassName(context, PLAYER_ACTIVITY)
            .putStringArrayListExtra(EXTRA_PLAYLIST, ArrayList(playlist.map { it.value }))
            .putExtra(EXTRA_START_INDEX, startIndex)
}
