package com.alxmovies.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

@Serializable
data class SocialSettings(
    @SerialName("friend_code") val friendCode: String? = null,
    @SerialName("show_watching") val showWatching: Boolean? = false,
    @SerialName("friends_enabled") val friendsEnabled: Boolean? = false,
    @SerialName("accept_requests") val acceptRequests: Boolean? = false,
    val discoverable: Boolean? = false,
)

@Serializable
data class DiscoverRow(
    @SerialName("profile_id") val profileId: String,
    val name: String = "",
    val avatar: String? = null,
    @SerialName("color_theme") val colorTheme: String? = null,
    @SerialName("mutual_count") val mutualCount: Int? = 0,
)

@Serializable
data class FriendRow(
    @SerialName("friendship_id") val friendshipId: String,
    val direction: String = "friend",
    @SerialName("friend_name") val name: String = "",
    @SerialName("friend_avatar") val avatar: String? = null,
    @SerialName("friend_color") val color: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_message") val lastMessage: String? = null,
    @SerialName("last_message_kind") val lastMessageKind: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    @SerialName("last_message_mine") val lastMessageMine: Boolean? = false,
    @SerialName("unread_count") val unread: Int? = 0,
    @SerialName("watching_title") val watchingTitle: String? = null,
    @SerialName("watching_at") val watchingAt: String? = null,
    @SerialName("watching_media_id") val watchingMediaId: Int? = null,
    @SerialName("watching_media_type") val watchingMediaType: String? = null,
    @SerialName("watching_season") val watchingSeason: Int? = null,
    @SerialName("watching_episode") val watchingEpisode: Int? = null,
)

@Serializable
data class FriendMessage(
    val id: String,
    @SerialName("friendship_id") val friendshipId: String = "",
    @SerialName("sender_id") val senderId: String = "",
    val kind: String = "text",
    val body: String? = "",
    val payload: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
)

data class FriendNotification(
    val id: String,
    val kind: String,
    val friendshipId: String,
    val fromName: String,
    val fromAvatar: String?,
    val fromColor: String?,
    val createdAt: String?,
)

data class FriendLists(val friends: List<FriendRow>, val incoming: List<FriendRow>, val outgoing: List<FriendRow>, val blocked: List<FriendRow>)

private fun parseTime(iso: String?): Long? = try { if (iso == null) null else OffsetDateTime.parse(iso).toInstant().toEpochMilli() } catch (e: Exception) { null }

fun FriendRow.isLive(): Boolean {
    val t = parseTime(watchingAt) ?: return false
    return !watchingTitle.isNullOrEmpty() && System.currentTimeMillis() - t < 150_000
}

fun FriendRow.watchingLabel(): String {
    if (watchingTitle.isNullOrEmpty()) return ""
    return if (watchingMediaType == "tv" && watchingSeason != null && watchingEpisode != null)
        "$watchingTitle · T$watchingSeason E$watchingEpisode" else watchingTitle
}

fun messagePreview(kind: String?, body: String?, mine: Boolean?): String {
    val p = if (mine == true) "Você: " else ""
    return when (kind) {
        "title" -> "$p🎬 Indicou um título"
        "room" -> "$p👥 Convidou pra uma sala"
        else -> p + (body ?: "")
    }
}

fun timeAgo(iso: String?): String {
    val t = parseTime(iso) ?: return ""
    val min = ((System.currentTimeMillis() - t) / 60000).toInt()
    if (min < 1) return "agora"
    if (min < 60) return "há $min min"
    val h = min / 60
    if (h < 24) return "há $h h"
    val d = h / 24
    if (d == 1) return "ontem"
    if (d < 7) return "há $d dias"
    return OffsetDateTime.parse(iso).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
}

