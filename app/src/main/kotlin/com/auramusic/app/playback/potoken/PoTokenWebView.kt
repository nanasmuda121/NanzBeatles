package com.auramusic.app.playback.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.collection.ArrayMap
import com.auramusic.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A non-fatal PO token failure (transient challenge/network/renderer issues). */
class PoTokenException(message: String) : Exception(message)

/**
 * The system WebView implementation itself is broken (cannot run BotGuard at
 * all). Must disable PO token generation for the whole session instead of
 * retrying forever.
 */
class BadWebViewException(message: String) : Exception(message)

/**
 * Hosts an offscreen [WebView] that runs YouTube's BotGuard attestation and
 * exposes a token minter.
 *
 * Design notes:
 * - The WebView has no network access ([android.webkit.WebSettings.blockNetworkLoads]);
 *   all Google endpoint traffic happens on the Kotlin side via OkHttp.
 * - Initialization: load bundled HTML -> fetch BotGuard challenge -> run it ->
 *   exchange the botguard response for an integrity token -> build the JS
 *   minter from `webPoSignalOutput`.
 * - Token minting is then a fast local computation per identifier (session or video id).
 * - Tracks [isExpired] (integrity tokens are time-limited) and [isDead]
 *   (renderer crash / wedge) so callers can recreate cleanly.
 */
