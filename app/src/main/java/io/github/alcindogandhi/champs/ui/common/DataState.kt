package io.github.alcindogandhi.champs.ui.common

import io.github.alcindogandhi.champs.data.Loaded
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de um dado na tela: o último valor conhecido, mais carregamento e erro da última tentativa. */
data class DataState<T>(
    val data: T? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: Throwable? = null,
    val fetchedAt: Long? = null,
)

/**
 * Liga um fluxo do repositório a um [DataState], cancelando a carga anterior a cada nova.
 * [liveSource] é a fonte da atualização automática de jogos ao vivo; sem ela, usa [source] forçando a rede.
 */
class Loader<T>(
    private val scope: CoroutineScope,
    private val liveSource: (() -> Flow<Loaded<T>>)? = null,
    private val source: (force: Boolean) -> Flow<Loaded<T>>,
) {
    private val _state = MutableStateFlow(DataState<T>())
    val state: StateFlow<DataState<T>> = _state.asStateFlow()
    private var job: Job? = null

    /** [force] ignora o cache; [silent] não mostra o indicador de atualização. */
    fun load(force: Boolean = false, silent: Boolean = false) = start(source(force), showRefreshing = force && !silent)

    /**
     * Atualização automática (jogos ao vivo): sempre busca na rede, sem indicador. Não depende da validade
     * do cache, que conta a partir da chegada da resposta e faria um tique sim, outro não, ser ignorado.
     */
    fun refreshLive() = start(liveSource?.invoke() ?: source(true), showRefreshing = false)

    private fun start(flow: Flow<Loaded<T>>, showRefreshing: Boolean) {
        job?.cancel()
        _state.update { it.copy(loading = it.data == null, refreshing = showRefreshing, error = null) }
        job = scope.launch {
            flow
                .catch { e ->
                    if (e is CancellationException) throw e
                    _state.update { it.copy(loading = false, refreshing = false, error = e) }
                }
                .collect { loaded ->
                    _state.update {
                        it.copy(
                            data = loaded.data,
                            fetchedAt = loaded.fetchedAt,
                            loading = false,
                            refreshing = it.refreshing && !loaded.fresh,
                        )
                    }
                }
            _state.update { it.copy(loading = false, refreshing = false) }
        }
    }
}
