package com.alxmovies.app.ui.account

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alxmovies.app.data.Api
import com.alxmovies.app.data.Channel
import com.alxmovies.app.data.Channels
import com.alxmovies.app.data.Config
import com.alxmovies.app.data.Friends
import com.alxmovies.app.data.Graph
import com.alxmovies.app.data.Repo
import com.alxmovies.app.data.SocialSettings
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.CardBg
import com.alxmovies.app.ui.theme.ErrorRed
import com.alxmovies.app.ui.theme.Green
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class AccountState(
    val loading: Boolean = true,
    val email: String = "",
    val username: String = "",
    val phone: String = "",
    val createdAt: String = "",
    val subscription: String? = null,
    val social: SocialSettings? = null,
    val watched: Int = 0,
    val inProgress: Int = 0,
    val favChannels: List<Channel> = emptyList(),
    val guestCode: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

class AccountViewModel : ViewModel() {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()
    private var pid: String? = null

    fun start(profileId: String) {
        if (pid == profileId) return
        pid = profileId
        viewModelScope.launch {
            val u = runCatching { Api.getUser() }.getOrNull()
            val meta = u?.get("user_metadata") as? kotlinx.serialization.json.JsonObject
            _state.update {
                it.copy(
                    loading = false,
                    email = u?.get("email")?.jsonPrimitive?.contentOrNull ?: Graph.session.email ?: "",
                    username = meta?.get("username")?.jsonPrimitive?.contentOrNull ?: "",
                    phone = meta?.get("phone")?.jsonPrimitive?.contentOrNull ?: "",
                    createdAt = (u?.get("created_at")?.jsonPrimitive?.contentOrNull ?: "").take(10),
                )
            }
        }
        viewModelScope.launch { runCatching { Friends.settings(profileId) }.onSuccess { s -> _state.update { it.copy(social = s) } } }
        viewModelScope.launch {
            val w = runCatching { Repo.fetchWatched(profileId, 500).size }.getOrDefault(0)
            val p = runCatching { Repo.fetchContinueWatching(profileId, 500).size }.getOrDefault(0)
            _state.update { it.copy(watched = w, inProgress = p) }
        }
        viewModelScope.launch { runCatching { Channels.favorites(profileId) }.onSuccess { c -> _state.update { it.copy(favChannels = c) } } }
        viewModelScope.launch {
            val uid = Graph.session.userId ?: return@launch
            runCatching {
                val t = Api.rest("GET", "subscriptions?select=*&user_id=eq.$uid&limit=1")
                val o = (Api.json.parseToJsonElement(t) as? JsonArray)?.firstOrNull()?.jsonObject
                o?.get("status")?.jsonPrimitive?.contentOrNull
            }.onSuccess { st -> _state.update { it.copy(subscription = st) } }
        }
    }

    fun consumeMessage() { _state.update { it.copy(message = null) } }

    private fun run(okMessage: String, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false, message = okMessage, error = false) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = e.message ?: "Algo deu errado", error = true) }
            }
        }
    }

    fun saveInfo(username: String, phone: String) = run("Dados atualizados!") {
        Api.updateUser(buildJsonObject { putJsonObject("data") { put("username", username.trim()); put("phone", phone.trim().ifEmpty { null }) } }.toString())
        _state.update { it.copy(username = username.trim(), phone = phone.trim()) }
    }

    fun changePassword(current: String, new: String) = run("Senha alterada!") {
        Api.reauthenticate(current)
        Api.updateUser(buildJsonObject { put("password", new) }.toString())
    }

    fun sendReset() = run("Enviamos um link de redefinição para o seu e-mail.") { Api.requestPasswordReset(_state.value.email) }

    fun upgrade(email: String, password: String, username: String, phone: String) = run("Conta criada! Confirme o e-mail se for solicitado.") {
        Api.upgradeGuest(email.trim(), password, username.trim(), phone.trim().ifEmpty { null }, null)
    }

    fun genCode() = run("Código gerado!") {
        val c = Api.generateGuestCode()
        _state.update { it.copy(guestCode = c) }
    }

    fun setSocial(showWatching: Boolean? = null, friendsEnabled: Boolean? = null, accept: Boolean? = null) {
        val p = pid ?: return
        viewModelScope.launch {
            try {
                val s = Friends.updateSettings(p, showWatching, friendsEnabled, accept)
                _state.update { it.copy(social = s) }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Não foi possível salvar", error = true) }
            }
        }
    }

    fun removeChannel(c: Channel) {
        val p = pid ?: return
        viewModelScope.launch {
            runCatching { Channels.removeFavorite(p, c.stream) }
                .onSuccess { _state.update { s -> s.copy(favChannels = s.favChannels.filterNot { it.stream == c.stream }) } }
        }
    }
}

