package com.dietpizza.rxplayer.core.netfs

import com.dietpizza.rxplayer.core.netfs.RemotePath.Crumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePathTest {

    @Test fun normalize() {
        assertEquals("/", RemotePath.normalize(""))
        assertEquals("/", RemotePath.normalize("/"))
        assertEquals("/a/b", RemotePath.normalize("a//b/"))
        assertEquals("/a/c", RemotePath.normalize("/a/./b/../c"))
        assertEquals("/", RemotePath.normalize("/../.."))
        assertEquals("/share/dir", RemotePath.normalize("\\share\\dir"))
    }

    @Test fun join() {
        assertEquals("/x.mkv", RemotePath.join("/", "x.mkv"))
        assertEquals("/a/b/x.mkv", RemotePath.join("/a/b/", "x.mkv"))
        assertEquals("/a/My Film #1.mkv", RemotePath.join("/a", "My Film #1.mkv"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `join rejects nested names`() {
        RemotePath.join("/a", "b/c")
    }

    @Test fun `parent and name`() {
        assertNull(RemotePath.parent("/"))
        assertEquals("/", RemotePath.parent("/a"))
        assertEquals("/a", RemotePath.parent("/a/b"))
        assertEquals("b.mkv", RemotePath.name("/a/b.mkv"))
        assertEquals("", RemotePath.name("/"))
    }

    @Test fun isWithin() {
        assertTrue(RemotePath.isWithin("/", "/anything"))
        assertTrue(RemotePath.isWithin("/a", "/a"))
        assertTrue(RemotePath.isWithin("/a", "/a/b"))
        assertFalse(RemotePath.isWithin("/a", "/ab"))
        assertFalse(RemotePath.isWithin("/a/b", "/a"))
    }

    @Test fun validNames() {
        assertTrue(RemotePath.isValidName("x.mkv"))
        assertFalse(RemotePath.isValidName(""))
        assertFalse(RemotePath.isValidName("  "))
        assertFalse(RemotePath.isValidName(".."))
        assertFalse(RemotePath.isValidName("a/b"))
        assertFalse(RemotePath.isValidName("a\\b"))
    }

    @Test fun `breadcrumbs start at the root`() {
        assertEquals(
            listOf(Crumb("NAS", "/Media"), Crumb("TV", "/Media/TV"), Crumb("Show", "/Media/TV/Show")),
            RemotePath.breadcrumbs("/Media", "/Media/TV/Show", "NAS"),
        )
        assertEquals(listOf(Crumb("NAS", "/")), RemotePath.breadcrumbs("/", "/", "NAS"))
        assertEquals(
            listOf(Crumb("NAS", "/"), Crumb("a", "/a")),
            RemotePath.breadcrumbs("/", "/a/", "NAS"),
        )
    }

    @Test fun `breadcrumbs outside the root collapse to the root`() {
        assertEquals(listOf(Crumb("NAS", "/Media")), RemotePath.breadcrumbs("/Media", "/Other/x", "NAS"))
    }
}
