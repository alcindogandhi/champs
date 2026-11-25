package io.github.alcindogandhi.champs.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.data.settings.TableColumn
import io.github.alcindogandhi.champs.domain.StandingGroup
import io.github.alcindogandhi.champs.domain.StandingRow
import io.github.alcindogandhi.champs.domain.ZoneInfo
import io.github.alcindogandhi.champs.domain.ZoneKind

@StringRes
fun TableColumn.header(): Int = when (this) {
    TableColumn.PLAYED -> R.string.header_played
    TableColumn.WINS -> R.string.header_wins
    TableColumn.DRAWS -> R.string.header_draws
    TableColumn.LOSSES -> R.string.header_losses
    TableColumn.GOALS_FOR -> R.string.header_goals_for
    TableColumn.GOALS_AGAINST -> R.string.header_goals_against
    TableColumn.GOAL_DIFFERENCE -> R.string.header_goal_difference
    TableColumn.PERCENTAGE -> R.string.header_percentage
}

@StringRes
fun TableColumn.label(): Int = when (this) {
    TableColumn.PLAYED -> R.string.played
    TableColumn.WINS -> R.string.wins
    TableColumn.DRAWS -> R.string.draws
    TableColumn.LOSSES -> R.string.losses
    TableColumn.GOALS_FOR -> R.string.goals_for
    TableColumn.GOALS_AGAINST -> R.string.goals_against
    TableColumn.GOAL_DIFFERENCE -> R.string.goal_difference
    TableColumn.PERCENTAGE -> R.string.percentage
}

private fun StandingRow.value(column: TableColumn): String = when (column) {
    TableColumn.PLAYED -> played.toString()
    TableColumn.WINS -> wins.toString()
    TableColumn.DRAWS -> draws.toString()
    TableColumn.LOSSES -> losses.toString()
    TableColumn.GOALS_FOR -> goalsFor.toString()
    TableColumn.GOALS_AGAINST -> goalsAgainst.toString()
    TableColumn.GOAL_DIFFERENCE -> if (goalDifference > 0) "+$goalDifference" else goalDifference.toString()
    TableColumn.PERCENTAGE -> percentage.toString()
}

@StringRes
fun ZoneKind.label(): Int = when (this) {
    ZoneKind.CONTINENTAL -> R.string.zone_continental
    ZoneKind.CONTINENTAL_QUALIFYING -> R.string.zone_continental_qualifying
    ZoneKind.SECONDARY_CONTINENTAL -> R.string.zone_secondary_continental
    ZoneKind.PROMOTION -> R.string.zone_promotion
    ZoneKind.PLAYOFF -> R.string.zone_playoff
    ZoneKind.RELEGATION -> R.string.zone_relegation
}

private fun parseColor(hex: String): Color? =
    runCatching { Color(hex.toColorInt()) }.getOrNull()

private val NumberWidth = 30.dp

/**
 * Tabela de classificação dentro de uma LazyColumn. [variation] (id do time → posições ganhas) adiciona a
 * coluna de variação do simulador.
 */
fun LazyListScope.standingsTable(
    groups: List<StandingGroup>,
    columns: Set<TableColumn>,
    onTeamClick: ((StandingRow) -> Unit)?,
    highlightTeamId: String? = null,
    variation: Map<String, Int>? = null,
) {
    val visible = TableColumn.entries.filter { it in columns }
    groups.forEachIndexed { index, group ->
        item(key = "header-$index") {
            if (group.name != null) SectionHeader(group.name)
            HeaderRow(visible, variation != null)
        }
        items(group.rows, key = { "row-$index-${it.team.id}" }) { row ->
            StandingRowView(row, visible, onTeamClick, row.team.id == highlightTeamId, variation?.get(row.team.id))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
    val zones = groups.flatMap { g -> g.rows.mapNotNull { it.zone } }.distinctBy { it.description ?: it.kind?.name ?: it.color }
    if (zones.isNotEmpty()) {
        item(key = "legend") { Legend(zones) }
    }
}

@Composable
private fun HeaderRow(columns: List<TableColumn>, showVariation: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = MaterialTheme.typography.labelMedium
        Text(stringResource(R.string.header_position), style = style, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        Text(stringResource(R.string.team), style = style, modifier = Modifier.weight(1f).padding(start = 30.dp))
        Text(stringResource(R.string.header_points), style = style, fontWeight = FontWeight.Bold, modifier = Modifier.width(NumberWidth), textAlign = TextAlign.Center)
        columns.forEach { col ->
            Text(stringResource(col.header()), style = style, modifier = Modifier.width(NumberWidth), textAlign = TextAlign.Center)
        }
        if (showVariation) {
            Text(stringResource(R.string.header_variation), style = style, modifier = Modifier.width(NumberWidth), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun StandingRowView(
    row: StandingRow,
    columns: List<TableColumn>,
    onTeamClick: ((StandingRow) -> Unit)?,
    highlight: Boolean,
    variation: Int?,
) {
    val zoneColor = row.zone?.color?.let(::parseColor)
    val description = stringResource(R.string.standing_row_description, row.rank, row.team.name, row.points)
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .then(if (highlight) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) else Modifier)
            .then(if (onTeamClick != null) Modifier.clickable { onTeamClick(row) } else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(zoneColor ?: Color.Transparent))
        Spacer(Modifier.width(4.dp))
        Text(
            row.rank.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Center,
        )
        TeamCrest(row.team, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            row.team.shortName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            row.points.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(NumberWidth),
            textAlign = TextAlign.Center,
        )
        columns.forEach { col ->
            Text(row.value(col), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(NumberWidth), textAlign = TextAlign.Center)
        }
        if (variation != null) {
            val (text, color) = when {
                variation > 0 -> "▲$variation" to Color(0xFF2E7D32)
                variation < 0 -> "▼${-variation}" to Color(0xFFC62828)
                else -> "–" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.width(NumberWidth), textAlign = TextAlign.Center)
        }
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun Legend(zones: List<ZoneInfo>) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        zones.forEach { zone ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(parseColor(zone.color) ?: Color.Gray, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(
                    zone.description ?: zone.kind?.let { stringResource(it.label()) }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (zones.any { it.kind != null }) {
            Text(
                stringResource(R.string.zones_approximate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
