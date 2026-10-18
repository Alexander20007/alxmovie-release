package com.alxmovies.app.ui.rooms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.alxmovies.app.data.EpRef
import com.alxmovies.app.data.Episode
import com.alxmovies.app.data.Graph
import com.alxmovies.app.data.MediaUtil
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.QueueItem
import com.alxmovies.app.data.RoomMessage
import com.alxmovies.app.data.RoomRow
import com.alxmovies.app.data.Rooms
import com.alxmovies.app.data.Stream
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.details
import com.alxmovies.app.data.season
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

data class Person(val userId: String, val name: String)

data class RoomState(
    val room: RoomRow? = null,
    val isHost: Boolean = false,
    val loadingVideo: Boolean = true,
    val slow: Boolean = false,
    val error: String? = null,
    val closed: Boolean = false,
    val messages: List<RoomMessage> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val people: List<Person> = emptyList(),
    val askNext: EpRef? = null,
    val notice: String? = null,
    val position: Long = 0,
    val duration: Long = 0,
)

/** Sala de assistir junto: o anfitrião grava o estado (play/pause/posição); quem está assistindo consulta e acompanha. */
class RoomViewModel : ViewModel() {
    private val session = Graph.session

    val player: ExoPlayer = ExoPlayer.Builder(Graph.appContext).build()

    private val _state = MutableStateFlow(RoomState())
    val state: StateFlow<RoomState> = _state.asStateFlow()

    private var roomId = ""
    private var userId = ""
    private var myName = ""
    private var started = false
    private var suppress = false
    private var contentKey = ""
    private var loadJob: Job? = null
    private val jobs = mutableListOf<Job>()
    private val seasonCache = mutableMapOf<Int, List<Episode>>()
    private var lastHostSeenMs = System.currentTimeMillis()

    private val room get() = _state.value.room
    private val isHost get() = _state.value.isHost

