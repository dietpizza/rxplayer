package com.dietpizza.rxplayer.feature.network

import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.netfs.NetKey
import com.dietpizza.rxplayer.core.netfs.model.MediaType
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import com.dietpizza.rxplayer.core.netfs.model.RemoteEntry

/** Pure listing rules for the remote browser (unit-tested). */
internal object BrowserListing {

    /**
     * Folders first, then files, each in natural name order. Default view: folders + photos +
     * videos, no hidden entries; [showAll] shows everything.
     */
    fun visible(entries: List<RemoteEntry>, showAll: Boolean): List<RemoteEntry> =
        entries
            .filter { showAll || (!it.hidden && (it.isDirectory || it.type.isMedia)) }
            .sortedWith(compareBy<RemoteEntry> { !it.isDirectory }.then { a, b -> NaturalOrder.compare(a.name, b.name) })

    /** True when [visible] hides files that [showAll] would reveal (for the empty-state hint). */
    fun hidesFiles(entries: List<RemoteEntry>): Boolean = entries.any { !it.hidden && !it.isDirectory && !it.type.isMedia }

    /**
     * What tapping [tapped] plays: every video in [rows] in order, starting at the tapped one.
     * Images open in the viewer with the folder's photos and videos. Null for anything else.
     */
    fun playlistFor(rows: List<RemoteEntry>, tapped: RemoteEntry, protocol: NetProtocol, serverId: Long): Playlist? {
        val types = when (tapped.type) {
            MediaType.VIDEO -> setOf(MediaType.VIDEO)
            MediaType.IMAGE -> setOf(MediaType.IMAGE, MediaType.VIDEO)
            else -> return null
        }
        val items = rows.filter { it.type in types }
        val index = items.indexOfFirst { it.path == tapped.path }
        if (index < 0) return null
        return Playlist(
            keys = items.map { NetKey.of(protocol, serverId, it.path).toMediaKey() },
            startIndex = index,
            viewer = tapped.type == MediaType.IMAGE,
        )
    }

    data class Playlist(val keys: List<MediaKey>, val startIndex: Int, val viewer: Boolean)

    /** Initial selection in the rename field: the base name, without the extension. */
    fun baseNameEnd(name: String, isDirectory: Boolean): Int {
        if (isDirectory) return name.length
        val dot = name.lastIndexOf('.')
        return if (dot > 0) dot else name.length
    }
}

/** "Episode 2" before "Episode 10"; case-insensitive. */
internal object NaturalOrder : Comparator<String> {
    override fun compare(a: String, b: String): Int {
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isDigit() && cb.isDigit()) {
                val si = i
                val sj = j
                while (i < a.length && a[i].isDigit()) i++
                while (j < b.length && b[j].isDigit()) j++
                val na = a.substring(si, i).trimStart('0')
                val nb = b.substring(sj, j).trimStart('0')
                if (na.length != nb.length) return na.length - nb.length
                val c = na.compareTo(nb)
                if (c != 0) return c
            } else {
                val c = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (c != 0) return c
                i++
                j++
            }
        }
        val rest = (a.length - i) - (b.length - j)
        return if (rest != 0) rest else a.compareTo(b)
    }
}