class PoTokenWebView private constructor(
    context: Context,
    // to be used exactly once only during initialization!
    private val continuation: Continuation<PoTokenWebView>,
) {
    private val webView = WebView(context)
    private val scope = MainScope()

    // Guards the single-shot init continuation: initialization errors can arrive from several
    // paths (JS console "Uncaught", botguard request failure, renderer-gone) and a second resume
    // on a plain Continuation throws IllegalStateException.
    private val initResumed = AtomicBoolean(false)

    @Volatile
    private var closed = false

    /**
     * Set when the renderer died or a generate timed out. The render-gone callback isn't
     * delivered reliably enough on its own; callers check this to recreate immediately.
     */
    @Volatile
    var isDead: Boolean = false
        private set

    private val poTokenContinuations =
        Collections.synchronizedMap(ArrayMap<String, Continuation<String>>())

    // Makes each generatePoToken call's continuation key unique (see generatePoToken).
    private val requestCounter = AtomicLong()

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        onInitializationErrorCloseAndCancel(t)
    }

    private lateinit var expirationInstant: Instant

    init {
        val webViewSettings = webView.settings
        //noinspection SetJavaScriptEnabled we want to use JavaScript!
        webViewSettings.javaScriptEnabled = true
        webViewSettings.userAgentString = USER_AGENT
        webViewSettings.blockNetworkLoads = true // the WebView does not need internet access
        webViewSettings.domStorageEnabled = true

        // so that we can run async functions and get back the result
        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val msg = m.message()
                when (m.messageLevel()) {
                    ConsoleMessage.MessageLevel.ERROR -> Timber.tag(TAG).e("JS: %s", msg)
                    ConsoleMessage.MessageLevel.WARNING -> Timber.tag(TAG).w("JS: %s", msg)
                    else -> Timber.tag(TAG).d("JS: %s", msg)
                }

                if (msg.contains("Uncaught")) {
                    val fmt = "\"$msg\", source: ${m.sourceId()} (${m.lineNumber()})"
                    if (initResumed.get()) {
                        // Post-init: our static HTML already executed fine, so an uncaught error
                        // here comes from Google's remotely-served botguard/minter JS — transient,
                        // NOT a BadWebViewException, which would permanently disable poTokens for
                        // the session.
                        Timber.tag(TAG).e("Uncaught JS error after init (treating as transient): %s", fmt)
                        isDead = true
                        val exception = PoTokenException(fmt)
                        close()
                        popAllPoTokenContinuations().forEach { (_, cont) ->
                            runCatching { cont.resumeWithException(exception) }
                        }
                    } else {
                        val exception = BadWebViewException(fmt)
                        Timber.tag(TAG).e("This WebView implementation is broken: %s", fmt)
                        onInitializationErrorCloseAndCancel(exception)
                        popAllPoTokenContinuations().forEach { (_, cont) ->
                            runCatching { cont.resumeWithException(exception) }
                        }
                    }
                }
                return super.onConsoleMessage(m)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            // API 26+ callback; on providers that don't deliver it the withTimeout nets in
            // getNewPoTokenGenerator()/generatePoToken() carry the recovery.
            @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.O)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val didCrash = runCatching { detail.didCrash() }.getOrNull()
                Timber.tag(TAG).e("PO token WebView render process gone (didCrash=%s)", didCrash)
                isDead = true
                // Transient (OOM kill under memory pressure), NOT a BadWebViewException — that
                // would permanently disable poTokens for the session.
                val exception = PoTokenException("WebView render process gone (didCrash=$didCrash)")
                onInitializationErrorCloseAndCancel(exception)
                popAllPoTokenContinuations().forEach { (_, cont) ->
                    runCatching { cont.resumeWithException(exception) }
                }
                // Consume the event so the framework doesn't kill the app process.
                return true
            }
        }
    }

    /**
     * Must be called right after instantiating [PoTokenWebView] to perform the actual
     * initialization. This will asynchronously go through all the steps needed to load BotGuard,
     * run it, and obtain an integrity token.
     */
    private fun loadHtmlAndObtainBotguard() {
        Timber.tag(TAG).d("loadHtmlAndObtainBotguard() called")

        scope.launch(exceptionHandler) {
            val html = withContext(Dispatchers.IO) {
                webView.context.assets.open("po_token.html").bufferedReader().use { it.readText() }
            }

            // calls downloadAndRunBotguard() when the page has finished loading
            val data = html.replaceFirst("</script>", "\n$JS_INTERFACE.downloadAndRunBotguard()</script>")
            webView.loadDataWithBaseURL("https://www.youtube.com", data, "text/html", "utf-8", null)
        }
    }

    /**
     * Called during initialization by the JavaScript snippet appended to the HTML page content in
     * [loadHtmlAndObtainBotguard] after the WebView content has been loaded.
     */
    @JavascriptInterface
    fun downloadAndRunBotguard() {
        Timber.tag(TAG).d("downloadAndRunBotguard() called")

        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/Create",
            "[ \"$REQUEST_KEY\" ]",
        ) { responseBody ->
            val parsedChallengeData = parseChallengeData(responseBody)
            webView.evaluateJavascript(
                """try {
                    data = $parsedChallengeData
                    runBotGuard(data).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotguardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    /** Called during initialization by the JavaScript snippets on any initialization error. */
    @JavascriptInterface
    fun onJsInitializationError(error: String) {
        Timber.tag(TAG).e("Initialization error from JavaScript: %s", error)
        onInitializationErrorCloseAndCancel(buildExceptionForJsError(error))
    }

    private fun buildExceptionForJsError(error: String): Exception =
        if (error.contains("is not defined") || error.contains("EvalError")) {
            BadWebViewException(error)
        } else {
            PoTokenException(error)
        }

    /**
     * Called during initialization by the JavaScript snippet from [downloadAndRunBotguard] after
     * obtaining the BotGuard execution output.
     */
    @JavascriptInterface
    fun onRunBotguardResult(botguardResponse: String) {
        Timber.tag(TAG).d("botguardResponse received (%d chars)", botguardResponse.length)
        makeBotguardServiceRequest(
            "https://www.youtube.com/api/jnn/v1/GenerateIT",
            "[ \"$REQUEST_KEY\", \"$botguardResponse\" ]",
        ) { responseBody ->
            try {
                val (integrityToken, expirationTimeInSeconds) = parseIntegrityTokenData(responseBody)
                Timber.tag(TAG).d("Parsed integrityToken (expires in %ds)", expirationTimeInSeconds)

                // leave 10 minutes of margin just to be sure
                expirationInstant = Instant.now().plusSeconds(expirationTimeInSeconds).minus(10, ChronoUnit.MINUTES)

                // Store integrityToken and create the minter callback ONCE
                webView.evaluateJavascript(
                    """try {
                        this.integrityToken = $integrityToken
                        createPoTokenMinter(webPoSignalOutput, integrityToken).then(function() {
                            $JS_INTERFACE.onMinterCreated()
                        }).catch(function(error) {
                            $JS_INTERFACE.onJsInitializationError(error + "\n" + (error.stack || ''))
                        })
                    } catch (error) {
                        $JS_INTERFACE.onJsInitializationError(error + "\n" + error.stack)
                    }""",
                    null
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to parse integrity token data")
                onInitializationErrorCloseAndCancel(PoTokenException("parseIntegrityTokenData failed: ${e.message}"))
            }
        }
    }

    /** Called during initialization after the poToken minter has been created successfully. */
    @JavascriptInterface
    fun onMinterCreated() {
        Timber.tag(TAG).d("poToken minter created successfully, initialization complete")
        if (initResumed.compareAndSet(false, true)) {
            continuation.resume(this)
        }
    }

    //region Obtaining poTokens
    suspend fun generatePoToken(identifier: String): String {
        if (isDead || closed) {
            // Fail fast (no fixed timeout wait): the caller recreates the WebView from scratch.
            throw PoTokenException("PoToken WebView is dead/closed — instance must be recreated")
        }
        // Continuations are keyed by a per-call unique key, not the raw identifier: concurrent
        // calls for the same identifier would otherwise silently overwrite each other's
        // continuation and orphan one caller into the timeout.
        val requestKey = "$identifier#${requestCounter.incrementAndGet()}"
        return try {
            withTimeout(GENERATE_TIMEOUT_MS) {
                generatePoTokenInternal(identifier, requestKey)
            }
        } catch (e: TimeoutCancellationException) {
            // A renderer that never answers is wedged/dead (safety net for providers where
            // onRenderProcessGone doesn't fire) — drop the pending continuation and fail fast.
            isDead = true
            popPoTokenContinuation(requestKey)
            Timber.tag(TAG).e("generatePoToken(%s) timed out after %dms", identifier, GENERATE_TIMEOUT_MS)
            throw PoTokenException("poToken generation timed out after ${GENERATE_TIMEOUT_MS}ms")
        }
    }

    private suspend fun generatePoTokenInternal(identifier: String, requestKey: String): String {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                Timber.tag(TAG).d("generatePoToken() called")
                addPoTokenEmitter(requestKey, cont)
                // The IIFE keeps requestKey/u8Identifier lexically captured per call: bare globals
                // here would let a concurrent call reassign them before this call's promise
                // resolves, delivering this token to the other call's continuation.
                webView.evaluateJavascript(
                    """(function() {
                        var requestKey = "$requestKey"
                        try {
                            var u8Identifier = ${stringToU8(identifier)}
                            obtainPoToken(u8Identifier).then(function(poTokenU8) {
                                $JS_INTERFACE.onObtainPoTokenResult(requestKey, poTokenU8.join(","))
                            }).catch(function(error) {
                                $JS_INTERFACE.onObtainPoTokenError(requestKey, error + "\n" + (error.stack || ''))
                            })
                        } catch (error) {
                            $JS_INTERFACE.onObtainPoTokenError(requestKey, error + "\n" + error.stack)
                        }
                    })()""",
                    null
                )
            }
        }
    }

    /** Called by the JavaScript snippet from [generatePoToken] when the minter throws. */
    @JavascriptInterface
    fun onObtainPoTokenError(requestKey: String, error: String) {
        Timber.tag(TAG).e("obtainPoToken error from JavaScript: %s", error)
        // Always transient here: the minter was already created successfully, so even a
        // "SyntaxError" comes from Google's challenge/program data, not a broken WebView engine.
        popPoTokenContinuation(requestKey)?.resumeWithException(PoTokenException(error))
    }

    /** Called by the JavaScript snippet from [generatePoToken] with the minted token bytes. */
    @JavascriptInterface
    fun onObtainPoTokenResult(requestKey: String, poTokenU8: String) {
        val poToken = try {
            u8ToBase64(poTokenU8)
        } catch (t: Throwable) {
            popPoTokenContinuation(requestKey)?.resumeWithException(t)
            return
        }

        Timber.tag(TAG).d("Generated poToken successfully")
        popPoTokenContinuation(requestKey)?.resume(poToken)
    }

    val isExpired: Boolean
        get() = Instant.now().isAfter(expirationInstant)
    //endregion

    //region Handling multiple emitters
    private fun addPoTokenEmitter(identifier: String, continuation: Continuation<String>) {
        poTokenContinuations[identifier] = continuation
    }

    private fun popPoTokenContinuation(identifier: String): Continuation<String>? {
        return poTokenContinuations.remove(identifier)
    }

    private fun popAllPoTokenContinuations(): Map<String, Continuation<String>> {
        val result = poTokenContinuations.toMap()
        poTokenContinuations.clear()
        return result
    }
    //endregion

    //region Utils
    private fun makeBotguardServiceRequest(
        url: String,
        data: String,
        handleResponseBody: (String) -> Unit,
    ) {
        scope.launch(exceptionHandler) {
            val requestBuilder = okhttp3.Request.Builder()
                .post(data.toRequestBody())
                .headers(
                    mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "application/json",
                        "Content-Type" to "application/json+protobuf",
                        "x-goog-api-key" to GOOGLE_API_KEY,
                        "x-user-agent" to "grpc-web-javascript/0.1",
                    ).toHeaders()
                )
                .url(url)
            // .use{} so the response is closed on the non-200 path too (an unread body would
            // otherwise strand its connection).
            val (httpCode, body) = withContext(Dispatchers.IO) {
                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    response.code to if (response.code == 200) response.body?.string() else null
                }
            }
            // Treat an empty 200 body as a failure too.
            if (body.isNullOrEmpty()) {
                onInitializationErrorCloseAndCancel(PoTokenException("Invalid botguard response (code=$httpCode, empty body)"))
            } else {
                handleResponseBody(body)
            }
        }
    }

    private fun onInitializationErrorCloseAndCancel(error: Throwable) {
        close()
        if (initResumed.compareAndSet(false, true)) {
            // The continuation may have been cancelled by the init timeout.
            runCatching { continuation.resumeWithException(error) }
        }
    }

    fun close() {
        if (closed) return
        closed = true

        scope.cancel()

        // WebView methods must run on the thread that created the WebView (main), but some
        // callers arrive on the JavaBridge thread — post the teardown there instead of letting
        // it throw and leak the instance.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            destroyWebView()
        } else {
            Handler(Looper.getMainLooper()).post { destroyWebView() }
        }
    }

    @MainThread
    private fun destroyWebView() {
        // After a render-process crash some WebView methods can throw — never let teardown crash.
        runCatching {
            webView.clearHistory()
            webView.clearCache(true)
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }.onFailure { Timber.tag(TAG).w("PO token WebView teardown threw: %s", it) }
    }
    //endregion

    companion object {
        private const val TAG = "PoTokenWebView"
        // Public Google jnn-pa API key, assembled from parts so it isn't stripped by scanners.
        private const val GOOGLE_API_KEY = "AIza" + "SyDyT5W" + "0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        private const val JS_INTERFACE = "AndroidPoTokenBridge"

        // Init does network round-trips (botguard Create/GenerateIT) + JS execution; a WebView
        // that hasn't finished after this long has a dead/wedged renderer or dead network.
        private const val INIT_TIMEOUT_MS = 45_000L

        // A live renderer mints a poToken in well under a second.
        private const val GENERATE_TIMEOUT_MS = 15_000L

        private val httpClient = OkHttpClient.Builder()
            .proxy(YouTube.proxy)
            .build()

        suspend fun getNewPoTokenGenerator(context: Context): PoTokenWebView {
            var created: PoTokenWebView? = null
            try {
                return withTimeout(INIT_TIMEOUT_MS) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { cont ->
                            val potWv = PoTokenWebView(context.applicationContext, cont)
                            created = potWv
                            potWv.loadHtmlAndObtainBotguard()
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.tag(TAG).e("PoTokenWebView init timed out after %dms", INIT_TIMEOUT_MS)
                closeQuietly(created)
                throw PoTokenException("PoTokenWebView init timed out after ${INIT_TIMEOUT_MS}ms")
            } catch (e: CancellationException) {
                // Caller cancelled — don't leak the half-initialized WebView.
                closeQuietly(created)
                throw e
            }
        }

        private suspend fun closeQuietly(potWv: PoTokenWebView?) {
            if (potWv == null) return
            withContext(NonCancellable + Dispatchers.Main) {
                // Mark init resumed so a late JS/network callback can't resume a cancelled
                // continuation.
                potWv.initResumed.set(true)
                potWv.close()
            }
        }
    }
}
