package io.github.alcindogandhi.champs.ui.competition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.data.settings.Settings
import io.github.alcindogandhi.champs.data.settings.TableColumn
import io.github.alcindogandhi.champs.domain.SeasonBuilder
import io.github.alcindogandhi.champs.ui.common.Loader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fase e rodada exibidas na aba Jogos. */
data class Selection(val phase: Int, val round: Int)

class CompetitionViewModel(private val container: AppContainer, val slug: String) : ViewModel() {

    val standings = Loader(viewModelScope) { force -> container.football.standings(slug, force) }
    val season = Loader(viewModelScope) { force -> container.football.season(slug, force) }
    val scorers = Loader(viewModelScope) { force -> container.football.scorers(slug, force) }

    val settings: StateFlow<Settings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    private val _selection = MutableStateFlow<Selection?>(null)
    val selection: StateFlow<Selection?> = _selection.asStateFlow()

    init {
        standings.load()
        season.load()
        scorers.load()
        // Abre na rodada atual assim que a temporada chega.
        viewModelScope.launch {
            val data = season.state.first { it.data != null }.data!!
            if (_selection.value == null) {
                val (phase, round) = SeasonBuilder.currentPosition(data)
                _selection.value = Selection(phase, round)
            }
        }
    }

    fun refresh(tab: Int) = when (tab) {
        0 -> standings.load(force = true)
        1 -> season.load(force = true)
        else -> scorers.load(force = true)
    }

    fun selectPhase(phase: Int) {
        val data = season.state.value.data ?: return
        val target = data.phases.getOrNull(phase) ?: return
        val (currentPhase, currentRound) = SeasonBuilder.currentPosition(data)
        val round = if (phase == currentPhase) currentRound else if (phase < currentPhase) target.rounds.lastIndex else 0
        _selection.value = Selection(phase, round.coerceAtLeast(0))
    }

    fun selectRound(round: Int) {
        val current = _selection.value ?: return
        _selection.value = current.copy(round = round)
    }

    fun setColumns(columns: Set<TableColumn>) = viewModelScope.launch { container.settings.setColumns(columns) }
}
