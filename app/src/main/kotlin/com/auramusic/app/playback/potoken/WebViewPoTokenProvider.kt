package com.auramusic.app.playback.potoken

import android.annotation.SuppressLint
import android.content.Context
import com.auramusic.innertube.PoTokenProvider
import com.auramusic.innertube.YouTube
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * WebView-backed Proof-of-Origin token provider.
 *
 * Token semantics follow current YouTube attestation behaviour:
 * - [getPlayerPoToken]: the SESSION-bound token (minted against the active
 *   visitorData). Sent inside `/player` requests'
 *   `serviceIntegrityDimensions.poToken` for web-family clients.
 * - [getStreamingPoToken]: the VIDEO-bound token (minted against the video
 *   id). Appended as the `pot` query parameter on googlevideo stream URLs.
 *
 * The BotGuard runtime lives inside a single reusable [PoTokenWebView]; it is
 * recreated when expired, dead (renderer crash/wedge), or when the session
 * changes. Generation is capped by a hard timeout so playback always falls
 * through to non-web fallback clients instead of hanging.
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewPoTokenProvider(
    private val context: Context,
) : PoTokenProvider {

    private val logTag = "PoTokenProvider"

    /** Overall cap for one mint attempt (incl. WebView cold start on first use). */
    private val generationTimeoutMs = 12_000L

    /** Wait this long after a failed attestation before trying to mint again. */
    private val failureBackoffMs = 60_000L

    private val generationMutex = Mutex()

    @Volatile private var generator: PoTokenWebView? = null
    @Volatile private var generatorSessionId: String? = null
    @Volatile private var sessionPot: String? = null
    @Volatile private var webViewBadImpl = false
    @Volatile private var lastGenerationFailureAtMs = 0L

    /** Video-bound tokens are reusable for a while; cache to avoid re-minting. */
    private val videoTokenCache = ConcurrentHashMap<String, CachedToken>()

    private data class CachedToken(val value: String, val expiresAtMs: Long) {
        fun isValid() = System.currentTimeMillis() < expiresAtMs
    }

    override suspend fun getPlayerPoToken(videoId: String): String? {
        val sessionId = YouTube.visitorData ?: run {
            Timber.tag(logTag).w("Cannot generate PO tokens without visitorData")
            return null
        }
        // Session-bound token for /player requests.
        return generateTokens(videoId, sessionId)?.first
    }

    override suspend fun getStreamingPoToken(videoId: String): String? {
        videoTokenCache[videoId]?.takeIf { it.isValid() }?.let { return it.value }

        val sessionId = YouTube.visitorData ?: return null
        // Video-bound token for googlevideo (`pot=`) requests.
        val videoPot = generateTokens(videoId, sessionId)?.second ?: return null

        if (videoPot.isNotBlank()) {
            videoTokenCache[videoId] =
                CachedToken(videoPot, System.currentTimeMillis() + 6 * 60 * 60 * 1000L)
        }
        return videoPot.takeIf { it.isNotBlank() }
    }

    /**
     * Returns `(sessionPot, videoPot)` or null on failure/backoff.
     *
     * Flow mirrors the well-established open implementation of this scheme:
     * under the mutex ensure a live generator exists AND the session pot was
     * minted once; then mint the video pot OUTSIDE the mutex so the
     * recreate-and-retry pass can re-enter safely.
     */
    private suspend fun generateTokens(videoId: String, sessionId: String): Pair<String, String>? {
        if (webViewBadImpl) return null

        val lastFailure = lastGenerationFailureAtMs
        if (lastFailure != 0L && System.currentTimeMillis() - lastFailure < failureBackoffMs) {
            Timber.tag(logTag).d("PO token generation backing off (recent failure)")
            return null
        }

        return try {
            withTimeout(generationTimeoutMs) {
                val (gen, sessPot, fresh) = acquireGenerator(sessionId)
                val videoPot = try {
                    gen.generatePoToken(videoId)
                } catch (throwable: Throwable) {
                    if (fresh) throw throwable
                    // The app may have been backgrounded and the WebView content
                    // lost, or the renderer died silently — retry once with a
                    // from-scratch generator.
                    Timber.tag(logTag).w(throwable, "PO token mint failed; retrying with recreated generator")
                    val (gen2, _, _) = acquireGeneratorForceFresh(sessionId)
                    gen2.generatePoToken(videoId)
                }
                lastGenerationFailureAtMs = 0L
                sessPot to videoPot
            }
        } catch (e: TimeoutCancellationException) {
            // The WebView renderer can be culled by the OS and hang forever;
            // cap it so resolution can fall through to guest clients instead
            // of blocking the whole playback path. Tear down the wedged
            // instance so the next attempt starts fresh.
            Timber.tag(logTag).w("PO token generation timed out after %dms", generationTimeoutMs)
            markFailure()
            closeGenerator()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: BadWebViewException) {
            Timber.tag(logTag).e(e, "System WebView cannot run BotGuard; disabling PO tokens for this session")
            webViewBadImpl = true
            markFailure()
            null
        } catch (t: Throwable) {
            Timber.tag(logTag).w(t, "PO token generation threw")
            markFailure()
            null
        }
    }

    /**
     * Ensures a live generator + session pot exist. Returns
     * `(generator, sessionPot, freshlyCreated)`.
     */
    private suspend fun acquireGenerator(sessionId: String): Triple<PoTokenWebView, String, Boolean> {
        generationMutex.withLock {
            val shouldRecreate = generator == null ||
                generator!!.isExpired || generator!!.isDead || generatorSessionId != sessionId

            if (shouldRecreate) {
                createGeneratorLocked(sessionId)
                return Triple(requireNotNull(generator), requireNotNull(sessionPot), true)
            }
        }
        return Triple(requireNotNull(generator), requireNotNull(sessionPot), false)
    }

    private suspend fun acquireGeneratorForceFresh(sessionId: String): Triple<PoTokenWebView, String, Boolean> {
        generationMutex.withLock {
            createGeneratorLocked(sessionId)
        }
        return Triple(requireNotNull(generator), requireNotNull(sessionPot), true)
    }

    /** Must be called while holding [generationMutex]. */
    private suspend fun createGeneratorLocked(sessionId: String) {
        withContext(Dispatchers.Main) { runCatching { generator?.close() } }

        // Clear committed state BEFORE the fallible steps below so a throw here
        // forces a full recreation next time instead of pairing an updated
        // sessionId with a stale/null session pot.
        generator = null
        sessionPot = null
        generatorSessionId = null

        val newGenerator = PoTokenWebView.getNewPoTokenGenerator(context)

        // The session pot must be generated exactly once before any other token.
        val newSessionPot = try {
            newGenerator.generatePoToken(sessionId)
        } catch (t: Throwable) {
            // Don't leak the freshly created WebView.
            runCatching { newGenerator.close() }
            throw t
        }

        generator = newGenerator
        sessionPot = newSessionPot
        generatorSessionId = sessionId
        Timber.tag(logTag).d("BotGuard generator ready; session PO token minted")
    }

    private suspend fun closeGenerator() {
        generationMutex.withLock {
            withContext(Dispatchers.Main) { runCatching { generator?.close() } }
            generator = null
            sessionPot = null
            generatorSessionId = null
        }
    }

    private fun markFailure() {
        lastGenerationFailureAtMs = System.currentTimeMillis()
    }

    override fun invalidatePoTokens(videoId: String?) {
        // An explicit invalidation is a deliberate retry signal (e.g. from the
        // player's 403 recovery path) — lift any failure backoff.
        lastGenerationFailureAtMs = 0L
        if (videoId == null) {
            videoTokenCache.clear()
            sessionPot = null
            Timber.tag(logTag).d("Invalidated all cached PO tokens")
        } else {
            videoTokenCache.remove(videoId)
            Timber.tag(logTag).d("Invalidated PO tokens for video %s", videoId)
        }
    }

    fun destroy() {
        // Never block the caller (destroy may run on the main thread): post
        // the WebView teardown to the main looper instead.
        val gen = generator
        generator = null
        sessionPot = null
        generatorSessionId = null
        videoTokenCache.clear()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching { gen?.close() }
        }
    }
}
