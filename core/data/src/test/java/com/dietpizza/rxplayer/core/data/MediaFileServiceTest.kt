package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.media.FileOpResult
import com.dietpizza.rxplayer.core.media.FileSystemOperations
import com.dietpizza.rxplayer.core.model.FileOpError
import com.dietpizza.rxplayer.core.model.MediaKey
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaFileServiceTest {

    /** Fake key store that applies the rewrite rules to in-memory "tables". */
    private class FakeKeyStore : MediaKeyStore {
        val playback = linkedMapOf<String, String>() // key -> payload
        val favourites = linkedSetOf<String>()
        val playlistItems = mutableListOf<String>()
        var transactions = 0

        override suspend fun rewrite(moves: Map<String, String>) {
            if (moves.isEmpty()) return
            transactions++
            for ((from, to) in moves) {
                playback.remove(from)?.let { playback[to] = it }
                if (favourites.remove(from)) favourites += to
                playlistItems.replaceAll { if (it == from) to else it }
            }
        }

        override suspend fun remove(keys: List<String>) {
            if (keys.isEmpty()) return
            transactions++
            keys.forEach { playback.remove(it); favourites.remove(it) }
            playlistItems.removeAll { it in keys }
        }
    }

    private lateinit var root: File
    private val store = FakeKeyStore()
    private val service = DefaultMediaFileService(FileSystemOperations({}, Dispatchers.Unconfined), store)

    @Before
    fun setUp() {
        root = Files.createTempDirectory("rx-mfs").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun video(rel: String): MediaKey {
        val f = File(root, rel).apply { parentFile!!.mkdirs(); writeText(rel) }
        return MediaKey.forFile(f.path)
    }

    @Test
    fun renameRewritesKeyEverywhereInOneTransaction() = runBlocking {
        val key = video("a/clip.mp4")
        store.playback[key.value] = "resume"
        store.favourites += key.value
        store.playlistItems += listOf(key.value, "file:///other.mp4", key.value)

        val r = service.rename(key, "better.mp4")

        val newKey = MediaKey.forFile(File(root, "a/better.mp4").path)
        assertEquals(newKey, r.newKey)
        assertEquals(mapOf(newKey.value to "resume"), store.playback)
        assertEquals(setOf(newKey.value), store.favourites)
        assertEquals(listOf(newKey.value, "file:///other.mp4", newKey.value), store.playlistItems)
        assertEquals(1, store.transactions)
    }

    @Test
    fun failedRenameLeavesRowsAlone() = runBlocking {
        val key = video("a/clip.mp4")
        video("a/taken.mp4")
        store.playback[key.value] = "resume"
        val r = service.rename(key, "taken.mp4")
        assertEquals(FileOpError.TARGET_EXISTS, r.error)
        assertEquals(setOf(key.value), store.playback.keys)
        assertEquals(0, store.transactions)
    }

    @Test
    fun moveRewritesOnlySuccessfulItemsAndKeepsOrder() = runBlocking {
        val a = video("a/one.mp4")
        val missing = MediaKey.forFile(File(root, "a/missing.mp4").path)
        val network = MediaKey("smb://3/x.mkv")
        val b = video("b/two.mp4")
        listOf(a, missing, b).forEach { store.playback[it.value] = it.value }

        val results = service.move(listOf(a, missing, network, b), File(root, "dest").path)

        assertEquals(listOf(true, false, false, true), results.map { it.isSuccess })
        assertEquals(FileOpError.NOT_FOUND, results[1].error)
        assertEquals(FileOpError.UNSUPPORTED, results[2].error)
        val newA = MediaKey.forFile(File(root, "dest/one.mp4").path)
        val newB = MediaKey.forFile(File(root, "dest/two.mp4").path)
        assertEquals(listOf(newA, null, null, newB), results.map { it.newKey })
        assertEquals(setOf(newA.value, missing.value, newB.value), store.playback.keys)
        assertEquals(1, store.transactions)
    }

    @Test
    fun copyDoesNotTouchRows() = runBlocking {
        val a = video("a/one.mp4")
        store.playback[a.value] = "resume"
        val r = service.copy(listOf(a), File(root, "dest").path).single()
        assertTrue(r.isSuccess)
        assertEquals(setOf(a.value), store.playback.keys)
        assertEquals(0, store.transactions)
    }

    @Test
    fun deleteRemovesRowsForDeletedAndAlreadyMissingFiles() = runBlocking {
        val a = video("a/one.mp4")
        val missing = MediaKey.forFile(File(root, "a/missing.mp4").path)
        val kept = "file:///elsewhere.mp4"
        listOf(a.value, missing.value, kept).forEach { store.playback[it] = it }
        store.playlistItems += listOf(a.value, kept)

        val results = service.delete(listOf(a, missing))

        assertTrue(results[0].isSuccess)
        assertEquals(FileOpError.NOT_FOUND, results[1].error)
        assertEquals(setOf(kept), store.playback.keys)
        assertEquals(listOf(kept), store.playlistItems)
        assertEquals(1, store.transactions)
    }

    @Test
    fun keyRewritesSkipsFailuresAndNoOps() {
        val rewrites = keyRewrites(
            listOf(
                FileOpResult.ok("/a/x.mp4", "/b/x.mp4"),
                FileOpResult.ok("/a/same.mp4", "/a/same.mp4"),
                FileOpResult.fail("/a/bad.mp4", FileOpError.IO),
            ),
        )
        assertEquals(mapOf("file:///a/x.mp4" to "file:///b/x.mp4"), rewrites)
    }
}
