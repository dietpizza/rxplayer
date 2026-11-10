package com.dietpizza.rxplayer.feature.settings

import com.dietpizza.rxplayer.core.designsystem.component.RxBars
import com.dietpizza.rxplayer.core.designsystem.component.rxHeader
import com.dietpizza.rxplayer.core.designsystem.icon.RxIcons
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
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
    Scaffold(
        modifier = modifier,
        containerColor = RxBars.pageColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(RxIcons.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                colors = RxBars.headerColors(),
                modifier = Modifier.rxHeader(),
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
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_list"),
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
    SectionHeader(stringResource(R.string.settings_section_appearance))
    SettingsGroup {
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = count),
            leadingContent = { Icon(RxIcons.Contrast, contentDescription = null) },
            supportingContent = {
                ThemeModeButtonGroup(
                    selected = appearance.themeMode,
                    onSelect = onThemeModeChange,
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) { Text(stringResource(R.string.settings_theme)) }

        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            // onClick (not checked/onCheckedChange) so the row itself never takes a "selected" highlight;
            // the Switch alone shows state.
            onClick = { onDynamicColorChange(!appearance.dynamicColor) },
            modifier = Modifier.switchSemantics(appearance.dynamicColor),
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = count),
            leadingContent = { Icon(RxIcons.Palette, contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.settings_dynamic_colour_summary)) },
            trailingContent = { Switch(checked = appearance.dynamicColor, onCheckedChange = null) },
        ) { Text(stringResource(R.string.settings_dynamic_colour)) }

        val canBeDark = appearance.themeMode != ThemeMode.LIGHT
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            onClick = { onPureBlackChange(!appearance.pureBlack) },
            modifier = Modifier.switchSemantics(appearance.pureBlack),
            shapes = ListItemDefaults.segmentedShapes(index = 2, count = count),
            enabled = canBeDark,
            leadingContent = { Icon(RxIcons.DarkMode, contentDescription = null) },
            supportingContent = {
                Text(
                    stringResource(
                        if (canBeDark) R.string.settings_pure_black_summary else R.string.settings_pure_black_disabled,
                    ),
                )
            },
            trailingContent = {
                Switch(checked = appearance.pureBlack, onCheckedChange = null, enabled = canBeDark)
            },
        ) { Text(stringResource(R.string.settings_pure_black)) }
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
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(RxBars.ButtonGroupGap),
    ) {
        options.forEachIndexed { index, mode ->
            val checked = mode == selected
            ToggleButton(
                colors = RxBars.toggleButtonColors(),
                checked = checked,
                onCheckedChange = { onSelect(mode) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = RxBars.connectedShapes(index, options.lastIndex),
            ) {
                Icon(
                    imageVector = if (checked) RxIcons.Check else mode.icon,
                    contentDescription = null,
                    modifier = Modifier.size(ToggleButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                Text(stringResource(mode.label), maxLines = 1)
            }
        }
    }
}

/** The row is a plain clickable; announce it to TalkBack as a switch with its on/off state. */
internal fun Modifier.switchSemantics(checked: Boolean) = semantics {
    role = Role.Switch
    toggleableState = ToggleableState(checked)
}

@get:StringRes
private val ThemeMode.label: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

private val ThemeMode.icon
    get() = when (this) {
        ThemeMode.SYSTEM -> RxIcons.BrightnessAuto
        ThemeMode.LIGHT -> RxIcons.LightMode
        ThemeMode.DARK -> RxIcons.DarkMode
    }

@Composable
private fun AboutSection(appInfo: AppInfo) {
    SectionHeader(stringResource(R.string.settings_section_about))
    SettingsGroup {
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 2),
            leadingContent = { Icon(RxIcons.PlayCircle, contentDescription = null) },
            supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
        ) { Text(appInfo.appName) }
        SegmentedListItem(
            colors = RxBars.cardListItemColors(),
            shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
            leadingContent = { Icon(RxIcons.Info, contentDescription = null) },
            supportingContent = { Text(appInfo.versionName) },
        ) { Text(stringResource(R.string.settings_version)) }
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
