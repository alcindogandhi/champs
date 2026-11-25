package io.github.alcindogandhi.champs.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Subconjunto das respostas da API pública da ESPN. Todos os campos são opcionais: a API não é oficial
// e muda sem aviso, então o app ignora campos desconhecidos e tolera os ausentes.

@Serializable
class ScoreboardDto(val leagues: List<LeagueDto> = emptyList(), val events: List<EventDto> = emptyList())

@Serializable
class LeagueDto(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val season: LeagueSeasonDto? = null,
    val calendarStartDate: String? = null,
    val calendarEndDate: String? = null,
)

@Serializable
class LeagueSeasonDto(val year: Int? = null, val startDate: String? = null, val endDate: String? = null)

@Serializable
class EventDto(
    val id: String,
    val date: String? = null,
    val season: EventSeasonDto? = null,
    val status: StatusDto? = null,
    val competitions: List<CompetitionDto> = emptyList(),
)

@Serializable
class EventSeasonDto(val year: Int? = null, val slug: String? = null)

@Serializable
class StatusDto(val displayClock: String? = null, val period: Int? = null, val type: StatusTypeDto? = null)

@Serializable
class StatusTypeDto(
    val name: String? = null,
    val state: String? = null,
    val completed: Boolean = false,
    val description: String? = null,
    val detail: String? = null,
    val shortDetail: String? = null,
)

@Serializable
class CompetitionDto(
    val id: String? = null,
    val date: String? = null,
    val status: StatusDto? = null,
    val venue: VenueDto? = null,
    val altGameNote: String? = null,
    val notes: List<NoteDto> = emptyList(),
    val series: SeriesDto? = null,
    val competitors: List<CompetitorDto> = emptyList(),
)

@Serializable
class CompetitorDto(
    val id: String? = null,
    val homeAway: String? = null,
    val winner: Boolean? = null,
    /** Texto ("2") no scoreboard e objeto (`{"value": 2.0}`) na agenda do time. */
    val score: JsonElement? = null,
    val shootoutScore: JsonElement? = null,
    val team: TeamDto? = null,
)

@Serializable
class TeamDto(
    val id: String,
    val abbreviation: String? = null,
    val displayName: String? = null,
    val shortDisplayName: String? = null,
    val name: String? = null,
    val logo: String? = null,
    val logos: List<LogoDto> = emptyList(),
)

@Serializable
class LogoDto(val href: String? = null, val rel: List<String> = emptyList())

@Serializable
class SeriesDto(val competitors: List<SeriesCompetitorDto> = emptyList(), val completed: Boolean? = null)

@Serializable
class SeriesCompetitorDto(val id: String? = null, val winner: Boolean? = null, val aggregateScore: Double? = null)

@Serializable
class NoteDto(val headline: String? = null, val text: String? = null)

@Serializable
class VenueDto(val fullName: String? = null, val displayName: String? = null, val address: AddressDto? = null)

@Serializable
class AddressDto(val city: String? = null, val country: String? = null)

// Classificação: /apis/v2/sports/soccer/{slug}/standings

@Serializable
class StandingsDto(val children: List<StandingsChildDto> = emptyList(), val standings: StandingsBodyDto? = null)

@Serializable
class StandingsChildDto(val name: String? = null, val standings: StandingsBodyDto? = null)

@Serializable
class StandingsBodyDto(val entries: List<StandingEntryDto> = emptyList())

@Serializable
class StandingEntryDto(val team: TeamDto, val stats: List<StatDto> = emptyList(), val note: EntryNoteDto? = null)

@Serializable
class StatDto(val name: String? = null, val value: Double? = null, val displayValue: String? = null)

@Serializable
class EntryNoteDto(val color: String? = null, val description: String? = null)

// Detalhe do jogo: /apis/site/v2/sports/soccer/{slug}/summary?event={id}

@Serializable
class SummaryDto(
    val header: HeaderDto? = null,
    val keyEvents: List<KeyEventDto> = emptyList(),
    val commentary: List<CommentaryDto> = emptyList(),
    val rosters: List<RosterDto> = emptyList(),
    val boxscore: BoxscoreDto? = null,
    val gameInfo: GameInfoDto? = null,
)

@Serializable
class HeaderDto(val id: String? = null, val competitions: List<CompetitionDto> = emptyList())

@Serializable
class GameInfoDto(val venue: VenueDto? = null)

@Serializable
class TypeDto(val id: String? = null, val text: String? = null, val type: String? = null)

@Serializable
class ClockDto(val value: Double? = null, val displayValue: String? = null)

@Serializable
class TeamRefDto(val id: String? = null, val displayName: String? = null)

@Serializable
class KeyEventDto(
    val id: String? = null,
    val type: TypeDto? = null,
    val text: String? = null,
    val shortText: String? = null,
    val clock: ClockDto? = null,
    val scoringPlay: Boolean = false,
    val team: TeamRefDto? = null,
)

@Serializable
class CommentaryDto(
    val sequence: Int = 0,
    val time: ClockDto? = null,
    val text: String? = null,
    val play: PlayDto? = null,
)

@Serializable
class PlayDto(
    val id: String? = null,
    val type: TypeDto? = null,
    val scoringPlay: Boolean = false,
    val team: TeamRefDto? = null,
)

@Serializable
class RosterDto(
    val homeAway: String? = null,
    val team: TeamDto? = null,
    val formation: String? = null,
    val roster: List<RosterEntryDto> = emptyList(),
)

@Serializable
class RosterEntryDto(
    val starter: Boolean = false,
    val jersey: String? = null,
    val position: PositionDto? = null,
    val formationPlace: String? = null,
    /** Booleano na maioria das respostas, objeto em algumas. */
    val subbedIn: JsonElement? = null,
    val subbedOut: JsonElement? = null,
    val athlete: AthleteDto? = null,
)

@Serializable
class PositionDto(val name: String? = null, val displayName: String? = null, val abbreviation: String? = null)

@Serializable
class AthleteDto(
    val id: String? = null,
    val displayName: String? = null,
    val shortName: String? = null,
    val team: TeamDto? = null,
    val statistics: List<StatDto> = emptyList(),
)

@Serializable
class BoxscoreDto(val teams: List<BoxTeamDto> = emptyList())

@Serializable
class BoxTeamDto(val team: TeamDto? = null, val homeAway: String? = null, val statistics: List<StatDto> = emptyList())

// Artilheiros: /apis/site/v2/sports/soccer/{slug}/statistics

@Serializable
class LeadersDto(val stats: List<LeaderCategoryDto> = emptyList())

@Serializable
class LeaderCategoryDto(val name: String? = null, val leaders: List<LeaderDto> = emptyList())

@Serializable
class LeaderDto(val value: Double? = null, val athlete: AthleteDto? = null)

// Times: /apis/site/v2/sports/soccer/{slug}/teams

@Serializable
class TeamsDto(val sports: List<SportDto> = emptyList())

@Serializable
class SportDto(val leagues: List<TeamsLeagueDto> = emptyList())

@Serializable
class TeamsLeagueDto(val teams: List<TeamWrapperDto> = emptyList())

@Serializable
class TeamWrapperDto(val team: TeamDto)

// Agenda do time: /apis/site/v2/sports/soccer/{slug}/teams/{id}/schedule

@Serializable
class ScheduleDto(val team: TeamDto? = null, val events: List<EventDto> = emptyList())
