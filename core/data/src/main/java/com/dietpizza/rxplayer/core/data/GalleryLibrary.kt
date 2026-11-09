package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.GalleryAlbum
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.MediaSortField
import com.dietpizza.rxplayer.core.model.VirtualAlbum

/**
 * One snapshot of the local photo + video library, grouped once per MediaStore emission. Every list
 * in it is in the default order (date taken, newest first); other orders are derived on demand by
 * [sortItems] / [sortAlbums].
 */
class GalleryLibrary(
    /** Folder albums (the Camera folders are excluded: they live in the pinned [VirtualAlbum.CAMERA]). Unsorted. */
    val albums: List<GalleryAlbum>,
    /** Non-empty virtual albums, in pinned-row order. */
    val virtualAlbums: List<GalleryAlbum>,
    /** folderPath → items directly in it, newest first. Includes the Camera folders. */
    val itemsByFolder: Map<String, List<GalleryItem>>,
    /** Items per virtual album, newest first. Empty albums are present with an empty list. */
    val virtualItems: Map<VirtualAlbum, List<GalleryItem>>,
) {
    fun items(album: VirtualAlbum): List<GalleryItem> = virtualItems[album].orEmpty()

    companion object {
        val EMPTY = GalleryLibrary(emptyList(), emptyList(), emptyMap(), emptyMap())
    }
}

/** Default order: date taken, newest first; ties by date modified, then id (newest row first). Total. */
val NewestFirst: Comparator<GalleryItem> = Comparator { a, b ->
    when {
        a.dateTakenMs != b.dateTakenMs -> b.dateTakenMs.compareTo(a.dateTakenMs)
        a.dateModifiedMs != b.dateModifiedMs -> b.dateModifiedMs.compareTo(a.dateModifiedMs)
        a.id != b.id -> b.id.compareTo(a.id)
        else -> a.path.compareTo(b.path)
    }
}

private val ByName: Comparator<GalleryItem> =
    Comparator<GalleryItem> { a, b -> NaturalOrderComparator.compare(a.displayName, b.displayName) }
        .thenBy { it.path }

private val BySize: Comparator<GalleryItem> =
    Comparator<GalleryItem> { a, b -> a.sizeBytes.compareTo(b.sizeBytes) }.then(ByName)

private val AlbumByName: Comparator<GalleryAlbum> =
    Comparator<GalleryAlbum> { a, b -> NaturalOrderComparator.compare(a.name, b.name) }.thenBy { it.path }

private val AlbumByModified: Comparator<GalleryAlbum> =
    Comparator<GalleryAlbum> { a, b -> a.lastModifiedMs.compareTo(b.lastModifiedMs) }.then(AlbumByName.reversed())

private val AlbumBySize: Comparator<GalleryAlbum> =
    Comparator<GalleryAlbum> { a, b -> a.totalSizeBytes.compareTo(b.totalSizeBytes) }.then(AlbumByName.reversed())

private val AlbumByCount: Comparator<GalleryAlbum> =
    Comparator<GalleryAlbum> { a, b -> a.itemCount.compareTo(b.itemCount) }.then(AlbumByName.reversed())

/** `…/DCIM/Camera` on any volume (case-insensitive, as some OEMs write `camera`). */
fun isCameraFolder(folderPath: String): Boolean =
    folderPath.endsWith(CAMERA_SUFFIX, ignoreCase = true)

private const val CAMERA_SUFFIX = "/DCIM/Camera"

/**
 * Groups [items] (any order) into a [GalleryLibrary]. O(n log n) overall: one hash insert per item,
 * one sort per folder, the virtual albums merged from the already-sorted folders' items.
 */
