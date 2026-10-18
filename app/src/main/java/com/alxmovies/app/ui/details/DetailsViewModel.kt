package com.alxmovies.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alxmovies.app.data.CommentRow
import com.alxmovies.app.data.ContentFilter
import com.alxmovies.app.data.EpRef
import com.alxmovies.app.data.Episode
import com.alxmovies.app.data.Graph
import com.alxmovies.app.data.LikeInfo
import com.alxmovies.app.data.MediaDetails
import com.alxmovies.app.data.MediaUtil
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.RatingSummary
import com.alxmovies.app.data.Repo
import com.alxmovies.app.data.Social
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.addFavorite
import com.alxmovies.app.data.details
import com.alxmovies.app.data.isFavorite
import com.alxmovies.app.data.removeFavorite
import com.alxmovies.app.data.season
import com.alxmovies.app.data.trailerKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val media: MediaDetails? = null,
    val blocked: Boolean = false,
    val fav: Boolean = false,
    val favBusy: Boolean = false,
    val season: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val episodesLoading: Boolean = false,
    val start: EpRef? = null,
    val last: EpRef? = null,
    val resumeMovie: Boolean = false,
    val message: String? = null,
    val rating: String? = null,
    val summary: RatingSummary? = null,
    val trailerKey: String? = null,
    val watched: Boolean = false,
    val watchedBusy: Boolean = false,
    val comments: List<CommentRow> = emptyList(),
    val likes: Map<String, LikeInfo> = emptyMap(),
    val commentsLoading: Boolean = true,
    val commentBusy: Boolean = false,
)

class DetailsViewModel : ViewModel() {
    private val session = Graph.session
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()

    private var profile: Profile? = null
    private var type = "movie"
    private var id = 0
    private var loaded = false
    private val seasonCache = mutableMapOf<Int, List<Episode>>()

