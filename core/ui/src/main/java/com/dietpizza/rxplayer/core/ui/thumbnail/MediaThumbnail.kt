package com.dietpizza.rxplayer.core.ui.thumbnail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

/**
 * A MediaStore thumbnail (video frame or photo) for [contentUri], over a `surfaceContainerHigh`
 * placeholder. The caller sizes and clips it through [modifier] (e.g. `aspectRatio(16f / 9f)`
 * + `clip(shape)`).
 *
 * [requestWidth] is roughly how wide the thumbnail is drawn. It's rounded up to a small set of
 * pixel buckets so the same item shown in a list and a grid usually shares one memory-cache entry,
 * and the request is square so portrait and landscape media both come back sharp after cropping.
 */
@Composable
fun MediaThumbnail(
    contentUri: String?,
    modifier: Modifier = Modifier,
    requestWidth: Dp = 160.dp,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val placeholder = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    if (contentUri == null || LocalInspectionMode.current) {
        Box(placeholder)
        return
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { requestWidth.roundToPx() }.toBucket()
    val request = remember(contentUri, px) {
        ImageRequest.Builder(context)
            .data(MediaStoreThumbnail(contentUri))
            .size(px, px)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        imageLoader = rememberRxImageLoader(),
        modifier = placeholder,
        contentScale = contentScale,
    )
}

private val Buckets = intArrayOf(192, 256, 384, 512, 768, 1024)

private fun Int.toBucket(): Int {
    for (b in Buckets) if (this <= b) return b
    return Buckets.last()
}
