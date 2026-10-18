package com.alxmovies.app.ui.profiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.alxmovies.app.data.Api
import com.alxmovies.app.data.ApiException
import com.alxmovies.app.data.ContentFilter
import com.alxmovies.app.data.Genre
import com.alxmovies.app.data.Graph
import com.alxmovies.app.data.Profile
import com.alxmovies.app.data.Repo
import com.alxmovies.app.data.Tmdb
import com.alxmovies.app.data.updateProfile
import com.alxmovies.app.ui.parseColor
import com.alxmovies.app.ui.theme.Bg
import com.alxmovies.app.ui.theme.ErrorRed
import com.alxmovies.app.ui.theme.Muted
import com.alxmovies.app.ui.theme.Red
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream

val AVATAR_COLORS = listOf("#ff2d55", "#e0a11c", "#2e9e4a", "#1f7ae0", "#8e2eea", "#e02e8e", "#2ec3c3", "#6b6b6b")

class ProfileFormValues(
    val name: String,
    val age: Int?,
    val birthday: String?,
    val isKids: Boolean,
    val isAdmin: Boolean,
    val maturity: String,
    val pin: String?,
    val color: String,
    val favGenres: List<Int>,
    val timeEnabled: Boolean,
    val start: String?,
    val end: String?,
    val avatarBytes: ByteArray?,
    val removeAvatar: Boolean,
)

/** Cria ou atualiza um perfil (com envio da foto, se houver). */
object ProfileActions {
    suspend fun save(initial: Profile?, v: ProfileFormValues) {
        val uid = Graph.session.userId ?: throw ApiException("Usuário não autenticado")
        var avatarUrl: String? = initial?.avatar
        if (v.removeAvatar) avatarUrl = null
        if (v.avatarBytes != null) {
            val key = (initial?.id ?: uid.take(8)) + "-" + System.currentTimeMillis() + ".jpg"
            avatarUrl = Api.uploadAvatar(key, v.avatarBytes, "image/jpeg")
        }
        val admin = if (v.isKids) false else v.isAdmin
        val body = buildJsonObject {
            if (initial == null) put("user_id", uid)
            put("name", v.name)
            put("avatar", avatarUrl)
            put("is_kids", v.isKids)
            put("maturity_rating", if (v.isKids) "10" else v.maturity)
            put("pin", v.pin)
            put("age", v.age)
            put("birthday", v.birthday)
            put("color_theme", v.color)
            put("favorite_genres", JsonArray(v.favGenres.map { JsonPrimitive(it) }))
            put("time_restriction_enabled", v.timeEnabled)
            if (v.timeEnabled) { put("allowed_start_time", v.start); put("allowed_end_time", v.end) }
            else { put("allowed_start_time", JsonNull); put("allowed_end_time", JsonNull) }
        }
        if (initial == null) {
            val existing = Repo.fetchProfiles(uid)
            val isAdmin = admin || existing.isEmpty()
            if (isAdmin && existing.any { it.admin }) Api.rest("PATCH", "profiles?user_id=eq.$uid&is_admin=eq.true", """{"is_admin":false}""")
            val full = buildJsonObject { body.forEach { (k, e) -> put(k, e) }; put("is_admin", isAdmin) }
            Api.rest("POST", "profiles", full.toString(), returnRep = true)
        } else {
            val full = buildJsonObject {
                body.forEach { (k, e) -> put(k, e) }
                if (admin != initial.admin) put("is_admin", admin)
            }
            Repo.updateProfile(initial.id, full)
        }
    }
}

/** Lê a foto escolhida, corta em quadrado e reduz para até 512 px (JPEG). */
fun readAvatarBytes(context: Context, uri: Uri): ByteArray? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (opts.outWidth / sample > 1024 || opts.outHeight / sample > 1024) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val side = minOf(bmp.width, bmp.height)
    val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
    val out = Bitmap.createScaledBitmap(square, minOf(512, side), minOf(512, side), true)
    val bos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.JPEG, 85, bos)
    return bos.toByteArray()
}

