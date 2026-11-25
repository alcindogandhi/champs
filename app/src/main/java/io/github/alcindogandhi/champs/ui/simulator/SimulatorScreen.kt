package io.github.alcindogandhi.champs.ui.simulator

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import io.github.alcindogandhi.champs.data.db.SimulationEntity
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.Format
import io.github.alcindogandhi.champs.ui.common.MessageState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

class SimulatorViewModel(private val container: AppContainer, val slug: String) : ViewModel() {
    private val dao = container.database.simulations()

    val simulations: StateFlow<List<SimulationEntity>?> = dao.observeByCompetition(slug)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun create(name: String, onCreated: (Long) -> Unit) = viewModelScope.launch {
        val id = dao.insert(SimulationEntity(competitionSlug = slug, name = name, createdAt = System.currentTimeMillis()))
        onCreated(id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatorScreen(container: AppContainer, slug: String, navigator: Navigator) {
    val vm = viewModel(key = "simulator-$slug") { SimulatorViewModel(container, slug) }
    val simulations by vm.simulations.collectAsStateWithLifecycle()
    val competition = CompetitionCatalog.get(slug)
    val defaultName = stringResource(R.string.simulation_default_name, (simulations?.size ?: 0) + 1)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.simulator_of, competition.name)) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.create(defaultName) { id -> navigator.simulation(id, slug) } },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_simulation)) },
            )
        },
    ) { padding ->
        val list = simulations ?: return@Scaffold
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.simulator_info), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (list.isEmpty()) {
                item { MessageState(stringResource(R.string.empty_simulations), icon = Icons.Outlined.Calculate, scrollable = false) }
            }
            items(list, key = { it.id }) { simulation ->
                ListItem(
                    headlineContent = { Text(simulation.name) },
                    supportingContent = { Text(stringResource(R.string.created_at, Format.dateTime(Instant.ofEpochMilli(simulation.createdAt)))) },
                    leadingContent = { Icon(Icons.Outlined.Calculate, contentDescription = null) },
                    modifier = Modifier.clickable { navigator.simulation(simulation.id, slug) },
                )
                HorizontalDivider()
            }
        }
    }
}