@Composable
private fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Text(if (open) "▴" else "▾", color = Muted)
        }
        if (open) Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun SwitchLine(label: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(hint, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun AccountScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onManageProfiles: () -> Unit,
    vm: AccountViewModel = viewModel(),
) {
    val profiles by app.profiles.collectAsStateWithLifecycle()
    val activeId by app.activeId.collectAsStateWithLifecycle()
    val profile = profiles.firstOrNull { it.id == activeId } ?: return
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val guest = Graph.session.isAnonymous

    LaunchedEffect(profile.id) { vm.start(profile.id) }
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }

    fun open(path: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Config.SITE_ORIGIN + path)))

    Box(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White) }
                Text("Minha conta", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            if (s.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Red) }
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBg).padding(14.dp)) {
                    Text(if (guest) "🎭 Você está como convidado" else (s.username.ifEmpty { s.email }), fontWeight = FontWeight.Black, fontSize = 18.sp)
                    if (guest) Text("A sessão de convidado dura 24h. Crie uma conta para manter seus dados.", color = Muted, fontSize = 13.sp)
                    else {
                        Text(s.email, color = Muted, fontSize = 13.sp)
                        if (s.createdAt.isNotEmpty()) Text("Conta criada em ${s.createdAt}", color = Muted, fontSize = 12.sp)
                    }
                    Text("Perfil ativo: ${profile.name}", color = Muted, fontSize = 12.sp)
                }

                if (guest) GuestPanel(s, vm) else {
                    Section("Dados da conta", "Nome de usuário e telefone") { InfoForm(s, vm) }
                    Section("Segurança", "Trocar a senha") { PasswordForm(s, vm) }
                }

                Section("Perfis", "Foto, PIN, classificação, horário…") {
                    Button(onClick = onManageProfiles, colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth()) { Text("Gerenciar perfis") }
                }

                Section("Amigos e privacidade", "Vale para o perfil \"${profile.name}\"") {
                    val social = s.social
                    if (social == null) Text("Carregando...", color = Muted)
                    else {
                        val locked = profile.kids && !profile.admin
                        if (locked) Text("Perfis infantis só podem ser ajustados pelo responsável da conta.", color = Muted, fontSize = 12.sp)
                        SwitchLine("Ativar amizades", "Adicionar amigos e trocar mensagens", social.friendsEnabled == true, !locked) { vm.setSocial(friendsEnabled = it, accept = if (it) true else null) }
                        SwitchLine("Receber pedidos de amizade", "Quem tiver seu código pode te enviar pedido", social.acceptRequests == true, !locked && social.friendsEnabled == true) { vm.setSocial(accept = it) }
                        SwitchLine("Mostrar o que estou assistindo", "Amigos veem seu título em tempo real", social.showWatching == true, !locked && social.friendsEnabled == true) { vm.setSocial(showWatching = it) }
                    }
                }

                Section("Atividade", "Resumo deste perfil") {
                    Text("✔ ${s.watched} título(s) assistido(s)")
                    Text("▶ ${s.inProgress} em andamento")
                    Text("📡 ${s.favChannels.size} canal(is) favorito(s)")
                }

                Section("Assinatura", "Plano da conta") {
                    Text(
                        when (s.subscription) { null -> "Sem assinatura ativa"; "active" -> "✔ Assinatura ativa"; else -> "Status: ${s.subscription}" },
                        color = if (s.subscription == "active") Green else Muted,
                    )
                }

                Section("Canais favoritos", "${s.favChannels.size} salvo(s)") {
                    if (s.favChannels.isEmpty()) Text("Nenhum canal favoritado ainda.", color = Muted, fontSize = 13.sp)
                    s.favChannels.forEach { c ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(c.name ?: "Canal", Modifier.weight(1f), maxLines = 1)
                            TextButton(onClick = { vm.removeChannel(c) }) { Text("Remover", color = Muted) }
                        }
                    }
                }

                Section("Sessão e conta", "Sair ou excluir a conta") {
                    OutlinedButton(onClick = { app.signOut() }, modifier = Modifier.fillMaxWidth()) { Text("Sair da conta neste dispositivo", color = Color.White) }
                    if (!guest) OutlinedButton(
                        onClick = {
                            val subject = Uri.encode("Solicitação de exclusão de conta - ALXmovies")
                            val body = Uri.encode("Olá, gostaria de solicitar a exclusão definitiva da minha conta ALXmovies (${s.email}).")
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("mailto:alxmovies.tv@gmail.com?subject=$subject&body=$body")))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Solicitar exclusão da conta", color = ErrorRed) }
                    Text("A exclusão definitiva remove a conta, todos os perfis, favoritos e histórico, e é confirmada por e-mail com nossa equipe.", color = Muted, fontSize = 11.sp)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { open("/privacidade") }) { Text("Privacidade", color = Muted, fontSize = 12.sp) }
                    TextButton(onClick = { open("/cookies") }) { Text("Cookies", color = Muted, fontSize = 12.sp) }
                    TextButton(onClick = { open("/seguranca") }) { Text("Segurança", color = Muted, fontSize = 12.sp) }
                }
                Text("ALXmovies nativo 2.0", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(16.dp))
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun InfoForm(s: AccountState, vm: AccountViewModel) {
    var username by remember(s.username) { mutableStateOf(s.username) }
    var phone by remember(s.phone) { mutableStateOf(s.phone) }
    OutlinedTextField(value = s.email, onValueChange = {}, label = { Text("E-mail") }, enabled = false, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = username, onValueChange = { if (it.length <= 24) username = it }, label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefone") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    Button(
        onClick = { vm.saveInfo(username, phone) }, enabled = !s.busy && username.trim().length >= 3,
        colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth(),
    ) { Text("Salvar") }
}

