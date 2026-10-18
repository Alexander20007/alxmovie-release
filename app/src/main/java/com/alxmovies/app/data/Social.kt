package com.alxmovies.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class RatingSummary(val liked: Int = 0, val loved: Int = 0, val disliked: Int = 0) {
    val total get() = liked + loved + disliked
}

@Serializable
data class CommentProfile(val name: String? = null, val avatar: String? = null)

@Serializable
data class CommentRow(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    val body: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("parent_comment_id") val parentId: String? = null,
    val profiles: CommentProfile? = null,
)

data class LikeInfo(val count: Int = 0, val byMe: Boolean = false)

/** Avaliações, comentários, curtidas e "marcar como visto". */
object Social {
    suspend fun getRating(pid: String, id: Int, type: String): String? {
        val t = Api.rest("GET", "ratings?select=rating&profile_id=eq.$pid&media_id=eq.$id&media_type=eq.$type&limit=1")
        return (Api.json.parseToJsonElement(t) as? JsonArray)?.firstOrNull()?.jsonObject?.get("rating")?.jsonPrimitive?.contentOrNull
    }

    suspend fun setRating(pid: String, id: Int, type: String, title: String, rating: String) {
        val body = buildJsonObject {
            put("profile_id", pid); put("media_id", id); put("media_type", type); put("title", title); put("rating", rating)
        }.toString()
        Api.rest("POST", "ratings?on_conflict=profile_id,media_id,media_type", body, prefer = "resolution=merge-duplicates,return=minimal")
    }

    suspend fun ratingSummary(id: Int, type: String): RatingSummary {
        val t = Api.rest("GET", "ratings?select=rating&media_id=eq.$id&media_type=eq.$type")
        var l = 0; var lo = 0; var d = 0
        (Api.json.parseToJsonElement(t) as? JsonArray)?.forEach {
            when (it.jsonObject["rating"]?.jsonPrimitive?.contentOrNull) { "liked" -> l++; "loved" -> lo++; "disliked" -> d++ }
        }
        return RatingSummary(l, lo, d)
    }

    suspend fun comments(id: Int, type: String): List<CommentRow> =
        Api.json.decodeFromString<List<CommentRow>>(
            Api.rest("GET", "comments?select=*,profiles(name,avatar)&media_id=eq.$id&media_type=eq.$type&order=created_at.desc")
        )

    suspend fun addComment(pid: String, id: Int, type: String, body: String, parentId: String? = null) {
        val b = buildJsonObject {
            put("profile_id", pid); put("media_id", id); put("media_type", type); put("body", body)
            if (parentId != null) put("parent_comment_id", parentId) else put("parent_comment_id", JsonNull)
        }.toString()
        Api.rest("POST", "comments", b)
    }

    suspend fun deleteComment(commentId: String) { Api.rest("DELETE", "comments?id=eq.$commentId") }

    suspend fun likes(ids: List<String>, pid: String): Map<String, LikeInfo> {
        val info = HashMap<String, LikeInfo>()
        ids.forEach { info[it] = LikeInfo() }
        if (ids.isEmpty()) return info
        val t = Api.rest("GET", "comment_likes?select=comment_id,profile_id&comment_id=in.(${ids.joinToString(",")})")
        (Api.json.parseToJsonElement(t) as? JsonArray)?.forEach {
            val o = it.jsonObject
            val cid = o["comment_id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val cur = info[cid] ?: LikeInfo()
            info[cid] = LikeInfo(cur.count + 1, cur.byMe || o["profile_id"]?.jsonPrimitive?.contentOrNull == pid)
        }
        return info
    }

    suspend fun toggleLike(pid: String, commentId: String) {
        val t = Api.rest("GET", "comment_likes?select=id&comment_id=eq.$commentId&profile_id=eq.$pid&limit=1")
        val existing = (Api.json.parseToJsonElement(t) as? JsonArray)?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        if (existing != null) Api.rest("DELETE", "comment_likes?id=eq.$existing")
        else Api.rest("POST", "comment_likes", buildJsonObject { put("comment_id", commentId); put("profile_id", pid) }.toString())
    }

    suspend fun isMovieWatched(pid: String, id: Int): Boolean {
        val t = Api.rest("GET", "watch_history?select=progress&profile_id=eq.$pid&media_id=eq.$id&media_type=eq.movie&limit=1")
        val p = (Api.json.parseToJsonElement(t) as? JsonArray)?.firstOrNull()?.jsonObject?.get("progress")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 0.0
        return p >= 95
    }

    suspend fun setMovieWatched(pid: String, id: Int, title: String, poster: String?, seen: Boolean) {
        if (seen) Repo.saveProgress(pid, id, "movie", title, poster, 100.0)
        else Api.rest("DELETE", "watch_history?profile_id=eq.$pid&media_id=eq.$id&media_type=eq.movie")
    }
}
