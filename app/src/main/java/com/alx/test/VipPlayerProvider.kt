package com.alx.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class VipPlayerProvider(private val client: OkHttpClient) {

    suspend fun extractAuto(id: String, isSerie: Boolean, season: Int, episode: Int, log: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            // 1. Pega a lista de servidores no playerflix
            log("🔍 Buscando hash no playerflix...")
            val tipo = if (isSerie) "tv" else "movie"
            val s = if (isSerie) season.toString() else "null"
            val e = if (isSerie) episode.toString() else "null"
            val apiUrl = "https://playerflix.ink/inc/Ajax.php?type=$tipo&id=$id&season=$s&episode=$e"

            val apiReq = Request.Builder()
                .url(apiUrl)
                .header("Referer", "https://playerflix.ink/")
                .header("X-Requested-With", "XMLHttpRequest")
                .build()

            val apiRes = client.newCall(apiReq).execute()
            val apiJson = JSONObject(apiRes.body?.string() ?: "{}")
            
            if (!apiJson.has("status") || !apiJson.getBoolean("status")) {
                log("❌ playerflix não retornou status=true")
                return@withContext null
            }

            val options = apiJson.getJSONObject("data").getJSONArray("options")
            var embedUrl = ""

            for (i in 0 until options.length()) {
                val opt = options.getJSONObject(i)
                if (opt.getString("label") == "VIP Player") {
                    embedUrl = opt.getString("embed")
                    break
                }
            }

            if (embedUrl.isEmpty()) {
                log("❌ VIP Player não encontrado na lista")
                return@withContext null
            }

            // 2. Extrai o hash do embed e faz o POST
            val hash = embedUrl.split("/").last()
            log("📡 Hash encontrado: $hash")

            val postUrl = "https://embedplayer2.xyz/player/index.php?data=$hash&do=getVideo"
            val body = "hash=$hash&r=https://embedplayer2.xyz/".toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val postReq = Request.Builder()
                .url(postUrl)
                .header("Referer", "https://embedplayer2.xyz/")
                .header("Origin", "https://embedplayer2.xyz")
                .header("X-Requested-With", "XMLHttpRequest")
                .post(body)
                .build()

            val postRes = client.newCall(postReq).execute()
            val postJson = JSONObject(postRes.body?.string() ?: "{}")

            if (postJson.has("securedLink")) {
                val m3u8 = postJson.getString("securedLink")
                log("✅ M3U8 do VIP Player obtido!")
                return@withContext m3u8
            } else {
                log("❌ VIP Player não retornou securedLink")
                return@withContext null
            }
        } catch (e: Exception) {
            log("❌ Erro: ${e.message}")
            null
        }
    }
}
