package io.github.alcindogandhi.champs.domain

import java.time.Duration
import java.time.Instant

/** Último estado de um jogo acompanhado, guardado entre uma consulta e outra. */
data class LastSeen(val state: MatchState, val home: Int?, val away: Int?)

sealed interface MatchEvent {
    data object Started : MatchEvent

    /**
     * Gol de [teamId]; [home] × [away] é o placar logo depois desse gol (vários gols numa mesma consulta saem
     * com os placares parciais, em ordem). [goal] traz autor e minuto, se a ESPN informou.
     */
    data class GoalScored(val teamId: String, val home: Int, val away: Int, val goal: Goal?) : MatchEvent
}

object MatchEvents {

    /**
     * Avisos a mandar comparando o último estado visto com o atual:
     * - início: o jogo estava agendado (ou adiado) e agora está em andamento;
     * - gols: o placar de um time subiu, enquanto o jogo está em andamento ou logo ao terminar (último gol
     *   antes do apito final). Um jogo inteiro perdido entre duas consultas não gera avisos atrasados.
     * Placar que diminui (gol anulado) só atualiza o estado, sem aviso.
     */
    fun detect(previous: LastSeen, current: Match): List<MatchEvent> {
        val events = mutableListOf<MatchEvent>()
        val state = current.status.state
        val wasPending = previous.state == MatchState.SCHEDULED || previous.state == MatchState.POSTPONED
        if (state == MatchState.LIVE && wasPending) events += MatchEvent.Started

        val countsGoals = state == MatchState.LIVE || state == MatchState.FINISHED && previous.state == MatchState.LIVE
        val home = current.home.score
        val away = current.away.score
        if (!countsGoals || home == null || away == null) return events

        val newHome = (home - (previous.home ?: 0)).coerceAtLeast(0)
        val newAway = (away - (previous.away ?: 0)).coerceAtLeast(0)
        if (newHome == 0 && newAway == 0) return events

        // Os gols novos são os últimos de cada time no resumo; mantém a ordem cronológica entre os dois.
        val homeId = current.home.team.id
        val awayId = current.away.team.id
        val indexed = current.goals.withIndex()
        val homeGoals = indexed.filter { it.value.teamId == homeId }.toList().takeLast(newHome)
        val awayGoals = indexed.filter { it.value.teamId == awayId }.toList().takeLast(newAway)
        val known = (homeGoals + awayGoals).sortedBy { it.index }.map { it.value }
        // Placar parcial a cada gol, a partir do último placar visto.
        var runningHome = previous.home ?: 0
        var runningAway = previous.away ?: 0
        fun scored(teamId: String, goal: Goal?) {
            if (teamId == homeId) runningHome++ else runningAway++
            events += MatchEvent.GoalScored(teamId, runningHome, runningAway, goal)
        }
        known.forEach { scored(it.teamId, it) }
        // Gols sem detalhe no resumo ainda: avisa mesmo assim, sem autor.
        repeat(newHome - homeGoals.size) { scored(homeId, null) }
        repeat(newAway - awayGoals.size) { scored(awayId, null) }
        return events
    }

    private val EARLY = Duration.ofMinutes(2)
    private val WINDOW = Duration.ofHours(4)
    private val LIVE_LIMIT = Duration.ofHours(6)

    /**
     * Jogo que precisa ser consultado agora: a partir de 2 min antes do início e até 4 h depois
     * (6 h se ainda está em andamento, para prorrogação, pênaltis ou atrasos).
     */
    fun isDue(kickoff: Instant?, lastState: MatchState, now: Instant): Boolean {
        if (kickoff == null) return lastState == MatchState.LIVE
        if (now < kickoff.minus(EARLY)) return false
        val limit = if (lastState == MatchState.LIVE) LIVE_LIMIT else WINDOW
        return now <= kickoff.plus(limit)
    }

    /**
     * Espera até a próxima consulta: curta com a bola rolando, maior antes do apito inicial e no intervalo
     * (quando não há gol a perder). Com [batterySaver], tudo fica mais espaçado.
     */
    fun pollInterval(matches: List<Match>, batterySaver: Boolean): Duration {
        val ballRolling = matches.any { it.status.state == MatchState.LIVE && !it.status.halftime }
        return when {
            ballRolling && batterySaver -> Duration.ofMinutes(2)
            ballRolling -> Duration.ofSeconds(30)
            batterySaver -> Duration.ofMinutes(3)
            else -> Duration.ofMinutes(1)
        }
    }

    /** Quando começar a consultar um jogo agendado. */
    fun watchStart(kickoff: Instant): Instant = kickoff.minus(EARLY)
}
