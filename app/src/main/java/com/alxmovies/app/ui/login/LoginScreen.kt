package com.alxmovies.app.ui.login

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alxmovies.app.data.Api
import com.alxmovies.app.ui.AppViewModel
import com.alxmovies.app.ui.theme.Amber
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.ErrorRed
import com.alxmovies.app.ui.theme.Green
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.coroutines.launch

private enum class Mode { LOGIN, SIGNUP, FORGOT }

private data class Copy(val title: String, val subtitle: String, val submit: String, val switchText: String, val switchLink: String)

private val COPY = mapOf(
    Mode.LOGIN to Copy("Bem-vindo de volta", "Entre para continuar assistindo.", "Entrar", "Novo por aqui?", "Criar conta"),
    Mode.SIGNUP to Copy("Criar sua conta", "Leva menos de um minuto.", "Cadastrar", "Já tem conta?", "Entrar"),
    Mode.FORGOT to Copy("Recuperar senha", "Enviamos um link para você criar uma nova senha.", "Enviar link de recuperação", "Lembrou a senha?", "Voltar para o login"),
)

private val ERRORS = mapOf(
    "Invalid login credentials" to "E-mail ou senha incorretos.",
    "User already registered" to "Já existe uma conta com esse e-mail.",
    "Email not confirmed" to "Confirme seu e-mail antes de entrar.",
)

private fun translate(msg: String) = ERRORS[msg] ?: msg

private fun strength(pw: String): Pair<Float, Color> {
    var score = 0
    if (pw.length >= 6) score++
    if (pw.length >= 10) score++
    if (pw.any { it.isUpperCase() } && pw.any { it.isDigit() }) score++
    if (pw.any { !it.isLetterOrDigit() }) score++
    val pct = listOf(0f, 0.25f, 0.55f, 0.8f, 1f)[score]
    val color = listOf(Red, Red, Amber, Amber, Green)[score]
    return pct to color
}

