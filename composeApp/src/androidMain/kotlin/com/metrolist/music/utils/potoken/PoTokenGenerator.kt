package com.metrolist.music.utils.potoken

import android.webkit.CookieManager
import co.touchlab.kermit.Logger
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    private val logger = Logger.withTag(TAG)
    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        logger.d("getWebClientPoToken called: videoId=$videoId, sessionId=$sessionId")
        logger.d("WebView state: supported=$webViewSupported, badImpl=$webViewBadImpl")
        if (!webViewSupported || webViewBadImpl) {
            logger.d("WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return null
        }

        return try {
            logger.d("Calling runBlocking to generate poToken (timeout=${POTOKEN_TIMEOUT_MS}ms)...")
            runBlocking {
                withTimeout(POTOKEN_TIMEOUT_MS) {
                    getWebClientPoToken(videoId, sessionId, forceRecreate = false)
                }
            }
        } catch (e: TimeoutCancellationException) {
            // The WebView's sandboxed process can be culled by the OS (storage pressure, low
            // memory, etc.) which leaves the PoToken WebView call hung indefinitely. Cap it so
            // playerResponseForPlayback can fall through to non-PoToken fallback clients (e.g.
            // ANDROID_VR) instead of blocking the entire playback path.
            logger.w("poToken generation timed out after ${POTOKEN_TIMEOUT_MS}ms; proceeding without PoToken")
            runBlocking {
                webPoTokenGenLock.withLock {
                    try {
                        withContext(Dispatchers.Main) {
                            webPoTokenGenerator?.close()
                        }
                    } catch (closeEx: Exception) {
                        logger.e("Exception closing PoTokenWebView during timeout cleanup", closeEx)
                    }
                    webPoTokenGenerator = null
                    webPoTokenStreamingPot = null
                    webPoTokenSessionId = null
                }
            }
            null
        } catch (e: Exception) {
            logger.e("poToken generation exception: ${e.javaClass.simpleName}: ${e.message}", e)
            when (e) {
                is BadWebViewException -> {
                    logger.e("Could not obtain poToken because WebView is broken", e)
                    webViewBadImpl = true
                    null
                }
                else -> throw e // includes PoTokenException
            }
        }
    }

    private companion object {
        // Healthy cold-start (WebView spin-up + botguard JS + token gen) is ~2–5s in practice;
        // 8s leaves slack for a slow device without making the user wait too long before the
        // fallback chain (ANDROID_VR, etc.) takes over when the WebView hangs.
        const val POTOKEN_TIMEOUT_MS = 8_000L
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time
     * [PoTokenWebView.generatePoToken] was called
     */
    private suspend fun getWebClientPoToken(videoId: String, sessionId: String, forceRecreate: Boolean): PoTokenResult {
        logger.d("Web poToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired || webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    logger.d("Creating new PoTokenWebView (forceRecreate=$forceRecreate)")
                    webPoTokenSessionId = sessionId

                    withContext(Dispatchers.Main) {
                        webPoTokenGenerator?.close()
                    }

                    // create a new webPoTokenGenerator
                    webPoTokenGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    webPoTokenStreamingPot = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
                    logger.d("Streaming poToken generated for sessionId=${webPoTokenSessionId?.take(20)}...")
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                // the poTokenGenerator has just been recreated (and possibly this is already the
                // second time we try), so there is likely nothing we can do
                throw throwable
            } else {
                // retry, this time recreating the [webPoTokenGenerator] from scratch;
                // this might happen for example if the app goes in the background and the WebView
                // content is lost
                logger.e("Failed to obtain poToken, retrying", throwable)
                return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        logger.d("poToken generated successfully: session=${streamingPot.take(20)}..., video=${playerPot.take(20)}...")

        /*
         * These two were the wrong way round, and it is the reason playback died at roughly half a
         * megabyte.
         *
         * BotGuard mints a token *bound to whatever identifier you hand it*:
         *   - `streamingPot` was minted from the **session id** (the visitorData)
         *   - `playerPot`    was minted from the **videoId**
         *
         * YouTube expects the opposite pairing to the one that was being sent: the `/player`
         * request must carry the token bound to the video, and the media URL's `pot=` must carry
         * the token bound to the session. Swapped, the `pot` on every stream URL was a token for
         * the wrong subject, so googlevideo treated the request as unattested and served only a
         * ~0.5 MB prefix — measured directly: ranges up to ~450 KB returned 206 and everything
         * past that returned 403, on a 496 MB file as readily as on a 566 KB one.
         *
         * Naming it explicitly here because the two are indistinguishable at the call site: both
         * are opaque base64 strings, and getting them the wrong way round fails silently as a
         * *throttle* rather than as an error.
         */
        return PoTokenResult(
            playerRequestPoToken = playerPot,
            streamingDataPoToken = streamingPot,
        )
    }
}
