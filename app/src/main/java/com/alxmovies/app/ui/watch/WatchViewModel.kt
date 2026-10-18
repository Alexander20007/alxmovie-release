package com.alxmovies.app.ui.watch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alxmovies.app.AlxPlayerActivity
import com.alxmovies.app.data.ContentFilter
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.Episode
import com.alxmovies.app.data.EpRef
import com.alxmovies.app.data.Friends
import com.alxmovies.app.data.Graph
import com.alxmovies.app.data.MediaDetails
import com.alxmovies.app.data.MediaUtil
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.Repo
import com.alxmovies.app.data.Stream
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.details
import com.alxmovies.app.data.saveProgress
import com.alxmovies.app.data.season
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Phase { LOADING, ERROR, BLOCKED, CLOSED }

data class WatchUi(
    val phase: Phase = Phase.LOADING,
    val slow: Boolean = false,
    val message: String = "",
    val title: String = "",
    val subtitle: String = "",
    val backdrop: String? = null,
)

class PlayRequest(
    val token: Int,
    val url: String,
    val title: String,
    val subtitle: String,
    val poster: String,
    val startMs: Long,
    val startPercent: Double,
    val hasNext: Boolean,
    val server: String,
    val source: String,
    val serverLabels: Array<String>,
    val serverOk: BooleanArray,
    val rate: Float,
)

class PlayerResult(val reason: String, val positionMs: Long, val durationMs: Long, val rate: Float, val server: String?)

/**
 * Prepara o link (API principal + reserva), abre o player nativo e reage ao que a pessoa fez nele:
 * fechou, terminou, foi para o próximo, trocou de servidor ou deu erro. Também salva onde parou.
 */
class WatchViewModel : ViewModel() {
    private val session = Graph.session

    private val _ui = MutableStateFlow(WatchUi())
    val ui: StateFlow<WatchUi> = _ui.asStateFlow()

    private val _request = MutableStateFlow<PlayRequest?>(null)
    val request: StateFlow<PlayRequest?> = _request.asStateFlow()

    private var profile: Profile? = null
    private var type = "movie"
    private var id = 0
    private var media: MediaDetails? = null
    private var ep: EpRef? = null
    private var epName = ""
    private var job: Job? = null
    private var pending: CompletableDeferred<PlayerResult>? = null
    private var token = 0
    private var started = false
    private var lastDur = 0L
    private var hasNextNow = false
    private val seasonCache = mutableMapOf<Int, List<Episode>>()

    private val isTv get() = type == "tv"
    private fun key(kind: String, season: Int? = null, episode: Int? = null) =
        MediaUtil.key(kind, profile!!.id, type, id, season, episode)

    fun start(profile: Profile, type: String, id: Int, season: Int, episode: Int) {
        if (started) return
        started = true
        this.profile = profile
        this.type = type
        this.id = id
        AlxPlayerActivity.progressListener = AlxPlayerActivity.ProgressListener { pos, dur ->
            lastDur = dur
            report(pos, dur, false, hasNextNow)
        }
        job = viewModelScope.launch { begin(season, episode) }
    }

    fun consumeRequest() { _request.value = null }

    fun onPlayerResult(r: PlayerResult) { pending?.complete(r) }

    fun cancel() {
        Friends.stopWatchingAsync()
        job?.cancel()
        pending?.cancel()
        AlxPlayerActivity.progressListener = null
    }

    fun retry() {
        _ui.update { it.copy(phase = Phase.LOADING, slow = false, message = "") }
        job?.cancel()
        job = viewModelScope.launch { playEpisode() }
    }

    override fun onCleared() {
        cancel()
        super.onCleared()
    }

    // ------------------------------------------------------------------ início

    private suspend fun begin(s: Int, e: Int) {
        val m = try {
            Tmdb.details(type, id)
        } catch (ex: Exception) {
            _ui.update { it.copy(phase = Phase.ERROR, message = "Verifique sua conexão e tente de novo.") }
            return
        }
        media = m
        val backdrop = Config.backdrop(m.backdropPath, "w780") ?: Config.poster(m.posterPath, "w780")
        _ui.update { it.copy(title = m.displayTitle, backdrop = backdrop) }

        if (!ContentFilter.allowed(m.asItem(), profile)) {
            _ui.update { it.copy(phase = Phase.BLOCKED, message = "Este título não está disponível para o perfil \"${profile?.name ?: ""}\" por causa da classificação indicativa.") }
            return
        }
        ep = if (isTv) pickStart(m, s, e) else null
        playEpisode()
    }

    private fun pickStart(m: MediaDetails, s: Int, e: Int): EpRef {
        if (s > 0 && e > 0) return EpRef(s, e)
        MediaUtil.parseEp(session.pref(key("lastep")))?.let { return it }
        val first = MediaUtil.seasonsList(m).firstOrNull()
        return EpRef(first?.seasonNumber ?: 1, 1)
    }

    private suspend fun resumeInfo(): Pair<Long, Double> {
        val cur = ep
        val secs = session.pref(key("resume", cur?.season, cur?.episode))?.toLongOrNull() ?: 0L
        if (secs > 5) return secs * 1000 to -1.0
        if (!isTv) {
            val item = runCatching { Repo.fetchContinueWatching(profile!!.id, 50) }.getOrNull()
                ?.firstOrNull { it.mediaId == id && it.mediaType == "movie" }
            val p = item?.progress ?: 0.0
            if (p >= 3) return 0L to p
        }
        return 0L to -1.0
    }

    private suspend fun seasonEps(season: Int): List<Episode> =
        seasonCache[season] ?: Tmdb.season(id, season).also { seasonCache[season] = it }

