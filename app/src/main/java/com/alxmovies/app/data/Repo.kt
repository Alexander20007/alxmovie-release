package com.alxmovies.app.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Repo {
    suspend fun fetchProfiles(userId: String): List<Profile> =
        Api.json.decodeFromString<List<Profile>>(
            Api.rest("GET", "profiles?select=*&user_id=eq.$userId&order=created_at.asc")
        )

    suspend fun createProfile(
        name: String,
        age: Int?,
        isKids: Boolean,
        wantsAdmin: Boolean,
        maturity: String,
        pin: String?,
        color: String?,
        birthday: String?,
    ): Profile {
        val uid = Graph.session.userId ?: throw ApiException("Usuário não autenticado")
        val existing = fetchProfiles(uid)
        // O primeiro perfil é sempre o responsável; perfil infantil nunca é. Só existe UM responsável.
        val isAdmin = if (isKids) false else wantsAdmin || existing.isEmpty()
        if (isAdmin && existing.any { it.admin }) {
            Api.rest("PATCH", "profiles?user_id=eq.$uid&is_admin=eq.true", """{"is_admin":false}""")
        }
        val body = buildJsonObject {
            put("user_id", uid)
            put("name", name)
            put("avatar", JsonNull)
            put("is_kids", isKids)
            put("maturity_rating", maturity)
            put("pin", pin)
            put("age", age)
            put("color_theme", color)
            put("is_admin", isAdmin)
            put("birthday", birthday)
        }.toString()
        val created = Api.json.decodeFromString<List<Profile>>(Api.rest("POST", "profiles", body, returnRep = true))
        return created.first()
    }

    private suspend fun history(profileId: String, limit: Int): List<HistoryItem> =
        Api.json.decodeFromString<List<HistoryItem>>(
            Api.rest("GET", "watch_history?select=*&profile_id=eq.$profileId&order=watched_at.desc&limit=$limit")
        )

    suspend fun fetchContinueWatching(profileId: String, limit: Int = 20): List<HistoryItem> =
        history(profileId, limit).filter { (it.progress ?: 0.0) < 95 }

    suspend fun fetchWatched(profileId: String, limit: Int = 20): List<HistoryItem> =
        history(profileId, limit * 3).filter { (it.progress ?: 0.0) >= 95 }.take(limit)

    suspend fun fetchMyList(profileId: String): List<FavoriteItem> =
        Api.json.decodeFromString<List<FavoriteItem>>(
            Api.rest("GET", "favorites?select=id,profile_id,media_id,media_type,title,poster_path&profile_id=eq.$profileId&order=added_at.desc")
        )
}

suspend fun Repo.isFavorite(profileId: String, mediaId: Int, type: String): Boolean {
    val text = Api.rest("GET", "favorites?select=id&profile_id=eq.$profileId&media_id=eq.$mediaId&media_type=eq.$type&limit=1")
    return (Api.json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonArray)?.isNotEmpty() == true
}

suspend fun Repo.addFavorite(profileId: String, type: String, m: MediaDetails) {
    val uid = Graph.session.userId ?: throw ApiException("Usuário não autenticado")
    val body = buildJsonObject {
        put("user_id", uid)
        put("profile_id", profileId)
        put("media_id", m.id)
        put("media_type", type)
        put("movie_data", buildJsonObject {
            put("id", m.id)
            put("title", m.title)
            put("name", m.name)
            put("overview", m.overview)
            put("poster_path", m.posterPath)
            put("backdrop_path", m.backdropPath)
            put("vote_average", m.voteAverage)
        })
        put("title", m.displayTitle)
        put("poster_path", m.posterPath)
        put("overview", m.overview)
        put("vote_average", m.voteAverage)
        put("backdrop_path", m.backdropPath)
        put("release_date", m.releaseDate ?: m.firstAirDate)
        put("genre_ids", m.genres?.map { it.id }?.joinToString(prefix = "[", postfix = "]", separator = ","))
        put("added_at", java.time.Instant.now().toString())
    }.toString()
    Api.rest("POST", "favorites", body)
}

suspend fun Repo.removeFavorite(profileId: String, mediaId: Int, type: String) {
    Api.rest("DELETE", "favorites?profile_id=eq.$profileId&media_id=eq.$mediaId&media_type=eq.$type")
}

suspend fun Repo.saveProgress(profileId: String, mediaId: Int, type: String, title: String, posterPath: String?, percent: Double) {
    val uid = Graph.session.userId ?: throw ApiException("Usuário não autenticado")
    val body = buildJsonObject {
        put("user_id", uid)
        put("profile_id", profileId)
        put("media_id", mediaId)
        put("media_type", type)
        put("title", title)
        put("poster_path", posterPath)
        put("progress", Math.round(percent.coerceIn(0.0, 100.0)).toInt())
        put("watched_at", java.time.Instant.now().toString())
    }.toString()
    Api.rest(
        "POST", "watch_history?on_conflict=profile_id,media_id,media_type", body,
        prefer = "resolution=merge-duplicates,return=minimal",
    )
}

suspend fun Repo.updateProfile(profileId: String, updates: kotlinx.serialization.json.JsonObject) {
    val uid = Graph.session.userId ?: throw ApiException("Usuário não autenticado")
    val isAdmin = (updates["is_admin"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toBooleanStrictOrNull()
    if (isAdmin == false) {
        val all = fetchProfiles(uid)
        if (all.none { it.id != profileId && it.admin }) throw ApiException("Precisa deixar pelo menos um perfil como responsável.")
    }
    if (isAdmin == true) Api.rest("PATCH", "profiles?user_id=eq.$uid&is_admin=eq.true", """{"is_admin":false}""")
    Api.rest("PATCH", "profiles?id=eq.$profileId", updates.toString())
}

suspend fun Repo.deleteProfile(profileId: String) {
    val uid = Graph.session.userId ?: throw ApiException("Usuário não autenticado")
    val all = fetchProfiles(uid)
    val target = all.firstOrNull { it.id == profileId }
    if (target?.admin == true && all.none { it.id != profileId && it.admin }) {
        throw ApiException("Esse é o único perfil responsável da conta — marque outro como responsável antes de apagar este.")
    }
    Api.rest("DELETE", "profiles?id=eq.$profileId")
}
