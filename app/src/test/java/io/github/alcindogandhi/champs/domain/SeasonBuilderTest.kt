package io.github.alcindogandhi.champs.domain

import io.github.alcindogandhi.champs.Fixtures
import io.github.alcindogandhi.champs.data.remote.EspnMapper
import io.github.alcindogandhi.champs.data.remote.ScoreboardDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SeasonBuilderTest {

    private val brasileirao by lazy {
        EspnMapper.matches(Fixtures.decode<ScoreboardDto>("season_bra1_2026.json"), "bra.1")
    }

    @Test
    fun `brasileirao tem 38 rodadas de 10 jogos com 20 times distintos`() {
        val season = SeasonBuilder.build(brasileirao)
        val phase = season.phases.single()
        assertEquals(PhaseKind.LEAGUE, phase.kind)
        assertEquals(38, phase.rounds.size)
        phase.rounds.forEach { round ->
            assertEquals("rodada ${round.number}", 10, round.matches.size)
            assertEquals("rodada ${round.number}", 20, round.matches.flatMap { it.teamIds }.toSet().size)
        }
    }

    @Test
    fun `jogo de reposicao herda a rodada do jogo adiado`() {
        val season = SeasonBuilder.build(brasileirao)
        val rounds = season.phases.single().rounds
        // Bahia x Chapecoense da 4ª rodada (25/02) foi adiado e jogado em 17/07 com outro id.
        val round4 = rounds[3].matches
        val replay = round4.first { it.home.team.abbreviation == "BAH" && it.away.team.abbreviation == "CHA" }
        assertEquals("401879459", replay.id)
        assertTrue(replay.isFinished)
        assertFalse(season.matches.any { it.status.state == MatchState.POSTPONED })
    }

    @Test
    fun `rodada atual e a primeira com a maioria dos jogos pendentes`() {
        val season = SeasonBuilder.build(brasileirao)
        val (phase, round) = SeasonBuilder.currentPosition(season)
        assertEquals(0, phase)
        val current = season.phases[0].rounds[round]
        assertTrue(current.matches.count { !it.isFinished } * 2 > current.matches.size)
        val previous = season.phases[0].rounds[round - 1]
        assertTrue(previous.matches.count { !it.isFinished } * 2 <= previous.matches.size)
    }

    @Test
    fun `mata-mata vira confrontos com agregado`() {
        val matches = EspnMapper.matches(Fixtures.decode<ScoreboardDto>("scoreboard_copadobrasil_month.json"), "bra.copa_do_brazil")
        val season = SeasonBuilder.build(matches)
        val phase = season.phases.single { it.slug == "quarterfinals" }
        assertEquals(PhaseKind.KNOCKOUT, phase.kind)
        assertEquals(4, phase.ties.size)
        val tie = phase.ties.first { it.teamIdsContain("CAM") }
        val cam = if (tie.first.abbreviation == "CAM") tie.first else tie.second
        assertEquals(cam.id, tie.winnerId)
    }

    private fun KnockoutTie.teamIdsContain(abbreviation: String) =
        first.abbreviation == abbreviation || second.abbreviation == abbreviation

    @Test
    fun `classifica fases pelo formato`() {
        fun m(id: Int, home: String, away: String) = Match(
            id = id.toString(), competitionSlug = "x", date = Instant.EPOCH.plusSeconds(id * 1000L),
            status = MatchStatus(MatchState.FINISHED),
            home = MatchSide(Team(home, home), 1), away = MatchSide(Team(away, away), 0),
        )
        val singleOpponent = listOf(m(1, "a", "b"), m(2, "c", "d"), m(3, "b", "a"), m(4, "d", "c"))
        val roundRobin = listOf(m(1, "a", "b"), m(2, "c", "d"), m(3, "a", "c"), m(4, "b", "d"))
        assertTrue(SeasonBuilder.isKnockout("second-round", singleOpponent))
        assertFalse(SeasonBuilder.isKnockout("first-round", roundRobin))
        assertFalse(SeasonBuilder.isKnockout("group-stage", singleOpponent))
        assertFalse(SeasonBuilder.isKnockout("2026-brasileiro-serie-a", singleOpponent))
        assertTrue(SeasonBuilder.isKnockout("taca-guanabara---final", roundRobin))
    }

    @Test
    fun `confronto sem jogos encerrados nao tem vencedor`() {
        val scheduled = Match(
            id = "1", competitionSlug = "x", date = Instant.EPOCH, status = MatchStatus(MatchState.SCHEDULED),
            home = MatchSide(Team("a", "A")), away = MatchSide(Team("b", "B")), phaseSlug = "final",
        )
        val tie = SeasonBuilder.build(listOf(scheduled)).phases.single().ties.single()
        assertNull(tie.winnerId)
        assertNull(tie.firstGoals)
    }

    @Test
    fun `janela de atualizacao ao vivo`() {
        val now = Instant.parse("2026-11-25T22:00:00Z")
        fun match(state: MatchState, date: Instant?) = Match(
            id = "1", competitionSlug = "x", date = date, status = MatchStatus(state),
            home = MatchSide(Team("a", "A")), away = MatchSide(Team("b", "B")),
        )
        assertTrue(match(MatchState.LIVE, now.minusSeconds(4 * 3600)).needsLiveRefresh(now))
        // Agendado que já devia ter começado, ou que começa em 1 min.
        assertTrue(match(MatchState.SCHEDULED, now.minusSeconds(10 * 60)).needsLiveRefresh(now))
        assertTrue(match(MatchState.SCHEDULED, now.plusSeconds(60)).needsLiveRefresh(now))
        // Longe do horário, encerrado ou adiado: não.
        assertFalse(match(MatchState.SCHEDULED, now.plusSeconds(3600)).needsLiveRefresh(now))
        assertFalse(match(MatchState.SCHEDULED, now.minusSeconds(4 * 3600)).needsLiveRefresh(now))
        assertFalse(match(MatchState.FINISHED, now).needsLiveRefresh(now))
        assertFalse(match(MatchState.POSTPONED, now).needsLiveRefresh(now))
        assertFalse(match(MatchState.SCHEDULED, null).needsLiveRefresh(now))
    }
}
