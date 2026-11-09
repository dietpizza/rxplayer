package com.dietpizza.rxplayer.core.netfs.model

/** One file or folder on a server. [path] is absolute (`/a/b.mkv`), see [com.dietpizza.rxplayer.core.netfs.RemotePath]. */
data class RemoteEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    /** Bytes, -1 when unknown (always -1 for folders). */
    val size: Long,
    /** Epoch ms, 0 when unknown. */
    val modifiedMs: Long,
    val hidden: Boolean = name.startsWith('.'),
) {
    val type: MediaType get() = if (isDirectory) MediaType.FOLDER else MediaType.of(name)
}

enum class MediaType {
    FOLDER, VIDEO, IMAGE, AUDIO, SUBTITLE, OTHER;

    val isMedia: Boolean get() = this == VIDEO || this == IMAGE

    companion object {
        private val VIDEO_EXT = setOf(
            "mkv", "mp4", "m4v", "mov", "avi", "webm", "wmv", "flv", "ts", "m2ts", "mts", "mpg",
            "mpeg", "3gp", "3g2", "ogv", "vob", "divx", "rmvb", "asf", "f4v",
        )
        private val IMAGE_EXT = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "avif", "bmp", "dng",
        )
        private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "opus", "wav", "wma", "alac", "ape")
        private val SUBTITLE_EXT = setOf("srt", "ass", "ssa", "vtt", "sub", "idx", "sup")

        fun of(fileName: String): MediaType {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                in VIDEO_EXT -> VIDEO
                in IMAGE_EXT -> IMAGE
                in AUDIO_EXT -> AUDIO
                in SUBTITLE_EXT -> SUBTITLE
                else -> OTHER
            }
        }
    }
}
