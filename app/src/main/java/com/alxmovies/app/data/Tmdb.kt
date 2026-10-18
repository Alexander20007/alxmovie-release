package com.alxmovies.app.data

import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object Tmdb {
    private suspend fun paged(path: String, params: Map<String, Any?> = emptyMap()): Paged =
        Api.json.decodeFromString<Paged>(Api.tmdb(path, params))

    suspend fun trending(type: String = "movie", window: String = "week"): Paged =
        paged("/trending/$type/$window")

    suspend fun popular(type: String = "movie", page: Int = 1): Paged =
        paged("/$type/popular", mapOf("page" to page))

    suspend fun byGenre(type: String, genreId: Int, page: Int = 1): Paged =
        paged("/discover/$type", mapOf("with_genres" to genreId, "page" to page))

    suspend fun recommendations(type: String, id: Int, page: Int = 1): Paged =
        paged("/$type/$id/recommendations", mapOf("page" to page))

    suspend fun genres(type: String = "movie"): List<Genre> =
        Api.json.decodeFromString<GenreList>(Api.tmdb("/genre/$type/list")).genres
}

suspend fun Tmdb.details(type: String, id: Int): MediaDetails {
    val append = if (type == "movie") "similar,release_dates" else "similar,content_ratings"
    val d = Api.json.decodeFromString<MediaDetails>(Api.tmdb("/$type/$id", mapOf("append_to_response" to append)))
    if (!d.overview.isNullOrBlank()) return d
    // sem sinopse em português: tenta em inglês
    val fb = runCatching {
        Api.json.decodeFromString<MediaDetails>(Api.tmdb("/$type/$id", mapOf("language" to "en-US"))).overview
    }.getOrNull()
    return if (!fb.isNullOrBlank()) d.copy(overview = fb) else d
}

suspend fun Tmdb.season(tvId: Int, season: Int): List<Episode> =
    Api.json.decodeFromString<SeasonDetail>(Api.tmdb("/tv/$tvId/season/$season")).episodes

suspend fun Tmdb.search(query: String, type: String = "multi", page: Int = 1): Paged =
    Api.json.decodeFromString<Paged>(Api.tmdb("/search/$type", mapOf("query" to query, "page" to page)))

/** Só o básico do título (gêneros etc.), sem os extras dos detalhes. */
suspend fun Tmdb.basic(type: String, id: Int): MediaDetails =
    Api.json.decodeFromString<MediaDetails>(Api.tmdb("/$type/$id"))

suspend fun Tmdb.trailerKey(type: String, id: Int): String? {
    val text = Api.tmdb("/$type/$id/videos", mapOf("include_video_language" to "pt,en,null"))
    val list = (Api.json.parseToJsonElement(text).jsonObject["results"] as? kotlinx.serialization.json.JsonArray) ?: return null
    data class V(val key: String, val type: String, val official: Boolean, val lang: String?)
    val vids = list.mapNotNull { el ->
        val o = el.jsonObject
        if (o["site"]?.jsonPrimitive?.contentOrNull != "YouTube") return@mapNotNull null
        V(
            o["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
            o["type"]?.jsonPrimitive?.contentOrNull ?: "",
            o["official"]?.jsonPrimitive?.booleanOrNull == true,
            o["iso_639_1"]?.jsonPrimitive?.contentOrNull,
        )
    }
    val trailers = vids.filter { it.type == "Trailer" }.ifEmpty { vids }
    return (trailers.firstOrNull { it.lang == "pt" } ?: trailers.firstOrNull { it.official } ?: trailers.firstOrNull())?.key
}
