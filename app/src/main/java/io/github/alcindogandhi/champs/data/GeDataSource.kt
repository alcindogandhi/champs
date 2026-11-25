package io.github.alcindogandhi.champs.data

import io.github.alcindogandhi.champs.data.remote.EspnClient
import io.github.alcindogandhi.champs.data.remote.ge.GeJogoDto
import io.github.alcindogandhi.champs.data.remote.ge.GeMapper
import io.github.alcindogandhi.champs.data.remote.ge.GePhaseV1Dto
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.MatchDetail
import io.github.alcindogandhi.champs.domain.Scorer
import io.github.alcindogandhi.champs.domain.Season
import io.github.alcindogandhi.champs.domain.StandingGroup
import io.github.alcindogandhi.champs.domain.Team
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Campeonatos que a ESPN não cobre, vindos do ge (globoesporte), em dois formatos:
 *
 * - **v1:** a página do campeonato informa o id da tabela e as fases; os dados vêm da API JSON pública
 *   `api.globoesporte.globo.com/tabela/{id}/fase/{fase}/classificacao/` e `.../rodada/{n}/jogos/`.
 * - **v2:** a página já traz a tabela e os jogos embutidos ("getStandingData" e "getMatchListData"); atualizar
 *   exige baixar a página de novo (~120 KB), então nunca mais que a cada 2 min.
 *
 * O ge não tem artilharia, lances, escalações nem estatísticas por API: esses dados ficam vazios e o detalhe
 * do jogo aponta para a página do jogo no ge.
 */
