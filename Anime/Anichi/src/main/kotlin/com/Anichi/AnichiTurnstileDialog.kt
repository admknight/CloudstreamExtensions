package com.Anichi

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.api.Log

/**
 * Full-screen BottomSheet that loads [targetUrl] (the episode page on allmanga.to) in a real
 * WebView. The site's own JavaScript handles the Cloudflare Turnstile challenge and then POSTs
 * to api.allanime.day. A fetch/XHR interceptor injected into the page captures that raw API
 * response body (which contains the encrypted `tobeparsed` field) and returns it via [onFinished].
 *
 * [onFinished] is called with:
 *  - the raw JSON response body when successfully intercepted, or
 *  - null on timeout or user dismissal.
 */
class AnichiTurnstileDialog(
    private val targetUrl: String,
    private val onFinished: ((String?) -> Unit)? = null
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "AnichiTurnstileDialog"
        private const val TIMEOUT_MS = 60_000L
    }

    private var webView: WebView? = null
    private var statusText: TextView? = null
    private var progressBar: ProgressBar? = null
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var responseSaved = false

    // ── JavaScript bridge ───────────────────────────────────────────────────────

    inner class ApiResponseBridge {
        @JavascriptInterface
        fun onApiResponse(body: String) {
            if (responseSaved) return
            // Only accept responses that look like an episode API reply
            if (body.contains("tobeparsed") || body.contains("\"sourceUrls\"")) {
                Log.d(TAG, "✅ API response intercepted from WebView (${body.length} bytes)")
                handler.post { saveResponseAndDismiss(body) }
            }
        }
    }

    /**
     * Injected into the WebView page to wrap window.fetch and XMLHttpRequest.
     * Any call whose URL contains "allanime.day" has its response forwarded to
     * the Android bridge [ApiResponseBridge.onApiResponse].
     * The guard flag `__anichiHooked` prevents double-injection on re-fires.
     */
    private val interceptorJs = """
        (function() {
            if (window.__anichiHooked) return;
            window.__anichiHooked = true;

            // ── Fetch interceptor ──────────────────────────────────────────────
            var _origFetch = window.fetch;
            window.fetch = function() {
                var args = Array.prototype.slice.call(arguments);
                var req  = args[0];
                var url  = (typeof req === 'string') ? req : (req ? req.url : '');
                return _origFetch.apply(this, args).then(function(resp) {
                    if (url && url.indexOf('allanime.day') !== -1) {
                        resp.clone().text().then(function(body) {
                            if (window.AnichiApiBridge) {
                                window.AnichiApiBridge.onApiResponse(body);
                            }
                        });
                    }
                    return resp;
                });
            };

            // ── XHR interceptor ────────────────────────────────────────────────
            var _origOpen = XMLHttpRequest.prototype.open;
            var _origSend = XMLHttpRequest.prototype.send;

            XMLHttpRequest.prototype.open = function(method, url) {
                this._capturedUrl = url;
                _origOpen.apply(this, arguments);
            };

            XMLHttpRequest.prototype.send = function() {
                var self = this;
                this.addEventListener('load', function() {
                    if (self._capturedUrl &&
                        self._capturedUrl.indexOf('allanime.day') !== -1 &&
                        window.AnichiApiBridge) {
                        window.AnichiApiBridge.onApiResponse(self.responseText || '');
                    }
                });
                _origSend.apply(this, arguments);
            };
        })();
    """.trimIndent()

    // ── Timeout ─────────────────────────────────────────────────────────────────

    private val timeoutRunnable = Runnable {
        if (responseSaved || !isAdded) return@Runnable
        updateStatus("⏱️ Timed out — no API response intercepted.")
        handler.postDelayed({
            if (isAdded && !responseSaved) {
                onFinished?.invoke(null)
                dismissAllowingStateLoss()
            }
        }, 1_500L)
    }

    // ── Dialog / View setup ─────────────────────────────────────────────────────

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        sheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        sheet?.requestLayout()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val screenH = requireContext().resources.displayMetrics.heightPixels
        val wvHeight = (screenH * 0.82).toInt()

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(TextView(requireContext()).apply {
            text = "🛡️ Anichi Security Check"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 8)
        })

        statusText = TextView(requireContext()).apply {
            text = "Loading episode page…"
            textSize = 13f
            setTextColor(Color.parseColor("#A0A0B0"))
            setPadding(0, 0, 0, 4)
        }
        root.addView(statusText)

        root.addView(TextView(requireContext()).apply {
            text = "Solve the security check if prompted. Dialog closes automatically once the episode loads."
            textSize = 11f
            setTextColor(Color.parseColor("#707080"))
            setPadding(0, 0, 0, 12)
        })

        progressBar = ProgressBar(
            requireContext(), null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = 12 }
        }
        root.addView(progressBar)

        val wvContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                wvHeight
            )
        }
        webView = buildWebView()
        wvContainer.addView(
            webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        root.addView(wvContainer)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
            flush()
        }
        webView?.loadUrl(targetUrl)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWebView(): WebView {
        val wv = WebView(requireContext())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowContentAccess = true
            allowFileAccess = true
            loadsImagesAutomatically = true
        }

        // Register BEFORE loading any URL so the bridge is available from page start
        wv.addJavascriptInterface(ApiResponseBridge(), "AnichiApiBridge")

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (!responseSaved) updateStatus("Loading… $newProgress%")
            }
        }

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = false

            override fun onPageStarted(
                view: WebView?, url: String?, favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                // Inject as early as possible — before the page's own scripts execute
                view?.evaluateJavascript(interceptorJs, null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (responseSaved) return
                // Re-inject after page finishes in case the early injection was pre-empted
                view?.evaluateJavascript(interceptorJs, null)
                updateStatus("✏️ Page ready — waiting for episode API call…")
            }
        }
        return wv
    }

    // ── Result helpers ──────────────────────────────────────────────────────────

    private fun saveResponseAndDismiss(body: String) {
        if (responseSaved) return
        responseSaved = true
        handler.removeCallbacks(timeoutRunnable)
        updateStatus("✅ Response captured!")
        webView?.postDelayed({
            if (isAdded) {
                onFinished?.invoke(body)
                dismissAllowingStateLoss()
            }
        }, 800L)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        if (!responseSaved) {
            handler.removeCallbacks(timeoutRunnable)
            onFinished?.invoke(null)
        }
    }

    private fun updateStatus(msg: String) {
        activity?.runOnUiThread {
            statusText?.text = msg
            if (msg.startsWith("✅")) {
                progressBar?.visibility = View.GONE
                statusText?.setTextColor(Color.parseColor("#4CAF50"))
            } else {
                progressBar?.visibility = View.VISIBLE
                statusText?.setTextColor(Color.parseColor("#A0A0B0"))
            }
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(timeoutRunnable)
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroyView()
    }
}
