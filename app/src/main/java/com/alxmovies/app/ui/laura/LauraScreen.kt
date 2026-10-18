package com.alxmovies.app.ui.laura

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alxmovies.app.data.Assistant
import com.alxmovies.app.data.Channel
import com.alxmovies.app.data.Channels
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.ContentFilter
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.Repo
import com.alxmovies.app.data.RoomRow
import com.alxmovies.app.data.Rooms
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.details
import com.alxmovies.app.data.knownOnline
import com.alxmovies.app.data.search
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.channels.ChannelSession
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.ErrorRed
import com.alxmovies.app.ui.theme.Green
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovieMatch(
    val id: Int, val type: String, val name: String, val poster: String?, val year: String, val overview: String,
    val vote: Double, val voteCount: Int, val genre: String, val duration: String,
)

sealed interface LMsg { val key: Int }
data class LText(override val key: Int, val who: String, val text: String) : LMsg
data class LMovies(override val key: Int, val items: List<MovieMatch>) : LMsg
data class LChannels(override val key: Int, val items: List<Channel>) : LMsg
data class LRooms(override val key: Int, val items: List<RoomRow>) : LMsg

data class LauraState(val messages: List<LMsg> = emptyList(), val busy: Boolean = false)

class LauraViewModel : ViewModel() {
    private val _state = MutableStateFlow(LauraState())
    val state: StateFlow<LauraState> = _state.asStateFlow()
    private var next = 1

    private fun push(build: (Int) -> LMsg): Int {
        val k = next++
        _state.update { it.copy(messages = it.messages + build(k)) }
        return k
    }
    private fun drop(k: Int) = _state.update { s -> s.copy(messages = s.messages.filterNot { it.key == k }) }

    fun reset() { if (!_state.value.busy) _state.update { it.copy(messages = emptyList()) } }

