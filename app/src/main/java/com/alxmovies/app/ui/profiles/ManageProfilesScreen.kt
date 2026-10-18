package com.alxmovies.app.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.Repo
import com.alxmovies.app.data.deleteProfile
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.ProfileAvatar
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.launch

@Composable
fun ManageProfilesScreen(app: AppViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    var busy by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { app.loadProfiles() }
    BackHandler { onBack() }

    fun save(initial: Profile?, v: ProfileFormValues) {
        busy = true; formError = null
        scope.launch {
            try {
                ProfileActions.save(initial, v)
                app.loadProfiles()
                editing = null; creating = false
            } catch (e: Exception) {
                formError = e.message ?: "Erro ao salvar"
            } finally {
                busy = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
                Text("Gerenciar perfis", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(profiles, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProfileAvatar(p, 48.dp)
                        Column(Modifier.weight(1f)) {
                            Text((if (p.admin) "👑 " else "") + p.name, fontWeight = FontWeight.Bold)
                            val tags = listOfNotNull(
                                if (p.kids) "Infantil" else "Classificação ${p.maturityRating ?: "—"}",
                                if (!p.pin.isNullOrEmpty()) "PIN" else null,
                                if (p.timeRestrictionEnabled == true) "Horário limitado" else null,
                            )
                            Text(tags.joinToString(" · "), color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { formError = null; editing = p }) { Text("Editar", color = Color.White) }
                        TextButton(onClick = { deleting = p }) { Text("Apagar", color = Muted) }
                    }
                }
                if (profiles.size < Config.MAX_PROFILES) {
                    item {
                        OutlinedButton(onClick = { formError = null; creating = true }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            Text("+ Adicionar perfil", color = Color.White)
                        }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }

    if (creating) {
        ProfileFormDialog(
            initial = null, hasProfiles = profiles.isNotEmpty(), busy = busy, error = formError,
            onCancel = { if (!busy) creating = false }, onSubmit = { save(null, it) },
        )
    }
    editing?.let { p ->
        ProfileFormDialog(
            initial = p, hasProfiles = profiles.size > 1, busy = busy, error = formError,
            onCancel = { if (!busy) editing = null }, onSubmit = { save(p, it) },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Apagar \"${p.name}\"?") },
            text = { Text("O histórico, a lista e as avaliações deste perfil serão apagados. Não dá para desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try {
                            Repo.deleteProfile(p.id)
                            if (app.activeId.value == p.id) app.clearProfile()
                            app.loadProfiles()
                        } catch (e: Exception) {
                            snack.showSnackbar(e.message ?: "Não foi possível apagar")
                        }
                    }
                }) { Text("Apagar", color = Red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar", color = Muted) } },
        )
    }
}
