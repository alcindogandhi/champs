package io.github.alcindogandhi.champs.ui.competitions

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.catalog.Region
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.SectionHeader
import kotlinx.coroutines.launch

fun Region.label(): Int = when (this) {
    Region.BRAZIL -> R.string.region_brazil
    Region.SOUTH_AMERICA -> R.string.region_south_america
    Region.EUROPE -> R.string.region_europe
    Region.WORLD -> R.string.region_world
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCompetitionsScreen(container: AppContainer, navigator: Navigator) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val followed = settings?.followed ?: return

    fun toggle(slug: String, follow: Boolean) {
        // Mantém a ordem do catálogo no menu.
        val next = CompetitionCatalog.all.map { it.slug }.filter { if (it == slug) follow else it in followed }
        scope.launch { container.settings.setFollowed(next) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_competitions)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            CompetitionCatalog.all.groupBy { it.region }.forEach { (region, competitions) ->
                item(key = region.name) { SectionHeader(stringResource(region.label())) }
                items(competitions, key = { it.slug }) { competition ->
                    val checked = competition.slug in followed
                    ListItem(
                        headlineContent = { Text(competition.name) },
                        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                        modifier = Modifier.toggleable(
                            value = checked,
                            role = Role.Checkbox,
                            onValueChange = { toggle(competition.slug, it) },
                        ),
                    )
                }
            }
        }
    }
}
