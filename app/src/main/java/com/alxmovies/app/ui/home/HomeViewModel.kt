package com.alxmovies.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alxmovies.app.data.ContentFilter
import com.alxmovies.app.data.MediaItem
import com.alxmovies.app.data.PosterItem
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.Repo
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.toPoster
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RowData(val order: Int, val title: String, val items: List<PosterItem>, val ranked: Boolean = false)

data class HomeState(
    val loading: Boolean = true,
    val heroes: List<MediaItem> = emptyList(),
    val rows: List<RowData> = emptyList(),
    val failed: Boolean = false,
)

private data class Seed(val id: Int, val type: String, val title: String?)

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private var loadedFor: String? = null
    private var job: Job? = null

    private fun addRow(row: RowData) {
        if (row.items.isEmpty()) return
        _state.update { s -> s.copy(rows = (s.rows.filterNot { it.order == row.order } + row).sortedBy { it.order }) }
    }

    fun load(profile: Profile, force: Boolean = false) {
        if (!force && loadedFor == profile.id) return
        loadedFor = profile.id
        job?.cancel()
        _state.value = HomeState()

        job = viewModelScope.launch {
            coroutineScope {
                // Destaques do topo
                launch {
                    runCatching { Tmdb.trending("movie", "day") }.onSuccess { r ->
                        val heroes = ContentFilter.filter(r.results, profile).filter { it.backdropPath != null }.take(8)
                        _state.update { it.copy(heroes = heroes) }
                    }
                }
                // Continuar assistindo
                launch {
                    runCatching { Repo.fetchContinueWatching(profile.id) }.onSuccess { list ->
                        addRow(RowData(0, "Continuar assistindo", list.map {
                            PosterItem(it.mediaId, it.mediaType, it.title ?: "", it.posterPath, (it.progress ?: 0.0).toInt())
                        }))
                    }
                }
                // Assistidos recentemente + "Porque você assistiu X"
                launch {
                    val watched = runCatching { Repo.fetchWatched(profile.id) }.getOrDefault(emptyList())
                    addRow(RowData(1, "Assistidos recentemente", watched.map {
                        PosterItem(it.mediaId, it.mediaType, it.title ?: "", it.posterPath)
                    }))
                    val seed: Seed? = watched.firstOrNull()?.let { Seed(it.mediaId, it.mediaType, it.title) }
                        ?: runCatching { Repo.fetchMyList(profile.id) }.getOrNull()?.firstOrNull()
                            ?.let { Seed(it.mediaId, it.mediaType, it.title) }
                    if (seed != null) {
                        runCatching { Tmdb.recommendations(seed.type, seed.id) }.onSuccess { r ->
                            val items = ContentFilter.filter(r.results, profile).map { it.toPoster(seed.type) }
                            addRow(RowData(2, "Porque você assistiu \"${seed.title ?: "esse título"}\"", items))
                        }
                    }
                }
                // Em alta esta semana
                launch {
                    runCatching { Tmdb.trending("movie", "week") }.onSuccess { r ->
                        addRow(RowData(4, "Em alta esta semana", ContentFilter.filter(r.results, profile).map { it.toPoster() }, ranked = true))
                    }
                }
                // Populares
                launch {
                    runCatching { Tmdb.popular("movie") }.onSuccess { r ->
                        addRow(RowData(5, "Populares", ContentFilter.filter(r.results, profile).map { it.toPoster() }))
                    }
                }
                // Até 4 fileiras de gênero (favoritos do perfil primeiro; infantil só gêneros seguros)
                launch {
                    val genres = runCatching { Tmdb.genres("movie") }.getOrDefault(emptyList())
                    val pool = if (profile.kids) genres.filter { it.id == 16 || it.id == 10751 } else genres
                    val fav = profile.favGenres()
                    val featured = (pool.filter { it.id in fav } + pool.filterNot { it.id in fav }).take(4)
                    coroutineScope {
                        featured.forEachIndexed { i, g ->
                            launch {
                                runCatching { Tmdb.byGenre("movie", g.id) }.onSuccess { r ->
                                    addRow(RowData(10 + i, g.name, ContentFilter.filter(r.results, profile).map { it.toPoster() }))
                                }
                            }
                        }
                    }
                }
            }
            _state.update { it.copy(loading = false, failed = it.rows.isEmpty() && it.heroes.isEmpty()) }
        }
    }
}
