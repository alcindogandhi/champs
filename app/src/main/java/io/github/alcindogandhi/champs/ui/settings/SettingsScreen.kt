package io.github.alcindogandhi.champs.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.BuildConfig
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.data.settings.ThemeMode
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val REPOSITORY = "https://github.com/alcindogandhi/champs"
private const val PRIVACY = "$REPOSITORY/blob/main/PRIVACY.md"
private const val LICENSE = "$REPOSITORY/blob/main/LICENSE"

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, navigator: Navigator) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var confirmingClear by rememberSaveable { mutableStateOf(false) }
    val clearedMessage = stringResource(R.string.clear_data_done)
    val current = settings ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionHeader(stringResource(R.string.general))
            Item(Icons.Outlined.DarkMode, stringResource(R.string.choose_theme), stringResource(current.theme.label())) { choosingTheme = true }
            Item(
                Icons.Outlined.Favorite,
                stringResource(R.string.choose_current_team),
                current.favorite?.team?.name ?: stringResource(R.string.none),
                onClick = navigator::favoriteTeam,
            )
            Item(Icons.Outlined.Tune, stringResource(R.string.manage_competitions), null, onClick = navigator::manageCompetitions)
            HorizontalDivider()
            SectionHeader(stringResource(R.string.data))
            Item(Icons.Outlined.Public, stringResource(R.string.data_source), stringResource(R.string.data_source_description), onClick = null)
            Item(Icons.Outlined.DeleteForever, stringResource(R.string.clear_data), stringResource(R.string.clear_data_description)) {
                confirmingClear = true
            }
            HorizontalDivider()
            SectionHeader(stringResource(R.string.about))
            Item(Icons.Outlined.Info, stringResource(R.string.version), BuildConfig.VERSION_NAME, onClick = null)
            Item(Icons.Outlined.Code, stringResource(R.string.source_code), REPOSITORY) { uri.openUri(REPOSITORY) }
            Item(Icons.Outlined.Gavel, stringResource(R.string.license), stringResource(R.string.license_description)) { uri.openUri(LICENSE) }
            Item(Icons.Outlined.PrivacyTip, stringResource(R.string.privacy), stringResource(R.string.privacy_description)) { uri.openUri(PRIVACY) }
        }
    }

    if (choosingTheme) {
        AlertDialog(
            onDismissRequest = { choosingTheme = false },
            title = { Text(stringResource(R.string.choose_theme)) },
            text = {
                Column {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(selected = mode == current.theme, role = Role.RadioButton) {
                                    scope.launch { container.settings.setTheme(mode) }
                                    choosingTheme = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = mode == current.theme, onClick = null)
                            Text(stringResource(mode.label()), Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosingTheme = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text(stringResource(R.string.clear_data)) },
            text = { Text(stringResource(R.string.are_you_sure_clear_data)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingClear = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            container.football.clearCache()
                            container.database.clearAllTables()
                        }
                        container.settings.clear()
                        snackbar.showSnackbar(clearedMessage)
                    }
                }) { Text(stringResource(R.string.erase)) }
            },
            dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
    )
}
