package com.nanzbeatles.nanas.utils

import com.nanzbeatles.nanas.constants.VideoQuality
import com.nanzbeatles.auravideo.BeatlesVideo
import com.nanzbeatles.auravideo.BeatlesVideo.VideoStreamResult
import com.nanzbeatles.auravideo.BeatlesVideo.VideoSearchResult
import com.nanzbeatles.auravideo.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

object BeatlesPlayerUtils {
    private const val logTag = "BeatlesPlayerUtils"

    /**
     * Set the preferred video quality before fetching video URLs
     */
    fun setPreferredVideoQuality(quality: VideoQuality) {
        // Map PreferenceKeys.VideoQuality to BeatlesVideo.VideoQuality
        val auraQuality = when (quality) {
            VideoQuality.QUALITY_360P -> BeatlesVideo.VideoQuality.QUALITY_360P
            VideoQuality.QUALITY_480P -> BeatlesVideo.VideoQuality.QUALITY_480P
            VideoQuality.QUALITY_720P -> BeatlesVideo.VideoQuality.QUALITY_720P
            VideoQuality.QUALITY_1080P -> BeatlesVideo.VideoQuality.QUALITY_1080P
        }
        BeatlesVideo.setPreferredVideoQuality(auraQuality)
    }

    suspend fun getVideoStreamUrl(videoId: String): Result<String> = withContext(Dispatchers.IO) {
        Timber.tag(logTag).d("BeatlesPlayerUtils: Fetching video stream URL for videoId: $videoId")

        BeatlesVideo.getVideoStreamUrl(videoId).map { result ->
            Timber.tag(logTag).d("BeatlesPlayerUtils: Successfully obtained video URL with mimeType: ${result.mimeType}")
            "${result.url}|${result.mimeType}"
        }.also { result ->
            result.onSuccess { _ ->
                Timber.tag(logTag).d("BeatlesPlayerUtils: Successfully obtained video URL")
            }.onFailure { e ->
                Timber.tag(logTag).e(e, "BeatlesPlayerUtils: Failed to get video URL")
            }
        }
    }

    /**
     * Resolve a video stream as either a single muxed URL or a pair of
     * video-only + audio-only streams that the player must merge.
     *
     * For quality > 720p, a single muxed URL is physically not available from
     * YouTube — the caller MUST handle the [BeatlesVideo.VideoStreamSource.Merged]
     * case to actually display 1080p video.
     */
    suspend fun getVideoStreamSource(videoId: String): Result<BeatlesVideo.VideoStreamSource> =
        withContext(Dispatchers.IO) {
            Timber.tag(logTag).d("BeatlesPlayerUtils: Resolving stream source for videoId: $videoId")
            BeatlesVideo.getVideoStreamSource(videoId).also { result ->
                result.onSuccess { source ->
                    when (source) {
                        is BeatlesVideo.VideoStreamSource.Single ->
                            Timber.tag(logTag).d("BeatlesPlayerUtils: Single source (${source.mimeType})")
                        is BeatlesVideo.VideoStreamSource.Merged ->
                            Timber.tag(logTag)
                                .d("BeatlesPlayerUtils: Merged source ${source.height}p video + audio")
                    }
                }.onFailure { e ->
                    Timber.tag(logTag).e(e, "BeatlesPlayerUtils: Failed to resolve stream source")
                }
            }
        }

    /**
     * Get video stream URL with automatic search fallback for regular songs
     * This searches for official music video if direct lookup fails
     */
    suspend fun getVideoStreamUrlWithFallback(songTitle: String, artistName: String, videoId: String, isVideoSong: Boolean = true): Result<VideoSearchResult> = withContext(Dispatchers.IO) {
        Timber.tag(logTag).d("BeatlesPlayerUtils: Trying to get video with fallback for: $songTitle by $artistName (isVideoSong=$isVideoSong)")

        try {
            val result = BeatlesVideo.getVideoStreamUrlWithFallback(songTitle, artistName, videoId, isVideoSong)
            result.onSuccess { searchResult ->
                Timber.tag(logTag).d("BeatlesPlayerUtils: Found video via fallback: ${searchResult.videoId}")
            }
            result
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "BeatlesPlayerUtils: Fallback search also failed")
            Result.failure(e)
        }
    }

    suspend fun hasVideoPlayback(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            BeatlesVideo.hasVideoPlayback(videoId)
        } catch (e: Exception) {
            Timber.tag(logTag).e(e, "BeatlesPlayerUtils: Error checking video availability")
            false
        }
    }

    suspend fun getVideoDetails(videoId: String): Result<VideoItem> = withContext(Dispatchers.IO) {
        BeatlesVideo.getVideoDetails(videoId)
    }
}

/** Maps app-level video quality constants to the library enum, used by settings UI. */
fun appVideoQualityToLibrary(quality: VideoQuality): BeatlesVideo.VideoQuality = when (quality) {
    VideoQuality.QUALITY_360P -> BeatlesVideo.VideoQuality.QUALITY_360P
    VideoQuality.QUALITY_480P -> BeatlesVideo.VideoQuality.QUALITY_480P
    VideoQuality.QUALITY_720P -> BeatlesVideo.VideoQuality.QUALITY_720P
    VideoQuality.QUALITY_1080P -> BeatlesVideo.VideoQuality.QUALITY_1080P
}
