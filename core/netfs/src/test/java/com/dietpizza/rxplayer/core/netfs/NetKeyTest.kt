package com.dietpizza.rxplayer.core.netfs

import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetKeyTest {

    @Test fun `builds the plan 7_1 format`() {
        assertEquals("smb://3/Movies/x.mkv", NetKey.of(NetProtocol.SMB, 3, "/Movies/x.mkv").value)
        assertEquals("webdav://12/a/b.mp4", NetKey.of(NetProtocol.WEBDAV, 12, "a//b.mp4").value)
        assertEquals("ftps://1/", NetKey.of(NetProtocol.FTPS, 1, "").value)
    }

    @Test fun `parses scheme, id and path`() {
        assertEquals(NetKey("smb", 3, "/Movies/x.mkv"), NetKey.parse("smb://3/Movies/x.mkv"))
        assertEquals(NetKey("sftp", 42, "/"), NetKey.parse("sftp://42"))
        assertEquals(NetKey("ftp", 7, "/"), NetKey.parse("FTP://7/"))
    }

    @Test fun `raw paths keep spaces, hash, question mark and percent`() {
        val path = "/TV/Show #1/Ep ?? 100%.mkv"
        val key = NetKey.of(NetProtocol.SFTP, 5, path)
        assertEquals("sftp://5$path", key.value)
        assertEquals(key, NetKey.parse(key.value))
        assertEquals(path, NetKey.parse(key.value)!!.path)
    }

    @Test fun `webdavs is an alias of webdav`() {
        assertEquals(NetKey("webdav", 2, "/x.mkv"), NetKey.parse("webdavs://2/x.mkv"))
    }

    @Test fun `rejects non-network keys`() {
        assertNull(NetKey.parse("file:///sdcard/x.mkv"))
        assertNull(NetKey.parse("content://media/external/video/1"))
        assertNull(NetKey.parse("http://host/x.mkv"))
        assertNull(NetKey.parse("smb://host/share/x.mkv")) // authority must be a server id
        assertNull(NetKey.parse("smb://0/x"))
        assertNull(NetKey.parse("smb:/3/x"))
    }

    @Test fun `every protocol scheme is routed`() {
        for (p in NetProtocol.entries) assert(p.scheme in NetKey.SCHEMES)
    }
}
