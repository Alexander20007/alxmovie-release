package com.alxmovies.app.data

object Config {
    const val SUPABASE_URL = "https://onqxflwfkexitipylixc.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_P6xFktXhhIjgC4hVxD6FxA_EbPl-clT"
    const val TMDB_FUNCTION_URL = "$SUPABASE_URL/functions/v1/bright-handler"
    const val TMDB_IMG = "https://image.tmdb.org/t/p"
    const val STREAM_BASE_URL = "https://alx-api.onrender.com"
    const val EXTRACTOR_BASE_URL = "https://api-alex-jney.onrender.com"
    const val SITE_ORIGIN = "https://alxmovies.netlify.app"
    const val GUEST_SESSION_MS = 24L * 60 * 60 * 1000
    const val MAX_PROFILES = 5

    fun poster(path: String?, size: String = "w500"): String? = path?.let { "$TMDB_IMG/$size$it" }
    fun backdrop(path: String?, size: String = "w1280"): String? = path?.let { "$TMDB_IMG/$size$it" }
}
