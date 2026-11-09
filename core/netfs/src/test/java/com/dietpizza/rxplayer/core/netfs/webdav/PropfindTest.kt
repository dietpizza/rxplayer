package com.dietpizza.rxplayer.core.netfs.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PropfindTest {

    private val xml = """<?xml version="1.0" encoding="utf-8"?>
<d:multistatus xmlns:d="DAV:">
  <d:response>
    <d:href>/dav/Movies/</d:href>
    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype>
      <d:getlastmodified>Mon, 02 Nov 2026 10:00:00 GMT</d:getlastmodified></d:prop>
      <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
  </d:response>
  <d:response>
    <d:href>http://nas.local/dav/Movies/My%20Film%20%231+.mkv</d:href>
    <d:propstat><d:prop><d:resourcetype/><d:getcontentlength>1234567</d:getcontentlength>
      <d:getlastmodified>Tue, 03 Nov 2026 12:30:00 GMT</d:getlastmodified></d:prop>
      <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
    <d:propstat><d:prop><d:getcontentlength>999</d:getcontentlength></d:prop>
      <d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
  </d:response>
  <D:response xmlns:D="DAV:">
    <D:href>/dav/Movies/Sub/</D:href>
    <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
      <D:status>HTTP/1.1 200 OK</D:status></D:propstat>
  </D:response>
</d:multistatus>"""

    @Test fun `parses collections, files, sizes and dates`() {
        val res = Propfind.parse(xml.byteInputStream())
        assertEquals(3, res.size)
        assertEquals(DavResource("/dav/Movies", true, -1, 1793613600000), res[0])
        assertEquals("/dav/Movies/My Film #1+.mkv", res[1].path)
        assertEquals(false, res[1].isCollection)
        assertEquals(1234567L, res[1].contentLength)
        assertEquals(DavResource("/dav/Movies/Sub", true, -1, 0), res[2])
    }

    @Test fun `content-range totals`() {
        assertEquals(1234L, WebDavFileSystem.parseTotal("bytes 0-99/1234"))
        assertNull(WebDavFileSystem.parseTotal("bytes 0-99/*"))
        assertNull(WebDavFileSystem.parseTotal(null))
    }
}
