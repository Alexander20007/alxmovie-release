package com.alx.test

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class EmbedSuProvider(private val client: OkHttpClient) {

    private fun decodeHash(hash: String): List<JSONObject> {
        // Simulação simplificada da decodificação dupla em Kotlin
        val firstDecode = String(android.util.Base64.decode(hash, android.util.Base64.DEFAULT))
            .split(".").map { it.reversed() }.joinToString("")
        val reversed = firstDecode.reversed()
        val secondDecode = String(android.util.Base64.decode(reversed, android.util.Base64.DEFAULT))
        
        // No app real, precisaríamos de um parser JSON array. Aqui vamos simplificar.
        return emptyList() 
    }

    suspend fun extract(id: String, isSerie: Boolean, season: Int, episode: Int, log: (String) -> Unit): String? = withContext(Dispatchers.IO) {
        try {
            val url = if (isSerie) "https://embed.su/embed/tv/$id/$season/$episode" else "https://embed.su/embed/movie/$id"
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            val html = client.newCall(req).execute().body?.string() ?: ""

            val match = Regex("""window\.vConfig\s*=\s*JSON\.parse\(atob\(`(.+?)`\)\)""").find(html)
            if (match == null) {
                log("❌ EmbedSu: vConfig não encontrado")
                return@withContext null
            }

            val decodedData = JSONObject(String(android.util.Base64.decode(match.groupValues[1], android.util.Base64.DEFAULT)))
            val servers = decodeHash(decodedData.getString("hash"))

            // Como a decodificação complexa em Kotlin pode ser instável sem libs externas,
            // vamos tentar fazer a requisição direta pro endpoint de teste deles (se existir)
            log("⚠️ EmbedSu: decodificação complexa (requer ajuste fino em Kotlin)")
            return@withContext null
        } catch (e: Exception) {
            log("❌ Erro: ${e.message}")
            null
        }
    }
}