class GeDataSource(
    private val client: EspnClient,
    /** Caminho da página do campeonato no ge (ex.: "pr/futebol/campeonato-paranaense"), pelo id do app. */
    private val pagePath: (String) -> String?,
    private val now: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : FootballDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    /** NORMAL respeita o cache; FORCE atualiza o que pode ter mudado; LIVE só o necessário para placares. */
    private enum class Mode { NORMAL, FORCE, LIVE }

    private class Snapshot(val season: Season, val standings: List<StandingGroup>, val fetchedAt: Long)

    /** Leituras de uma carga: cache válido, rede ou, se a rede falhar, o cache vencido (anotando a falha). */
    private inner class Fetch {
        var failure: Throwable? = null
        var oldest = Long.MAX_VALUE
        private val semaphore = Semaphore(6)

        suspend fun get(url: String, maxAge: Duration, force: Boolean): String = semaphore.withPermit {
            val cached = client.cached(url)
            if (!force && cached != null && client.currentTime() - cached.fetchedAt <= maxAge.toMillis()) {
                oldest = minOf(oldest, cached.fetchedAt)
                return@withPermit cached.text
            }
            try {
                client.fetch(url).also { oldest = minOf(oldest, it.fetchedAt) }.text
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (cached == null) throw e
                failure = failure ?: e
                oldest = minOf(oldest, cached.fetchedAt)
                cached.text
            }
        }
    }

    private suspend fun snapshot(slug: String, mode: Mode): Pair<Snapshot, Throwable?> {
        val path = pagePath(slug) ?: error("Campeonato desconhecido: $slug")
        val pageUrl = "$PAGE_BASE/$path/"
        val fetch = Fetch()
        // No v1 a página só diz o id e as fases (muda pouco); no v2 ela é o próprio dado.
        val cachedIsV2 = client.cached(pageUrl)?.text?.contains(V2_MARKER) == true
        val pageMaxAge = when {
            !cachedIsV2 -> PAGE_V1_MAX_AGE
            mode == Mode.NORMAL -> FIVE_MINUTES
            else -> V2_MIN_INTERVAL
        }
        val html = fetch.get(pageUrl, pageMaxAge, force = cachedIsV2 && mode == Mode.FORCE)
        val snapshot = GeMapper.pageV1(html, json)?.let { v1(slug, it, mode, fetch) }
            ?: GeMapper.pageV2(html, json)?.let { page ->
                Snapshot(Season(GeMapper.phasesV2(page, slug)), GeMapper.standingsV2(page), 0)
            }
            ?: error("Página do ge sem tabela: $path")
        val fetchedAt = if (fetch.oldest == Long.MAX_VALUE) client.currentTime() else fetch.oldest
        return Snapshot(snapshot.season, snapshot.standings, fetchedAt) to fetch.failure
    }

    private suspend fun v1(slug: String, page: GeMapper.PageV1, mode: Mode, fetch: Fetch): Snapshot = coroutineScope {
        val base = "$API_BASE/${page.tableId}/fase"
        val phases = page.phases.map { nav ->
            async {
                val url = "$base/${nav.slug}/classificacao/"
                val force = mode == Mode.FORCE || mode == Mode.LIVE && nav.atual
                nav to json.decodeFromString<GePhaseV1Dto>(fetch.get(url, FIVE_MINUTES, force))
            }
        }.awaitAll()
        val instant = now()
        val built = phases.map { (nav, dto) ->
            async {
                val rounds: Map<Int, List<GeJogoDto>> = if (GeMapper.isKnockout(dto)) {
                    emptyMap()
                } else {
                    val current = GeMapper.currentRound(dto) ?: 1
                    val last = GeMapper.lastRound(dto)
                    if (last == null) {
                        mapOf(current to dto.listaJogos)
                    } else {
                        (1..last).map { n ->
                            async {
                                val force = when (mode) {
                                    Mode.FORCE -> n in current - 1..current + 1
                                    Mode.LIVE -> n == current
                                    Mode.NORMAL -> false
                                }
                                val text = fetch.get("$base/${nav.slug}/rodada/$n/jogos/", roundMaxAge(n, current), force)
                                n to json.decodeFromString<List<GeJogoDto>>(text)
                            }
                        }.awaitAll().toMap()
                    }
                }
                GeMapper.phaseV1(nav.nome, slug, nav.slug, dto, rounds, instant)
            }
        }.awaitAll()
        // Tabela: fases de liga e de grupos, a mais recente primeiro; com o nome da fase se houver mais de uma.
        val leaguePhases = phases.filterNot { (_, dto) -> GeMapper.isKnockout(dto) }
        val standings = leaguePhases.reversed().flatMap { (nav, dto) ->
            GeMapper.standingsV1(dto, nav.nome.takeIf { leaguePhases.size > 1 })
        }
        Snapshot(Season(built.filter { it.rounds.isNotEmpty() || it.ties.isNotEmpty() }), standings, 0)
    }

    private fun roundMaxAge(round: Int, current: Int): Duration = when {
        round < current - 1 -> ONE_WEEK
        round <= current + 1 -> TWO_MINUTES
        else -> SIX_HOURS
    }

    private fun <T> from(slug: String, mode: Mode, pick: (Snapshot) -> T): Flow<Loaded<T>> = flow {
        val (snapshot, failure) = snapshot(slug, mode)
        emit(Loaded(pick(snapshot), snapshot.fetchedAt, failure == null))
        // Dado antigo mostrado porque a rede falhou: a tela exibe o aviso.
        if (failure != null) throw failure
    }.flowOn(Dispatchers.Default)

    private fun mode(force: Boolean) = if (force) Mode.FORCE else Mode.NORMAL

    override fun season(slug: String, force: Boolean) = from(slug, mode(force)) { it.season }

    override fun seasonLive(slug: String) = from(slug, Mode.LIVE) { it.season }

    override fun standings(slug: String, force: Boolean) = from(slug, mode(force)) { it.standings }

    /** O ge não tem artilharia por API. */
    override fun scorers(slug: String, force: Boolean): Flow<Loaded<List<Scorer>>> =
        flowOf(Loaded(emptyList(), client.currentTime(), true))

    override fun matchesOn(slug: String, date: LocalDate, force: Boolean): Flow<Loaded<List<Match>>> =
        from(slug, if (force) Mode.LIVE else Mode.NORMAL) { snapshot ->
            snapshot.season.matches.filter { it.date?.atZone(zone())?.toLocalDate() == date }.sortedBy { it.date }
        }

    /** Para o vigia de jogos: devolve os jogos da temporada com os placares de agora (o dia não importa). */
    override fun matchesOfEspnDay(slug: String, instant: Instant, dayOffset: Long, force: Boolean): Flow<Loaded<List<Match>>> =
        if (dayOffset != 0L) flowOf(Loaded(emptyList(), client.currentTime(), true)) else from(slug, Mode.LIVE) { it.season.matches }

    override fun matchDetail(slug: String, eventId: String, force: Boolean): Flow<Loaded<MatchDetail>> =
        from(slug, if (force) Mode.LIVE else Mode.NORMAL) { snapshot ->
            val match = snapshot.season.matches.firstOrNull { it.id == eventId } ?: error("Jogo $eventId não encontrado no ge")
            MatchDetail(match, plays = emptyList(), homeLineup = null, awayLineup = null, stats = emptyList())
        }

    override fun teams(slug: String, force: Boolean): Flow<Loaded<List<Team>>> = from(slug, mode(force)) { snapshot ->
        (snapshot.standings.flatMap { g -> g.rows.map { it.team } } + snapshot.season.teams).distinctBy { it.id }.sortedBy { it.name }
    }

    override fun teamSchedule(slug: String, teamId: String, force: Boolean): Flow<Loaded<TeamSchedule>> =
        from(slug, mode(force)) { snapshot ->
            val matches = snapshot.season.matches.filter { teamId in it.teamIds }
            TeamSchedule(
                results = matches.filter { it.isFinished || it.isLive }.sortedByDescending { it.date },
                fixtures = matches.filterNot { it.isFinished || it.isLive }.sortedBy { it.date },
            )
        }

    override fun teamFixtures(slug: String, teamId: String, force: Boolean): Flow<Loaded<List<Match>>> =
        from(slug, mode(force)) { snapshot ->
            snapshot.season.matches.filter { teamId in it.teamIds && !it.isFinished }.sortedBy { it.date }
        }

    override suspend fun clearCache() {
        client.clear()
    }

    private companion object {
        const val PAGE_BASE = "https://ge.globo.com"
        const val API_BASE = "https://api.globoesporte.globo.com/tabela"
        const val V2_MARKER = "\"getStandingData\":{\"status\""
        val TWO_MINUTES: Duration = Duration.ofMinutes(2)
        val FIVE_MINUTES: Duration = Duration.ofMinutes(5)
        val V2_MIN_INTERVAL: Duration = TWO_MINUTES
        val SIX_HOURS: Duration = Duration.ofHours(6)
        val PAGE_V1_MAX_AGE: Duration = Duration.ofHours(12)
        val ONE_WEEK: Duration = Duration.ofDays(7)
    }
}
