package com.alxmovies.app.ui.channels

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color as AColor
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alxmovies.app.data.Category
import com.alxmovies.app.data.Channel
import com.alxmovies.app.data.Channels
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.Guide
import com.alxmovies.app.data.Program
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.MainScaffold
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.Green
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

private fun titleCase(s: String) = s.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** Categorias → canais no ar, ou resultado de uma busca. Usado na aba Canais e na aba "Canais" da Busca. */
@Composable
fun ChannelsBrowser(
    vm: ChannelsViewModel,
    externalQuery: String?,
    onOpen: (Channel, List<Channel>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(externalQuery) { if (externalQuery != null) vm.setQuery(externalQuery) }

    Column(modifier.fillMaxSize()) {
        val inList = s.group != null || s.query.isNotEmpty()
        if (inList) {
            TextButton(onClick = { vm.back() }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("← Voltar às categorias", color = Muted) }
            if (s.group != null) {
                Text(
                    "${Channels.icon(s.group?.name ?: "")} ${if (s.group?.name == null) "Sem categoria" else titleCase(s.group!!.name!!)}",
                    fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            val status = when {
                s.listLoading -> "Procurando canais..."
                s.done -> "✅ ${s.shown.size} canal(is) online."
                else -> "Testado ${s.tested}/${s.total} — ${s.shown.size} online até agora..."
            }
            Text(status, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (s.done && s.shown.isEmpty()) {
                Text("Nenhum canal online encontrado ainda.", color = Muted, modifier = Modifier.padding(16.dp))
            }
            ChannelGrid(s.shown, vm, onOpen)
        } else if (s.loadingCats) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (s.favorites.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("♥ Meus canais favoritos", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
                            ChannelsRow(s.favorites, vm, onOpen)
                            Spacer(Modifier.height(8.dp))
                            Text("Categorias", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                if (s.cats.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text("Não foi possível carregar as categorias agora.", color = Muted, modifier = Modifier.padding(16.dp))
                    }
                }
                items(s.cats, key = { it.name ?: "none" }) { cat -> CategoryCard(cat) { vm.openGroup(cat) } }
            }
        }
    }
}

@Composable
private fun CategoryCard(cat: Category, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(if (cat.name == null) "🗂️" else Channels.icon(cat.name), fontSize = 26.sp)
        Spacer(Modifier.height(6.dp))
        Text(if (cat.name == null) "Sem categoria" else titleCase(cat.name), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${cat.count} canal${if (cat.count == 1) "" else "is"}", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ChannelsRow(list: List<Channel>, vm: ChannelsViewModel, onOpen: (Channel, List<Channel>) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list.size) { i -> ChannelCard(list[i], vm, Modifier.size(width = 120.dp, height = 130.dp)) { onOpen(list[i], list) } }
    }
}

@Composable
private fun ChannelGrid(list: List<Channel>, vm: ChannelsViewModel, onOpen: (Channel, List<Channel>) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3), contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(list.size, key = { "$it-${list[it].stream}" }) { i ->
            ChannelCard(list[i], vm, Modifier.fillMaxWidth().height(130.dp)) { onOpen(list[i], list) }
        }
    }
}

@Composable
private fun ChannelCard(c: Channel, vm: ChannelsViewModel, modifier: Modifier, onClick: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val fav = s.favorites.any { it.stream == c.stream }
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(CardBg).clickable(onClick = onClick)) {
        Column(Modifier.fillMaxSize().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AsyncImage(
                model = c.logo, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(54.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(c.name ?: "Canal", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        IconButton(onClick = { vm.toggleFavorite(c) }, modifier = Modifier.align(Alignment.TopEnd).size(34.dp)) {
            Icon(
                if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favoritar",
                tint = if (fav) Red else Muted, modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Aba "Canais" da barra de baixo. */
@Composable
fun ChannelsScreen(
    app: AppViewModel,
    onNavigate: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenChannel: () -> Unit,
    vm: ChannelsViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(profile.id) { vm.start(profile.id) }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    MainScaffold(
        selected = "channels", title = "Canais", profile = profile, onNavigate = onNavigate,
        onSwitchProfile = onSwitchProfile, onLogout = { app.signOut() }, snackbarHost = snack,
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = text, onValueChange = { text = it; vm.setQuery(it) },
                placeholder = { Text("Buscar canal...") }, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (text.isNotEmpty()) IconButton(onClick = { text = ""; vm.setQuery("") }) { Icon(Icons.Filled.Close, "Limpar") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            ChannelsBrowser(vm, externalQuery = null, onOpen = { c, list ->
                ChannelSession.list = list
                ChannelSession.index = list.indexOfFirst { it.stream == c.stream }.coerceAtLeast(0)
                onOpenChannel()
            })
        }
    }
}

// ---------------------------------------------------------------- player de canal (página do canal dentro de um WebView)

private fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
private fun StreamWebView(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val activity = ctx.findActivity()
            WebView(ctx).apply {
                setBackgroundColor(AColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()
                webChromeClient = object : WebChromeClient() {
                    private var custom: View? = null
                    private var cb: WebChromeClient.CustomViewCallback? = null
                    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
                        val decor = activity?.window?.decorView as? FrameLayout ?: return
                        custom = view; cb = callback
                        decor.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    }
                    override fun onHideCustomView() {
                        val decor = activity?.window?.decorView as? FrameLayout
                        custom?.let { decor?.removeView(it) }
                        cb?.onCustomViewHidden()
                        custom = null; cb = null
                    }
                }
                tag = url
                loadUrl(url)
            }
        },
        update = { w -> if (w.tag != url) { w.tag = url; w.loadUrl(url) } },
        onRelease = { w -> w.loadUrl("about:blank"); w.destroy() },
    )
}

private fun clock(iso: String?): String? = try {
    if (iso == null) null else OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
} catch (e: Exception) { null }

@Composable
private fun ProgramLine(tag: String, p: Program) {
    val a = clock(p.start); val b = clock(p.end)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(tag, color = if (tag == "AGORA") Red else Muted, fontSize = 10.sp, fontWeight = FontWeight.Black)
        Text("  ${p.title}" + if (a != null && b != null) " ($a–$b)" else "", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
    }
}

@Composable
fun ChannelPlayerScreen(app: AppViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId }

    val list = ChannelSession.list
    var index by remember { mutableStateOf(ChannelSession.index) }
    val channel = list.getOrNull(index)
    var guide by remember { mutableStateOf<Guide?>(null) }
    var favs by remember { mutableStateOf<Set<String>>(emptySet()) }

    // paisagem + tela cheia + tela sempre acesa enquanto assiste
    DisposableEffect(Unit) {
        val act = context.findActivity()
        val win = act?.window
        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        win?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val ctl = win?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            win?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ctl?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(profile?.id) {
        val pid = profile?.id ?: return@LaunchedEffect
        runCatching { Channels.favorites(pid) }.onSuccess { f -> favs = f.map { it.stream }.toSet() }
    }
    LaunchedEffect(channel?.stream) {
        guide = null
        channel?.let { guide = Channels.guide(it.stream) }
    }

    BackHandler { onClose() }
    if (channel == null) { LaunchedEffect(Unit) { onClose() }; return }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
            AsyncImage(model = channel.logo, contentDescription = null, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(channel.name ?: "Canal", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
                val g = guide
                if (g?.now != null) ProgramLine("AGORA", g.now)
                else Text(Channels.groupLabel(channel.group), color = Muted, fontSize = 12.sp, maxLines = 1)
            }
            val fav = channel.stream in favs
            IconButton(onClick = {
                val pid = profile?.id ?: return@IconButton
                scope.launch {
                    runCatching { if (fav) Channels.removeFavorite(pid, channel.stream) else Channels.addFavorite(pid, channel) }
                        .onSuccess { favs = if (fav) favs - channel.stream else favs + channel.stream }
                }
            }) { Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favoritar", tint = if (fav) Red else Color.White) }
            IconButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${channel.name} — ALXmovies\n${Config.SITE_ORIGIN}/home?openChannel=${java.net.URLEncoder.encode(channel.stream, "UTF-8")}")
                }
                context.startActivity(Intent.createChooser(send, "Compartilhar"))
            }) { Icon(Icons.Filled.Share, "Compartilhar", tint = Color.White) }
        }

        StreamWebView(channel.stream, Modifier.weight(1f).fillMaxWidth())

        Row(
            Modifier.fillMaxWidth().background(Color(0xCC000000)),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { index = (index - 1 + list.size) % list.size; ChannelSession.index = index }) { Text("◀ Anterior", color = Color.White) }
            val g = guide
            if (g?.next != null) Box(Modifier.weight(1f).padding(horizontal = 8.dp)) { ProgramLine("A SEGUIR", g.next) }
            else Text("${index + 1} / ${list.size}", color = Muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(onClick = { index = (index + 1) % list.size; ChannelSession.index = index }) { Text("Próximo ▶", color = Color.White) }
        }
    }
}
