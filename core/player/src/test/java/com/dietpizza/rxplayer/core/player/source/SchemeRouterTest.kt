package com.dietpizza.rxplayer.core.player.source

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import com.dietpizza.rxplayer.core.player.MediaKeys
import com.dietpizza.rxplayer.core.player.SchemeDataSourceFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(UnstableApi::class)
class SchemeRouterTest {
    private class Fake(override val schemes: Set<String>) : SchemeDataSourceFactory {
        var created = 0
        val factory = DataSource.Factory { throw UnsupportedOperationException() }
        override fun createDataSourceFactory(): DataSource.Factory {
            created++
            return factory
        }
    }

    @Test
    fun routesContributedSchemesCaseInsensitively() {
        val smb = Fake(setOf("smb"))
        val dav = Fake(setOf("webdav", "webdavs"))
        val router = SchemeRouter(setOf(smb, dav))
        assertSame(smb.factory, router.factoryFor("smb"))
        assertSame(smb.factory, router.factoryFor("SMB"))
        assertSame(dav.factory, router.factoryFor("webdavs"))
        assertEquals(1, smb.created) // created once, then reused
        assertEquals(setOf("smb", "webdav", "webdavs"), router.schemes)
    }

    @Test
    fun everythingElseFallsBackToDefault() {
        val router = SchemeRouter(setOf(Fake(setOf("smb"))))
        assertNull(router.factoryFor("file"))
        assertNull(router.factoryFor("content"))
        assertNull(router.factoryFor("http"))
        assertNull(router.factoryFor(null))
        assertNull(SchemeRouter(emptySet()).factoryFor("smb"))
    }

    @Test(expected = IllegalStateException::class)
    fun duplicateClaimsFailFast() {
        SchemeRouter(setOf(Fake(setOf("smb")), Fake(setOf("SMB"))))
    }

    @Test
    fun keySchemeAndTitles() {
        assertEquals("smb", MediaKeys.scheme("smb://3/Movies/x.mkv"))
        assertEquals("sftp", MediaKeys.scheme("SFTP://1/a"))
        assertEquals("file", MediaKeys.scheme("file:///sdcard/a.mp4"))
        assertNull(MediaKeys.scheme("/sdcard/a.mp4"))
        // Raw network paths keep '#', '?', '%' and spaces.
        assertEquals("My film #2", MediaKeys.fallbackTitle("smb://3/Movies/My film #2.mkv", rawPath = true))
        assertEquals("100% real? yes", MediaKeys.fallbackTitle("ftp://2/x/100% real? yes.mp4", rawPath = true))
        // Encoded URIs are decoded, query/fragment dropped.
        assertEquals("My Film", MediaKeys.fallbackTitle("http://h/v/My%20Film.mkv?token=1", rawPath = false))
        assertEquals("a+b", MediaKeys.fallbackTitle("content://x/y/a+b.mp4", rawPath = false))
    }
}
