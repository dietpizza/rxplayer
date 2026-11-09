package com.dietpizza.rxplayer.core.model

/** Why a file operation (rename / move / copy / delete) failed for one item. */
enum class FileOpError {
    /** The source doesn't exist (or isn't a regular file). */
    NOT_FOUND,

    /** Rename / new folder: something with that name already exists. (Move/copy pick a free name instead.) */
    TARGET_EXISTS,

    /** Empty name, contains '/', or is "." / "..". */
    INVALID_NAME,

    /** The target directory doesn't exist and couldn't be created. */
    TARGET_DIR_UNAVAILABLE,

    /** Not a local file (a network or content:// key). */
    UNSUPPORTED,

    /** Any other I/O failure (permission, disk full, ...). */
    IO,
}

/** Byte progress for copies (and moves that have to copy across volumes). */
data class CopyProgress(
    /** 0-based index of the item being copied. */
    val itemIndex: Int,
    val itemCount: Int,
    val totalBytesCopied: Long,
    val totalBytes: Long,
) {
    val fraction: Float get() = if (totalBytes <= 0) 0f else (totalBytesCopied.toFloat() / totalBytes).coerceIn(0f, 1f)
}
