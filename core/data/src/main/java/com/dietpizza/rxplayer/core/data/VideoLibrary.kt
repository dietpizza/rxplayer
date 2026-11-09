package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.media.LocalVideo
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaybackState
import com.dietpizza.rxplayer.core.model.VideoFolder
import com.dietpizza.rxplayer.core.model.VideoItem

/** One snapshot of the local library joined with playback state. Built once per upstream emission. */
class VideoLibrary(
    /** Sorted by folder name, natural + case-insensitive. */
    val folders: List<VideoFolder>,
    /** folderPath → videos directly in it, sorted by title (natural order). */
    val videosByFolder: Map<String, List<VideoItem>>,
) {
    companion object {
        val EMPTY = VideoLibrary(emptyList(), emptyMap())
    }
}

private val videoOrder: Comparator<VideoItem> =
    Comparator<VideoItem> { a, b -> NaturalOrderComparator.compare(a.title, b.title) }
        .thenBy { it.path }

private val folderOrder: Comparator<VideoFolder> =
    Comparator<VideoFolder> { a, b -> NaturalOrderComparator.compare(a.name, b.name) }
        .thenBy { it.path }

/** Title shown in lists: the file name without its extension. */
internal fun titleOf(displayName: String): String {
    val dot = displayName.lastIndexOf('.')
    return if (dot > 0) displayName.substring(0, dot) else displayName
}

internal fun LocalVideo.toVideoItem(playback: PlaybackState?): VideoItem = VideoItem(
    key = MediaKey.forFile(path),
    contentUri = contentUri,
    title = titleOf(displayName),
    path = path,
    folderPath = folderPath,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    dateModifiedMs = dateModifiedMs,
    playback = playback,
)

/**
 * Pure join of MediaStore rows with playback state (keyed by `MediaKey.value`). O(n log n) overall;
 * one hash lookup per video, no per-item queries.
 */
fun buildVideoLibrary(videos: List<LocalVideo>, states: Map<String, PlaybackState>): VideoLibrary {
    if (videos.isEmpty()) return VideoLibrary.EMPTY
    val byFolder = HashMap<String, ArrayList<VideoItem>>()
    for (v in videos) {
        val item = v.toVideoItem(states[MediaKey.forFile(v.path).value])
        byFolder.getOrPut(v.folderPath) { ArrayList() }.add(item)
    }
    val folders = ArrayList<VideoFolder>(byFolder.size)
    for ((path, items) in byFolder) {
        items.sortWith(videoOrder)
        folders += aggregateFolder(path, items)
    }
    folders.sortWith(folderOrder)
    return VideoLibrary(folders, byFolder)
}

/** Folder card stats: count, NEW count, total size, cover = most recently modified video. */
fun aggregateFolder(path: String, items: List<VideoItem>): VideoFolder {
    var newCount = 0
    var totalSize = 0L
    var latest: VideoItem? = null
    for (item in items) {
        if (item.isNew) newCount++
        totalSize += item.sizeBytes
        if (latest == null || item.dateModifiedMs > latest.dateModifiedMs) latest = item
    }
    return VideoFolder(
        path = path,
        name = path.substring(path.lastIndexOf('/') + 1).ifEmpty { path },
        videoCount = items.size,
        newCount = newCount,
        totalSizeBytes = totalSize,
        coverContentUri = latest?.contentUri,
        lastModifiedMs = latest?.dateModifiedMs ?: 0L,
    )
}
