package io.github.alcindogandhi.champs.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.data.settings.FavoriteTeam
import io.github.alcindogandhi.champs.data.settings.Settings
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.DataState
import io.github.alcindogandhi.champs.ui.common.Loader
import io.github.alcindogandhi.champs.ui.common.SectionHeader
import io.github.alcindogandhi.champs.ui.common.TeamCrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

class FavoriteTeamViewModel(private val container: AppContainer) : ViewModel() {

    val settings: StateFlow<Settings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _sections = MutableStateFlow<List<Pair<String, DataState<List<Team>>>>>(emptyList())
    val sections: StateFlow<List<Pair<String, DataState<List<Team>>>>> = _sections

    init {
        viewModelScope.launch {
            val followed = container.settings.settings.first().followed
            val loaders = followed.map { slug -> slug to Loader(viewModelScope) { force -> container.football.teams(slug, force) } }
            loaders.forEach { it.second.load() }
            if (loaders.isEmpty()) return@launch
            combine(loaders.map { (slug, loader) -> loader.state.map { slug to it } }) { it.toList() }
                .collect { _sections.value = it }
        }
    }

    fun choose(slug: String, team: Team?) = viewModelScope.launch {
        container.settings.setFavorite(team?.let { FavoriteTeam(slug, it) })
    }
}

private fun String.normalized() =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteTeamPickerScreen(container: AppContainer, navigator: Navigator) {
    val vm = viewModel { FavoriteTeamViewModel(container) }
    val sections by vm.sections.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.choose_current_team)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text(stringResource(R.string.search_team)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            settings.favorite?.let { favorite ->
                OutlinedButton(
                    onClick = { vm.choose(favorite.competitionSlug, null) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) { Text(stringResource(R.string.unchoose_current_team)) }
            }
            val needle = query.trim().normalized()
            // Copas repetem times das ligas: cada time aparece só no primeiro campeonato seguido.
            val seen = mutableSetOf<String>()
            val visible = sections.map { (slug, state) ->
                Triple(
                    slug,
                    state,
                    state.data.orEmpty()
                        .filter { needle.isEmpty() || it.name.normalized().contains(needle) }
                        .filter { seen.add(it.id) },
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (sections.isEmpty()) {
                    item { Text(stringResource(R.string.empty_teams), Modifier.padding(16.dp)) }
                }
                visible.forEach { (slug, state, teams) ->
                    if (teams.isEmpty() && !state.loading) return@forEach
                    item(key = "h-$slug") { SectionHeader(CompetitionCatalog.get(slug).name) }
                    if (state.loading && state.data == null) {
                        item(key = "l-$slug") {
                            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    items(teams, key = { "$slug-${it.id}" }) { team ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.choose(slug, team)
                                    navigator.back()
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TeamCrest(team, 28.dp)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                team.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (settings.favorite?.team?.id == team.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}
