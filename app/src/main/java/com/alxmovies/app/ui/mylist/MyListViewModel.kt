package com.alxmovies.app.ui.mylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alxmovies.app.data.Channels
import com.alxmovies.app.data.PosterItem
import com.alxmovies.app.data.Repo
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.basic
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class Badge(val icon: String, val label: String, val detail: String)
data class GenreStat(val name: String, val count: Int)

data class MyListState(
    val tab: Int = 0,
    val loading: Boolean = true,
    val error: String? = null,
    val items: Map<Int, List<PosterItem>> = emptyMap(),
    val badges: List<Badge>? = null,
    val badgesError: Boolean = false,
    val more: Boolean = false,
    val genreStats: List<GenreStat>? = null,
)

class MyListViewModel : ViewModel() {
    private val _state = MutableStateFlow(MyListState())
    val state: StateFlow<MyListState> = _state.asStateFlow()
    private var pid: String? = null

    fun start(profileId: String) {
        if (pid == profileId) return
        pid = profileId
        _state.value = MyListState()
        loadTab(0)
        loadBadges()
    }

    fun selectTab(t: Int) {
        _state.update { it.copy(tab = t, error = null) }
        if (_state.value.items[t] == null) loadTab(t)
    }

    fun reload() = loadTab(_state.value.tab, force = true)

    private fun loadTab(t: Int, force: Boolean = false) {
        val p = pid ?: return
        if (!force && _state.value.items[t] != null) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val list: List<PosterItem> = when (t) {
                    0 -> Repo.fetchMyList(p).map { PosterItem(it.mediaId, it.mediaType, it.title ?: "", it.posterPath) }
                    1 -> Repo.fetchContinueWatching(p).map { PosterItem(it.mediaId, it.mediaType, it.title ?: "", it.posterPath, (it.progress ?: 0.0).toInt()) }
                    else -> Repo.fetchWatched(p, 50).map { PosterItem(it.mediaId, it.mediaType, it.title ?: "", it.posterPath) }
                }
                _state.update { it.copy(loading = false, items = it.items + (t to list)) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Erro ao carregar") }
            }
        }
    }

    // ---------------- conquistas ----------------

    private suspend fun genreStats(p: String): List<GenreStat> {
        val watched = runCatching { Repo.fetchWatched(p, 30) }.getOrDefault(emptyList()).map { it.mediaType to it.mediaId }
        val favs = runCatching { Repo.fetchMyList(p) }.getOrDefault(emptyList()).map { it.mediaType to it.mediaId }
        val items = (watched + favs).distinct().take(30)
        val counts = HashMap<String, Int>()
        val sem = Semaphore(6)
        kotlinx.coroutines.coroutineScope {
            items.map { (type, id) ->
                async {
                    sem.withPermit {
                        runCatching { Tmdb.basic(type, id) }.getOrNull()?.genres?.forEach { g ->
                            synchronized(counts) { counts[g.name] = (counts[g.name] ?: 0) + 1 }
                        }
                    }
                }
            }.awaitAll()
        }
        return counts.entries.map { GenreStat(it.key, it.value) }.sortedByDescending { it.count }
    }

    private fun loadBadges() {
        val p = pid ?: return
        viewModelScope.launch {
            try {
                val watched = runCatching { Repo.fetchWatched(p, 200) }.getOrDefault(emptyList())
                val favorites = runCatching { Repo.fetchMyList(p) }.getOrDefault(emptyList())
                val channels = runCatching { Channels.favorites(p) }.getOrDefault(emptyList())
                val genres = genreStats(p)
                val b = mutableListOf<Badge>()
                if (watched.isNotEmpty()) b.add(Badge("🎬", "Primeiro filme", "Assistiu o primeiro título"))
                if (watched.size >= 10) b.add(Badge("🍿", "Maratonista", "${watched.size} títulos assistidos"))
                if (watched.size >= 25) b.add(Badge("🏆", "Cinéfilo", "${watched.size} títulos assistidos"))
                if (favorites.size >= 10) b.add(Badge("❤️", "Curador", "${favorites.size} favoritos"))
                if (channels.size >= 5) b.add(Badge("📡", "Zapper", "${channels.size} canais favoritos"))
                genres.firstOrNull()?.takeIf { it.count >= 5 }?.let { b.add(Badge("🎯", "Fã de ${it.name}", "${it.count} títulos desse gênero")) }
                _state.update { it.copy(badges = b, genreStats = genres) }
            } catch (e: Exception) {
                _state.update { it.copy(badgesError = true) }
            }
        }
    }

    fun toggleMore() = _state.update { it.copy(more = !it.more) }
}
