package com.alxmovies.app.ui.rooms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.search
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.Green
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.delay

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
fun RoomScreen(app: AppViewModel, roomId: String, onLeave: () -> Unit, vm: RoomViewModel = viewModel()) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId }
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val view = LocalView.current

    var tab by remember { mutableIntStateOf(0) }
    var confirmClose by remember { mutableStateOf(false) }
    var addQueue by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val chatState = rememberLazyListState()

    LaunchedEffect(profile?.id) { profile?.let { vm.start(it, roomId) } }
    LaunchedEffect(s.notice) { s.notice?.let { snack.showSnackbar(it); vm.consumeNotice() } }
    LaunchedEffect(s.messages.size, tab) { if (tab == 0 && s.messages.isNotEmpty()) chatState.scrollToItem(s.messages.lastIndex) }
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    BackHandler { onLeave() }

    val room = s.room
    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize()) {
            // barra
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onLeave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Sair", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(room?.title ?: "Sala", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val ep = if (room?.mediaType == "tv" && room.season != null) " · T${room.season} E${room.episode}" else ""
                    Text((room?.movieTitle ?: "") + ep, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (s.isHost) TextButton(onClick = { confirmClose = true }) { Text("Encerrar", color = Red, fontWeight = FontWeight.Bold) }
            }

            // vídeo
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = vm.player
                            useController = s.isHost
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { it.useController = s.isHost },
                )
                if (s.loadingVideo) Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Red)
                    Text(if (s.slow) "Esse servidor está demorando… tentando outro caminho." else "Preparando o vídeo…", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
                }
                if (s.error != null && !s.loadingVideo) Column(Modifier.align(Alignment.Center).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.error ?: "", color = Color.White, textAlign = TextAlign.Center, fontSize = 13.sp)
                    TextButton(onClick = { vm.retry() }) { Text("Tentar de novo", color = Red, fontWeight = FontWeight.Bold) }
                }
            }
            if (!s.isHost && s.duration > 0) {
                LinearProgressIndicator(progress = { (s.position.toFloat() / s.duration).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Red, trackColor = Color(0x33FFFFFF))
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (s.isHost) "👑 Você é o anfitrião — controla o vídeo para todos" else "👑 ${room?.hostName ?: ""} controla o vídeo · você acompanha",
                    color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2,
                )
                if (s.duration > 0) Text("${fmt(s.position)} / ${fmt(s.duration)}", color = Muted, fontSize = 12.sp)
            }

            // abas
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Chat") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Fila (${s.queue.size})") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Pessoas (${s.people.size})") })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    0 -> Column(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.weight(1f), state = chatState, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (s.messages.isEmpty()) item { Text("Nenhuma mensagem ainda. Comece a conversa!", color = Muted, fontSize = 13.sp) }
                            items(s.messages, key = { it.id }) { m ->
                                Column {
                                    Text(m.displayName, color = Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(m.message, fontSize = 14.sp)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = text, onValueChange = { if (it.length <= 300) text = it }, placeholder = { Text("Mensagem...") },
                                modifier = Modifier.weight(1f), singleLine = true,
                            )
                            IconButton(onClick = { vm.sendMessage(text); text = "" }, enabled = text.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (text.isNotBlank()) Red else Muted)
                            }
                        }
                    }
                    1 -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            OutlinedButton(onClick = { addQueue = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Adicionar à fila", color = Color.White) }
                        }
                        if (s.queue.isEmpty()) item { Text("A fila está vazia.", color = Muted, fontSize = 13.sp) }
                        items(s.queue, key = { it.id }) { q ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                AsyncImage(model = Config.poster(q.posterPath, "w154"), contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(width = 40.dp, height = 60.dp).clip(RoundedCornerShape(6.dp)))
                                Column(Modifier.weight(1f)) {
                                    Text(q.movieTitle, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(
                                        (if (q.mediaType == "tv" && q.season != null) "T${q.season} E${q.episode} · " else "") + "por ${q.addedByName}",
                                        color = Muted, fontSize = 11.sp,
                                    )
                                }
                                if (s.isHost) TextButton(onClick = { vm.playQueueItem(q) }) { Text("▶", color = Red, fontSize = 18.sp) }
                                if (s.isHost || q.addedByUserId == (profile?.userId ?: "")) TextButton(onClick = { vm.removeFromQueue(q.id) }) { Text("✕", color = Muted) }
                            }
                        }
                    }
                    else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(s.people, key = { it.userId }) { p ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text((if (p.userId == room?.hostUserId) "👑 " else "") + p.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                if (s.isHost && p.userId != room?.hostUserId) TextButton(onClick = { vm.makeHost(p) }) { Text("Tornar anfitrião", color = Red, fontSize = 12.sp) }
                            }
                        }
                        item { Text("Mostra o anfitrião e quem já participou do chat.", color = Muted, fontSize = 11.sp) }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Encerrar a sala?") },
            text = { Text("Todo mundo será desconectado.") },
            confirmButton = { TextButton(onClick = { confirmClose = false; vm.closeRoom() }) { Text("Encerrar", color = Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Cancelar", color = Muted) } },
        )
    }
    if (s.closed) {
        AlertDialog(
            onDismissRequest = onLeave,
            title = { Text("Sala encerrada") },
            text = { Text("Esta sala não está mais ativa.") },
            confirmButton = { TextButton(onClick = onLeave) { Text("OK", color = Red, fontWeight = FontWeight.Bold) } },
        )
    }
    s.askNext?.let { n ->
        AlertDialog(
            onDismissRequest = { vm.dismissAskNext() },
            title = { Text("Próximo episódio?") },
            text = { Text("Assistir T${n.season} E${n.episode} agora, com todo mundo?") },
            confirmButton = { TextButton(onClick = { vm.advance(n) }) { Text("Assistir", color = Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { vm.dismissAskNext() }) { Text("Agora não", color = Muted) } },
        )
    }
    if (addQueue) {
        AddQueueDialog(onClose = { addQueue = false }, onAdd = { id, type, se, ep, title, poster -> vm.addToQueue(id, type, se, ep, title, poster); addQueue = false })
    }
}

@Composable
private fun AddQueueDialog(onClose: () -> Unit, onAdd: (Int, String, Int?, Int?, String, String?) -> Unit) {
    var term by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.alxmovies.app.data.MediaItem>>(emptyList()) }
    var picked by remember { mutableStateOf<com.alxmovies.app.data.MediaItem?>(null) }
    var season by remember { mutableStateOf("1") }
    var episode by remember { mutableStateOf("1") }

    LaunchedEffect(term) {
        if (term.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(400)
        results = runCatching { Tmdb.search(term.trim(), "multi").results }.getOrDefault(emptyList())
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }.take(6)
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Adicionar à fila") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val p = picked
                if (p == null) {
                    OutlinedTextField(value = term, onValueChange = { term = it }, placeholder = { Text("Buscar filme ou série") }, singleLine = true)
                    results.forEach { r ->
                        Text(r.displayTitle + if (r.mediaType == "tv") " (série)" else "", modifier = Modifier.fillMaxWidth().clickable { picked = r }.padding(vertical = 6.dp), maxLines = 2)
                    }
                } else {
                    Text("✔ ${p.displayTitle}", fontWeight = FontWeight.Bold)
                    if (p.mediaType == "tv") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = season, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) season = v }, label = { Text("Temp.") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(value = episode, onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 3) episode = v }, label = { Text("Ep.") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = picked != null,
                onClick = {
                    val p = picked ?: return@TextButton
                    val tv = p.mediaType == "tv"
                    onAdd(p.id, p.mediaType ?: "movie", if (tv) season.toIntOrNull() ?: 1 else null, if (tv) episode.toIntOrNull() ?: 1 else null, p.displayTitle, p.posterPath)
                },
            ) { Text("Adicionar", color = Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar", color = Muted) } },
    )
}
