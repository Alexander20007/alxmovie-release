package com.alx.test

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var etTmdbId: EditText
    private lateinit var etSeason: EditText
    private lateinit var etEpisode: EditText
    private lateinit var spinnerType: Spinner
    private lateinit var spinnerProvider: Spinner
    private lateinit var layoutSerie: LinearLayout
    private lateinit var tvLogs: TextView
    private lateinit var scrollLogs: ScrollView
    private lateinit var playerView: PlayerView

    private var exoPlayer: ExoPlayer? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val providers = listOf(
        "WatchPlay",
        "VIP Player",
        "StreamX",
        "VidSrcRip",
        "EmbedSu",
        "Blogger (WebView)",
        "SuperFlix (WebView)",
        "Pobreflix (WebView)",
        "VidSrcMe (WebView)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etTmdbId = findViewById(R.id.etTmdbId)
        etSeason = findViewById(R.id.etSeason)
        etEpisode = findViewById(R.id.etEpisode)
        spinnerType = findViewById(R.id.spinnerType)
        spinnerProvider = findViewById(R.id.spinnerProvider)
        layoutSerie = findViewById(R.id.layoutSerie)
        tvLogs = findViewById(R.id.tvLogs)
        scrollLogs = findViewById(R.id.scrollLogs)
        playerView = findViewById(R.id.playerView)
        val btnTest = findViewById<Button>(R.id.btnTest)

        spinnerType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Filme", "Série"))
        spinnerProvider.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, providers)

        spinnerType.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                layoutSerie.visibility = if (position == 1) android.view.View.VISIBLE else android.view.View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        btnTest.setOnClickListener {
            val id = etTmdbId.text.toString().trim()
            if (id.isEmpty()) return@setOnClickListener

            val isSerie = spinnerType.selectedItemPosition == 1
            val season = etSeason.text.toString().toIntOrNull() ?: 1
            val episode = etEpisode.text.toString().toIntOrNull() ?: 1
            val provider = providers[spinnerProvider.selectedItemPosition]

            tvLogs.text = "Logs...\n"
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()

            lifecycleScope.launch {
                log("🚀 Iniciando: $provider | ID: $id")
                try {
                    val m3u8 = when (provider) {
                        "WatchPlay" -> WatchPlayProvider(client).extract(id, isSerie, season, episode, ::log)
                        "VIP Player" -> VipPlayerProvider(client).extractAuto(id, isSerie, season, episode, ::log)
                        "StreamX" -> StreamXProvider(client).extractAuto(id, isSerie, season, episode, ::log)
                        "VidSrcRip" -> VidSrcRipProvider(client).extract(id, isSerie, season, episode, ::log)
                        "Blogger (WebView)" -> extrairBlogger(id, isSerie, season, episode, ::log)
                        "SuperFlix (WebView)" -> WebViewExtractor(this@MainActivity).extract("https://superflixonline.lat/filme/$id", ::log)
                        "Pobreflix (WebView)" -> WebViewExtractor(this@MainActivity).extract("https://filmesbluray.net/filme/$id", ::log)
                        "VidSrcMe (WebView)" -> WebViewExtractor(this@MainActivity).extract("https://vidsrcme.ru/embed/movie/$id", ::log)
                        else -> null
                    }

                    if (m3u8 != null) {
                        log("✅ URL: ${m3u8.take(80)}...")
                        playVideo(m3u8)
                    } else {
                        log("❌ Nenhuma URL encontrada")
                    }
                } catch (e: Exception) {
                    log("❌ Erro: ${e.message}")
                }
            }
        }
    }

    // Busca o link do Blogger no playerflix e chama o WebView
    private suspend fun extrairBlogger(
        id: String,
        isSerie: Boolean,
        season: Int,
        episode: Int,
        log: (String) -> Unit
    ): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val tipo = if (isSerie) "tv" else "movie"
                val s = if (isSerie) season.toString() else "null"
                val e = if (isSerie) episode.toString() else "null"
                val apiUrl = "https://playerflix.ink/inc/Ajax.php?type=$tipo&id=$id&season=$s&episode=$e"

                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Referer", "https://playerflix.ink/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                    .build()

                val response = client.newCall(request).execute()
                val json = JSONObject(response.body?.string() ?: "{}")

                if (!json.has("status") || !json.getBoolean("status")) {
                    log("❌ playerflix não retornou status=true")
                    return@withContext null
                }

                val options = json.getJSONObject("data").getJSONArray("options")
                var embedUrl = ""

                for (i in 0 until options.length()) {
                    val opt = options.getJSONObject(i)
                    if (opt.getString("label") == "Blogger") {
                        embedUrl = opt.getString("embed")
                        break
                    }
                }

                if (embedUrl.isEmpty()) {
                    log("❌ Blogger não encontrado para este ID")
                    return@withContext null
                }

                log("🎯 Link do Blogger: ${embedUrl.take(80)}...")

                // Chama o WebView para carregar o link do Blogger e capturar o googlevideo
                return@withContext WebViewExtractor(this@MainActivity).extract(embedUrl, log)

            } catch (e: Exception) {
                log("❌ Erro ao buscar Blogger: ${e.message}")
                return@withContext null
            }
        }
    }

    private fun log(msg: String) {
        runOnUiThread {
            tvLogs.append("$msg\n")
            scrollLogs.post { scrollLogs.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun playVideo(url: String) {
        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
