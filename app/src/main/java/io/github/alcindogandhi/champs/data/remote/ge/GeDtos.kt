package io.github.alcindogandhi.champs.data.remote.ge

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Dados do ge (globoesporte). Há dois formatos, conforme o campeonato:
// - v1: API JSON pública em api.globoesporte.globo.com/tabela/{id}/fase/{fase}/..., a mesma que o Kamps usava;
// - v2: dados já resolvidos pelo servidor e embutidos na página do campeonato ("tabelas-esportes-v2").
// Todos os campos são opcionais: a API não é documentada e pode mudar.

// ---- v1: objeto "const classificacao" da página e respostas de /classificacao/ e /rodada/{n}/jogos/ ----

@Serializable
class GePageV1Dto(
    val fase: GeFaseDto? = null,
    @SerialName("fases_navegacao") val fasesNavegacao: List<GeFaseNavDto> = emptyList(),
)

@Serializable
class GeFaseNavDto(val slug: String, val nome: String? = null, val atual: Boolean = false)

@Serializable
class GeFaseDto(val slug: String? = null, val tipo: GeTipoDto? = null)

@Serializable
class GeTipoDto(@SerialName("tipo_id") val tipoId: String? = null, val descricao: String? = null)

@Serializable
class GePhaseV1Dto(
    val fase: GeFaseDto? = null,
    @SerialName("faixas_classificacao") val faixas: List<GeFaixaDto> = emptyList(),
    val classificacao: List<GeRowDto> = emptyList(),
    val grupos: List<GeGroupDto> = emptyList(),
    val secao: List<GeSecaoDto> = emptyList(),
    val rodada: GeRodadaDto? = null,
    /** Jogos da rodada atual, quando a fase não informa o total de rodadas. */
    @SerialName("lista_jogos") val listaJogos: List<GeJogoDto> = emptyList(),
)

@Serializable
class GeFaixaDto(val cor: String? = null, val nome: String? = null)

@Serializable
class GeRodadaDto(val atual: Int? = null, val ultima: Int? = null)

@Serializable
class GeRowDto(
    val ordem: Int = 0,
    val variacao: Int = 0,
    val pontos: Int = 0,
    val jogos: Int = 0,
    val vitorias: Int = 0,
    val empates: Int = 0,
    val derrotas: Int = 0,
    @SerialName("gols_pro") val golsPro: Int = 0,
    @SerialName("gols_contra") val golsContra: Int = 0,
    @SerialName("equipe_id") val equipeId: Long = 0,
    @SerialName("nome_popular") val nomePopular: String? = null,
    val sigla: String? = null,
    val escudo: String? = null,
    @SerialName("faixa_classificacao_cor") val faixaCor: String? = null,
)

@Serializable
class GeGroupDto(
    @SerialName("nome_grupo") val nomeGrupo: String? = null,
    val classificacao: List<GeRowDto> = emptyList(),
    val rodada: GeRodadaDto? = null,
)

@Serializable
class GeSecaoDto(val chave: List<GeChaveDto> = emptyList())

@Serializable
class GeChaveDto(val id: Long = 0, val nome: String? = null, val jogos: List<GeJogoDto> = emptyList())

@Serializable
class GeJogoDto(
    val id: Long,
    @SerialName("data_realizacao") val data: String? = null,
    @SerialName("hora_realizacao") val hora: String? = null,
    @SerialName("placar_oficial_mandante") val placarMandante: Int? = null,
    @SerialName("placar_oficial_visitante") val placarVisitante: Int? = null,
    @SerialName("placar_penaltis_mandante") val penaltisMandante: Int? = null,
    @SerialName("placar_penaltis_visitante") val penaltisVisitante: Int? = null,
    val equipes: GeEquipesDto? = null,
    val sede: GeSedeDto? = null,
    val transmissao: GeTransmissaoDto? = null,
    @SerialName("jogo_ja_comecou") val jaComecou: Boolean = false,
)

@Serializable
class GeEquipesDto(val mandante: GeEquipeDto? = null, val visitante: GeEquipeDto? = null)

