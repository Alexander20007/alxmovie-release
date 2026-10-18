package com.alxmovies.app.ui.rooms

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.CreateRoomInput
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.RoomRow
import com.alxmovies.app.data.Rooms
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.search
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.MainScaffold
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.ErrorRed
import com.alxmovies.app.ui.theme.Green
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RoomsState(
    val loading: Boolean = true,
    val error: Boolean = false,
    val all: List<RoomRow> = emptyList(),
    val recent: List<RoomRow> = emptyList(),
)

class RoomsViewModel : ViewModel() {
    private val _state = MutableStateFlow(RoomsState())
    val state: StateFlow<RoomsState> = _state.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            while (true) { load(); delay(15_000) }
        }
    }

    suspend fun load() {
        try {
            val a = Rooms.listPublic()
            val r = runCatching { Rooms.listMyRecent(12) }.getOrDefault(emptyList())
            _state.update { it.copy(loading = false, error = false, all = a, recent = r) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = true) }
        }
    }
}

private val TABS = listOf("recentes" to "Recentes", "todas" to "Todas", "publicas" to "Públicas", "series" to "Séries", "filmes" to "Filmes")
private val EMPTY = mapOf(
    "recentes" to "Você ainda não entrou em nenhuma sala.",
    "todas" to "Nenhuma sala ativa agora. Crie a primeira!",
    "publicas" to "Nenhuma sala pública no momento.",
    "series" to "Nenhuma sala de série no momento.",
    "filmes" to "Nenhuma sala de filme no momento.",
)

