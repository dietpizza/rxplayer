package com.dietpizza.rxplayer.core.player.source

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.dietpizza.rxplayer.core.player.SchemeDataSourceFactory
import java.util.Locale

/**
 * Scheme → [DataSource.Factory] table built from the injected [SchemeDataSourceFactory] set
 * (plan decision 37). Pure: unit tested without Android.
 */
@UnstableApi
class SchemeRouter(contributors: Set<SchemeDataSourceFactory>) {
    private val table: Map<String, SchemeDataSourceFactory> = buildMap {
        for (c in contributors) for (s in c.schemes) {
            val key = s.lowercase(Locale.ROOT)
            check(key !in this) { "Two SchemeDataSourceFactory contributors claim the '$key' scheme" }
            put(key, c)
        }
    }

    /** Lazily created per scheme; DataSource.Factory instances are reusable. */
    private val factories = HashMap<String, DataSource.Factory>()

    val schemes: Set<String> get() = table.keys

    /** The factory for [scheme], or null to use the default (file/content/http/…). */
    @Synchronized
    fun factoryFor(scheme: String?): DataSource.Factory? {
        if (scheme.isNullOrEmpty()) return null
        val key = scheme.lowercase(Locale.ROOT)
        factories[key]?.let { return it }
        val contributor = table[key] ?: return null
        return contributor.createDataSourceFactory().also { factories[key] = it }
    }
}

/**
 * Routes each `open()` to the [SchemeRouter]'s factory for the URI scheme, else to [fallback]
 * (`DefaultDataSource`). One instance per loader, as Media3 expects.
 */
@UnstableApi
class SchemeRoutingDataSourceFactory(
    private val router: SchemeRouter,
    private val fallback: DataSource.Factory,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SchemeRoutingDataSource(router, fallback)
}

@UnstableApi
private class SchemeRoutingDataSource(
    private val router: SchemeRouter,
    private val fallback: DataSource.Factory,
) : DataSource {
    private val listeners = ArrayList<TransferListener>(2)
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        current?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        check(current == null) { "already open" }
        val factory = router.factoryFor(dataSpec.uri.scheme) ?: fallback
        val source = factory.createDataSource()
        for (l in listeners) source.addTransferListener(l)
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current) { "not open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = current ?: return
        current = null
        source.close()
    }
}
