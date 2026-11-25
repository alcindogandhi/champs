package io.github.alcindogandhi.champs.data

import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.data.remote.Body
import io.github.alcindogandhi.champs.data.remote.EspnClient
import io.github.alcindogandhi.champs.data.remote.EspnClient.Companion.localeQuery
import io.github.alcindogandhi.champs.data.remote.EspnClient.Companion.SITE
import io.github.alcindogandhi.champs.data.remote.EspnClient.Companion.STANDINGS
import io.github.alcindogandhi.champs.data.remote.EspnMapper
import io.github.alcindogandhi.champs.data.remote.LeadersDto
import io.github.alcindogandhi.champs.data.remote.ScheduleDto
import io.github.alcindogandhi.champs.data.remote.ScoreboardDto
import io.github.alcindogandhi.champs.data.remote.StandingsDto
import io.github.alcindogandhi.champs.data.remote.SummaryDto
import io.github.alcindogandhi.champs.data.remote.TeamsDto
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.MatchDetail
import io.github.alcindogandhi.champs.domain.MatchState
import io.github.alcindogandhi.champs.domain.Scorer
import io.github.alcindogandhi.champs.domain.Season
import io.github.alcindogandhi.champs.domain.SeasonBuilder
import io.github.alcindogandhi.champs.domain.StandingGroup
import io.github.alcindogandhi.champs.domain.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Dado carregado: [fresh] indica que veio da rede agora; senão é o último guardado em cache. */
data class Loaded<T>(val data: T, val fetchedAt: Long, val fresh: Boolean)

data class TeamSchedule(val results: List<Match>, val fixtures: List<Match>)

/**
 * Fonte de dados de futebol. Cada função devolve um fluxo que emite primeiro o que estiver em cache e,
 * se o cache estiver vencido (ou [force] for true), o dado novo da rede. Erros de rede são lançados no fluxo.
 */
interface FootballDataSource {
    fun season(slug: String, force: Boolean = false): Flow<Loaded<Season>>
    fun standings(slug: String, force: Boolean = false): Flow<Loaded<List<StandingGroup>>>
    fun scorers(slug: String, force: Boolean = false): Flow<Loaded<List<Scorer>>>
    fun matchesOn(slug: String, date: LocalDate, force: Boolean = false): Flow<Loaded<List<Match>>>
    fun matchDetail(slug: String, eventId: String, force: Boolean = false): Flow<Loaded<MatchDetail>>
    fun teams(slug: String, force: Boolean = false): Flow<Loaded<List<Team>>>
    fun teamSchedule(slug: String, teamId: String, force: Boolean = false): Flow<Loaded<TeamSchedule>>
    suspend fun clearCache()
}

