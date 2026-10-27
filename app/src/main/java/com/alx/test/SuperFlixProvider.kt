package com.alx.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class SuperFlixProvider(private val client: OkHttpClient) {

    suspend fun extract(id: String, isSerie: Boolean, season: Int, episode: Int, log: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val url = if (isSerie) {
                "https://superflixonline.lat/episodio/$id/$season/$episode" // Ajustar se o padrão de série for diferente
            } else {
                "https://superflixonline.lat/filme/$id"
            }

            log("📡 GET $url (via OkHttp)")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; TECNO KI5k) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""

            log("📥 Status: ${response.code} | Tamanho: ${html.length} bytes")

            if (response.code != 200) {
                log("❌ Erro HTTP: ${response.code}")
                return@withContext null
            }

            // Regex pra achar o link dentro do data-playback-source
            val regex = Regex("""data-playback-source=["']([^"']+)["']""")
            val match = regex.find(html)

            if (match != null) {
                val embedUrl = match.groupValues[1]
                log("🎯 URL do iframe encontrada: ${embedUrl.take(80)}...")
                
                // Agora que temos a URL do iframe (que provavelmente não tem Cloudflare),
                // usamos o WebView pra carregar ELA.
                return@withContext WebViewExtractor(client).extract(embedUrl, log) // Precisamos passar o context pro WebViewExtractor
                
            } else {
                log("❌ data-playback-source não encontrado no HTML")
                return@withContext null
            }

        } catch (e: Exception) {
            log("❌ Erro: ${e.message}")
            null
        }
    }
}
