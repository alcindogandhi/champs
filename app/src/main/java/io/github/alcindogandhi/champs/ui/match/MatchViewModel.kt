package io.github.alcindogandhi.champs.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.ui.common.Loader
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MatchViewModel(private val container: AppContainer, val slug: String, private val eventId: String) : ViewModel() {
    val detail = Loader(viewModelScope) { force -> container.football.matchDetail(slug, eventId, force) }

    /** O jogo está na lista de acompanhados (avisos de início e gols). */
    val following: StateFlow<Boolean> = container.watcher.observeFollowedIds()
        .map { eventId in it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        detail.load()
    }

    /** Atualização pedida pelo usuário: busca na rede e mostra o indicador. */
    fun refresh() = detail.load(force = true)

    /** Atualização automática durante o jogo, sem indicador. */
    fun refreshLive() = detail.refreshLive()

    fun follow(match: Match, onFollowed: (explainExactAlarm: Boolean) -> Unit) = viewModelScope.launch {
        container.watcher.follow(match)
        val explain = !container.watcher.canScheduleExactAlarms() && !container.settings.settings.first().exactAlarmExplained
        if (explain) container.settings.setExactAlarmExplained()
        onFollowed(explain)
    }

    fun unfollow() = viewModelScope.launch { container.watcher.unfollow(eventId) }
}
