package io.github.alcindogandhi.champs.data.remote

import io.github.alcindogandhi.champs.Fixtures
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.domain.EventKind
import io.github.alcindogandhi.champs.domain.MatchState
import io.github.alcindogandhi.champs.domain.ZoneKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EspnMapperTest {

    @Test
    fun `scoreboard do dia vira jogos com placar, status e times`() {
        val matches = EspnMapper.matches(Fixtures.decode<ScoreboardDto>("scoreboard_bra1_day.json"), "bra.1")
        val match = matches.first { it.id == "401841318" }
        assertEquals("Athletico-PR", match.home.team.name)
        assertEquals("Remo", match.away.team.name)
        assertEquals(4, match.home.score)
        assertEquals(2, match.away.score)
        assertEquals(MatchState.FINISHED, match.status.state)
        assertEquals(Instant.parse("2026-11-22T19:00:00Z"), match.date)
        assertEquals("2026-brasileiro-serie-a", match.phaseSlug)
        assertEquals("Arena da Baixada", match.venue)
        assertTrue(match.home.team.logo!!.startsWith("https://"))
    }

    @Test
    fun `jogo ainda nao iniciado fica sem placar`() {
        val matches = EspnMapper.matches(Fixtures.decode<ScoreboardDto>("scoreboard_bra1_month.json"), "bra.1")
        val scheduled = matches.filter { it.status.state == MatchState.SCHEDULED }
        assertTrue(scheduled.isNotEmpty())
        assertTrue(scheduled.all { it.home.score == null && it.away.score == null })
    }

    @Test
    fun `mata-mata traz agregado e vencedor do confronto`() {
        val matches = EspnMapper.matches(Fixtures.decode<ScoreboardDto>("scoreboard_copadobrasil_month.json"), "bra.copa_do_brazil")
        val volta = matches.first { it.home.team.abbreviation == "CAM" && it.away.team.abbreviation == "CRU" }
        assertEquals("quarterfinals", volta.phaseSlug)
        assertEquals(3 to 2, volta.aggregate)
        assertEquals(volta.home.team.id, volta.seriesWinnerId)
        assertTrue(volta.note.startsWith("Volta"))
    }

    @Test
    fun `classificacao de pontos corridos usa zonas do catalogo`() {
        val groups = EspnMapper.standings(Fixtures.decode<StandingsDto>("standings_bra1.json"), CompetitionCatalog.get("bra.1"))
        assertEquals(1, groups.size)
        val rows = groups.single().rows
        assertEquals(20, rows.size)
        assertEquals((1..20).toList(), rows.map { it.rank })
        val leader = rows.first()
        assertEquals("Flamengo", leader.team.name)
        assertEquals(80, leader.points)
        assertEquals(36, leader.played)
        assertEquals(24 * 3 + 8, leader.points)
        assertEquals(43, leader.goalDifference)
        assertEquals(ZoneKind.CONTINENTAL, leader.zone?.kind)
        assertEquals(ZoneKind.RELEGATION, rows.last().zone?.kind)
        assertNull(rows[13].zone)
    }

    @Test
    fun `classificacao com grupos usa as zonas da ESPN`() {
        val groups = EspnMapper.standings(
            Fixtures.decode<StandingsDto>("standings_libertadores.json"),
            CompetitionCatalog.get("conmebol.libertadores"),
        )
        assertEquals(8, groups.size)
        assertEquals("Grupo A", groups.first().name)
        assertTrue(groups.all { it.rows.size == 4 })
        val first = groups.first().rows.first()
        assertNotNull(first.zone)
        assertTrue(first.zone!!.color.startsWith("#"))
        assertNotNull(first.zone!!.description)
    }

    @Test
    fun `artilheiros ordenados com empates dividindo a posicao`() {
        val scorers = EspnMapper.scorers(Fixtures.decode<LeadersDto>("statistics_bra1.json"))
        assertTrue(scorers.size >= 10)
        assertEquals("Kevin Viveros", scorers.first().playerName)
        assertEquals(21, scorers.first().goals)
        assertEquals("Athletico Paranaense", scorers.first().team?.name)
        assertEquals(32, scorers.first().appearances)
        assertTrue(scorers.zipWithNext().all { (a, b) -> a.goals >= b.goals })
        scorers.zipWithNext().filter { (a, b) -> a.goals == b.goals }.forEach { (a, b) -> assertEquals(a.rank, b.rank) }
    }

    @Test
    fun `detalhe do jogo traz lances, escalacoes e estatisticas`() {
        val detail = EspnMapper.matchDetail(Fixtures.decode<SummaryDto>("summary_bra1.json"), "bra.1", "401841318", null)
        assertNotNull(detail)
        detail!!
        assertEquals(4, detail.match.home.score)
        assertEquals(2, detail.match.away.score)
        assertTrue(detail.plays.size > 100)
        val goals = detail.plays.filter { it.kind == EventKind.GOAL }
        assertEquals(6, goals.size)
        assertEquals(4, goals.count { it.isHome == true })
        assertTrue(detail.plays.any { it.kind == EventKind.YELLOW_CARD })
        assertTrue(detail.plays.any { it.kind == EventKind.SUBSTITUTION })

        val home = detail.homeLineup!!
        assertEquals("3-4-2-1", home.formation)
        assertEquals(11, home.starters.size)
        assertEquals(12, home.substitutes.size)
        assertTrue(home.substitutes.any { it.subbedIn })
        assertEquals(11, detail.awayLineup!!.starters.size)

        val possession = detail.stats.first { it.key == "possessionPct" }
        assertEquals(100.0, possession.home + possession.away, 0.5)
        assertEquals("51%", possession.homeText)
    }

    @Test
    fun `times e agenda do time`() {
        val teams = EspnMapper.teams(Fixtures.decode<TeamsDto>("teams_bra1.json"))
        assertEquals(20, teams.size)
        val schedule = EspnMapper.schedule(Fixtures.decode<ScheduleDto>("schedule_team.json"), "bra.1")
        assertTrue(schedule.size >= 30)
        val last = schedule.first { it.id == "401841318" }
        assertEquals(4, last.home.score)
        assertEquals(2, last.away.score)
    }

    @Test
    fun `intervalo do jogo vem marcado`() {
        val halftime = EspnMapper.status(StatusDto(displayClock = "45'", type = StatusTypeDto(name = "STATUS_HALFTIME", state = "in")))
        assertEquals(MatchState.LIVE, halftime.state)
        assertTrue(halftime.halftime)
        val rolling = EspnMapper.status(StatusDto(displayClock = "67'", type = StatusTypeDto(name = "STATUS_SECOND_HALF", state = "in")))
        assertEquals(false, rolling.halftime)
    }

    @Test
    fun `datas da ESPN em varios formatos`() {
        assertEquals(Instant.parse("2026-11-22T19:00:00Z"), EspnMapper.parseInstant("2026-11-22T19:00Z"))
        assertEquals(Instant.parse("2026-01-01T05:05:00Z"), EspnMapper.parseInstant("2026-01-01T05:05:00.000+00:00"))
        assertNull(EspnMapper.parseInstant("ontem"))
    }
}
