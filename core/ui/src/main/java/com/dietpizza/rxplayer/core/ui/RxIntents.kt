package com.dietpizza.rxplayer.core.ui

import android.content.Context
import android.content.Intent
import com.dietpizza.rxplayer.core.model.MediaKey
import java.util.UUID

/**
 * Cross-feature Activity launches. Features never depend on each other (plan §3.2a), so
 * activities in other modules are addressed by class name here.
 */
object RxIntents {
    const val PLAYER_ACTIVITY = "com.dietpizza.rxplayer.feature.player.PlayerActivity"
    const val SEARCH_ACTIVITY = "com.dietpizza.rxplayer.feature.search.SearchActivity"

    /** Ordered playlist of media keys (strings); next/prev and auto-next follow this order. */
    const val EXTRA_PLAYLIST = "rx.extra.PLAYLIST"
    const val EXTRA_START_INDEX = "rx.extra.START_INDEX"

    /** Token for a playlist too big for a Binder transaction, held in [PlaylistHandoff]. */
    const val EXTRA_PLAYLIST_TOKEN = "rx.extra.PLAYLIST_TOKEN"

    /** The start item's key, so a lost handoff (process death) still opens that item. */
    const val EXTRA_START_KEY = "rx.extra.START_KEY"

    /** [MODE_PLAYER] (default): open straight into the full player. [MODE_VIEWER]: the gallery
     *  viewer pager (photos + inline videos) which can morph into the full player (plan §4.1). */
    const val EXTRA_MODE = "rx.extra.MODE"
    const val MODE_PLAYER = "player"
    const val MODE_VIEWER = "viewer"

    /** Opens the full player on [playlist] at [startIndex]. */
    fun player(context: Context, playlist: List<MediaKey>, startIndex: Int): Intent {
        val intent = Intent().setClassName(context, PLAYER_ACTIVITY)
            .putExtra(EXTRA_START_INDEX, startIndex)
            .putExtra(EXTRA_START_KEY, playlist.getOrNull(startIndex)?.value)
        // Binder transactions cap out around 1 MB (shared by everything in flight), and a big
        // album's paths blow straight through it: TransactionTooLargeException kills the app.
        // Small lists travel in the intent (survive process death); big ones stay in memory.
        val bytes = playlist.sumOf { it.value.length.toLong() * 2 + 8 }
        return if (bytes <= INLINE_PLAYLIST_BYTES) {
            intent.putStringArrayListExtra(EXTRA_PLAYLIST, ArrayList(playlist.map { it.value }))
        } else {
            intent.putExtra(EXTRA_PLAYLIST_TOKEN, PlaylistHandoff.put(playlist))
        }
    }

    /**
     * The playlist and start index a [player]/[viewer] intent carries, or null if it carries
     * none. If an in-memory handoff was lost (process died), falls back to the start item alone.
     */
    fun playlistOf(intent: Intent): Pair<List<MediaKey>, Int>? {
        val start = intent.getIntExtra(EXTRA_START_INDEX, 0)
        intent.getStringArrayListExtra(EXTRA_PLAYLIST)?.takeIf { it.isNotEmpty() }?.let { list ->
            return list.map(::MediaKey) to start.coerceIn(0, list.lastIndex)
        }
        intent.getStringExtra(EXTRA_PLAYLIST_TOKEN)?.let(PlaylistHandoff::get)?.takeIf { it.isNotEmpty() }?.let { list ->
            return list to start.coerceIn(0, list.lastIndex)
        }
        return intent.getStringExtra(EXTRA_START_KEY)?.let { listOf(MediaKey(it)) to 0 }
    }

    /**
     * Opens the gallery viewer on [items] (photos and videos, album order) at [startIndex].
     * Hosted by PlayerActivity (plan §3.2a). Until the viewer lands (M4b) PlayerActivity may
     * treat this like [player] for video items.
     */
    fun viewer(context: Context, items: List<MediaKey>, startIndex: Int): Intent =
        player(context, items, startIndex).putExtra(EXTRA_MODE, MODE_VIEWER)

    private const val INLINE_PLAYLIST_BYTES = 128 * 1024L

    /** Opens search (filename + folder, plan §4 screen 11). */
    fun search(context: Context): Intent = Intent().setClassName(context, SEARCH_ACTIVITY)
}

/** In-process store for playlists too large to put in an Intent. Keeps the last few. */
internal object PlaylistHandoff {
    private const val MAX_ENTRIES = 4
    private val entries = object : LinkedHashMap<String, List<MediaKey>>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MediaKey>>?) = size > MAX_ENTRIES
    }

    @Synchronized
    fun put(list: List<MediaKey>): String = UUID.randomUUID().toString().also { entries[it] = list.toList() }

    /** Not removed on read: the activity may re-read its intent (recreation, onNewIntent). */
    @Synchronized
    fun get(token: String): List<MediaKey>? = entries[token]
}
