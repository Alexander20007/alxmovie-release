package com.alxmovies.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alxmovies.app.data.Category
import com.alxmovies.app.data.Channel
import com.alxmovies.app.data.Channels
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Canal escolhido para assistir (lista + posição, para o "anterior/próximo"). */
object ChannelSession {
    var list: List<Channel> = emptyList()
    var index: Int = 0
}

data class ChannelsState(
    val loadingCats: Boolean = true,
    val cats: List<Category> = emptyList(),
    val group: Category? = null,
    val query: String = "",
    val listLoading: Boolean = false,
    val total: Int = 0,
    val tested: Int = 0,
    val done: Boolean = false,
    val shown: List<Channel> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val message: String? = null,
)

class ChannelsViewModel : ViewModel() {
    private val _state = MutableStateFlow(ChannelsState())
    val state: StateFlow<ChannelsState> = _state.asStateFlow()

    private var profileId: String? = null
    private var listJob: Job? = null
    private var searchJob: Job? = null
    private var started = false

    fun start(profileId: String) {
        if (started) return
        started = true
        this.profileId = profileId
        viewModelScope.launch {
            val cats = Channels.categories()
            _state.update { it.copy(loadingCats = false, cats = cats) }
        }
        loadFavorites()
    }

    fun loadFavorites() {
        val pid = profileId ?: return
        viewModelScope.launch {
            runCatching { Channels.favorites(pid) }.onSuccess { f -> _state.update { it.copy(favorites = f) } }
        }
    }

    fun isFavorite(stream: String) = _state.value.favorites.any { it.stream == stream }

    fun toggleFavorite(c: Channel) {
        val pid = profileId ?: return
        val now = isFavorite(c.stream)
        viewModelScope.launch {
            try {
                if (now) Channels.removeFavorite(pid, c.stream) else Channels.addFavorite(pid, c)
                _state.update { s ->
                    s.copy(favorites = if (now) s.favorites.filterNot { it.stream == c.stream } else listOf(c) + s.favorites)
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = "Não foi possível atualizar os favoritos: ${e.message ?: "tente de novo"}") }
            }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }

    fun openGroup(cat: Category) {
        _state.update { it.copy(group = cat) }
        runList { Channels.inCategory(cat.name) }
    }

    fun back() {
        listJob?.cancel()
        searchJob?.cancel()
        _state.update { it.copy(group = null, query = "", shown = emptyList(), listLoading = false, done = false, tested = 0, total = 0) }
    }

    fun setQuery(text: String) {
        val t = text.trim()
        _state.update { it.copy(query = t) }
        searchJob?.cancel()
        if (t.isEmpty()) {
            if (_state.value.group == null) back()
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(group = null) }
            runList { Channels.search(t) }
        }
    }

    private fun runList(source: suspend () -> List<Channel>) {
        listJob?.cancel()
        _state.update { it.copy(listLoading = true, shown = emptyList(), tested = 0, total = 0, done = false) }
        listJob = viewModelScope.launch {
            val list = source().filter { it.stream.isNotEmpty() }
            _state.update { it.copy(listLoading = false, total = list.size) }
            val found = mutableListOf<Channel>()
            Channels.testOnline(
                list,
                onProgress = { n -> _state.update { it.copy(tested = n) } },
                onOnline = { c -> found.add(c); _state.update { it.copy(shown = found.toList()) } },
            )
            _state.update { it.copy(done = true) }
        }
    }
}
