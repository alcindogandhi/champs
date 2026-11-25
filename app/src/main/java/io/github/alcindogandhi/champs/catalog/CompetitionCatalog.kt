package io.github.alcindogandhi.champs.catalog

import io.github.alcindogandhi.champs.domain.ZoneInfo
import io.github.alcindogandhi.champs.domain.ZoneKind

enum class Region { BRAZIL, STATES, SOUTH_AMERICA, EUROPE, WORLD }

/** Faixa da tabela usada quando a ESPN não informa as zonas (ela só informa em alguns campeonatos). */
data class Zone(val ranks: IntRange, val kind: ZoneKind)

data class Competition(
    /** Identificador do campeonato: o slug da ESPN (`bra.1`) ou `ge.<nome>` para os que vêm do ge. */
    val slug: String,
    val name: String,
    val region: Region,
    /** Pontos corridos puros: permite o simulador. */
    val simulable: Boolean = false,
    val zones: List<Zone> = emptyList(),
    /** Caminho da página do campeonato no ge (ex.: `pr/futebol/campeonato-paranaense`); null = ESPN. */
    val gePath: String? = null,
) {
    val fromGe: Boolean get() = gePath != null

    fun zoneFor(rank: Int): ZoneInfo? =
        zones.firstOrNull { rank in it.ranks }?.let { ZoneInfo(color = zoneColor(it.kind), kind = it.kind) }
}

fun zoneColor(kind: ZoneKind): String = when (kind) {
    ZoneKind.CONTINENTAL -> "#1E88E5"
    ZoneKind.CONTINENTAL_QUALIFYING -> "#64B5F6"
    ZoneKind.SECONDARY_CONTINENTAL -> "#FB8C00"
    ZoneKind.PROMOTION -> "#43A047"
    ZoneKind.PLAYOFF -> "#FDD835"
    ZoneKind.RELEGATION -> "#E53935"
}

/**
 * Campeonatos oferecidos pelo app. Os slugs da ESPN foram conferidos com `scripts/check_slugs.py`; os do ge,
 * com `scripts/check_ge.py` (entram só os que a ESPN não cobre e com edição atual no ge).
 * As zonas são aproximadas: o número de vagas muda de ano para ano.
 */
object CompetitionCatalog {

    private fun z(ranks: IntRange, kind: ZoneKind) = Zone(ranks, kind)

    /** Campeonato do ge: id `ge.<nome>`, zonas e nomes de fase vêm do próprio ge. */
    private fun ge(id: String, name: String, region: Region, path: String) = Competition("ge.$id", name, region, gePath = path)

    private fun europeanLeague(cl: IntRange, el: IntRange?, relegation: IntRange, playoff: IntRange? = null) =
        listOfNotNull(
            z(cl, ZoneKind.CONTINENTAL),
            el?.let { z(it, ZoneKind.SECONDARY_CONTINENTAL) },
            playoff?.let { z(it, ZoneKind.PLAYOFF) },
            z(relegation, ZoneKind.RELEGATION),
        )