@Composable
fun RoomsScreen(
    app: AppViewModel,
    onNavigate: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onEnterRoom: (String) -> Unit,
    vm: RoomsViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var chosen by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pwRoom by remember { mutableStateOf<RoomRow?>(null) }
    var pwError by remember { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.start() }

    val tab = chosen ?: if (s.loading) null else if (s.recent.isNotEmpty()) "recentes" else "todas"
    val list: List<RoomRow> = when (tab) {
        "recentes" -> s.recent
        "todas" -> s.all
        "publicas" -> s.all.filter { it.isPrivate != true }
        "series" -> s.all.filter { it.mediaType == "tv" }
        "filmes" -> s.all.filter { it.mediaType == "movie" }
        else -> emptyList()
    }

    val name = profile.name
    fun enter(room: RoomRow, password: String?) {
        joining = true
        scope.launch {
            try {
                Rooms.join(room.id, password, name)
                pwRoom = null
                onEnterRoom(room.id)
            } catch (e: Exception) {
                val msg = e.message ?: ""
                val wrongPw = Regex("senha", RegexOption.IGNORE_CASE).containsMatchIn(msg)
                if (wrongPw && password == null) { pwError = ""; pwRoom = room }
                else if (wrongPw) pwError = "Senha incorreta. Confira e tente de novo."
                else if (pwRoom != null) pwError = msg.ifEmpty { "Não foi possível entrar na sala." }
                else snack.showSnackbar(msg.ifEmpty { "Não foi possível entrar na sala." })
            } finally {
                joining = false
            }
        }
    }

    MainScaffold(
        selected = "salas", title = "Salas", profile = profile, onNavigate = onNavigate,
        onSwitchProfile = onSwitchProfile, onLogout = { app.signOut() }, snackbarHost = snack,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Assista junto com quem você quiser, com chat ao vivo.", Modifier.weight(1f), color = Muted, fontSize = 13.sp)
                Button(onClick = { creating = true }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("+ Criar sala") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TABS.forEach { (id, label) ->
                    val count = when (id) { "recentes" -> s.recent.size; "todas" -> s.all.size; "publicas" -> s.all.count { it.isPrivate != true }
                        "series" -> s.all.count { it.mediaType == "tv" }; else -> s.all.count { it.mediaType == "movie" } }
                    FilterChip(selected = tab == id, onClick = { chosen = id }, label = { Text(if (count > 0) "$label ($count)" else label) })
                }
            }
            when {
                tab == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
                s.error && list.isEmpty() -> Text("Não foi possível carregar as salas.", color = ErrorRed, modifier = Modifier.padding(24.dp))
                list.isEmpty() -> Text(EMPTY[tab] ?: "", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { r ->
                        RoomCard(r) {
                            if (r.hasPassword == true && tab != "recentes") { pwError = ""; pwRoom = r } else enter(r, null)
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        CreateRoomDialog(profile = profile, onClose = { creating = false }, onCreated = { creating = false; onEnterRoom(it) })
    }
    pwRoom?.let { r ->
        var pw by remember(r.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!joining) pwRoom = null },
            title = { Text("🔒 Sala com senha") },
            text = {
                Column {
                    Text(r.title, color = Muted)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pw, onValueChange = { pw = it; pwError = "" }, label = { Text("Senha") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (pwError.isNotEmpty()) Text(pwError, color = ErrorRed, modifier = Modifier.padding(top = 6.dp))
                }
            },
            confirmButton = { TextButton(onClick = { if (pw.isNotEmpty()) enter(r, pw) }, enabled = pw.isNotEmpty() && !joining) { Text("Entrar", color = Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { pwRoom = null }) { Text("Cancelar", color = Muted) } },
        )
    }
}

@Composable
private fun RoomCard(room: RoomRow, onClick: () -> Unit) {
    val ep = if (room.mediaType == "tv" && room.season != null) " · T${room.season} E${room.episode}" else ""
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable(onClick = onClick).padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(width = 70.dp, height = 104.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B1B26))) {
            AsyncImage(model = Config.poster(room.posterPath, "w300"), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f)) {
            Text(room.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(room.movieTitle + ep, color = Color(0xFFE6E6EC), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("com ${room.hostName}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (room.isPlaying == true) "🔴 Ao vivo" else "⏸ Pausada", color = if (room.isPlaying == true) Green else Muted, fontSize = 12.sp)
                Text(if (room.isPrivate == true) "🔒 Com senha" else "🌐 Aberta", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

private data class Picked(val id: Int, val type: String, val title: String, val poster: String?)

@Composable
private fun CreateRoomDialog(profile: Profile, onClose: () -> Unit, onCreated: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var term by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Picked?>(null) }
    var results by remember { mutableStateOf<List<Picked>>(emptyList()) }
    var season by remember { mutableStateOf("1") }
    var episode by remember { mutableStateOf("1") }
    var limit by remember { mutableStateOf("") }
    var priv by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(term) {
        if (term.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        results = runCatching { Tmdb.search(term.trim(), "multi").results }.getOrDefault(emptyList())
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }.take(8)
            .map { Picked(it.id, it.mediaType ?: "movie", it.displayTitle, it.posterPath) }
    }

    fun submit() {
        error = ""
        val p = picked
        if (title.trim().isEmpty()) { error = "Dê um nome pra sala."; return }
        if (p == null) { error = "Escolha um filme ou série."; return }
        if (priv && password.isEmpty()) { error = "Defina uma senha ou desmarque 'sala privada'."; return }
        val isTv = p.type == "tv"
        busy = true
        scope.launch {
            try {
                val room = Rooms.create(
                    CreateRoomInput(
                        title = title.trim(), isPrivate = priv, password = password.ifEmpty { null }, tmdbId = p.id, mediaType = p.type,
                        season = if (isTv) season.toIntOrNull() ?: 1 else null, episode = if (isTv) episode.toIntOrNull() ?: 1 else null,
                        movieTitle = p.title, posterPath = p.poster, hostProfileId = profile.id, hostName = profile.name,
                        seasonLimit = if (isTv) limit.toIntOrNull() else null,
                    )
                )
                onCreated(room.id)
            } catch (e: Exception) {
                error = e.message ?: "Não foi possível criar a sala."
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose, enabled = !busy) { Text("Cancelar", color = Muted) }
                Text("Criar sala", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
                TextButton(onClick = { if (!busy) submit() }, enabled = !busy) { Text(if (busy) "Criando…" else "Criar e entrar", color = Red, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error.isNotEmpty()) Text(error, color = ErrorRed)
                OutlinedTextField(value = title, onValueChange = { if (it.length <= 60) title = it }, label = { Text("Nome da sala") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = term, onValueChange = { term = it }, label = { Text("Filme ou série") }, placeholder = { Text("Digite o nome...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                results.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardBg)
                            .clickable { picked = r; term = ""; results = emptyList(); if (title.isBlank()) title = "Assistindo ${r.title}" }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AsyncImage(model = Config.poster(r.poster, "w154"), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 36.dp, height = 54.dp).clip(RoundedCornerShape(6.dp)))
                        Text(r.title + if (r.type == "tv") " (série)" else "", maxLines = 2)
                    }
                }
                picked?.let { p ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x33E50914)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(model = Config.poster(p.poster, "w154"), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(width = 36.dp, height = 54.dp).clip(RoundedCornerShape(6.dp)))
                        Text("✔ ${p.title}", fontWeight = FontWeight.Bold)
                    }
                    if (p.type == "tv") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = season, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) season = v }, label = { Text("Temporada") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                            OutlinedTextField(value = episode, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) episode = v }, label = { Text("Episódio") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(value = limit, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) limit = v },
                            label = { Text("Maratona até a temporada nº (opcional)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Sala privada (com senha)")
                    Switch(checked = priv, onCheckedChange = { priv = it })
                }
                if (priv) OutlinedTextField(value = password, onValueChange = { if (it.length <= 40) password = it }, label = { Text("Senha da sala") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
