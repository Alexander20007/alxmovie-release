package com.alxmovies.app.data

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Calendar
import java.util.TimeZone

/** Restrição de horário do perfil, usando a hora do servidor (a criança não burla mudando o relógio). */
object Parental {
    suspend fun nowUtcMinutes(): Int = try {
        val text = Api.rest("POST", "rpc/get_server_time", "{}")
        val s = Api.json.parseToJsonElement(text).jsonPrimitive.content
        val t = OffsetDateTime.parse(s).withOffsetSameInstant(ZoneOffset.UTC)
        t.hour * 60 + t.minute
    } catch (e: Exception) {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    private fun parseUtcOffset(str: String?): Int? {
        val m = Regex("^([+-])(\\d{2})(\\d{2})$").find((str ?: "").trim()) ?: return null
        val sign = if (m.groupValues[1] == "-") -1 else 1
        return sign * (m.groupValues[2].toInt() * 60 + m.groupValues[3].toInt())
    }

    /** Fuso pelo IP (cache de 24h); se falhar, usa o fuso do aparelho. */
    suspend fun tzOffsetMinutes(): Int {
        val s = Graph.session
        val dayMs = 24L * 60 * 60 * 1000
        if (s.tzOffsetMin != Int.MIN_VALUE && System.currentTimeMillis() - s.tzAt < dayMs) return s.tzOffsetMin
        return try {
            val text = Api.simpleGet("https://ipapi.co/json/")
            val off = parseUtcOffset(Api.json.parseToJsonElement(text).jsonObject["utc_offset"]?.jsonPrimitive?.content)
                ?: throw IllegalStateException("sem fuso")
            s.tzOffsetMin = off
            s.tzAt = System.currentTimeMillis()
            off
        } catch (e: Exception) {
            TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
        }
    }

    private fun toMin(hhmm: String?): Int? {
        val m = Regex("^(\\d{1,2}):(\\d{2})").find((hhmm ?: "").trim()) ?: return null
        return m.groupValues[1].toInt() * 60 + m.groupValues[2].toInt()
    }

    fun isWithin(p: Profile, utcMinutes: Int, offsetMinutes: Int): Boolean {
        if (p.timeRestrictionEnabled != true) return true
        val start = toMin(p.allowedStartTime) ?: return true
        val end = toMin(p.allowedEndTime) ?: return true
        val now = (((utcMinutes + offsetMinutes) % 1440) + 1440) % 1440
        return if (start <= end) now in start..end else (now >= start || now <= end)
    }

    fun window(p: Profile): String? =
        if (p.timeRestrictionEnabled == true && p.allowedStartTime != null && p.allowedEndTime != null)
            "${p.allowedStartTime.take(5)} – ${p.allowedEndTime.take(5)}" else null
}