fun clockTime(iso: String?): String = try {
    OffsetDateTime.parse(iso).atZoneSameInstant(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
} catch (e: Exception) { "" }

fun formatFriendCode(code: String?): String {
    val c = (code ?: "").uppercase()
    return if (c.length == 8) "${c.take(4)}-${c.drop(4)}" else c
}

/** Títulos indicados por mensagem: { media_id, media_type, title, poster_path, year } */
data class TitlePayload(val id: Int, val type: String, val title: String, val poster: String?, val year: String)
data class RoomPayload(val id: String, val title: String, val movieTitle: String, val poster: String?)

fun parseTitlePayload(p: JsonElement?): TitlePayload? {
    val o = p as? JsonObject ?: return null
    val id = o["media_id"]?.jsonPrimitive?.intOrNull ?: return null
    val type = when (o["media_type"]?.jsonPrimitive?.contentOrNull) { "tv" -> "tv"; "movie" -> "movie"; else -> return null }
    return TitlePayload(
        id, type, o["title"]?.jsonPrimitive?.contentOrNull ?: "Título", o["poster_path"]?.jsonPrimitive?.contentOrNull,
        (o["year"]?.jsonPrimitive?.contentOrNull ?: "").take(4),
    )
}

fun parseRoomPayload(p: JsonElement?): RoomPayload? {
    val o = p as? JsonObject ?: return null
    val id = o["room_id"]?.jsonPrimitive?.contentOrNull ?: return null
    if (!Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$").matches(id)) return null
    return RoomPayload(id, o["title"]?.jsonPrimitive?.contentOrNull ?: "Sala", o["movie_title"]?.jsonPrimitive?.contentOrNull ?: "", o["poster_path"]?.jsonPrimitive?.contentOrNull)
}

object Friends {
    private val bg = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

    fun stopWatchingAsync() { bg.launch { stopWatching() } }

    private fun friendly(e: ApiException): ApiException {
        val raw = e.message ?: ""
        return if (Regex("could not find the function|best candidate function", RegexOption.IGNORE_CASE).containsMatchIn(raw))
            ApiException("Banco de dados incompleto: $raw — rode de novo as partes 1 a 7 da pasta sql/amigos no Supabase (em ordem).")
        else e
    }

    private suspend fun rpc(name: String, args: JsonObject): String =
        try { Api.rest("POST", "rpc/$name", args.toString()) } catch (e: ApiException) { throw friendly(e) }

    private inline fun <reified T> list(text: String): List<T> =
        if (text.isBlank() || text.trim() == "null") emptyList() else Api.json.decodeFromString<List<T>>(text)

    suspend fun settings(pid: String): SocialSettings =
        Api.json.decodeFromString(rpc("social_ensure_settings", buildJsonObject { put("p_profile_id", pid) }))

    suspend fun updateSettings(pid: String, showWatching: Boolean? = null, friendsEnabled: Boolean? = null, acceptRequests: Boolean? = null): SocialSettings {
        presenceEnabled = null
        return Api.json.decodeFromString(rpc("social_update_settings", buildJsonObject {
            put("p_profile_id", pid)
            if (showWatching != null) put("p_show_watching", showWatching)
            if (friendsEnabled != null) put("p_friends_enabled", friendsEnabled)
            if (acceptRequests != null) put("p_accept_requests", acceptRequests)
        }))
    }

    suspend fun discover(pid: String, query: String = ""): List<DiscoverRow> =
        list(rpc("social_discover", buildJsonObject {
            put("p_profile_id", pid)
            if (query.trim().isEmpty()) put("p_query", JsonNull) else put("p_query", query.trim())
            put("p_limit", 20)
        }))

    suspend fun sendRequestTo(pid: String, target: String) {
        rpc("social_send_request_to", buildJsonObject { put("p_from_profile_id", pid); put("p_to_profile_id", target) })
    }

    suspend fun setDiscoverable(pid: String, v: Boolean) {
        rpc("social_set_discoverable", buildJsonObject { put("p_profile_id", pid); put("p_value", v) })
    }

    suspend fun regenerateCode(pid: String): SocialSettings =
        Api.json.decodeFromString(rpc("social_regenerate_code", buildJsonObject { put("p_profile_id", pid) }))

    suspend fun sendRequest(pid: String, code: String) {
        rpc("social_send_request", buildJsonObject { put("p_from_profile_id", pid); put("p_code", code) })
    }

    suspend fun respond(pid: String, friendshipId: String, accept: Boolean) {
        rpc("social_respond_request", buildJsonObject { put("p_profile_id", pid); put("p_friendship_id", friendshipId); put("p_accept", accept) })
    }

    suspend fun remove(pid: String, id: String) { rpc("social_remove_friend", buildJsonObject { put("p_profile_id", pid); put("p_friendship_id", id) }) }
    suspend fun block(pid: String, id: String) { rpc("social_block", buildJsonObject { put("p_profile_id", pid); put("p_friendship_id", id) }) }
    suspend fun unblock(pid: String, id: String) { rpc("social_unblock", buildJsonObject { put("p_profile_id", pid); put("p_friendship_id", id) }) }

    suspend fun lists(pid: String): FriendLists {
        val rows = list<FriendRow>(rpc("social_list", buildJsonObject { put("p_profile_id", pid) }))
        fun ts(s: String?) = parseTime(s) ?: 0L
        val friends = rows.filter { it.direction == "friend" }.sortedWith { a, b ->
            val ta = ts(a.lastMessageAt); val tb = ts(b.lastMessageAt)
            when {
                ta != tb -> tb.compareTo(ta)
                a.isLive() != b.isLive() -> if (a.isLive()) -1 else 1
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }
        fun byDate(d: String) = rows.filter { it.direction == d }.sortedByDescending { ts(it.createdAt) }
        return FriendLists(friends, byDate("incoming"), byDate("outgoing"), byDate("blocked"))
    }

    // ---------- "assistindo agora" ----------
    private var presenceProfile: String? = null
    private var presenceEnabled: Boolean? = null
    private var lastSent = 0L

    private suspend fun presenceOn(pid: String): Boolean {
        if (presenceProfile != pid) { presenceProfile = pid; presenceEnabled = null; lastSent = 0 }
        if (presenceEnabled == null) {
            presenceEnabled = try { val s = settings(pid); s.showWatching == true && s.friendsEnabled == true } catch (e: Exception) { false }
        }
        return presenceEnabled == true
    }

    suspend fun announceWatching(pid: String, mediaId: Int, type: String, title: String?, poster: String?, season: Int?, episode: Int?, force: Boolean = false) {
        if (!presenceOn(pid)) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSent < 45_000) return
        lastSent = now
        runCatching {
            rpc("social_set_watching", buildJsonObject {
                put("p_profile_id", pid); put("p_media_id", mediaId); put("p_media_type", type)
                put("p_title", title ?: "Sem título"); put("p_poster_path", poster)
                put("p_season", season); put("p_episode", episode)
            })
        }
    }

    suspend fun stopWatching() {
        val pid = presenceProfile ?: return
        if (presenceEnabled != true) return
        lastSent = 0
        runCatching { rpc("social_clear_watching", buildJsonObject { put("p_profile_id", pid) }) }
    }

    // ---------- mensagens ----------
    suspend fun sendMessage(pid: String, friendshipId: String, body: String, kind: String = "text", payload: JsonObject? = null) {
        rpc("social_send_message", buildJsonObject {
            put("p_profile_id", pid); put("p_friendship_id", friendshipId); put("p_body", body); put("p_kind", kind)
            if (payload != null) put("p_payload", payload)
        })
    }

    suspend fun messages(friendshipId: String, before: String? = null, limit: Int = 50): List<FriendMessage> {
        val extra = if (before != null) "&created_at=lt.${java.net.URLEncoder.encode(before, "UTF-8")}" else ""
        val text = try {
            Api.rest("GET", "friend_messages?select=*&friendship_id=eq.$friendshipId$extra&order=created_at.desc&limit=$limit")
        } catch (e: ApiException) { throw friendly(e) }
        return list<FriendMessage>(text).reversed()
    }

    suspend fun markRead(pid: String, friendshipId: String) {
        rpc("social_mark_read", buildJsonObject { put("p_profile_id", pid); put("p_friendship_id", friendshipId) })
    }

    suspend fun unreadTotal(pid: String): Int =
        rpc("social_unread_total", buildJsonObject { put("p_profile_id", pid) }).trim().trim('"').toDoubleOrNull()?.toInt() ?: 0

    suspend fun notifications(pid: String): List<FriendNotification> {
        val arr = Api.json.parseToJsonElement(rpc("social_get_notifications", buildJsonObject { put("p_profile_id", pid) })) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el.jsonObject
            FriendNotification(
                id = o["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                kind = o["kind"]?.jsonPrimitive?.contentOrNull ?: "",
                friendshipId = o["friendship_id"]?.jsonPrimitive?.contentOrNull ?: "",
                fromName = o["from_name"]?.jsonPrimitive?.contentOrNull ?: "Alguém",
                fromAvatar = o["from_avatar"]?.jsonPrimitive?.contentOrNull,
                fromColor = o["from_color"]?.jsonPrimitive?.contentOrNull,
                createdAt = o["created_at"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    suspend fun markNotificationsRead(pid: String) {
        rpc("social_mark_notifications_read", buildJsonObject { put("p_profile_id", pid) })
    }
}
