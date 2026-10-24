package com.alx.test

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WebViewExtractor(private val context: Context) {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun extract(embedUrl: String, log: (String) -> Unit): String? = suspendCancellableCoroutine { continuation ->
        
        val container = FrameLayout(context)
        container.layoutParams = FrameLayout.LayoutParams(1, 1)
        container.x = -1000f
        container.y = -1000f
        
        val webView = WebView(context)
        container.addView(webView)
        
        val rootView = (context as? android.app.Activity)?.window?.decorView as? android.view.ViewGroup
        rootView?.addView(container)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; TECNO KI5k) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        var resolved = false
        val handler = Handler(Looper.getMainLooper())
        
        fun cleanup() {
            if (!resolved) {
                resolved = true
                handler.removeCallbacksAndMessages(null)
                rootView?.removeView(container)
                webView.destroy()
            }
        }

        // Timeout geral de 90s (se não achar o m3u8, desiste)
        handler.postDelayed({
            if (!resolved) {
                log("⏰ Timeout de 90s atingido.")
                cleanup()
                continuation.resume(null)
            }
        }, 90000)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                log("📄 Página carregada. Iniciando loop de clique...")

                // Loop de clique: tenta a cada 1s por 20s
                val js = """
                    (function() {
                        var attempts = 0;
                        var maxAttempts = 20;
                        var interval = setInterval(function() {
                            attempts++;
                            var selectors = ['#bigPlay', '.jw-bigplay', 'button[aria-label="Play"]', 'video', '.play-button', '[class*="play"]'];
                            for (var i = 0; i < selectors.length; i++) {
                                var el = document.querySelector(selectors[i]);
                                if (el) {
                                    el.click();
                                    console.log('CLICOU: ' + selectors[i]);
                                    clearInterval(interval);
                                    return;
                                }
                            }
                            if (attempts >= maxAttempts) {
                                clearInterval(interval);
                                console.log('DESISTIU: botão não apareceu');
                            }
                        }, 1000);
                    })();
                """.trimIndent()

                view?.evaluateJavascript(js, null)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                
                if (url.contains(".m3u8") && !resolved) {
                    log("🎯 M3U8 capturado: ${url.take(80)}...")
                    cleanup()
                    continuation.resume(url)
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        log("🌐 WebView carregando: $embedUrl")
        webView.loadUrl(embedUrl)
    }
}
