package com.alx.test

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WebViewExtractor(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(embedUrl: String, log: (String) -> Unit): String? = suspendCancellableCoroutine { continuation ->
        
        // Cria o WebView (ele não é adicionado a nenhuma View, então fica invisível)
        val webView = WebView(context)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; TECNO KI5k) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        var resolved = false
        val handler = Handler(Looper.getMainLooper())
        
        // Timeout de 60 segundos
        val timeoutRunnable = Runnable {
            if (!resolved) {
                resolved = true
                log("⏰ Timeout de 60s atingido no WebView")
                webView.destroy()
                continuation.resume(null)
            }
        }
        handler.postDelayed(timeoutRunnable, 60000)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                
                // Se achou o M3U8 e ainda não resolveu
                if (url.contains(".m3u8") && !resolved) {
                    resolved = true
                    handler.removeCallbacks(timeoutRunnable)
                    log("🎯 WebView capturou M3U8: ${url.take(80)}...")
                    
                    // Destroi o webview depois
                    handler.post { webView.destroy() }
                    continuation.resume(url)
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        log("🌐 WebView carregando: $embedUrl")
        webView.loadUrl(embedUrl)
    }
}
