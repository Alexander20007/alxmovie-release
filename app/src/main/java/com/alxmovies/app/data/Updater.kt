package com.alxmovies.app.data

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class UpdateInfo(val tag: String, val number: Int, val apkUrl: String?, val pageUrl: String, val notes: String)

/** Confere no GitHub (Releases) se existe uma versão mais nova que a instalada. */
object Updater {
    private const val RELEASE_API = "https://api.github.com/repos/Alexander20007/alxmovies-tv/releases/latest"

    @Suppress("DEPRECATION")
    fun installedCode(ctx: Context): Long = try {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) p.longVersionCode else p.versionCode.toLong()
    } catch (e: Exception) { 0L }

    suspend fun check(ctx: Context): UpdateInfo? = try {
        val (code, text) = Api.getRaw(RELEASE_API, 15)
        if (code !in 200..299) null else {
            val o = Api.json.parseToJsonElement(text).jsonObject
            val tag = o["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
            // tags no formato v2.0.N: N é o número da compilação (igual ao versionCode do APK)
            val n = Regex("(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
            val apk = (o["assets"] as? JsonArray)?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true }
                ?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            if (n > installedCode(ctx)) UpdateInfo(tag, n, apk, o["html_url"]?.jsonPrimitive?.contentOrNull ?: "", o["body"]?.jsonPrimitive?.contentOrNull ?: "") else null
        }
    } catch (e: Exception) { null }
}
