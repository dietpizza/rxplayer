package com.dietpizza.rxplayer.core.data

import com.dietpizza.rxplayer.core.database.PlaybackStateEntity
import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryTest {
    private fun row(key: String, source: String, lastPlayedAt: Long) =
        PlaybackStateEntity(key, key, source, 10, 100, false, 1, lastPlayedAt, null, null)

    private fun video(key: String) = VideoItem(
        key = MediaKey(key), contentUri = "", title = key, path = key.removePrefix("file://"),
        folderPath = "/f", durationMs = 100, sizeBytes = 1, width = 0, height = 0, dateModifiedMs = 0,
        playback = null,
    )

    @Test
    fun keepsOnlyLocalPlayedExistingFilesNewestFirst() {
        val rows = listOf(
            row("file:///f/old.mp4", "LOCAL", 100),
            row("smb://3/movie.mkv", "SMB", 900), // decision 23: network never in History
            row("file:///f/new.mp4", "LOCAL", 500),
            row("file:///f/deleted.mp4", "LOCAL", 700), // no longer in MediaStore
            row("file:///f/cleared.mp4", "LOCAL", 0), // history cleared / never counted as a play
            row("content://x/1", "CONTENT", 800),
        )
        val library = listOf("file:///f/old.mp4", "file:///f/new.mp4", "file:///f/cleared.mp4", "smb://3/movie.mkv")
            .associateWith(::video)

        val history = buildHistory(rows, library)

        assertEquals(listOf("file:///f/new.mp4", "file:///f/old.mp4"), history.map { it.video.key.value })
        assertEquals(listOf(500L, 100L), history.map { it.lastPlayedAt })
    }

    @Test
    fun playlistEntriesKeepOrderAndMarkMissing() {
        val entries = buildPlaylistEntries(
            listOf("file:///f/b.mp4", "file:///f/gone.mp4", "file:///f/a.mp4"),
            listOf("file:///f/a.mp4", "file:///f/b.mp4").associateWith(::video),
        )
        assertEquals(listOf(0, 1, 2), entries.map { it.index })
        assertEquals(listOf(true, false, true), entries.map { it.video != null })
    }
}