    private suspend fun findNext(m: MediaDetails, season: Int, episode: Int): EpRef? {
        val today = LocalDate.now().toString()
        val eps = seasonEps(season)
        val nextIn = eps.firstOrNull { it.number > episode }
        if (nextIn != null) {
            val air = nextIn.airDate
            return if (air != null && air > today) null else EpRef(season, nextIn.number)
        }
        val numbers = MediaUtil.seasonsList(m).map { it.seasonNumber }.sorted()
        val nextSeason = numbers.getOrNull(numbers.indexOf(season) + 1) ?: return null
        val first = seasonEps(nextSeason).firstOrNull() ?: return null
        val air = first.airDate
        if (air != null && air > today) return null
        return EpRef(nextSeason, first.number)
    }

    // ------------------------------------------------------------------ laço do player

    private suspend fun playEpisode() {
        val m = media ?: return
        val cur = ep
        var subtitle = ""
        if (isTv && cur != null) {
            epName = runCatching { seasonEps(cur.season).firstOrNull { it.number == cur.episode }?.name }.getOrNull() ?: ""
            subtitle = "T${cur.season} · E${cur.episode}" + if (epName.isNotEmpty()) " — $epName" else ""
        }
        _ui.update { it.copy(phase = Phase.LOADING, slow = false, subtitle = subtitle) }

        val (resumeMs, resumePct) = resumeInfo()
        var atMs = resumeMs
        var usePct = atMs <= 0 && resumePct > 0
        var forced: String? = null
        val tried = mutableSetOf<String>()
        var guard = 0

        while (guard++ < 8) {
            val s = try {
                Stream.resolve(
                    type, id, cur?.season, cur?.episode,
                    server = forced?.takeIf { it != Stream.FALLBACK },
                    fallbackOnly = forced == Stream.FALLBACK,
                    onSlow = { _ui.update { u -> if (u.phase == Phase.LOADING) u.copy(slow = true) else u } },
                )
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                _ui.update { it.copy(phase = Phase.ERROR, message = ex.message ?: "Erro ao carregar o vídeo.") }
                return
            }
            tried.add(s.server)

            var next: EpRef? = s.next
            if (next == null && isTv && cur != null) next = runCatching { findNext(m, cur.season, cur.episode) }.getOrNull()
            hasNextNow = next != null

            val rate = session.pref("player_rate")?.toFloatOrNull() ?: 1f
            val d = CompletableDeferred<PlayerResult>()
            pending = d
            _request.value = PlayRequest(
                token = ++token, url = s.m3u8, title = m.displayTitle, subtitle = subtitle,
                poster = Config.backdrop(m.backdropPath, "w780") ?: "",
                startMs = atMs, startPercent = if (usePct) resumePct else -1.0,
                hasNext = next != null, server = s.server, source = s.source,
                serverLabels = s.servers.map { it.label }.toTypedArray(),
                serverOk = s.servers.map { it.ok }.toBooleanArray(),
                rate = rate,
            )
            val res = d.await()
            pending = null

            session.setPref("player_rate", res.rate.toString())
            val dur = if (res.durationMs > 0) res.durationMs else lastDur
            atMs = res.positionMs
            usePct = false

            when (res.reason) {
                "closed" -> {
                    report(res.positionMs, dur, false, next != null)
                    close()
                    return
                }
                "ended" -> {
                    report(res.positionMs, dur, true, next != null)
                    if (next != null) goNext(next) else close()
                    return
                }
                "next" -> {
                    report(res.positionMs, dur, false, true)
                    if (next != null) goNext(next) else close()
                    return
                }
                "server" -> {
                    forced = res.server
                    continue
                }
                else -> {
                    // erro de reprodução: tenta outro servidor e, por último, a API reserva
                    val other = s.servers.firstOrNull { it.ok && it.label !in tried }
                    if (other != null) forced = other.label
                    else if (s.source == "main" && Stream.FALLBACK !in tried) forced = Stream.FALLBACK
                    else {
                        _ui.update { it.copy(phase = Phase.ERROR, message = "Não foi possível reproduzir este vídeo. Tente outro servidor ou tente novamente.") }
                        return
                    }
                }
            }
        }
    }

    private fun close() {
        Friends.stopWatchingAsync()
        _ui.update { it.copy(phase = Phase.CLOSED) }
    }

    private fun goNext(n: EpRef) {
        ep = n
        session.setPref(key("lastep"), "${n.season}:${n.episode}")
        job = viewModelScope.launch { playEpisode() }
    }

    // ------------------------------------------------------------------ progresso

    private fun report(posMs: Long, durMs: Long, ended: Boolean, hasNext: Boolean) {
        if (durMs <= 0) return
        val percent = if (ended) 100.0 else posMs.toDouble() / durMs * 100.0
        handleProgress(posMs / 1000.0, percent, ended, hasNext)
    }

    private fun handleProgress(time: Double, percent: Double, ended: Boolean, hasNext: Boolean) {
        val m = media ?: return
        val p = profile ?: return
        val e = ep
        if (isTv && e != null) session.setPref(key("lastep"), "${e.season}:${e.episode}")
        val rk = key("resume", e?.season, e?.episode)
        if (percent >= 95 || ended) session.delPref(rk)
        else if (time > 5) session.setPref(rk, time.toLong().toString())

        if (time < 5 && !ended) return // não sobrescreve progresso antigo com 0%
        val pct = if (isTv && hasNext) minOf(percent, 94.0) else percent
        viewModelScope.launch {
            runCatching { Repo.saveProgress(p.id, id, type, m.displayTitle, m.posterPath, pct) }
            if (!ended && percent < 95) {
                runCatching { Friends.announceWatching(p.id, id, type, m.displayTitle, m.posterPath, e?.season, e?.episode) }
            }
        }
    }
}
