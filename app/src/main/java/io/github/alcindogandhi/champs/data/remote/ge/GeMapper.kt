package io.github.alcindogandhi.champs.data.remote.ge

import io.github.alcindogandhi.champs.domain.KnockoutTie
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.MatchSide
import io.github.alcindogandhi.champs.domain.MatchState
import io.github.alcindogandhi.champs.domain.MatchStatus
import io.github.alcindogandhi.champs.domain.Phase
import io.github.alcindogandhi.champs.domain.PhaseKind
import io.github.alcindogandhi.champs.domain.Round
import io.github.alcindogandhi.champs.domain.StandingGroup
import io.github.alcindogandhi.champs.domain.StandingRow
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.domain.ZoneInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Converte os dados do ge (v1 e v2) no modelo do app. */
object GeMapper {

    /** Os horários do ge são de Brasília. */
    private val BRASILIA: ZoneId = ZoneId.of("America/Sao_Paulo")

    /** Ids do ge (jogos e times) ganham prefixo para nunca colidirem com os da ESPN. */
    const val ID_PREFIX = "ge-"

    /** Sem status explícito no v1: um jogo começado há mais que isso é considerado encerrado. */
    private val MATCH_LENGTH: Duration = Duration.ofMinutes(150)

    @Serializable
    class Resolved<T>(val status: Int = 0, val body: T? = null)

    data class PageV1(val tableId: String, val phases: List<GeFaseNavDto>)

    data class PageV2(val standings: GeV2StandingsDto?, val matchGroups: List<GeV2MatchGroupDto>)

    // ---------------------------------------------------------------- páginas

