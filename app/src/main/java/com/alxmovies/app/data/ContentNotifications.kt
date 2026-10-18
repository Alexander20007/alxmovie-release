package com.alxmovies.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.OffsetDateTime

data class ContentNotification(val kind: String, val icon: String, val text: String, val id: Int, val type: String, val poster: String?)

/** Avisos feitos a partir do que a pessoa assiste e favorita ("você parou em…", "a sequência já saiu"). */
object ContentNotifications {
    private data class Alert(val source: String, val id: Int, val title: String, val poster: String?)

    private suspend fun sequelAlerts(pid: String): List<Alert> {
        val favorites = runCatching { Repo.fetchMyList(pid) }.getOrDefault(emptyList())
        val movies = favorites.filter { it.mediaType == "movie" }.take(15)
        val known = favorites.map { it.mediaId }.toSet()
        val today = LocalDate.now().toString()
        val alerts = mutableListOf<Alert>()
        val sem = Semaphore(5)
        coroutineScope {
            movies.map { fav ->
                async {
                    sem.withPermit {
                        runCatching {
                            val d = Tmdb.details("movie", fav.mediaId)
                            val colId = d.collection?.id ?: return@runCatching
                            val col = Api.json.parseToJsonElement(Api.tmdb("/collection/$colId")).jsonObject
                            (col["parts"] as? JsonArray)?.forEach { el ->
                                val p = el.jsonObject
                                val id = p["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@forEach
                                val rel = p["release_date"]?.jsonPrimitive?.contentOrNull
                                if (id == fav.mediaId || id in known || rel.isNullOrEmpty()) return@forEach
                                val mine = d.releaseDate
                                if (!mine.isNullOrEmpty() && rel <= mine) return@forEach
                                if (rel > today) return@forEach
                                synchronized(alerts) {
                                    alerts.add(Alert(fav.title ?: "", id, p["title"]?.jsonPrimitive?.contentOrNull ?: "", p["poster_path"]?.jsonPrimitive?.contentOrNull))
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        return alerts
    }

    suspend fun get(pid: String): List<ContentNotification> {
        val sequels = runCatching { sequelAlerts(pid) }.getOrDefault(emptyList())
        val watching = runCatching { Repo.fetchContinueWatching(pid) }.getOrDefault(emptyList())
        val out = mutableListOf<ContentNotification>()
        val last = watching.firstOrNull()
        val at = last?.watchedAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        if (last != null && at != null && (System.currentTimeMillis() - at) / 86_400_000.0 >= 1) {
            out.add(ContentNotification("stalled", "▶", "Você parou em \"${last.title}\" — continuar de onde ficou?", last.mediaId, last.mediaType, last.posterPath))
        }
        sequels.forEach {
            out.add(ContentNotification("sequel", "🎬", "\"${it.title}\" já saiu — a sequência de \"${it.source}\" que você favoritou.", it.id, "movie", it.poster))
        }
        return out
    }
}
