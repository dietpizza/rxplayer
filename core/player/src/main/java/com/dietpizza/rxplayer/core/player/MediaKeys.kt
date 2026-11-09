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
     * Title when nothing better is known: the last path segment, percent-decoded, without its
     * extension. `smb://3/Movies/My%20Film.mkv` → `My Film`; `/sdcard/a/b.mp4` → `b`.
     */
    fun fallbackTitle(value: String): String {
        var path = value.substringBefore('?').substringBefore('#')
        val schemeEnd = path.indexOf("://")
        if (schemeEnd >= 0) path = path.substring(schemeEnd + 3)
        path = path.trimEnd('/')
        val segment = decode(path.substringAfterLast('/'))
        if (segment.isEmpty()) return value
        val name = segment.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    /** Percent-decoding that leaves `+` alone (it's a literal in paths). Malformed input is kept. */
    fun decode(s: String): String = if ('%' !in s) {
        s
    } else {
        runCatching { URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8) }.getOrDefault(s)
    }
}
