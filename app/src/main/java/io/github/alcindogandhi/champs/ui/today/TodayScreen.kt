package io.github.alcindogandhi.champs.ui.today

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.Format
import io.github.alcindogandhi.champs.ui.common.LoadingState
import io.github.alcindogandhi.champs.ui.common.MatchRow
import io.github.alcindogandhi.champs.ui.common.MessageState
import io.github.alcindogandhi.champs.ui.common.SectionHeader
import io.github.alcindogandhi.champs.ui.common.StaleBanner
import kotlinx.coroutines.delay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(container: AppContainer, navigator: Navigator) {
    val vm = viewModel { TodayViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    // Atualização automática enquanto a tela está visível e o dia é hoje.
    LaunchedEffect(state.date, state.anyLive) {
        if (state.date != LocalDate.now()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(if (state.anyLive) 30_000 else 120_000)
                vm.refreshLive()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.matches_today)) },
                navigationIcon = {
                    IconButton(onClick = navigator.openDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.drawer_open))
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            DaySelector(state.date, onShift = vm::shiftDay, onToday = vm::goToday)
            Row(Modifier.padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = state.liveOnly,
                    onClick = vm::toggleLiveOnly,
                    label = { Text(stringResource(R.string.only_live_matches)) },
                )
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                val sections = state.sections.map { section ->
                    section to section.state.data.orEmpty().filter { !state.liveOnly || it.isLive }
                }
                val failure = state.sections.firstNotNullOfOrNull { it.state.error }
                val allFailed = state.sections.isNotEmpty() && state.sections.all { it.state.data == null && it.state.error != null }
                when {
                    state.sections.isEmpty() -> MessageState(
                        stringResource(R.string.select_championship_empty),
                        action = stringResource(R.string.manage_competitions),
                        onAction = navigator::manageCompetitions,
                    )
                    state.loading -> LoadingState()
                    allFailed && failure != null -> MessageState(
                        Format.error(resources, failure),
                        icon = Icons.Outlined.CloudOff,
                        action = stringResource(R.string.retry),
                        onAction = { vm.refresh() },
                    )
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        if (failure != null) {
                            item { StaleBanner(failure, null) { vm.refresh() } }
                        }
                        val visible = sections.filter { it.second.isNotEmpty() }
                        if (visible.isEmpty() && state.sections.none { it.state.loading }) {
                            item {
                                MessageState(
                                    stringResource(if (state.liveOnly) R.string.empty_live_matches else R.string.empty_matches),
                                    scrollable = false,
                                )
                            }
                        }
                        visible.forEach { (section, matches) ->
                            item(key = "h-${section.slug}") {
                                SectionHeader(CompetitionCatalog.get(section.slug).name) { navigator.competition(section.slug) }
                            }
                            items(matches, key = { "${section.slug}-${it.id}" }) { match ->
                                MatchRow(match, onClick = { navigator.match(section.slug, match.id) })
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySelector(date: LocalDate, onShift: (Long) -> Unit, onToday: () -> Unit) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> null
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onShift(-1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.previous_day))
        }
        TextButton(onClick = onToday, modifier = Modifier.weight(1f)) {
            Text(
                listOfNotNull(label, Format.longDate(date)).joinToString(" · "),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
        IconButton(onClick = { onShift(1) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.next_day))
        }
    }
}
