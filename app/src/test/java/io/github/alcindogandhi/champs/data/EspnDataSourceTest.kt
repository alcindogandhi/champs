package io.github.alcindogandhi.champs.data

import io.github.alcindogandhi.champs.Fixtures
import io.github.alcindogandhi.champs.data.remote.EspnClient
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

class EspnDataSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    // O interceptor roda em várias threads (os meses são baixados em paralelo).
    private val requests = CopyOnWriteArrayList<String>()

    /** ESPN falsa: sem data → datas da temporada; mês → temporada inteira; dia → placar do dia alterado. */
    private fun dataSource(): EspnDataSource {
        val season = Fixtures.read("season_bra1_2026.json")
        val seasonDates = Fixtures.read("scoreboard_bra1_month.json")
        // No placar do dia, o Athletico (mandante) marcou mais um: 5 x 2 em vez de 4 x 2.
        val day = Fixtures.read("scoreboard_bra1_day.json")
            .replace("\"homeAway\":\"home\",\"winner\":true,\"form\":\"VDDDE\",\"score\":\"4\"", "\"homeAway\":\"home\",\"winner\":true,\"form\":\"VDDDE\",\"score\":\"5\"")
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url
            requests += url.toString()
            val dates = url.queryParameter("dates")
            val body = when (dates?.length) {
                null -> seasonDates
                6 -> season
                else -> day
            }
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        return EspnDataSource(
            EspnClient(http, folder.newFolder("espn")),
            zone = { ZoneId.of("America/Sao_Paulo") },
            // 25/11, 19:00 em São Paulo (17:00 em Nova York): um único dia da ESPN cobre os jogos ao vivo.
            now = { Instant.parse("2026-11-25T22:00:00Z") },
        )
    }

    @Test
    fun `atualizacao ao vivo baixa so o placar do dia e aplica sobre a temporada em cache`() = runTest {
        val source = dataSource()
        val before = source.season("bra.1").last().data
        assertEquals(4, before.matches.first { it.id == "401841318" }.home.score)
        requests.clear()

        val live = source.seasonLive("bra.1").last().data

        assertEquals(5, live.matches.first { it.id == "401841318" }.home.score)
        // Só o placar de hoje da ESPN; nenhum mês é baixado de novo.
        assertEquals(1, requests.size)
        assertTrue(requests.single().contains("dates=20261125&"))
        assertTrue(requests.all { (Regex("dates=(\\d+)").find(it)?.groupValues?.get(1)?.length ?: 0) == 8 })
        // As rodadas continuam as mesmas.
        assertEquals(before.phases.single().rounds.size, live.phases.single().rounds.size)
    }

    @Test
    fun `vigia de jogos baixa um unico dia da ESPN, no horario de Nova York`() = runTest {
        val source = dataSource()
        // 03/12 às 00:30 UTC é 02/12 às 19:30 em Nova York: a ESPN lista o jogo no dia 02.
        val kickoff = Instant.parse("2026-12-03T00:30:00Z")
        source.matchesOfEspnDay("bra.1", kickoff, force = true).last()
        assertEquals(1, requests.size)
        assertTrue(requests.single().contains("dates=20261202&"))
        requests.clear()
        source.matchesOfEspnDay("bra.1", kickoff, dayOffset = 1, force = true).last()
        assertTrue(requests.single().contains("dates=20261203&"))
    }

    @Test
    fun `forcar a temporada baixa todos os meses de novo`() = runTest {
        val source = dataSource()
        source.season("bra.1").last()
        requests.clear()
        source.season("bra.1", force = true).last()
        assertTrue(requests.joinToString("\n"), requests.count { Regex("dates=\\d{6}&").containsMatchIn(it) } >= 12)
    }
}
