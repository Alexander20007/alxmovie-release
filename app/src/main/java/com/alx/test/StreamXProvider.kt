package com.alx.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class StreamXProvider(private val client: OkHttpClient) {

    suspend fun extract(
        id: String,
        isSerie: Boolean,
        season: Int,
        episode: Int,
        log: (String) -> Unit
    ): String? = withContext(Dispatchers.IO) {
        try {
            // StreamX usa ID fixo 8277813 + slug
            // O slug é derivado do título (não temos o título aqui)
            // Vamos testar com o slug genérico
            val slug = "teste"
            val url = "https://streamx.me/8277813/$slug.mp4?$slug"

            log("📡 GET $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", "https://filmesbluray.net/")
                .header("Range", "bytes=0-")
                .build()

            val response = client.newCall(request).execute()
            val contentType = response.header("Content-Type") ?: "?"

            log("📥 Status: ${response.code} | Tipo: $contentType")

            if (response.code == 200 || response.code == 206) {
                if (contentType.contains("video/mp4")) {
                    log("✅ MP4 válido encontrado!")
                    return@withContext url
                } else {
                    log("⚠️ Retornou HTML (bloqueado ou slug errado)")
                    return@withContext null
                }
            } else {
                log("❌ Bloqueado: ${response.code}")
                return@withContext null
            }
        } catch (e: Exception) {
            log("❌ Erro: ${e.message}")
            null
        }
    }
}