@Composable
fun LoginScreen(app: AppViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val notice by app.notice.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(Mode.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    var guestOpen by rememberSaveable { mutableStateOf(false) }
    var guestCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(notice) {
        notice?.let {
            message = it
            app.consumeNotice()
        }
    }

    val copy = COPY.getValue(mode)

    fun fail(m: String) { error = m; message = "" }
    fun changeMode(m: Mode) { mode = m; error = ""; message = "" }
    fun errText(e: Exception) = translate(e.message ?: "Erro inesperado")

    fun submit() {
        error = ""; message = ""
        val mail = email.trim()

        if (mode == Mode.FORGOT) {
            if (mail.isEmpty()) return fail("Digite seu e-mail.")
            busy = true
            scope.launch {
                try {
                    Api.requestPasswordReset(mail)
                    message = "Se esse e-mail tiver uma conta, enviamos um link de recuperação. Confira sua caixa de entrada (e o spam)."
                } catch (e: Exception) {
                    fail(errText(e))
                } finally {
                    busy = false
                }
            }
            return
        }

        if (mail.isEmpty() || password.isEmpty()) return fail("Preencha e-mail e senha.")
        if (password.length < 6) return fail("A senha precisa ter pelo menos 6 caracteres.")
        if (mode == Mode.SIGNUP) {
            val u = username.trim()
            if (u.isEmpty()) return fail("Escolha um nome de usuário.")
            if (!Regex("^[a-zA-Z0-9_.]{3,24}$").matches(u)) return fail("Nome de usuário deve ter 3–24 caracteres (letras, números, _ ou .).")
            if (age.toIntOrNull() == null) return fail("Informe sua idade.")
        }

        busy = true
        scope.launch {
            try {
                if (mode == Mode.LOGIN) {
                    Api.signIn(mail, password)
                    app.onLoggedIn()
                    onDone()
                } else {
                    Api.signUp(mail, password, username.trim(), age.toInt(), phone.trim().ifEmpty { null })
                    mode = Mode.LOGIN
                    message = "Conta criada! Verifique seu e-mail para confirmar antes de entrar."
                }
            } catch (e: Exception) {
                fail(errText(e))
            } finally {
                busy = false
            }
        }
    }

    fun guestNew() {
        busy = true
        scope.launch {
            try {
                Api.signInAsGuest()
                guestOpen = false
                app.onLoggedIn()
                onDone()
            } catch (e: Exception) {
                guestOpen = false
                val m = e.message ?: "Erro inesperado"
                fail(if (m.contains("Anonymous", ignoreCase = true)) "Login de convidado está desativado neste servidor no momento." else translate(m))
            } finally {
                busy = false
            }
        }
    }

    fun guestRedeem() {
        busy = true
        scope.launch {
            try {
                Api.redeemGuestCode(guestCode)
                guestOpen = false
                app.onLoggedIn()
                onDone()
            } catch (e: Exception) {
                guestOpen = false
                fail(translate(e.message ?: "Código inválido."))
            } finally {
                busy = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF2A0509), Bg, Bg)))
            .systemBarsPadding()
            .imePadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Red),
                    contentAlignment = Alignment.Center,
                ) { Text("A", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp) }
                Spacer(Modifier.size(10.dp))
                Text("ALXmovies", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Spacer(Modifier.height(28.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(copy.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(copy.subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)

                if (error.isNotEmpty()) Text(error, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                if (message.isNotEmpty()) Text(message, color = Green, style = MaterialTheme.typography.bodyMedium)

                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = if (mode == Mode.FORGOT) ImeAction.Done else ImeAction.Next,
                    ),
                )

                if (mode == Mode.SIGNUP) {
                    OutlinedTextField(
                        value = username, onValueChange = { if (it.length <= 24) username = it },
                        label = { Text("Nome de usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = age, onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) age = v },
                            label = { Text("Idade") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        )
                        OutlinedTextField(
                            value = phone, onValueChange = { phone = it },
                            label = { Text("Telefone (opcional)") }, singleLine = true, modifier = Modifier.weight(2f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        )
                    }
                }

                if (mode != Mode.FORGOT) {
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        label = { Text("Senha") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Mostrar senha")
                            }
                        },
                    )
                    if (mode == Mode.SIGNUP && password.isNotEmpty()) {
                        val (pct, color) = strength(password)
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF2A2A38))) {
                            Box(Modifier.fillMaxWidth(pct).height(4.dp).background(color))
                        }
                    }
                }

                if (mode == Mode.LOGIN) {
                    Text(
                        "Esqueci minha senha", color = Muted, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { changeMode(Mode.FORGOT) }.padding(vertical = 4.dp),
                    )
                }

                Button(
                    onClick = { if (!busy) submit() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White),
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                    else Text(copy.submit, fontWeight = FontWeight.Bold)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(copy.switchText, color = Muted)
                    TextButton(onClick = {
                        changeMode(if (mode == Mode.LOGIN) Mode.SIGNUP else Mode.LOGIN)
                    }) { Text(copy.switchLink, color = Color.White, fontWeight = FontWeight.Bold) }
                }

                if (mode == Mode.LOGIN) {
                    OutlinedButton(
                        onClick = { guestOpen = true },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) { Text("Continuar como convidado", color = Color.White) }
                }
            }
        }
    }

    if (guestOpen) {
        AlertDialog(
            onDismissRequest = { if (!busy) guestOpen = false },
            title = { Text("Entrar como convidado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Você poderá assistir por 24 horas sem criar conta. Depois disso a sessão expira.")
                    Text("Já tem um código de recuperação?", color = Muted)
                    OutlinedTextField(
                        value = guestCode, onValueChange = { guestCode = it.uppercase() }, placeholder = { Text("Código") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { if (!busy) guestRedeem() }, enabled = !busy && guestCode.trim().length >= 4) {
                        Text("Usar código", color = Red, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { if (!busy) guestNew() }, enabled = !busy) {
                    Text(if (busy) "Entrando..." else "Nova sessão de convidado", color = Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { guestOpen = false }, enabled = !busy) { Text("Cancelar", color = Muted) }
            },
        )
    }
}
