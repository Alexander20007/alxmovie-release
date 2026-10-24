package com.alx.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class WatchPlayProvider(private val client: OkHttpClient) {

    suspend fun extract(
        id: String,
        isSerie: Boolean,
        season: Int,
        episode: Int,
        log: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            val url = if (isSerie) {
                "https://v1.watchplay.shop/tvshow/$id/$season/$episode"
            } else {
                "https://v1.watchplay.shop/movie/$id"
            }

            log("📡 GET $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", "https://playerflix.ink/")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext null

            log("📥 Status: ${response.code} | Tamanho: ${html.length} bytes")

            // Regex pra capturar a URL do m3u8
            val regex = Regex("""url:\s*["']([^"']+\.m3u8[^"']*)["']""")
            val match = regex.find(html)

            if (match != null) {
                val m3u8 = match.groupValues[1].replace("\\/", "/")
                log("🎯 M3U8: $m3u8")
                return@withContext m3u8
            } else {
                log("❌ M3U8 não encontrado no HTML")
                return@withContext null
            }
        } catch (e: Exception) {
            log("❌ Erro: ${e.message}")
            null
        }
    }
}
