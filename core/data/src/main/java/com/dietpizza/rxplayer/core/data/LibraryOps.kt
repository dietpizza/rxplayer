package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.database.PlaybackStateEntity
import com.dietpizza.rxplayer.core.media.FileOpResult
import com.dietpizza.rxplayer.core.model.HistoryEntry
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaylistEntry
import com.dietpizza.rxplayer.core.model.VideoItem

/** Pure ordering rules for playlists. Lists are of `MediaKey.value`, unique. */
object PlaylistOrder {
    /** [current] followed by the [added] keys it doesn't already have (first occurrence wins). */
    fun append(current: List<String>, added: List<String>): List<String> {
        val seen = current.toHashSet()
        val out = ArrayList<String>(current.size + added.size)
        out += current
        for (k in added) if (seen.add(k)) out += k
        return out
    }

    fun remove(current: List<String>, removed: Collection<String>): List<String> {
        if (removed.isEmpty()) return current
        val set = removed.toHashSet()
        return current.filterNot { it in set }
    }

    /** Moves the item at [from] to [to]; out-of-range indices leave the list unchanged. */
    fun move(current: List<String>, from: Int, to: Int): List<String> {
        if (from !in current.indices || to !in current.indices || from == to) return current
        val out = current.toMutableList()
        out.add(to, out.removeAt(from))
        return out
    }

    /**
     * [desired] order, restricted to keys still in [current], followed by keys of [current] that
     * [desired] doesn't mention (added concurrently), in their current order.
     */
    fun applyOrder(current: List<String>, desired: List<String>): List<String> {
        val present = current.toHashSet()
        val out = ArrayList<String>(current.size)
        val used = HashSet<String>()
        for (k in desired) if (k in present && used.add(k)) out += k
        for (k in current) if (used.add(k)) out += k
        return out
    }
}

/**
 * History filter (plan §7.2, decision 23): LOCAL rows that were actually played, whose file still
 * exists in the visible library, most recent first.
 */
fun buildHistory(rows: List<PlaybackStateEntity>, videosByKey: Map<String, VideoItem>): List<HistoryEntry> {
    val out = ArrayList<HistoryEntry>(rows.size)
    for (row in rows) {
        if (row.source != PlaybackStateEntity.SOURCE_LOCAL || row.lastPlayedAt <= 0) continue
        val video = videosByKey[row.mediaKey] ?: continue
        out += HistoryEntry(video, row.lastPlayedAt)
    }
    out.sortByDescending { it.lastPlayedAt }
    return out
}

/** Joins ordered playlist keys with the live library. */
fun buildPlaylistEntries(keys: List<String>, videosByKey: Map<String, VideoItem>): List<PlaylistEntry> =
    keys.mapIndexed { i, k -> PlaylistEntry(i, MediaKey(k), videosByKey[k]) }

/** `old key → new key` for every successful rename/move that changed the path. */
fun keyRewrites(results: List<FileOpResult>): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (r in results) {
        val target = r.targetPath ?: continue
        if (!r.isSuccess || target == r.sourcePath) continue
        out[MediaKey.forFile(r.sourcePath).value] = MediaKey.forFile(target).value
    }
    return out
}
