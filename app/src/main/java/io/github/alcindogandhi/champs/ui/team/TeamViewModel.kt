package io.github.alcindogandhi.champs.ui.team

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.data.settings.FavoriteTeam
import io.github.alcindogandhi.champs.data.settings.Settings
import io.github.alcindogandhi.champs.domain.Team
import io.github.alcindogandhi.champs.ui.common.Loader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TeamViewModel(private val container: AppContainer, val slug: String, val team: Team) : ViewModel() {

    val schedule = Loader(viewModelScope) { force -> container.football.teamSchedule(slug, team.id, force) }
    val standings = Loader(viewModelScope) { force -> container.football.standings(slug, force) }

    val settings: StateFlow<Settings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    init {
        schedule.load()
        standings.load()
    }

    fun refresh() {
        schedule.load(force = true)
        standings.load(force = true)
    }

    fun toggleFavorite() = viewModelScope.launch {
        val isFavorite = settings.value.favorite?.team?.id == team.id
        container.settings.setFavorite(if (isFavorite) null else FavoriteTeam(slug, team))
    }
}
