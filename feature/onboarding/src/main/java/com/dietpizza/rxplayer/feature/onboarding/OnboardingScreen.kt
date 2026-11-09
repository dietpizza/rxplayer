package com.dietpizza.rxplayer.feature.onboarding

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme

/** True when the app may proceed past onboarding. Only all-files access is required. */
fun hasRequiredPermissions(context: Context): Boolean = Environment.isExternalStorageManager()

private fun hasNotificationPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAllFilesAccessSettings() {
    val appSpecific = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:$packageName"),
    )
    try {
        startActivity(appSpecific)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        } catch (_: ActivityNotFoundException) {
            // Nothing else to try; the user can grant it from system Settings.
        }
    }
}

private fun Context.openNotificationSettings() {
    try {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    } catch (_: ActivityNotFoundException) {
    }
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var filesGranted by remember { mutableStateOf(hasRequiredPermissions(context)) }
    var notificationsGranted by remember { mutableStateOf(hasNotificationPermission(context)) }
    var notificationsDenied by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        filesGranted = hasRequiredPermissions(context)
        notificationsGranted = hasNotificationPermission(context)
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
        notificationsDenied = !granted
    }

    OnboardingContent(
        filesGranted = filesGranted,
        notificationsGranted = notificationsGranted,
        notificationsDenied = notificationsDenied,
        onGrantFiles = { context.openAllFilesAccessSettings() },
        onGrantNotifications = {
            // After a denial the system may stop showing the dialog; send the user to Settings.
            if (notificationsDenied) {
                context.openNotificationSettings()
            } else {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onContinue = onFinished,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingContent(
    filesGranted: Boolean,
    notificationsGranted: Boolean,
    notificationsDenied: Boolean,
    onGrantFiles: () -> Unit,
    onGrantNotifications: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val wide = maxWidth > maxHeight && maxWidth >= 560.dp
            val details: @Composable (Modifier) -> Unit = { m ->
                Details(
                    filesGranted = filesGranted,
                    notificationsGranted = notificationsGranted,
                    notificationsDenied = notificationsDenied,
                    onGrantFiles = onGrantFiles,
                    onGrantNotifications = onGrantNotifications,
                    onContinue = onContinue,
                    centered = !wide,
                    modifier = m,
                )
            }
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Box(
                        modifier = Modifier.weight(0.9f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        HeroIllustration(Modifier.fillMaxHeight(0.8f).aspectRatio(1f))
                    }
                    details(
                        Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 24.dp),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(8.dp))
                    HeroIllustration(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .widthIn(max = 320.dp)
                            .aspectRatio(1f),
                    )
                    Spacer(Modifier.height(24.dp))
                    details(Modifier.widthIn(max = 560.dp).fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Details(
    filesGranted: Boolean,
    notificationsGranted: Boolean,
    notificationsDenied: Boolean,
    onGrantFiles: () -> Unit,
    onGrantNotifications: () -> Unit,
    onContinue: () -> Unit,
    centered: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.onboarding_app_name),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
        Spacer(Modifier.height(28.dp))

        PermissionRow(
            icon = Icons.Rounded.FolderOpen,
            iconShape = MaterialShapes.Cookie9Sided,
            title = stringResource(R.string.onboarding_files_title),
            badge = stringResource(R.string.onboarding_required),
            body = stringResource(R.string.onboarding_files_body),
            granted = filesGranted,
            actionLabel = stringResource(R.string.onboarding_allow),
            onAction = onGrantFiles,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            icon = Icons.Rounded.NotificationsActive,
            iconShape = MaterialShapes.Clover4Leaf,
            title = stringResource(R.string.onboarding_notifications_title),
            badge = stringResource(R.string.onboarding_optional),
            body = stringResource(R.string.onboarding_notifications_body),
            granted = notificationsGranted,
            actionLabel = stringResource(if (notificationsDenied) R.string.onboarding_open_settings else R.string.onboarding_allow),
            onAction = onGrantNotifications,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(28.dp))

        val height = ButtonDefaults.MediumContainerHeight
        Button(
            onClick = onContinue,
            enabled = filesGranted,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = Modifier.fillMaxWidth().heightIn(min = height),
        ) {
            Text(stringResource(R.string.onboarding_continue), style = ButtonDefaults.textStyleFor(height))
            Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(height)))
            Icon(
                Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
        }
        AnimatedContent(
            targetState = filesGranted,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "continueHint",
        ) { granted ->
            Text(
                text = stringResource(if (granted) R.string.onboarding_all_set else R.string.onboarding_allow_to_continue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    iconShape: RoundedPolygon,
    title: String,
    badge: String,
    body: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(iconShape.toShape())
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Column(modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedContent(
                targetState = granted,
                transitionSpec = {
                    (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn())
                        .togetherWith(scaleOut() + fadeOut())
                },
                contentAlignment = Alignment.Center,
                label = "grantState",
            ) { isGranted ->
                if (isGranted) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialShapes.Cookie4Sided.toShape())
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.onboarding_granted, title),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                } else {
                    val spoken = stringResource(R.string.onboarding_action_for, actionLabel, title)
                    FilledTonalButton(
                        onClick = onAction,
                        modifier = Modifier.semantics { contentDescription = spoken },
                    ) { Text(actionLabel) }
                }
            }
        }
    }
}

/**
 * Composed hero: a slowly rotating blob that morphs between two expressive shapes, with a
 * play tile and a photo tile layered on top. Pure vector + colour roles, no image assets.
 */
@Composable
private fun HeroIllustration(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst) }
    val transition = rememberInfiniteTransition(label = "hero")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "morph",
    )
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "rotation",
    )
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    val path = remember { Path() }
    val matrix = remember { Matrix() }

    // Purely decorative: hidden from accessibility services.
    BoxWithConstraints(modifier = modifier.clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(Modifier.fillMaxSize()) {
            // Soft halo.
            drawCircle(colors.surfaceContainerHighest, radius = size.minDimension / 2f)
            // Morphing blob, normalised to the unit square; scale to 86% and rotate about centre.
            path.rewind()
            morph.toPath(progress, path)
            val s = size.minDimension * 0.86f
            matrix.reset()
            matrix.translate(center.x, center.y)
            matrix.rotateZ(rotation)
            matrix.translate(-s / 2f, -s / 2f)
            matrix.scale(s, s)
            path.transform(matrix)
            drawPath(path, colors.primaryContainer)
        }
        HeroTile(
            icon = Icons.Rounded.Image,
            shape = MaterialShapes.Slanted,
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
            modifier = Modifier
                .align(Alignment.Center)
                .size(side * 0.34f)
                .graphicsLayer {
                    translationX = -side.toPx() * 0.17f
                    translationY = -side.toPx() * 0.12f + bob * 6.dp.toPx()
                    rotationZ = -10f
                },
        )
        HeroTile(
            icon = Icons.Rounded.PlayArrow,
            shape = MaterialShapes.Cookie4Sided,
            container = colors.primary,
            content = colors.onPrimary,
            modifier = Modifier
                .align(Alignment.Center)
                .size(side * 0.38f)
                .graphicsLayer {
                    translationX = side.toPx() * 0.13f
                    translationY = side.toPx() * 0.10f - bob * 6.dp.toPx()
                    rotationZ = rotation * -0.25f
                },
        )
    }
}

@Composable
private fun HeroTile(
    icon: ImageVector,
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(shape.toShape())
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.fillMaxSize(0.5f),
        )
    }
}

@Preview(name = "Onboarding", showBackground = true, widthDp = 380, heightDp = 820)
@Composable
private fun OnboardingPreview() {
    RxTheme {
        OnboardingContent(
            filesGranted = true,
            notificationsGranted = false,
            notificationsDenied = false,
            onGrantFiles = {},
            onGrantNotifications = {},
            onContinue = {},
        )
    }
}

@Preview(name = "Onboarding landscape", showBackground = true, widthDp = 820, heightDp = 380)
@Composable
private fun OnboardingLandscapePreview() {
    RxTheme {
        OnboardingContent(
            filesGranted = false,
            notificationsGranted = false,
            notificationsDenied = false,
            onGrantFiles = {},
            onGrantNotifications = {},
            onContinue = {},
        )
    }
}
