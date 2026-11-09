package com.dietpizza.rxplayer.core.player.subtitle

import java.util.Locale

/** A subtitle file found next to a video. */
data class SidecarMatch(
    val fileName: String,
    val mimeType: String,
    /** BCP-47-ish tag from `movie.<lang>.srt`, else null. */
    val language: String?,
)

/**
 * Same-name sidecar subtitle matching (decision 9, plan §6.4). Pure: works on file names so it
 * can be unit tested; the caller lists the directory off the main thread.
 *
 * `Movie.mkv` matches `Movie.srt`, `movie.SRT`, `Movie.en.srt`, `Movie.pt-BR.vtt`, `Movie.eng.ass`.
 * An exact base-name match sorts first, then by language tag, then by format preference
 * (srt, vtt, ass, ssa).
 */
object SidecarSubtitles {
    const val MIME_SUBRIP = "application/x-subrip"
    const val MIME_SSA = "text/x-ssa"
    const val MIME_VTT = "text/vtt"

    private val EXTENSIONS = linkedMapOf(
        "srt" to MIME_SUBRIP,
        "vtt" to MIME_VTT,
        "ass" to MIME_SSA,
        "ssa" to MIME_SSA,
    )

    /** Matches [siblings] (file names in the video's folder) against [videoFileName]. */
    fun match(videoFileName: String, siblings: Collection<String>): List<SidecarMatch> {
        val base = videoFileName.substringBeforeLast('.', videoFileName)
        if (base.isEmpty()) return emptyList()
        val baseLower = base.lowercase(Locale.ROOT)
        val out = ArrayList<SidecarMatch>()
        for (name in siblings) {
            val dot = name.lastIndexOf('.')
            if (dot <= 0) continue
            val ext = name.substring(dot + 1).lowercase(Locale.ROOT)
            val mime = EXTENSIONS[ext] ?: continue
            val stem = name.substring(0, dot)
            val stemLower = stem.lowercase(Locale.ROOT)
            val language: String? = when {
                stemLower == baseLower -> null
                stemLower.startsWith("$baseLower.") -> {
                    val tag = stem.substring(base.length + 1)
                    if (isLanguageTag(tag)) tag else continue
                }
                else -> continue
            }
            out += SidecarMatch(name, mime, language)
        }
        out.sortWith(
            compareBy<SidecarMatch>({ it.language != null }, { it.language?.lowercase(Locale.ROOT) ?: "" })
                .thenBy { EXTENSIONS.keys.indexOf(it.fileName.substringAfterLast('.').lowercase(Locale.ROOT)) },
        )
        return out
    }

    /** MIME type for a subtitle file name, or null if it isn't one we load. */
    fun mimeTypeFor(fileName: String): String? =
        EXTENSIONS[fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)]

    /** `en`, `eng`, `pt-BR`, `zh_Hans`; longer words such as `forced` are rejected. */
    private fun isLanguageTag(tag: String): Boolean {
        if (tag.isEmpty() || tag.length > 12) return false
        val parts = tag.split('-', '_')
        val primary = parts[0]
        if (primary.length !in 2..3 || !primary.all { it.isLetter() }) return false
        return parts.drop(1).all { p -> p.length in 2..8 && p.all { it.isLetterOrDigit() } }
    }
}
