package io.github.alcindogandhi.champs.data

import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import java.time.Instant
import java.time.LocalDate

/** Manda cada campeonato para a fonte dele: a ESPN ou, para o que ela não cobre, o ge. */
class RoutingDataSource(
    private val espn: FootballDataSource,
    private val ge: FootballDataSource,
    private val isGe: (String) -> Boolean = { CompetitionCatalog.find(it)?.gePath != null },
) : FootballDataSource {

    private fun of(slug: String) = if (isGe(slug)) ge else espn

    override fun season(slug: String, force: Boolean) = of(slug).season(slug, force)
    override fun seasonLive(slug: String) = of(slug).seasonLive(slug)
    override fun standings(slug: String, force: Boolean) = of(slug).standings(slug, force)
    override fun scorers(slug: String, force: Boolean) = of(slug).scorers(slug, force)
    override fun matchesOn(slug: String, date: LocalDate, force: Boolean) = of(slug).matchesOn(slug, date, force)
    override fun matchesOfEspnDay(slug: String, instant: Instant, dayOffset: Long, force: Boolean) =
        of(slug).matchesOfEspnDay(slug, instant, dayOffset, force)
    override fun matchDetail(slug: String, eventId: String, force: Boolean) = of(slug).matchDetail(slug, eventId, force)
    override fun teams(slug: String, force: Boolean) = of(slug).teams(slug, force)
    override fun teamSchedule(slug: String, teamId: String, force: Boolean) = of(slug).teamSchedule(slug, teamId, force)
    override fun teamFixtures(slug: String, teamId: String, force: Boolean) = of(slug).teamFixtures(slug, teamId, force)

    override suspend fun clearCache() {
        espn.clearCache()
        ge.clearCache()
    }
}
