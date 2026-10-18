package com.alxmovies.app.ui.watch

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alxmovies.app.AlxPlayerActivity
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red

@Composable
fun WatchScreen(
    app: AppViewModel,
    type: String,
    id: Int,
    season: Int,
    episode: Int,
    onClose: () -> Unit,
    vm: WatchViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId }

    val ui by vm.ui.collectAsStateWithLifecycle()
    val request by vm.request.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val d = r.data
        vm.onPlayerResult(
            PlayerResult(
                reason = d?.getStringExtra("reason") ?: "closed",
                positionMs = d?.getLongExtra("positionMs", 0L) ?: 0L,
                durationMs = d?.getLongExtra("durationMs", 0L) ?: 0L,
                rate = d?.getFloatExtra("rate", 1f) ?: 1f,
                server = d?.getStringExtra("server"),
            )
        )
    }

    LaunchedEffect(profile?.id) { profile?.let { vm.start(it, type, id, season, episode) } }

    LaunchedEffect(request?.token) {
        val r = request ?: return@LaunchedEffect
        val i = Intent(context, AlxPlayerActivity::class.java).apply {
            putExtra("url", r.url)
            putExtra("title", r.title)
            putExtra("subtitle", r.subtitle)
            putExtra("poster", r.poster)
            putExtra("startMs", r.startMs)
            putExtra("startPercent", r.startPercent)
            putExtra("hasNext", r.hasNext)
            putExtra("server", r.server)
            putExtra("source", r.source)
            putExtra("fallbackLabel", "Reserva")
            putExtra("serverLabels", r.serverLabels)
            putExtra("serverOk", r.serverOk)
            putExtra("rate", r.rate)
        }
        vm.consumeRequest()
        launcher.launch(i)
    }

    LaunchedEffect(ui.phase) { if (ui.phase == Phase.CLOSED) onClose() }

    BackHandler {
        vm.cancel()
        onClose()
    }

    when (ui.phase) {
        Phase.ERROR -> MessageScreen(
            title = "Não foi possível reproduzir", text = ui.message,
            primary = "Tentar novamente", onPrimary = { vm.retry() },
            secondary = "Voltar", onSecondary = { vm.cancel(); onClose() },
        )
        Phase.BLOCKED -> MessageScreen(
            title = "🔒 Conteúdo bloqueado", text = ui.message,
            primary = "Voltar", onPrimary = { onClose() },
        )
        else -> PlayerLoading(ui.title, ui.subtitle, ui.backdrop, ui.slow)
    }
}

@Composable
private fun PlayerLoading(title: String, subtitle: String, backdrop: String?, slow: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (backdrop != null) {
            AsyncImage(model = backdrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xB3000000), Color(0xE6000000)))))
        Column(
            Modifier.align(Alignment.Center).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("ALXMOVIES", color = Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Color(0xFFD0D0DA), fontSize = 15.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(modifier = Modifier.width(240.dp).height(4.dp), color = Red, trackColor = Color(0x29FFFFFF))
            Spacer(Modifier.height(12.dp))
            Text(
                if (slow) "Esse servidor está demorando… estamos tentando outro caminho." else "Preparando o vídeo…",
                color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MessageScreen(
    title: String,
    text: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().background(Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(text, color = Muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onPrimary, modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
        ) { Text(primary, fontWeight = FontWeight.Bold) }
        if (secondary != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(secondary, color = Color.White) }
        }
    }
}
