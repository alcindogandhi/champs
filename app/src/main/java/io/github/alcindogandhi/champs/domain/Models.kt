package io.github.alcindogandhi.champs.domain

import java.time.Duration
import java.time.Instant

data class Team(
    val id: String,
    val name: String,
    val shortName: String = name,
    val abbreviation: String = "",
    val logo: String? = null,
)

enum class MatchState { SCHEDULED, LIVE, FINISHED, POSTPONED, CANCELED }

data class MatchStatus(
    val state: MatchState,
    /** Relógio do jogo ao vivo ("67'", "45'+2'"); vazio fora do jogo. */
    val clock: String = "",
    /** Texto curto da ESPN para o status ("Intervalo", "Finalizado", "Pênaltis"). */
    val detail: String = "",
)

data class MatchSide(
    val team: Team,
    val score: Int? = null,
    val shootout: Int? = null,
    val winner: Boolean? = null,
)

data class Match(
    val id: String,
    val competitionSlug: String,
    val date: Instant?,
    val status: MatchStatus,
    val home: MatchSide,
    val away: MatchSide,
    /** Slug da fase na ESPN (`season.slug`), ex.: `quarterfinals`, `group-stage`, `2026-brasileiro-serie-a`. */
    val phaseSlug: String = "",
    /** Observação da partida, ex.: "Volta - Palmeiras vence 3-0 no agregado". */
    val note: String = "",
    val venue: String = "",
    /** Agregado do confronto de ida e volta, quando a ESPN informa (mandante, visitante). */
    val aggregate: Pair<Int, Int>? = null,
    /** Id do time que avançou no confronto, quando a ESPN informa. */
    val seriesWinnerId: String? = null,
) {
    val isLive get() = status.state == MatchState.LIVE
    val isFinished get() = status.state == MatchState.FINISHED
    val teamIds get() = setOf(home.team.id, away.team.id)

    /**
     * Jogo que pede atualização automática: ao vivo, ou agendado com início entre 3 h atrás e daqui a
     * 2 min (o apito inicial pode ainda não ter chegado ao dado em cache).
     */
    fun needsLiveRefresh(now: Instant = Instant.now()): Boolean = when (status.state) {
        MatchState.LIVE -> true
        MatchState.SCHEDULED -> date != null && date in now.minus(LIVE_WINDOW)..now.plus(KICKOFF_MARGIN)
        else -> false
    }

    private companion object {
        val LIVE_WINDOW: Duration = Duration.ofHours(3)
        val KICKOFF_MARGIN: Duration = Duration.ofMinutes(2)
    }
}

enum class PhaseKind { LEAGUE, KNOCKOUT }

data class Round(val number: Int, val matches: List<Match>)

data class KnockoutTie(
    val first: Team,
    val second: Team,
    val matches: List<Match>,
    /** Gols somados de [first] e [second] nos jogos encerrados ou em andamento. */
    val firstGoals: Int?,
    val secondGoals: Int?,
    val winnerId: String?,
)

data class Phase(
    val slug: String,
    val kind: PhaseKind,
    val rounds: List<Round> = emptyList(),
    val ties: List<KnockoutTie> = emptyList(),
) {
    val matches: List<Match> get() =
        if (kind == PhaseKind.LEAGUE) rounds.flatMap { it.matches } else ties.flatMap { it.matches }
}

data class Season(val phases: List<Phase>) {
    val matches: List<Match> get() = phases.flatMap { it.matches }
    val teams: List<Team> get() =
        matches.flatMap { listOf(it.home.team, it.away.team) }.distinctBy { it.id }.sortedBy { it.name }
}

/** Tipo de faixa da tabela, quando a ESPN não descreve a faixa. */
enum class ZoneKind { CONTINENTAL, CONTINENTAL_QUALIFYING, SECONDARY_CONTINENTAL, PROMOTION, PLAYOFF, RELEGATION }

/** Faixa de classificação (G4, Z4, etc.). [color] em #RRGGBB; [description] vem da ESPN, [kind] do catálogo. */
data class ZoneInfo(val color: String, val description: String? = null, val kind: ZoneKind? = null)

data class StandingRow(
    val rank: Int,
    val team: Team,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val points: Int,
    val zone: ZoneInfo? = null,
    /** Variação de posição: positivo subiu, negativo caiu. */
    val rankChange: Int = 0,
) {
    val goalDifference get() = goalsFor - goalsAgainst

    /** Aproveitamento em %, 0 a 100. */
    val percentage: Int get() = if (played == 0) 0 else Math.round(points * 100.0 / (played * 3)).toInt()
}

data class StandingGroup(val name: String?, val rows: List<StandingRow>)

data class Scorer(
    val rank: Int,
    val playerName: String,
    val team: Team?,
    val goals: Int,
    val appearances: Int?,
)

enum class EventKind { GOAL, OWN_GOAL, PENALTY_GOAL, PENALTY_MISSED, YELLOW_CARD, RED_CARD, SUBSTITUTION, PERIOD, VAR, OTHER }

data class Play(
    val clock: String,
    val text: String,
    val kind: EventKind,
    /** true = mandante, false = visitante, null = sem time (início, intervalo...). */
    val isHome: Boolean?,
    val highlight: Boolean,
)

data class LineupPlayer(
    val name: String,
    val jersey: String,
    val position: String,
    val formationPlace: Int,
    val starter: Boolean,
    val subbedIn: Boolean,
    val subbedOut: Boolean,
)

data class Lineup(
    val team: Team,
    val formation: String?,
    val starters: List<LineupPlayer>,
    val substitutes: List<LineupPlayer>,
)

data class TeamStatComparison(val key: String, val home: Double, val away: Double, val homeText: String, val awayText: String)

data class MatchDetail(
    val match: Match,
    /** Lance a lance, do mais recente para o mais antigo. */
    val plays: List<Play>,
    val homeLineup: Lineup?,
    val awayLineup: Lineup?,
    val stats: List<TeamStatComparison>,
)
