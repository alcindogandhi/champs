package io.github.alcindogandhi.champs.domain

/** Placar usado no cálculo: real (jogo encerrado) ou simulado. */
data class Result(val homeId: String, val awayId: String, val homeGoals: Int, val awayGoals: Int)

/**
 * Calcula a classificação de pontos corridos a partir dos resultados.
 *
 * Critérios de desempate do Brasileirão (os mesmos da maioria das ligas que o app simula):
 * pontos, vitórias, saldo de gols, gols pró e, por fim, ordem alfabética como critério estável.
 * Confronto direto, cartões e sorteio não entram.
 */
object StandingsCalculator {

    fun compute(teams: List<Team>, results: List<Result>, zones: (Int) -> ZoneInfo? = { null }): List<StandingRow> {
        class Acc { var p = 0; var w = 0; var d = 0; var l = 0; var gf = 0; var ga = 0 }
        val acc = teams.associate { it.id to Acc() }
        for (r in results) {
            val home = acc[r.homeId] ?: continue
            val away = acc[r.awayId] ?: continue
            home.p++; away.p++
            home.gf += r.homeGoals; home.ga += r.awayGoals
            away.gf += r.awayGoals; away.ga += r.homeGoals
            when {
                r.homeGoals > r.awayGoals -> { home.w++; away.l++ }
                r.homeGoals < r.awayGoals -> { away.w++; home.l++ }
                else -> { home.d++; away.d++ }
            }
        }
        val rows = teams.map { t ->
            val a = acc.getValue(t.id)
            StandingRow(0, t, a.p, a.w, a.d, a.l, a.gf, a.ga, a.w * 3 + a.d)
        }
        return rows
            .sortedWith(
                compareByDescending<StandingRow> { it.points }
                    .thenByDescending { it.wins }
                    .thenByDescending { it.goalDifference }
                    .thenByDescending { it.goalsFor }
                    .thenBy { it.team.name },
            )
            .mapIndexed { i, row -> row.copy(rank = i + 1, zone = zones(i + 1)) }
    }

    /** Resultados reais dos jogos encerrados de uma lista. */
    fun finishedResults(matches: List<Match>): List<Result> = matches.mapNotNull { m ->
        val h = m.home.score
        val a = m.away.score
        if (m.isFinished && h != null && a != null) Result(m.home.team.id, m.away.team.id, h, a) else null
    }
}
