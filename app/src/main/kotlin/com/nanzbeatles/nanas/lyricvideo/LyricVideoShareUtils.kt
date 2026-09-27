/**
 * NanzBeatles Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.nanzbeatles.nanas.lyricvideo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.nanzbeatles.nanas.R
import android.net.ConnectivityManager
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.nanzbeatles.nanas.constants.AudioQuality
import com.nanzbeatles.nanas.constants.AudioQualityKey
import com.nanzbeatles.nanas.lyrics.LyricsEntry
import com.nanzbeatles.nanas.models.MediaMetadata
import com.nanzbeatles.nanas.utils.ShareUtils
import com.nanzbeatles.nanas.utils.YTPlayerUtils
import com.nanzbeatles.nanas.utils.enumPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

object LyricVideoShareUtils {

    private const val TAG = "LyricVideoShareUtils"

    /**
     * Resolves the full audio track locally so MediaExtractor/MediaCodec can access it.
     * Priority:
     * 1. downloadCache (full offline download)
     * 2. playerCache (playback buffer)
     * 3. Network pre-download via YTPlayerUtils resolver
     */
    suspend fun resolveLocalAudioFile(
        context: Context,
        mediaId: String,
        downloadCache: SimpleCache? = null,
        playerCache: SimpleCache? = null,
        onProgress: ((String, Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val audioDir = File(context.cacheDir, "temp_lyric_audio").apply { mkdirs() }
        val targetAudioFile = File(audioDir, "audio_${mediaId}.m4a")

        if (targetAudioFile.exists() && targetAudioFile.length() > 64 * 1024L) {
            Timber.tag(TAG).d("Reusing existing local audio file: ${targetAudioFile.absolutePath}")
            return@withContext targetAudioFile
        }

        onProgress?.invoke("Memeriksa cache audio lokal...", 0.05f)

        // 1. Check downloadCache
        if (downloadCache != null && downloadCache.isCached(mediaId, 0, C.LENGTH_UNSET)) {
            Timber.tag(TAG).d("Audio found in downloadCache, extracting...")
            onProgress?.invoke("Membaca audio dari unduhan offline...", 0.10f)
            if (copyFromCache(downloadCache, mediaId, targetAudioFile)) {
                return@withContext targetAudioFile
            }
        }

        // 2. Check playerCache
        if (playerCache != null && playerCache.isCached(mediaId, 0, C.LENGTH_UNSET)) {
            Timber.tag(TAG).d("Audio found in playerCache, extracting...")
            onProgress?.invoke("Membaca audio dari buffer pemutar...", 0.10f)
            if (copyFromCache(playerCache, mediaId, targetAudioFile)) {
                return@withContext targetAudioFile
            }
        }

        // 3. Fallback: Resolve stream and download temporary copy
        Timber.tag(TAG).d("Audio not fully cached, downloading temporary audio stream...")
        onProgress?.invoke("Mengunduh stream audio untuk video...", 0.12f)

        val connectivityManager = context.getSystemService<ConnectivityManager>()
            ?: error("ConnectivityManager not available")

        val playbackData = YTPlayerUtils.playerResponseForPlayback(
            videoId = mediaId,
            audioQuality = AudioQuality.HIGH,
            connectivityManager = connectivityManager
        ).getOrThrow()

        val streamUrl = playbackData.streamUrl
        val client = OkHttpClient.Builder().build()
        val request = Request.Builder().url(streamUrl).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Failed to download audio stream: HTTP ${response.code}")
            val body = response.body ?: error("Empty audio response body")

            FileOutputStream(targetAudioFile).use { output ->
                body.byteStream().copyTo(output)
            }
        }

        Timber.tag(TAG).d("Audio downloaded to temporary file: ${targetAudioFile.length()} bytes")
        targetAudioFile
    }

    private fun copyFromCache(cache: SimpleCache, key: String, destFile: File): Boolean {
        return try {
            val dataSource = CacheDataSource.Factory()
                .setCache(cache)
                .createDataSource()

            val dataSpec = DataSpec.Builder().setKey(key).build()
            val totalBytes = dataSource.open(dataSpec)

            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var bytesRead: Int
                while (dataSource.read(buffer, 0, buffer.size).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
            dataSource.close()
            destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to copy audio from cache")
            destFile.delete()
            false
        }
    }

    /**
     * Loads cover art bitmap via Coil.
     */
    suspend fun loadCoverBitmap(context: Context, thumbnailUrl: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (thumbnailUrl.isNullOrBlank()) return@withContext null
        try {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .allowHardware(false)
                .build()

            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                result.image.toBitmap()
            } else null
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Could not load cover bitmap")
            null
        }
    }

    /**
     * Generates a complete lyric video file.
     */
    suspend fun generateLyricVideo(
        context: Context,
        mediaMetadata: MediaMetadata,
        lyrics: List<LyricsEntry>?,
        startTimeMs: Long,
        durationMs: Long,
        downloadCache: SimpleCache? = null,
        playerCache: SimpleCache? = null,
        onProgress: (stage: String, progress: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        try {
            onProgress("Menyiapkan audio lagu...", 0.05f)
            val audioFile = resolveLocalAudioFile(
                context = context,
                mediaId = mediaMetadata.id,
                downloadCache = downloadCache,
                playerCache = playerCache,
                onProgress = onProgress
            )

            onProgress("Menganalisis amplitudo musik...", 0.22f)
            val amplitudes = AudioAmplitudeAnalyzer.analyzeAmplitudes(
                audioFile = audioFile,
                fps = 30,
                totalDurationMs = durationMs
            ) { progress ->
                onProgress("Menganalisis amplitudo musik...", 0.22f + 0.15f * progress)
            }

            onProgress("Memuat gambar cover...", 0.38f)
            val coverBitmap = loadCoverBitmap(context, mediaMetadata.thumbnailUrl)
            val caseBitmap = try {
                BitmapFactory.decodeResource(context.resources, R.drawable.lyric_video_cd_case)
            } catch (e: Exception) {
                null
            }

            val outputDir = File(context.cacheDir, "lyric_videos").apply { mkdirs() }
            val outputFile = File(outputDir, "NanzBeatles_${mediaMetadata.id}_${System.currentTimeMillis()}.mp4")

            val config = LyricVideoEncoder.EncodeConfig(
                audioFile = audioFile,
                outputFile = outputFile,
                coverBitmap = coverBitmap,
                lyrics = lyrics,
                songTitle = mediaMetadata.title,
                songArtist = mediaMetadata.artist,
                startTimeMs = startTimeMs,
                durationMs = durationMs,
                amplitudes = amplitudes,
                caseBitmap = caseBitmap
            )

            LyricVideoEncoder.encodeLyricVideo(config, onProgress)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to generate lyric video")
            Result.failure(e)
        }
    }

    /**
     * Shares the rendered lyric video MP4 file to a selected platform or system chooser.
     */
    fun shareLyricVideo(
        context: Context,
        videoFile: File,
        songTitle: String,
        songArtist: String,
        platform: ShareUtils.SharePlatform = ShareUtils.SharePlatform.GENERIC
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.FileProvider",
            videoFile
        )

        val shareText = "🎵 $songTitle - $songArtist\nVideo lirik dibuat dengan NanzBeatles"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (platform.packageName != null) {
                setPackage(platform.packageName)
            }
        }

        try {
            if (platform == ShareUtils.SharePlatform.GENERIC) {
                context.startActivity(Intent.createChooser(intent, "Bagikan Video Lirik lewat"))
            } else {
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            // Fallback to generic chooser if specific app isn't installed
            if (platform != ShareUtils.SharePlatform.GENERIC) {
                shareLyricVideo(context, videoFile, songTitle, songArtist, ShareUtils.SharePlatform.GENERIC)
            }
        }
    }
}
