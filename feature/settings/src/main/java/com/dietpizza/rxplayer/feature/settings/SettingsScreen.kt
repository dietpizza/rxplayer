package com.dietpizza.rxplayer.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dietpizza.rxplayer.core.designsystem.theme.RxTheme
import com.dietpizza.rxplayer.core.model.AppearanceSettings
import com.dietpizza.rxplayer.core.model.ThemeMode

@Composable
fun SettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SettingsRoute(onBack = onBack, modifier = modifier)
}

@Composable
internal fun SettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val actions = remember(viewModel) {
        SettingsActions(
            onVideoViewMode = viewModel::setVideoViewMode,
            onVideoSort = viewModel::setVideoSort,
            onGridColumns = viewModel::setGalleryGridColumns,
            onShowHidden = viewModel::setShowHidden,
            onPlayer = viewModel::editPlayer,
            onSubtitles = viewModel::editSubtitles,
            onClearCache = viewModel::clearCache,
            onClearHistory = viewModel::clearHistory,
        )
    }
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onThemeModeChange = viewModel::setThemeMode,
        onDynamicColorChange = viewModel::setDynamicColor,
        onPureBlackChange = viewModel::setPureBlack,
        actions = actions,
        snackbarHostState = snackbar,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    actions: SettingsActions = SettingsActions.None,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val state = uiState as? SettingsUiState.Ready ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "appearance") {
                AppearanceSection(
                    appearance = state.appearance,
                    onThemeModeChange = onThemeModeChange,
                    onDynamicColorChange = onDynamicColorChange,
                    onPureBlackChange = onPureBlackChange,
                )
            }
            item(key = "library") { LibrarySection(state.library, actions) }
            item(key = "player") { PlayerSection(state.player, actions) }
            item(key = "subtitles") { SubtitlesSection(state.subtitles, actions) }
            item(key = "storage") { StorageSection(state.cacheBytes, actions) }
            item(key = "about") { AboutSection(state.appInfo) }
        }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
            .semantics { heading() },
    )
}

/** A rounded "segmented list" group: items separated by a small gap, outer corners rounded. */
@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) { content() }
}

@Composable
private fun AppearanceSection(
    appearance: AppearanceSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPureBlackChange: (Boolean) -> Unit,
) {
    val count = 3
    SectionHeader("Appearance")
    SettingsGroup {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(Icons.Rounded.Contrast, contentDescription = null) },
            supportingContent = {
                ThemeModeButtonGroup(
                    selected = appearance.themeMode,
                    onSelect = onThemeModeChange,
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text("Theme") }

        SegmentedListItem(
            // onClick (not checked/onCheckedChange) so the row itself never takes a "selected" highlight;
            // the Switch alone shows state.
            onClick = { onDynamicColorChange(!appearance.dynamicColor) },
            modifier = Modifier.switchSemantics(appearance.dynamicColor),
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
            leadingContent = { Icon(Icons.Rounded.Palette, contentDescription = null) },
            supportingContent = { Text("Use colours from your wallpaper") },
            trailingContent = { Switch(checked = appearance.dynamicColor, onCheckedChange = null) },
        ) { Text("Dynamic colour") }

        val canBeDark = appearance.themeMode != ThemeMode.LIGHT
        SegmentedListItem(
            onClick = { onPureBlackChange(!appearance.pureBlack) },
            modifier = Modifier.switchSemantics(appearance.pureBlack),
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = count),
            enabled = canBeDark,
            leadingContent = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
            supportingContent = {
                Text(if (canBeDark) "True black backgrounds in dark theme" else "Only applies to the dark theme")
            },
            trailingContent = {
                Switch(checked = appearance.pureBlack, onCheckedChange = null, enabled = canBeDark)
            },
        ) { Text("Pure black") }
    }
}

@Composable
private fun ThemeModeButtonGroup(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = ThemeMode.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, mode ->
            val checked = mode == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(mode) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Icon(
                    imageVector = if (checked) Icons.Rounded.Check else mode.icon,
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(mode.label, maxLines = 1)
            }
        }
    }
}

/** The row is a plain clickable; announce it to TalkBack as a switch with its on/off state. */
internal fun Modifier.switchSemantics(checked: Boolean) = semantics {
    role = Role.Switch
    toggleableState = ToggleableState(checked)
}

private val ThemeMode.label: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

private val ThemeMode.icon
    get() = when (this) {
        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
        ThemeMode.LIGHT -> Icons.Rounded.LightMode
        ThemeMode.DARK -> Icons.Rounded.DarkMode
    }

@Composable
private fun AboutSection(appInfo: AppInfo) {
    SectionHeader("About")
    SettingsGroup {
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
            leadingContent = { Icon(Icons.Rounded.PlayCircle, contentDescription = null) },
            supportingContent = { Text("Gallery and video player") },
        ) { Text(appInfo.appName) }
        SegmentedListItem(
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            leadingContent = { Icon(Icons.Rounded.Info, contentDescription = null) },
            supportingContent = { Text(appInfo.versionName) },
        ) { Text("Version") }
    }
}

private val previewInfo = AppInfo(appName = "Rx Player", versionName = "0.1.0")

@Preview(name = "Settings – light")
@Composable
private fun SettingsLightPreview() {
    val appearance = AppearanceSettings(themeMode = ThemeMode.LIGHT, dynamicColor = false)
    RxTheme(appearance) {
        SettingsContent(SettingsUiState.Ready(appearance, previewInfo), {}, {}, {}, {})
    }
}

@Preview(name = "Settings – dark, pure black")
@Composable
private fun SettingsDarkPreview() {
    val appearance = AppearanceSettings(themeMode = ThemeMode.DARK, dynamicColor = false, pureBlack = true)
    RxTheme(appearance) {
        SettingsContent(SettingsUiState.Ready(appearance, previewInfo), {}, {}, {}, {})
    }
}
