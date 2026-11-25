package io.github.alcindogandhi.champs.domain

import io.github.alcindogandhi.champs.Fixtures
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.data.remote.EspnMapper
import io.github.alcindogandhi.champs.data.remote.ScoreboardDto
import io.github.alcindogandhi.champs.data.remote.StandingsDto
import org.junit.Assert.assertEquals
import org.junit.Test

class StandingsCalculatorTest {

    @Test
    fun `tabela recalculada a partir dos jogos bate com a da ESPN`() {
        val matches = EspnMapper.matches(Fixtures.decode<ScoreboardDto>("season_bra1_2026.json"), "bra.1")
        val season = SeasonBuilder.build(matches)
        val computed = StandingsCalculator.compute(season.teams, StandingsCalculator.finishedResults(season.matches))
            .associateBy { it.team.id }
        val official = EspnMapper.standings(Fixtures.decode<StandingsDto>("standings_bra1.json"), CompetitionCatalog.get("bra.1"))
            .single().rows
        // Times com jogo em andamento na hora da gravação podem divergir.
        val playing = season.matches.filter { it.isLive }.flatMap { it.teamIds }.toSet()
        val compared = official.filter { it.team.id !in playing }
        compared.forEach { row ->
            val mine = computed.getValue(row.team.id)
            assertEquals(row.team.name, row.points, mine.points)
            assertEquals(row.team.name, row.played, mine.played)
            assertEquals(row.team.name, row.goalDifference, mine.goalDifference)
        }
        assertEquals(20, official.size)
    }

    @Test
    fun `desempate por vitorias, saldo e gols pro`() {
        val a = Team("a", "A")
        val b = Team("b", "B")
        val c = Team("c", "C")
        val d = Team("d", "D")
        val results = listOf(
            Result("a", "d", 1, 0), // A: 3 pts, 1 vitória
            Result("b", "d", 0, 0), // B: 1 pt
            Result("b", "d", 0, 0), // B: 2 pts
            Result("b", "d", 0, 0), // B: 3 pts, 0 vitórias
            Result("c", "d", 3, 2), // C: 3 pts, 1 vitória, saldo 1, 3 gols pró
        )
        val table = StandingsCalculator.compute(listOf(a, b, c, d), results)
        assertEquals(listOf("c", "a", "b", "d"), table.map { it.team.id })
        assertEquals(listOf(1, 2, 3, 4), table.map { it.rank })
        assertEquals(100, table.first().percentage)
        assertEquals(33, table[2].percentage) // B: 3 pontos em 3 jogos
    }
}
