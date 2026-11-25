package io.github.alcindogandhi.champs.ui.competition

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.ViewColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.data.settings.TableColumn
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.label
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionScreen(container: AppContainer, slug: String, navigator: Navigator) {
    val vm = viewModel(key = "competition-$slug") { CompetitionViewModel(container, slug) }
    val competition = CompetitionCatalog.get(slug)
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    var choosingColumns by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(competition.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = navigator.openDrawer) {
                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.drawer_open))
                    }
                },
                actions = {
                    if (pager.currentPage == 0) {
                        IconButton(onClick = { choosingColumns = true }) {
                            Icon(Icons.Outlined.ViewColumn, contentDescription = stringResource(R.string.choose_columns))
                        }
                    }
                    if (competition.simulable) {
                        IconButton(onClick = { navigator.simulator(slug) }) {
                            Icon(Icons.Outlined.Calculate, contentDescription = stringResource(R.string.simulator))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val tabs = listOf(R.string.standings, R.string.matches, R.string.scorers)
            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pager.currentPage == index,
                        onClick = { scope.launch { pager.animateScrollToPage(index) } },
                        text = { Text(stringResource(title)) },
                    )
                }
            }
            HorizontalPager(pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
                when (page) {
                    0 -> StandingsTab(vm, settings.columns, settings.favorite?.team?.id, navigator)
                    1 -> MatchesTab(vm, navigator)
                    else -> ScorersTab(vm)
                }
            }
        }
    }

    if (choosingColumns) {
        ColumnsDialog(settings.columns, onDismiss = { choosingColumns = false }, onConfirm = {
            vm.setColumns(it)
            choosingColumns = false
        })
    }
}

@Composable
private fun ColumnsDialog(current: Set<TableColumn>, onDismiss: () -> Unit, onConfirm: (Set<TableColumn>) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_info_to_show)) },
        text = {
            Column {
                TableColumn.entries.forEach { col ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = col in selected,
                            onCheckedChange = { checked -> selected = if (checked) selected + col else selected - col },
                        )
                        Text(stringResource(col.label()))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