    fun load(profile: Profile, type: String, id: Int) {
        if (loaded) return
        loaded = true
        this.profile = profile
        this.type = type
        this.id = id
        viewModelScope.launch {
            val m = try {
                Tmdb.details(type, id)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = true) }
                return@launch
            }
            if (!ContentFilter.allowed(m.asItem(), profile)) {
                _state.update { it.copy(loading = false, media = m, blocked = true) }
                return@launch
            }
            val isTv = type == "tv"
            val last = if (isTv) MediaUtil.parseEp(session.pref(MediaUtil.key("lastep", profile.id, "tv", id))) else null
            val first = MediaUtil.seasonsList(m).firstOrNull()
            val start = if (isTv) last ?: EpRef(first?.seasonNumber ?: 1, 1) else null
            val resumeMovie = !isTv &&
                (session.pref(MediaUtil.key("resume", profile.id, type, id))?.toLongOrNull() ?: 0L) > 5L
            _state.update {
                it.copy(
                    loading = false, media = m, start = start, last = last, resumeMovie = resumeMovie,
                    season = start?.season ?: 1,
                )
            }
            launch { runCatching { Repo.isFavorite(profile.id, id, type) }.onSuccess { f -> _state.update { it.copy(fav = f) } } }
            launch { runCatching { Social.getRating(profile.id, id, type) }.onSuccess { r -> _state.update { it.copy(rating = r) } } }
            launch { runCatching { Social.ratingSummary(id, type) }.onSuccess { r -> _state.update { it.copy(summary = r) } } }
            launch { runCatching { Tmdb.trailerKey(type, id) }.onSuccess { k -> _state.update { it.copy(trailerKey = k) } } }
            if (!isTv) launch { runCatching { Social.isMovieWatched(profile.id, id) }.onSuccess { w -> _state.update { it.copy(watched = w) } } }
            loadComments()
            if (isTv && start != null) loadSeason(start.season)
        }
    }

    // ---------------- episódios ----------------

    private fun loadSeason(n: Int) {
        val cached = seasonCache[n]
        if (cached != null) {
            _state.update { it.copy(season = n, episodes = cached, episodesLoading = false) }
            return
        }
        _state.update { it.copy(season = n, episodes = emptyList(), episodesLoading = true) }
        viewModelScope.launch {
            val eps = runCatching { Tmdb.season(id, n) }.getOrDefault(emptyList())
            seasonCache[n] = eps
            _state.update { if (it.season == n) it.copy(episodes = eps, episodesLoading = false) else it }
        }
    }

    fun selectSeason(n: Int) = loadSeason(n)

    // ---------------- lista / visto / avaliação ----------------

    fun toggleFav() {
        val p = profile ?: return
        val m = _state.value.media ?: return
        if (_state.value.favBusy) return
        val now = _state.value.fav
        _state.update { it.copy(favBusy = true) }
        viewModelScope.launch {
            try {
                if (now) Repo.removeFavorite(p.id, id, type) else Repo.addFavorite(p.id, type, m)
                _state.update { it.copy(fav = !now, favBusy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(favBusy = false, message = "Não foi possível atualizar sua lista: ${e.message ?: "tente de novo"}") }
            }
        }
    }

    fun toggleWatched() {
        val p = profile ?: return
        val m = _state.value.media ?: return
        if (_state.value.watchedBusy) return
        val seen = !_state.value.watched
        _state.update { it.copy(watched = seen, watchedBusy = true) }
        viewModelScope.launch {
            try {
                Social.setMovieWatched(p.id, id, m.displayTitle, m.posterPath, seen)
                _state.update { it.copy(watchedBusy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(watched = !seen, watchedBusy = false, message = "Não foi possível salvar agora: ${e.message ?: "tente de novo"}") }
            }
        }
    }

    fun setRating(r: String) {
        val p = profile ?: return
        val m = _state.value.media ?: return
        _state.update { it.copy(rating = r) }
        viewModelScope.launch {
            runCatching { Social.setRating(p.id, id, type, m.displayTitle, r) }
                .onFailure { e -> _state.update { it.copy(message = "Não foi possível avaliar: ${e.message ?: "tente de novo"}") } }
            runCatching { Social.ratingSummary(id, type) }.onSuccess { s -> _state.update { it.copy(summary = s) } }
        }
    }

    // ---------------- comentários ----------------

    private fun loadComments() {
        val p = profile ?: return
        viewModelScope.launch {
            try {
                val list = Social.comments(id, type)
                val likes = runCatching { Social.likes(list.map { it.id }, p.id) }.getOrDefault(emptyMap())
                _state.update { it.copy(comments = list, likes = likes, commentsLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(commentsLoading = false) }
            }
        }
    }

    fun addComment(body: String, parentId: String? = null) {
        val p = profile ?: return
        if (body.isBlank() || _state.value.commentBusy) return
        _state.update { it.copy(commentBusy = true) }
        viewModelScope.launch {
            try {
                Social.addComment(p.id, id, type, body.trim(), parentId)
                _state.update { it.copy(commentBusy = false) }
                loadComments()
            } catch (e: Exception) {
                _state.update { it.copy(commentBusy = false, message = "Não foi possível comentar: ${e.message ?: "tente de novo"}") }
            }
        }
    }

    fun deleteComment(commentId: String) {
        viewModelScope.launch {
            runCatching { Social.deleteComment(commentId) }
                .onFailure { e -> _state.update { it.copy(message = "Não foi possível apagar: ${e.message ?: "tente de novo"}") } }
            loadComments()
        }
    }

    fun toggleLike(commentId: String) {
        val p = profile ?: return
        val cur = _state.value.likes[commentId] ?: LikeInfo()
        _state.update { it.copy(likes = it.likes + (commentId to LikeInfo(if (cur.byMe) cur.count - 1 else cur.count + 1, !cur.byMe))) }
        viewModelScope.launch {
            runCatching { Social.toggleLike(p.id, commentId) }.onFailure { loadComments() }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }
}
