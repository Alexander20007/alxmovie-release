package com.alxmovies.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder

@Serializable
data class Channel(val name: String? = null, val group: String? = null, val logo: String? = null, val stream: String)

@Serializable
private data class ChannelsResp(val success: Boolean = false, val canais: List<Channel> = emptyList())

@Serializable
private data class ChannelFavRow(
    @SerialName("channel_name") val name: String? = null,
    @SerialName("channel_group") val group: String? = null,
    @SerialName("channel_logo") val logo: String? = null,
    @SerialName("channel_stream") val stream: String,
)

data class Category(val name: String?, val count: Int)
data class Program(val title: String, val start: String?, val end: String?)
data class Guide(val now: Program?, val next: Program?)

object Channels {
    private const val CHANNELS_API = "https://alx-player.netlify.app/.netlify/functions/canais"
    private const val EPG_API = "https://alx-player.netlify.app/.netlify/functions/programacao"
    private const val CACHE_TTL_MS = 10 * 60 * 1000L

    @Volatile private var catalog: List<Channel>? = null
    private val onlineCache = HashMap<String, Pair<Boolean, Long>>()

    private fun cleanGroup(g: String?) = (g ?: "").trim().replace(Regex("^canais\\s*\\|\\s*", RegexOption.IGNORE_CASE), "").trim()
    fun groupLabel(g: String?) = (g ?: "").replace(Regex("^canais\\s*\\|\\s*", RegexOption.IGNORE_CASE), "")

    suspend fun fetchAll(): List<Channel> {
        catalog?.let { return it }
        return try {
            val (_, text) = Api.getRaw(CHANNELS_API, 15)
            val r = Api.json.decodeFromString<ChannelsResp>(text)
            val list = if (r.success) r.canais else emptyList()
            if (list.isNotEmpty()) catalog = list
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun categories(): List<Category> {
        val all = fetchAll()
        val counts = LinkedHashMap<String, Int>()
        var noGroup = 0
        for (c in all) {
            val g = cleanGroup(c.group)
            if (g.isEmpty()) { noGroup++; continue }
            if (Regex("adult", RegexOption.IGNORE_CASE).containsMatchIn(g)) continue
            counts[g] = (counts[g] ?: 0) + 1
        }
        val cats = counts.entries.map { Category(it.key, it.value) }.sortedBy { it.name!!.lowercase() }.toMutableList()
        if (noGroup > 0) cats.add(Category(null, noGroup))
        return cats
    }

    suspend fun inCategory(name: String?): List<Channel> {
        val all = fetchAll()
        return if (name == null) all.filter { cleanGroup(it.group).isEmpty() }
        else all.filter { (it.group ?: "").uppercase().contains(name.uppercase()) }
    }

    suspend fun search(text: String): List<Channel> = try {
        val (_, body) = Api.getRaw("$CHANNELS_API?query=${URLEncoder.encode(text, "UTF-8")}", 15)
        val r = Api.json.decodeFromString<ChannelsResp>(body)
        if (r.success) r.canais else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    fun icon(name: String): String {
        val n = name.uppercase()
        val rules = listOf(
            "GLOBO|RECORD|BAND|SBT|RTP|SIC|TVI" to "📺", "ESPN|SPORT|ESPORT|LUTA|UFC|MMA|COMBATE" to "⚽",
            "FILME|MOVIE|CINEMA|SERIE" to "🎬", "ANIME|GEEK|NERD" to "🎌", "INFANT|KIDS|CRIAN" to "🧸",
            "NOTIC|NEWS|JORNAL" to "📰", "NOVELA|SOAP" to "💫", "MUSIC|MÚSIC" to "🎵", "RELIGI|GOSPEL" to "🙏", "DOC" to "🎥",
        )
        return rules.firstOrNull { Regex(it.first).containsMatchIn(n) }?.second ?: "📡"
    }

    /** Testa vários canais ao mesmo tempo e entrega cada um que estiver no ar. */
    suspend fun testOnline(channels: List<Channel>, onProgress: (tested: Int) -> Unit, onOnline: (Channel) -> Unit) {
        val sem = Semaphore(30)
        var tested = 0
        coroutineScope {
            channels.filter { it.stream.isNotEmpty() }.map { c ->
                async {
                    sem.withPermit {
                        val hit = synchronized(onlineCache) { onlineCache[c.stream]?.takeIf { System.currentTimeMillis() - it.second <= CACHE_TTL_MS }?.first }
                        val online = hit ?: Api.probe(c.stream).also { r ->
                            synchronized(onlineCache) { onlineCache[c.stream] = r to System.currentTimeMillis() }
                        }
                        tested++
                        onProgress(tested)
                        if (online) onOnline(c)
                    }
                }
            }.awaitAll()
        }
    }

    // ---------- favoritos (por perfil) ----------
    suspend fun favorites(profileId: String): List<Channel> =
        Api.json.decodeFromString<List<ChannelFavRow>>(
            Api.rest("GET", "channel_favorites?select=*&profile_id=eq.$profileId&order=added_at.desc")
        ).map { Channel(it.name, it.group, it.logo, it.stream) }

    suspend fun addFavorite(profileId: String, c: Channel) {
        val body = buildJsonObject {
            put("profile_id", profileId)
            put("channel_name", c.name ?: "Canal")
            put("channel_group", c.group)
            put("channel_logo", c.logo)
            put("channel_stream", c.stream)
        }.toString()
        Api.rest("POST", "channel_favorites", body)
    }

    suspend fun removeFavorite(profileId: String, stream: String) {
        Api.rest("DELETE", "channel_favorites?profile_id=eq.$profileId&channel_stream=eq.${URLEncoder.encode(stream, "UTF-8")}")
    }

    // ---------- guia de programação ----------
    private fun prog(o: JsonObject?): Program? {
        val t = runCatching { o?.get("titulo")?.jsonPrimitive?.contentOrNull }.getOrNull() ?: return null
        return Program(t, o?.get("inicio")?.jsonPrimitive?.contentOrNull, o?.get("fim")?.jsonPrimitive?.contentOrNull)
    }

    suspend fun guide(stream: String): Guide? = try {
        val (code, text) = Api.getRaw("$EPG_API?stream=${URLEncoder.encode(stream, "UTF-8")}", 5)
        val o = Api.json.parseToJsonElement(text).jsonObject
        if (code !in 200..299 || o["success"]?.jsonPrimitive?.contentOrNull != "true") null
        else Guide(prog(o["atual"] as? JsonObject), prog(o["proximo"] as? JsonObject))
    } catch (e: Exception) {
        null
    }
}

/** Alguns canais que estão no ar agora (para a Laura sugerir sem inventar). */
suspend fun Channels.knownOnline(limit: Int = 20): List<Channel> {
    val all = fetchAll().filter { it.stream.isNotEmpty() && !Regex("adult", RegexOption.IGNORE_CASE).containsMatchIn(it.group ?: "") }
    val pool = all.shuffled().take(60)
    val found = mutableListOf<Channel>()
    testOnline(pool, onProgress = {}, onOnline = { c -> if (found.size < limit) found.add(c) })
    return found.toList()
}
