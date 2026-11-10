package com.dietpizza.rxplayer.core.media

import com.dietpizza.rxplayer.core.model.CopyProgress
import com.dietpizza.rxplayer.core.model.FileOpError
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FileSystemOperationsTest {

    private lateinit var root: File
    private val scanned = ArrayList<String>()
    private val ops = FileSystemOperations({ scanned += it }, Dispatchers.Unconfined)

    @Before
    fun setUp() {
        root = Files.createTempDirectory("rx-fileops").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun file(rel: String, bytes: Int = 16): File =
        File(root, rel).apply {
            parentFile!!.mkdirs()
            writeBytes(ByteArray(bytes) { (it % 251).toByte() })
        }

    @Test
    fun renameMovesFileAndScansBothPaths() = runBlocking {
        val src = file("a/clip.mp4")
        val r = ops.rename(src.path, "renamed.mp4")
        assertTrue(r.isSuccess)
        assertEquals(File(root, "a/renamed.mp4").path, r.targetPath)
        assertFalse(src.exists())
        assertTrue(File(r.targetPath!!).exists())
        assertEquals(listOf(src.path, r.targetPath), scanned)
    }

    @Test
    fun renameRejectsClashesAndBadNames() = runBlocking {
        val src = file("a/clip.mp4")
        file("a/other.mp4")
        assertEquals(FileOpError.TARGET_EXISTS, ops.rename(src.path, "other.mp4").error)
        assertEquals(FileOpError.INVALID_NAME, ops.rename(src.path, "x/y.mp4").error)
        assertEquals(FileOpError.INVALID_NAME, ops.rename(src.path, "  ").error)
        assertEquals(FileOpError.INVALID_NAME, ops.rename(src.path, "..").error)
        assertEquals(FileOpError.NOT_FOUND, ops.rename(File(root, "nope.mp4").path, "x.mp4").error)
        assertTrue(src.exists())
        assertTrue(scanned.isEmpty())
    }

    @Test
    fun renameToSameNameIsANoOp() = runBlocking {
        val src = file("a/clip.mp4")
        val r = ops.rename(src.path, "clip.mp4")
        assertTrue(r.isSuccess)
        assertEquals(src.path, r.targetPath)
        assertTrue(src.exists())
    }

    @Test
    fun moveRenamesIntoTargetAndAvoidsClashes() = runBlocking {
        val a = file("a/clip.mp4")
        val b = file("b/clip.mp4")
        val dest = File(root, "dest").apply { mkdirs() }
        File(dest, "clip.mp4").writeText("existing")

        val results = ops.move(listOf(a.path, b.path), dest.path)

        assertEquals(listOf(File(dest, "clip (1).mp4").path, File(dest, "clip (2).mp4").path), results.map { it.targetPath })
        assertFalse(a.exists())
        assertFalse(b.exists())
        assertEquals("existing", File(dest, "clip.mp4").readText())
        assertTrue(scanned.containsAll(listOf(a.path, b.path) + results.map { it.targetPath!! }))
    }

    @Test
    fun moveCreatesMissingTargetDirAndReportsMissingSources() = runBlocking {
        val a = file("a/clip.mp4")
        val missing = File(root, "a/gone.mp4").path
        val results = ops.move(listOf(a.path, missing), File(root, "new/dir").path)
        assertTrue(results[0].isSuccess)
        assertEquals(FileOpError.NOT_FOUND, results[1].error)
        assertTrue(File(root, "new/dir/clip.mp4").exists())
    }

    @Test
    fun moveIntoOwnFolderIsANoOp() = runBlocking {
        val a = file("a/clip.mp4")
        val r = ops.move(listOf(a.path), a.parent).single()
        assertTrue(r.isSuccess)
        assertEquals(a.path, r.targetPath)
        assertTrue(a.exists())
    }

    @Test
    fun copyKeepsSourceCopiesBytesAndReportsProgress() = runBlocking {
        val a = file("a/clip.mp4", bytes = 3 * 1024 * 1024 + 7)
        val b = file("a/small.mp4", bytes = 10)
        val progress = ArrayList<CopyProgress>()

        val results = ops.copy(listOf(a.path, b.path), a.parent) { progress += it }

        assertTrue(results.all { it.isSuccess })
        assertEquals(File(root, "a/clip (1).mp4").path, results[0].targetPath)
        assertArrayEquals(a.readBytes(), File(results[0].targetPath!!).readBytes())
        assertTrue(a.exists())
        val last = progress.last()
        assertEquals(a.length() + b.length(), last.totalBytes)
        assertEquals(last.totalBytes, last.totalBytesCopied)
        assertEquals(1, last.itemIndex)
        assertTrue(progress.zipWithNext().all { (x, y) -> y.totalBytesCopied >= x.totalBytesCopied })
        // A copy scans only the new files.
        assertFalse(scanned.contains(a.path))
    }

    @Test
    fun cancelledCopyRemovesPartialFileAndKeepsFinishedOnes() = runBlocking {
        val small = file("a/small.mp4", bytes = 10)
        val big = file("a/big.mp4", bytes = 4 * 1024 * 1024)
        val dest = File(root, "dest")
        var threw = false
        val job = launch(Dispatchers.Unconfined) {
            val self = coroutineContext[Job]!!
            try {
                ops.copy(listOf(small.path, big.path), dest.path) { p ->
                    if (p.itemIndex == 1 && p.totalBytesCopied > small.length()) self.cancel()
                }
            } catch (e: CancellationException) {
                threw = true
            }
        }
        job.join()
        assertTrue(threw)
        assertTrue(File(dest, "small.mp4").exists())
        assertFalse(File(dest, "big.mp4").exists())
        assertTrue(scanned.contains(File(dest, "small.mp4").path))
    }

    @Test
    fun deleteRemovesFilesAndScansThem() = runBlocking {
        val a = file("a/clip.mp4")
        val results = ops.delete(listOf(a.path, File(root, "nope").path))
        assertTrue(results[0].isSuccess)
        assertNull(results[0].targetPath)
        assertEquals(FileOpError.NOT_FOUND, results[1].error)
        assertFalse(a.exists())
        assertEquals(listOf(a.path), scanned)
    }

    @Test
    fun createDirectory() = runBlocking {
        val r = ops.createDirectory(root.path, "New")
        assertTrue(r.isSuccess)
        assertTrue(File(root, "New").isDirectory)
        assertTrue(ops.createDirectory(root.path, "New").isSuccess) // Existing dir is fine.
        file("f")
        assertEquals(FileOpError.TARGET_EXISTS, ops.createDirectory(root.path, "f").error)
        assertEquals(FileOpError.INVALID_NAME, ops.createDirectory(root.path, "a/b").error)
    }

    @Test
    fun freeTargetNumbersBeforeExtension() {
        File(root, "x.tar.mkv").writeText("")
        File(root, "noext").writeText("")
        assertEquals("x.tar (1).mkv", FileSystemOperations.freeTarget(root, "x.tar.mkv").name)
        assertEquals("noext (1)", FileSystemOperations.freeTarget(root, "noext").name)
        assertEquals("fresh.mp4", FileSystemOperations.freeTarget(root, "fresh.mp4").name)
    }

    @Test
    fun renameDirectoryMovesTreeAndReportsEveryFile() = runBlocking {
        val a = file("Movies/Show/ep1.mp4")
        val b = file("Movies/Show/Extras/bonus.mkv")
        var moved: Map<String, String> = emptyMap()
        val r = ops.renameDirectory(File(root, "Movies/Show").path, "Series") { moved = it }
        assertTrue(r.isSuccess)
        assertEquals(File(root, "Movies/Series").path, r.targetPath)
        assertFalse(File(root, "Movies/Show").exists())
        assertEquals(
            mapOf(
                a.path to File(root, "Movies/Series/ep1.mp4").path,
                b.path to File(root, "Movies/Series/Extras/bonus.mkv").path,
            ),
            moved,
        )
        assertEquals((moved.keys + moved.values).toSet(), scanned.toSet())
    }

    @Test
    fun renameDirectoryRejectsClashesAndBadNames() = runBlocking {
        file("A/x.mp4")
        file("B/y.mp4")
        assertEquals(FileOpError.TARGET_EXISTS, ops.renameDirectory(File(root, "A").path, "B").error)
        assertEquals(FileOpError.INVALID_NAME, ops.renameDirectory(File(root, "A").path, "a/b").error)
        assertEquals(FileOpError.NOT_FOUND, ops.renameDirectory(File(root, "C").path, "D").error)
        assertTrue(File(root, "A/x.mp4").exists())
    }

    @Test
    fun removeDirectoryIfEmptyKeepsFoldersWithOtherFiles() = runBlocking {
        file("Full/notes.txt")
        File(root, "Empty").mkdirs()
        assertFalse(ops.removeDirectoryIfEmpty(File(root, "Full").path))
        assertTrue(File(root, "Full/notes.txt").exists())
        assertTrue(ops.removeDirectoryIfEmpty(File(root, "Empty").path))
        assertFalse(File(root, "Empty").exists())
    }
}
