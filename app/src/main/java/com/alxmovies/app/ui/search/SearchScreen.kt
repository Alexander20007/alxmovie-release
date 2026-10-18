package com.alxmovies.app.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.MainScaffold
import com.alxmovies.app.ui.channels.ChannelSession
import com.alxmovies.app.ui.channels.ChannelsBrowser
import com.alxmovies.app.ui.channels.ChannelsViewModel
import com.alxmovies.app.ui.components.PosterGrid
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red

@Composable
fun SearchScreen(
    app: AppViewModel,
    onNavigate: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenDetails: (type: String, id: Int) -> Unit,
    onOpenChannel: () -> Unit,
    vm: SearchViewModel = viewModel(),
    channelsVm: ChannelsViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val s by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(profile.id) {
        vm.init(profile)
        channelsVm.start(profile.id)
    }

    val isChannels = vm.type == "channels"

    MainScaffold(
        selected = "search", title = "Buscar", profile = profile, onNavigate = onNavigate,
        onSwitchProfile = onSwitchProfile, onLogout = { app.signOut() },
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = vm.term, onValueChange = { vm.setTerm(it) },
                placeholder = { Text(if (isChannels) "Buscar canal..." else "Digite um título...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (vm.term.isNotEmpty()) IconButton(onClick = { vm.setTerm("") }) { Icon(Icons.Filled.Close, "Limpar") } },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("" to "Filmes e séries", "movie" to "Filmes", "tv" to "Séries", "channels" to "📡 Canais").forEach { (v, label) ->
                    FilterChip(selected = vm.type == v, onClick = { vm.setType(v) }, label = { Text(label) })
                }
                if (!isChannels) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { menu = true }) {
                            Text(s.genres.firstOrNull { it.id == vm.genre }?.name ?: "Todos os gêneros", color = Color.White)
                            Icon(Icons.Filled.ExpandMore, null, tint = Color.White)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Todos os gêneros") }, onClick = { menu = false; vm.setGenre(0) })
                            s.genres.forEach { g -> DropdownMenuItem(text = { Text(g.name) }, onClick = { menu = false; vm.setGenre(g.id) }) }
                        }
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (isChannels) {
                    ChannelsBrowser(channelsVm, externalQuery = vm.term, onOpen = { c, list ->
                        ChannelSession.list = list
                        ChannelSession.index = list.indexOfFirst { it.stream == c.stream }.coerceAtLeast(0)
                        onOpenChannel()
                    })
                } else when {
                    !s.searched -> Message("Digite algo ou escolha um gênero para buscar.")
                    s.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
                    s.error -> Message("Erro ao buscar. Tente novamente.")
                    s.results.isEmpty() -> Message("Nenhum resultado encontrado.")
                    else -> PosterGrid(s.results, onClick = { onOpenDetails(it.type, it.id) }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, color = Muted, textAlign = TextAlign.Center)
    }
}
