package io.github.alcindogandhi.champs.ui.common

import android.content.res.Resources
import io.github.alcindogandhi.champs.R

/** Nome legível da fase a partir do `season.slug` da ESPN ("taca-guanabara---quarterfinals" etc.). */
object PhaseNames {

    private val known = mapOf(
        "group-stage" to R.string.phase_group_stage,
        "league-phase" to R.string.phase_league_phase,
        "regular-season" to R.string.phase_regular_season,
        "first-stage" to R.string.phase_first_stage,
        "second-stage" to R.string.phase_second_stage,
        "third-stage" to R.string.phase_third_stage,
        "first-round" to R.string.phase_first_round,
        "second-round" to R.string.phase_second_round,
        "third-round" to R.string.phase_third_round,
        "fourth-round" to R.string.phase_fourth_round,
        "fifth-round" to R.string.phase_fifth_round,
        "round-of-32" to R.string.phase_round_of_32,
        "round-of-16" to R.string.phase_round_of_16,
        "quarterfinals" to R.string.phase_quarterfinals,
        "semifinals" to R.string.phase_semifinals,
        "final" to R.string.phase_final,
        "finals" to R.string.phase_final,
        "knockout-round-playoffs" to R.string.phase_knockout_playoffs,
        "playoffs" to R.string.phase_playoffs,
        "relegation-stage" to R.string.phase_relegation,
        "relegation" to R.string.phase_relegation,
        "taca-guanabara" to R.string.phase_taca_guanabara,
        "taca-rio" to R.string.phase_taca_rio,
        "trofeu-inconfidencia" to R.string.phase_trofeu_inconfidencia,
        "farroupilha" to R.string.phase_farroupilha,
        "torneo-apertura" to R.string.phase_apertura,
        "torneo-clausura" to R.string.phase_clausura,
    )

    fun name(resources: Resources, slug: String): String {
        if (slug.isBlank() || slug.first().isDigit()) return resources.getString(R.string.phase_regular_season)
        return slug.split("---").joinToString(" · ") { part ->
            known[part]?.let(resources::getString)
                ?: part.replace('-', ' ').replaceFirstChar { it.uppercase() }
        }
    }
}