    private val TABLE_ID = Regex("""tUUID:\s*"([0-9a-f-]{36})"""")

    fun pageV1(html: String, json: Json): PageV1? {
        val tableId = TABLE_ID.find(html)?.groupValues?.get(1) ?: return null
        val start = html.indexOf("const classificacao = ")
        val page = if (start >= 0) {
            extractObject(html, html.indexOf('{', start))?.let { runCatching { json.decodeFromString<GePageV1Dto>(it) }.getOrNull() }
        } else {
            null
        }
        val phases = page?.fasesNavegacao.orEmpty().ifEmpty {
            listOfNotNull(page?.fase?.slug?.let { GeFaseNavDto(it, null, true) })
        }
        if (phases.isEmpty()) return null
        return PageV1(tableId, phases)
    }

    fun pageV2(html: String, json: Json): PageV2? {
        val standings = resolved<GeV2StandingBodyDto>(html, "getStandingData", json)?.standings
        val matches = resolved<GeV2MatchListBodyDto>(html, "getMatchListData", json)?.matches.orEmpty()
        if (standings == null && matches.isEmpty()) return null
        return PageV2(standings, matches)
    }

    private inline fun <reified T> resolved(html: String, key: String, json: Json): T? {
        val marker = "\"$key\":{\"status\""
        val at = html.indexOf(marker).takeIf { it >= 0 } ?: return null
        val text = extractObject(html, at + key.length + 3) ?: return null
        return runCatching { json.decodeFromString<Resolved<T>>(text).body }.getOrNull()
    }

    /** Recorta o objeto JSON que começa em [start] ('{'), respeitando strings e escapes. */
    internal fun extractObject(text: String, start: Int): String? {
        if (start < 0 || start >= text.length || text[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> if (--depth == 0) return text.substring(start, i + 1)
            }
        }
        return null
    }

    // ---------------------------------------------------------------- comuns

    fun instant(date: String?, hour: String?): Instant? {
        if (date.isNullOrBlank()) return null
        val day = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return null
        val timeText = if (date.length > 10) date.substring(11) else hour
        val time = timeText?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.NOON
        return LocalDateTime.of(day, time).atZone(BRASILIA).toInstant()
    }

    private fun team(id: Long, name: String?, abbreviation: String?, logo: String?): Team {
        val display = name ?: abbreviation ?: id.toString()
        return Team(id = "$ID_PREFIX$id", name = display, shortName = display, abbreviation = abbreviation.orEmpty(), logo = logo)
    }

    private fun color(hex: String?) = hex?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("#")) it else "#$it" }

    // ---------------------------------------------------------------- v1

    fun isKnockout(dto: GePhaseV1Dto) = dto.fase?.tipo?.descricao?.contains("mata", ignoreCase = true) == true

    fun matchV1(dto: GeJogoDto, slug: String, phaseSlug: String, now: Instant): Match {
        val date = instant(dto.data, dto.hora)
        val home = dto.equipes?.mandante
        val away = dto.equipes?.visitante
        val state = when {
            !dto.jaComecou -> MatchState.SCHEDULED
            dto.transmissao?.broadcast?.id.equals("ENCERRADA", ignoreCase = true) -> MatchState.FINISHED
            date != null && now > date.plus(MATCH_LENGTH) -> MatchState.FINISHED
            else -> MatchState.LIVE
        }
        val started = state != MatchState.SCHEDULED
        return Match(
            id = "$ID_PREFIX${dto.id}",
            competitionSlug = slug,
            date = date,
            status = MatchStatus(state),
            home = MatchSide(
                team(home?.id ?: 0, home?.nomePopular, home?.sigla, home?.escudo),
                if (started) dto.placarMandante else null,
                dto.penaltisMandante,
            ),
            away = MatchSide(
                team(away?.id ?: 0, away?.nomePopular, away?.sigla, away?.escudo),
                if (started) dto.placarVisitante else null,
                dto.penaltisVisitante,
            ),
            phaseSlug = phaseSlug,
            venue = dto.sede?.nomePopular.orEmpty(),
            url = dto.transmissao?.url,
        )
    }

    fun standingsV1(dto: GePhaseV1Dto, title: String?): List<StandingGroup> {
        val zoneNames = dto.faixas.mapNotNull { f -> color(f.cor)?.let { it.lowercase() to f.nome } }.toMap()
        fun rows(list: List<GeRowDto>) = list.map { r ->
            StandingRow(
                rank = r.ordem,
                team = team(r.equipeId, r.nomePopular, r.sigla, r.escudo),
                played = r.jogos,
                wins = r.vitorias,
                draws = r.empates,
                losses = r.derrotas,
                goalsFor = r.golsPro,
                goalsAgainst = r.golsContra,
                points = r.pontos,
                zone = color(r.faixaCor)?.let { ZoneInfo(it, zoneNames[it.lowercase()]) },
                rankChange = r.variacao,
            )
        }.sortedBy { it.rank }
        val groups = mutableListOf<StandingGroup>()
        if (dto.classificacao.isNotEmpty()) groups += StandingGroup(title, rows(dto.classificacao))
        dto.grupos.filter { it.classificacao.isNotEmpty() }.forEach { g ->
            groups += StandingGroup(listOfNotNull(title, g.nomeGrupo).joinToString(" · ").ifBlank { null }, rows(g.classificacao))
        }
        return groups
    }

    /** Última rodada da fase de liga ou de grupos (o ge informa no topo ou em cada grupo). */
    fun lastRound(dto: GePhaseV1Dto): Int? =
        dto.rodada?.ultima ?: dto.grupos.mapNotNull { it.rodada?.ultima }.maxOrNull()

    fun currentRound(dto: GePhaseV1Dto): Int? =
        dto.rodada?.atual ?: dto.grupos.mapNotNull { it.rodada?.atual }.maxOrNull()

    fun tiesV1(dto: GePhaseV1Dto, slug: String, phaseSlug: String, now: Instant): List<KnockoutTie> =
        dto.secao.flatMap { it.chave }.mapNotNull { chave ->
            tie(chave.jogos.map { matchV1(it, slug, phaseSlug, now) })
        }

    fun phaseV1(name: String?, slug: String, phaseSlug: String, dto: GePhaseV1Dto, rounds: Map<Int, List<GeJogoDto>>, now: Instant): Phase =
        if (isKnockout(dto)) {
            Phase(phaseSlug, PhaseKind.KNOCKOUT, name = name, ties = tiesV1(dto, slug, phaseSlug, now))
        } else {
            Phase(
                phaseSlug,
                PhaseKind.LEAGUE,
                name = name,
                rounds = rounds.toSortedMap().map { (n, games) ->
                    Round(n, games.map { matchV1(it, slug, phaseSlug, now) }.sortedBy { it.date })
                }.filter { it.matches.isNotEmpty() },
            )
        }

    // ---------------------------------------------------------------- v2

    fun matchV2(dto: GeV2MatchDto, slug: String, phaseSlug: String): Match {
        val moment = dto.moment?.uppercase()
        val state = when {
            dto.result != null || moment == "PAST" -> MatchState.FINISHED
            moment == null || moment == "FUTURE" -> MatchState.SCHEDULED
            else -> MatchState.LIVE
        }
        val started = state != MatchState.SCHEDULED
        val (penHome, penAway) = penalties(dto.scoreboard?.penalty)
        val home = dto.firstContestant
        val away = dto.secondContestant
        return Match(
            id = "$ID_PREFIX${dto.id}",
            competitionSlug = slug,
            date = instant(dto.startDate, dto.startHour),
            status = MatchStatus(state),
            home = MatchSide(team(home?.id ?: 0, home?.popularName ?: home?.name, home?.abbreviation, home?.badge), if (started) dto.scoreboard?.home else null, penHome),
            away = MatchSide(team(away?.id ?: 0, away?.popularName ?: away?.name, away?.abbreviation, away?.badge), if (started) dto.scoreboard?.away else null, penAway),
            phaseSlug = phaseSlug,
            venue = dto.location?.popularName ?: dto.location?.name.orEmpty(),
            url = dto.transmission?.url,
        )
    }

    private fun penalties(element: JsonElement?): Pair<Int?, Int?> = when (element) {
        is JsonObject -> (element["home"] as? JsonPrimitive)?.intOrNull to (element["away"] as? JsonPrimitive)?.intOrNull
        else -> null to null
    }

    fun standingsV2(page: PageV2): List<StandingGroup> {
        val phases = page.standings?.multisportStandings.orEmpty()
            .filter { p -> p.standing.any { it.ratings.isNotEmpty() } }
        val titled = phases.size > 1
        // Fase mais recente primeiro: é a que interessa durante o campeonato.
        return phases.reversed().flatMap { p ->
            val phaseName = p.phase?.name.takeIf { titled }
            p.standing.filter { it.ratings.isNotEmpty() }.map { group ->
                StandingGroup(
                    listOfNotNull(phaseName, group.name).joinToString(" · ").ifBlank { null },
                    group.ratings.map { r ->
                        val values = r.standingColumns.associate { it.slug to (it.value ?: 0.0).toInt() }
                        val t = r.contestant?.contestantData
                        StandingRow(
                            rank = r.order,
                            team = team(t?.id ?: 0, t?.popularName ?: t?.name, t?.abbreviation, t?.badge),
                            played = values["jogos"] ?: 0,
                            wins = values["vitorias"] ?: 0,
                            draws = values["empates"] ?: 0,
                            losses = values["derrotas"] ?: 0,
                            goalsFor = values["gols-pro"] ?: 0,
                            goalsAgainst = values["gols-contra"] ?: 0,
                            points = values["pontos"] ?: 0,
                            zone = color(r.classificationRange?.color)?.let { ZoneInfo(it, r.classificationRange?.name) },
                            rankChange = r.variation,
                        )
                    }.sortedBy { it.rank },
                )
            }
        }
    }

    /** Fases do v2: mata-mata com todas as chaves; fases de liga só com a rodada que a página traz. */
    fun phasesV2(page: PageV2, slug: String): List<Phase> {
        val standings = page.standings?.multisportStandings.orEmpty()
        val bySlug = page.matchGroups.groupBy { it.phaseSlug }
        val phases = standings.mapNotNull { p ->
            val phaseSlug = p.phase?.slug ?: return@mapNotNull null
            val name = p.phase.name
            val brackets = p.standing.flatMap { it.brackets }
            if (brackets.isNotEmpty() || p.phase.typeID == "2") {
                val ties = brackets.sortedBy { it.id }.mapNotNull { b ->
                    tie(b.events.mapNotNull { it.match }.map { matchV2(it, slug, phaseSlug) })
                }
                Phase(phaseSlug, PhaseKind.KNOCKOUT, name = name, ties = ties).takeIf { ties.isNotEmpty() }
            } else {
                val rounds = bySlug[phaseSlug].orEmpty().map { group ->
                    Round(
                        group.round ?: 1,
                        group.items.flatMap { it.events }.mapNotNull { it.match }.map { matchV2(it, slug, phaseSlug) }.sortedBy { it.date },
                    )
                }.filter { it.matches.isNotEmpty() }.sortedBy { it.number }
                Phase(phaseSlug, PhaseKind.LEAGUE, name = name, rounds = rounds).takeIf { rounds.isNotEmpty() }
            }
        }
        return phases
    }

    // ---------------------------------------------------------------- mata-mata

    /** Confronto a partir dos jogos de uma chave: agregado, pênaltis do último jogo e classificado. */
    private fun tie(matches: List<Match>): KnockoutTie? {
        val legs = matches.sortedBy { it.date }
        val first = legs.firstOrNull()?.home?.team ?: return null
        val second = legs.first().away.team
        val counted = legs.filter { (it.isFinished || it.isLive) && it.home.score != null && it.away.score != null }
        fun goals(team: Team) = counted.takeIf { it.isNotEmpty() }
            ?.sumOf { if (it.home.team.id == team.id) it.home.score!! else it.away.score!! }
        val firstGoals = goals(first)
        val secondGoals = goals(second)
        val winner = if (legs.all { it.isFinished } && firstGoals != null && secondGoals != null) {
            when {
                firstGoals > secondGoals -> first.id
                secondGoals > firstGoals -> second.id
                else -> legs.last().let { last ->
                    val hs = last.home.shootout
                    val aws = last.away.shootout
                    if (hs != null && aws != null && hs != aws) (if (hs > aws) last.home.team.id else last.away.team.id) else null
                }
            }
        } else {
            null
        }
        return KnockoutTie(first, second, legs, firstGoals, secondGoals, winner)
    }
}
