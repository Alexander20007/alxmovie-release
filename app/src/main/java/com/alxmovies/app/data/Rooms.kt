package com.alxmovies.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.OffsetDateTime

@Serializable
data class RoomRow(
    val id: String,
    @SerialName("host_user_id") val hostUserId: String = "",
    @SerialName("host_name") val hostName: String = "",
    val title: String = "",
    @SerialName("tmdb_id") val tmdbId: Int = 0,
    @SerialName("media_type") val mediaType: String = "movie",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("movie_title") val movieTitle: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("is_private") val isPrivate: Boolean? = false,
    @SerialName("has_password") val hasPassword: Boolean? = false,
    @SerialName("is_playing") val isPlaying: Boolean? = false,
    @SerialName("position_seconds") val positionSeconds: Double? = 0.0,
    @SerialName("state_updated_at") val stateUpdatedAt: String? = null,
    @SerialName("season_limit") val seasonLimit: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class QueueItem(
    val id: String,
    @SerialName("tmdb_id") val tmdbId: Int = 0,
    @SerialName("media_type") val mediaType: String = "movie",
    val season: Int? = null,
    val episode: Int? = null,
    @SerialName("movie_title") val movieTitle: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("added_by_user_id") val addedByUserId: String = "",
    @SerialName("added_by_name") val addedByName: String = "",
)

@Serializable
data class RoomMessage(
    val id: String,
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    val message: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

data class CreateRoomInput(
    val title: String, val isPrivate: Boolean, val password: String?, val tmdbId: Int, val mediaType: String,
    val season: Int?, val episode: Int?, val movieTitle: String, val posterPath: String?,
    val hostProfileId: String?, val hostName: String, val seasonLimit: Int?,
)

object Rooms {
    private suspend fun rpc(name: String, args: kotlinx.serialization.json.JsonObject): String =
        Api.rest("POST", "rpc/$name", args.toString())

    private inline fun <reified T> list(text: String): List<T> =
        if (text.isBlank() || text.trim() == "null") emptyList() else Api.json.decodeFromString<List<T>>(text)

    suspend fun create(i: CreateRoomInput): RoomRow = Api.json.decodeFromString(rpc("create_watch_room", buildJsonObject {
        put("p_title", i.title)
        put("p_is_private", i.isPrivate)
        put("p_password", if (i.isPrivate) i.password else null)
        put("p_tmdb_id", i.tmdbId)
        put("p_media_type", i.mediaType)
        put("p_season", i.season)
        put("p_episode", i.episode)
        put("p_movie_title", i.movieTitle)
        put("p_poster_path", i.posterPath)
        put("p_host_profile_id", i.hostProfileId)
        put("p_host_name", i.hostName)
        put("p_season_limit", i.seasonLimit)
    }))

    suspend fun join(roomId: String, password: String?, displayName: String): RoomRow =
        Api.json.decodeFromString(rpc("join_watch_room", buildJsonObject {
            put("p_room_id", roomId); put("p_password", password); put("p_display_name", displayName)
        }))

    suspend fun listPublic(): List<RoomRow> = list(Api.rest("GET", "watch_rooms_public?select=*&order=created_at.desc"))

    suspend fun listMyRecent(limit: Int = 12): List<RoomRow> {
        val mem = Api.json.parseToJsonElement(Api.rest("GET", "watch_room_members?select=room_id,joined_at&order=joined_at.desc&limit=${limit * 3}")) as? JsonArray
            ?: return emptyList()
        if (mem.isEmpty()) return emptyList()
        val ids = mem.mapNotNull { it.jsonObject["room_id"]?.jsonPrimitive?.contentOrNull }.distinct()
        val order = ids.withIndex().associate { it.value to it.index }
        val rooms = list<RoomRow>(Api.rest("GET", "watch_rooms_public?select=*&id=in.(${ids.joinToString(",")})"))
        return rooms.sortedBy { order[it.id] ?: Int.MAX_VALUE }.take(limit)
    }

    /** null = a sala acabou (ou foi fechada). */
    suspend fun get(roomId: String): RoomRow? = list<RoomRow>(Api.rest("GET", "watch_rooms_public?select=*&id=eq.$roomId")).firstOrNull()

    suspend fun updateState(roomId: String, playing: Boolean, position: Double) {
        rpc("update_watch_room_state", buildJsonObject { put("p_room_id", roomId); put("p_is_playing", playing); put("p_position_seconds", position) })
    }

    suspend fun advanceEpisode(roomId: String, season: Int, episode: Int) {
        rpc("advance_room_episode", buildJsonObject { put("p_room_id", roomId); put("p_season", season); put("p_episode", episode) })
    }

    suspend fun close(roomId: String) { rpc("close_watch_room", buildJsonObject { put("p_room_id", roomId) }) }

    suspend fun transferHost(roomId: String, newHostUserId: String) {
        rpc("transfer_room_host", buildJsonObject { put("p_room_id", roomId); put("p_new_host_user_id", newHostUserId) })
    }

    suspend fun playFromQueue(queueId: String): RoomRow =
        Api.json.decodeFromString(rpc("play_from_queue", buildJsonObject { put("p_queue_id", queueId) }))

    suspend fun claimHostIfAbandoned(roomId: String, onlineUserIds: List<String>): Boolean {
        val text = rpc("claim_room_host_if_abandoned", buildJsonObject {
            put("p_room_id", roomId)
            put("p_online_user_ids", buildJsonArray { onlineUserIds.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
        })
        return (Api.json.parseToJsonElement(text) as? JsonArray)?.isNotEmpty() == true
    }

    // ---------- fila ----------
    suspend fun queue(roomId: String): List<QueueItem> = list(Api.rest("GET", "watch_room_queue?select=*&room_id=eq.$roomId&order=created_at.asc"))

    suspend fun addToQueue(roomId: String, userId: String, userName: String, tmdbId: Int, type: String, season: Int?, episode: Int?, title: String, poster: String?) {
        Api.rest("POST", "watch_room_queue", buildJsonObject {
            put("room_id", roomId); put("tmdb_id", tmdbId); put("media_type", type); put("season", season); put("episode", episode)
            put("movie_title", title); put("poster_path", poster); put("added_by_user_id", userId); put("added_by_name", userName)
        }.toString())
    }

    suspend fun removeFromQueue(queueId: String) { Api.rest("DELETE", "watch_room_queue?id=eq.$queueId") }

    // ---------- chat ----------
    suspend fun messages(roomId: String, after: String? = null): List<RoomMessage> {
        val q = if (after != null) "&created_at=gt.${java.net.URLEncoder.encode(after, "UTF-8")}&order=created_at.asc&limit=100"
        else "&order=created_at.desc&limit=60"
        val l = list<RoomMessage>(Api.rest("GET", "watch_room_messages?select=*&room_id=eq.$roomId$q"))
        return if (after == null) l.reversed() else l
    }

    suspend fun sendMessage(roomId: String, userId: String, name: String, text: String) {
        Api.rest("POST", "watch_room_messages", buildJsonObject {
            put("room_id", roomId); put("user_id", userId); put("display_name", name); put("message", text)
        }.toString())
    }

    // ---------- relógio do servidor (para estimar a posição do anfitrião) ----------
    @Volatile private var clockOffsetMs: Long? = null

    suspend fun serverNowMs(): Long {
        clockOffsetMs?.let { return System.currentTimeMillis() + it }
        return try {
            val t = Api.json.parseToJsonElement(Api.rest("POST", "rpc/get_server_time", "{}")).jsonPrimitive.content
            val ms = OffsetDateTime.parse(t).toInstant().toEpochMilli()
            clockOffsetMs = ms - System.currentTimeMillis()
            ms
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun parseMs(iso: String?): Long? = try { if (iso == null) null else OffsetDateTime.parse(iso).toInstant().toEpochMilli() } catch (e: Exception) { null }
}
