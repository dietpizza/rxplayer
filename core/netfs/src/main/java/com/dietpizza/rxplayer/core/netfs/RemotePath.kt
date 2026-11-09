package com.dietpizza.rxplayer.core.netfs

/**
 * Remote paths are absolute, `/`-separated, raw (never URL-encoded) and have no trailing slash
 * except the root `/`. Every client converts to its own form at the edge (SMB backslashes,
 * WebDAV URL segments).
 */
object RemotePath {
    const val ROOT = "/"

    /** Collapses duplicate slashes, resolves `.` and `..`, and adds the leading slash. */
    fun normalize(path: String): String {
        val out = ArrayList<String>()
        for (part in path.replace('\\', '/').split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                else -> out += part
            }
        }
        return if (out.isEmpty()) ROOT else out.joinToString("/", prefix = "/")
    }

    fun join(parent: String, name: String): String {
        require(name.isNotEmpty() && '/' !in name) { "Not a single path segment: $name" }
        val p = normalize(parent)
        return if (p == ROOT) "/$name" else "$p/$name"
    }

    /** Parent folder, or null for the root. */
    fun parent(path: String): String? {
        val p = normalize(path)
        if (p == ROOT) return null
        val i = p.lastIndexOf('/')
        return if (i <= 0) ROOT else p.substring(0, i)
    }

    /** Last segment ("" for the root). */
    fun name(path: String): String = normalize(path).substringAfterLast('/')

    fun segments(path: String): List<String> =
        normalize(path).split('/').filter { it.isNotEmpty() }

    /** True when [path] is [root] or below it. */
    fun isWithin(root: String, path: String): Boolean {
        val r = normalize(root)
        val p = normalize(path)
        return r == ROOT || p == r || p.startsWith("$r/")
    }

    /** A valid new file name: one non-empty segment, not `.`/`..`. */
    fun isValidName(name: String): Boolean =
        name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name && '\u0000' !in name

    data class Crumb(val label: String, val path: String)

    /**
     * Breadcrumbs from [root] down to [path]. The first crumb is the root with [rootLabel];
     * paths outside [root] collapse to just the root crumb.
     */
    fun breadcrumbs(root: String, path: String, rootLabel: String): List<Crumb> {
        val r = normalize(root)
        val p = normalize(path)
        val crumbs = arrayListOf(Crumb(rootLabel, r))
        if (!isWithin(r, p) || p == r) return crumbs
        var current = r
        for (segment in segments(p).drop(segments(r).size)) {
            current = join(current, segment)
            crumbs += Crumb(segment, current)
        }
        return crumbs
    }
}
