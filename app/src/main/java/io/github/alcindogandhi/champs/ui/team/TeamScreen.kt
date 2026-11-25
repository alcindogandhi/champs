package io.github.alcindogandhi.champs.ui.team

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.StandingGroup
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.DataContent
import io.github.alcindogandhi.champs.ui.common.MatchRow
import io.github.alcindogandhi.champs.ui.common.SectionHeader
import io.github.alcindogandhi.champs.ui.common.TeamCrest
import io.github.alcindogandhi.champs.ui.common.rememberNotificationPermissionRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(container: AppContainer, slug: String, teamId: String, name: String, logo: String?, navigator: Navigator) {
    val vm = viewModel(key = "team-$slug-$teamId") {
        TeamViewModel(container, slug, Team(id = teamId, name = name, shortName = name, logo = logo))
    }
    val schedule by vm.schedule.state.collectAsStateWithLifecycle()
    val standings by vm.standings.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isFavorite = settings.favorite?.team?.id == teamId
    // Ao escolher o time do coração, pede a permissão para avisar os jogos dele.
    val requestPermission = rememberNotificationPermissionRequest()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        vm.toggleFavorite()
                        if (!isFavorite && settings.notifyFavorite) requestPermission()
                    }) {
                        if (isFavorite) {
                            Icon(Icons.Filled.Favorite, stringResource(R.string.unchoose_current_team), tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.FavoriteBorder, stringResource(R.string.choose_current_team))
                        }
                    }
                },
            )
        },
    ) { padding ->
        DataContent(
            state = schedule,
            onRefresh = vm::refresh,
            emptyText = stringResource(R.string.empty_matches),
            modifier = Modifier.padding(padding),
            isEmpty = { it.results.isEmpty() && it.fixtures.isEmpty() },
        ) { data ->
            LazyColumn(Modifier.fillMaxSize()) {
                item { TeamHeader(vm.team, slug, standings.data, isFavorite) }
                if (data.fixtures.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.next_matches)) }
                    matchItems(data.fixtures, teamId) { navigator.match(slug, it) }
                }
                if (data.results.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.results)) }
                    matchItems(data.results, teamId) { navigator.match(slug, it) }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.matchItems(matches: List<Match>, teamId: String, onMatch: (String) -> Unit) {
    items(matches, key = { it.id }) { match ->
        MatchRow(match, onClick = { onMatch(match.id) }, showDate = true, highlightTeamId = teamId)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun TeamHeader(team: Team, slug: String, standings: List<StandingGroup>?, isFavorite: Boolean) {
    val row = standings?.firstNotNullOfOrNull { g -> g.rows.firstOrNull { it.team.id == team.id }?.let { g to it } }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            TeamCrest(row?.second?.team ?: team, 64.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(team.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(CompetitionCatalog.get(slug).name, style = MaterialTheme.typography.bodyMedium)
                if (row != null) {
                    val (group, standing) = row
                    val position = stringResource(R.string.team_position, standing.rank) +
                        (group.name?.let { " · $it" } ?: "")
                    Text(position, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        pluralStringResource(R.plurals.team_points_games, standing.played, standing.points, standing.played) +
                            " · " + stringResource(R.string.team_record, standing.wins, standing.draws, standing.losses),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isFavorite) {
                    Text(
                        stringResource(R.string.choose_current_team),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
