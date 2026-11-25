package io.github.alcindogandhi.champs.ui.simulator

import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.data.settings.TableColumn
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.StandingRow
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.DataContent
import io.github.alcindogandhi.champs.ui.common.Format
import io.github.alcindogandhi.champs.ui.common.MessageState
import io.github.alcindogandhi.champs.ui.common.TeamCrest
import io.github.alcindogandhi.champs.ui.common.standingsTable
import io.github.alcindogandhi.champs.ui.common.statusText
import io.github.alcindogandhi.champs.ui.competition.RoundSelector
import io.github.alcindogandhi.champs.domain.StandingGroup
import kotlinx.coroutines.launch

private const val APP_LINK = "https://github.com/alcindogandhi/champs"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulationScreen(container: AppContainer, simulationId: Long, slug: String, navigator: Navigator) {
    val vm = viewModel(key = "simulation-$simulationId") { SimulationViewModel(container, simulationId, slug) }
    val simulation by vm.simulation.collectAsStateWithLifecycle()
    val seasonState by vm.season.state.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val round by vm.round.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val shareTitle = stringResource(R.string.share)
    var menu by remember { mutableStateOf(false) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val competition = CompetitionCatalog.get(slug)
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(simulation?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(competition.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    ui?.let { data ->
                        IconButton(onClick = {
                            val text = shareText(resources, competition.name, data.table)
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            context.startActivity(Intent.createChooser(send, shareTitle))
                        }) { Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share)) }
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit_name)) }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.clear_simulation)) }, onClick = { menu = false; vm.clearAll() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete_simulation)) }, onClick = { menu = false; deleting = true })
                    }
                },
            )
        },
    ) { padding ->
        DataContent(
            state = seasonState,
            onRefresh = { vm.season.load(force = true) },
            emptyText = stringResource(R.string.simulation_needs_data),
            modifier = Modifier.padding(padding),
            isEmpty = { season -> season.phases.none { it.kind == io.github.alcindogandhi.champs.domain.PhaseKind.LEAGUE } },
        ) {
            val data = ui ?: return@DataContent
            Column(Modifier.fillMaxSize()) {
                PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                    listOf(R.string.matches, R.string.standings).forEachIndexed { index, title ->
                        Tab(
                            selected = pager.currentPage == index,
                            onClick = { scope.launch { pager.animateScrollToPage(index) } },
                            text = { Text(stringResource(title)) },
                        )
                    }
                }
                HorizontalPager(pager, Modifier.weight(1f)) { page ->
                    if (page == 0) {
                        SimulatedRound(data, round ?: data.currentRound, vm)
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            standingsTable(
                                groups = listOf(StandingGroup(null, data.table)),
                                columns = setOf(TableColumn.PLAYED, TableColumn.WINS, TableColumn.GOAL_DIFFERENCE),
                                onTeamClick = null,
                                variation = data.variation,
                            )
                        }
                    }
                }
            }
        }
    }

    if (renaming) {
        var name by rememberSaveable { mutableStateOf(simulation?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.edit_name)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.rename(name)
                    renaming = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text(stringResource(R.string.delete_simulation)) },
            text = { Text(stringResource(R.string.delete_simulation_confirmation)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    vm.delete(navigator::back)
                }) { Text(stringResource(R.string.erase)) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SimulatedRound(data: SimulationUi, roundIndex: Int, vm: SimulationViewModel) {
    val rounds = data.phase.rounds
    if (rounds.isEmpty()) {
        MessageState(stringResource(R.string.empty_matches))
        return
    }
    val index = roundIndex.coerceIn(0, rounds.lastIndex)
    val round = rounds[index]
    Column(Modifier.fillMaxSize()) {
        RoundSelector(round, hasPrevious = index > 0, hasNext = index < rounds.lastIndex) { vm.selectRound(index + it) }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            items(round.matches, key = { it.id }) { match ->
                SimulatedMatchRow(
                    match = match,
                    score = data.scores[match.id],
                    onChange = { h, a -> vm.setScore(match.id, h, a) },
                    onClear = { vm.clearScore(match.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

/** Jogo encerrado mostra o placar real; os demais têm botões − e + para cada time. */
@Composable
private fun SimulatedMatchRow(match: Match, score: Score?, onChange: (Int, Int) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (match.isFinished) stringResource(R.string.real_result) else Format.shortDate(match.date) + " · " + statusText(match),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (score != null && !match.isFinished) {
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.clear_score), Modifier.size(16.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                TeamCrest(match.home.team, 28.dp)
                Text(match.home.team.shortName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (match.isFinished) {
                Text(
                    "${match.home.score} × ${match.away.score}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val (home, away) = score ?: (null to null)
                Stepper(home, onMinus = { onChange(((home ?: 0) - 1).coerceAtLeast(0), away ?: 0) }, onPlus = { onChange((home ?: 0) + 1, away ?: 0) })
                Text("×", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 4.dp))
                Stepper(away, onMinus = { onChange(home ?: 0, ((away ?: 0) - 1).coerceAtLeast(0)) }, onPlus = { onChange(home ?: 0, (away ?: 0) + 1) })
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                TeamCrest(match.away.team, 28.dp)
                Text(match.away.team.shortName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun Stepper(value: Int?, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.filledTonalIconButtonColors()) {
            Icon(Icons.Filled.Remove, stringResource(R.string.decrease_goals), Modifier.size(16.dp))
        }
        Text(
            value?.toString() ?: "–",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp),
        )
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Add, stringResource(R.string.increase_goals), Modifier.size(16.dp))
        }
        Spacer(Modifier.width(2.dp))
    }
}

private fun shareText(resources: Resources, competition: String, table: List<StandingRow>): String = buildString {
    appendLine(resources.getString(R.string.share_simulation_header))
    appendLine(competition)
    appendLine()
    table.forEach { row -> appendLine("${row.rank}. ${row.team.shortName} — ${row.points}") }
    appendLine()
    append(resources.getString(R.string.share_simulation_footer, APP_LINK))
}