    fun start(profile: Profile, roomId: String) {
        if (started) return
        started = true
        this.roomId = roomId
        this.userId = session.userId ?: ""
        this.myName = profile.name

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { if (isHost && !suppress) pushState() }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                if (isHost && !suppress && reason == Player.DISCONTINUITY_REASON_SEEK) pushState()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && isHost) onEnded()
            }
            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(loadingVideo = false, error = "Não foi possível reproduzir o vídeo.") }
            }
        })

        jobs += viewModelScope.launch {
            val r = runCatching { Rooms.get(roomId) }.getOrNull()
            if (r == null) { _state.update { it.copy(closed = true) }; return@launch }
            _state.update { it.copy(room = r, isHost = r.hostUserId == userId) }
            loadContent(r, firstLoad = true)
        }
        jobs += viewModelScope.launch { while (true) { pollRoom(); delay(2000) } }
        jobs += viewModelScope.launch { while (true) { pollMessages(); delay(2000) } }
        jobs += viewModelScope.launch { while (true) { pollQueue(); delay(5000) } }
        jobs += viewModelScope.launch { while (true) { delay(5000); if (isHost) pushState() } }
        jobs += viewModelScope.launch { while (true) { delay(500); _state.update { it.copy(position = player.currentPosition, duration = maxOf(0L, player.duration)) } } }
    }

    override fun onCleared() {
        jobs.forEach { it.cancel() }
        player.release()
        super.onCleared()
    }

    fun consumeNotice() { _state.update { it.copy(notice = null) } }

    // ------------------------------------------------------------ vídeo

    private fun keyOf(r: RoomRow) = "${r.tmdbId}-${r.mediaType}-${r.season}-${r.episode}"

    private fun loadContent(r: RoomRow, firstLoad: Boolean = false) {
        contentKey = keyOf(r)
        loadJob?.cancel()
        _state.update { it.copy(loadingVideo = true, slow = false, error = null, askNext = null) }
        loadJob = viewModelScope.launch {
            try {
                val s = Stream.resolve(
                    r.mediaType, r.tmdbId, r.season, r.episode, server = null, fallbackOnly = false,
                    onSlow = { _state.update { it.copy(slow = true) } },
                )
                val dsf = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                val src = HlsMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(s.m3u8))
                suppress = true
                player.setMediaSource(src)
                player.prepare()
                player.playWhenReady = false
                _state.update { it.copy(loadingVideo = false) }
                if (!isHost) alignToRoom(r, force = true)
                else if (firstLoad && (r.positionSeconds ?: 0.0) > 3) player.seekTo(((r.positionSeconds ?: 0.0) * 1000).toLong())
                delay(300)
                suppress = false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                suppress = false
                _state.update { it.copy(loadingVideo = false, error = e.message ?: "Não foi possível carregar o vídeo.") }
            }
        }
    }

    fun retry() { room?.let { loadContent(it) } }

    private fun pushState() {
        val rid = room?.id ?: return
        val playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        val pos = player.currentPosition / 1000.0
        viewModelScope.launch { runCatching { Rooms.updateState(rid, playing, pos) } }
    }

    private suspend fun alignToRoom(r: RoomRow, force: Boolean = false) {
        val playing = r.isPlaying == true
        var target = r.positionSeconds ?: 0.0
        if (playing) {
            val upd = Rooms.parseMs(r.stateUpdatedAt)
            if (upd != null) target += maxOf(0L, Rooms.serverNowMs() - upd) / 1000.0
        }
        val cur = player.currentPosition / 1000.0
        suppress = true
        if (force || abs(cur - target) > 3.5) player.seekTo((target * 1000).toLong())
        if (playing != player.playWhenReady) player.playWhenReady = playing
        viewModelScope.launch { delay(400); suppress = false }
    }

    // ------------------------------------------------------------ consultas periódicas

    private suspend fun pollRoom() {
        if (room == null) return
        val r = try { Rooms.get(roomId) } catch (e: Exception) { return }
        if (r == null) { _state.update { it.copy(closed = true) }; return }

        val meHost = r.hostUserId == userId
        if (meHost != isHost) {
            _state.update { it.copy(room = r, isHost = meHost, notice = if (meHost) "Você agora é o anfitrião da sala." else "${r.hostName} agora é o anfitrião.") }
        } else {
            _state.update { it.copy(room = r) }
        }
        if (keyOf(r) != contentKey && !isHost) { loadContent(r); return }
        if (isHost) return

        // acompanhar o anfitrião
        if (!_state.value.loadingVideo) alignToRoom(r)

        // anfitrião sumiu? (ele grava o estado a cada ~5 s)
        val upd = Rooms.parseMs(r.stateUpdatedAt)
        if (upd != null && Rooms.serverNowMs() - upd > 40_000 && System.currentTimeMillis() - lastHostSeenMs > 40_000) {
            lastHostSeenMs = System.currentTimeMillis()
            val claimed = runCatching { Rooms.claimHostIfAbandoned(roomId, listOf(userId)) }.getOrDefault(false)
            if (claimed) _state.update { it.copy(notice = "O anfitrião saiu — você assumiu a sala.") }
        } else if (upd != null && Rooms.serverNowMs() - upd <= 40_000) {
            lastHostSeenMs = System.currentTimeMillis()
        }
    }

    private suspend fun pollMessages() {
        val cur = _state.value.messages
        try {
            val new = Rooms.messages(roomId, after = cur.lastOrNull()?.createdAt)
            val fresh = if (cur.isEmpty()) new else new.filter { n -> cur.none { it.id == n.id } }
            if (fresh.isNotEmpty() || cur.isEmpty()) {
                val all = if (cur.isEmpty()) fresh else cur + fresh
                val people = (listOfNotNull(room?.let { Person(it.hostUserId, it.hostName) }) + all.map { Person(it.userId, it.displayName) }).distinctBy { it.userId }
                _state.update { it.copy(messages = all, people = people) }
            }
        } catch (e: Exception) { /* tenta de novo no próximo ciclo */ }
    }

    private suspend fun pollQueue() {
        runCatching { Rooms.queue(roomId) }.onSuccess { q -> _state.update { it.copy(queue = q) } }
    }

    // ------------------------------------------------------------ ações

    fun sendMessage(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        viewModelScope.launch {
            runCatching { Rooms.sendMessage(roomId, userId, myName, t) }
                .onFailure { e -> _state.update { it.copy(notice = e.message ?: "Não foi possível enviar") } }
            pollMessages()
        }
    }

    fun addToQueue(tmdbId: Int, type: String, season: Int?, episode: Int?, title: String, poster: String?) {
        viewModelScope.launch {
            runCatching { Rooms.addToQueue(roomId, userId, myName, tmdbId, type, season, episode, title, poster) }
                .onFailure { e -> _state.update { it.copy(notice = e.message ?: "Não foi possível adicionar") } }
            pollQueue()
        }
    }

    fun removeFromQueue(id: String) {
        viewModelScope.launch { runCatching { Rooms.removeFromQueue(id) }; pollQueue() }
    }

    fun playQueueItem(item: QueueItem) {
        viewModelScope.launch {
            try {
                val r = Rooms.playFromQueue(item.id)
                _state.update { it.copy(room = r) }
                loadContent(r)
                pollQueue()
            } catch (e: Exception) {
                _state.update { it.copy(notice = e.message ?: "Não foi possível tocar agora") }
            }
        }
    }

    fun makeHost(p: Person) {
        viewModelScope.launch {
            try {
                Rooms.transferHost(roomId, p.userId)
                _state.update { it.copy(notice = "${p.name} agora é o anfitrião.") }
                pollRoom()
            } catch (e: Exception) {
                _state.update { it.copy(notice = e.message ?: "Não foi possível transferir") }
            }
        }
    }

    fun closeRoom() {
        viewModelScope.launch {
            runCatching { Rooms.close(roomId) }
            _state.update { it.copy(closed = true) }
        }
    }

    // ------------------------------------------------------------ fim do episódio (anfitrião)

    private suspend fun seasonEps(season: Int): List<Episode> =
        seasonCache[season] ?: Tmdb.season(room!!.tmdbId, season).also { seasonCache[season] = it }

    private fun onEnded() {
        val r = room ?: return
        if (r.mediaType != "tv" || r.season == null || r.episode == null) { pushState(); return }
        viewModelScope.launch {
            val next = runCatching {
                val today = LocalDate.now().toString()
                val eps = seasonEps(r.season)
                val inSeason = eps.firstOrNull { it.number > r.episode }
                if (inSeason != null) {
                    val air = inSeason.airDate
                    if (air != null && air > today) null else EpRef(r.season, inSeason.number)
                } else {
                    val m = Tmdb.details("tv", r.tmdbId)
                    val numbers = MediaUtil.seasonsList(m).map { it.seasonNumber }.sorted()
                    val ns = numbers.getOrNull(numbers.indexOf(r.season) + 1)
                    val first = ns?.let { seasonEps(it).firstOrNull() }
                    if (ns != null && first != null) EpRef(ns, first.number) else null
                }
            }.getOrNull()
            if (next == null) { _state.update { it.copy(notice = "Fim da série (ou o próximo episódio ainda não saiu).") }; return@launch }
            val limit = r.seasonLimit
            when {
                limit != null && next.season <= limit -> advance(next)
                limit != null -> _state.update { it.copy(notice = "A maratona configurada vai até a temporada $limit e terminou aqui.") }
                else -> _state.update { it.copy(askNext = next) }
            }
        }
    }

    fun dismissAskNext() { _state.update { it.copy(askNext = null) } }

    fun advance(next: EpRef) {
        val r = room ?: return
        viewModelScope.launch {
            try {
                Rooms.advanceEpisode(r.id, next.season, next.episode)
                val updated = r.copy(season = next.season, episode = next.episode, isPlaying = false, positionSeconds = 0.0)
                _state.update { it.copy(room = updated, askNext = null) }
                loadContent(updated)
                delay(1500)
                player.playWhenReady = true
            } catch (e: Exception) {
                _state.update { it.copy(notice = e.message ?: "Não foi possível avançar") }
            }
        }
    }
}
