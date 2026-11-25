package io.github.alcindogandhi.champs.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.data.settings.TableColumn
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.MatchSide
import io.github.alcindogandhi.champs.domain.MatchState
import io.github.alcindogandhi.champs.domain.MatchStatus
import io.github.alcindogandhi.champs.domain.StandingGroup
import io.github.alcindogandhi.champs.domain.StandingRow
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.ui.common.MatchRow
import io.github.alcindogandhi.champs.ui.common.standingsTable
import io.github.alcindogandhi.champs.ui.theme.ChampsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    // Textos lidos dos recursos: o teste passa em qualquer idioma do aparelho.
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val flamengo = Team("819", "Flamengo")
    private val palmeiras = Team("2029", "Palmeiras")

    @Test
    fun tabelaMostraTimesPontosEColunasEscolhidas() {
        var clicked: String? = null
        val rows = listOf(
            StandingRow(1, flamengo, 36, 24, 8, 4, 74, 31, 80),
            StandingRow(2, palmeiras, 37, 21, 11, 5, 60, 25, 74),
        )
        compose.setContent {
            ChampsTheme {
                LazyColumn {
                    standingsTable(
                        groups = listOf(StandingGroup(null, rows)),
                        columns = setOf(TableColumn.PLAYED, TableColumn.GOAL_DIFFERENCE),
                        onTeamClick = { clicked = it.team.id },
                    )
                }
            }
        }
        compose.onNodeWithText("Flamengo").assertIsDisplayed()
        compose.onNodeWithText("+43").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.standing_row_description, 2, "Palmeiras", 74)).performClick()
        assertEquals("2029", clicked)
    }

    @Test
    fun linhaDeJogoMostraPlacarEStatus() {
        val match = Match(
            id = "1",
            competitionSlug = "bra.1",
            date = Instant.parse("2026-11-21T21:30:00Z"),
            status = MatchStatus(MatchState.FINISHED),
            home = MatchSide(palmeiras, 1),
            away = MatchSide(flamengo, 1),
        )
        compose.setContent { ChampsTheme { MatchRow(match, onClick = {}) } }
        compose.onNodeWithText("1 × 1").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.match_finished), substring = true).assertIsDisplayed()
    }
}
