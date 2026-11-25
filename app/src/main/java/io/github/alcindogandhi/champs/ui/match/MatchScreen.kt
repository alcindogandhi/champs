package io.github.alcindogandhi.champs.ui.match

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import io.github.alcindogandhi.champs.ui.common.exactAlarmSettingsIntent
import io.github.alcindogandhi.champs.ui.common.rememberNotificationPermissionRequest
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.domain.EventKind
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.domain.MatchDetail
import io.github.alcindogandhi.champs.domain.MatchState
import io.github.alcindogandhi.champs.domain.Play
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.domain.TeamStatComparison
import io.github.alcindogandhi.champs.ui.Navigator
import io.github.alcindogandhi.champs.ui.common.DataContent
import io.github.alcindogandhi.champs.ui.common.Format
import io.github.alcindogandhi.champs.ui.common.LiveBadge
import io.github.alcindogandhi.champs.ui.common.MessageState
import io.github.alcindogandhi.champs.ui.common.ScoreBox
import io.github.alcindogandhi.champs.ui.common.TeamCrest
import io.github.alcindogandhi.champs.ui.common.statusText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchScreen(container: AppContainer, slug: String, eventId: String, navigator: Navigator) {
    val vm = viewModel(key = "match-$slug-$eventId") { MatchViewModel(container, slug, eventId) }
    val state by vm.detail.state.collectAsStateWithLifecycle()
    val following by vm.following.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var explainingExactAlarm by rememberSaveable { mutableStateOf(false) }
    val followedMessage = stringResource(R.string.follow_match_done)
    val blockedMessage = stringResource(R.string.notifications_blocked)
    val match = state.data?.match
    val canFollow = match != null && (match.status.state == MatchState.SCHEDULED || match.isLive)
    val requestPermission = rememberNotificationPermissionRequest { granted ->
        val current = match ?: return@rememberNotificationPermissionRequest
        if (!granted) {
            scope.launch { snackbar.showSnackbar(blockedMessage) }
            return@rememberNotificationPermissionRequest
        }
        vm.follow(current) { explain ->
            if (explain) explainingExactAlarm = true else scope.launch { snackbar.showSnackbar(followedMessage) }
        }
    }
    val live = state.data?.match?.needsLiveRefresh() == true
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(live) {
        if (!live) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(20_000)
                vm.refreshLive()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(CompetitionCatalog.get(slug).name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = navigator::back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (following || canFollow) {
                        IconButton(onClick = { if (following) vm.unfollow() else requestPermission() }) {
                            if (following) {
                                Icon(
                                    Icons.Filled.NotificationsActive,
                                    contentDescription = stringResource(R.string.unfollow_match),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(Icons.Outlined.NotificationAdd, contentDescription = stringResource(R.string.follow_match))
                            }
                        }
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        DataContent(
            state = state,
            onRefresh = { vm.refresh() },
            emptyText = stringResource(R.string.empty_lances),
            modifier = Modifier.padding(padding),
        ) { detail ->
            Column(Modifier.fillMaxSize()) {
                MatchHeader(detail.match) { team -> navigator.team(slug, team.id, team.name, team.logo) }
                if (CompetitionCatalog.find(slug)?.fromGe == true) GeMatchPanel(detail.match) else MatchTabs(detail)
            }
        }
    }

    if (explainingExactAlarm) {
        ExactAlarmDialog(
            onAllow = {
                explainingExactAlarm = false
                runCatching { context.startActivity(exactAlarmSettingsIntent(context)) }
            },
            onDismiss = {
                explainingExactAlarm = false
                scope.launch { snackbar.showSnackbar(followedMessage) }
            },
        )
    }
}

/** Jogo de campeonato do ge: sem lances, escalações e estatísticas por API; oferece a página do jogo. */
@Composable
private fun GeMatchPanel(match: Match) {
    val uri = LocalUriHandler.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.ge_match_details_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        match.url?.let { url ->
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { runCatching { uri.openUri(url) } }) {
                Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ge_open_match))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.source_ge), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExactAlarmDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.exact_alarm_dialog_title)) },
        text = { Text(stringResource(R.string.exact_alarm_dialog_text)) },
        confirmButton = { TextButton(onClick = onAllow) { Text(stringResource(R.string.allow)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

@Composable
private fun MatchHeader(match: Match, onTeam: (Team) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeaderTeam(match.home.team, Modifier.weight(1f)) { onTeam(match.home.team) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreBox(match, large = true)
                    Spacer(Modifier.height(6.dp))
                    if (match.isLive) LiveBadge(statusText(match)) else Text(
                        if (match.status.state == MatchState.SCHEDULED) Format.time(match.date) else statusText(match),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HeaderTeam(match.away.team, Modifier.weight(1f)) { onTeam(match.away.team) }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                listOf(Format.dateTime(match.date), match.venue).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (match.note.isNotBlank()) {
                Text(match.note, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun HeaderTeam(team: Team, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TeamCrest(team, 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            team.shortName,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MatchTabs(detail: MatchDetail) {
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val tabs = listOf(R.string.tab_plays, R.string.tab_lineups, R.string.tab_stats)
    PrimaryTabRow(selectedTabIndex = pager.currentPage) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = pager.currentPage == index,
                onClick = { scope.launch { pager.animateScrollToPage(index) } },
                text = { Text(stringResource(title)) },
            )
        }
    }
    HorizontalPager(pager, modifier = Modifier.fillMaxSize()) { page ->
        when (page) {
            0 -> PlaysList(detail)
            1 -> LineupsTab(detail)
            else -> StatsTab(detail)
        }
    }
}

@Composable
private fun PlaysList(detail: MatchDetail) {
    if (detail.plays.isEmpty()) {
        val notStarted = detail.match.status.state == MatchState.SCHEDULED
        MessageState(stringResource(if (notStarted) R.string.match_not_started else R.string.empty_lances))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(detail.plays) { play ->
            PlayRow(play)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun PlayRow(play: Play) {
    val important = play.kind in setOf(EventKind.GOAL, EventKind.OWN_GOAL, EventKind.PENALTY_GOAL, EventKind.RED_CARD)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (important) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            play.clock,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(52.dp),
        )
        Box(Modifier.width(28.dp), contentAlignment = Alignment.TopCenter) { PlayIcon(play.kind) }
        Text(
            play.text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (play.highlight) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PlayIcon(kind: EventKind) {
    when (kind) {
        EventKind.GOAL, EventKind.PENALTY_GOAL -> Icon(Icons.Outlined.SportsSoccer, stringResource(R.string.event_goal), Modifier.size(18.dp))
        EventKind.OWN_GOAL -> Icon(Icons.Outlined.SportsSoccer, stringResource(R.string.event_own_goal), Modifier.size(18.dp), tint = Color(0xFFC62828))
        EventKind.PENALTY_MISSED -> Icon(Icons.Outlined.SportsSoccer, stringResource(R.string.event_penalty_missed), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
        EventKind.YELLOW_CARD -> CardIcon(Color(0xFFFDD835), stringResource(R.string.event_yellow_card))
        EventKind.RED_CARD -> CardIcon(Color(0xFFE53935), stringResource(R.string.event_red_card))
        EventKind.SUBSTITUTION -> Icon(Icons.Outlined.SwapVert, stringResource(R.string.event_substitution), Modifier.size(18.dp))
        EventKind.PERIOD -> Icon(Icons.Outlined.Flag, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        EventKind.VAR -> Icon(Icons.Outlined.Tv, stringResource(R.string.event_var), Modifier.size(18.dp))
        EventKind.OTHER -> Unit
    }
}

@Composable
private fun CardIcon(color: Color, description: String) {
    Box(
        Modifier
            .padding(top = 2.dp)
            .size(width = 11.dp, height = 15.dp)
            .background(color, RoundedCornerShape(2.dp))
            .semantics { contentDescription = description },
    )
}

@Composable
private fun StatsTab(detail: MatchDetail) {
    if (detail.stats.isEmpty()) {
        MessageState(stringResource(R.string.empty_stats))
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamCrest(detail.match.home.team, 28.dp)
                Spacer(Modifier.weight(1f))
                TeamCrest(detail.match.away.team, 28.dp)
            }
        }
        items(detail.stats) { stat -> StatRow(stat) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun StatRow(stat: TeamStatComparison) {
    val total = stat.home + stat.away
    val homeShare = if (total > 0) (stat.home / total).toFloat() else 0.5f
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.homeText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
            Text(
                stringResource(statLabel(stat.key)),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                stat.awayText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(56.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { homeShare },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.tertiary,
            strokeCap = StrokeCap.Round,
            gapSize = 2.dp,
            drawStopIndicator = {},
        )
    }
}

private fun statLabel(key: String): Int = when (key) {
    "possessionPct" -> R.string.stat_possession
    "totalShots" -> R.string.stat_shots
    "shotsOnTarget" -> R.string.stat_shots_on_target
    "wonCorners" -> R.string.stat_corners
    "saves" -> R.string.stat_saves
    "accuratePasses" -> R.string.stat_accurate_passes
    "totalPasses" -> R.string.stat_passes
    "foulsCommitted" -> R.string.stat_fouls
    "offsides" -> R.string.stat_offsides
    "yellowCards" -> R.string.stat_yellow_cards
    "redCards" -> R.string.stat_red_cards
    else -> R.string.stat_other
}