    private suspend fun findOnSite(titles: List<Pair<String, String>>, profile: Profile): List<MovieMatch> {
        val seen = mutableSetOf<String>()
        return kotlinx.coroutines.coroutineScope {
            titles.take(4).map { (title, _) ->
                async {
                    runCatching {
                        val hit = Tmdb.search(title, "multi").results.firstOrNull { it.mediaType == "movie" || it.mediaType == "tv" } ?: return@runCatching null
                        if (!ContentFilter.allowed(hit, profile)) return@runCatching null
                        val type = hit.mediaType ?: "movie"
                        var m = MovieMatch(
                            hit.id, type, hit.displayTitle, hit.posterPath, (hit.releaseDate ?: hit.firstAirDate ?: "").take(4),
                            hit.overview ?: "", hit.voteAverage ?: 0.0, 0, "", "",
                        )
                        runCatching {
                            val full = Tmdb.details(type, m.id)
                            val mins = (full.runtime ?: 0).takeIf { it > 0 } ?: full.episodeRunTime?.firstOrNull() ?: 0
                            val dur = if (mins > 0) (if (mins >= 60) "${mins / 60}h ${if (mins % 60 > 0) "${mins % 60}min" else ""}".trim() else "${mins}min") else ""
                            m = m.copy(genre = full.genres?.firstOrNull()?.name ?: "", overview = full.overview?.takeIf { it.isNotBlank() } ?: m.overview, duration = dur)
                        }
                        m
                    }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull().filter { seen.add("${it.type}-${it.id}") }
    }

    private suspend fun history(profile: Profile): List<Pair<String, String>> {
        val watched = runCatching { Repo.fetchWatched(profile.id, 15) }.getOrDefault(emptyList()).map { Triple(it.mediaType, it.mediaId, it.title) }
        val favs = runCatching { Repo.fetchMyList(profile.id) }.getOrDefault(emptyList()).map { Triple(it.mediaType, it.mediaId, it.title) }
        val seen = mutableSetOf<String>()
        return (watched + favs).filter { !it.third.isNullOrEmpty() && seen.add("${it.first}-${it.second}") }
            .map { (it.third ?: "") to it.first }.take(15)
    }

    fun ask(raw: String, profile: Profile) {
        val q = raw.trim()
        if (q.isEmpty() || _state.value.busy) return
        push { LText(it, "user", q) }
        _state.update { it.copy(busy = true) }
        val loading = push { LText(it, "loading", "Pensando...") }
        viewModelScope.launch {
            try {
                val wantsChannels = Regex("\\bcanai?s?\\b|\\btv\\s*(ao vivo|aberta)?\\b|\\bassistir ao vivo\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
                val onlineChannels = if (wantsChannels) Channels.knownOnline(20) else emptyList()
                val wantsRooms = Regex("\\bsalas?\\b|\\bassistir\\s+junt[oa]\\b|\\bwatch\\s*party\\b", RegexOption.IGNORE_CASE).containsMatchIn(q)
                val onlineRooms = if (wantsRooms) runCatching { Rooms.listPublic() }.getOrDefault(emptyList()) else emptyList()

                val a = Assistant.ask(q, onlineChannels, runCatching { history(profile) }.getOrDefault(emptyList()), onlineRooms)
                drop(loading)
                push { LText(it, "bot", a.reply) }

                if (a.titles.isNotEmpty()) {
                    val searching = push { LText(it, "loading", "Procurando no catálogo...") }
                    val matches = findOnSite(a.titles.map { it.title to it.type }, profile)
                    drop(searching)
                    if (matches.isNotEmpty()) push { LMovies(it, matches) }
                }
                val chCh = onlineChannels.filter { c -> a.channels.any { it.equals(c.name ?: "", ignoreCase = true) } }
                if (chCh.isNotEmpty()) push { LChannels(it, chCh) }
                val chRooms = onlineRooms.filter { r -> a.rooms.any { it.equals(r.title, ignoreCase = true) } }
                if (chRooms.isNotEmpty()) push { LRooms(it, chRooms) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                drop(loading)
                push { LText(it, "error", e.message ?: "Não consegui responder agora. Tente de novo.") }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }
}

private val SUGGESTIONS = listOf("🎬 Um suspense parecido com Se7en", "😄 Comédia leve pra hoje à noite", "📺 Séries curtas pra maratonar", "⭐ Filmes de ação com nota alta")

private fun aiText(text: String): AnnotatedString = buildAnnotatedString {
    text.split("\n").forEachIndexed { i, line ->
        if (i > 0) append("\n")
        line.split(Regex("\\*\\*(.+?)\\*\\*")).let { _ ->
            var last = 0
            for (m in Regex("\\*\\*(.+?)\\*\\*").findAll(line)) {
                append(line.substring(last, m.range.first))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
                last = m.range.last + 1
            }
            append(line.substring(last))
        }
    }
}

@Composable
private fun Orb(size: Int = 30) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFF8E2EEA)))),
        contentAlignment = Alignment.Center,
    ) { Text("✨", fontSize = (size * 0.5f).sp) }
}

@Composable
fun LauraScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenDetails: (type: String, id: Int) -> Unit,
    onPlay: (type: String, id: Int) -> Unit,
    onOpenChannel: () -> Unit,
    onGoRooms: () -> Unit,
    vm: LauraViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.lastIndex) }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
            Orb()
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Laura IA", fontWeight = FontWeight.Bold)
                Text("● Online", color = Green, fontSize = 11.sp)
            }
            if (s.messages.isNotEmpty()) IconButton(onClick = { vm.reset() }, enabled = !s.busy) { Icon(Icons.Filled.Refresh, "Nova conversa", tint = Color.White) }
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (s.messages.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Orb(64)
                    Spacer(Modifier.height(12.dp))
                    val first = profile.name.trim().split(Regex("\\s+")).firstOrNull() ?: ""
                    Text("Oi${if (first.isNotEmpty()) ", $first" else ""}! Eu sou a Laura", fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Text("Me diga o que você quer assistir ou peça uma recomendação — eu procuro no catálogo do ALXmovies pra você.", color = Muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    SUGGESTIONS.forEach { sug ->
                        OutlinedButton(
                            onClick = { vm.ask(sug.substringAfter(" "), profile) }, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(sug, color = Color.White) }
                    }
                }
            }
            items(s.messages, key = { it.key }) { m ->
                when (m) {
                    is LText -> when (m.who) {
                        "user" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Text(aiText(m.text), Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(Red).padding(12.dp), color = Color.White)
                        }
                        "loading" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Orb(26); Text("● ● ●", color = Muted, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(CardBg).padding(horizontal = 14.dp, vertical = 8.dp))
                        }
                        else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Orb(26)
                            Text(aiText(m.text), Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp)).background(CardBg).padding(12.dp),
                                color = if (m.who == "error") ErrorRed else Color.White)
                        }
                    }
                    is LMovies -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { m.items.forEach { MovieCardView(it, onOpenDetails, onPlay) } }
                    is LChannels -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        m.items.forEach { c ->
                            Column(
                                Modifier.size(width = 110.dp, height = 110.dp).clip(RoundedCornerShape(12.dp)).background(CardBg)
                                    .clickable { ChannelSession.list = m.items; ChannelSession.index = m.items.indexOf(c).coerceAtLeast(0); onOpenChannel() }.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                AsyncImage(model = c.logo, contentDescription = null, modifier = Modifier.size(46.dp))
                                Text(c.name ?: "Canal", fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    is LRooms -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        m.items.forEach { r ->
                            Column(
                                Modifier.size(width = 110.dp, height = 190.dp).clip(RoundedCornerShape(12.dp)).background(CardBg).clickable { onGoRooms() }.padding(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AsyncImage(model = Config.poster(r.posterPath, "w200"), contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(8.dp)))
                                Text(r.title + if (r.hasPassword == true) " 🔒" else "", fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, placeholder = { Text("Pergunte algo sobre filmes e séries...") },
                modifier = Modifier.weight(1f), singleLine = true, enabled = !s.busy,
            )
            IconButton(onClick = { vm.ask(input, profile); input = "" }, enabled = !s.busy && input.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (!s.busy && input.isNotBlank()) Red else Muted)
            }
        }
    }
}

@Composable
private fun MovieCardView(m: MovieMatch, onOpenDetails: (String, Int) -> Unit, onPlay: (String, Int) -> Unit) {
    val meta = listOf(m.year, m.genre, m.duration).filter { it.isNotEmpty() }.joinToString(" • ")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(
            model = Config.poster(m.poster, "w185"), contentDescription = m.name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 84.dp, height = 126.dp).clip(RoundedCornerShape(8.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(m.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, color = Muted, fontSize = 12.sp)
            if (m.vote > 0) Text("★ ${String.format(java.util.Locale.US, "%.1f", m.vote)}", color = Color(0xFFE0A11C), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (m.overview.isNotEmpty()) Text(m.overview, color = Color(0xFFD0D0DA), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onPlay(m.type, m.id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                ) { Text("▶ Assistir", fontSize = 12.sp) }
                OutlinedButton(onClick = { onOpenDetails(m.type, m.id) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    Text("Detalhes", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}
