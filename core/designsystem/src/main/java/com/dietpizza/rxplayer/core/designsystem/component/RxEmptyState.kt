package com.dietpizza.rxplayer.core.designsystem.component

import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode
import kotlinx.coroutines.delay

/**
 * Expressive empty state: the icon sits in a large M3 Expressive shape (Cookie 9 ↔ Sunny)
 * that slowly rotates and periodically morphs using the theme's expressive spring.
 */
@Composable
fun RxEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MorphingIconBadge(icon)
            Spacer(Modifier.height(32.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(28.dp))
                action()
            }
        }
    }
}

@Composable
private fun MorphingIconBadge(icon: ImageVector) {
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Sunny) }
    val progress = remember { Animatable(0f) }
    val morphSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(morphSpec) {
        while (true) {
            delay(2_400)
            progress.animateTo(if (progress.targetValue < 0.5f) 1f else 0f, morphSpec)
        }
    }
    val rotation by rememberInfiniteTransition(label = "emptyStateRotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    Box(Modifier.size(152.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotation }
                .drawWithCache {
                    val matrix = Matrix().apply { scale(size.width, size.height) }
                    val path = Path()
                    onDrawBehind {
                        // Reading progress here keeps the morph in the draw phase (no recomposition).
                        path.rewind()
                        morph.toPath(progress = progress.value, path = path)
                        path.transform(matrix)
                        drawPath(path, containerColor)
                    }
                },
        )
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(64.dp),
        )
    }
}

@Preview(name = "Empty state – light", showBackground = true)
@Composable
private fun RxEmptyStateLightPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)) {
        Surface {
            RxEmptyState(
                icon = RxIcons.VideoLibrary,
                title = "No videos yet",
                body = "Videos you record or download will show up here.",
                action = { FilledTonalButton(onClick = {}) { Text("Grant access") } },
            )
        }
    }
}

@Preview(name = "Empty state – dark", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun RxEmptyStateDarkPreview() {
    RxTheme(AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false, pureBlack = true)) {
        Surface {
            RxEmptyState(
                icon = RxIcons.VideoLibrary,
                title = "Nothing here",
                body = "This folder doesn't contain any videos.",
            )
        }
    }
}
