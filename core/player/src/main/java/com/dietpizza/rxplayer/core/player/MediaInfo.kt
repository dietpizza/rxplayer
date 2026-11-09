package com.dietpizza.rxplayer.core.player

import androidx.media3.common.Format
import com.dietpizza.rxplayer.core.player.tracks.TrackNames
import java.util.Locale
import kotlin.math.roundToInt

/** Rows for the ⋮ sheet's info section (codec, resolution, bitrate, fps, path). */
data class MediaInfo(val title: String, val rows: List<Pair<String, String>>) {
    companion object {
        fun from(
            title: String,
            location: String,
            video: Format?,
            audio: Format?,
            displaySize: VideoDimensions,
            durationMs: Long,
            fileSizeBytes: Long,
            videoDecoder: String?,
            audioDecoder: String?,
        ): MediaInfo {
            val rows = ArrayList<Pair<String, String>>()
            if (video != null) {
                val codec = TrackNames.codecName(video.sampleMimeType) ?: video.sampleMimeType.orEmpty()
                rows += "Video" to listOfNotNull(codec, video.codecs?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
                val w = if (video.width > 0) video.width else displaySize.width
                val h = if (video.height > 0) video.height else displaySize.height
                if (w > 0 && h > 0) {
                    val display = if (displaySize.width > 0 && displaySize.width != w) " (shown ${displaySize.width}×${displaySize.height})" else ""
                    rows += "Resolution" to "$w×$h$display"
                }
                if (video.frameRate > 0) rows += "Frame rate" to fps(video.frameRate)
                bitrate(video.bitrate)?.let { rows += "Video bitrate" to it }
                hdr(video)?.let { rows += "HDR" to it }
                videoDecoder?.let { rows += "Video decoder" to it }
            }
            if (audio != null) {
                val parts = listOfNotNull(
                    TrackNames.codecName(audio.sampleMimeType),
                    TrackNames.channelName(audio.channelCount),
                    audio.sampleRate.takeIf { it > 0 }?.let { "${(it / 100) / 10.0} kHz" },
                    bitrate(audio.bitrate),
                )
                rows += "Audio" to parts.joinToString(" · ")
                audioDecoder?.let { rows += "Audio decoder" to it }
            }
            if (fileSizeBytes > 0 && durationMs > 0) {
                bitrate((fileSizeBytes * 8_000L / durationMs).toInt())?.let { rows += "Overall bitrate" to it }
            }
            if (fileSizeBytes > 0) rows += "Size" to size(fileSizeBytes)
            rows += "Location" to location
            return MediaInfo(title, rows)
        }

        fun bitrate(bps: Int): String? = when {
            bps <= 0 -> null
            bps >= 1_000_000 -> String.format(Locale.ROOT, "%.1f Mbps", bps / 1_000_000.0)
            else -> "${(bps / 1000.0).roundToInt()} kbps"
        }

        private fun fps(rate: Float): String {
            val rounded = (rate * 1000).roundToInt() / 1000.0
            return if (rounded % 1.0 == 0.0) "${rounded.toInt()} fps" else String.format(Locale.ROOT, "%.3f fps", rounded)
        }

        private fun size(bytes: Long): String = when {
            bytes >= 1L shl 30 -> String.format(Locale.ROOT, "%.2f GB", bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> String.format(Locale.ROOT, "%.1f MB", bytes / (1L shl 20).toDouble())
            else -> "${bytes / 1024} KB"
        }

        private fun hdr(f: Format): String? = when (f.colorInfo?.colorTransfer) {
            androidx.media3.common.C.COLOR_TRANSFER_ST2084 -> "HDR10 / PQ"
            androidx.media3.common.C.COLOR_TRANSFER_HLG -> "HLG"
            else -> null
        }
    }
}
