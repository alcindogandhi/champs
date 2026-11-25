package io.github.alcindogandhi.champs.ui.competition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.data.settings.TableColumn
import io.github.alcindogandhi.champs.domain.KnockoutTie
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.PhaseKind
import io.github.alcindogandhi.champs.domain.Round
import io.github.alcindogandhi.champs.domain.Season
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.DataContent
import io.github.alcindogandhi.champs.ui.common.Format
import io.github.alcindogandhi.champs.ui.common.MatchRow
import io.github.alcindogandhi.champs.ui.common.PhaseNames
import io.github.alcindogandhi.champs.ui.common.TeamCrest
import io.github.alcindogandhi.champs.ui.common.standingsTable
import kotlinx.coroutines.delay

@Composable
fun StandingsTab(vm: CompetitionViewModel, columns: Set<TableColumn>, favoriteTeamId: String?, navigator: Navigator) {
    val state by vm.standings.state.collectAsStateWithLifecycle()
    DataContent(
        state = state,
        onRefresh = { vm.refresh(0) },
        emptyText = stringResource(R.string.empty_classification),
        isEmpty = { it.isEmpty() },
    ) { groups ->
        LazyColumn(Modifier.fillMaxSize()) {
            standingsTable(
                groups = groups,
                columns = columns,
                onTeamClick = { row -> navigator.team(vm.slug, row.team.id, row.team.name, row.team.logo) },
                highlightTeamId = favoriteTeamId,
            )
        }
    }
}

@Composable
fun MatchesTab(vm: CompetitionViewModel, navigator: Navigator) {
    val state by vm.season.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Atualiza os placares sozinho enquanto a rodada ou fase aberta tem jogo ao vivo ou começando.
    val liveInView = state.data?.let { season -> selection?.let { visibleMatches(season, it) } }
        .orEmpty()
        .any { it.needsLiveRefresh() }
    LaunchedEffect(liveInView) {
        if (!liveInView) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(LIVE_REFRESH_MILLIS)
                vm.refreshLive()
            }
        }
    }

    DataContent(
        state = state,
        onRefresh = { vm.refresh(1) },
        emptyText = stringResource(R.string.empty_matches),
        isEmpty = { it.phases.isEmpty() },
    ) { season ->
        val current = selection ?: return@DataContent
        SeasonMatches(
            season = season,
            selection = current,
            onPhase = vm::selectPhase,
            onRound = vm::selectRound,
            onMatch = { navigator.match(vm.slug, it) },
        )
    }
}

private const val LIVE_REFRESH_MILLIS = 30_000L

/** Jogos que a aba Jogos mostra para a seleção: a rodada (liga) ou a fase inteira (mata-mata). */
private fun visibleMatches(season: Season, selection: Selection): List<Match> {
    val phase = season.phases.getOrNull(selection.phase.coerceIn(0, (season.phases.lastIndex).coerceAtLeast(0)))
        ?: return emptyList()
    return when (phase.kind) {
        PhaseKind.LEAGUE -> phase.rounds.getOrNull(selection.round.coerceIn(0, (phase.rounds.lastIndex).coerceAtLeast(0)))
            ?.matches.orEmpty()
        PhaseKind.KNOCKOUT -> phase.matches
    }
}

@Composable
private fun SeasonMatches(
    season: Season,
    selection: Selection,
    onPhase: (Int) -> Unit,
    onRound: (Int) -> Unit,
    onMatch: (String) -> Unit,
) {
    val resources = LocalResources.current
    val phaseIndex = selection.phase.coerceIn(0, season.phases.lastIndex)
    val phase = season.phases[phaseIndex]
    Column(Modifier.fillMaxSize()) {
        if (season.phases.size > 1) {
            val chips = rememberLazyListState(initialFirstVisibleItemIndex = phaseIndex)
            LazyRow(
                state = chips,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(season.phases) { index, p ->
                    FilterChip(
                        selected = index == phaseIndex,
                        onClick = { onPhase(index) },
                        label = { Text(PhaseNames.name(resources, p.slug)) },
                    )
                }
            }
        }
        when (phase.kind) {
            PhaseKind.LEAGUE -> {
                val roundIndex = selection.round.coerceIn(0, (phase.rounds.lastIndex).coerceAtLeast(0))
                val round = phase.rounds.getOrNull(roundIndex) ?: return@Column
                RoundSelector(round, hasPrevious = roundIndex > 0, hasNext = roundIndex < phase.rounds.lastIndex) { delta ->
                    onRound(roundIndex + delta)
                }
                HorizontalDivider()
                val list = rememberLazyListState()
                LaunchedEffect(phaseIndex, roundIndex) { list.scrollToItem(0) }
                LazyColumn(Modifier.fillMaxSize(), state = list) {
                    items(round.matches, key = { it.id }) { match ->
                        MatchRow(match, onClick = { onMatch(match.id) }, showDate = true)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
            PhaseKind.KNOCKOUT -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(phase.ties, key = { it.matches.first().id }) { tie -> TieCard(tie, onMatch) }
            }
        }
    }
}

@Composable
fun RoundSelector(round: Round, hasPrevious: Boolean, hasNext: Boolean, onShift: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }, enabled = hasPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_round))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.round_number, round.number), style = MaterialTheme.typography.titleMedium)
            val dates = round.matches.mapNotNull { it.date }
            if (dates.isNotEmpty()) {
                val first = Format.shortDate(dates.min())
                val last = Format.shortDate(dates.max())
                Text(
                    if (first == last) first else "$first – $last",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = { onShift(1) }, enabled = hasNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_round))
        }
    }
}

@Composable
private fun TieCard(tie: KnockoutTie, onMatch: (String) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            TieTeamRow(tie.first, tie.firstGoals, tie.winnerId)
            TieTeamRow(tie.second, tie.secondGoals, tie.winnerId)
            if (tie.matches.size > 1 || tie.firstGoals == null) {
                HorizontalDivider(Modifier.padding(top = 8.dp))
            }
            tie.matches.forEachIndexed { index, match ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                MatchRow(match, onClick = { onMatch(match.id) }, showDate = true)
            }
        }
    }
}

@Composable
private fun TieTeamRow(team: Team, goals: Int?, winnerId: String?) {
    val winner = winnerId == team.id
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(team, 24.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            team.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (winner) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (winner) {
            Text(
                stringResource(R.string.advanced),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        Text(
            goals?.toString() ?: "",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp),
        )
    }
}

@Composable
fun ScorersTab(vm: CompetitionViewModel) {
    val state by vm.scorers.state.collectAsStateWithLifecycle()
    DataContent(
        state = state,
        onRefresh = { vm.refresh(2) },
        emptyText = stringResource(R.string.no_scorers),
        isEmpty = { it.isEmpty() },
    ) { scorers ->
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val style = MaterialTheme.typography.labelMedium
                    Text(stringResource(R.string.header_position), style = style, modifier = Modifier.width(32.dp))
                    Text(stringResource(R.string.player), style = style, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.header_played), style = style, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                    Text(stringResource(R.string.header_goals), style = style, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                }
                HorizontalDivider()
            }
            items(scorers, key = { "${it.rank}-${it.playerName}" }) { scorer ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(scorer.rank.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(32.dp))
                    TeamCrest(scorer.team, 24.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(scorer.playerName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        scorer.team?.let {
                            Text(it.shortName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        scorer.appearances?.toString().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        scorer.goals.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item {
                Text(
                    stringResource(R.string.source_espn),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
