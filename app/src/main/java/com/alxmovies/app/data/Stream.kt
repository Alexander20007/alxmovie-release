package com.alxmovies.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

data class ServerOpt(val label: String, val ok: Boolean)

data class StreamResult(
    /** "main" (API principal) ou "fallback" (API reserva). */
    val source: String,
    val m3u8: String,
    val server: String,
    val servers: List<ServerOpt>,
    val next: EpRef?,
)

/** Descobre o link do vídeo: API principal, com a reserva entrando se a principal falhar ou demorar. */
object Stream {
    const val FALLBACK = "Reserva"
    private const val PRIORITY = "VIP Player"
    private const val PROXY_REFERER = "https://embedplayer2.xyz/"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun base() = Config.STREAM_BASE_URL.trimEnd('/')

    private suspend fun requestOnce(type: String, id: Int, season: Int?, episode: Int?, server: String?): JsonObject {
        var url = if (type == "tv") "${base()}/api/stream/tv/$id/$season/$episode" else "${base()}/api/stream/movie/$id"
        if (server != null) url += "?servidor=${enc(server)}"

        val (_, text) = try {
            Api.getRaw(url, 90)
        } catch (e: ApiException) {
            delay(1200)
            Api.getRaw(url, 90)
        }
        val data = runCatching { Api.json.parseToJsonElement(text).jsonObject }.getOrNull()
        val ok = data?.get("status")?.jsonPrimitive?.booleanOrNull == true
        val m3u8 = data?.get("m3u8")?.jsonPrimitive?.contentOrNull
        if (data == null || !ok || m3u8.isNullOrEmpty()) {
            throw ApiException(
                runCatching { data?.get("erro")?.jsonPrimitive?.contentOrNull }.getOrNull()
                    ?: "Este vídeo não está disponível no momento."
            )
        }
        return data
    }

    private suspend fun fetchMain(type: String, id: Int, season: Int?, episode: Int?, server: String?): StreamResult {
        val data = if (server != null) {
            requestOnce(type, id, season, episode, server)
        } else {
            try {
                requestOnce(type, id, season, episode, PRIORITY)
            } catch (e: ApiException) {
                requestOnce(type, id, season, episode, null)
            }
        }
        val servidor = data["servidor"]?.jsonPrimitive?.contentOrNull ?: ""
        val raw = data["m3u8"]!!.jsonPrimitive.content
        // O servidor prioritário precisa passar pelo proxy da API
        val m3u8 = if (servidor == PRIORITY) "${base()}/api/proxy?url=${enc(raw)}&referer=${enc(PROXY_REFERER)}" else raw

        val servers = (data["servidores_disponiveis"] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val label = o["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ServerOpt(label, o["budget"]?.jsonPrimitive?.contentOrNull == "success")
        } ?: emptyList()

        val n = data["proximo_episodio"] as? JsonObject
        val s = n?.get("season")?.jsonPrimitive?.intOrNull
        val e = n?.get("episode")?.jsonPrimitive?.intOrNull
        return StreamResult("main", m3u8, servidor, servers, if (s != null && e != null) EpRef(s, e) else null)
    }

    private suspend fun fetchFallback(type: String, id: Int, season: Int?, episode: Int?): StreamResult {
        val fb = Config.EXTRACTOR_BASE_URL.trimEnd('/')
        var url = "$fb/extract?tmdb_id=$id&type=$type"
        if (type == "tv" && season != null && episode != null) url += "&season=$season&episode=$episode"
        val (_, text) = Api.getRaw(url, 60)
        val data = runCatching { Api.json.parseToJsonElement(text).jsonObject }.getOrNull()
        val ok = data?.get("success")?.jsonPrimitive?.booleanOrNull == true
        val hls = data?.get("hlsUrl")?.jsonPrimitive?.contentOrNull
        if (!ok || hls.isNullOrEmpty()) throw ApiException("Não foi possível carregar o vídeo.")
        val cookies = data?.get("cookies")?.jsonPrimitive?.contentOrNull ?: ""
        return StreamResult("fallback", "$fb/proxy?url=${enc(hls)}&cookies=${enc(cookies)}", FALLBACK, emptyList(), null)
    }

    suspend fun resolve(
        type: String,
        id: Int,
        season: Int?,
        episode: Int?,
        server: String?,
        fallbackOnly: Boolean,
        onSlow: () -> Unit,
    ): StreamResult = coroutineScope {
        if (fallbackOnly) return@coroutineScope fetchFallback(type, id, season, episode)
        if (server != null) return@coroutineScope fetchMain(type, id, season, episode, server)

        val result = CompletableDeferred<StreamResult>()
        var mainErr: Throwable? = null
        var mainFailed = false
        var fbFailed = false
        var fbStarted = false

        fun lose() {
            if (!result.isCompleted && mainFailed && fbFailed) {
                result.completeExceptionally(
                    (mainErr as? ApiException) ?: ApiException("Este vídeo não está disponível no momento.")
                )
            }
        }

        fun startFallback() {
            if (fbStarted || result.isCompleted) return
            fbStarted = true
            launch {
                try {
                    result.complete(fetchFallback(type, id, season, episode))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    fbFailed = true
                    lose()
                }
            }
        }

        launch { delay(6000); if (!result.isCompleted) onSlow() }
        launch { delay(8000); startFallback() }
        launch {
            try {
                result.complete(fetchMain(type, id, season, episode, null))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mainErr = e
                mainFailed = true
                startFallback()
                lose()
            }
        }

        try {
            result.await()
        } finally {
            coroutineContext.cancelChildren()
        }
    }
}
