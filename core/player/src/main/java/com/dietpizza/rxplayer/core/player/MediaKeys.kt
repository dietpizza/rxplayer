package com.dietpizza.rxplayer.core.player

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** Pure helpers on media-key strings (plan §7.1). JVM-testable: no android.net.Uri. */
object MediaKeys {

    /** Lower-case scheme of a key/URI string, or null. */
    fun scheme(value: String): String? {
        val colon = value.indexOf(':')
        if (colon <= 0) return null
        val s = value.substring(0, colon)
        if (!s[0].isLetter() || !s.all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }) return null
        return s.lowercase()
    }

    /**
     * Title when nothing better is known: the last path segment without its extension.
     *
     * Network keys (`smb://3/Movies/My film #2.mkv`) hold RAW paths that may contain spaces,
     * `#`, `?` and `%`, so for [rawPath] keys we cut after the last '/' of the string as-is
     * (never `Uri.getLastPathSegment()`, which stops at `#`/`?`). Other URIs (content, http)
     * are encoded: their query/fragment is dropped and the segment percent-decoded.
     */
    fun fallbackTitle(value: String, rawPath: Boolean): String {
        var path = value
        if (!rawPath) path = path.substringBefore('?').substringBefore('#')
        val schemeEnd = path.indexOf("://")
        if (schemeEnd >= 0) path = path.substring(schemeEnd + 3)
        path = path.trimEnd('/')
        var segment = path.substringAfterLast('/')
        if (!rawPath) segment = decode(segment).substringAfterLast('/')
        if (segment.isEmpty()) return value
        val dot = segment.lastIndexOf('.')
        return if (dot > 0) segment.substring(0, dot) else segment
    }

    /** Percent-decoding that leaves `+` alone (it's a literal in paths). Malformed input is kept. */
    fun decode(s: String): String = if ('%' !in s) {
        s
    } else {
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8) }.getOrDefault(s)
    }
}
