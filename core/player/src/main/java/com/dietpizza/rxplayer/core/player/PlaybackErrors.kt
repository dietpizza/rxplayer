package com.dietpizza.rxplayer.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer

/** A user-facing playback problem. [fatal] = playback stopped; otherwise a warning. */
data class PlayerError(
    val title: String,
    val message: String,
    val fatal: Boolean,
)

/** Turns ExoPlayer errors into messages that name the codec / MIME type where possible. */
@OptIn(UnstableApi::class)
internal object PlaybackErrors {

    fun describe(error: PlaybackException): PlayerError {
        val renderer = error as? ExoPlaybackException
        if (renderer != null && renderer.type == ExoPlaybackException.TYPE_RENDERER) {
            val format = renderer.rendererFormat
            val init = generateSequence(error.cause) { it.cause }
                .filterIsInstance<MediaCodecRenderer.DecoderInitializationException>()
                .firstOrNull()
            val mime = format?.sampleMimeType ?: init?.mimeType
            val kind = kindOf(mime)
            val codec = if (format != null) codecLabel(format) else mime?.let(::mimeLabel) ?: "this format"
            val details = formatDetails(format)
            val message = when {
                renderer.rendererFormatSupport == C.FORMAT_EXCEEDS_CAPABILITIES ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
                    "$codec$details exceeds what this device's $kind decoder can handle" +
                        " (resolution, frame rate or bit depth too high)."
                init != null -> "The $kind decoder for $codec failed to start." +
                    (init.diagnosticInfo?.let { " ($it)" } ?: "")
                else -> "$codec isn't supported on this device."
            }
            return PlayerError("Can't play $kind", message, fatal = true)
        }
        val message = when (error.errorCode) {
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
                "This container format isn't supported."
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "The file looks damaged or incomplete."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "The file no longer exists."
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "RX Player isn't allowed to read this file."
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This format isn't supported."
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "The decoder failed to start."
            PlaybackException.ERROR_CODE_DECODING_FAILED -> "Decoding failed part-way through the file."
            else -> error.message ?: "Playback failed."
        }
        return PlayerError("Can't play this video", "$message (${error.errorCodeName})", fatal = true)
    }

    /** Warning when a file has a track type we can't decode (ExoPlayer plays the rest silently). */
    fun unsupportedTrack(trackType: Int, format: Format): PlayerError {
        val kind = if (trackType == C.TRACK_TYPE_VIDEO) "video" else "audio"
        val rest = if (trackType == C.TRACK_TYPE_VIDEO) "Playing audio only." else "Playing without sound."
        return PlayerError(
            title = "Unsupported $kind codec",
            message = "${codecLabel(format)}${formatDetails(format)} isn't supported on this device. $rest",
            fatal = false,
        )
    }

    private fun kindOf(mime: String?): String = when {
        MimeTypes.isVideo(mime) -> "video"
        MimeTypes.isAudio(mime) -> "audio"
        MimeTypes.isText(mime) -> "subtitles"
        else -> "media"
    }

    fun codecLabel(format: Format): String {
        val mime = format.sampleMimeType ?: format.containerMimeType ?: return "Unknown codec"
        val label = mimeLabel(mime)
        val codecs = format.codecs
        return if (codecs.isNullOrBlank() || label == codecs) label else "$label ($codecs)"
    }

    private fun formatDetails(format: Format?): String {
        if (format == null) return ""
        val parts = buildList {
            if (format.width > 0 && format.height > 0) add("${format.width}×${format.height}")
            if (format.frameRate > 0f) add("${"%.0f".format(format.frameRate)} fps")
            if (format.channelCount > 0) add("${format.channelCount} ch")
        }
        return if (parts.isEmpty()) "" else " " + parts.joinToString(", ", "[", "]")
    }

    fun mimeLabel(mime: String): String = when (mime) {
        MimeTypes.VIDEO_H264 -> "H.264"
        MimeTypes.VIDEO_H265 -> "HEVC (H.265)"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP8 -> "VP8"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_DOLBY_VISION -> "Dolby Vision"
        MimeTypes.VIDEO_MPEG -> "MPEG-1"
        MimeTypes.VIDEO_MPEG2 -> "MPEG-2"
        MimeTypes.VIDEO_MP4V -> "MPEG-4 Part 2"
        MimeTypes.VIDEO_H263 -> "H.263"
        MimeTypes.VIDEO_DIVX -> "DivX"
        MimeTypes.VIDEO_MJPEG -> "Motion JPEG"
        MimeTypes.VIDEO_VC1 -> "VC-1"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital (AC-3)"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus (E-AC-3)"
        MimeTypes.AUDIO_AC4 -> "Dolby AC-4"
        MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS, MimeTypes.AUDIO_DTS_X -> "DTS"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_ALAC -> "ALAC"
        MimeTypes.AUDIO_RAW -> "PCM"
        else -> mime
    }
}
