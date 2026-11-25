package io.github.alcindogandhi.champs.domain

import java.time.Instant

/**
 * Organiza os jogos de uma temporada em fases, rodadas e confrontos de mata-mata.
 *
 * A ESPN não informa o número da rodada. Porém, os ids dos jogos são criados na ordem da tabela oficial,
 * e um jogo adiado mantém seu id (ou ganha um jogo novo de reposição). Então:
 * 1. O jogo de reposição herda a posição do jogo adiado original, que sai da lista.
 * 2. Dentro de cada fase, os jogos são ordenados pelo id e agrupados em rodadas de `times / 2` jogos;
 *    uma rodada também fecha antes se um time fosse jogar duas vezes nela.
 *
 * Validado com o Brasileirão 2026 (38 rodadas de 10 jogos), a Premier League, a fase de grupos da
 * Libertadores e a fase de liga do Paulista.
 */
object SeasonBuilder {

    fun build(matches: List<Match>, now: Instant = Instant.now()): Season {
        val (ordered, orderKey) = resolveReplays(matches)
        val phases = ordered
            .groupBy { it.phaseSlug }
            .entries
            .sortedBy { (_, list) -> list.mapNotNull { it.date }.minOrNull() ?: Instant.MAX }
            .map { (slug, list) ->
                val sorted = list.sortedWith(compareBy<Match> { orderKey.getValue(it.id) }.thenBy { it.date })
                if (isKnockout(slug, sorted)) {
                    Phase(slug, PhaseKind.KNOCKOUT, ties = buildTies(sorted))
                } else {
                    Phase(slug, PhaseKind.LEAGUE, rounds = buildRounds(sorted))
                }
            }
        return Season(phases)
    }

    /** Fase e rodada que o app deve abrir: a primeira com a maioria dos jogos ainda por acontecer. */
    fun currentPosition(season: Season): Pair<Int, Int> {
        if (season.phases.isEmpty()) return 0 to 0
        season.phases.forEachIndexed { phaseIndex, phase ->
            when (phase.kind) {
                PhaseKind.LEAGUE -> phase.rounds.forEachIndexed { roundIndex, round ->
                    val pending = round.matches.count { it.isPending() }
                    if (pending * 2 > round.matches.size) return phaseIndex to roundIndex
                }
                PhaseKind.KNOCKOUT -> if (phase.ties.any { tie -> tie.matches.any { it.isPending() } }) {
                    return phaseIndex to 0
                }
            }
        }
        val last = season.phases.lastIndex
        return last to (season.phases[last].rounds.lastIndex).coerceAtLeast(0)
    }

    private fun Match.isPending() = status.state == MatchState.SCHEDULED || status.state == MatchState.LIVE

    private fun Match.isPostponed() =
        status.state == MatchState.POSTPONED || status.state == MatchState.CANCELED

    private fun resolveReplays(matches: List<Match>): Pair<List<Match>, Map<String, Long>> {
        val orderKey = matches.associate { it.id to (it.id.toLongOrNull() ?: Long.MAX_VALUE) }.toMutableMap()
        val dropped = mutableSetOf<String>()
        val usedReplays = mutableSetOf<String>()
        for (original in matches.filter { it.isPostponed() }.sortedBy { orderKey[it.id] }) {
            val replay = matches.firstOrNull { candidate ->
                candidate.id != original.id &&
                    candidate.id !in usedReplays &&
                    !candidate.isPostponed() &&
                    candidate.phaseSlug == original.phaseSlug &&
                    candidate.home.team.id == original.home.team.id &&
                    candidate.away.team.id == original.away.team.id &&
                    orderKey.getValue(candidate.id) > orderKey.getValue(original.id)
            } ?: continue
            orderKey[replay.id] = orderKey.getValue(original.id)
            usedReplays += replay.id
            dropped += original.id
        }
        return matches.filter { it.id !in dropped } to orderKey
    }

    private val knockoutHints = listOf("final", "round-of", "knockout", "playoff")
    private val leagueHints =listOf("group", "league", "regular", "season", "taca", "relegation", "liguilla")

    /** Mata-mata quando cada time enfrenta um único adversário na fase, salvo nomes típicos de fase de liga. */
    internal fun isKnockout(slug: String, matches: List<Match>): Boolean {
        if (slug.firstOrNull()?.isDigit() == true) return false
        val lower = slug.lowercase()
        if (knockoutHints.any { lower.contains(it) }) return true
        if (leagueHints.any { lower.contains(it) }) return false
        val opponents = mutableMapOf<String, MutableSet<String>>()
        for (m in matches) {
            opponents.getOrPut(m.home.team.id) { mutableSetOf() } += m.away.team.id
            opponents.getOrPut(m.away.team.id) { mutableSetOf() } += m.home.team.id
        }
        return opponents.values.all { it.size <= 1 }
    }

    private fun buildRounds(sorted: List<Match>): List<Round> {
        val teamCount = sorted.flatMap { it.teamIds }.toSet().size
        val roundSize = (teamCount / 2).coerceAtLeast(1)
        val rounds = mutableListOf<Round>()
        var current = mutableListOf<Match>()
        val currentTeams = mutableSetOf<String>()
        for (m in sorted) {
            if (current.size == roundSize || m.teamIds.any { it in currentTeams }) {
                rounds += Round(rounds.size + 1, current.sortedBy { it.date })
                current = mutableListOf()
                currentTeams.clear()
            }
            current += m
            currentTeams += m.teamIds
        }
        if (current.isNotEmpty()) rounds += Round(rounds.size + 1, current.sortedBy { it.date })
        return rounds
    }

    private fun buildTies(sorted: List<Match>): List<KnockoutTie> =
        sorted.groupBy { setOf(it.home.team.id, it.away.team.id) }.values.map { legs ->
            val byDate = legs.sortedBy { it.date }
            val first = byDate.first().home.team
            val second = byDate.first().away.team
            val counted = byDate.filter { (it.isFinished || it.isLive) && it.home.score != null && it.away.score != null }
            val firstGoals = counted.takeIf { it.isNotEmpty() }?.sumOf { if (it.home.team.id == first.id) it.home.score!! else it.away.score!! }
            val secondGoals = counted.takeIf { it.isNotEmpty() }?.sumOf { if (it.home.team.id == second.id) it.home.score!! else it.away.score!! }
            KnockoutTie(first, second, byDate, firstGoals, secondGoals, tieWinner(byDate, first.id, second.id, firstGoals, secondGoals))
        }

    private fun tieWinner(legs: List<Match>, firstId: String, secondId: String, firstGoals: Int?, secondGoals: Int?): String? {
        val last = legs.last()
        last.seriesWinnerId?.let { return it }
        if (legs.any { !it.isFinished }) return null
        if (firstGoals != null && secondGoals != null && firstGoals != secondGoals) {
            return if (firstGoals > secondGoals) firstId else secondId
        }
        // Empate no agregado: decide quem a ESPN marcou como vencedor do último jogo (pênaltis).
        return when {
            last.home.winner == true -> last.home.team.id
            last.away.winner == true -> last.away.team.id
            else -> null
        }
    }
}
