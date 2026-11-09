package com.dietpizza.rxplayer.feature.network

import android.content.Context
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.dietpizza.rxplayer.core.netfs.fs.NetErrorKind
import com.dietpizza.rxplayer.core.netfs.model.MediaType
import com.dietpizza.rxplayer.core.netfs.model.NetProtocol
import kotlinx.coroutines.delay

internal fun NetProtocol.icon(): ImageVector = when (this) {
    NetProtocol.SMB -> Icons.Rounded.Computer
    NetProtocol.SFTP -> Icons.Rounded.Terminal
    NetProtocol.FTP, NetProtocol.FTPS -> Icons.Rounded.SwapVert
    NetProtocol.WEBDAV -> Icons.Rounded.Cloud
}

internal fun MediaType.icon(): ImageVector = when (this) {
    MediaType.FOLDER -> Icons.Rounded.Folder
    MediaType.VIDEO -> Icons.Rounded.Movie
    MediaType.IMAGE -> Icons.Rounded.Image
    MediaType.AUDIO -> Icons.Rounded.AudioFile
    MediaType.SUBTITLE -> Icons.Rounded.Subtitles
    MediaType.OTHER -> Icons.AutoMirrored.Rounded.InsertDriveFile
}

@StringRes
internal fun NetErrorKind.titleRes(): Int = when (this) {
    NetErrorKind.AUTH -> R.string.error_auth
    NetErrorKind.NOT_FOUND -> R.string.error_not_found
    NetErrorKind.PERMISSION -> R.string.error_permission
    NetErrorKind.HOST_KEY_CHANGED -> R.string.error_host_key
    NetErrorKind.UNKNOWN_HOST -> R.string.error_unknown_host
    NetErrorKind.UNREACHABLE -> R.string.error_unreachable
    NetErrorKind.TIMEOUT -> R.string.error_timeout
    NetErrorKind.TLS -> R.string.error_tls
    NetErrorKind.CONFIG -> R.string.error_config
    NetErrorKind.OTHER -> R.string.error_other
}

/** Icon in a tonal circle, the leading element of server and file rows. */
@Composable
internal fun LeadingIcon(icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(40.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
    }
}

@Composable
internal fun ProtocolIcon(protocol: NetProtocol, modifier: Modifier = Modifier) = LeadingIcon(
    icon = protocol.icon(),
    container = MaterialTheme.colorScheme.secondaryContainer,
    content = MaterialTheme.colorScheme.onSecondaryContainer,
    modifier = modifier,
)

internal fun formatSize(context: Context, bytes: Long): String? =
    if (bytes < 0) null else Formatter.formatShortFileSize(context, bytes)

internal fun formatDate(context: Context, epochMs: Long): String? =
    if (epochMs <= 0) {
        null
    } else {
        DateUtils.formatDateTime(
            context,
            epochMs,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_YEAR,
        )
    }

internal fun formatRelative(epochMs: Long): String =
    DateUtils.getRelativeTimeSpanString(epochMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

/** A loading indicator that only appears if loading takes noticeably long (no one-frame flash). */
@Composable
internal fun DelayedLoadingIndicator(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (visible) LoadingIndicator()
    }
}

/** Scaffold padding + shell padding + the list's gutter, combined once. */
@Composable
internal fun listPadding(
    innerPadding: PaddingValues,
    extra: PaddingValues = PaddingValues(),
    gutter: Dp = 16.dp,
    top: Dp = 8.dp,
    bottom: Dp = 24.dp,
): PaddingValues {
    val dir = LocalLayoutDirection.current
    return PaddingValues(
        start = innerPadding.calculateStartPadding(dir) + extra.calculateStartPadding(dir) + gutter,
        end = innerPadding.calculateEndPadding(dir) + extra.calculateEndPadding(dir) + gutter,
        top = innerPadding.calculateTopPadding() + extra.calculateTopPadding() + top,
        bottom = innerPadding.calculateBottomPadding() + extra.calculateBottomPadding() + bottom,
    )
}