@Composable
private fun PasswordForm(s: AccountState, vm: AccountViewModel) {
    var cur by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var conf by remember { mutableStateOf("") }
    val err = when {
        new.isNotEmpty() && new.length < 6 -> "A nova senha precisa ter pelo menos 6 caracteres."
        conf.isNotEmpty() && conf != new -> "As senhas não conferem."
        else -> null
    }
    OutlinedTextField(value = cur, onValueChange = { cur = it }, label = { Text("Senha atual") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = new, onValueChange = { new = it }, label = { Text("Nova senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = conf, onValueChange = { conf = it }, label = { Text("Repetir nova senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    if (err != null) Text(err, color = ErrorRed, fontSize = 12.sp)
    Button(
        onClick = { vm.changePassword(cur, new); cur = ""; new = ""; conf = "" },
        enabled = !s.busy && cur.isNotEmpty() && new.length >= 6 && conf == new,
        colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth(),
    ) { Text("Alterar senha") }
    TextButton(onClick = { vm.sendReset() }, enabled = !s.busy) { Text("Esqueci a senha — enviar link por e-mail", color = Muted) }
}

@Composable
private fun GuestPanel(s: AccountState, vm: AccountViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    Section("✨ Criar conta", "Transforme este acesso em uma conta de verdade") {
        OutlinedTextField(value = username, onValueChange = { if (it.length <= 24) username = it }, label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefone (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Button(
            onClick = { vm.upgrade(email, password, username, phone) },
            enabled = !s.busy && email.contains("@") && password.length >= 6 && username.trim().length >= 3,
            colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth(),
        ) { Text("Criar conta") }
    }
    Section("🔑 Código de recuperação", "Volte a esta sessão em outro aparelho") {
        Text("Gere um código e use-o em \"Continuar como convidado\" na tela de login para recuperar esta sessão.", color = Muted, fontSize = 12.sp)
        if (s.guestCode != null) Text(s.guestCode, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Green)
        OutlinedButton(onClick = { vm.genCode() }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text(if (s.guestCode == null) "Gerar código" else "Gerar outro código", color = Color.White) }
    }
}
