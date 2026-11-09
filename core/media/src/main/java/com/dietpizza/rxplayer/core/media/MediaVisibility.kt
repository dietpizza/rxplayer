package com.dietpizza.rxplayer.core.media

import java.io.File

/**
 * Decides whether a video path is visible (plan decision 32): hidden if any path segment starts
 * with "." or if its directory or any ancestor contains a `.nomedia` file.
 *
 * Results are memoised per directory, so one instance should live for one query only (a `.nomedia`
 * file added later is picked up on the next query). Not thread-safe.
 */
class MediaVisibility(
    private val hasNoMedia: (dir: String) -> Boolean = { File(it, ".nomedia").exists() },
) {
    private val dirCache = HashMap<String, Boolean>()

    fun isVisible(path: String): Boolean {
        val slash = path.lastIndexOf('/')
        if (slash < 0) return false
        if (path.startsWith(".", startIndex = slash + 1)) return false
        return isDirVisible(path.substring(0, slash))
    }

    fun isDirVisible(dir: String): Boolean {
        if (dir.isEmpty()) return true
        dirCache[dir]?.let { return it }
        val slash = dir.lastIndexOf('/')
        val visible = when {
            dir.startsWith(".", startIndex = slash + 1) -> false
            hasNoMedia(dir) -> false
            slash <= 0 -> true
            else -> isDirVisible(dir.substring(0, slash))
        }
        dirCache[dir] = visible
        return visible
    }
}
