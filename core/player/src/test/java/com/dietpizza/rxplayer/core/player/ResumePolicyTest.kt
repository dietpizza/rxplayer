package com.dietpizza.rxplayer.core.player

import com.dietpizza.rxplayer.core.model.MediaKey
import com.dietpizza.rxplayer.core.model.PlaybackState
import com.dietpizza.rxplayer.core.model.ResumeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePolicyTest {
    private fun state(pos: Long, dur: Long = 3_600_000, completed: Boolean = false) = PlaybackState(
        key = MediaKey.forFile("/sdcard/Movies/a.mkv"),
        title = "a",
        positionMs = pos,
        durationMs = dur,
        completed = completed,
        playCount = 1,
        lastPlayedAt = 0,
    )

    @Test
    fun noStateStartsOver() {
        ResumeMode.entries.forEach { assertEquals(ResumeDecision.StartOver, ResumePolicy.decide(it, null)) }
    }

    @Test
    fun askModeAsks() {
        assertEquals(ResumeDecision.Ask(751_000), ResumePolicy.decide(ResumeMode.ASK, state(751_000)))
    }

    @Test
    fun autoModeResumes() {
        assertEquals(ResumeDecision.Resume(751_000), ResumePolicy.decide(ResumeMode.AUTO, state(751_000)))
    }

    @Test
    fun neverModeStartsOver() {
        assertEquals(ResumeDecision.StartOver, ResumePolicy.decide(ResumeMode.NEVER, state(751_000)))
    }

    @Test
    fun completedStartsOver() {
        ResumeMode.entries.forEach {
            assertEquals(ResumeDecision.StartOver, ResumePolicy.decide(it, state(751_000, completed = true)))
        }
    }

    @Test
    fun tinyProgressStartsOver() {
        assertEquals(ResumeDecision.StartOver, ResumePolicy.decide(ResumeMode.ASK, state(4_999)))
        assertEquals(ResumeDecision.Ask(5_000), ResumePolicy.decide(ResumeMode.ASK, state(5_000)))
    }

    @Test
    fun nearTheEndStartsOver() {
        assertEquals(ResumeDecision.StartOver, ResumePolicy.decide(ResumeMode.AUTO, state(56_000, dur = 60_000)))
        assertEquals(ResumeDecision.Resume(54_000), ResumePolicy.decide(ResumeMode.AUTO, state(54_000, dur = 60_000)))
    }

    @Test
    fun unknownDurationStillResumes() {
        assertEquals(ResumeDecision.Resume(30_000), ResumePolicy.decide(ResumeMode.AUTO, state(30_000, dur = 0)))
    }
}
