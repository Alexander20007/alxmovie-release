package com.alxmovies.app.ui.mylist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.MainScaffold
import com.alxmovies.app.ui.components.PosterGrid
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.ErrorRed
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red

private val TABS = listOf(
    "Favoritos" to "Sua lista de favoritos está vazia. Adicione filmes e séries tocando no coração nos detalhes.",
    "Continuar assistindo" to "Você ainda não começou a assistir nada.",
    "Já assistidos" to "Nenhum título totalmente assistido ainda.",
)

@Composable
fun MyListScreen(
    app: AppViewModel,
    onNavigate: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenDetails: (type: String, id: Int) -> Unit,
    vm: MyListViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val s by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(profile.id) { vm.start(profile.id) }

    MainScaffold(
        selected = "mylist", title = "Minha lista", profile = profile, onNavigate = onNavigate,
        onSwitchProfile = onSwitchProfile, onLogout = { app.signOut() },
    ) {
        val items = s.items[s.tab]
        PosterGrid(
            items = items ?: emptyList(),
            onClick = { onOpenDetails(it.type, it.id) },
            modifier = Modifier.fillMaxSize(),
            header = {
                Column {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TABS.forEachIndexed { i, t ->
                            FilterChip(selected = s.tab == i, onClick = { vm.selectTab(i) }, label = { Text(t.first) })
                        }
                    }
                    Achievements(s, vm)
                    when {
                        s.loading && items == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
                        s.error != null -> Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Erro ao carregar: ${s.error}", color = ErrorRed, textAlign = TextAlign.Center)
                            Button(onClick = { vm.reload() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Tentar de novo") }
                        }
                        items != null && items.isEmpty() -> Text(TABS[s.tab].second, color = Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp))
                    }
                }
            },
        )
    }
}

@Composable
private fun Achievements(s: MyListState, vm: MyListViewModel) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp).clip(RoundedCornerShape(14.dp)).background(CardBg).padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🏆 Suas conquistas", fontWeight = FontWeight.Bold)
            TextButton(onClick = { vm.toggleMore() }) { Text(if (s.more) "Ver menos ▴" else "Ver mais ▾", color = Muted) }
        }
        when {
            s.badgesError -> Text("Não foi possível carregar as conquistas agora.", color = Muted, fontSize = 13.sp)
            s.badges == null -> Text("Carregando...", color = Muted, fontSize = 13.sp)
            s.badges.isEmpty() -> Text("Assista ou favorite alguns títulos pra desbloquear conquistas.", color = Muted, fontSize = 13.sp)
            else -> Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                s.badges.forEach { b ->
                    Column(
                        Modifier.width(120.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1B1B26)).padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(b.icon, fontSize = 26.sp)
                        Text(b.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, textAlign = TextAlign.Center)
                        Text(b.detail, color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        if (s.more) {
            val stats = s.genreStats
            Column(Modifier.padding(top = 10.dp)) {
                when {
                    stats == null -> Text("Calculando seus gêneros favoritos...", color = Muted, fontSize = 13.sp)
                    stats.isEmpty() -> Text("Sem dados suficientes ainda pra calcular seus gêneros favoritos.", color = Muted, fontSize = 13.sp)
                    else -> {
                        val max = stats.first().count.toFloat()
                        stats.take(8).forEach { g ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(g.name, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF2A2A38))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(g.count / max).background(Red))
                                }
                                Text("${g.count}", fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
