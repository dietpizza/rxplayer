package com.dietpizza.rxplayer.core.player.tracks

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Tracks
import java.util.Locale

/** One selectable audio or subtitle track, ready for a picker row. */
data class TrackOption(
    /** Index into `Tracks.groups`. */
    val groupIndex: Int,
    val trackIndex: Int,
    val title: String,
    /** Codec / channels / "Default" / "Forced" etc.; empty if nothing worth saying. */
    val detail: String,
    val selected: Boolean,
    val supported: Boolean,
)

/** Audio + subtitle options of the current item. */
data class TrackMenu(
    val audio: List<TrackOption> = emptyList(),
    val text: List<TrackOption> = emptyList(),
    /** True when text is deliberately off (disabled or nothing selected). */
    val textOff: Boolean = true,
) {
    companion object {
        val Empty = TrackMenu()
    }
}

/** Builds [TrackMenu]s from Media3 [Tracks] with human names (language + label). */
object TrackNames {

    fun menu(tracks: Tracks, locale: Locale = Locale.getDefault()): TrackMenu {
        val audio = ArrayList<TrackOption>()
        val text = ArrayList<TrackOption>()
        var anyTextSelected = false
        val groups = tracks.groups
        for (g in groups.indices) {
            val group = groups[g]
            val target = when (group.type) {
                C.TRACK_TYPE_AUDIO -> audio
                C.TRACK_TYPE_TEXT -> text
                else -> continue
            }
            for (t in 0 until group.length) {
                val format = group.getTrackFormat(t)
                val selected = group.isTrackSelected(t)
                if (selected && group.type == C.TRACK_TYPE_TEXT) anyTextSelected = true
                target += TrackOption(
                    groupIndex = g,
                    trackIndex = t,
                    title = title(format, locale),
                    detail = detail(format, group.type),
                    selected = selected,
                    supported = group.isTrackSupported(t),
                )
            }
        }
        return TrackMenu(number(audio, "Audio"), number(text, "Subtitle"), textOff = !anyTextSelected)
    }

    /** "English", "English · Commentary", "Commentary", or "" when nothing is known. */
    fun title(format: Format, locale: Locale = Locale.getDefault()): String {
        val language = languageName(format.language, locale)
        val label = format.label?.trim().orEmpty()
        return when {
            language.isNotEmpty() && label.isNotEmpty() &&
                !label.equals(language, ignoreCase = true) -> "$language · $label"
            language.isNotEmpty() -> language
            else -> label
        }
    }

    /** Display name for a language tag ("en" → "English"); "" for unknown/undetermined. */
    fun languageName(tag: String?, locale: Locale = Locale.getDefault()): String {
        if (tag.isNullOrBlank() || tag == C.LANGUAGE_UNDETERMINED || tag.equals("und", true)) return ""
        val l = Locale.forLanguageTag(tag.replace('_', '-'))
        val name = l.getDisplayName(locale)
        if (name.isBlank() || name.equals(tag, ignoreCase = true)) return tag
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    private fun detail(format: Format, type: Int): String {
        val parts = ArrayList<String>(4)
        // Subtitles parsed during extraction carry the original MIME type in `codecs`.
        val mime = if (format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES) {
            format.codecs
        } else {
            format.sampleMimeType ?: format.containerMimeType
        }
        codecName(mime)?.let(parts::add)
        if (type == C.TRACK_TYPE_AUDIO) channelName(format.channelCount)?.let(parts::add)
        if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) parts += "Forced"
        if (format.roleFlags and C.ROLE_FLAG_COMMENTARY != 0) parts += "Commentary"
        if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_VIDEO != 0) parts += "Description"
        if (format.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND != 0) parts += "SDH"
        return parts.joinToString(" · ")
    }

    fun channelName(count: Int): String? = when (count) {
        Format.NO_VALUE, 0 -> null
        1 -> "Mono"
        2 -> "Stereo"
        6 -> "5.1"
        8 -> "7.1"
        else -> "$count ch"
    }

    fun codecName(mime: String?): String? = when (mime) {
        null -> null
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "AC-3"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "E-AC-3"
        MimeTypes.AUDIO_AC4 -> "AC-4"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_MPEG_L2 -> "MP2"
        MimeTypes.AUDIO_RAW -> "PCM"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "HEVC"
        MimeTypes.VIDEO_VP8 -> "VP8"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        MimeTypes.VIDEO_MP4V -> "MPEG-4"
        MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        MimeTypes.APPLICATION_SUBRIP -> "SRT"
        MimeTypes.TEXT_SSA -> "SSA/ASS"
        MimeTypes.TEXT_VTT -> "WebVTT"
        MimeTypes.APPLICATION_TTML -> "TTML"
        MimeTypes.APPLICATION_PGS -> "PGS"
        MimeTypes.APPLICATION_VOBSUB -> "VobSub"
        MimeTypes.APPLICATION_DVBSUBS -> "DVB"
        MimeTypes.APPLICATION_TX3G -> "TX3G"
        MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_CEA708 -> "CC"
        MimeTypes.APPLICATION_MEDIA3_CUES -> null
        else -> mime.substringAfter('/').removePrefix("x-").uppercase(Locale.ROOT)
    }

    /** Untitled tracks get "Audio 2" / "Subtitle 1" so every row has a name. */
    private fun number(list: ArrayList<TrackOption>, noun: String): List<TrackOption> {
        for (i in list.indices) {
            if (list[i].title.isEmpty()) list[i] = list[i].copy(title = "$noun ${i + 1}")
        }
        return list
    }
}
