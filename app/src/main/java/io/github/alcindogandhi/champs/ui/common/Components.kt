package io.github.alcindogandhi.champs.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.MatchState
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.ui.theme.LiveRed

@Composable
fun TeamCrest(team: Team?, size: Dp, modifier: Modifier = Modifier) {
    val placeholder = rememberVectorPainter(Icons.Outlined.Shield)
    AsyncImage(
        model = team?.logo,
        contentDescription = null,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        modifier = modifier.size(size),
    )
}

@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun MessageState(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.SportsSoccer,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    /** false quando já está dentro de uma lista rolável (LazyColumn). */
    scrollable: Boolean = true,
) {
    // Rolável para o "puxar para atualizar" funcionar também na tela vazia.
    Column(
        modifier
            .fillMaxSize()
            .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

/** Aviso de que a tela mostra dado antigo porque a atualização falhou. */
@Composable
fun StaleBanner(error: Throwable, fetchedAt: Long?, onRetry: () -> Unit) {
    val resources = LocalResources.current
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            val message = Format.error(resources, error)
            val text = if (fetchedAt != null) stringResource(R.string.showing_data_from, message, Format.fetchedAt(fetchedAt)) else message
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/**
 * Trata carregando / erro sem dado / vazio e mostra [content] quando há dado, com "puxar para atualizar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DataContent(
    state: DataState<T>,
    onRefresh: () -> Unit,
    emptyText: String,
    modifier: Modifier = Modifier,
    isEmpty: (T) -> Boolean = { false },
    content: @Composable BoxScope.(T) -> Unit,
) {
    val resources = LocalResources.current
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        val data = state.data
        when {
            data == null && state.loading -> LoadingState()
            data == null -> MessageState(
                text = state.error?.let { Format.error(resources, it) } ?: emptyText,
                icon = Icons.Outlined.CloudOff,
                action = stringResource(R.string.retry),
                onAction = onRefresh,
            )
            else -> Column(Modifier.fillMaxSize()) {
                state.error?.let { StaleBanner(it, state.fetchedAt, onRefresh) }
                Box(Modifier.weight(1f)) {
                    if (isEmpty(data)) MessageState(emptyText) else content(data)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
fun LiveBadge(text: String) {
    Text(
        text,
        color = androidx.compose.ui.graphics.Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier.background(LiveRed, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Texto de status do jogo: horário, relógio ao vivo, "Encerrado", "Adiado". */
@Composable
fun statusText(match: Match): String = when (match.status.state) {
    MatchState.SCHEDULED -> Format.time(match.date)
    MatchState.LIVE -> match.status.clock.ifBlank { match.status.detail }.ifBlank { stringResource(R.string.live) }
    MatchState.FINISHED -> stringResource(R.string.match_finished)
    MatchState.POSTPONED -> stringResource(R.string.match_postponed)
    MatchState.CANCELED -> stringResource(R.string.match_canceled)
}

/**
 * Linha de jogo: data/horário ou status em cima e "mandante placar visitante" embaixo.
 * [highlightTeamId] deixa o time em negrito (tela do time).
 */
@Composable
fun MatchRow(
    match: Match,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    highlightTeamId: String? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val info = buildList {
                if (showDate) add(Format.shortDate(match.date))
                if (match.status.state == MatchState.SCHEDULED) add(Format.time(match.date))
                if (match.note.isNotBlank()) add(match.note)
            }.joinToString(" · ")
            if (match.isLive) {
                LiveBadge(statusText(match))
                Spacer(Modifier.width(6.dp))
            } else if (match.status.state != MatchState.SCHEDULED) {
                Text(
                    statusText(match) + " · ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                info,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamName(match.home.team, highlightTeamId, TextAlign.End, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TeamCrest(match.home.team, 24.dp)
            ScoreBox(match, Modifier.padding(horizontal = 8.dp))
            TeamCrest(match.away.team, 24.dp)
            Spacer(Modifier.width(8.dp))
            TeamName(match.away.team, highlightTeamId, TextAlign.Start, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TeamName(team: Team, highlightTeamId: String?, align: TextAlign, modifier: Modifier) {
    Text(
        team.shortName,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (team.id == highlightTeamId) FontWeight.Bold else FontWeight.Normal,
        textAlign = align,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun ScoreBox(match: Match, modifier: Modifier = Modifier, large: Boolean = false) {
    val home = match.home.score
    val away = match.away.score
    val text = if (home != null && away != null) {
        val hs = match.home.shootout
        val aws = match.away.shootout
        if (hs != null && aws != null) "($hs) $home × $away ($aws)" else "$home × $away"
    } else {
        "×"
    }
    Text(
        text,
        style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (match.isLive) LiveRed else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier.background(
            MaterialTheme.colorScheme.surfaceContainerHigh,
            RoundedCornerShape(6.dp),
        ).padding(horizontal = if (large) 16.dp else 8.dp, vertical = 2.dp),
    )
}