private val TIME = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileFormDialog(
    initial: Profile?,
    hasProfiles: Boolean,
    busy: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onSubmit: (ProfileFormValues) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var age by remember { mutableStateOf(initial?.age?.toString() ?: "") }
    var birthday by remember { mutableStateOf(initial?.birthday ?: "") }
    var pin by remember { mutableStateOf(initial?.pin ?: "") }
    var kids by remember { mutableStateOf(initial?.kids ?: false) }
    var admin by remember { mutableStateOf(initial?.admin ?: false) }
    var maturity by remember { mutableStateOf(initial?.maturityRating ?: "16") }
    var color by remember { mutableStateOf(initial?.colorTheme ?: AVATAR_COLORS.random()) }
    var fav by remember { mutableStateOf(initial?.favGenres()?.toSet() ?: emptySet()) }
    var timeOn by remember { mutableStateOf(initial?.timeRestrictionEnabled == true) }
    var start by remember { mutableStateOf(initial?.allowedStartTime?.take(5) ?: "08:00") }
    var end by remember { mutableStateOf(initial?.allowedEndTime?.take(5) ?: "20:00") }
    var avatarBytes by remember { mutableStateOf<ByteArray?>(null) }
    var removeAvatar by remember { mutableStateOf(false) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { genres = runCatching { Tmdb.genres("movie") }.getOrDefault(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val b = runCatching { readAvatarBytes(context, uri) }.getOrNull()
            if (b != null) { avatarBytes = b; removeAvatar = false } else localError = "Não foi possível ler essa imagem."
        }
    }

    fun submit() {
        val n = name.trim()
        if (n.isEmpty()) { localError = "Digite um nome."; return }
        if (pin.isNotEmpty() && pin.length != 4) { localError = "O PIN precisa ter 4 números."; return }
        if (birthday.isNotEmpty() && !Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(birthday)) { localError = "Aniversário no formato AAAA-MM-DD."; return }
        if (timeOn && (!TIME.matches(start) || !TIME.matches(end))) { localError = "Horários no formato HH:mm (ex.: 08:00)."; return }
        localError = null
        onSubmit(
            ProfileFormValues(
                name = n, age = age.toIntOrNull(), birthday = birthday.ifEmpty { null }, isKids = kids, isAdmin = admin && !kids,
                maturity = maturity, pin = pin.ifEmpty { null }, color = color, favGenres = fav.toList(),
                timeEnabled = timeOn, start = start.takeIf { timeOn }, end = end.takeIf { timeOn },
                avatarBytes = avatarBytes, removeAvatar = removeAvatar,
            )
        )
    }

    Dialog(onDismissRequest = { if (!busy) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel, enabled = !busy) { Text("Cancelar", color = Muted) }
                Text(
                    if (initial == null) "Novo perfil" else "Editar perfil", Modifier.weight(1f),
                    fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                TextButton(onClick = { if (!busy) submit() }, enabled = !busy) {
                    Text(if (busy) "Salvando..." else "Salvar", color = Red, fontWeight = FontWeight.Bold)
                }
            }
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val shown = localError ?: error
                if (shown != null) Text(shown, color = ErrorRed)

                // foto
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(parseColor(color)), contentAlignment = Alignment.Center) {
                        val model: Any? = avatarBytes ?: if (removeAvatar) null else initial?.avatar?.takeIf { it.isNotBlank() }
                        if (model != null) AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else Text(name.trim().take(1).uppercase().ifEmpty { "?" }, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        TextButton(onClick = { picker.launch("image/*") }) { Text("Escolher foto", color = Red, fontWeight = FontWeight.Bold) }
                        if (avatarBytes != null || (!removeAvatar && !initial?.avatar.isNullOrBlank())) {
                            TextButton(onClick = { avatarBytes = null; removeAvatar = true }) { Text("Remover foto", color = Muted) }
                        }
                    }
                }

                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= 20) name = it }, label = { Text("Nome") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = age, onValueChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) age = v },
                    label = { Text("Idade (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = birthday, onValueChange = { if (it.length <= 10) birthday = it },
                    label = { Text("Aniversário AAAA-MM-DD (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin, onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) pin = v },
                    label = { Text("PIN de 4 números (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )

                SwitchRow("Perfil infantil", kids) { kids = it }
                if (!kids && (hasProfiles || initial != null)) SwitchRow("Responsável da conta", admin) { admin = it }
                if (!kids) {
                    Text("Classificação máxima", color = Muted, style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(ContentFilter.RATINGS) { (value, label) ->
                            FilterChip(selected = maturity == value, onClick = { maturity = value }, label = { Text(label) })
                        }
                    }
                }

                Text("Cor do avatar", color = Muted, style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(AVATAR_COLORS) { hex ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(parseColor(hex))
                                .border(if (hex == color) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { color = hex }
                        )
                    }
                }

                if (genres.isNotEmpty()) {
                    Text("Gêneros favoritos", color = Muted, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genres.forEach { g ->
                            FilterChip(
                                selected = g.id in fav,
                                onClick = { fav = if (g.id in fav) fav - g.id else fav + g.id },
                                label = { Text(g.name) },
                            )
                        }
                    }
                }

                SwitchRow("Limitar horário de uso", timeOn) { timeOn = it }
                if (timeOn) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = start, onValueChange = { if (it.length <= 5) start = it }, label = { Text("Das") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = end, onValueChange = { if (it.length <= 5) end = it }, label = { Text("Até") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Text("Fora desse horário o perfil não abre. Usa a hora do servidor.", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

