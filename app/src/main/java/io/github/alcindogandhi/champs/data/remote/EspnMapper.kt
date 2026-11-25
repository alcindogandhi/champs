package io.github.alcindogandhi.champs.data.remote

import io.github.alcindogandhi.champs.catalog.Competition
import io.github.alcindogandhi.champs.domain.EventKind
import io.github.alcindogandhi.champs.domain.Lineup
import io.github.alcindogandhi.champs.domain.LineupPlayer
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.MatchDetail
import io.github.alcindogandhi.champs.domain.MatchSide
import io.github.alcindogandhi.champs.domain.MatchState
import io.github.alcindogandhi.champs.domain.MatchStatus
import io.github.alcindogandhi.champs.domain.Play
import io.github.alcindogandhi.champs.domain.Scorer
import io.github.alcindogandhi.champs.domain.StandingGroup
import io.github.alcindogandhi.champs.domain.StandingRow
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.domain.TeamStatComparison
import io.github.alcindogandhi.champs.domain.ZoneInfo
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Converte os DTOs da ESPN no modelo do app. */
object EspnMapper {

    fun team(dto: TeamDto): Team {
        val name = dto.shortDisplayName ?: dto.displayName ?: dto.name ?: dto.abbreviation ?: dto.id
        val logo = dto.logo
            ?: dto.logos.firstOrNull { "default" in it.rel }?.href
            ?: dto.logos.firstOrNull()?.href
            ?: "https://a.espncdn.com/i/teamlogos/soccer/500/${dto.id}.png"
        return Team(
            id = dto.id,
            name = dto.displayName ?: name,
            shortName = name,
            abbreviation = dto.abbreviation.orEmpty(),
            logo = logo,
        )
    }

