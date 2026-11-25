package io.github.alcindogandhi.champs.data

import io.github.alcindogandhi.champs.Fixtures
import io.github.alcindogandhi.champs.data.remote.EspnClient
import io.github.alcindogandhi.champs.data.remote.ge.GeJogoDto
import io.github.alcindogandhi.champs.data.remote.ge.GeMapper
import io.github.alcindogandhi.champs.data.remote.ge.GePhaseV1Dto
import io.github.alcindogandhi.champs.domain.MatchState
import io.github.alcindogandhi.champs.domain.PhaseKind
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class GeDataSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json = Fixtures.json
    private val now = Instant.parse("2026-11-25T15:00:00Z")

    private fun ge(name: String) = Fixtures.read("../ge/$name")

    @Test
    fun `pagina v1 traz id da tabela e fases com nomes`() {
        val page = GeMapper.pageV1(ge("page_v1_paranaense.html"), json)!!
        assertEquals("0ea83e27-84f3-4801-84ea-22b51845b1c7", page.tableId)
        assertEquals(5, page.phases.size)
        assertEquals("Quartas de final", page.phases[2].nome)
        assertNull(GeMapper.pageV2(ge("page_v1_paranaense.html"), json))
    }

    @Test
    fun `classificacao v1 de pontos corridos com faixas nomeadas`() {
        val dto = json.decodeFromString<GePhaseV1Dto>(ge("classificacao_seriea.json"))
        val groups = GeMapper.standingsV1(dto, null)
        val rows = groups.single().rows
        assertEquals(20, rows.size)
        assertEquals("Flamengo", rows.first().team.name)
        assertEquals(80, rows.first().points)
        assertTrue(rows.first().team.id.startsWith(GeMapper.ID_PREFIX))
        assertTrue(rows.first().team.logo!!.endsWith(".svg"))
        assertEquals("Libertadores", rows.first().zone?.description)
        assertEquals("Rebaixados", rows.last().zone?.description)
        assertEquals(38, GeMapper.lastRound(dto))
    }

    @Test
    fun `jogos v1 com horario de Brasilia e status deduzido`() {
        val games = json.decodeFromString<List<GeJogoDto>>(ge("rodada37_seriea.json"))
            .map { GeMapper.matchV1(it, "ge.x", "fase", now) }
        assertEquals(10, games.size)
        val chape = games.first { it.home.team.name == "Chapecoense" }
        assertEquals(MatchState.FINISHED, chape.status.state)
        assertEquals(3, chape.home.score)
        assertEquals(0, chape.away.score)
        assertEquals(Instant.parse("2026-11-24T21:00:00Z"), chape.date) // 18:00 em Brasília
        val future = games.filter { it.status.state == MatchState.SCHEDULED }
        assertTrue(future.isNotEmpty())
        assertTrue(future.all { it.home.score == null })
    }

    @Test
    fun `mata-mata v1 com agregado e penaltis`() {
        val dto = json.decodeFromString<GePhaseV1Dto>(ge("classificacao_copadobrasil_semifinal.json"))
        assertTrue(GeMapper.isKnockout(dto))
        val ties = GeMapper.tiesV1(dto, "ge.x", "semifinal", now)
        assertEquals(2, ties.size)
        val palVas = ties.first { t -> setOf(t.first.name, t.second.name).any { it.startsWith("Vasco") } }
        assertEquals(2, palVas.firstGoals)
        assertEquals(2, palVas.secondGoals)
        val palmeiras = listOf(palVas.first, palVas.second).first { it.name == "Palmeiras" }
        assertEquals(palmeiras.id, palVas.winnerId) // 5 x 4 nos pênaltis
    }

    @Test
    fun `pagina v2 traz grupos, mata-mata e rodada atual`() {
        val page = GeMapper.pageV2(ge("page_v2_pernambucano.html"), json)!!
        assertNull(GeMapper.pageV1(ge("page_v2_pernambucano.html"), json))
        val standings = GeMapper.standingsV2(page)
        assertTrue(standings.size >= 2)
        assertTrue(standings.first().name!!.startsWith("Quadrangular"))
        assertTrue(standings.all { g -> g.rows.isNotEmpty() && g.rows.all { it.team.name.isNotBlank() } })
        val phases = GeMapper.phasesV2(page, "ge.pernambucano")
        assertTrue(phases.any { it.kind == PhaseKind.KNOCKOUT && it.ties.isNotEmpty() })
        val league = phases.first { it.kind == PhaseKind.LEAGUE }
        assertTrue(league.rounds.single().matches.isNotEmpty())
        val finished = phases.flatMap { it.matches }.filter { it.isFinished }
        assertTrue(finished.isNotEmpty() && finished.all { it.home.score != null })
    }

    // ---- ponta a ponta com o ge simulado (Paranaense, v1)

    private val requests = CopyOnWriteArrayList<String>()

    private fun source(): GeDataSource {
        val api = json.parseToJsonElement(ge("paranaense_api.json")).jsonObject
        val phases = api["phases"]!!.jsonObject
        val rounds = api["rounds"]!!.jsonObject
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            requests += url
            val body = when {
                url.startsWith("https://ge.globo.com/") -> ge("page_v1_paranaense.html")
                url.endsWith("/classificacao/") -> phases[url.substringAfter("/fase/").substringBefore("/")].toString()
                "/rodada/" in url -> {
                    val phase = url.substringAfter("/fase/").substringBefore("/")
                    val n = url.substringAfter("/rodada/").substringBefore("/")
                    (rounds["$phase/$n"] ?: JsonObject(emptyMap())).toString().let { if (it == "{}") "[]" else it }
                }
                else -> "{}"
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return GeDataSource(EspnClient(http, folder.newFolder("ge")), pagePath = { "pr/futebol/campeonato-paranaense" }, now = { now })
    }

    @Test
    fun `temporada e tabela do Paranaense pelo ge`() = runTest {
        val source = source()
        val season = source.season("ge.paranaense").last().data
        assertEquals(listOf("Primeira fase", "Descenso", "Quartas de final", "Semifinal", "Final"), season.phases.map { it.name })
        assertEquals(PhaseKind.LEAGUE, season.phases.first().kind)
        assertEquals(PhaseKind.KNOCKOUT, season.phases.last().kind)
        val final = season.phases.last().ties.single()
        assertNotNull(final.winnerId)
        val standings = source.standings("ge.paranaense").last().data
        assertTrue(standings.isNotEmpty())
        // Mais de uma fase com tabela: o nome da fase acompanha o do grupo.
        assertTrue(standings.all { it.name != null })
    }

    @Test
    fun `atualizacao ao vivo pede so a fase atual e a rodada atual`() = runTest {
        val source = source()
        source.season("ge.paranaense").last()
        requests.clear()
        source.seasonLive("ge.paranaense").last()
        // A fase atual (final) é forçada; as demais e as rodadas antigas vêm do cache.
        assertTrue(requests.joinToString("\n"), requests.size <= 2)
        assertTrue(requests.any { it.contains("final-paranaense-2026/classificacao/") })
        assertTrue(requests.none { it.startsWith("https://ge.globo.com/") })
    }
}
