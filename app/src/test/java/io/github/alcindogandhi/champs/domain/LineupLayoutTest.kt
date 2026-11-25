package io.github.alcindogandhi.champs.domain

import io.github.alcindogandhi.champs.Fixtures
import io.github.alcindogandhi.champs.data.remote.EspnMapper
import io.github.alcindogandhi.champs.data.remote.SummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineupLayoutTest {

    @Test
    fun `escalacao 3-4-2-1 distribui os titulares nas linhas do esquema`() {
        val detail = EspnMapper.matchDetail(Fixtures.decode<SummaryDto>("summary_bra1.json"), "bra.1", "401841318", null)!!
        val lineup = detail.homeLineup!!
        val lines = LineupLayout.lines(lineup.formation, lineup.starters)
        assertEquals(listOf(1, 3, 4, 2, 1), lines.map { it.size })
        assertEquals("G", lines.first().single().position)
        assertEquals("A", lines.last().single().position)
        // Linha de meio-campo da esquerda para a direita.
        assertEquals("ME", lines[2].first().position)
        assertEquals("MD", lines[2].last().position)
    }

    @Test
    fun `sem esquema valido nao monta o campo`() {
        val players = List(11) { LineupPlayer("J$it", "$it", "M", it, true, false, false) }
        assertTrue(LineupLayout.lines(null, players).isEmpty())
        assertTrue(LineupLayout.lines("4-4-3", players).isEmpty())
        assertTrue(LineupLayout.lines("4-4-2", players.take(10)).isEmpty())
    }
}
