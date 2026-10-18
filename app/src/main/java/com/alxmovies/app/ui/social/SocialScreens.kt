package com.alxmovies.app.ui.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.FriendRow
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.clockTime
import com.alxmovies.app.data.formatFriendCode
import com.alxmovies.app.data.isLive
import com.alxmovies.app.data.messagePreview
import com.alxmovies.app.data.parseRoomPayload
import com.alxmovies.app.data.parseTitlePayload
import com.alxmovies.app.data.timeAgo
import com.alxmovies.app.data.watchingLabel
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.parseColor
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.ErrorRed
import com.alxmovies.app.ui.theme.Green
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red

@Composable
private fun SimpleAvatar(name: String, avatar: String?, color: String?, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(parseColor(color)), contentAlignment = Alignment.Center) {
        if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text(name.trim().take(1).uppercase().ifEmpty { "?" }, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
    }
}

@Composable
private fun TopBackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
private fun activeProfile(app: AppViewModel): Profile? {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    return profiles.firstOrNull { it.id == activeId }
}

// ================================================================= AMIGOS

@Composable
fun FriendsScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (friendshipId: String, name: String) -> Unit,
    onWatchAlong: (type: String, id: Int, season: Int?, episode: Int?) -> Unit,
    vm: FriendsViewModel = viewModel(),
) {
    val profile = activeProfile(app) ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

    LaunchedEffect(profile.id) { vm.start(profile.id) }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBackBar("Amigos", onBack)
            when {
                s.loading && s.settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
                s.error != null && s.settings == null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.error ?: "", color = ErrorRed, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { vm.refresh() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Tentar de novo") }
                }
                s.settings?.friendsEnabled != true -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👥 Amizades desligadas neste perfil", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (profile.kids) "Perfis infantis começam com amizades desligadas. Só o responsável pode ligar, em Minha conta › Amigos e privacidade."
                        else "Ligue para adicionar amigos, trocar mensagens e ver o que eles estão assistindo.",
                        color = Muted, textAlign = TextAlign.Center,
                    )
                    if (!profile.kids) {
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { vm.enableFriends() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Ativar amizades") }
                    }
                }
                else -> {
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Amigos (${s.lists.friends.size})") })
                        FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Pedidos (${s.lists.incoming.size + s.lists.outgoing.size})") })
                        FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Adicionar") })
                    }
                    when (tab) {
                        0 -> FriendsTab(s.lists.friends, onOpenChat, onWatchAlong, onRemove = { vm.remove(it) }, onBlock = { vm.block(it) })
                        1 -> RequestsTab(s, vm)
                        else -> AddTab(s, vm)
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FriendsTab(
    friends: List<FriendRow>,
    onOpenChat: (String, String) -> Unit,
    onWatchAlong: (String, Int, Int?, Int?) -> Unit,
    onRemove: (String) -> Unit,
    onBlock: (String) -> Unit,
) {
    var menuFor by remember { mutableStateOf<FriendRow?>(null) }
    if (friends.isEmpty()) {
        Text("Você ainda não tem amigos. Use a aba Adicionar para enviar um pedido pelo código.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(friends, key = { it.friendshipId }) { f ->
            val live = f.isLive()
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable { onOpenChat(f.friendshipId, f.name) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SimpleAvatar(f.name, f.avatar, f.color)
                Column(Modifier.weight(1f)) {
                    Text(f.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (live) Text("🔴 Assistindo: ${f.watchingLabel()}", color = Green, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Text(messagePreview(f.lastMessageKind, f.lastMessage, f.lastMessageMine).ifEmpty { "Toque para conversar" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (live && f.watchingMediaId != null && f.watchingMediaType != null) {
                    TextButton(onClick = { onWatchAlong(f.watchingMediaType, f.watchingMediaId, f.watchingSeason, f.watchingEpisode) }) { Text("Assistir junto", color = Red, fontSize = 12.sp) }
                }
                if ((f.unread ?: 0) > 0) {
                    Box(Modifier.size(22.dp).background(Red, CircleShape), contentAlignment = Alignment.Center) {
                        Text("${f.unread}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Box {
                    Text("⋮", fontSize = 22.sp, color = Muted, modifier = Modifier.clickable { menuFor = f }.padding(horizontal = 6.dp))
                    androidx.compose.material3.DropdownMenu(expanded = menuFor?.friendshipId == f.friendshipId, onDismissRequest = { menuFor = null }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Remover amigo") }, onClick = { menuFor = null; onRemove(f.friendshipId) })
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Bloquear") }, onClick = { menuFor = null; onBlock(f.friendshipId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestsTab(s: FriendsState, vm: FriendsViewModel) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (s.lists.incoming.isEmpty() && s.lists.outgoing.isEmpty() && s.lists.blocked.isEmpty()) {
            item { Text("Nenhum pedido pendente.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp)) }
        }
        if (s.lists.incoming.isNotEmpty()) item { Text("Recebidos", fontWeight = FontWeight.Bold) }
        items(s.lists.incoming, key = { "in-" + it.friendshipId }) { f ->
            RequestRow(f) {
                TextButton(onClick = { vm.respond(f.friendshipId, true) }, enabled = !s.busy) { Text("Aceitar", color = Green, fontWeight = FontWeight.Bold) }
                TextButton(onClick = { vm.respond(f.friendshipId, false) }, enabled = !s.busy) { Text("Recusar", color = Muted) }
            }
        }
        if (s.lists.outgoing.isNotEmpty()) item { Text("Enviados", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(s.lists.outgoing, key = { "out-" + it.friendshipId }) { f ->
            RequestRow(f) { TextButton(onClick = { vm.remove(f.friendshipId) }, enabled = !s.busy) { Text("Cancelar", color = Muted) } }
        }
        if (s.lists.blocked.isNotEmpty()) item { Text("Bloqueados", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
        items(s.lists.blocked, key = { "bl-" + it.friendshipId }) { f ->
            RequestRow(f) { TextButton(onClick = { vm.unblock(f.friendshipId) }, enabled = !s.busy) { Text("Desbloquear", color = Muted) } }
        }
    }
}

@Composable
private fun RequestRow(f: FriendRow, actions: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SimpleAvatar(f.name, f.avatar, f.color, 40)
        Text(f.name, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
private fun AddTab(s: FriendsState, vm: FriendsViewModel) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(14.dp)) {
                Text("Seu código de amigo", color = Muted, fontSize = 12.sp)
                Text(formatFriendCode(s.settings?.friendCode), fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Row {
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Código de amigo", formatFriendCode(s.settings?.friendCode)))
                    }) { Text("Copiar", color = Red, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { vm.regenerate() }) { Text("Gerar outro", color = Muted) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Aparecer nas sugestões", fontSize = 13.sp)
                    Switch(checked = s.settings?.discoverable == true, onCheckedChange = { vm.setDiscoverable(it) })
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(14.dp)) {
                Text("Adicionar pelo código", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code, onValueChange = { if (it.length <= 9) code = it.uppercase() }, placeholder = { Text("XXXX-XXXX") }, singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { vm.sendCode(code.replace("-", "")); code = "" }, enabled = code.replace("-", "").length >= 8 && !s.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Enviar") }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(14.dp)) {
                Text("Sugestões e busca", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it; vm.search(it) }, placeholder = { Text("Buscar pelo nome (2+ letras)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                if (s.suggestionsLoading) CircularProgressIndicator(Modifier.size(22.dp), color = Red, strokeWidth = 2.dp)
                if (!s.suggestionsLoading && s.suggestions.isEmpty()) Text("Nenhuma sugestão agora.", color = Muted, fontSize = 13.sp)
                s.suggestions.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SimpleAvatar(d.name, d.avatar, d.colorTheme, 38)
                        Column(Modifier.weight(1f)) {
                            Text(d.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if ((d.mutualCount ?: 0) > 0) Text("${d.mutualCount} amigo(s) em comum", color = Muted, fontSize = 11.sp)
                        }
                        OutlinedButton(onClick = { vm.requestTo(d.profileId) }, enabled = !s.busy) { Text("Adicionar", color = Color.White, fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

// ================================================================= MENSAGENS (lista de conversas)

@Composable
fun MessagesScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (friendshipId: String, name: String) -> Unit,
    onOpenFriends: () -> Unit,
    vm: FriendsViewModel = viewModel(),
) {
    val profile = activeProfile(app) ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(profile.id) { vm.start(profile.id) }

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        TopBackBar("Mensagens", onBack) { TextButton(onClick = onOpenFriends) { Text("Amigos", color = Red, fontWeight = FontWeight.Bold) } }
        when {
            s.loading && s.settings == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
            s.settings?.friendsEnabled != true -> Text("As amizades estão desligadas neste perfil. Abra Amigos para ativar.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))
            s.lists.friends.isEmpty() -> Text("Você ainda não tem conversas. Adicione amigos primeiro.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp))
            else -> LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.lists.friends, key = { it.friendshipId }) { f ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable { onOpenChat(f.friendshipId, f.name) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SimpleAvatar(f.name, f.avatar, f.color)
                        Column(Modifier.weight(1f)) {
                            Text(f.name, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(messagePreview(f.lastMessageKind, f.lastMessage, f.lastMessageMine).ifEmpty { "Diga oi 👋" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(timeAgo(f.lastMessageAt), color = Muted, fontSize = 11.sp)
                            if ((f.unread ?: 0) > 0) Box(Modifier.padding(top = 4.dp).size(20.dp).background(Red, CircleShape), contentAlignment = Alignment.Center) {
                                Text("${f.unread}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================================================================= CONVERSA

@Composable
fun ChatScreen(
    app: AppViewModel,
    friendshipId: String,
    friendName: String,
    onBack: () -> Unit,
    onOpenTitle: (type: String, id: Int) -> Unit,
    onOpenRoom: (String) -> Unit,
    vm: ChatViewModel = viewModel(),
) {
    val profile = activeProfile(app) ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(profile.id, friendshipId) { vm.start(profile.id, friendshipId) }
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.lastIndex) }
    DisposableBadge(app)

    Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
        TopBackBar(friendName, onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                s.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Red)
                s.messages.isEmpty() -> Text("Nenhuma mensagem ainda. Diga oi 👋", color = Muted, modifier = Modifier.align(Alignment.Center))
                else -> LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(s.messages, key = { it.id }) { m ->
                        val mine = m.senderId == profile.id
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Column(
                                Modifier.widthIn(max = 280.dp).clip(RoundedCornerShape(16.dp))
                                    .background(if (mine) Red else CardBg).padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                when (m.kind) {
                                    "title" -> {
                                        val t = parseTitlePayload(m.payload)
                                        if (t != null) Row(Modifier.clickable { onOpenTitle(t.type, t.id) }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            AsyncImage(model = Config.poster(t.poster, "w154"), contentDescription = null, contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(width = 54.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)))
                                            Column {
                                                Text("🎬 Indicou", fontSize = 11.sp, color = Color(0xCCFFFFFF))
                                                Text(t.title, fontWeight = FontWeight.Bold, maxLines = 2)
                                                if (t.year.isNotEmpty()) Text(t.year, fontSize = 12.sp, color = Color(0xCCFFFFFF))
                                            }
                                        } else Text(m.body ?: "")
                                    }
                                    "room" -> {
                                        val r = parseRoomPayload(m.payload)
                                        if (r != null) Column(Modifier.clickable { onOpenRoom(r.id) }) {
                                            Text("👥 Convite para sala", fontSize = 11.sp, color = Color(0xCCFFFFFF))
                                            Text(r.title, fontWeight = FontWeight.Bold)
                                            if (r.movieTitle.isNotEmpty()) Text(r.movieTitle, fontSize = 12.sp, color = Color(0xCCFFFFFF))
                                            Text("Toque para entrar", fontSize = 11.sp, color = Color.White)
                                        } else Text(m.body ?: "")
                                    }
                                    else -> Text(m.body ?: "", color = Color.White)
                                }
                                Text(clockTime(m.createdAt), fontSize = 10.sp, color = Color(0x99FFFFFF), modifier = Modifier.align(Alignment.End))
                            }
                        }
                    }
                }
            }
        }
        if (s.error != null) Text(s.error ?: "", color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 1000) text = it }, placeholder = { Text("Mensagem...") },
                modifier = Modifier.weight(1f), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
            )
            IconButton(onClick = { vm.send(text); text = "" }, enabled = text.isNotBlank() && !s.sending) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (text.isNotBlank()) Red else Muted)
            }
        }
    }
}

/** Ao sair da conversa, atualiza as bolinhas de mensagens não lidas. */
@Composable
private fun DisposableBadge(app: AppViewModel) {
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { app.refreshBadges() } }
}

// ================================================================= AVISOS

@Composable
fun NotificationsScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenTitle: (type: String, id: Int) -> Unit,
    onOpenFriends: () -> Unit,
    vm: NotificationsViewModel = viewModel(),
) {
    val profile = activeProfile(app) ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(profile.id) { vm.start(profile.id) }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.markAllRead(); app.refreshBadges() } }

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            TopBackBar("Avisos", onBack)
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.loading && s.contentLoading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) } }
                items(s.friend, key = { "f-" + it.id }) { n ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SimpleAvatar(n.fromName, n.fromAvatar, n.fromColor, 40)
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (n.kind == "friend_request") "${n.fromName} quer ser seu amigo" else "${n.fromName} aceitou seu pedido",
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(timeAgo(n.createdAt), color = Muted, fontSize = 11.sp)
                        }
                        if (n.kind == "friend_request") {
                            TextButton(onClick = { vm.respond(n, true) }) { Text("Aceitar", color = Green, fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { vm.respond(n, false) }) { Text("Recusar", color = Muted) }
                        } else TextButton(onClick = onOpenFriends) { Text("Ver", color = Red) }
                    }
                }
                items(s.content, key = { "c-${it.kind}-${it.id}" }) { n ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable { onOpenTitle(n.type, n.id) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(n.icon, fontSize = 22.sp)
                        Text(n.text, Modifier.weight(1f), fontSize = 14.sp)
                    }
                }
                if (!s.loading && !s.contentLoading && s.friend.isEmpty() && s.content.isEmpty()) {
                    item { Text("Nenhum aviso por enquanto. 🎉", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(28.dp)) }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}
