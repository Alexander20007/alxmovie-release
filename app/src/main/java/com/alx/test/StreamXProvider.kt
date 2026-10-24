package com.alx.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class StreamXProvider(private val client: OkHttpClient) {

    suspend fun extractAuto(id: String, isSerie: Boolean, season: Int, episode: Int, log: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            log("🔍 Buscando slug no filmesbluray...")
            
            // 1. Busca no TMDB para achar o slug (título formatado)
            val tmdbUrl = "https://api.themoviedb.org/3/movie/$id?api_key=SUA_CHAVE_TMDB&language=pt-BR"
            val tmdbReq = Request.Builder().url(tmdbUrl).build()
            val tmdbRes = client.newCall(tmdbReq).execute()
            val tmdbJson = tmdbRes.body?.string() ?: "{}"
            
            // Extrai o título sem JSON parser complexo
            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(tmdbJson)
            val title = titleMatch?.groupValues?.get(1) ?: "filme"
            
            // Formata o slug (igual ao Pobreflix)
            val slug = title.lowercase()
                .replace(Regex("[^a-z0-9\\s]"), "")
                .replace(" ", "-")
                .trim()

            log("📡 Slug encontrado: $slug")

            // 2. Tenta o StreamX com o slug (ID fixo 8277813)
            val streamxUrl = "https://streamx.me/8277813/filme.mp4?$slug"
            log("📡 GET $streamxUrl")

            val req = Request.Builder()
                .url(streamxUrl)
                .header("Referer", "https://filmesbluray.net/")
                .header("Range", "bytes=0-")
                .build()

            val res = client.newCall(req).execute()
            val contentType = res.header("Content-Type") ?: "?"

            log("📥 Status: ${res.code} | Tipo: $contentType")

            if ((res.code == 200 || res.code == 206) && contentType.contains("video/mp4")) {
                log("✅ MP4 válido!")
                return@withContext streamxUrl
            } else {
                log("⚠️ Bloqueado ou slug errado")
                return@withContext null
            }
        } catch (e: Exception) {
            log("❌ Erro: ${e.message}")
            null
        }
    }
}
