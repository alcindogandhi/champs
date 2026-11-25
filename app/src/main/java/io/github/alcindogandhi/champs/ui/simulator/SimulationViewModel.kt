package io.github.alcindogandhi.champs.ui.simulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.data.db.SimulatedScoreEntity
import io.github.alcindogandhi.champs.data.db.SimulationEntity
import io.github.alcindogandhi.champs.domain.Phase
import io.github.alcindogandhi.champs.domain.PhaseKind
import io.github.alcindogandhi.champs.domain.Result
import io.github.alcindogandhi.champs.domain.SeasonBuilder
import io.github.alcindogandhi.champs.domain.StandingRow
import io.github.alcindogandhi.champs.domain.StandingsCalculator
import io.github.alcindogandhi.champs.ui.common.Loader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Placar simulado (mandante, visitante). */
typealias Score = Pair<Int, Int>

data class SimulationUi(
    val phase: Phase,
    val scores: Map<String, Score>,
    val table: List<StandingRow>,
    /** Posições ganhas (+) ou perdidas (−) em relação à tabela real. */
    val variation: Map<String, Int>,
    val currentRound: Int,
)

class SimulationViewModel(private val container: AppContainer, val simulationId: Long, val slug: String) : ViewModel() {

    private val dao = container.database.simulations()
    private val competition = CompetitionCatalog.get(slug)

    val season = Loader(viewModelScope) { force -> container.football.season(slug, force) }

    val simulation: StateFlow<SimulationEntity?> = dao.observe(simulationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _round = MutableStateFlow<Int?>(null)
    val round: StateFlow<Int?> = _round

    val ui: StateFlow<SimulationUi?> = combine(season.state, dao.observeScores(simulationId)) { state, scores ->
        val data = state.data ?: return@combine null
        val phase = data.phases.firstOrNull { it.kind == PhaseKind.LEAGUE } ?: return@combine null
        val simulated = scores.associate { it.matchId to (it.homeGoals to it.awayGoals) }
        val matches = phase.matches
        val teams = data.teams.filter { t -> matches.any { t.id in it.teamIds } }
        val real = StandingsCalculator.finishedResults(matches)
        val withSimulation = real + matches.filter { !it.isFinished }.mapNotNull { m ->
            simulated[m.id]?.let { (h, a) -> Result(m.home.team.id, m.away.team.id, h, a) }
        }
        val realTable = StandingsCalculator.compute(teams, real)
        val simTable = StandingsCalculator.compute(teams, withSimulation, competition::zoneFor)
        val realRank = realTable.associate { it.team.id to it.rank }
        val current = SeasonBuilder.currentPosition(data).let { (p, r) -> if (data.phases[p] === phase) r else 0 }
        SimulationUi(
            phase = phase,
            scores = simulated,
            table = simTable,
            variation = simTable.associate { it.team.id to (realRank.getValue(it.team.id) - it.rank) },
            currentRound = current,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        season.load()
        viewModelScope.launch {
            val first = ui.first { it != null }!!
            if (_round.value == null) _round.value = first.currentRound
        }
    }

    fun selectRound(index: Int) {
        _round.value = index
    }

    fun setScore(matchId: String, home: Int, away: Int) = viewModelScope.launch {
        dao.upsertScore(SimulatedScoreEntity(simulationId, matchId, home.coerceIn(0, 99), away.coerceIn(0, 99)))
    }

    fun clearScore(matchId: String) = viewModelScope.launch { dao.deleteScore(simulationId, matchId) }

    fun clearAll() = viewModelScope.launch { dao.clearScores(simulationId) }

    fun rename(name: String) = viewModelScope.launch { dao.rename(simulationId, name.trim()) }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        dao.delete(simulationId)
        onDone()
    }
}
