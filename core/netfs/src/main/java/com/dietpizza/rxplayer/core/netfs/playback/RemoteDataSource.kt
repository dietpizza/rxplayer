package com.dietpizza.rxplayer.core.netfs.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import com.dietpizza.rxplayer.core.netfs.NetFsPool
import com.dietpizza.rxplayer.core.netfs.NetKey
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.fs.NetFsErrors
import com.dietpizza.rxplayer.core.netfs.fs.RemoteRandomAccess
import com.dietpizza.rxplayer.core.player.SchemeDataSourceFactory
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media3 [DataSource] for every network scheme ([NetKey.SCHEMES]), over the protocol-neutral
 * [RemoteRandomAccess]. The server comes from the key's id; the handle comes from [NetFsPool], so a
 * seek (close + open at a new position) reuses the open file and connection.
 */
@UnstableApi
class RemoteDataSource(private val pool: NetFsPool) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var key: NetKey? = null
    private var handle: RemoteRandomAccess? = null
    private var reader: RemoteReader? = null
    private var bytesRemaining = 0L
    private var opened = false
    private var healthy = true

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        // Raw key string: paths are not URL-encoded (see NetKey), so never use uri.path here.
        val k = NetKey.parse(dataSpec.uri.toString())
            ?: throw DataSourceException(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        key = k
        val h = try {
            pool.acquireHandle(k.serverId, k.path)
        } catch (e: IOException) {
            throw DataSourceException(e, errorCode(e))
        } catch (e: RuntimeException) {
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        handle = h
        healthy = true

        val total = h.length
        val position = dataSpec.position
        if (total >= 0 && position > total) {
            throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        }
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            total >= 0 -> total - position
            else -> C.LENGTH_UNSET.toLong()
        }
        val end = if (bytesRemaining == C.LENGTH_UNSET.toLong()) -1L else position + bytesRemaining
        reader = RemoteReader(h, position, end)
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val r = reader ?: throw IllegalStateException("Not opened")
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), bytesRemaining).toInt()
        val n = try {
            r.read(buffer, offset, toRead)
        } catch (e: IOException) {
            healthy = false
            throw DataSourceException(e, errorCode(e))
        } catch (e: RuntimeException) {
            healthy = false
            throw DataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED)
        }
        if (n < 0) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                healthy = false
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        reader = null
        val h = handle
        val k = key
        handle = null
        key = null
        if (h != null && k != null) pool.releaseHandle(k.serverId, k.path, h, reusable = healthy)
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun errorCode(e: Throwable): Int = when (NetFsErrors.classify(e)) {
        NetErrorKind.NOT_FOUND -> PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
        NetErrorKind.AUTH, NetErrorKind.PERMISSION, NetErrorKind.HOST_KEY_CHANGED -> PlaybackException.ERROR_CODE_IO_NO_PERMISSION
        NetErrorKind.TIMEOUT -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
        NetErrorKind.UNKNOWN_HOST, NetErrorKind.UNREACHABLE, NetErrorKind.TLS -> PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        else -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }
}

/**
 * Plugs [RemoteDataSource] into playback for `smb`, `sftp`, `ftp`, `ftps`, `webdav` (and the
 * `webdavs` alias). Contributed to the player's multibinding in `NetFsModule`.
 */
@UnstableApi
@Singleton
class NetFsSchemeDataSourceFactory @Inject constructor(private val pool: NetFsPool) : SchemeDataSourceFactory {
    override val schemes: Set<String> = NetKey.SCHEMES

    override fun createDataSourceFactory(): DataSource.Factory = DataSource.Factory { RemoteDataSource(pool) }
}
