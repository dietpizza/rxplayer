package com.dietpizza.rxplayer.core.netfs.fs

import com.hierynomus.mssmb2.SMBApiException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.userauth.UserAuthException

/** What went wrong, coarse enough for a UI message. */
enum class NetErrorKind { AUTH, NOT_FOUND, PERMISSION, HOST_KEY_CHANGED, UNKNOWN_HOST, UNREACHABLE, TIMEOUT, TLS, CONFIG, OTHER }

/** A failure we classified ourselves (wrong config, server said no, ...). */
open class NetFsException(val kind: NetErrorKind, message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * The SFTP server presented a different host key than the one trusted on first use. Connecting is
 * refused until the user explicitly trusts [actual] (ServerActivity).
 */
class HostKeyMismatchException(val expected: String, val actual: String) :
    NetFsException(NetErrorKind.HOST_KEY_CHANGED, "Host key changed: expected $expected, got $actual")

object NetFsErrors {

    fun classify(t: Throwable): NetErrorKind {
        var e: Throwable? = t
        var depth = 0
        while (e != null && depth++ < 8) {
            kindOf(e)?.let { return it }
            e = e.cause
        }
        return NetErrorKind.OTHER
    }

    /** The most specific message in the cause chain, for an error detail line. */
    fun message(t: Throwable): String {
        var e: Throwable? = t
        var best: String? = null
        var depth = 0
        while (e != null && depth++ < 8) {
            if (!e.message.isNullOrBlank()) best = e.message
            if (kindOf(e) != null) break
            e = e.cause
        }
        return best ?: t.javaClass.simpleName
    }

    private fun kindOf(e: Throwable): NetErrorKind? = when (e) {
        is NetFsException -> e.kind
        is UnknownHostException -> NetErrorKind.UNKNOWN_HOST
        is SocketTimeoutException, is TimeoutException -> NetErrorKind.TIMEOUT
        is ConnectException, is NoRouteToHostException -> NetErrorKind.UNREACHABLE
        is SSLException -> NetErrorKind.TLS
        is UserAuthException -> NetErrorKind.AUTH
        is FileNotFoundException -> NetErrorKind.NOT_FOUND
        is SFTPException -> when (e.statusCode) {
            Response.StatusCode.NO_SUCH_FILE, Response.StatusCode.NO_SUCH_PATH -> NetErrorKind.NOT_FOUND
            Response.StatusCode.PERMISSION_DENIED -> NetErrorKind.PERMISSION
            else -> null
        }
        is SMBApiException -> smbKind(e)
        else -> null
    }

    private fun smbKind(e: SMBApiException): NetErrorKind? = when (e.status?.name) {
        "STATUS_LOGON_FAILURE", "STATUS_ACCOUNT_DISABLED", "STATUS_ACCOUNT_RESTRICTION",
        "STATUS_PASSWORD_EXPIRED", "STATUS_WRONG_PASSWORD", "STATUS_NO_SUCH_USER",
        "STATUS_ACCOUNT_LOCKED_OUT", "STATUS_LOGON_TYPE_NOT_GRANTED",
        -> NetErrorKind.AUTH
        "STATUS_OBJECT_NAME_NOT_FOUND", "STATUS_OBJECT_PATH_NOT_FOUND", "STATUS_BAD_NETWORK_NAME",
        "STATUS_NO_SUCH_FILE", "STATUS_NOT_FOUND",
        -> NetErrorKind.NOT_FOUND
        "STATUS_ACCESS_DENIED", "STATUS_SHARING_VIOLATION", "STATUS_CANNOT_DELETE" -> NetErrorKind.PERMISSION
        else -> null
    }
}
