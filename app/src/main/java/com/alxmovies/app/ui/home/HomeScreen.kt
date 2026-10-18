package com.alxmovies.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.MediaItem
import com.alxmovies.app.data.PosterItem
import com.alxmovies.app.data.Profile
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.MainScaffold
import com.alxmovies.app.ui.ProfileAvatar
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SOON = "Essa parte chega nas próximas etapas do app nativo."

@Composable
fun HomeScreen(
    app: AppViewModel,
    onNavigate: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenDetails: (type: String, id: Int) -> Unit,
    onPlay: (type: String, id: Int) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val loaded by app.loaded.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId }

    LaunchedEffect(Unit) { if (profiles.isEmpty()) app.loadProfiles() }
    LaunchedEffect(profile, loaded) { if (loaded && profile == null) onSwitchProfile() }
    LaunchedEffect(profile?.id) { profile?.let { vm.load(it) } }

    if (profile == null) {
        Box(Modifier.fillMaxSize().background(Bg), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
        return
    }

    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val solid by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 250 } }

    MainScaffold(
        selected = "home", profile = profile, onNavigate = onNavigate,
        onSwitchProfile = onSwitchProfile, onLogout = { app.signOut() },
        overlay = true, solid = solid,
    ) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (state.heroes.isNotEmpty()) {
                item(key = "hero") {
                    Hero(
                        state.heroes,
                        onOpen = { onOpenDetails("movie", it.id) },
                        onPlay = { onPlay("movie", it.id) },
                    )
                }
            } else {
                item(key = "hero-space") { Spacer(Modifier.statusBarsPadding().height(72.dp)) }
            }

            if (state.loading && state.rows.isEmpty()) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
                }
            }
            if (!state.loading && state.failed) {
                item(key = "failed") {
                    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Não foi possível carregar o catálogo. Verifique sua internet.", color = Muted, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.load(profile, force = true) }, colors = ButtonDefaults.buttonColors(containerColor = Red)) {
                            Text("Tentar de novo")
                        }
                    }
                }
            }

            items(state.rows, key = { it.order }) { row ->
                MediaRowView(row, onClick = { p ->
                    if (p.progress != null) onPlay(p.type, p.id) else onOpenDetails(p.type, p.id)
                })
            }
        }
    }
}

@Composable
private fun Hero(items: List<MediaItem>, onOpen: (MediaItem) -> Unit, onPlay: (MediaItem) -> Unit) {
    val pager = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(pager.settledPage, items.size) {
        if (items.size > 1) {
            delay(5500)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.settledPage + 1) % items.size)
        }
    }

    Box(Modifier.fillMaxWidth().aspectRatio(0.8f)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val m = items[page]
            Box(Modifier.fillMaxSize().clickable { onOpen(m) }) {
                AsyncImage(
                    model = Config.backdrop(m.backdropPath),
                    contentDescription = m.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x33000000), Bg),
                            startY = 200f,
                        )
                    )
                )
                Column(Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 36.dp)) {
                    Text("🔥 Em alta hoje", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        m.displayTitle, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier.size(54.dp).clip(CircleShape).background(Red).clickable { onPlay(m) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Assistir", tint = Color.White, modifier = Modifier.size(32.dp)) }
                }
            }
        }
        if (items.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(items.size) { i ->
                    Box(
                        Modifier.size(if (i == pager.currentPage) 18.dp else 6.dp, 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i == pager.currentPage) Color.White else Color(0x66FFFFFF))
                    )
                }
            }
        }
    }
}

@Composable
fun MediaRowView(row: RowData, onClick: (PosterItem) -> Unit) {
    Column(Modifier.padding(top = 20.dp)) {
        Text(
            row.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(row.items, key = { i, p -> "${p.type}-${p.id}-$i" }) { i, p ->
                PosterCard(p, rank = if (row.ranked) i + 1 else null, onClick = { onClick(p) })
            }
        }
    }
}

@Composable
fun PosterCard(item: PosterItem, rank: Int?, onClick: () -> Unit, modifier: Modifier = Modifier.width(118.dp)) {
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(CardBg).clickable(onClick = onClick),
    ) {
        val url = Config.poster(item.poster)
        if (url != null) {
            AsyncImage(model = url, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                item.title, color = Muted, textAlign = TextAlign.Center, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
        }
        if (rank != null) {
            Text(
                "$rank", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 4.dp),
            )
        }
        if (item.progress != null && item.progress > 0) {
            LinearProgressIndicator(
                progress = { item.progress / 100f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                color = Red, trackColor = Color(0x66000000),
            )
        }
    }
}
