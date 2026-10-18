package com.alxmovies.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(message: String, val code: Int = 0) : Exception(message)

@Serializable
private data class AuthUserDto(
    val id: String,
    val email: String? = null,
    @SerialName("is_anonymous") val isAnonymous: Boolean? = false,
)

@Serializable
private data class AuthResp(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: AuthUserDto? = null,
)

/** Fala direto com a API REST do Supabase (login + banco) e com a função do TMDB. */
object Api {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        isLenient = true
    }

    private val jsonType = "application/json".toMediaType()
    private val refreshLock = Mutex()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Origin", Config.SITE_ORIGIN).build())
        }
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun nowSec() = System.currentTimeMillis() / 1000

    private fun build(
        url: String,
        method: String,
        body: String? = null,
        bearer: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): Request {
        val b = Request.Builder().url(url)
            .header("apikey", Config.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer " + (bearer ?: Config.SUPABASE_ANON_KEY))
        for ((k, v) in extra) b.header(k, v)
        val rb = if (method == "GET") null else (body ?: "").toRequestBody(jsonType)
        return b.method(method, rb).build()
    }

    /** Chamada de rede que cancela de verdade quando a corrotina é cancelada. */
    private suspend fun exec(req: Request, readTimeoutSec: Long? = null): Pair<Int, String> =
        suspendCancellableCoroutine { cont ->
            val c = if (readTimeoutSec == null) client else client.newBuilder().readTimeout(readTimeoutSec, TimeUnit.SECONDS).build()
            val call = c.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(ApiException("Sem conexão. Verifique sua internet."))
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.body?.string() ?: ""
                        val code = response.code
                        if (cont.isActive) cont.resume(code to text)
                    } catch (e: IOException) {
                        if (cont.isActive) cont.resumeWithException(ApiException("Sem conexão. Verifique sua internet."))
                    } finally {
                        response.close()
                    }
                }
            })
        }

    private fun errorOf(code: Int, body: String): ApiException {
        val msg = runCatching {
            val o = json.parseToJsonElement(body).jsonObject
            (o["msg"] ?: o["message"] ?: o["error_description"] ?: o["error"])?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return ApiException(msg ?: "Erro $code", code)
    }

    // ---------- sessão ----------

    private fun saveSession(r: AuthResp) {
        val s = Graph.session
        s.accessToken = r.accessToken
        s.refreshToken = r.refreshToken
        s.expiresAt = r.expiresAt ?: (nowSec() + (r.expiresIn ?: 3600L))
        s.userId = r.user?.id
        s.email = r.user?.email
        s.isAnonymous = r.user?.isAnonymous ?: false
        s.markLoggedIn()
    }

    private suspend fun refresh(): String? = refreshLock.withLock {
        val s = Graph.session
        if (nowSec() < s.expiresAt - 60) return@withLock s.accessToken
        val rt = s.refreshToken ?: return@withLock null
        val body = buildJsonObject { put("refresh_token", rt) }.toString()
        val (code, text) = exec(build("${Config.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token", "POST", body))
        if (code in 200..299) {
            saveSession(json.decodeFromString<AuthResp>(text))
            s.accessToken
        } else {
            if (code == 400 || code == 401 || code == 403) s.clearAuth()
            null
        }
    }

    private suspend fun validToken(): String {
        val s = Graph.session
        val at = s.accessToken ?: throw ApiException("Usuário não autenticado", 401)
        if (nowSec() < s.expiresAt - 60) return at
        return refresh() ?: at
    }

    // ---------- auth ----------

    suspend fun signIn(email: String, password: String) {
        val body = buildJsonObject { put("email", email); put("password", password) }.toString()
        val (code, text) = exec(build("${Config.SUPABASE_URL}/auth/v1/token?grant_type=password", "POST", body))
        if (code !in 200..299) throw errorOf(code, text)
        saveSession(json.decodeFromString<AuthResp>(text))
        Graph.session.guestStartedAt = 0L
    }

    suspend fun signUp(email: String, password: String, username: String, age: Int, phone: String?) {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            putJsonObject("data") {
                put("age", age)
                put("username", username)
                put("phone", phone)
            }
        }.toString()
        val (code, text) = exec(build("${Config.SUPABASE_URL}/auth/v1/signup", "POST", body))
        if (code !in 200..299) throw errorOf(code, text)
    }

    suspend fun signInAsGuest() {
        val (code, text) = exec(build("${Config.SUPABASE_URL}/auth/v1/signup", "POST", """{"data":{}}"""))
        if (code !in 200..299) throw errorOf(code, text)
        val r = json.decodeFromString<AuthResp>(text)
        if (r.accessToken == null) throw ApiException("Anonymous sign-ins are disabled")
        Graph.session.profileId = null
        saveSession(r)
        Graph.session.guestStartedAt = System.currentTimeMillis()
    }

    suspend fun requestPasswordReset(email: String) {
        val redirect = URLEncoder.encode("${Config.SITE_ORIGIN}/reset-password", "UTF-8")
        val body = buildJsonObject { put("email", email) }.toString()
        val (code, text) = exec(build("${Config.SUPABASE_URL}/auth/v1/recover?redirect_to=$redirect", "POST", body))
        if (code !in 200..299) throw errorOf(code, text)
    }

    suspend fun signOut() {
        val s = Graph.session
        val at = s.accessToken
        if (at != null) runCatching { exec(build("${Config.SUPABASE_URL}/auth/v1/logout", "POST", "{}", at)) }
        s.clearAuth()
    }

    // ---------- banco (PostgREST) ----------

    suspend fun rest(method: String, path: String, body: String? = null, returnRep: Boolean = false, prefer: String? = null): String {
        val pv = prefer ?: if (returnRep) "return=representation" else null
        val extra = if (pv != null) mapOf("Prefer" to pv) else emptyMap()
        for (attempt in 0..1) {
            val token = validToken()
            val (code, text) = exec(build("${Config.SUPABASE_URL}/rest/v1/$path", method, body, token, extra))
            if (code in 200..299) return text
            if (code == 401 && attempt == 0) {
                Graph.session.expiresAt = 0L // força renovar o token e tenta de novo
                continue
            }
            throw errorOf(code, text)
        }
        throw ApiException("Falha na requisição")
    }

    // ---------- usuário, funções, upload ----------

    /** Chamada autenticada (com o token do usuário) a qualquer endpoint do Supabase. */
    suspend fun authed(method: String, url: String, body: String? = null, extra: Map<String, String> = emptyMap()): String {
        val token = validToken()
        val (code, text) = exec(build(url, method, body, token, extra))
        if (code !in 200..299) throw errorOf(code, text)
        return text
    }

    suspend fun getUser(): kotlinx.serialization.json.JsonObject =
        json.parseToJsonElement(authed("GET", "${Config.SUPABASE_URL}/auth/v1/user")).jsonObject

    suspend fun updateUser(body: String) {
        authed("PUT", "${Config.SUPABASE_URL}/auth/v1/user", body)
    }

    /** Confirma a senha atual (usado antes de ações sensíveis). */
    suspend fun reauthenticate(password: String) {
        val email = Graph.session.email ?: throw ApiException("Usuário não autenticado")
        signIn(email, password)
    }

    suspend fun callFunction(name: String, body: String = "{}", useUserToken: Boolean = true): kotlinx.serialization.json.JsonObject {
        val token = if (useUserToken) validToken() else null
        val (code, text) = exec(build("${Config.SUPABASE_URL}/functions/v1/$name", "POST", body, token), 60)
        val o = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
        if (code !in 200..299) throw ApiException(runCatching { o?.get("error")?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "Erro $code", code)
        return o ?: kotlinx.serialization.json.JsonObject(emptyMap())
    }

    suspend fun uploadAvatar(path: String, bytes: ByteArray, mime: String): String {
        val token = validToken()
        val req = Request.Builder().url("${Config.SUPABASE_URL}/storage/v1/object/avatars/$path")
            .header("apikey", Config.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer $token")
            .header("x-upsert", "true")
            .post(bytes.toRequestBody(mime.toMediaType()))
            .build()
        val (code, text) = exec(req)
        if (code !in 200..299) throw errorOf(code, text)
        return "${Config.SUPABASE_URL}/storage/v1/object/public/avatars/$path"
    }

    /** Entra numa sessão de convidado já existente usando o código de recuperação. */
    suspend fun redeemGuestCode(code: String) {
        val o = callFunction("guest-redeem-code", buildJsonObject { put("code", code.trim()) }.toString(), useUserToken = false)
        val sess = o["session"]?.jsonObject ?: throw ApiException("Código inválido.")
        val s = Graph.session
        s.accessToken = sess["access_token"]?.jsonPrimitive?.contentOrNull
        s.refreshToken = sess["refresh_token"]?.jsonPrimitive?.contentOrNull
        s.expiresAt = nowSec() + 3000
        val u = getUser()
        s.userId = u["id"]?.jsonPrimitive?.contentOrNull
        s.email = u["email"]?.jsonPrimitive?.contentOrNull
        s.isAnonymous = true
        s.guestStartedAt = runCatching { java.time.Instant.parse(o["guestStartedAt"]?.jsonPrimitive?.contentOrNull ?: "").toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
        s.profileId = null
        s.markLoggedIn()
    }

    suspend fun generateGuestCode(): String {
        val o = callFunction("guest-generate-code")
        return o["code"]?.jsonPrimitive?.contentOrNull ?: throw ApiException("Não foi possível gerar o código.")
    }

    /** Vira conta de verdade (convidado -> e-mail e senha). */
    suspend fun upgradeGuest(email: String, password: String, username: String, phone: String?, age: Int?) {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            putJsonObject("data") { put("username", username); put("phone", phone); put("age", age) }
        }.toString()
        updateUser(body)
        Graph.session.isAnonymous = false
        Graph.session.guestStartedAt = 0L
        Graph.session.email = email
    }

    /** "Está no ar?": qualquer resposta HTTP conta (igual ao teste no-cors do site). */
    suspend fun probe(url: String, timeoutMs: Long = 2500): Boolean {
        suspend fun once(method: String): Boolean = suspendCancellableCoroutine { cont ->
            val c = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
            val req = try {
                Request.Builder().url(url).method(method, null).build()
            } catch (e: Exception) { cont.resume(false); return@suspendCancellableCoroutine }
            val call = c.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resume(false) }
                override fun onResponse(call: Call, response: Response) { response.close(); if (cont.isActive) cont.resume(true) }
            })
        }
        return once("HEAD") || once("GET")
    }

    // ---------- TMDB (via função do Supabase) ----------

    suspend fun tmdb(path: String, params: Map<String, Any?> = emptyMap()): String {
        val body = buildJsonObject {
            put("path", path.trimStart('/'))
            putJsonObject("params") {
                put("language", "pt-BR")
                for ((k, v) in params) {
                    when (v) {
                        is Int -> put(k, v)
                        is String -> put(k, v)
                        is Boolean -> put(k, v)
                        else -> {}
                    }
                }
            }
        }.toString()
        val (code, text) = exec(build(Config.TMDB_FUNCTION_URL, "POST", body))
        if (code !in 200..299) throw ApiException("Erro TMDB: $code", code)
        return text
    }

    /** POST de JSON usando só a chave pública do app; devolve (código, corpo) mesmo quando não é 2xx. */
    suspend fun postRaw(url: String, body: String, readTimeoutSec: Long = 30): Pair<Int, String> =
        exec(build(url, "POST", body, null), readTimeoutSec)

    /** GET sem login; devolve (código, corpo) mesmo quando o código não é 2xx. */
    suspend fun getRaw(url: String, readTimeoutSec: Long = 30): Pair<Int, String> =
        exec(Request.Builder().url(url).get().build(), readTimeoutSec)

    suspend fun simpleGet(url: String): String {
        val (code, text) = exec(Request.Builder().url(url).get().build())
        if (code !in 200..299) throw ApiException("Erro $code", code)
        return text
    }
}
