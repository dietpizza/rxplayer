package com.dietpizza.rxplayer.core.netfs.webdav

import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** One `<d:response>` of a PROPFIND multistatus. [path] is decoded, without trailing slash. */
internal data class DavResource(
    val path: String,
    val isCollection: Boolean,
    val contentLength: Long,
    val lastModifiedMs: Long,
)

internal object Propfind {

    const val BODY = """<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""

    private const val DAV = "DAV:"

    fun parse(xml: InputStream): List<DavResource> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // No DTDs / external entities from a server we don't control.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            isExpandEntityReferences = false
        }
        val doc = factory.newDocumentBuilder().parse(xml)
        val responses = doc.getElementsByTagNameNS(DAV, "response")
        val out = ArrayList<DavResource>(responses.length)
        for (i in 0 until responses.length) {
            val response = responses.item(i) as Element
            val href = response.firstText("href") ?: continue
            var collection = false
            var length = -1L
            var modified = 0L
            val propstats = response.getElementsByTagNameNS(DAV, "propstat")
            for (j in 0 until propstats.length) {
                val propstat = propstats.item(j) as Element
                val status = propstat.firstText("status")
                if (status != null && !status.contains(" 200")) continue
                if (propstat.getElementsByTagNameNS(DAV, "collection").length > 0) collection = true
                propstat.firstText("getcontentlength")?.trim()?.toLongOrNull()?.let { length = it }
                propstat.firstText("getlastmodified")?.let { modified = parseHttpDate(it) }
            }
            out += DavResource(hrefToPath(href), collection, if (collection) -1 else length, modified)
        }
        return out
    }

    /** `href` may be a full URL or an absolute path, percent-encoded; returns the decoded path. */
    fun hrefToPath(href: String): String {
        val trimmed = href.trim()
        val rawPath = runCatching { URI(trimmed).rawPath }.getOrNull() ?: trimmed.substringBefore('?')
        // Decode %XX only: '+' is a literal plus in a URL path.
        val decoded = URLDecoder.decode(rawPath.replace("+", "%2B"), Charsets.UTF_8)
        val noTrailing = decoded.trimEnd('/')
        return noTrailing.ifEmpty { "/" }
    }

    fun parseHttpDate(value: String): Long =
        runCatching { ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .getOrDefault(0L)

    private fun Element.firstText(localName: String): String? {
        val nodes = getElementsByTagNameNS(DAV, localName)
        return if (nodes.length == 0) null else nodes.item(0).textContent
    }
}
