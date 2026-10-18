package com.alxmovies.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alxmovies.app.data.Graph
import com.alxmovies.app.data.Rooms
import com.alxmovies.app.data.UpdateInfo
import com.alxmovies.app.data.Updater
import com.alxmovies.app.ui.account.AccountScreen
import com.alxmovies.app.ui.channels.ChannelPlayerScreen
import com.alxmovies.app.ui.channels.ChannelsScreen
import com.alxmovies.app.ui.details.DetailsScreen
import com.alxmovies.app.ui.home.HomeScreen
import com.alxmovies.app.ui.laura.LauraScreen
import com.alxmovies.app.ui.login.LoginScreen
import com.alxmovies.app.ui.mylist.MyListScreen
import com.alxmovies.app.ui.profiles.ManageProfilesScreen
import com.alxmovies.app.ui.profiles.ProfilesScreen
import com.alxmovies.app.ui.rooms.RoomScreen
import com.alxmovies.app.ui.rooms.RoomsScreen
import com.alxmovies.app.ui.search.SearchScreen
import com.alxmovies.app.ui.social.ChatScreen
import com.alxmovies.app.ui.social.FriendsScreen
import com.alxmovies.app.ui.social.MessagesScreen
import com.alxmovies.app.ui.social.NotificationsScreen
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.launch

private val TAB_ROUTES = setOf("home", "search", "channels", "salas", "mylist")

