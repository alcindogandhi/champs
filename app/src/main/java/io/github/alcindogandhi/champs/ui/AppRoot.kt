package io.github.alcindogandhi.champs.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.R
import io.github.alcindogandhi.champs.catalog.CompetitionCatalog
import io.github.alcindogandhi.champs.data.settings.Settings
import io.github.alcindogandhi.champs.ui.common.TeamCrest
import io.github.alcindogandhi.champs.ui.competition.CompetitionScreen
import io.github.alcindogandhi.champs.ui.competitions.ManageCompetitionsScreen
import io.github.alcindogandhi.champs.ui.match.MatchScreen
import io.github.alcindogandhi.champs.ui.settings.SettingsScreen
import io.github.alcindogandhi.champs.ui.simulator.SimulationScreen
import io.github.alcindogandhi.champs.ui.simulator.SimulatorScreen
import io.github.alcindogandhi.champs.ui.team.FavoriteTeamPickerScreen
import io.github.alcindogandhi.champs.ui.team.TeamScreen
import io.github.alcindogandhi.champs.ui.theme.ChampsTheme
import io.github.alcindogandhi.champs.ui.today.TodayScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data class CompetitionRoute(val slug: String)

@Serializable
data class MatchRoute(val slug: String, val eventId: String)

@Serializable
data class TeamRoute(val slug: String, val teamId: String, val name: String, val logo: String? = null)

@Serializable
data object ManageCompetitionsRoute

@Serializable
data object FavoriteTeamRoute

@Serializable
data class SimulatorRoute(val slug: String)

@Serializable
data class SimulationRoute(val simulationId: Long, val slug: String)

@Serializable
data object SettingsRoute

/** Ações de navegação compartilhadas pelas telas. */
class Navigator(private val nav: NavHostController, val openDrawer: () -> Unit) {
    fun back() {
        nav.popBackStack()
    }

    fun today() = topLevel(TodayRoute)

    fun competition(slug: String) = topLevel(CompetitionRoute(slug))

    private fun topLevel(route: Any) {
        nav.navigate(route) {
            popUpTo<TodayRoute>()
            launchSingleTop = true
        }
    }

    fun match(slug: String, eventId: String) = nav.navigate(MatchRoute(slug, eventId))

    fun team(slug: String, teamId: String, name: String, logo: String?) = nav.navigate(TeamRoute(slug, teamId, name, logo))

    fun manageCompetitions() = nav.navigate(ManageCompetitionsRoute) { launchSingleTop = true }

    fun favoriteTeam() = nav.navigate(FavoriteTeamRoute) { launchSingleTop = true }

    fun simulator(slug: String) = nav.navigate(SimulatorRoute(slug))

    fun simulation(id: Long, slug: String) = nav.navigate(SimulationRoute(id, slug))

    fun settings() = nav.navigate(SettingsRoute) { launchSingleTop = true }
}

@Composable
fun AppRoot(container: AppContainer, pendingMatch: MutableStateFlow<MatchRoute?>) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val current = settings ?: return
    ChampsTheme(current.theme) {
        val nav = rememberNavController()
        val drawerState = rememberDrawerState(DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val navigator = Navigator(nav) { scope.launch { drawerState.open() } }
        // Toque numa notificação de jogo: abre o jogo por cima da tela atual.
        val openMatch by pendingMatch.collectAsStateWithLifecycle()
        LaunchedEffect(openMatch) {
            openMatch?.let { route ->
                nav.navigate(route) { launchSingleTop = true }
                pendingMatch.value = null
            }
        }
        val entry by nav.currentBackStackEntryAsState()
        val destination = entry?.destination
        val topLevel = destination?.hasRoute<TodayRoute>() == true || destination?.hasRoute<CompetitionRoute>() == true
        val selectedSlug = if (destination?.hasRoute<CompetitionRoute>() == true) entry?.toRoute<CompetitionRoute>()?.slug else null

        fun closeThen(action: () -> Unit) {
            scope.launch { drawerState.close() }
            action()
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = topLevel || drawerState.isOpen,
            drawerContent = {
                AppDrawer(
                    settings = current,
                    todaySelected = destination?.hasRoute<TodayRoute>() == true,
                    selectedSlug = selectedSlug,
                    onToday = { closeThen(navigator::today) },
                    onCompetition = { slug -> closeThen { navigator.competition(slug) } },
                    onFavorite = {
                        closeThen {
                            val fav = current.favorite
                            if (fav == null) navigator.favoriteTeam() else navigator.team(fav.competitionSlug, fav.team.id, fav.team.name, fav.team.logo)
                        }
                    },
                    onManage = { closeThen(navigator::manageCompetitions) },
                    onSettings = { closeThen(navigator::settings) },
                )
            },
        ) {
            NavHost(nav, startDestination = TodayRoute) {
                composable<TodayRoute> { TodayScreen(container, navigator) }
                composable<CompetitionRoute> { CompetitionScreen(container, it.toRoute<CompetitionRoute>().slug, navigator) }
                composable<MatchRoute> {
                    val route = it.toRoute<MatchRoute>()
                    MatchScreen(container, route.slug, route.eventId, navigator)
                }
                composable<TeamRoute> {
                    val route = it.toRoute<TeamRoute>()
                    TeamScreen(container, route.slug, route.teamId, route.name, route.logo, navigator)
                }
                composable<ManageCompetitionsRoute> { ManageCompetitionsScreen(container, navigator) }
                composable<FavoriteTeamRoute> { FavoriteTeamPickerScreen(container, navigator) }
                composable<SimulatorRoute> { SimulatorScreen(container, it.toRoute<SimulatorRoute>().slug, navigator) }
                composable<SimulationRoute> {
                    val route = it.toRoute<SimulationRoute>()
                    SimulationScreen(container, route.simulationId, route.slug, navigator)
                }
                composable<SettingsRoute> { SettingsScreen(container, navigator) }
            }
        }
    }
}

@Composable
private fun AppDrawer(
    settings: Settings,
    todaySelected: Boolean,
    selectedSlug: String?,
    onToday: () -> Unit,
    onCompetition: (String) -> Unit,
    onFavorite: () -> Unit,
    onManage: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            val favorite = settings.favorite
            NavigationDrawerItem(
                label = { Text(favorite?.team?.name ?: stringResource(R.string.choose_favorite_team)) },
                badge = if (favorite != null) ({ Text(stringResource(R.string.favorite_team_short)) }) else null,
                icon = {
                    if (favorite != null) TeamCrest(favorite.team, 24.dp) else Icon(Icons.Outlined.FavoriteBorder, contentDescription = null)
                },
                selected = false,
                onClick = onFavorite,
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.matches_today)) },
                icon = { Icon(Icons.Outlined.Today, contentDescription = null) },
                selected = todaySelected,
                onClick = onToday,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                stringResource(R.string.competitions),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            settings.followed.forEach { slug ->
                NavigationDrawerItem(
                    label = { Text(CompetitionCatalog.get(slug).name) },
                    icon = { Icon(Icons.Outlined.EmojiEvents, contentDescription = null) },
                    selected = slug == selectedSlug,
                    onClick = { onCompetition(slug) },
                )
            }
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.manage_competitions)) },
                icon = { Icon(Icons.Outlined.Tune, contentDescription = null) },
                selected = false,
                onClick = onManage,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.settings)) },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                selected = false,
                onClick = onSettings,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
