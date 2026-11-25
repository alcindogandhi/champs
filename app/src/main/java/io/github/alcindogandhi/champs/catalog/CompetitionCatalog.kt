package io.github.alcindogandhi.champs.catalog

import io.github.alcindogandhi.champs.domain.ZoneInfo
import io.github.alcindogandhi.champs.domain.ZoneKind

enum class Region { BRAZIL, SOUTH_AMERICA, EUROPE, WORLD }

/** Faixa da tabela usada quando a ESPN não informa as zonas (ela só informa em alguns campeonatos). */
data class Zone(val ranks: IntRange, val kind: ZoneKind)

data class Competition(
    /** Identificador do campeonato na ESPN, ex.: `bra.1`. */
    val slug: String,
    val name: String,
    val region: Region,
    /** Pontos corridos puros: permite o simulador. */
    val simulable: Boolean = false,
    val zones: List<Zone> = emptyList(),
) {
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
 * Campeonatos oferecidos pelo app. Os slugs foram conferidos com `scripts/check_slugs.py`.
 * As zonas são aproximadas: o número de vagas muda de ano para ano.
 */
object CompetitionCatalog {

    private fun z(ranks: IntRange, kind: ZoneKind) = Zone(ranks, kind)

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
        Competition("bra.copa_do_brazil", "Copa do Brasil", Region.BRAZIL),
        Competition("bra.copa_do_nordeste", "Copa do Nordeste", Region.BRAZIL),
        Competition("bra.camp.paulista", "Campeonato Paulista", Region.BRAZIL),
        Competition("bra.camp.carioca", "Campeonato Carioca", Region.BRAZIL),
        Competition("bra.camp.mineiro", "Campeonato Mineiro", Region.BRAZIL),
        Competition("bra.camp.gaucho", "Campeonato Gaúcho", Region.BRAZIL),

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
    )

    /** Seguidos no primeiro uso. */
    val defaultFollowed: List<String> = listOf("bra.1", "bra.2", "bra.copa_do_brazil", "conmebol.libertadores")

    private val bySlug = all.associateBy { it.slug }

    fun find(slug: String): Competition? = bySlug[slug]

    fun get(slug: String): Competition = bySlug[slug] ?: Competition(slug, slug, Region.WORLD)
}
