package com.metrolist.music.utils

import com.metrolist.innertube.models.response.PlayerResponse.StreamingData.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Video track selection for a car head unit.
 *
 * The priorities being locked in, in order: hardware-decodable codec, then the tallest track that
 * fits the connection's height cap, then the cheapest stream at that size.
 */
class FindVideoFormatTest {

    @Test
    fun `prefers avc1 over vp9 at the same height`() {
        val chosen = YTPlayerUtils.findVideoFormat(
            listOf( video( itag = 248, mime = "video/webm; codecs=\"vp9\"", height = 720 ),
                    video( itag = 136, mime = "video/mp4; codecs=\"avc1.4d401f\"", height = 720 ) ),
            isMetered = false
        )

        assertEquals( 136, chosen?.itag )
    }

    @Test
    fun `takes the tallest track within the unmetered cap`() {
        val chosen = YTPlayerUtils.findVideoFormat(
            listOf( video( itag = 134, height = 360 ),
                    video( itag = 136, height = 720 ),
                    video( itag = 137, height = 1080 ) ),
            isMetered = false
        )

        assertEquals( 137, chosen?.itag )
    }

    @Test
    fun `never exceeds 1080p even when a taller track exists`() {
        val chosen = YTPlayerUtils.findVideoFormat(
            listOf( video( itag = 137, height = 1080 ),
                    video( itag = 266, height = 2160 ) ),
            isMetered = false
        )

        assertEquals( 137, chosen?.itag )
    }

    @Test
    fun `caps at 480p on a metered connection`() {
        val chosen = YTPlayerUtils.findVideoFormat(
            listOf( video( itag = 134, height = 360 ),
                    video( itag = 135, height = 480 ),
                    video( itag = 137, height = 1080 ) ),
            isMetered = true
        )

        assertEquals( 135, chosen?.itag )
    }

    @Test
    fun `falls back to the smallest track when everything exceeds the cap`() {
        // Better to show a too-large stream than to silently drop video entirely
        val chosen = YTPlayerUtils.findVideoFormat(
            listOf( video( itag = 137, height = 1080 ),
                    video( itag = 266, height = 2160 ) ),
            isMetered = true
        )

        assertEquals( 137, chosen?.itag )
    }

    @Test
    fun `prefers the cheaper stream when height and codec tie`() {
        val chosen = YTPlayerUtils.findVideoFormat(
            listOf( video( itag = 298, height = 720, bitrate = 3_000_000 ),
                    video( itag = 136, height = 720, bitrate = 1_200_000 ) ),
            isMetered = false
        )

        assertEquals( 136, chosen?.itag )
    }

    @Test
    fun `ignores audio-only formats`() {
        val audioOnly = Format(
            itag = 251, url = URL, mimeType = "audio/webm; codecs=\"opus\"", bitrate = 128_000,
            width = null, height = null, contentLength = null, quality = "tiny", fps = null,
            qualityLabel = null, averageBitrate = null, audioQuality = "AUDIO_QUALITY_MEDIUM",
            approxDurationMs = null, audioSampleRate = null, audioChannels = 2, loudnessDb = null,
            lastModified = null, signatureCipher = null, cipher = null, audioTrack = null
        )

        assertNull( YTPlayerUtils.findVideoFormat( listOf( audioOnly ), isMetered = false ) )
    }

    @Test
    fun `returns null when there are no formats at all`() {
        assertNull( YTPlayerUtils.findVideoFormat( emptyList(), isMetered = false ) )
    }

    private companion object {

        const val URL = "https://example.invalid/stream"

        fun video(
            itag: Int,
            height: Int,
            mime: String = "video/mp4; codecs=\"avc1.4d401f\"",
            bitrate: Int = 1_000_000
        ) = Format(
            itag = itag,
            url = URL,
            mimeType = mime,
            bitrate = bitrate,
            // isAudio is derived from `width == null`, so a video track must carry one
            width = height * 16 / 9,
            height = height,
            contentLength = null,
            quality = "hd$height",
            fps = 30,
            qualityLabel = "${height}p",
            averageBitrate = bitrate,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
            cipher = null,
            audioTrack = null
        )
    }
}
