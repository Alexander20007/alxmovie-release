package com.alxmovies.app.ui.details

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.ContentFilter
import com.alxmovies.app.data.MediaDetails
import com.alxmovies.app.data.MediaUtil
import com.alxmovies.app.data.toPoster
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.home.MediaRowView
import com.alxmovies.app.ui.home.RowData
import com.alxmovies.app.ui.theme.Amber
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red

@Composable
fun DetailsScreen(
    app: AppViewModel,
    type: String,
    id: Int,
    onBack: () -> Unit,
    onPlay: (type: String, id: Int, season: Int?, episode: Int?) -> Unit,
    onOpen: (type: String, id: Int) -> Unit,
    vm: DetailsViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId }
    val state by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(profile?.id) { profile?.let { vm.load(it, type, id) } }
    LaunchedEffect(state.message) {
        state.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    val m = state.media
    Box(Modifier.fillMaxSize().background(Bg)) {
        when {
            state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Red)
            state.error || m == null -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Não foi possível carregar este título", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Tente voltar e clicar novamente.", color = Muted)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Voltar") }
            }
            state.blocked -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🔒 Conteúdo bloqueado", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Este título não está disponível para o perfil \"${profile?.name ?: ""}\" por causa da classificação indicativa configurada.",
                    color = Muted, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Voltar") }
            }
            else -> DetailsContent(
                m = m, type = type, state = state, profileKids = profile?.kids == true,
                onBack = onBack, onFav = { vm.toggleFav() }, vm = vm,
                onPlay = onPlay, onOpen = onOpen, onSeason = { vm.selectSeason(it) },
                onShare = {
                    val url = "${Config.SITE_ORIGIN}/details?id=$id&type=$type"
                    val send = Intent(Intent.ACTION_SEND).apply {
                        this.type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${m.displayTitle} — ALXmovies\n$url")
                    }
                    context.startActivity(Intent.createChooser(send, "Compartilhar"))
                },
                profile = profile,
            )
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun DetailsContent(
    m: MediaDetails,
    type: String,
    state: DetailsState,
    profileKids: Boolean,
    profile: com.alxmovies.app.data.Profile?,
    onBack: () -> Unit,
    onFav: () -> Unit,
    onPlay: (String, Int, Int?, Int?) -> Unit,
    onOpen: (String, Int) -> Unit,
    onSeason: (Int) -> Unit,
    onShare: () -> Unit,
    vm: DetailsViewModel,
) {
    val context = LocalContext.current
    var rateOpen by remember { mutableStateOf(false) }
    var commentOpen by remember { mutableStateOf(false) }
    val isTv = type == "tv"
    val year = (m.releaseDate ?: m.firstAirDate ?: "").take(4)
    val runtime = when {
        (m.runtime ?: 0) > 0 -> "${m.runtime} min"
        !m.episodeRunTime.isNullOrEmpty() -> "${m.episodeRunTime.first()} min/ep"
        else -> ""
    }
    val rating = m.voteAverage?.takeIf { it > 0 }?.let { String.format(java.util.Locale.US, "%.1f", it) }
    val cert = MediaUtil.certification(m, type)
    val hero = Config.backdrop(m.backdropPath) ?: Config.poster(m.posterPath)
    val start = state.start
    val playLabel = when {
        isTv -> if (state.last != null && start != null) "Continuar T${start.season}:E${start.episode}" else "Começar do início"
        state.resumeMovie -> "Continuar"
        else -> "Assistir"
    }
    val similar = ContentFilter.filter(m.similar?.results ?: emptyList(), profile).take(14)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
                AsyncImage(model = hero, contentDescription = m.displayTitle, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent, Bg), startY = 0f)
                    )
                )
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    RoundIcon(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
                    RoundIcon(onClick = onFav, enabled = !state.favBusy) {
                        Icon(
                            if (state.fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            if (state.fav) "Remover da minha lista" else "Adicionar à minha lista",
                            tint = if (state.fav) Red else Color.White,
                        )
                    }
                }
                if (cert != null) {
                    Text(
                        if (cert == "L") "L" else cert, color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)
                            .clip(RoundedCornerShape(6.dp)).background(certColor(cert)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(m.displayTitle, fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (rating != null) Text("★ $rating", color = Amber, fontWeight = FontWeight.Bold)
                    if (year.isNotEmpty()) Text(year, color = Muted)
                    if (runtime.isNotEmpty()) Text(runtime, color = Muted)
                    if (isTv) Text("Série", color = Muted)
                }
                val genres = m.genres?.take(4) ?: emptyList()
                if (genres.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genres.forEach { g ->
                            Text(
                                g.name, fontSize = 12.sp, color = Color(0xFFE6E6EC),
                                modifier = Modifier.border(1.dp, Color(0xFF3A3A4A), CircleShape).padding(horizontal = 12.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onPlay(type, m.id, start?.season, start?.episode) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(playLabel, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Share, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Compartilhar", color = Color.White)
                }

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val mine = RATINGS.firstOrNull { it.first == state.rating }
                    OutlinedButton(onClick = { rateOpen = true }, modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp)) {
                        Text(if (mine != null) "${mine.second} ${mine.third}" else "⭐ Avaliar", color = Color.White, maxLines = 1)
                    }
                    if (state.trailerKey != null) {
                        OutlinedButton(
                            onClick = {
                                val key = state.trailerKey
                                val app = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vnd.youtube:$key"))
                                val web = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/watch?v=$key"))
                                try { context.startActivity(app) } catch (e: Exception) { context.startActivity(web) }
                            },
                            modifier = Modifier.weight(1f).height(46.dp), shape = RoundedCornerShape(12.dp),
                        ) { Text("▶ Trailer", color = Color.White, maxLines = 1) }
                    }
                }
                if (!isTv) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { vm.toggleWatched() }, enabled = !state.watchedBusy,
                        modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(12.dp),
                    ) { Text(if (state.watched) "✔ Visto · toque para desmarcar" else "👁 Marcar como visto", color = Color.White) }
                }

                Spacer(Modifier.height(18.dp))
                Synopsis(m.overview?.takeIf { it.isNotBlank() } ?: "Sinopse não disponível para este título.")
            }
        }

        if (isTv) {
            item {
                val seasons = MediaUtil.seasonsList(m)
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Episódios", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box {
                        OutlinedButton(onClick = { menu = true }, shape = RoundedCornerShape(10.dp)) {
                            Text(MediaUtil.seasonLabel(state.season), color = Color.White)
                            Icon(Icons.Filled.ExpandMore, null, tint = Color.White)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            seasons.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(MediaUtil.seasonLabel(s.seasonNumber)) },
                                    onClick = { menu = false; onSeason(s.seasonNumber) },
                                )
                            }
                        }
                    }
                }
            }
            if (state.episodesLoading) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) } }
            }
            items(state.episodes, key = { "ep-${state.season}-${it.number}" }) { ep ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onPlay(type, m.id, state.season, ep.number) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(CardBg)) {
                        val still = Config.backdrop(ep.still, "w300")
                        if (still != null) AsyncImage(model = still, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("${ep.number}. ${ep.name ?: "Episódio ${ep.number}"}", fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val rt = ep.runtime
                        if (rt != null && rt > 0) Text("$rt min", color = Muted, fontSize = 12.sp)
                        if (!ep.overview.isNullOrBlank()) {
                            Text(ep.overview, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (similar.isNotEmpty()) {
            item {
                MediaRowView(
                    RowData(0, "Títulos parecidos", similar.map { it.toPoster(type) }),
                    onClick = { onOpen(it.type, it.id) },
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Comentários", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val sum = state.summary
                    Text(
                        if (sum != null && sum.total > 0) "👍 ${sum.liked}  ❤️ ${sum.loved}  👎 ${sum.disliked}" else "Notas e opiniões de quem já assistiu",
                        color = Muted, fontSize = 12.sp,
                    )
                }
                OutlinedButton(onClick = { commentOpen = true }, shape = RoundedCornerShape(10.dp)) { Text("💬 Comentar", color = Color.White) }
            }
        }
        if (state.commentsLoading) {
            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) } }
        } else if (state.comments.isEmpty()) {
            item { Text("Seja o primeiro a comentar.", color = Muted, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }
        val tops = state.comments.filter { it.parentId == null }
        items(tops, key = { "c-${it.id}" }) { c ->
            CommentItem(c, state, profile?.id, vm, replies = state.comments.filter { it.parentId == c.id }.reversed())
        }
    }

    if (rateOpen) RateSheet(current = state.rating, summary = state.summary, onPick = { vm.setRating(it); rateOpen = false }, onClose = { rateOpen = false })
    if (commentOpen) CommentDialog(busy = state.commentBusy, onSubmit = { vm.addComment(it); commentOpen = false }, onClose = { commentOpen = false })
}

private val RATINGS = listOf(
    Triple("disliked", "👎", "Não gostei"),
    Triple("liked", "👍", "Gostei"),
    Triple("loved", "❤️", "Amei"),
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun RateSheet(current: String?, summary: com.alxmovies.app.data.RatingSummary?, onPick: (String) -> Unit, onClose: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, containerColor = CardBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("O que você achou?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RATINGS.forEach { (v, icon, title) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (current == v) Color(0x33E50914) else Color(0xFF1B1B26))
                            .border(if (current == v) 2.dp else 0.dp, Red, RoundedCornerShape(14.dp))
                            .clickable { onPick(v) }.padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(icon, fontSize = 28.sp)
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (summary != null) Text(
                if (summary.total == 0) "Ainda sem avaliações de outros usuários." else "👍 ${summary.liked}   ❤️ ${summary.loved}   👎 ${summary.disliked}",
                color = Muted, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CommentDialog(busy: Boolean, onSubmit: (String) -> Unit, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Comentar") },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 600) text = it },
                placeholder = { Text("Escreva sua opinião...") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(text) }, enabled = text.isNotBlank() && !busy) { Text("Publicar", color = Red, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar", color = Muted) } },
    )
}

@Composable
private fun CommentItem(c: com.alxmovies.app.data.CommentRow, state: DetailsState, myId: String?, vm: DetailsViewModel, replies: List<com.alxmovies.app.data.CommentRow>) {
    var replyOpen by remember { mutableStateOf(false) }
    var reply by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        CommentBody(c, state, myId, vm, onReply = { replyOpen = !replyOpen })
        replies.forEach { r ->
            Column(Modifier.padding(start = 24.dp, top = 6.dp)) { CommentBody(r, state, myId, vm, onReply = null) }
        }
        if (replyOpen) {
            Row(Modifier.padding(start = 24.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = reply, onValueChange = { if (it.length <= 600) reply = it },
                    placeholder = { Text("Responder...") }, modifier = Modifier.weight(1f), singleLine = true,
                )
                TextButton(onClick = { vm.addComment(reply, c.id); reply = ""; replyOpen = false }, enabled = reply.isNotBlank()) {
                    Text("Enviar", color = Red, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CommentBody(c: com.alxmovies.app.data.CommentRow, state: DetailsState, myId: String?, vm: DetailsViewModel, onReply: (() -> Unit)?) {
    val like = state.likes[c.id] ?: com.alxmovies.app.data.LikeInfo()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CardBg).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0xFF333344)), contentAlignment = Alignment.Center) {
            val av = c.profiles?.avatar
            if (!av.isNullOrBlank()) AsyncImage(model = av, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text((c.profiles?.name ?: "?").take(1).uppercase(), fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(c.profiles?.name ?: "Usuário", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(c.body, fontSize = 14.sp, color = Color(0xFFE6E6EC))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { vm.toggleLike(c.id) }) {
                    Text((if (like.byMe) "❤️" else "🤍") + " ${like.count}", color = Muted, fontSize = 12.sp)
                }
                if (onReply != null) TextButton(onClick = onReply) { Text("Responder", color = Muted, fontSize = 12.sp) }
                if (myId != null && c.profileId == myId) TextButton(onClick = { vm.deleteComment(c.id) }) { Text("Apagar", color = Muted, fontSize = 12.sp) }
            }
        }
    }
}

private fun certColor(c: String): Color = when (c) {
    "L" -> Color(0xFF2E9E4A)
    "10" -> Color(0xFF1F7AE0)
    "12" -> Color(0xFFE0A11C)
    "14" -> Color(0xFFE07A1C)
    "16" -> Color(0xFFE02E2E)
    else -> Color(0xFF111111)
}

@Composable
private fun RoundIcon(onClick: () -> Unit, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(Color(0x66000000)).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Synopsis(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(14.dp)) {
        Text(
            text, color = Color(0xFFE6E6EC), lineHeight = 21.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
        )
        if (text.length > 180) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Mostrar menos" else "Mostrar mais", color = Red, fontWeight = FontWeight.Bold)
            }
        }
    }
}
