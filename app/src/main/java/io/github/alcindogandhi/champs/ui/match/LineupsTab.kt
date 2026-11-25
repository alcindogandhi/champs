package io.github.alcindogandhi.champs.ui.match

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Stadium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.domain.Lineup
import io.github.alcindogandhi.champs.domain.LineupLayout
import io.github.alcindogandhi.champs.domain.LineupPlayer
import io.github.alcindogandhi.champs.domain.MatchDetail
import io.github.alcindogandhi.champs.ui.common.MessageState
import io.github.alcindogandhi.champs.ui.common.SectionHeader
import io.github.alcindogandhi.champs.ui.common.TeamCrest
import io.github.alcindogandhi.champs.ui.theme.PitchGreen
import io.github.alcindogandhi.champs.ui.theme.PitchGreenDark

private val HomeShirt = Color(0xFFFFFFFF)
private val AwayShirt = Color(0xFF212121)

@Composable
fun LineupsTab(detail: MatchDetail) {
    val home = detail.homeLineup
    val away = detail.awayLineup
    if (home == null && away == null) {
        MessageState(stringResource(R.string.empty_escalacao))
        return
    }
    val homeLines = home?.let { LineupLayout.lines(it.formation, it.starters) }.orEmpty()
    val awayLines = away?.let { LineupLayout.lines(it.formation, it.starters) }.orEmpty()
    val canDrawField = homeLines.isNotEmpty() || awayLines.isNotEmpty()
    var mode by rememberSaveable { mutableIntStateOf(if (canDrawField) 0 else 1) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (canDrawField) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Outlined.Stadium, contentDescription = null, Modifier.size(18.dp)) },
                ) { Text(stringResource(R.string.lineup_field)) }
                SegmentedButton(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.AutoMirrored.Outlined.List, contentDescription = null, Modifier.size(18.dp)) },
                ) { Text(stringResource(R.string.lineup_list)) }
            }
        }
        if (mode == 0 && canDrawField) {
            FormationLabel(away, alignEnd = false)
            Pitch(homeLines, awayLines)
            FormationLabel(home, alignEnd = false)
            listOfNotNull(home, away).forEach { lineup ->
                SectionHeader(stringResource(R.string.substitutes_of, lineup.team.shortName))
                lineup.substitutes.forEach { PlayerRow(it) }
            }
        } else {
            listOfNotNull(home, away).forEach { lineup -> LineupList(lineup) }
        }
        Spacer(Modifier.padding(8.dp))
    }
}

@Composable
private fun FormationLabel(lineup: Lineup?, alignEnd: Boolean) {
    lineup ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamCrest(lineup.team, 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            lineup.team.shortName,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        )
        lineup.formation?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
    }
}

/** Campo vertical: visitante no alto (goleiro em cima), mandante embaixo. */
@Composable
private fun Pitch(homeLines: List<List<LineupPlayer>>, awayLines: List<List<LineupPlayer>>) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .aspectRatio(0.68f),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawPitch() }
        val width = maxWidth
        val height = maxHeight
        listOf(true to homeLines, false to awayLines).forEach { (isHome, lines) ->
            val count = lines.size
            lines.forEachIndexed { lineIndex, line ->
                // Goleiro perto do gol; última linha perto do meio de campo.
                val depth = if (count > 1) lineIndex / (count - 1f) else 0f
                val yHome = 0.94f - depth * 0.38f
                val y = if (isHome) yHome else 1f - yHome
                line.forEachIndexed { index, player ->
                    val xHome = (index + 1f) / (line.size + 1f)
                    val x = if (isHome) xHome else 1f - xHome
                    PlayerMarker(
                        player = player,
                        shirt = if (isHome) HomeShirt else AwayShirt,
                        number = if (isHome) Color.Black else Color.White,
                        modifier = Modifier.offset(x = width * x - 40.dp, y = height * y - 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerMarker(player: LineupPlayer, shirt: Color, number: Color, modifier: Modifier) {
    Column(modifier.width(80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(26.dp).background(shirt, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(player.jersey, color = number, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            player.name,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun DrawScope.drawPitch() {
    val stripes = 10
    val stripe = size.height / stripes
    for (i in 0 until stripes) {
        drawRect(if (i % 2 == 0) PitchGreen else PitchGreenDark, topLeft = Offset(0f, i * stripe), size = Size(size.width, stripe))
    }
    val line = Color.White.copy(alpha = 0.75f)
    val stroke = Stroke(width = 2.dp.toPx())
    val margin = 6.dp.toPx()
    val w = size.width - 2 * margin
    val h = size.height - 2 * margin
    drawRect(line, topLeft = Offset(margin, margin), size = Size(w, h), style = stroke)
    drawLine(line, Offset(margin, size.height / 2), Offset(size.width - margin, size.height / 2), strokeWidth = stroke.width)
    drawCircle(line, radius = w * 0.14f, center = center, style = stroke)
    val boxW = w * 0.6f
    val boxH = h * 0.15f
    val smallW = w * 0.3f
    val smallH = h * 0.05f
    for (top in listOf(true, false)) {
        val y = if (top) margin else size.height - margin - boxH
        drawRect(line, topLeft = Offset((size.width - boxW) / 2, y), size = Size(boxW, boxH), style = stroke)
        val sy = if (top) margin else size.height - margin - smallH
        drawRect(line, topLeft = Offset((size.width - smallW) / 2, sy), size = Size(smallW, smallH), style = stroke)
    }
}

@Composable
private fun LineupList(lineup: Lineup) {
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamCrest(lineup.team, 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(lineup.team.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        lineup.formation?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
    }
    lineup.starters.sortedBy { it.formationPlace }.forEach { PlayerRow(it) }
    if (lineup.substitutes.isNotEmpty()) {
        SectionHeader(stringResource(R.string.substitutes))
        lineup.substitutes.forEach { PlayerRow(it) }
    }
    HorizontalDivider(Modifier.padding(top = 8.dp))
}

@Composable
private fun PlayerRow(player: LineupPlayer) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(player.jersey, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp))
        Text(player.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (player.subbedIn) {
            Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.subbed_in), Modifier.size(16.dp), tint = Color(0xFF2E7D32))
        }
        if (player.subbedOut) {
            Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.subbed_out), Modifier.size(16.dp), tint = Color(0xFFC62828))
        }
        Text(
            player.position.takeIf { it != "SUB" }.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}
