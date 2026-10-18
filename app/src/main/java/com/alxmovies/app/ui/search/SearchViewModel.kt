package com.alxmovies.app.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alxmovies.app.data.ContentFilter
import com.alxmovies.app.data.Genre
import com.alxmovies.app.data.MediaItem
import com.alxmovies.app.data.PosterItem
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.search
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchState(
    val results: List<PosterItem> = emptyList(),
    val loading: Boolean = false,
    val error: Boolean = false,
    val searched: Boolean = false,
    val genres: List<Genre> = emptyList(),
)

class SearchViewModel : ViewModel() {
    var term by mutableStateOf("")
        private set
    /** "", "movie", "tv" ou "channels". */
    var type by mutableStateOf("")
        private set
    var genre by mutableIntStateOf(0)
        private set

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var profile: Profile? = null
    private var job: Job? = null
    private val genreCache = mutableMapOf<String, List<Genre>>()

    fun init(p: Profile) {
        if (profile?.id == p.id) return
        profile = p
        loadGenres()
    }

    fun setTerm(t: String) { term = t; refresh(400) }
    fun setType(t: String) { type = t; genre = 0; loadGenres(); refresh(0) }
    fun setGenre(g: Int) { genre = g; refresh(0) }

    private fun loadGenres() {
        if (type == "channels") return
        val t = if (type == "tv") "tv" else "movie"
        viewModelScope.launch {
            val g = genreCache[t] ?: runCatching { Tmdb.genres(t) }.getOrDefault(emptyList()).also { if (it.isNotEmpty()) genreCache[t] = it }
            _state.update { it.copy(genres = g) }
        }
    }

    private fun refresh(delayMs: Long) {
        job?.cancel()
        val q = term.trim()
        if (type == "channels" || (q.isEmpty() && genre == 0 && type.isEmpty())) {
            _state.update { it.copy(results = emptyList(), loading = false, error = false, searched = false) }
            return
        }
        val mt = type
        val g = genre
        job = viewModelScope.launch {
            delay(delayMs)
            _state.update { it.copy(loading = true, error = false, searched = true) }
            try {
                val raw: List<MediaItem> = when {
                    q.isNotEmpty() -> {
                        var l = Tmdb.search(q, if (mt.isEmpty()) "multi" else mt).results.filter { it.mediaType != "person" }
                        if (g != 0) l = l.filter { it.genreIds?.contains(g) == true }
                        l
                    }
                    g != 0 -> Tmdb.byGenre(mt.ifEmpty { "movie" }, g).results
                    else -> Tmdb.popular(mt.ifEmpty { "movie" }).results
                }
                val items = ContentFilter.filter(raw, profile).filter { it.posterPath != null }.map {
                    val t = if (it.mediaType == "tv" || (it.mediaType == null && (mt == "tv" || (it.title == null && it.name != null)))) "tv" else "movie"
                    PosterItem(it.id, t, it.displayTitle, it.posterPath)
                }
                _state.update { it.copy(results = items, loading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }
}
