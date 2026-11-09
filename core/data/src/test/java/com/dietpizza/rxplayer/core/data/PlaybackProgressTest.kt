package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.database.PlaybackStateDao
import com.dietpizza.rxplayer.core.database.PlaybackStateEntity
import com.dietpizza.rxplayer.core.model.MediaKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {
    @Test
    fun completedThreshold() {
        assertFalse(isCompleted(949, 1000))
        assertTrue(isCompleted(950, 1000))
        assertTrue(isCompleted(1000, 1000))
        assertFalse(isCompleted(0, 0))
        assertFalse(isCompleted(500, -1))
    }

    @Test
    fun sourceFromKey() {
        assertEquals("LOCAL", sourceOf(MediaKey.forFile("/a.mp4")))
        assertEquals("SMB", sourceOf(MediaKey("smb://3/Movies/x.mkv")))
        assertEquals("CONTENT", sourceOf(MediaKey("content://media/external/video/media/1")))
    }

    @Test
    fun repositoryRules() = runTest {
        val dao = FakeDao()
        var now = 1_000L
        val fp = FileFingerprint(42, 7)
        val repo = RoomPlaybackStateRepository(dao, { now }, { fp })
        val key = MediaKey.forFile("/m/a.mp4")

        repo.saveProgress(key, "a", 100, 1000, countAsPlay = true)
        var row = dao.rows.value.getValue(key.value)
        assertEquals(1, row.playCount)
        assertEquals(1_000L, row.lastPlayedAt)
        assertEquals("LOCAL", row.source)
        assertEquals(42L, row.sizeBytes)
        assertFalse(row.completed)

        now = 2_000L
        repo.saveProgress(key, "a", 960, 1000, countAsPlay = false)
        row = dao.rows.value.getValue(key.value)
        assertEquals(1, row.playCount)
        assertEquals(1_000L, row.lastPlayedAt)
        assertTrue(row.completed)
        assertEquals(960L, row.positionMs)

        now = 3_000L
        repo.saveProgress(key, "a", 10, 1000, countAsPlay = true)
        row = dao.rows.value.getValue(key.value)
        assertEquals(2, row.playCount)
        assertEquals(3_000L, row.lastPlayedAt)
        assertFalse(row.completed)
        assertEquals(10L, repo.get(key)?.positionMs)
    }

    @Test
    fun networkKeysSkipFingerprint() = runTest {
        val dao = FakeDao()
        val repo = RoomPlaybackStateRepository(dao, { 1L }, { error("must not stat network files") })
        val key = MediaKey("smb://3/x.mkv")
        repo.saveProgress(key, "x", 1, 10, countAsPlay = false)
        val row = dao.rows.value.getValue(key.value)
        assertEquals("SMB", row.source)
        assertEquals(0, row.playCount)
        assertEquals(null, row.sizeBytes)
    }

    private class FakeDao : PlaybackStateDao {
        val rows = MutableStateFlow<Map<String, PlaybackStateEntity>>(emptyMap())
        override fun observeAll(): Flow<List<PlaybackStateEntity>> = rows.map { it.values.toList() }
        override suspend fun get(mediaKey: String) = rows.value[mediaKey]
        override fun observe(mediaKey: String): Flow<PlaybackStateEntity?> = rows.map { it[mediaKey] }
        override suspend fun upsert(entity: PlaybackStateEntity) {
            rows.value = rows.value + (entity.mediaKey to entity)
        }
    }
}