fun buildGalleryLibrary(items: List<GalleryItem>): GalleryLibrary {
    if (items.isEmpty()) return GalleryLibrary.EMPTY
    val byFolder = HashMap<String, ArrayList<GalleryItem>>()
    val videos = ArrayList<GalleryItem>()
    val camera = ArrayList<GalleryItem>()
    for (item in items) {
        byFolder.getOrPut(item.folderPath) { ArrayList() }.add(item)
        if (item.isVideo) videos += item
        if (isCameraFolder(item.folderPath)) camera += item
    }
    val albums = ArrayList<GalleryAlbum>(byFolder.size)
    var cameraPath = ""
    for ((path, folderItems) in byFolder) {
        folderItems.sortWith(NewestFirst)
        if (isCameraFolder(path)) {
            if (cameraPath.isEmpty() || path < cameraPath) cameraPath = path
        } else {
            albums += aggregateAlbum(path, folderName(path), folderItems)
        }
    }
    videos.sortWith(NewestFirst)
    camera.sortWith(NewestFirst)

    val virtualItems = mapOf(VirtualAlbum.ALL_VIDEOS to videos, VirtualAlbum.CAMERA to camera)
    val virtualAlbums = ArrayList<GalleryAlbum>(2)
    if (videos.isNotEmpty()) {
        virtualAlbums += aggregateAlbum(videos.first().folderPath, "All videos", videos, VirtualAlbum.ALL_VIDEOS)
    }
    if (camera.isNotEmpty()) {
        virtualAlbums += aggregateAlbum(cameraPath, "Camera", camera, VirtualAlbum.CAMERA)
    }
    return GalleryLibrary(albums, virtualAlbums, byFolder, virtualItems)
}

internal fun folderName(path: String): String = path.substring(path.lastIndexOf('/') + 1).ifEmpty { path }

/** Album card stats. [items] must be newest first (the cover is the first item). */
fun aggregateAlbum(
    path: String,
    name: String,
    items: List<GalleryItem>,
    virtual: VirtualAlbum? = null,
): GalleryAlbum {
    var videoCount = 0
    var lastModified = 0L
    var latestTaken = 0L
    var totalSize = 0L
    for (item in items) {
        if (item.isVideo) videoCount++
        if (item.dateModifiedMs > lastModified) lastModified = item.dateModifiedMs
        if (item.dateTakenMs > latestTaken) latestTaken = item.dateTakenMs
        totalSize += item.sizeBytes
    }
    val cover = items.firstOrNull()
    return GalleryAlbum(
        path = path,
        name = name,
        itemCount = items.size,
        videoCount = videoCount,
        coverContentUri = cover?.contentUri,
        coverIsVideo = cover?.isVideo == true,
        lastModifiedMs = lastModified,
        latestTakenMs = latestTaken,
        totalSizeBytes = totalSize,
        virtual = virtual,
    )
}

/**
 * [items] in [sort] order. [items] must already be newest first (as every list in
 * [GalleryLibrary] is), so the default order is free and oldest-first is a reversed copy.
 */
fun sortItems(items: List<GalleryItem>, sort: MediaSort): List<GalleryItem> {
    if (items.size < 2) return items
    return when (sort.field) {
        MediaSortField.DATE_TAKEN -> if (sort.descending) items else items.asReversed().toList()
        MediaSortField.NAME -> items.sortedWith(if (sort.descending) ByName.reversed() else ByName)
        MediaSortField.SIZE -> items.sortedWith(if (sort.descending) BySize.reversed() else BySize)
    }
}

/** Folder albums in [sort] order. Ties always fall back to name A→Z. */
fun sortAlbums(albums: List<GalleryAlbum>, sort: AlbumSort): List<GalleryAlbum> {
    val comparator = when (sort.field) {
        AlbumSortField.NAME -> AlbumByName
        AlbumSortField.DATE_MODIFIED -> AlbumByModified
        AlbumSortField.COUNT -> AlbumByCount
        AlbumSortField.SIZE -> AlbumBySize
    }
    return albums.sortedWith(if (sort.descending) comparator.reversed() else comparator)
}
