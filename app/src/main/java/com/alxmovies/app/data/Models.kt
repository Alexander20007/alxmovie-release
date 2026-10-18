package com.alxmovies.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Genre(val id: Int, val name: String)

@Serializable
data class GenreList(val genres: List<Genre> = emptyList())

@Serializable
data class MediaItem(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
    val genres: List<Genre>? = null,
    val adult: Boolean? = null,
    @SerialName("media_type") val mediaType: String? = null,
) {
    val displayTitle: String get() = title ?: name ?: ""
}

@Serializable
data class Paged(
    val page: Int = 1,
    val results: List<MediaItem> = emptyList(),
    @SerialName("total_pages") val totalPages: Int = 1,
)

@Serializable
data class Profile(
    val id: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val avatar: String? = null,
    @SerialName("color_theme") val colorTheme: String? = null,
    @SerialName("is_kids") val isKids: Boolean? = false,
    @SerialName("is_admin") val isAdmin: Boolean? = false,
    @SerialName("maturity_rating") val maturityRating: String? = null,
    val pin: String? = null,
    val age: Int? = null,
    val birthday: String? = null,
    @SerialName("favorite_genres") val favoriteGenres: JsonElement? = null,
    @SerialName("time_restriction_enabled") val timeRestrictionEnabled: Boolean? = false,
    @SerialName("allowed_start_time") val allowedStartTime: String? = null,
    @SerialName("allowed_end_time") val allowedEndTime: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val kids: Boolean get() = isKids == true
    val admin: Boolean get() = isAdmin == true

    /** Gêneros favoritos (o banco pode devolver lista de números; qualquer outro formato vira lista vazia). */
    fun favGenres(): List<Int> =
        (favoriteGenres as? JsonArray)?.mapNotNull { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() } ?: emptyList()
}

@Serializable
data class HistoryItem(
    val id: String? = null,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("media_id") val mediaId: Int,
    @SerialName("media_type") val mediaType: String = "movie",
    val title: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    val progress: Double? = null,
    @SerialName("watched_at") val watchedAt: String? = null,
)

@Serializable
data class FavoriteItem(
    val id: String? = null,
    @SerialName("profile_id") val profileId: String? = null,
    @SerialName("media_id") val mediaId: Int,
    @SerialName("media_type") val mediaType: String = "movie",
    val title: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)

/** Item genérico de pôster usado nas fileiras (vem de TMDB, histórico ou lista). */
data class PosterItem(
    val id: Int,
    val type: String,
    val title: String,
    val poster: String?,
    val progress: Int? = null,
)

fun MediaItem.toPoster(defaultType: String = "movie"): PosterItem = PosterItem(
    id = id,
    type = mediaType?.takeIf { it == "movie" || it == "tv" } ?: defaultType,
    title = displayTitle,
    poster = posterPath,
)

// ---------- Detalhes ----------

@Serializable
data class Episode(
    @SerialName("episode_number") val number: Int,
    val name: String? = null,
    @SerialName("still_path") val still: String? = null,
    val runtime: Int? = null,
    val overview: String? = null,
    @SerialName("air_date") val airDate: String? = null,
)

@Serializable
data class SeasonDetail(val episodes: List<Episode> = emptyList())

@Serializable
data class SeasonInfo(
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("episode_count") val episodeCount: Int = 0,
)

@Serializable
data class ReleaseDateEntry(val certification: String? = null)

@Serializable
data class ReleaseDatesCountry(
    @SerialName("iso_3166_1") val iso: String = "",
    @SerialName("release_dates") val releaseDates: List<ReleaseDateEntry> = emptyList(),
)

@Serializable
data class ReleaseDatesWrap(val results: List<ReleaseDatesCountry> = emptyList())

@Serializable
data class ContentRating(
    @SerialName("iso_3166_1") val iso: String = "",
    val rating: String? = null,
)

@Serializable
data class ContentRatingsWrap(val results: List<ContentRating> = emptyList())

@Serializable
data class CollectionRef(val id: Int)

@Serializable
data class MediaDetails(
    val id: Int,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int>? = null,
    val genres: List<Genre>? = null,
    val seasons: List<SeasonInfo>? = null,
    val adult: Boolean? = null,
    val similar: Paged? = null,
    @SerialName("release_dates") val releaseDates: ReleaseDatesWrap? = null,
    @SerialName("content_ratings") val contentRatings: ContentRatingsWrap? = null,
    @SerialName("belongs_to_collection") val collection: CollectionRef? = null,
) {
    val displayTitle: String get() = title ?: name ?: ""

    fun asItem() = MediaItem(
        id = id, title = title, name = name, overview = overview, posterPath = posterPath, backdropPath = backdropPath,
        releaseDate = releaseDate, firstAirDate = firstAirDate, voteAverage = voteAverage, genres = genres, adult = adult,
    )
}
