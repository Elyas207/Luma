@file:OptIn(UnstableApi::class)

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.net.ConnectivityManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import co.touchlab.kermit.Logger
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.metrolist.innertube.models.YouTubeClient.Companion.VISIONOS
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.utils.YTPlayerUtils.MAIN_CLIENT
import com.metrolist.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.metrolist.music.utils.YTPlayerUtils.validateStatus
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import com.metrolist.music.utils.potoken.PoTokenGenerator
import com.metrolist.music.utils.potoken.PoTokenResult
import it.fast4x.rimusic.enums.AudioQualityFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object YTPlayerUtils : KoinComponent {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient by inject<OkHttpClient>()
    private val logger = Logger.withTag(logTag)

    private val poTokenGenerator = PoTokenGenerator()

    // Track videoIds whose WEB_REMIX stream URL 403'd on the ExoPlayer GET, so the next resolution
    // falls through to the fallback clients instead of skipping HEAD validation and looping.
    private val webRemixFailedIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    fun markWebRemixFailed(videoId: String) {
        webRemixFailedIds.add(videoId)
    }

    /**
     * Cleared when the cipher recovers (player config refreshed after a stream rejection): the
     * prior WEB_REMIX failures were caused by the stale cipher, so let resolution try WEB_REMIX
     * again instead of staying pinned to a lower fallback client for the rest of the process.
     */
    fun clearWebRemixFailures() {
        webRemixFailedIds.clear()
    }

    // Fire-and-forget scope for the cipher config self-heal triggered when a cipher client fails
    // stream validation during resolution. Only WEB_REMIX skips HEAD validation (so its bad URL
    // 403s on ExoPlayer and hits MusicService's handler); WEB_CREATOR / TVHTML5 / WEB are validated
    // here and never reach ExoPlayer, so without this trigger a WEB_REMIX-disabled user would never
    // self-heal a stale/wrong cipher config. Kept off the resolution coroutine so the (network)
    // refresh never blocks falling through to the next client.
    private val cipherRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    // VISIONOS first (its CDN URL has no spc throttle gate, so it streams whole songs with no
    // poToken/cipher — the most reliable fallback), then WEB_CREATOR, TVHTML5, the ANDROID_VR
    // variants, then TVHTML5_SIMPLY_EMBEDDED_PLAYER (login-free, bypasses age-restriction for
    // logged-out users), then the spc-gated IOS/IPADOS as last-ditch attempts.
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        WEB_CREATOR,
        TVHTML5,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        IPADOS,
        ANDROID_CREATOR,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        WEB,
    )

    /** Client names disabled by the user in Settings → Stream sources. Updated reactively by MusicService. */
    @Volatile
    var disabledStreamClients: Set<String> = emptySet()

    /**
     * Clients that have just told us to sign in, and when to stop believing them.
     *
     * YouTube's answer to "can this client stream" is a property of the *session*, not of the track:
     * when it decides a client looks like a bot it says so for every video, for a while. The walk
     * above had no memory of that, so a typical resolve spent roughly five seconds re-asking six
     * clients the same question and getting the same "Sign in to confirm you're not a bot" six
     * times — on every single track. That is the bulk of the "takes a very long time to load"
     * complaint, and none of it was doing any work.
     *
     * Ten minutes is chosen to be long enough to cover a listening session's worth of tracks and
     * short enough that a genuinely transient block clears itself without the user ever knowing
     * there was one. Nothing is ever *permanently* written off — a rejection expires on its own, so
     * this can never be the reason a client stops being tried.
     */
    private val rejectedClients = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private const val CLIENT_REJECTION_TTL_MS = 10 * 60 * 1000L

    private fun isRecentlyRejected( clientName: String ): Boolean {
        val until = rejectedClients[clientName] ?: return false
        if ( System.currentTimeMillis() > until ) {
            rejectedClients.remove( clientName )
            return false
        }
        return true
    }

    /**
     * Only auth-shaped refusals are remembered. A 403 on the media URL, an expired link or a
     * track-specific restriction says nothing about the client's standing, and writing those off
     * would slowly disable every client for the wrong reason.
     */
    private fun rememberRejection( clientName: String, status: String?, reason: String? ) {
        val authShaped = status == "LOGIN_REQUIRED" ||
                         reason?.contains( "sign in", ignoreCase = true ) == true

        if ( authShaped )
            rejectedClients[clientName] = System.currentTimeMillis() + CLIENT_REJECTION_TTL_MS
    }

    /** Lets a successful login immediately undo every "please sign in" we are currently honouring. */
    fun clearClientRejections() = rejectedClients.clear()

    /** Never stream a taller track than this, whatever the connection. */
    private const val MAX_VIDEO_HEIGHT = 1080

    /** Cap on a metered connection — 480p keeps a music video watchable without eating mobile data. */
    private const val METERED_VIDEO_HEIGHT = 480

    // A stable video id used only to warm the local BotGuard token generator; the token is
    // discarded. PoToken generation is a local WebView computation (no YouTube /player call), so
    // this triggers no network request to YouTube for the video itself.
    private const val POTOKEN_WARMUP_VIDEO_ID = "jNQXAC9IVRw"

    /**
     * Best-effort warm-up of the PoToken/BotGuard generator (BotGuard cold-start is ~2–5s) so the
     * first real playback skips it. Requires a session (visitorData); the caller should gate this on
     * visitorData being ready. The cipher WebView warm-up is separate (CipherDeobfuscator.prewarm)
     * since it needs no session. Failure is swallowed; playback falls back to lazy init unchanged.
     */

    /**
     * The identity a proof-of-origin token must be minted against.
     *
     * `YouTube.visitorData` is held percent-encoded — it ends `%3D%3D`, i.e. a base64 `==` that has
     * been escaped for use in the `X-Goog-Visitor-Id` header. BotGuard binds the token to the
     * *string it is handed*, and YouTube then validates the token against the visitor identity in
     * its decoded form, so minting against the escaped text produces a token that is structurally
     * valid and bound to the wrong subject. It fails exactly like no token at all: the stream
     * serves a short prefix and then 403s.
     */
    private fun poTokenIdentity( visitorData: String ): String =
        runCatching { java.net.URLDecoder.decode( visitorData, "UTF-8" ) }
            .getOrDefault( visitorData )

    suspend fun prewarmPoToken() {
        val sessionId = YouTube.visitorData ?: return
        if (!MAIN_CLIENT.useWebPoTokens) return
        runCatching {
            withContext(Dispatchers.IO) {
                poTokenGenerator.getWebClientPoToken(POTOKEN_WARMUP_VIDEO_ID, poTokenIdentity(sessionId))
            }
        }.onFailure { logger.w("PoToken prewarm skipped: ${it.message}", it) }
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String = "unknown",
        /**
         * Video track from the same response as [format], when one is available and usable.
         * Null means audio-only — either the content has no video or its video could not be
         * resolved, and in both cases playback proceeds with audio alone.
         */
        val videoFormat: PlayerResponse.StreamingData.Format? = null,
        val videoStreamUrl: String? = null,
    ) {
        /** Whether this resolution can drive a video surface as well as the audio renderer. */
        val hasVideo: Boolean
            get() = videoStreamUrl != null
    }
    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQualityFormat,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        logger.d("=== PLAYER RESPONSE FOR PLAYBACK ===")
        logger.d("videoId: $videoId")
        logger.d("playlistId: $playlistId")
        logger.d("audioQuality: $audioQuality")

        // Check if this is an uploaded/privately owned track
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true
        logger.d("Content type detection (preliminary):")
        logger.d("  isUploadedTrack (from playlistId): $isUploadedTrack")

        val isLoggedIn = YouTube.cookie != null
        logger.d("Authentication status: ${if (isLoggedIn) "LOGGED_IN" else "ANONYMOUS"}")

        // Get signature timestamp (same as before for normal content)
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        logger.d("Signature timestamp: ${signatureTimestamp.timestamp}")

        // Generate PoToken
        var poToken: PoTokenResult? = null
        val sessionId = YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            logger.d("Generating PoToken for WEB_REMIX with sessionId")
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, poTokenIdentity(sessionId))
                if (poToken != null) {
                    logger.d("PoToken generated successfully")
                }
            } catch (e: Exception) {
                logger.e("PoToken generation failed: ${e.message}", e)
            }
        }

        // Try WEB_REMIX with signature timestamp and poToken (same as before)
        logger.d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        var mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp.timestamp, poToken?.playerRequestPoToken).getOrThrow()

        // Debug uploaded track response
        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse.playabilityStatus.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse.playabilityStatus.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse.videoDetails?.title}, videoId=${mainPlayerResponse.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        // Check if WEB_REMIX response indicates age-restricted
        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "LOGIN_REQUIRED", "CONTENT_CHECK_REQUIRED")
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {
            // Age-restricted: use WEB_CREATOR directly (no NewPipe needed from here)
            logger.d("Age-restricted detected, using WEB_CREATOR")
            logger.i("Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                logger.d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        // If we still don't have a valid response, throw

        var audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        val retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        // Check current status
        val currentStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestricted = currentStatus in listOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "LOGIN_REQUIRED", "CONTENT_CHECK_REQUIRED")

        if (isAgeRestricted) {
            logger.d("Content is still age-restricted (status: $currentStatus), will try fallback clients")
            logger
                .i("Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }

        // For age-restricted: skip main client, start with fallbacks
        // For normal content: standard order
        val startIndex = when {
            isAgeRestricted -> 0
            else -> -1
        }

        var bestFallbackFormat: PlayerResponse.StreamingData.Format? = null
        var bestFallbackUrl: String? = null
        var bestFallbackExpiry: Int? = null
        var bestFallbackResponse: PlayerResponse? = null
        var bestFallbackClient: String? = null
        var successClient: String? = null

        // Whether *any* client considered this track playable, even if its url was later rejected.
        // Distinguishes "YouTube will not serve this to anyone" from "we could not get a working
        // link", which are different problems and deserve different words. See the throw below.
        var sawPlayableResponse = false

        // The response/client the audio url came from. The video track is taken from the same pair
        // so both halves of the stream are guaranteed to be mutually playable.
        var winningResponse: PlayerResponse? = null
        var winningClient: YouTubeClient? = null

        val hasHighQuality = mainPlayerResponse.streamingData?.adaptiveFormats?.any { it.audioQuality == "AUDIO_QUALITY_HIGH" } == true

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                // try with streams from main client first (use retry response if available)
                client = MAIN_CLIENT
                if (client.clientName in disabledStreamClients) {
                    logger.d("Skipping MAIN_CLIENT ${client.clientName} — disabled in stream sources")
                    continue
                }
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                logger.d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                // after main client use fallback clients
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                logger.d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")

                if (client.clientName in disabledStreamClients) {
                    logger.d("Skipping client ${client.clientName} — disabled in stream sources")
                    continue
                }

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    // skip client if it requires login but user is not logged in
                    logger.d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                if (isRecentlyRejected(client.clientName)) {
                    // Already refused us this session; asking again costs a full round trip to be
                    // told the same thing. See [rejectedClients].
                    logger.d("Skipping client ${client.clientName} — refused sign-in check recently")
                    continue
                }

                logger.d("Fetching player response for fallback client: ${client.clientName}")
                // Only web clients take proof-of-origin. Presenting the web token to VISIONOS,
                // ANDROID_VR and TVHTML5 was tried against the live service and changed nothing —
                // they still answered "Sign in to confirm you're not a bot" — so the token stays
                // where it is accepted rather than being sent to clients that have no use for it.
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                // Skip signature timestamp for age-restricted (faster), use it for normal content
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken).getOrNull()
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                logger.d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                sawPlayableResponse = true

                // Skip NewPipe for age-restricted content (NewPipe doesn't use our auth)
                val responseToUse = if (wasOriginallyAgeRestricted) {
                    logger.d("Skipping NewPipe for age-restricted content")
                    streamPlayerResponse
                } else {
                    // Try to get streams using newPipePlayer method
                    val newPipeResponse = YouTube.newPipePlayer(videoId, streamPlayerResponse)
                    newPipeResponse ?: streamPlayerResponse
                }

                if (audioConfig == null) {
                    audioConfig = responseToUse.playerConfig?.audioConfig

                    if (audioConfig != null) {
                        logger.d("AudioConfig obtained from response of client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    } else {
                        logger.d("No audioConfig found in responseToUse.")
                    }
                }

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                    )

                if (format == null) {
                    logger.d("No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    continue
                }

                logger.d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                if (streamUrl == null) {
                    logger.d("Stream URL not found for format")
                    continue
                }

                // Apply n-transform for throttle parameter handling
                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                val musicVideoType = streamPlayerResponse.videoDetails?.musicVideoType

                logger.d("=== N-TRANSFORM DECISION ===")
                logger.d("Content type analysis:")
                logger.d("  musicVideoType: $musicVideoType")
                logger.d("  isUploadedTrack (from playlistId): $isUploadedTrack")
                logger.d("  wasOriginallyAgeRestricted: $wasOriginallyAgeRestricted")
                logger.d("Client analysis:")
                logger.d("  currentClient: ${currentClient.clientName}")
                logger.d("  useWebPoTokens: ${currentClient.useWebPoTokens}")

                // Apply n-transform and PoToken for web clients (WEB, WEB_REMIX, WEB_CREATOR, TVHTML5)
                val needsNTransform = currentClient.useWebPoTokens ||
                    currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")

                logger.d("N-transform decision:")
                logger.d("  needsNTransform: $needsNTransform")
                logger.d("  Reason: useWebPoTokens=${currentClient.useWebPoTokens}, " +
                    "clientInList=${currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")}")

                /*
                 * Two separate jobs that were wired to one switch.
                 *
                 * The `n` transform only applies to clients whose urls actually carry an `n`
                 * parameter — that gate is correct. Proof-of-origin is not the same question: every
                 * url needs it, and it was riding along inside the same branch, so the clients that
                 * skip the n-transform (IOS, ANDROID — precisely the ones the fallback lands on)
                 * were also silently skipping `pot`. Attaching them separately is the difference
                 * between a stream that stops at half a megabyte and one that plays to the end.
                 */
                if (needsNTransform)
                    streamUrl = applyThrottleTransform(streamUrl, currentClient, poToken)
                else {
                    logger.d("Skipping n-transform (not required for this client/content)")
                    streamUrl = appendProofOfOrigin(streamUrl, poToken)
                }

                // Remember which response and client produced the winning audio url, so the video
                // track can be pulled from that same response without a second network sweep.
                winningResponse = responseToUse
                winningClient = currentClient

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    logger.d("Stream expiration time not found")
                    continue
                }

                logger.d("Stream expires in: $streamExpiresInSeconds seconds")

                fun scoreFallbackQuality(quality: String?): Int = when (quality) {
                    "AUDIO_QUALITY_HIGH" -> 3
                    "AUDIO_QUALITY_MEDIUM" -> 2
                    "AUDIO_QUALITY_LOW" -> 1
                    else -> 0
                }

                fun scoreFallbackCodec(mimeType: String): Int = when {
                    mimeType.contains("opus", ignoreCase = true) -> 2
                    mimeType.contains("mp4a", ignoreCase = true) -> 1
                    else -> 0
                }

                if (audioQuality == AudioQualityFormat.High && format.audioQuality != "AUDIO_QUALITY_HIGH" && hasHighQuality) {
                    val isBetter = bestFallbackFormat == null ||
                        compareValuesBy(
                            format, bestFallbackFormat,
                            { scoreFallbackQuality(it.audioQuality) },
                            { it.audioChannels ?: 2 },
                            { scoreFallbackCodec(it.mimeType) },
                            { it.bitrate }
                        ) > 0
                    if (isBetter) {
                        logger.d("Saving fallback format: ${format.mimeType}, bitrate: ${format.bitrate}")
                        bestFallbackFormat = format
                        bestFallbackUrl = streamUrl
                        bestFallbackExpiry = streamExpiresInSeconds
                        bestFallbackResponse = streamPlayerResponse
                        bestFallbackClient = currentClient.clientName
                    }
                    continue
                }

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    /** skip [validateStatus] for last client */
                    logger.d("Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    logger
                        .i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    successClient = currentClient.clientName
                    break
                }

                // WEB_REMIX authenticated CDN URLs can 403 on HEAD yet serve fine on the byte-range
                // GET that ExoPlayer makes. Skip HEAD validation for the main client and let ExoPlayer
                // try directly, UNLESS this videoId already 403'd on GET (markWebRemixFailed) — then
                // fall through to the fallback clients. Saves a validateStatus round-trip per resolve.
                if (clientIndex == -1 && currentClient.clientName == "WEB_REMIX" &&
                    !webRemixFailedIds.contains(videoId)
                ) {
                    /*
                     * This used to hand WEB_REMIX's url straight to ExoPlayer unchecked, because a
                     * `HEAD` against it returns 403 even when the url is fine. The deep probe does
                     * not have that false negative — it is the same ranged GET ExoPlayer issues —
                     * so the main client can now be held to the same standard as every fallback.
                     *
                     * Checking here is what stops a url that will die mid-track from becoming the
                     * user's problem: a failure costs one 2 KB request and moves to the next
                     * client, instead of a stall, an ExoPlayer error and a recovery pass.
                     */
                    if (validateStatus(streamUrl, deep = true)) {
                        logger.d("WEB_REMIX validated (deep probe)")
                        logger.i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                        successClient = currentClient.clientName
                        break
                    }
                    logger.d("WEB_REMIX failed deep validation — falling through to fallback clients")
                    markWebRemixFailed(videoId)
                }

                if (validateStatus(streamUrl, deep = true)) {
                    // working stream found
                    logger.d("Stream validated successfully with client: ${currentClient.clientName}")
                    // Log for release builds
                    logger.i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    logger.d("Stream validation failed for client: ${currentClient.clientName}")
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status
                val reason = streamPlayerResponse?.playabilityStatus?.reason
                logger.d("Player response status not OK: $status, reason: $reason")

                if (clientIndex >= 0)
                    rememberRejection(STREAM_FALLBACK_CLIENTS[clientIndex].clientName, status, reason)
            }
        }

        if (audioQuality == AudioQualityFormat.High && format?.audioQuality != "AUDIO_QUALITY_HIGH" && bestFallbackFormat != null) {
            logger.d("Using best fallback format: ${bestFallbackFormat.mimeType}, bitrate: ${bestFallbackFormat.bitrate}")
            format = bestFallbackFormat
            streamUrl = bestFallbackUrl
            streamExpiresInSeconds = bestFallbackExpiry
            streamPlayerResponse = bestFallbackResponse
            successClient = bestFallbackClient
        }

        if (streamPlayerResponse == null) {
            logger.e("Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val lastReason = streamPlayerResponse.playabilityStatus.reason

            /*
             * `streamPlayerResponse` holds whichever client happened to be *last* in the list, and
             * that client's opinion is not the truth about the track.
             *
             * Observed on a track that plays perfectly: clients 7 and 8 (IOS) returned OK and gave
             * real urls that 403'd, client 11 (ANDROID) returned OK, and client 12 (WEB) — the last
             * one, and therefore the one whose message reached the screen — said "Video
             * unavailable". The user is told the track does not exist when in fact ten clients
             * disagreed and the actual problem was that no url survived validation.
             *
             * So the message now follows what was actually observed. This is a user-facing string,
             * and a wrong one sends people looking for a problem that is not there.
             */
            val errorReason = if (sawPlayableResponse)
                "Couldn't get a playable stream — YouTube rejected every link we tried"
            else
                lastReason

            logger.e("Playability status not OK: $lastReason (sawPlayableResponse=$sawPlayableResponse)")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$lastReason")
            }
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            logger.e("Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            logger.e("Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            logger.e("Could not find stream url")
            throw Exception("Could not find stream url")
        }

        logger.d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl.take(100)}...")
        }

        // Video is strictly best-effort: it rides along on the response the audio already won with,
        // costs no extra network round trip, and a failure here must never break audio playback.
        val videoResponse = bestFallbackResponse?.takeIf { it === streamPlayerResponse } ?: winningResponse
        var videoFormat: PlayerResponse.StreamingData.Format? = null
        var videoStreamUrl: String? = null

        if ( videoResponse != null && winningClient != null ) {
            videoFormat = findVideoFormat(
                videoResponse.streamingData?.adaptiveFormats.orEmpty(),
                connectivityManager.isActiveNetworkMetered
            )

            if ( videoFormat != null )
                videoStreamUrl = runCatching {
                    findUrlOrNull( videoFormat, videoId, videoResponse, skipNewPipe = wasOriginallyAgeRestricted )
                        ?.let { applyThrottleTransform( it, winningClient, poToken ) }
                }.onFailure {
                    logger.w( "Video url resolution failed, continuing audio-only: ${it.message}" )
                }.getOrNull()
        }

        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
            streamClient = successClient ?: "unknown",
            videoFormat = videoFormat,
            videoStreamUrl = videoStreamUrl,
        )
    }.onFailure { e ->
        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }
    /**
     * Player response intended for metadata / playback-tracking retrieval.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        logger.d("Fetching metadata player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        val sessionId = YouTube.visitorData
        var poToken: PoTokenResult? = null
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, poTokenIdentity(sessionId))
            } catch (_: Exception) { }
        }
        return YouTube.player(videoId, playlistId, WEB_REMIX, signatureTimestamp.timestamp, poToken?.playerRequestPoToken)
            .onSuccess { logger.d("Successfully fetched metadata player response") }
            .onFailure { logger.e("Failed to fetch metadata player response", it) }
    }

    /**
     * Pick a video track from the same `adaptiveFormats` the audio track came from.
     *
     * Deliberately conservative, because the target is a car head unit rather than a phone:
     *
     * - **avc1 (H.264) is preferred over vp9/av1.** Practically every Android media block decodes
     *   avc1 in hardware; vp9 and av1 fall back to software on a lot of the cheap SoCs that end up
     *   in head units, which burns battery and drops frames.
     * - **Height is capped**, hard at [MAX_VIDEO_HEIGHT] and much lower on a metered connection.
     *   A centre display is not worth 1080p of somebody's mobile data, and the extra bitrate is the
     *   first thing to stall when signal drops.
     * - **60fps is not chased.** Where two formats tie on height, the lower bitrate wins.
     *
     * @return the chosen format, or `null` when the response carries no usable video track
     */
    /**
     * Undo YouTube's throttling `n` parameter and append the streaming PoToken.
     *
     * Extracted so the video url gets byte-identical treatment to the audio url — a video stream
     * that skipped the n-transform throttles to a crawl rather than failing outright, which is a
     * far more confusing symptom than an error.
     *
     * On failure the original url is returned: an un-transformed url usually still plays, just
     * slowly, and that beats dropping the track.
     */
    /**
     * Attach proof-of-origin to a stream url.
     *
     * Measured behaviour without it, against a live url: ranges up to ~450 KB return `206`, and
     * every range past that returns `403` — on a 496 MB file exactly as on a 566 KB one. It is a
     * flat prefix allowance, not a rate limit, so playback begins and then dies a few seconds in.
     * That is the whole failure the app was showing.
     *
     * The token is bound to the **session**, not to the video and not to the client, and
     * `visitorData` is shared across every client the app talks to — so the same token is the right
     * one to present whichever client minted the url.
     */
    private fun appendProofOfOrigin( url: String, poToken: PoTokenResult? ): String {
        val token = poToken?.streamingDataPoToken
        if ( token.isNullOrBlank() || "pot=" in url ) return url

        val separator = if ( "?" in url ) "&" else "?"
        return "$url${separator}pot=${Uri.encode( token )}"
    }

    private suspend fun applyThrottleTransform(
        url: String,
        client: YouTubeClient,
        poToken: PoTokenResult?,
    ): String = try {
        var transformed = CipherDeobfuscator.transformNParamInUrl( url )

        /*
         * `pot` goes on every client's url, not just the web family.
         *
         * The gate used to be `client.useWebPoTokens`, which meant the *only* client that ever
         * carried proof-of-origin was the one whose formats need the signature cipher — and with
         * the cipher broken, playback always landed on IOS or ANDROID instead, with no `pot` at
         * all. googlevideo then served a ~0.5 MB prefix and refused the rest, which is the
         * "starts and dies" behaviour.
         *
         * Attaching it to any client is correct rather than opportunistic: this token is minted
         * from the **session id**, and `visitorData` is a single value shared by every client the
         * app talks to (`InnerTube.visitorData`, sent as `X-Goog-Visitor-Id` on all of them). The
         * token therefore describes the session doing the fetching, which is exactly what
         * googlevideo is checking, whichever client happened to mint the url.
         */
        transformed = appendProofOfOrigin( transformed, poToken )

        logger.d( "Throttle transform applied, url changed: ${transformed != url}" )
        transformed
    } catch ( e: kotlinx.coroutines.CancellationException ) {
        throw e     // request superseded — don't hand back a half-transformed url
    } catch ( e: Exception ) {
        logger.e( "N-transform or pot append failed: ${e.message}", e )
        url
    }

    internal fun findVideoFormat(
        adaptiveFormats: List<PlayerResponse.StreamingData.Format>,
        isMetered: Boolean,
    ): PlayerResponse.StreamingData.Format? {
        val videoFormats = adaptiveFormats.filter { !it.isAudio && it.mimeType.startsWith( "video" ) }
        if ( videoFormats.isEmpty() ) return null

        val heightCap = if ( isMetered ) METERED_VIDEO_HEIGHT else MAX_VIDEO_HEIGHT

        fun scoreCodec( mimeType: String ): Int = when {
            mimeType.contains( "avc1", ignoreCase = true ) -> 2
            mimeType.contains( "vp9", ignoreCase = true ) -> 1
            else -> 0     // av1 and anything unrecognised
        }

        // Prefer to stay under the cap; if every track is above it, take the smallest available
        // rather than giving up on video entirely.
        val eligible = videoFormats.filter { ( it.height ?: Int.MAX_VALUE ) <= heightCap }
            .ifEmpty { listOfNotNull( videoFormats.minByOrNull { it.height ?: Int.MAX_VALUE } ) }

        return eligible.maxWithOrNull(
            compareBy<PlayerResponse.StreamingData.Format> { scoreCodec( it.mimeType ) }
                .thenBy { it.height ?: 0 }
                .thenByDescending { it.bitrate }        // cheapest stream at the chosen size
        ).also {
            if ( it == null )
                logger.d( "No suitable video format found" )
            else
                logger.d( "Selected video format: itag=${it.itag}, ${it.qualityLabel}, ${it.mimeType}" )
        }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQualityFormat,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        logger.d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats ?: return null

        val audioCapableFormats = adaptiveFormats.filter { it.isAudio }
        if (audioCapableFormats.isEmpty()) return null

        val maxBitrate = audioCapableFormats.maxOfOrNull { it.bitrate } ?: return null

        fun scoreCodec(mimeType: String): Int = when {
            mimeType.contains("opus", ignoreCase = true) -> 2
            mimeType.contains("mp4a", ignoreCase = true) -> 1
            else -> 0
        }

        val format = when (audioQuality) {
            AudioQualityFormat.High -> {
                audioCapableFormats.maxWithOrNull(
                    compareBy<PlayerResponse.StreamingData.Format> { format ->
                        when (format.audioQuality) {
                            "AUDIO_QUALITY_HIGH" -> 3
                            "AUDIO_QUALITY_MEDIUM" -> 2
                            "AUDIO_QUALITY_LOW" -> 1
                            else -> 0
                        }
                    }.thenBy { it.audioChannels ?: 2 }
                        .thenBy { scoreCodec(it.mimeType) }
                        .thenBy { it.bitrate }
                )
            }

            AudioQualityFormat.Low -> {
                val cappedFormats = audioCapableFormats.filter { it.bitrate <= 128000 }
                val lowFormat = cappedFormats
                    .filter { it.isOriginal }
                    .maxByOrNull { it.bitrate }
                    ?: cappedFormats.maxByOrNull { it.bitrate }
                    ?: audioCapableFormats
                        .filter { it.isOriginal }
                        .minByOrNull { kotlin.math.abs(it.bitrate.toDouble() - 128000.0) }
                    ?: audioCapableFormats.maxByOrNull { it.bitrate }

                if (lowFormat != null) {
                    logger.d("Selected LOW format: itag=${lowFormat.itag}, bitrate: ${lowFormat.bitrate}")
                }

                lowFormat
            }

            AudioQualityFormat.Auto -> {
                val targetBitrate = if (connectivityManager.isActiveNetworkMetered) 128000.0 else maxBitrate.toDouble()
                val cappedFormats = audioCapableFormats.filter { it.bitrate <= targetBitrate }
                val autoFormat = cappedFormats
                    .filter { it.isOriginal }
                    .maxByOrNull { it.bitrate }
                    ?: cappedFormats.maxByOrNull { it.bitrate }
                    ?: audioCapableFormats
                        .filter { it.isOriginal }
                        .minByOrNull { kotlin.math.abs(it.bitrate - targetBitrate) }
                    ?: audioCapableFormats.maxByOrNull { it.bitrate }

                if (autoFormat != null) {
                    logger.d("Selected AUTO format: itag=${autoFormat.itag}, bitrate: ${autoFormat.bitrate}")
                }

                autoFormat
            }
        }

        if (format != null) {
            logger.d("Selected format: itag=${format.itag}, mimeType=${format.mimeType}, bitrate=${format.bitrate}, audioQuality label: ${format.audioQuality}")
        } else {
            logger.d("No suitable audio format found")
        }

        return format
    }
    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    /**
     * Ask the CDN the same question ExoPlayer is about to ask.
     *
     * This check used to send `HEAD` with the account cookie attached to every url, and it was
     * throwing away working streams for two independent reasons:
     *
     * 1. **googlevideo does not reliably answer `HEAD`.** It routinely returns 403 for a url that
     *    serves a byte-range `GET` perfectly — which the code already knew, because the MAIN_CLIENT
     *    path above carries a comment saying exactly that and skips validation altogether to dodge
     *    it. Every other client was still being judged by a method known to lie. Observed directly:
     *    two IOS responses came back `OK` with real urls, both were discarded on a `HEAD` 403, and
     *    the walk then ran to the end and reported "Video unavailable" for a track that plays.
     * 2. **The web cookie was attached to non-web urls.** A url minted for `c=IOS` or `c=ANDROID` is
     *    not bound to the web session, and presenting a `SAPISID` cookie to it is a good way to be
     *    refused by a CDN that was perfectly willing to serve it anonymously.
     *
     * So: a one-byte ranged `GET`, which is what the player actually issues, and the cookie only for
     * urls minted by a cookie-authenticated client — which is what "privately owned tracks" needed
     * it for in the first place.
     */
    private fun validateStatus(url: String): Boolean = validateStatus(url, deep = false)

    /**
     * @param deep probe a range *past* the un-attested prefix allowance instead of the first byte.
     *
     * A one-byte probe answers "does this url exist", which is not the question that matters. An
     * un-attested googlevideo url happily serves its first ~450 KB and refuses everything beyond —
     * so a shallow check passes and the track still dies a few seconds in. A deep probe asks the
     * question playback actually depends on: *will this url serve the middle of the file?*
     */
    private fun validateStatus(url: String, deep: Boolean): Boolean {
        logger.d("Validating stream URL status (deep=$deep)")
        try {
            // `clen` is the CDN's own statement of length, so the probe lands inside the file
            // without a preflight round trip.
            val clen = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
            val range = when {
                !deep || clen == null || clen < 800_000L -> "bytes=0-1"
                // An un-attested stream serves a fixed *fraction* of the file and refuses the rest:
                // measured against a live url with clen=5365234, every 2 KB range up to offset
                // 1078308 returned 206 and everything from 1108040 on returned 403 — a boundary at
                // 20.1% that did not move when retried later, so it is a share of the file rather
                // than a byte budget or a rate limit. A fixed 600 KB probe therefore lands *inside*
                // the allowance for anything over 3 MB and certifies a stream that dies a minute in.
                // 40% is clear of the boundary on any file size while still costing 2 KB.
                else -> ( clen * 2 / 5 ).let { "bytes=$it-${it + 2000}" }
            }

            val requestBuilder = okhttp3.Request.Builder()
                .get()
                .url(url)
                .addHeader("Range", range)

            // Only web-family urls are bound to the account session. `c=` is set by the client that
            // produced the url; when it is absent, keeping the cookie preserves the old behaviour.
            val client = Regex("[?&]c=([A-Z_0-9]+)").find(url)?.groupValues?.get(1)
            val isWebFamily = client == null || client.startsWith("WEB") || client == "TVHTML5"

            if (isWebFamily)
                YouTube.cookie?.let { requestBuilder.addHeader("Cookie", it) }

            return httpClient.newCall(requestBuilder.build()).execute().use { response ->
                // 206 is the expected answer to a range request; 200 means the server ignored the
                // range and is about to hand over the whole file, which is also fine.
                val ok = response.code == 200 || response.code == 206
                logger.d("Stream URL validation result: ${if (ok) "Success" else "Failed"} (${response.code}, client=$client, cookie=$isWebFamily)")
                ok
            }
        } catch (e: Exception) {
            logger.e("Stream URL validation failed with exception", e)
        }
        return false
    }
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private suspend fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        logger.d("Getting signature timestamp for videoId: $videoId")

        // Prefer the STS of the player the cipher actually deciphers with. The STS decides which
        // player generation YouTube mints the signatureCipher for; during A/B rollouts NewPipe's
        // independently fetched player can be a DIFFERENT generation, and a sig minted for one
        // player but deciphered by another 403s on the CDN. NewPipe is kept for age-restriction
        // detection and as the STS source only when the cipher player fetch fails.
        val cipherSts = try {
            CipherDeobfuscator.signatureTimestamp()
                ?.also { logger.d("Signature timestamp from cipher player: $it") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // cooperative cancellation: don't swallow, let the playback coroutine unwind
        } catch (e: Exception) {
            logger.e("Cipher player STS fetch failed", e)
            null
        }

        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                val chosen = cipherSts ?: timestamp
                logger.d("Signature timestamp resolved: cipher=$cipherSts newpipe=$timestamp -> using $chosen")
                SignatureTimestampResult(chosen, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                when {
                    isAgeRestricted -> {
                        logger.d("Age-restricted content detected from NewPipe")
                        logger.i("Age-restricted detected early via NewPipe: videoId=$videoId")
                    }
                    cipherSts != null -> {
                        // Non-fatal: the cipher player's STS already covers us, so NewPipe is just
                        // a fallback here — don't report its failure as an exception (avoids noise).
                        logger.w("NewPipe STS unavailable, using cipher player STS: ${error.message}")
                    }
                    else -> {
                        logger.e("Failed to get signature timestamp via NewPipe", error)
                    }
                }
                // The cipher player's STS is exactly the one the cipher will decipher with.
                logger.d("Signature timestamp resolved: cipher=$cipherSts (NewPipe failed)")
                SignatureTimestampResult(cipherSts, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        logger.d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        // First check if format already has a URL
        if (!format.url.isNullOrEmpty()) {
            logger.d("Using URL from format directly")
            return format.url
        }

        // Try custom cipher deobfuscation for signatureCipher formats
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            logger.d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                logger.d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            logger.d("Custom cipher deobfuscation failed")
        }

        // Always try NewPipe signature deobfuscation - it doesn't need auth,
        // it just applies the cipher algorithm from player.js.
        // This is critical for privately-owned tracks where skipNewPipe is true.
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            logger.d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        // Skip StreamInfo fallback for age-restricted or private content
        // (StreamInfo fetch may fail without auth for these)
        if (skipNewPipe) {
            logger.d("Skipping StreamInfo fallback for age-restricted/private content")
            return null
        }

        // Fallback: try to get URL from StreamInfo
        logger.d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                logger.d("Stream URL obtained from StreamInfo")
                return streamUrl
            }

            // If exact itag not found, try to find any audio stream
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                logger.d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        logger.e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        logger.d("Force refreshing for videoId: $videoId")
    }
}