    val all: List<Competition> = listOf(
        Competition(
            "bra.1", "Brasileirão Série A", Region.BRAZIL, simulable = true,
            zones = listOf(
                z(1..4, ZoneKind.CONTINENTAL),
                z(5..6, ZoneKind.CONTINENTAL_QUALIFYING),
                z(7..12, ZoneKind.SECONDARY_CONTINENTAL),
                z(17..20, ZoneKind.RELEGATION),
            ),
        ),
        Competition(
            "bra.2", "Brasileirão Série B", Region.BRAZIL, simulable = true,
            zones = listOf(z(1..4, ZoneKind.PROMOTION), z(17..20, ZoneKind.RELEGATION)),
        ),
        ge("serie-c", "Brasileirão Série C", Region.BRAZIL, "futebol/brasileirao-serie-c"),
        ge("serie-d", "Brasileirão Série D", Region.BRAZIL, "futebol/brasileirao-serie-d"),
        Competition("bra.copa_do_brazil", "Copa do Brasil", Region.BRAZIL),
        Competition("bra.copa_do_nordeste", "Copa do Nordeste", Region.BRAZIL),
        ge("copa-verde", "Copa Verde", Region.BRAZIL, "futebol/copa-verde"),
        ge("brasileiro-feminino", "Brasileirão Feminino", Region.BRAZIL, "futebol/brasileiro-feminino"),
        ge("copinha", "Copa São Paulo de Futebol Júnior", Region.BRAZIL, "futebol/Copa-SP-de-futebol-junior"),

        // Estaduais: os da ESPN e, para os demais, o ge. Ordem alfabética do nome.
        ge("acreano", "Campeonato Acreano", Region.STATES, "ac/futebol/campeonato-acreano"),
        ge("alagoano", "Campeonato Alagoano", Region.STATES, "al/futebol/campeonato-alagoano"),
        ge("amapaense", "Campeonato Amapaense", Region.STATES, "ap/futebol/campeonato-amapaense"),
        ge("amazonense", "Campeonato Amazonense", Region.STATES, "am/futebol/campeonato-amazonense"),
        ge("baiano", "Campeonato Baiano", Region.STATES, "ba/futebol/campeonato-baiano"),
        ge("brasiliense", "Campeonato Brasiliense", Region.STATES, "df/futebol/campeonato-brasiliense"),
        ge("capixaba", "Campeonato Capixaba", Region.STATES, "es/futebol/campeonato-capixaba"),
        ge("capixaba-b", "Campeonato Capixaba Série B", Region.STATES, "es/futebol/capixaba-serie-b"),
        Competition("bra.camp.carioca", "Campeonato Carioca", Region.STATES),
        ge("catarinense", "Campeonato Catarinense", Region.STATES, "sc/futebol/campeonato-catarinense"),
        ge("catarinense-b", "Campeonato Catarinense Série B", Region.STATES, "sc/futebol/catarinense-segunda-divisao"),
        ge("cearense", "Campeonato Cearense", Region.STATES, "ce/futebol/campeonato-cearense"),
        Competition("bra.camp.gaucho", "Campeonato Gaúcho", Region.STATES),
        ge("goiano", "Campeonato Goiano", Region.STATES, "go/futebol/campeonato-goiano"),
        ge("goiano-2", "Campeonato Goiano 2ª Divisão", Region.STATES, "go/futebol/goiano-segunda-divisao"),
        ge("maranhense", "Campeonato Maranhense", Region.STATES, "ma/futebol/campeonato-maranhense"),
        ge("mato-grossense", "Campeonato Mato-Grossense", Region.STATES, "mt/futebol/campeonato-mato-grossense"),
        Competition("bra.camp.mineiro", "Campeonato Mineiro", Region.STATES),
        ge("mineiro-2", "Campeonato Mineiro Módulo 2", Region.STATES, "mg/futebol/mineiro-modulo-2"),
        ge("paraense", "Campeonato Paraense", Region.STATES, "pa/futebol/campeonato-paraense"),
        ge("paraibano", "Campeonato Paraibano", Region.STATES, "pb/futebol/campeonato-paraibano"),
        ge("paraibano-2", "Campeonato Paraibano 2ª Divisão", Region.STATES, "pb/futebol/paraibano-segunda-divisao"),
        ge("paranaense", "Campeonato Paranaense", Region.STATES, "pr/futebol/campeonato-paranaense"),
        Competition("bra.camp.paulista", "Campeonato Paulista", Region.STATES),
        ge("paulista-a2", "Campeonato Paulista Série A2", Region.STATES, "sp/futebol/paulista-serie-a2"),
        ge("paulista-a3", "Campeonato Paulista Série A3", Region.STATES, "sp/futebol/paulista-serie-a3"),
        ge("paulista-2", "Campeonato Paulista Segunda Divisão", Region.STATES, "sp/futebol/paulista-segunda-divisao"),
        ge("pernambucano", "Campeonato Pernambucano", Region.STATES, "pe/futebol/campeonato-pernambucano"),
        ge("piauiense", "Campeonato Piauiense", Region.STATES, "pi/futebol/campeonato-piauiense"),
        ge("potiguar", "Campeonato Potiguar", Region.STATES, "rn/futebol/campeonato-potiguar"),
        ge("rondoniense", "Campeonato Rondoniense", Region.STATES, "ro/futebol/campeonato-rondoniense"),
        ge("roraimense", "Campeonato Roraimense", Region.STATES, "rr/futebol/campeonato-roraimense"),
        ge("sergipano", "Campeonato Sergipano", Region.STATES, "se/futebol/campeonato-sergipano"),
        ge("sul-mato-grossense", "Campeonato Sul-Mato-Grossense", Region.STATES, "ms/futebol/campeonato-sul-mato-grossense"),
        ge("tocantinense", "Campeonato Tocantinense", Region.STATES, "to/futebol/campeonato-tocantinense"),
        ge("copa-es", "Copa Espírito Santo", Region.STATES, "es/futebol/copa-espirito-santo"),
        ge("copa-paulista", "Copa Paulista", Region.STATES, "sp/futebol/copa-paulista"),
        ge("copa-rio", "Copa Rio", Region.STATES, "rj/futebol/copa-rio"),

        Competition("conmebol.libertadores", "Libertadores", Region.SOUTH_AMERICA),
        Competition("conmebol.sudamericana", "Sul-Americana", Region.SOUTH_AMERICA),
        Competition("arg.1", "Campeonato Argentino", Region.SOUTH_AMERICA),

        Competition("uefa.champions", "Champions League", Region.EUROPE),
        Competition("uefa.europa", "Europa League", Region.EUROPE),
        Competition("uefa.europa.conf", "Conference League", Region.EUROPE),
        Competition("eng.1", "Premier League", Region.EUROPE, simulable = true, zones = europeanLeague(1..4, 5..5, 18..20)),
        Competition("esp.1", "La Liga", Region.EUROPE, simulable = true, zones = europeanLeague(1..4, 5..6, 18..20)),
        Competition("ita.1", "Serie A (Itália)", Region.EUROPE, simulable = true, zones = europeanLeague(1..4, 5..6, 18..20)),
        Competition("ger.1", "Bundesliga", Region.EUROPE, simulable = true, zones = europeanLeague(1..4, 5..6, 17..18, 16..16)),
        Competition("fra.1", "Ligue 1", Region.EUROPE, simulable = true, zones = europeanLeague(1..3, 4..5, 17..18, 16..16)),
        Competition("por.1", "Liga Portugal", Region.EUROPE, simulable = true, zones = europeanLeague(1..2, 3..4, 17..18, 16..16)),
        Competition("ned.1", "Eredivisie", Region.EUROPE, simulable = true, zones = europeanLeague(1..2, 3..4, 17..18, 16..16)),

        Competition("fifa.world", "Copa do Mundo", Region.WORLD),
        Competition("usa.1", "MLS", Region.WORLD),
        ge("sul-americano-sub-17", "Sul-Americano Sub-17", Region.WORLD, "futebol/sul-americano-sub-17"),
        ge("mundial-sub-17", "Mundial Sub-17", Region.WORLD, "futebol/mundial-sub-17"),
    )

    /** Seguidos no primeiro uso. */
    val defaultFollowed: List<String> = listOf("bra.1", "bra.2", "bra.copa_do_brazil", "conmebol.libertadores")

    private val bySlug = all.associateBy { it.slug }

    fun find(slug: String): Competition? = bySlug[slug]

    fun get(slug: String): Competition = bySlug[slug] ?: Competition(slug, slug, Region.WORLD)
}
