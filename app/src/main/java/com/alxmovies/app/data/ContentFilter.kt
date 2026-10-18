package com.alxmovies.app.data

/** Filtro de classificação indicativa por perfil (mesma regra do app web). */
object ContentFilter {
    val RATINGS = listOf("L" to "Livre", "10" to "10 anos", "12" to "12 anos", "14" to "14 anos", "16" to "16 anos", "18" to "18 anos")

    private val GENRE_MIN_RATING = mapOf(27 to 16, 10752 to 16, 10768 to 16, 80 to 14, 53 to 14, 9648 to 12)
    private val KIDS_GENRES = setOf(16, 10751, 10762)

    private fun ratingToNumber(r: String?): Int = when {
        r.isNullOrEmpty() -> 18
        r == "L" -> 0
        else -> r.toIntOrNull() ?: 18
    }

    fun allowed(item: MediaItem, profile: Profile?): Boolean {
        if (profile == null) return true
        val max = ratingToNumber(profile.maturityRating)
        val genres = item.genreIds ?: item.genres?.map { it.id } ?: emptyList()
        if (item.adult == true && max < 18) return false
        if (profile.kids) return genres.any { it in KIDS_GENRES }
        val required = genres.fold(0) { m, g -> maxOf(m, GENRE_MIN_RATING[g] ?: 0) }
        return required <= max
    }

    fun filter(items: List<MediaItem>, profile: Profile?): List<MediaItem> = items.filter { allowed(it, profile) }
}