@Composable
fun AlxNav() {
    val nav = rememberNavController()
    val app: AppViewModel = viewModel()
    val loggedIn by app.loggedIn.collectAsStateWithLifecycle()
    val badges by app.badges.collectAsStateWithLifecycle()
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val start = remember {
        val s = Graph.session
        when {
            !s.loggedIn -> "login"
            s.profileId == null -> "profiles"
            else -> "home"
        }
    }

    // Se a sessão cair (logout, token vencido, convidado expirado), volta para o login.
    LaunchedEffect(loggedIn) {
        if (!loggedIn && nav.currentBackStackEntry?.destination?.route != "login") {
            nav.navigate("login") { popUpTo(0) { inclusive = true } }
        }
    }

    // Aviso de versão nova (uma vez por abertura do app)
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(loggedIn) { if (loggedIn && update == null) update = Updater.check(context) }

    fun go(route: String) {
        when {
            route == "home" -> nav.popBackStack("home", false).let { ok -> if (!ok) nav.navigate("home") { launchSingleTop = true } }
            route in TAB_ROUTES -> nav.navigate(route) { popUpTo("home"); launchSingleTop = true }
            else -> nav.navigate(route)
        }
    }
    fun switchProfile() {
        app.clearProfile()
        nav.navigate("profiles") { popUpTo(0) { inclusive = true } }
    }
    fun openDetails(type: String, id: Int) = nav.navigate("details/$type/$id")
    fun play(type: String, id: Int, s: Int? = null, e: Int? = null) = nav.navigate("watch/$type/$id?s=${s ?: -1}&e=${e ?: -1}")
    fun joinAndOpenRoom(roomId: String) {
        val name = profiles.firstOrNull { it.id == activeId }?.name ?: "Convidado"
        scope.launch {
            val ok = runCatching { Rooms.join(roomId, null, name) }.isSuccess
            if (ok) nav.navigate("room/$roomId") else go("salas")
        }
    }

    CompositionLocalProvider(LocalBadges provides badges) {
        NavHost(navController = nav, startDestination = start) {
            composable("login") {
                LoginScreen(app = app, onDone = { nav.navigate("profiles") { popUpTo("login") { inclusive = true } } })
            }
            composable("profiles") {
                ProfilesScreen(
                    app = app,
                    onEnter = { nav.navigate("home") { popUpTo("profiles") { inclusive = true } } },
                    onManage = { nav.navigate("manage-profiles") },
                )
            }
            composable("manage-profiles") { ManageProfilesScreen(app = app, onBack = { nav.popBackStack() }) }

            composable("home") {
                HomeScreen(
                    app = app, onNavigate = ::go, onSwitchProfile = ::switchProfile,
                    onOpenDetails = ::openDetails, onPlay = { t, i -> play(t, i) },
                )
            }
            composable("search") {
                SearchScreen(
                    app = app, onNavigate = ::go, onSwitchProfile = ::switchProfile,
                    onOpenDetails = ::openDetails, onOpenChannel = { nav.navigate("channelplayer") },
                )
            }
            composable("channels") {
                ChannelsScreen(app = app, onNavigate = ::go, onSwitchProfile = ::switchProfile, onOpenChannel = { nav.navigate("channelplayer") })
            }
            composable("channelplayer") { ChannelPlayerScreen(app = app, onClose = { nav.popBackStack() }) }
            composable("salas") {
                RoomsScreen(app = app, onNavigate = ::go, onSwitchProfile = ::switchProfile, onEnterRoom = { nav.navigate("room/$it") })
            }
            composable("room/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                RoomScreen(app = app, roomId = entry.arguments?.getString("id") ?: "", onLeave = { nav.popBackStack() })
            }
            composable("mylist") {
                MyListScreen(app = app, onNavigate = ::go, onSwitchProfile = ::switchProfile, onOpenDetails = ::openDetails)
            }

            composable(
                "details/{type}/{id}",
                arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.IntType }),
            ) { entry ->
                DetailsScreen(
                    app = app,
                    type = entry.arguments?.getString("type") ?: "movie",
                    id = entry.arguments?.getInt("id") ?: 0,
                    onBack = { nav.popBackStack() },
                    onPlay = { t, i, s, e -> play(t, i, s, e) },
                    onOpen = ::openDetails,
                )
            }
            composable(
                "watch/{type}/{id}?s={s}&e={e}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.IntType },
                    navArgument("s") { type = NavType.IntType; defaultValue = -1 },
                    navArgument("e") { type = NavType.IntType; defaultValue = -1 },
                ),
            ) { entry ->
                val a = entry.arguments
                com.alxmovies.app.ui.watch.WatchScreen(
                    app = app,
                    type = a?.getString("type") ?: "movie",
                    id = a?.getInt("id") ?: 0,
                    season = a?.getInt("s") ?: -1,
                    episode = a?.getInt("e") ?: -1,
                    onClose = { nav.popBackStack() },
                )
            }

            composable("amigos") {
                FriendsScreen(
                    app = app, onBack = { nav.popBackStack() },
                    onOpenChat = { fid, name -> nav.navigate("chat/$fid?name=${Uri.encode(name)}") },
                    onWatchAlong = { t, i, s, e -> play(t, i, s, e) },
                )
            }
            composable("mensagens") {
                MessagesScreen(
                    app = app, onBack = { nav.popBackStack() },
                    onOpenChat = { fid, name -> nav.navigate("chat/$fid?name=${Uri.encode(name)}") },
                    onOpenFriends = { nav.navigate("amigos") },
                )
            }
            composable(
                "chat/{fid}?name={name}",
                arguments = listOf(
                    navArgument("fid") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                ChatScreen(
                    app = app,
                    friendshipId = entry.arguments?.getString("fid") ?: "",
                    friendName = entry.arguments?.getString("name") ?: "",
                    onBack = { nav.popBackStack() },
                    onOpenTitle = ::openDetails,
                    onOpenRoom = ::joinAndOpenRoom,
                )
            }
            composable("notificacoes") {
                NotificationsScreen(app = app, onBack = { nav.popBackStack() }, onOpenTitle = ::openDetails, onOpenFriends = { nav.navigate("amigos") })
            }
            composable("laura") {
                LauraScreen(
                    app = app, onBack = { nav.popBackStack() },
                    onOpenDetails = ::openDetails, onPlay = { t, i -> play(t, i) },
                    onOpenChannel = { nav.navigate("channelplayer") }, onGoRooms = { go("salas") },
                )
            }
            composable("account") {
                AccountScreen(app = app, onBack = { nav.popBackStack() }, onManageProfiles = { nav.navigate("manage-profiles") })
            }
        }
    }

    update?.let { u ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Nova versão disponível") },
            text = { Text("Versão ${u.tag} do ALXmovies já saiu.\n" + u.notes.take(300)) },
            confirmButton = {
                TextButton(onClick = {
                    val url = u.apkUrl ?: u.pageUrl
                    if (url.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    update = null
                }) { Text("Baixar", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { update = null }) { Text("Depois", color = Muted) } },
        )
    }
}
