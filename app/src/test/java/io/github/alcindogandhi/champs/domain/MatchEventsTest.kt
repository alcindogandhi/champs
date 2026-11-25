package io.github.alcindogandhi.champs.domain

import io.github.alcindogandhi.champs.Fixtures
import io.github.alcindogandhi.champs.data.remote.EspnMapper
import io.github.alcindogandhi.champs.data.remote.ScoreboardDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MatchEventsTest {

    private val flamengo = Team("819", "Flamengo")
    private val palmeiras = Team("2029", "Palmeiras")

    private fun match(state: MatchState, home: Int?, away: Int?, goals: List<Goal> = emptyList()) = Match(
        id = "1",
        competitionSlug = "bra.1",
        date = Instant.parse("2026-11-25T22:00:00Z"),
        status = MatchStatus(state),
        home = MatchSide(flamengo, home),
        away = MatchSide(palmeiras, away),
        goals = goals,
    )

    @Test
    fun `inicio do jogo`() {
        val events = MatchEvents.detect(LastSeen(MatchState.SCHEDULED, null, null), match(MatchState.LIVE, 0, 0))
        assertEquals(listOf(MatchEvent.Started), events)
    }

    @Test
    fun `nada muda, nada avisa`() {
        assertTrue(MatchEvents.detect(LastSeen(MatchState.LIVE, 1, 0), match(MatchState.LIVE, 1, 0)).isEmpty())
        assertTrue(MatchEvents.detect(LastSeen(MatchState.SCHEDULED, null, null), match(MatchState.SCHEDULED, null, null)).isEmpty())
    }

    @Test
    fun `gol com autor e minuto`() {
        val goal = Goal("2029", "67'", "Vitor Roque")
        val events = MatchEvents.detect(
            LastSeen(MatchState.LIVE, 1, 0),
            match(MatchState.LIVE, 1, 1, listOf(Goal("819", "10'", "Pedro"), goal)),
        )
        assertEquals(listOf(MatchEvent.GoalScored("2029", 1, 1, goal)), events)
    }

    @Test
    fun `inicio e gol na mesma consulta, gols de dois times em ordem`() {
        val g1 = Goal("819", "3'", "Pedro")
        val g2 = Goal("2029", "5'", "Estêvão")
        val events = MatchEvents.detect(
            LastSeen(MatchState.SCHEDULED, null, null),
            match(MatchState.LIVE, 1, 1, listOf(g1, g2)),
        )
        assertEquals(
            listOf(MatchEvent.Started, MatchEvent.GoalScored("819", 1, 0, g1), MatchEvent.GoalScored("2029", 1, 1, g2)),
            events,
        )
    }

    @Test
    fun `gol ainda sem detalhe avisa sem autor`() {
        val events = MatchEvents.detect(LastSeen(MatchState.LIVE, 0, 0), match(MatchState.LIVE, 1, 0))
        assertEquals(listOf(MatchEvent.GoalScored("819", 1, 0, null)), events)
    }

    @Test
    fun `gol no fim do jogo avisa, jogo inteiro perdido nao`() {
        val goal = Goal("819", "90'+3'", "Bruno Henrique")
        val lastMinute = MatchEvents.detect(LastSeen(MatchState.LIVE, 1, 1), match(MatchState.FINISHED, 2, 1, listOf(goal)))
        assertEquals(listOf(MatchEvent.GoalScored("819", 2, 1, goal)), lastMinute)
        val missed = MatchEvents.detect(LastSeen(MatchState.SCHEDULED, null, null), match(MatchState.FINISHED, 3, 0))
        assertTrue(missed.isEmpty())
    }

    @Test
    fun `gol anulado nao avisa`() {
        assertTrue(MatchEvents.detect(LastSeen(MatchState.LIVE, 2, 0), match(MatchState.LIVE, 1, 0)).isEmpty())
    }

    @Test
    fun `gols do scoreboard real`() {
        // Athletico-PR 4 x 2 Remo: visto pela última vez 2 x 1, terminou 4 x 2 logo depois.
        val real = EspnMapper.matches(Fixtures.decode<ScoreboardDto>("scoreboard_bra1_day.json"), "bra.1")
            .first { it.id == "401841318" }
        assertEquals(6, real.goals.size)
        val events = MatchEvents.detect(LastSeen(MatchState.LIVE, 2, 1), real)
        val goals = events.filterIsInstance<MatchEvent.GoalScored>()
        assertEquals(listOf("Leozinho", "Jorge Rivaldo", "Antonio Galeano"), goals.map { it.goal?.scorer })
        assertEquals(listOf("68'", "84'", "90'"), goals.map { it.goal?.clock })
        // Cada aviso com o placar parcial daquele gol.
        assertEquals(listOf(3 to 1, 4 to 1, 4 to 2), goals.map { it.home to it.away })
    }

    @Test
    fun `intervalo entre consultas`() {
        val rolling = match(MatchState.LIVE, 0, 0)
        val halftime = rolling.copy(status = MatchStatus(MatchState.LIVE, halftime = true))
        val scheduled = match(MatchState.SCHEDULED, null, null)
        assertEquals(java.time.Duration.ofSeconds(30), MatchEvents.pollInterval(listOf(scheduled, rolling), batterySaver = false))
        assertEquals(java.time.Duration.ofMinutes(1), MatchEvents.pollInterval(listOf(halftime, scheduled), batterySaver = false))
        assertEquals(java.time.Duration.ofMinutes(1), MatchEvents.pollInterval(emptyList(), batterySaver = false))
        assertEquals(java.time.Duration.ofMinutes(2), MatchEvents.pollInterval(listOf(rolling), batterySaver = true))
        assertEquals(java.time.Duration.ofMinutes(3), MatchEvents.pollInterval(listOf(halftime), batterySaver = true))
    }

    @Test
    fun `janela de consulta`() {
        val kickoff = Instant.parse("2026-11-25T22:00:00Z")
        assertFalse(MatchEvents.isDue(kickoff, MatchState.SCHEDULED, kickoff.minusSeconds(5 * 60)))
        assertTrue(MatchEvents.isDue(kickoff, MatchState.SCHEDULED, kickoff.minusSeconds(60)))
        assertTrue(MatchEvents.isDue(kickoff, MatchState.LIVE, kickoff.plusSeconds(5 * 3600)))
        assertFalse(MatchEvents.isDue(kickoff, MatchState.SCHEDULED, kickoff.plusSeconds(5 * 3600)))
        assertFalse(MatchEvents.isDue(kickoff, MatchState.LIVE, kickoff.plusSeconds(7 * 3600)))
    }
}
