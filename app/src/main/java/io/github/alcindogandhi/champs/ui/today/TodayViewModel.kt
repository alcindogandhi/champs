package io.github.alcindogandhi.champs.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.alcindogandhi.champs.AppContainer
import io.github.alcindogandhi.champs.domain.Match
import io.github.alcindogandhi.champs.ui.common.DataState
import io.github.alcindogandhi.champs.ui.common.Loader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodaySection(val slug: String, val state: DataState<List<Match>>)

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val liveOnly: Boolean = false,
    val sections: List<TodaySection> = emptyList(),
) {
    val loading get() = sections.isNotEmpty() && sections.all { it.state.data == null && it.state.loading }
    val refreshing get() = sections.any { it.state.refreshing }
    val anyLive get() = sections.any { s -> s.state.data.orEmpty().any { it.isLive } }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(private val container: AppContainer) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())
    private val liveOnly = MutableStateFlow(false)
    private var loaders: List<Pair<String, Loader<List<Match>>>> = emptyList()
    private val loadersFlow = MutableStateFlow(loaders)

    init {
        viewModelScope.launch {
            combine(container.settings.settings.map { it.followed }.distinctUntilChanged(), date) { followed, day -> followed to day }
                .collect { (followed, day) ->
                    loaders = followed.map { slug ->
                        slug to Loader(viewModelScope) { force -> container.football.matchesOn(slug, day, force) }
                    }
                    loadersFlow.value = loaders
                    loaders.forEach { it.second.load() }
                }
        }
    }

    val state: StateFlow<TodayUiState> = loadersFlow
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(list.map { (slug, loader) -> loader.state.map { TodaySection(slug, it) } }) { it.toList() }
            }
        }
        .let { sections -> combine(sections, date, liveOnly) { s, d, l -> TodayUiState(d, l, s) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun refresh(force: Boolean = true, silent: Boolean = false) = loaders.forEach { it.second.load(force, silent) }

    fun shiftDay(days: Long) = date.update { it.plusDays(days) }

    fun goToday() {
        date.value = LocalDate.now()
    }

    fun toggleLiveOnly() = liveOnly.update { !it }
}