@Serializable
class GeEquipeDto(
    val id: Long = 0,
    @SerialName("nome_popular") val nomePopular: String? = null,
    val sigla: String? = null,
    val escudo: String? = null,
)

@Serializable
class GeSedeDto(@SerialName("nome_popular") val nomePopular: String? = null)

@Serializable
class GeTransmissaoDto(val url: String? = null, val broadcast: GeBroadcastDto? = null)

@Serializable
class GeBroadcastDto(val id: String? = null)

// ---- v2: "getStandingData" e "getMatchListData" embutidos na página ----

@Serializable
class GeV2StandingBodyDto(val standings: GeV2StandingsDto? = null)

@Serializable
class GeV2StandingsDto(val multisportStandings: List<GeV2PhaseStandingDto> = emptyList())

@Serializable
class GeV2PhaseStandingDto(val phase: GeV2PhaseDto? = null, val standing: List<GeV2StandingDto> = emptyList())

@Serializable
class GeV2PhaseDto(
    val name: String? = null,
    val slug: String? = null,
    val type: String? = null,
    val typeID: String? = null,
    val actual: Boolean = false,
)

/** Tabela de um grupo (ratings) ou chaves de mata-mata (brackets), conforme [typename]. */
@Serializable
class GeV2StandingDto(
    @SerialName("__typename") val typename: String? = null,
    val name: String? = null,
    val round: Int? = null,
    val totalRounds: Int? = null,
    val ratings: List<GeV2RatingDto> = emptyList(),
    val brackets: List<GeV2BracketDto> = emptyList(),
)

@Serializable
class GeV2RatingDto(
    val order: Int = 0,
    val variation: Int = 0,
    val classificationRange: GeV2RangeDto? = null,
    val standingColumns: List<GeV2ColumnDto> = emptyList(),
    val contestant: GeV2ContestantDto? = null,
)

@Serializable
class GeV2RangeDto(val name: String? = null, val color: String? = null)

@Serializable
class GeV2ColumnDto(val slug: String? = null, val value: Double? = null)

@Serializable
class GeV2ContestantDto(val contestantData: GeV2TeamDto? = null)

@Serializable
class GeV2TeamDto(
    val id: Long = 0,
    val popularName: String? = null,
    val name: String? = null,
    val abbreviation: String? = null,
    val badge: String? = null,
)

@Serializable
class GeV2BracketDto(val id: Long = 0, val name: String? = null, val events: List<GeV2EventDto> = emptyList())

@Serializable
class GeV2EventDto(val match: GeV2MatchDto? = null)

@Serializable
class GeV2MatchDto(
    val id: Long,
    val startDate: String? = null,
    val startHour: String? = null,
    val firstContestant: GeV2TeamDto? = null,
    val secondContestant: GeV2TeamDto? = null,
    val result: String? = null,
    val round: Int? = null,
    val scoreboard: GeV2ScoreboardDto? = null,
    val transmission: GeV2TransmissionDto? = null,
    /** PAST, FUTURE ou, durante o jogo, outro valor (ex.: NOW). */
    val moment: String? = null,
    val location: GeV2LocationDto? = null,
)

@Serializable
class GeV2ScoreboardDto(
    val home: Int? = null,
    val away: Int? = null,
    /** Formato não visto ainda (sempre null nos exemplos): lido de forma flexível. */
    val penalty: JsonElement? = null,
)

@Serializable
class GeV2TransmissionDto(val url: String? = null)

@Serializable
class GeV2LocationDto(val popularName: String? = null, val name: String? = null)

@Serializable
class GeV2MatchListBodyDto(val matches: List<GeV2MatchGroupDto> = emptyList())

@Serializable
class GeV2MatchGroupDto(val phaseSlug: String? = null, val round: Int? = null, val items: List<GeV2MatchItemDto> = emptyList())

@Serializable
class GeV2MatchItemDto(val groupName: String? = null, val events: List<GeV2EventDto> = emptyList())
