package io.github.alcindogandhi.champs.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.ui.common.Loader

class MatchViewModel(container: AppContainer, val slug: String, eventId: String) : ViewModel() {
    val detail = Loader(viewModelScope) { force -> container.football.matchDetail(slug, eventId, force) }

    init {
        detail.load()
    }

    /** Atualização pedida pelo usuário: busca na rede e mostra o indicador. */
    fun refresh() = detail.load(force = true)

    /** Atualização automática durante o jogo, sem indicador. */
    fun refreshLive() = detail.refreshLive()
}
