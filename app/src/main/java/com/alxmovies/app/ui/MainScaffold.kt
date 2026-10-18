package com.alxmovies.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alxmovies.app.data.Profile
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red

data class Badges(val messages: Int = 0, val notifications: Int = 0)

val LocalBadges = androidx.compose.runtime.compositionLocalOf { Badges() }

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Início", Icons.Filled.Home),
    Tab("search", "Buscar", Icons.Filled.Search),
    Tab("channels", "Canais", Icons.Filled.LiveTv),
    Tab("salas", "Salas", Icons.Filled.Groups),
    Tab("mylist", "Lista", Icons.Filled.Bookmark),
)

/** Estrutura das telas principais: barra de cima (logo/título, mensagens, avisos, perfil) e barra de baixo. */
@Composable
fun MainScaffold(
    selected: String,
    profile: Profile,
    onNavigate: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onLogout: () -> Unit,
    title: String? = null,
    overlay: Boolean = false,
    solid: Boolean = true,
    snackbarHost: SnackbarHostState? = null,
    unreadMessages: Int = LocalBadges.current.messages,
    unreadNotifications: Int = LocalBadges.current.notifications,
    content: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { if (snackbarHost != null) SnackbarHost(snackbarHost) },
        bottomBar = { BottomBar(selected, onNavigate) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            if (overlay) {
                content()
                TopBar(title, solid, selected == "home", profile, onNavigate, onSwitchProfile, onLogout, unreadMessages, unreadNotifications)
            } else {
                Column(Modifier.fillMaxSize()) {
                    TopBar(title, true, selected == "home", profile, onNavigate, onSwitchProfile, onLogout, unreadMessages, unreadNotifications)
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    title: String?,
    solid: Boolean,
    showSearch: Boolean,
    profile: Profile,
    onNavigate: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    onLogout: () -> Unit,
    unreadMessages: Int,
    unreadNotifications: Int,
) {
    var menu by remember { mutableStateOf(false) }
    val bg = if (solid) Brush.verticalGradient(listOf(Bg, Bg)) else Brush.verticalGradient(listOf(Color(0xAA000000), Color.Transparent))
    Row(
        Modifier.fillMaxWidth().background(bg).statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (title == null) {
            Row(Modifier.weight(1f)) {
                Text("ALX", color = Red, fontWeight = FontWeight.Black, fontSize = 24.sp)
                Text("movies", color = Color.White, fontWeight = FontWeight.Black, fontSize = 24.sp)
            }
        } else {
            Text(title, Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        if (showSearch) {
            IconButton(onClick = { onNavigate("search") }) { Icon(Icons.Filled.Search, "Buscar", tint = Color.White) }
        }
        BadgeIcon(Icons.Filled.ChatBubbleOutline, "Mensagens", unreadMessages) { onNavigate("mensagens") }
        BadgeIcon(Icons.Filled.NotificationsNone, "Avisos", unreadNotifications) { onNavigate("notificacoes") }
        Box {
            ProfileAvatar(profile, 34.dp, Modifier.clickable { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Minha conta") }, onClick = { menu = false; onNavigate("account") })
                DropdownMenuItem(text = { Text("Amigos") }, onClick = { menu = false; onNavigate("amigos") })
                DropdownMenuItem(text = { Text("Laura, assistente") }, onClick = { menu = false; onNavigate("laura") })
                DropdownMenuItem(text = { Text("Gerenciar perfis") }, onClick = { menu = false; onNavigate("manage-profiles") })
                DropdownMenuItem(text = { Text("Trocar perfil") }, onClick = { menu = false; onSwitchProfile() })
                DropdownMenuItem(text = { Text("Sair da conta") }, onClick = { menu = false; onLogout() })
            }
        }
    }
}

@Composable
private fun BadgeIcon(icon: ImageVector, desc: String, count: Int, onClick: () -> Unit) {
    Box {
        IconButton(onClick = onClick) { Icon(icon, desc, tint = Color.White) }
        if (count > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp).size(16.dp)
                    .background(Red, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) { Text(if (count > 9) "9+" else "$count", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun BottomBar(selected: String, onNavigate: (String) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.White, selectedTextColor = Color.White,
        unselectedIconColor = Muted, unselectedTextColor = Muted,
        indicatorColor = Red,
    )
    NavigationBar(containerColor = Color(0xFF0D0D14)) {
        TABS.forEach { t ->
            NavigationBarItem(
                selected = t.route == selected,
                onClick = { if (t.route != selected) onNavigate(t.route) },
                icon = { Icon(t.icon, null) },
                label = { Text(t.label) },
                colors = colors,
            )
        }
    }
}
