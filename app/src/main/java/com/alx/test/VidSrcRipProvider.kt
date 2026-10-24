package com.alx.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class VidSrcRipProvider(private val client: OkHttpClient) {

    private var cachedKey: String? = null
    private var keyTime: Long = 0

    private fun getKey(log: (String) -> Unit): String {
        val now = System.currentTimeMillis()
        if (cachedKey != null && now - keyTime < 3600000) return cachedKey!!

        val req = Request.Builder()
            .url("https://vidsrc.rip/images/skip-button.png")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
        val res = client.newCall(req).execute()
        val key = res.body?.string() ?: ""
        cachedKey = key
        keyTime = now
        log("🔑 VidSrcRip: chave obtida (${key.length} chars)")
        return key
    }

    private fun generateVRF(key: String, path: String): String {
        val keyCodes = key.toByteArray()
        val msgCodes = path.toByteArray()
        val result = ByteArray(msgCodes.size)
        for (i in msgCodes.indices) {
            result[i] = (msgCodes[i].toInt() xor keyCodes[i % keyCodes.size].toInt()).toByte()
        }
        return URLEncoder.encode(android.util.Base64.encodeToString(result, android.util.Base64.DEFAULT), "UTF-8")
    }

    suspend fun extract(id: String, isSerie: Boolean, season: Int, episode: Int, log: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val url = if (isSerie) "https://vidsrc.rip/embed/tv/$id/$season/$episode" else "https://vidsrc.rip/embed/movie/$id"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val html = client.newCall(req).execute().body?.string() ?: ""

            // Parser manual simples pra pegar os servers do window.config
            val serverMatches = Regex("\"([a-zA-Z0-9_]+)\"").findAll(html.substringAfter("servers:").substringBefore("]"))
            val servers = serverMatches.map { it.groupValues[1] }.filter { it != "servers" }.toList()

            if (servers.isEmpty()) {
                log("❌ VidSrcRip: nenhum server encontrado")
                return@withContext null
            }

            val key = getKey(log)
            for (server in servers) {
                val path = "/api/source/$server/$id"
                val vrf = generateVRF(key, path)
                val apiUrl = "https://vidsrc.rip$path?vrf=$vrf" + (if (isSerie) "&s=$season&e=$episode" else "")

                log("📡 VidSrcRip: tentando $server")
                val apiReq = Request.Builder().url(apiUrl).header("User-Agent", "Mozilla/5.0").build()
                val apiRes = client.newCall(apiReq).execute()
                val json = JSONObject(apiRes.body?.string() ?: "{}")

                if (json.has("sources")) {
                    val sources = json.getJSONArray("sources")
                    if (sources.length() > 0) {
                        val file = sources.getJSONObject(0).getString("file")
                        log("✅ VidSrcRip: M3U8 encontrado!")
                        return@withContext file
                    }
                }
            }
            log("❌ VidSrcRip: todos os servers falharam")
            return@withContext null
        } catch (e: Exception) {
            log("❌ Erro: ${e.message}")
            null
        }
    }
}