    fun parseInstant(text: String?): Instant? {
        if (text.isNullOrBlank()) return null
        return runCatching { Instant.parse(text) }
            .recoverCatching { OffsetDateTime.parse(text).toInstant() }
            .recoverCatching { OffsetDateTime.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mmX")).toInstant() }
            .getOrNull()
    }

    fun status(dto: StatusDto?): MatchStatus {
        val type = dto?.type
        val name = type?.name.orEmpty()
        val state = when {
            name.contains("POSTPONED") || name.contains("DELAYED") && type?.state == "pre" -> MatchState.POSTPONED
            name.contains("CANCELED") || name.contains("ABANDONED") || name.contains("FORFEIT") -> MatchState.CANCELED
            type?.state == "in" -> MatchState.LIVE
            type?.state == "post" || type?.completed == true -> MatchState.FINISHED
            else -> MatchState.SCHEDULED
        }
        return MatchStatus(
            state = state,
            clock = if (state == MatchState.LIVE) dto?.displayClock.orEmpty() else "",
            detail = type?.shortDetail ?: type?.detail ?: type?.description.orEmpty(),
        )
    }

    private fun number(element: JsonElement?): Int? = when (element) {
        null -> null
        is JsonPrimitive -> element.doubleOrNull?.toInt() ?: element.content.toIntOrNull()
        is JsonObject -> (element["value"] as? JsonPrimitive)?.doubleOrNull?.toInt()
            ?: (element["displayValue"] as? JsonPrimitive)?.content?.toIntOrNull()
        else -> null
    }

    private fun flag(element: JsonElement?): Boolean = when (element) {
        is JsonPrimitive -> element.booleanOrNull ?: false
        is JsonObject -> (element["didSub"] as? JsonPrimitive)?.booleanOrNull ?: false
        else -> false
    }

    fun match(event: EventDto, slug: String): Match? {
        val comp = event.competitions.firstOrNull() ?: return null
        val home = comp.competitors.firstOrNull { it.homeAway == "home" } ?: comp.competitors.getOrNull(0) ?: return null
        val away = comp.competitors.firstOrNull { it.homeAway == "away" } ?: comp.competitors.getOrNull(1) ?: return null
        val homeTeam = home.team ?: return null
        val awayTeam = away.team ?: return null
        val status = status(event.status ?: comp.status)
        val hasScore = status.state == MatchState.LIVE || status.state == MatchState.FINISHED
        val seriesHome = comp.series?.competitors?.firstOrNull { it.id == homeTeam.id }
        val seriesAway = comp.series?.competitors?.firstOrNull { it.id == awayTeam.id }
        val aggregate = if (seriesHome?.aggregateScore != null && seriesAway?.aggregateScore != null) {
            seriesHome.aggregateScore.toInt() to seriesAway.aggregateScore.toInt()
        } else {
            null
        }
        val seriesWinner = comp.series?.takeIf { it.completed == true }?.competitors?.firstOrNull { it.winner == true }?.id
        return Match(
            id = event.id,
            competitionSlug = slug,
            date = parseInstant(event.date ?: comp.date),
            status = status,
            home = MatchSide(team(homeTeam), if (hasScore) number(home.score) else null, number(home.shootoutScore), home.winner),
            away = MatchSide(team(awayTeam), if (hasScore) number(away.score) else null, number(away.shootoutScore), away.winner),
            phaseSlug = event.season?.slug.orEmpty(),
            note = comp.notes.firstNotNullOfOrNull { it.headline ?: it.text }.orEmpty(),
            venue = comp.venue?.fullName ?: comp.venue?.displayName.orEmpty(),
            aggregate = aggregate,
            seriesWinnerId = seriesWinner,
        )
    }

    fun matches(dto: ScoreboardDto, slug: String): List<Match> = dto.events.mapNotNull { match(it, slug) }

    fun standings(dto: StandingsDto, competition: Competition): List<StandingGroup> {
        val bodies = if (dto.children.isNotEmpty()) {
            dto.children.map { it.name to it.standings }
        } else {
            listOf(null to dto.standings)
        }
        val grouped = bodies.size > 1
        return bodies.mapNotNull { (name, body) ->
            val entries = body?.entries.orEmpty()
            if (entries.isEmpty()) return@mapNotNull null
            val rows = entries.map { entry ->
                val stats = entry.stats.associateBy { it.name }
                fun stat(key: String) = stats[key]?.value?.toInt() ?: 0
                val rank = stat("rank")
                val zone = entry.note?.color?.let { ZoneInfo(it.withHash(), entry.note.description) }
                    ?: if (!grouped && rank > 0) competition.zoneFor(rank) else null
                StandingRow(
                    rank = rank,
                    team = team(entry.team),
                    played = stat("gamesPlayed"),
                    wins = stat("wins"),
                    draws = stat("ties"),
                    losses = stat("losses"),
                    goalsFor = stat("pointsFor"),
                    goalsAgainst = stat("pointsAgainst"),
                    points = stat("points"),
                    zone = zone,
                    rankChange = stat("rankChange"),
                )
            }.sortedBy { if (it.rank > 0) it.rank else Int.MAX_VALUE }
            StandingGroup(if (grouped) name else null, rows)
        }
    }

    private fun String.withHash() = if (startsWith("#")) this else "#$this"

    fun scorers(dto: LeadersDto): List<Scorer> {
        val category = dto.stats.firstOrNull { it.name == "goalsLeaders" }
            ?: dto.stats.firstOrNull { it.name?.contains("goal", ignoreCase = true) == true }
            ?: return emptyList()
        var lastGoals = -1
        var lastRank = 0
        return category.leaders.mapIndexedNotNull { index, leader ->
            val athlete = leader.athlete ?: return@mapIndexedNotNull null
            val goals = leader.value?.toInt() ?: 0
            // Empatados dividem a posição: 1, 2, 2, 4...
            val rank = if (goals == lastGoals) lastRank else index + 1
            lastGoals = goals
            lastRank = rank
            Scorer(
                rank = rank,
                playerName = athlete.displayName ?: athlete.shortName.orEmpty(),
                team = athlete.team?.let(::team),
                goals = goals,
                appearances = athlete.statistics.firstOrNull { it.name == "appearances" }?.value?.toInt(),
            )
        }
    }

    fun teams(dto: TeamsDto): List<Team> =
        dto.sports.flatMap { it.leagues }.flatMap { it.teams }.map { team(it.team) }.sortedBy { it.name }

    fun schedule(dto: ScheduleDto, slug: String): List<Match> = dto.events.mapNotNull { match(it, slug) }

    fun matchDetail(dto: SummaryDto, slug: String, eventId: String, fallback: Match?): MatchDetail? {
        val comp = dto.header?.competitions?.firstOrNull()
        val match = comp?.let {
            match(EventDto(id = eventId, date = it.date, status = it.status, competitions = listOf(it)), slug)
        }?.let { parsed ->
            parsed.copy(
                phaseSlug = fallback?.phaseSlug.orEmpty(),
                venue = dto.gameInfo?.venue?.fullName ?: parsed.venue.ifEmpty { fallback?.venue.orEmpty() },
            )
        } ?: fallback ?: return null
        val homeId = match.home.team.id
        return MatchDetail(
            match = match,
            plays = plays(dto, homeId),
            homeLineup = dto.rosters.firstOrNull { it.homeAway == "home" }?.let(::lineup),
            awayLineup = dto.rosters.firstOrNull { it.homeAway == "away" }?.let(::lineup),
            stats = stats(dto),
        )
    }

    private fun kind(type: String?, scoring: Boolean): EventKind {
        val t = type.orEmpty().lowercase()
        return when {
            t.contains("own-goal") -> EventKind.OWN_GOAL
            t.contains("penalty") && (t.contains("missed") || t.contains("saved")) -> EventKind.PENALTY_MISSED
            t.contains("penalty") && (scoring || t.contains("goal") || t.contains("scored")) -> EventKind.PENALTY_GOAL
            t.startsWith("goal") || scoring -> EventKind.GOAL
            t.contains("red-card") || t.contains("second-yellow") -> EventKind.RED_CARD
            t.contains("yellow-card") -> EventKind.YELLOW_CARD
            t.contains("substitution") -> EventKind.SUBSTITUTION
            t.contains("var") -> EventKind.VAR
            t.contains("kickoff") || t.contains("half") || t.contains("end") || t.contains("start") -> EventKind.PERIOD
            else -> EventKind.OTHER
        }
    }

    private val highlighted = setOf(
        EventKind.GOAL, EventKind.OWN_GOAL, EventKind.PENALTY_GOAL, EventKind.PENALTY_MISSED,
        EventKind.RED_CARD, EventKind.YELLOW_CARD, EventKind.SUBSTITUTION, EventKind.PERIOD, EventKind.VAR,
    )

    private fun plays(dto: SummaryDto, homeId: String): List<Play> {
        val homeName = dto.header?.competitions?.firstOrNull()?.competitors
            ?.firstOrNull { it.homeAway == "home" }?.team?.displayName
        if (dto.commentary.isNotEmpty()) {
            return dto.commentary.sortedByDescending { it.sequence }.mapNotNull { c ->
                val text = c.text?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val k = kind(c.play?.type?.type, c.play?.scoringPlay == true)
                val teamName = c.play?.team?.displayName
                Play(
                    clock = c.time?.displayValue.orEmpty(),
                    text = text,
                    kind = k,
                    isHome = teamName?.let { it == homeName },
                    highlight = k in highlighted,
                )
            }
        }
        return dto.keyEvents.reversed().mapNotNull { e ->
            val text = e.text ?: e.shortText ?: e.type?.text ?: return@mapNotNull null
            val k = kind(e.type?.type, e.scoringPlay)
            Play(
                clock = e.clock?.displayValue.orEmpty(),
                text = text,
                kind = k,
                isHome = e.team?.id?.let { it == homeId },
                highlight = k in highlighted,
            )
        }
    }

    private fun lineup(dto: RosterDto): Lineup? {
        val teamDto = dto.team ?: return null
        val players = dto.roster.map { r ->
            LineupPlayer(
                name = r.athlete?.shortName ?: r.athlete?.displayName.orEmpty(),
                jersey = r.jersey.orEmpty(),
                position = r.position?.abbreviation.orEmpty(),
                formationPlace = r.formationPlace?.toIntOrNull() ?: 0,
                starter = r.starter,
                subbedIn = flag(r.subbedIn),
                subbedOut = flag(r.subbedOut),
            )
        }
        if (players.isEmpty()) return null
        return Lineup(
            team = team(teamDto),
            formation = dto.formation,
            starters = players.filter { it.starter },
            substitutes = players.filterNot { it.starter },
        )
    }

    /** Estatísticas mostradas no comparador, na ordem da tela. */
    val comparedStats = listOf(
        "possessionPct", "totalShots", "shotsOnTarget", "wonCorners", "saves",
        "accuratePasses", "totalPasses", "foulsCommitted", "offsides", "yellowCards", "redCards",
    )

    private fun stats(dto: SummaryDto): List<TeamStatComparison> {
        val home = dto.boxscore?.teams?.firstOrNull { it.homeAway == "home" } ?: return emptyList()
        val away = dto.boxscore.teams.firstOrNull { it.homeAway == "away" } ?: return emptyList()
        val h = home.statistics.associateBy { it.name }
        val a = away.statistics.associateBy { it.name }
        return comparedStats.mapNotNull { key ->
            val hs = h[key] ?: return@mapNotNull null
            val aws = a[key] ?: return@mapNotNull null
            val hv = hs.displayValue?.toDoubleOrNull() ?: hs.value ?: return@mapNotNull null
            val av = aws.displayValue?.toDoubleOrNull() ?: aws.value ?: return@mapNotNull null
            if (key == "possessionPct") {
                // Arredonda o mandante e completa 100% com o visitante, para a soma não dar 101%.
                val home = Math.round(hv).toInt().coerceIn(0, 100)
                TeamStatComparison(key, hv, av, "$home%", "${100 - home}%")
            } else {
                TeamStatComparison(key, hv, av, hv.toInt().toString(), av.toInt().toString())
            }
        }
    }
}
