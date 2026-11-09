package com.dietpizza.rxplayer.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** STUB — replaced by the designsystem agent. Keep the signature stable. */
@Composable
fun RxEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Icon(icon, contentDescription = null)
        Text(title)
        Text(body)
        action?.invoke()
    }
}
