package com.alxmovies.app.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.ContentFilter
import com.alxmovies.app.data.Parental
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.Repo
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.ProfileAvatar
import com.alxmovies.app.ui.parseColor
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.ErrorRed
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen(app: AppViewModel, onEnter: () -> Unit, onManage: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val loading by app.loading.collectAsStateWithLifecycle()
    val loaded by app.loaded.collectAsStateWithLifecycle()
    val loadError by app.error.collectAsStateWithLifecycle()

    var notice by remember { mutableStateOf<String?>(null) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { app.loadProfiles() }

    fun enter(p: Profile) {
        app.selectProfile(p.id)
        onEnter()
    }

    fun choose(p: Profile) {
        scope.launch {
            val utc = Parental.nowUtcMinutes()
            val off = Parental.tzOffsetMinutes()
            if (!Parental.isWithin(p, utc, off)) {
                notice = "⏰ O perfil \"${p.name}\" só pode ser usado das ${Parental.window(p)}. Tente de novo dentro desse horário."
                return@launch
            }
            notice = null
            if (!p.pin.isNullOrEmpty()) pinFor = p else enter(p)
        }
    }

    fun create(v: ProfileFormValues) {
        busy = true
        formError = null
        scope.launch {
            try {
                ProfileActions.save(null, v)
                app.loadProfiles()
                creating = false
            } catch (e: Exception) {
                formError = e.message ?: "Erro ao criar perfil"
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Bg).systemBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Row {
            Text("ALX", color = Red, fontWeight = FontWeight.Black, fontSize = 28.sp)
            Text("movies", color = Color.White, fontWeight = FontWeight.Black, fontSize = 28.sp)
        }
        Spacer(Modifier.height(20.dp))
        Text("Quem está assistindo?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            notice ?: "Escolha um perfil para continuar",
            color = if (notice != null) Color(0xFFFF8B8F) else Muted,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        when {
            loading && profiles.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Red)
            }
            loadError != null && !loaded -> Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(loadError ?: "", color = ErrorRed, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { app.loadProfiles() }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Text("Tentar de novo") }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(profiles, key = { it.id }) { p ->
                    Column(
                        Modifier.fillMaxWidth().clickable { choose(p) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box {
                            ProfileAvatar(p, 104.dp)
                            if (p.kids) {
                                Text(
                                    "KIDS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                        .clip(RoundedCornerShape(6.dp)).background(Red)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text((if (p.admin) "👑 " else "") + p.name, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                }
                if (profiles.size < Config.MAX_PROFILES) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clickable { formError = null; creating = true },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(104.dp).clip(CircleShape).border(2.dp, Color(0xFF3A3A4A), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text("+", fontSize = 40.sp, color = Muted) }
                            Spacer(Modifier.height(8.dp))
                            Text("Adicionar perfil", color = Muted)
                        }
                    }
                }
            }
        }

        Row {
            TextButton(onClick = onManage) { Text("Gerenciar perfis", color = Color.White) }
            TextButton(onClick = { app.signOut() }) { Text("Sair da conta", color = Muted) }
        }
        Spacer(Modifier.height(8.dp))
    }

    pinFor?.let { p ->
        PinDialog(
            correct = p.pin ?: "",
            onOk = { pinFor = null; enter(p) },
            onCancel = { pinFor = null },
        )
    }

    if (creating) {
        ProfileFormDialog(
            initial = null, hasProfiles = profiles.isNotEmpty(), busy = busy, error = formError,
            onCancel = { if (!busy) creating = false },
            onSubmit = { create(it) },
        )
    }
}

@Composable
private fun PinDialog(correct: String, onOk: () -> Unit, onCancel: () -> Unit) {
    var value by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Perfil protegido") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) { value = v; wrong = false } },
                    label = { Text("Digite o PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                )
                if (wrong) Text("PIN incorreto.", color = ErrorRed, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (value == correct) onOk() else wrong = true }) {
                Text("Entrar", color = Red, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancelar", color = Muted) } },
    )
}