class EspnDataSource(
    private val client: EspnClient,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : FootballDataSource {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private fun <T> resource(
        url: String,
        force: Boolean,
        parse: (String) -> T,
        maxAge: (T?) -> Long,
    ): Flow<Loaded<T>> = flow<Loaded<T>> {
        val cached = client.cached(url)
        val cachedValue = cached?.let { body -> runCatching { parse(body.text) }.getOrNull() }
        if (cached != null && cachedValue != null) emit(Loaded(cachedValue, cached.fetchedAt, false))
        val expired = cached == null || cachedValue == null || client.currentTime() - cached.fetchedAt > maxAge(cachedValue)
        if (force || expired) {
            val body = client.fetch(url)
            emit(Loaded(parse(body.text), body.fetchedAt, true))
        }
    }.flowOn(Dispatchers.Default)

    override fun standings(slug: String, force: Boolean) = resource(
        url = "$STANDINGS/$slug/standings?${localeQuery()}",
        force = force,
        parse = { EspnMapper.standings(json.decodeFromString<StandingsDto>(it), CompetitionCatalog.get(slug)) },
        maxAge = { FIVE_MINUTES },
    )

    override fun scorers(slug: String, force: Boolean) = resource(
        url = "$SITE/$slug/statistics?${localeQuery()}",
        force = force,
        parse = { EspnMapper.scorers(json.decodeFromString<LeadersDto>(it)) },
        maxAge = { ONE_HOUR },
    )

    override fun matchesOn(slug: String, date: LocalDate, force: Boolean): Flow<Loaded<List<Match>>> {
        val today = LocalDate.now(zone())
        // A ESPN agrupa os jogos por dia no horário de Nova York. Um dia no fuso do aparelho cobre dois dias
        // de lá: o anterior, se o aparelho está a leste (Brasil, Europa), ou o seguinte, se está a oeste.
        val instant = date.atStartOfDay(zone()).toInstant()
        val eastOfEspn = zone().rules.getOffset(instant).totalSeconds >= ESPN_ZONE.rules.getOffset(instant).totalSeconds
        val days = if (eastOfEspn) listOf(date.minusDays(1), date) else listOf(date, date.plusDays(1))
        val urls = days.map { "$SITE/$slug/scoreboard?dates=${it.format(DAY)}&${localeQuery()}" }
        return multi(
            urls = urls,
            force = force,
            maxAge = { if (date == today) THIRTY_SECONDS else TEN_MINUTES },
        ) { bodies ->
            bodies.flatMap { EspnMapper.matches(json.decodeFromString<ScoreboardDto>(it), slug) }
                .distinctBy { it.id }
                .filter { m -> m.date?.atZone(zone())?.toLocalDate() == date }
                .sortedBy { it.date }
        }
    }

    override fun season(slug: String, force: Boolean): Flow<Loaded<Season>> = flow {
        // 1. Datas da temporada atual, informadas pelo scoreboard sem data.
        val currentUrl = "$SITE/$slug/scoreboard?${localeQuery()}"
        val cachedCurrent = client.cached(currentUrl)
        val current = cachedCurrent?.takeIf { client.currentTime() - it.fetchedAt < SIX_HOURS && !force }
            ?: runCatching { client.fetch(currentUrl) }.getOrElse { error -> cachedCurrent ?: throw error }
        val months = seasonMonths(json.decodeFromString<ScoreboardDto>(current.text))
        val urls = months.map { "$SITE/$slug/scoreboard?dates=${it.format(MONTH)}&${localeQuery()}" }
        val thisMonth = YearMonth.now(zone())
        // 2. Jogos mês a mês: meses passados quase não mudam, o mês corrente muda o tempo todo.
        multi(urls, force, maxAge = { url ->
            val month = months[urls.indexOf(url)]
            when {
                month == thisMonth || month == thisMonth.minusMonths(1) && LocalDate.now(zone()).dayOfMonth <= 2 -> ONE_MINUTE
                month < thisMonth -> ONE_WEEK
                else -> ONE_HOUR
            }
        }) { bodies ->
            val matches = bodies.flatMap { EspnMapper.matches(json.decodeFromString<ScoreboardDto>(it), slug) }.distinctBy { it.id }
            SeasonBuilder.build(matches)
        }.collect { emit(it) }
    }.flowOn(Dispatchers.Default)

    private fun seasonMonths(dto: ScoreboardDto): List<YearMonth> {
        val league = dto.leagues.firstOrNull()
        val start = EspnMapper.parseInstant(league?.season?.startDate ?: league?.calendarStartDate)
        val end = EspnMapper.parseInstant(league?.season?.endDate ?: league?.calendarEndDate)
        if (start == null || end == null) return listOf(YearMonth.now(zone()))
        // As datas da ESPN são meia-noite no fuso dos EUA: em UTC, o início cai no mês certo.
        var month = YearMonth.from(start.atZone(ZoneOffset.UTC))
        val last = YearMonth.from(end.atZone(ZoneOffset.UTC))
        val result = mutableListOf<YearMonth>()
        while (month <= last && result.size < 14) {
            result += month
            month = month.plusMonths(1)
        }
        return result
    }

    /** Vários endereços que formam um único dado: emite o cache completo e, depois, a versão atualizada. */
    private fun <T> multi(
        urls: List<String>,
        force: Boolean,
        maxAge: (String) -> Long,
        combine: (List<String>) -> T,
    ): Flow<Loaded<T>> = flow<Loaded<T>> {
        val cached = urls.associateWith { client.cached(it) }
        val complete = cached.values.all { it != null }
        if (complete) {
            runCatching { combine(urls.map { cached.getValue(it)!!.text }) }.getOrNull()?.let { value ->
                emit(Loaded(value, cached.values.minOf { it!!.fetchedAt }, false))
            }
        }
        val now = client.currentTime()
        val stale = urls.filter { url -> force || cached[url]?.let { now - it.fetchedAt > maxAge(url) } ?: true }
        if (stale.isEmpty()) return@flow
        val semaphore = Semaphore(4)
        val fetched: Map<String, Body> = coroutineScope {
            stale.map { url -> url to async { semaphore.withPermit { client.fetch(url) } } }
                .associate { (url, deferred) -> url to deferred.await() }
        }
        val bodies = urls.map { (fetched[it] ?: cached.getValue(it)!!) }
        emit(Loaded(combine(bodies.map { it.text }), bodies.minOf { it.fetchedAt }, true))
    }

    override fun matchDetail(slug: String, eventId: String, force: Boolean) = resource(
        url = "$SITE/$slug/summary?event=$eventId&${localeQuery()}",
        force = force,
        parse = { text ->
            EspnMapper.matchDetail(json.decodeFromString<SummaryDto>(text), slug, eventId, null)
                ?: error("Jogo $eventId sem dados")
        },
        maxAge = { detail ->
            when (detail?.match?.status?.state) {
                MatchState.FINISHED -> ONE_DAY
                MatchState.LIVE -> TWENTY_SECONDS
                else -> ONE_MINUTE
            }
        },
    )

    override fun teams(slug: String, force: Boolean) = resource(
        url = "$SITE/$slug/teams?${localeQuery()}",
        force = force,
        parse = { EspnMapper.teams(json.decodeFromString<TeamsDto>(it)) },
        maxAge = { ONE_DAY },
    )

    override fun teamSchedule(slug: String, teamId: String, force: Boolean): Flow<Loaded<TeamSchedule>> = multi(
        urls = listOf(
            "$SITE/$slug/teams/$teamId/schedule?${localeQuery()}",
            "$SITE/$slug/teams/$teamId/schedule?fixture=true&${localeQuery()}",
        ),
        force = force,
        maxAge = { TEN_MINUTES },
    ) { (results, fixtures) ->
        TeamSchedule(
            results = EspnMapper.schedule(json.decodeFromString<ScheduleDto>(results), slug)
                .filter { it.isFinished || it.isLive }
                .sortedByDescending { it.date },
            fixtures = EspnMapper.schedule(json.decodeFromString<ScheduleDto>(fixtures), slug)
                .filterNot { it.isFinished }
                .sortedBy { it.date },
        )
    }

    override suspend fun clearCache() = client.clear().let { }

    private companion object {
        val ESPN_ZONE: ZoneId = ZoneId.of("America/New_York")
        val DAY: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMM")
        const val TWENTY_SECONDS = 20_000L
        const val THIRTY_SECONDS = 30_000L
        const val ONE_MINUTE = 60_000L
        const val FIVE_MINUTES = 5 * ONE_MINUTE
        const val TEN_MINUTES = 10 * ONE_MINUTE
        const val ONE_HOUR = 60 * ONE_MINUTE
        const val SIX_HOURS = 6 * ONE_HOUR
        const val ONE_DAY = 24 * ONE_HOUR
        const val ONE_WEEK = 7 * ONE_DAY
    }
}
