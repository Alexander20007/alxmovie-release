package com.alx.test

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
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
        
        // 1. Cria um container para o WebView (necessário para o Turnstile renderizar)
        val container = FrameLayout(context)
        // Coloca o WebView fora da tela visível, mas com tamanho definido
        container.layoutParams = FrameLayout.LayoutParams(1, 1)
        container.x = -1000f
        container.y = -1000f
        
        val webView = WebView(context)
        container.addView(webView)
        
        // 2. Adiciona o container à janela atual (sem mostrar pro usuário)
        val rootView = (context as? android.app.Activity)?.window?.decorView as? android.view.ViewGroup
        rootView?.addView(container)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        // Manter o User-Agent consistente é crucial para o Turnstile [citation:1]
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; TECNO KI5k) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // Habilita cookies (necessário para o Turnstile) [citation:1]
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

        // Timeout de 90 segundos (Cloudflare + carregamento do player)
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
                log("📄 Página carregada, tentando clicar no play...")

                // 3. Injeção de JavaScript para clicar no botão de play
                // Tenta uma variedade de seletores comuns em players piratas
                val js = """
                    (function() {
                        var selectors = ['#bigPlay', '.jw-bigplay', 'button[aria-label="Play"]', 'video', '.play-button', '[class*="play"]'];
                        for (var i = 0; i < selectors.length; i++) {
                            var el = document.querySelector(selectors[i]);
                            if (el) {
                                el.click();
                                return 'clicked: ' + selectors[i];
                            }
                        }
                        return 'no button found';
                    })();
                """.trimIndent()

                view?.evaluateJavascript(js) { result ->
                    log("🖱️ Resultado do clique: $result")
                }
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

        log("🌐 WebView (anexado) carregando: $embedUrl")
        webView.loadUrl(embedUrl)
    }
}
