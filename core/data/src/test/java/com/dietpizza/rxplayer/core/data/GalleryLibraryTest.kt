package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.model.AlbumSort
import com.dietpizza.rxplayer.core.model.AlbumSortField
import com.dietpizza.rxplayer.core.model.GalleryItem
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.MediaSort
import com.dietpizza.rxplayer.core.model.MediaSortField
import com.dietpizza.rxplayer.core.model.VirtualAlbum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryLibraryTest {
    private var nextId = 1L

    private fun item(
        path: String,
        taken: Long = 0,
        modified: Long = taken,
        size: Long = 100,
        video: Boolean = false,
    ): GalleryItem {
        val id = nextId++
        return GalleryItem(
            key = MediaKey.forFile(path),
            id = id,
            contentUri = "content://media/external/${if (video) "video" else "images"}/media/$id",
            path = path,
            folderPath = path.substringBeforeLast('/'),
            displayName = path.substringAfterLast('/'),
            mimeType = if (video) "video/mp4" else "image/jpeg",
            isVideo = video,
            dateTakenMs = taken,
            dateModifiedMs = modified,
            sizeBytes = size,
            width = 4000,
            height = 3000,
            durationMs = if (video) 1_000 else 0,
            orientation = 0,
        )
    }

    @Test
    fun groupsFoldersNewestFirstAndAggregates() {
        val a1 = item("/s/Pics/a.jpg", taken = 10, modified = 50)
        val a2 = item("/s/Pics/b.mp4", taken = 30, modified = 20, video = true)
        val a3 = item("/s/Pics/c.jpg", taken = 20, modified = 30)
        val b1 = item("/s/Other/x.jpg", taken = 5)
        val lib = buildGalleryLibrary(listOf(a1, b1, a2, a3))

        assertEquals(listOf(a2, a3, a1), lib.itemsByFolder.getValue("/s/Pics"))
        val pics = lib.albums.single { it.path == "/s/Pics" }
        assertEquals("Pics", pics.name)
        assertEquals(3, pics.itemCount)
        assertEquals(1, pics.videoCount)
        assertEquals(a2.contentUri, pics.coverContentUri)
        assertTrue(pics.coverIsVideo)
        assertEquals(50L, pics.lastModifiedMs)
        assertEquals(30L, pics.latestTakenMs)
        assertNull(pics.virtual)
        assertEquals("/s/Pics", pics.id)
    }

    @Test
    fun virtualAlbumsMergeAcrossFoldersAndCameraIsPinnedNotListed() {
        val cam1 = item("/storage/emulated/0/DCIM/Camera/IMG_1.jpg", taken = 3)
        val cam2 = item("/storage/1234-ABCD/DCIM/Camera/VID_2.mp4", taken = 4, video = true)
        val vid = item("/s/Movies/m.mkv", taken = 1, video = true)
        val pic = item("/s/Pics/p.jpg", taken = 2)
        val lib = buildGalleryLibrary(listOf(cam1, cam2, vid, pic))

        assertEquals(listOf("Movies", "Pics"), lib.albums.map { it.name }.sorted())
        assertEquals(listOf(VirtualAlbum.ALL_VIDEOS, VirtualAlbum.CAMERA), lib.virtualAlbums.map { it.virtual })
        assertEquals(listOf(cam2, vid), lib.items(VirtualAlbum.ALL_VIDEOS))
        assertEquals(listOf(cam2, cam1), lib.items(VirtualAlbum.CAMERA))
        val camera = lib.virtualAlbums[1]
        assertEquals(2, camera.itemCount)
        assertEquals("virtual:camera", camera.id)
        // Each camera folder can still be opened by path.
        assertEquals(listOf(cam1), lib.itemsByFolder.getValue("/storage/emulated/0/DCIM/Camera"))
    }

    @Test
    fun emptyVirtualAlbumsAreHidden() {
        val lib = buildGalleryLibrary(listOf(item("/s/Pics/p.jpg")))
        assertTrue(lib.virtualAlbums.isEmpty())
        assertTrue(lib.items(VirtualAlbum.CAMERA).isEmpty())
        assertSame(GalleryLibrary.EMPTY, buildGalleryLibrary(emptyList()))
    }

    @Test
    fun cameraFolderDetection() {
        assertTrue(isCameraFolder("/storage/emulated/0/DCIM/Camera"))
        assertTrue(isCameraFolder("/storage/emulated/0/dcim/camera"))
        assertFalse(isCameraFolder("/storage/emulated/0/DCIM/Screenshots"))
        assertFalse(isCameraFolder("/storage/emulated/0/DCIM/Camera/Raw"))
    }

    @Test
    fun newestFirstTieBreaksAreTotal() {
        val older = item("/s/a/1.jpg", taken = 5, modified = 1)
        val newerMod = item("/s/a/2.jpg", taken = 5, modified = 2)
        val sameAll = item("/s/a/3.jpg", taken = 5, modified = 2) // higher id
        assertEquals(listOf(sameAll, newerMod, older), listOf(older, newerMod, sameAll).sortedWith(NewestFirst))
    }

    @Test
    fun itemSorts() {
        val a = item("/s/x/Img 10.jpg", taken = 1, size = 300)
        val b = item("/s/x/img 2.jpg", taken = 3, size = 100)
        val c = item("/s/x/Img 1.jpg", taken = 2, size = 200)
        val newest = listOf(b, c, a)

        assertSame(newest, sortItems(newest, MediaSort.DEFAULT))
        assertEquals(listOf(a, c, b), sortItems(newest, MediaSort(MediaSortField.DATE_TAKEN, descending = false)))
        assertEquals(listOf(c, b, a), sortItems(newest, MediaSort(MediaSortField.NAME, descending = false)))
        assertEquals(listOf(a, b, c), sortItems(newest, MediaSort(MediaSortField.NAME, descending = true)))
        assertEquals(listOf(a, c, b), sortItems(newest, MediaSort(MediaSortField.SIZE, descending = true)))
        assertEquals(listOf(b, c, a), sortItems(newest, MediaSort(MediaSortField.SIZE, descending = false)))
    }

    @Test
    fun albumSorts() {
        val lib = buildGalleryLibrary(
            listOf(
                item("/s/b/1.jpg", modified = 30),
                item("/s/A/1.jpg", modified = 10),
                item("/s/A/2.jpg", modified = 11),
                item("/s/A/3.jpg", modified = 12),
                item("/s/c 10/1.jpg", modified = 20),
                item("/s/c 2/1.jpg", modified = 20),
                item("/s/c 2/2.jpg", modified = 5),
            ),
        )
        fun names(sort: AlbumSort) = sortAlbums(lib.albums, sort).map { it.name }

        assertEquals(listOf("b", "c 2", "c 10", "A"), names(AlbumSort.DEFAULT))
        assertEquals(listOf("A", "c 10", "c 2", "b"), names(AlbumSort(AlbumSortField.DATE_MODIFIED, descending = false)))
        assertEquals(listOf("A", "b", "c 2", "c 10"), names(AlbumSort(AlbumSortField.NAME, descending = false)))
        assertEquals(listOf("c 10", "c 2", "b", "A"), names(AlbumSort(AlbumSortField.NAME, descending = true)))
        assertEquals(listOf("A", "c 2", "b", "c 10"), names(AlbumSort(AlbumSortField.COUNT, descending = true)))
        assertEquals(AlbumSort(AlbumSortField.NAME, descending = false), AlbumSort.defaultFor(AlbumSortField.NAME))
    }

    @Test
    fun albumSizeSort() {
        val lib = buildGalleryLibrary(
            listOf(
                item("/s/few big/1.mp4", size = 5_000),
                item("/s/many small/1.jpg", size = 10),
                item("/s/many small/2.jpg", size = 10),
                item("/s/many small/3.jpg", size = 10),
                item("/s/mid/1.jpg", size = 400),
            ),
        )
        assertEquals(5_000L, lib.albums.first { it.name == "few big" }.totalSizeBytes)
        val bigFirst = sortAlbums(lib.albums, AlbumSort.defaultFor(AlbumSortField.SIZE)).map { it.name }
        assertEquals(listOf("few big", "mid", "many small"), bigFirst)
        val smallFirst = sortAlbums(lib.albums, AlbumSort(AlbumSortField.SIZE, descending = false)).map { it.name }
        assertEquals(listOf("many small", "mid", "few big"), smallFirst)
    }

    @Test
    fun fiftyThousandItemsGroupQuickly() {
        val items = List(50_000) { i -> item("/s/folder${i % 400}/IMG_$i.jpg", taken = (i * 7919L) % 100_000, video = i % 10 == 0) }
        buildGalleryLibrary(items) // warm up
        val start = System.nanoTime()
        val lib = buildGalleryLibrary(items)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(400, lib.albums.size)
        assertEquals(5_000, lib.items(VirtualAlbum.ALL_VIDEOS).size)
        assertTrue("grouping took $ms ms", ms < 1_000)
    }
}
