package com.alxmovies.app.data

data class EpRef(val season: Int, val episode: Int)

object MediaUtil {
    /** Temporadas com episódios (sem os "Especiais", a não ser que sejam as únicas). */
    fun seasonsList(m: MediaDetails): List<SeasonInfo> {
        val all = (m.seasons ?: emptyList()).filter { it.episodeCount > 0 }
        val regular = all.filter { it.seasonNumber > 0 }
        return regular.ifEmpty { all }
    }

    fun seasonLabel(n: Int) = if (n == 0) "Especiais" else "Temporada $n"

    fun key(kind: String, profileId: String, type: String, id: Int, season: Int? = null, episode: Int? = null): String {
        val base = "${kind}_${profileId}_${type}_$id"
        return if (type == "tv" && season != null) "${base}_${season}_$episode" else base
    }

    fun parseEp(s: String?): EpRef? {
        val p = s?.split(":") ?: return null
        val a = p.getOrNull(0)?.toIntOrNull() ?: return null
        val b = p.getOrNull(1)?.toIntOrNull() ?: return null
        return EpRef(a, b)
    }

    private val US_MOVIE = mapOf("G" to "L", "PG" to "10", "PG-13" to "12", "R" to "16", "NC-17" to "18")
    private val US_TV = mapOf("TV-Y" to "L", "TV-G" to "L", "TV-Y7" to "10", "TV-PG" to "10", "TV-14" to "14", "TV-MA" to "18")

    private fun normalize(v: String?): String? {
        if (v.isNullOrBlank()) return null
        val t = v.trim().uppercase()
        if (t == "L" || t == "LIVRE") return "L"
        val n = t.filter { it.isDigit() }.toIntOrNull()
        return if (n in listOf(10, 12, 14, 16, 18)) n.toString() else null
    }

    /** Classificação indicativa (Brasil primeiro; senão converte a americana). */
    fun certification(m: MediaDetails, type: String): String? {
        if (type == "movie") {
            val list = m.releaseDates?.results ?: emptyList()
            fun pick(iso: String) = list.firstOrNull { it.iso == iso }?.releaseDates?.map { it.certification }?.firstOrNull { !it.isNullOrBlank() }
            normalize(pick("BR"))?.let { return it }
            pick("US")?.let { us -> US_MOVIE[us]?.let { return it } }
        } else {
            val list = m.contentRatings?.results ?: emptyList()
            normalize(list.firstOrNull { it.iso == "BR" }?.rating)?.let { return it }
            list.firstOrNull { it.iso == "US" }?.rating?.let { us -> US_TV[us]?.let { return it } }
        }
        return if (m.adult == true) "18" else null
    }
}
