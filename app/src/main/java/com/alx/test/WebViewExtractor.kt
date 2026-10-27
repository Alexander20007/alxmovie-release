package com.alx.test

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebChromeClient
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
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
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
                log("📄 Página carregada. Iniciando clique cirúrgico...")

                // JS pra clicar no botão correto que monta o iframe
                val js = """
                    (function() {
                        var attempts = 0;
                        var interval = setInterval(function() {
                            attempts++;
                            // Procura o elemento que tem o atributo data-playback-source
                            var btn = document.querySelector('[data-playback-source]');
                            if (btn) {
                                // O site espera o evento 'click', então vamos disparar ele
                                btn.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
                                console.log('CLICOU NO BOTAO CORRETO!');
                                clearInterval(interval);
                                return;
                            }
                            if (attempts > 30) {
                                clearInterval(interval);
                                console.log('DESISTIU: botao data-playback-source nao encontrado');
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

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                log("🖥️ JS: ${consoleMessage?.message()}")
                return true
            }
        }

        log("🌐 WebView carregando: $embedUrl")
        webView.loadUrl(embedUrl)
    }
}
