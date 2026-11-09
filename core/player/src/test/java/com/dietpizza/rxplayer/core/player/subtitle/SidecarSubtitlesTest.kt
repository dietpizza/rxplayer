package com.dietpizza.rxplayer.core.player.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SidecarSubtitlesTest {
    @Test
    fun matchesSameNameAndLanguageTags() {
        val siblings = listOf(
            "Movie.mkv", "Movie.en.srt", "Movie.srt", "movie.ASS", "Movie.pt-BR.vtt", "Movie.forced.srt",
            "Movie 2.srt", "Other.srt", "Movie.nfo", "Movie.eng.ssa",
        )
        val m = SidecarSubtitles.match("Movie.mkv", siblings)
        assertEquals(
            listOf("Movie.srt", "movie.ASS", "Movie.en.srt", "Movie.eng.ssa", "Movie.pt-BR.vtt"),
            m.map { it.fileName },
        )
        assertNull(m[0].language)
        assertEquals("en", m[2].language)
        assertEquals("pt-BR", m[4].language)
        assertEquals(SidecarSubtitles.MIME_SSA, m[1].mimeType)
        assertEquals(SidecarSubtitles.MIME_VTT, m[4].mimeType)
    }

    @Test
    fun handlesDotsInVideoNames() {
        val m = SidecarSubtitles.match("Show.S01E02.1080p.mkv", listOf("Show.S01E02.1080p.srt", "Show.S01E02.srt"))
        assertEquals(listOf("Show.S01E02.1080p.srt"), m.map { it.fileName })
    }

    @Test
    fun noMatchesAndMimeLookup() {
        assertEquals(emptyList<SidecarMatch>(), SidecarSubtitles.match("a.mp4", listOf("b.srt", "a.txt")))
        assertEquals(SidecarSubtitles.MIME_SUBRIP, SidecarSubtitles.mimeTypeFor("x.SRT"))
        assertNull(SidecarSubtitles.mimeTypeFor("x.sub"))
    }
}
