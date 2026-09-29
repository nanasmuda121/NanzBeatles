/**
 * NanzBeatles Project (C) 2026
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
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.nio.ByteBuffer
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.nanzbeatles.nanas.constants.AudioQuality
import com.nanzbeatles.nanas.constants.AudioQualityKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardAlphaPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardOffsetXPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardOffsetYPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardScalePercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyle
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyleKey
import com.nanzbeatles.nanas.constants.VideoLyricsOffsetXPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsOffsetYPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsScalePercentKey
import com.nanzbeatles.nanas.lyrics.LyricsEntry
import com.nanzbeatles.nanas.models.MediaMetadata
import com.nanzbeatles.nanas.utils.ShareUtils
import com.nanzbeatles.nanas.utils.YTPlayerUtils
import com.nanzbeatles.nanas.utils.dataStore
import com.nanzbeatles.nanas.utils.enumPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object LyricVideoShareUtils {

    private const val TAG = "LyricVideoShareUtils"

    /**
     * Checks if the given local file is a valid, readable AAC audio file that MediaExtractor and MediaMuxer can handle.
     */
    fun isValidAudioFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4 * 1024L) return false
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var hasAacAudio = false
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) ||
                    mime.contains("mp4a", ignoreCase = true) ||
                    mime.startsWith("audio/mp4", ignoreCase = true)) {
                    hasAacAudio = true
                    break
                }
            }
            hasAacAudio
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Audio file validation failed for: ${file.absolutePath}")
            false
        } finally {
            try { extractor.release() } catch (ignored: Exception) {}
        }
    }

    private fun extractClipFromExtractor(
        extractor: MediaExtractor,
        targetFile: File,
        startTimeMs: Long,
        durationMs: Long
    ): Boolean {
        var muxer: MediaMuxer? = null
        try {
            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrack == -1 || audioFormat == null) return false
            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (!mime.equals(MediaFormat.MIMETYPE_AUDIO_AAC, ignoreCase = true) &&
                !mime.contains("mp4a", ignoreCase = true) &&
                !mime.startsWith("audio/mp4", ignoreCase = true)) {
                // MediaMuxer with MUXER_OUTPUT_MPEG_4 requires AAC for direct remuxing
                return false
            }

            extractor.selectTrack(audioTrack)

            if (targetFile.exists()) targetFile.delete()
            targetFile.parentFile?.mkdirs()

            muxer = MediaMuxer(targetFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val startUs = startTimeMs * 1000L
            val endUs = (startTimeMs + durationMs) * 1000L

            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                while (extractor.sampleTime in 0 until startUs) {
                    if (extractor.sampleTime >= startUs - 12_000L) {
                        break
                    }
                    extractor.advance()
                }
            }

            val buffer = ByteBuffer.allocateDirect(256 * 1024)
            val info = MediaCodec.BufferInfo()
            var firstSampleTimeUs = -1L
            var lastPtsUs = -1L

            while (true) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0 || sampleTime > endUs) break

                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                if (firstSampleTimeUs == -1L) {
                    firstSampleTimeUs = sampleTime
                }

                info.offset = 0
                info.size = sampleSize
                var pts = maxOf(0L, sampleTime - firstSampleTimeUs)
                if (pts <= lastPtsUs) {
                    pts = lastPtsUs + 1000L
                }
                lastPtsUs = pts
                info.presentationTimeUs = pts
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            muxer = null

            return targetFile.exists() && targetFile.length() > 4 * 1024L
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "extractClipFromExtractor direct remux failed")
            targetFile.delete()
            return false
        } finally {
            try { muxer?.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Transcodes any audio format (Opus, Vorbis, WebM, MP3, FLAC) to standard MP4 AAC
     * using MediaCodec decoder and encoder. Guarantees 100% compatibility for long videos.
     */
    private fun transcodeAudioToAac(
        sourceFile: File,
        targetFile: File,
        startTimeMs: Long,
        durationMs: Long
    ): Boolean {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            extractor.setDataSource(sourceFile.absolutePath)
            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    inputFormat = format
                    break
                }
            }
            if (audioTrack == -1 || inputFormat == null) return false
            extractor.selectTrack(audioTrack)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return false
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val aacFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            if (targetFile.exists()) targetFile.delete()
            targetFile.parentFile?.mkdirs()
            muxer = MediaMuxer(targetFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerTrack = -1
            var muxerStarted = false

            val startUs = startTimeMs * 1000L
            val endUs = (startTimeMs + durationMs) * 1000L
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }

            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            var lastAudioPtsUs = -1L
            var firstPcmPtsUs = -1L

            val timeoutUs = 5000L

            while (!encoderDone) {
                // 1. Feed extractor into decoder
                if (!extractorDone) {
                    val inIdx = decoder.dequeueInputBuffer(timeoutUs)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            inBuf.clear()
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            val sampleTime = extractor.sampleTime
                            if (sampleSize < 0 || (sampleTime > endUs && sampleTime > 0)) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                extractorDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, sampleSize, sampleTime, extractor.sampleFlags)
                                extractor.advance()
                            }
                        }
                    }
                }

                // 2. Drain decoder and feed into encoder
                if (!decoderDone) {
                    val decOutIdx = decoder.dequeueOutputBuffer(decoderBufferInfo, timeoutUs)
                    if (decOutIdx >= 0) {
                        val decBuf = decoder.getOutputBuffer(decOutIdx)
                        val isEos = (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                        if (decBuf != null && decoderBufferInfo.size > 0 && decoderBufferInfo.presentationTimeUs >= startUs) {
                            if (firstPcmPtsUs == -1L) {
                                firstPcmPtsUs = decoderBufferInfo.presentationTimeUs
                            }
                            val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                            if (encInIdx >= 0) {
                                val encInBuf = encoder.getInputBuffer(encInIdx)
                                if (encInBuf != null) {
                                    encInBuf.clear()
                                    decBuf.position(decoderBufferInfo.offset)
                                    decBuf.limit(decoderBufferInfo.offset + decoderBufferInfo.size)
                                    encInBuf.put(decBuf)
                                    val pts = maxOf(0L, decoderBufferInfo.presentationTimeUs - firstPcmPtsUs)
                                    encoder.queueInputBuffer(encInIdx, 0, decoderBufferInfo.size, pts, 0)
                                }
                            }
                        }

                        decoder.releaseOutputBuffer(decOutIdx, false)
                        if (isEos) {
                            val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                            if (encInIdx >= 0) {
                                encoder.queueInputBuffer(encInIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            }
                            decoderDone = true
                        }
                    }
                }

                // 3. Drain encoder and write to muxer
                val encOutIdx = encoder.dequeueOutputBuffer(encoderBufferInfo, timeoutUs)
                if (encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    muxerTrack = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else if (encOutIdx >= 0) {
                    if ((encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        encoderDone = true
                    }
                    val encOutBuf = encoder.getOutputBuffer(encOutIdx)
                    if (encOutBuf != null && encoderBufferInfo.size > 0 && muxerStarted) {
                        encOutBuf.position(encoderBufferInfo.offset)
                        encOutBuf.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                        var pts = encoderBufferInfo.presentationTimeUs
                        if (pts <= lastAudioPtsUs) {
                            pts = lastAudioPtsUs + 1000L
                        }
                        lastAudioPtsUs = pts
                        encoderBufferInfo.presentationTimeUs = pts
                        muxer.writeSampleData(muxerTrack, encOutBuf, encoderBufferInfo)
                    }
                    encoder.releaseOutputBuffer(encOutIdx, false)
                }
            }

            if (muxerStarted) {
                muxer.stop()
                muxer.release()
                muxer = null
            }

            return targetFile.exists() && targetFile.length() > 4 * 1024L
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "transcodeAudioToAac failed")
            targetFile.delete()
            return false
        } finally {
            try { extractor.release() } catch (ignored: Exception) {}
            try { decoder?.stop(); decoder?.release() } catch (ignored: Exception) {}
            try { encoder?.stop(); encoder?.release() } catch (ignored: Exception) {}
            try { muxer?.release() } catch (ignored: Exception) {}
        }
    }

    private fun extractClipFromFile(
        sourceFile: File,
        targetFile: File,
        startTimeMs: Long,
        durationMs: Long
    ): Boolean {
        // 1. Try direct remux if audio is already AAC (fastest)
        val extractor = MediaExtractor()
        val directOk = try {
            extractor.setDataSource(sourceFile.absolutePath)
            extractClipFromExtractor(extractor, targetFile, startTimeMs, durationMs)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "extractClipFromExtractor direct remux failed")
            false
        } finally {
            try { extractor.release() } catch (ignored: Exception) {}
        }

        if (directOk && isValidAudioFile(targetFile)) {
            return true
        }

        // 2. Transcode to standard AAC via MediaCodec (supports Opus, WebM, MP3, etc.)
        Timber.tag(TAG).d("Falling back to MediaCodec transcode to AAC")
        return transcodeAudioToAac(sourceFile, targetFile, startTimeMs, durationMs)
    }

    private fun extractClipFromStream(
        context: Context,
        streamUrl: String,
        headers: Map<String, String>,
        targetFile: File,
        startTimeMs: Long,
        durationMs: Long
    ): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, Uri.parse(streamUrl), headers)
            extractClipFromExtractor(extractor, targetFile, startTimeMs, durationMs)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "extractClipFromStream failed")
            false
        } finally {
            try { extractor.release() } catch (ignored: Exception) {}
        }
    }

    /**
     * Resolves strictly the selected audio range (startTimeMs to startTimeMs + durationMs).
     * Extracts only the selected duration without downloading the full song when possible.
     */
    suspend fun resolveAudioClip(
        context: Context,
        mediaId: String,
        startTimeMs: Long,
        durationMs: Long,
        downloadCache: SimpleCache? = null,
        playerCache: SimpleCache? = null,
        onProgress: ((String, Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val audioDir = File(context.cacheDir, "temp_lyric_audio").apply { mkdirs() }
        val targetClipFile = File(audioDir, "clip_${mediaId}_${startTimeMs}_${durationMs}.m4a")

        if (targetClipFile.exists() && isValidAudioFile(targetClipFile)) {
            Timber.tag(TAG).d("Reusing existing valid audio clip: ${targetClipFile.absolutePath}")
            return@withContext targetClipFile
        }

        onProgress?.invoke("Menyiapkan segmen audio...", 0.05f)

        // 1. Check playerCache and downloadCache (offline / in-memory cache)
        val activeCaches = listOfNotNull(downloadCache, playerCache)
        for (cache in activeCaches) {
            val matchingKey = cache.keys.firstOrNull { it == mediaId || it.contains(mediaId) }
            if (matchingKey != null) {
                val tempOffline = File(audioDir, "temp_cache_${mediaId}_${System.currentTimeMillis()}.tmp")
                if (copyFromCache(cache, matchingKey, tempOffline)) {
                    if (tempOffline.exists() && tempOffline.length() > 4 * 1024L) {
                        onProgress?.invoke("Menyiapkan audio dari cache...", 0.10f)
                        val ok = extractClipFromFile(tempOffline, targetClipFile, startTimeMs, durationMs)
                        tempOffline.delete()
                        if (ok && isValidAudioFile(targetClipFile)) {
                            Timber.tag(TAG).d("Audio clip extracted from cache: ${targetClipFile.length()} bytes")
                            return@withContext targetClipFile
                        }
                    } else {
                        tempOffline.delete()
                    }
                }
            }
        }

        // 2. Stream online directly according to selected duration
        val connectivityManager = context.getSystemService<ConnectivityManager>()
            ?: error("ConnectivityManager tidak tersedia")

        onProgress?.invoke("Menghubungkan ke stream audio...", 0.08f)
        val playbackData = YTPlayerUtils.playerResponseForPlayback(
            videoId = mediaId,
            audioQuality = AudioQuality.HIGH,
            connectivityManager = connectivityManager,
            preferMp4Audio = true
        ).getOrThrow()

        // 3. Extract clip directly from stream without downloading full audio
        val durSec = durationMs / 1000L
        onProgress?.invoke("Mengunduh audio segmen ($durSec detik)...", 0.12f)
        val streamSuccess = try {
            extractClipFromStream(
                context = context,
                streamUrl = playbackData.streamUrl,
                headers = playbackData.streamHeaders,
                targetFile = targetClipFile,
                startTimeMs = startTimeMs,
                durationMs = durationMs
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Direct stream extraction failed, falling back to temp download")
            false
        }

        if (streamSuccess && isValidAudioFile(targetClipFile)) {
            Timber.tag(TAG).d("Audio clip extracted directly from stream: ${targetClipFile.length()} bytes")
            return@withContext targetClipFile
        }

        // 4. Fallback: Download temp audio with generous timeout and streaming buffer
        onProgress?.invoke("Mengunduh audio...", 0.14f)
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()

        val requestBuilder = Request.Builder().url(playbackData.streamUrl)
        playbackData.streamHeaders.forEach { (k, v) -> requestBuilder.addHeader(k, v) }
        val request = requestBuilder.build()
        val tempDownloadFile = File(audioDir, "temp_dl_${mediaId}_${System.currentTimeMillis()}.tmp")

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Gagal mengunduh audio: HTTP ${response.code}")
            val body = response.body ?: error("Empty audio response body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            FileOutputStream(tempDownloadFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                val inStream = body.byteStream()
                var read: Int
                while (inStream.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    if (totalBytes > 0) {
                        val prog = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                        onProgress?.invoke("Mengunduh audio (${(prog * 100).toInt()}%)...", 0.14f + 0.08f * prog)
                    }
                }
            }
        }

        val extractSuccess = extractClipFromFile(tempDownloadFile, targetClipFile, startTimeMs, durationMs)
        tempDownloadFile.delete() // Guarantee full audio is NEVER kept, only the selected clip!

        if (!extractSuccess || !isValidAudioFile(targetClipFile)) {
            targetClipFile.delete()
            error("Gagal mengekstrak segmen audio yang dipilih")
        }

        Timber.tag(TAG).d("Audio clip extracted via fallback: ${targetClipFile.length()} bytes")
        targetClipFile
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
        cardStyle: VideoLyricsCardStyle? = null,
        downloadCache: SimpleCache? = null,
        playerCache: SimpleCache? = null,
        onProgress: (stage: String, progress: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        try {
            onProgress("Menyiapkan segmen audio lagu...", 0.05f)
            val audioFile = resolveAudioClip(
                context = context,
                mediaId = mediaMetadata.id,
                startTimeMs = startTimeMs,
                durationMs = durationMs,
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

            val prefs = context.dataStore.data.first()
            val styleStr = prefs[VideoLyricsCardStyleKey] ?: VideoLyricsCardStyle.NORMAL.name
            val effectiveCardStyle = cardStyle ?: try {
                VideoLyricsCardStyle.valueOf(styleStr)
            } catch (e: Exception) {
                VideoLyricsCardStyle.NORMAL
            }
            val scalePercent = prefs[VideoLyricsScalePercentKey] ?: 80
            val offsetXPercent = prefs[VideoLyricsOffsetXPercentKey] ?: 0
            val offsetYPercent = prefs[VideoLyricsOffsetYPercentKey] ?: 0
            val cardScalePercent = prefs[VideoLyricsCardScalePercentKey] ?: 100
            val cardOffsetXPercent = prefs[VideoLyricsCardOffsetXPercentKey] ?: 0
            val cardOffsetYPercent = prefs[VideoLyricsCardOffsetYPercentKey] ?: 0
            val cardAlphaPercent = prefs[VideoLyricsCardAlphaPercentKey] ?: 100

            val lyricsScale = (scalePercent / 80f).coerceIn(0.4f, 1.5f)
            val lyricsOffsetX = (offsetXPercent / 100f) * 200f
            val lyricsOffsetY = (offsetYPercent / 100f) * 150f
            val cardScale = (cardScalePercent / 100f).coerceIn(0.4f, 1.6f)
            val cardOffsetX = (cardOffsetXPercent / 100f) * 200f
            val cardOffsetY = (cardOffsetYPercent / 100f) * 150f
            val cardAlpha = (cardAlphaPercent / 100f).coerceIn(0f, 1f)

            val config = LyricVideoEncoder.EncodeConfig(
                audioFile = audioFile,
                outputFile = outputFile,
                coverBitmap = coverBitmap,
                lyrics = lyrics,
                songTitle = mediaMetadata.title,
                songArtist = mediaMetadata.artists.joinToString { it.name },
                startTimeMs = startTimeMs,
                durationMs = durationMs,
                amplitudes = amplitudes,
                caseBitmap = caseBitmap,
                cardStyle = effectiveCardStyle,
                lyricsScale = lyricsScale,
                lyricsOffsetX = lyricsOffsetX,
                lyricsOffsetY = lyricsOffsetY,
                cardScale = cardScale,
                cardOffsetX = cardOffsetX,
                cardOffsetY = cardOffsetY,
                cardAlpha = cardAlpha
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

    /**
     * Saves the generated lyric video MP4 directly to the device Gallery (Movies/NanzBeatles).
     */
    fun saveVideoToGallery(
        context: Context,
        videoFile: File,
        songTitle: String,
        songArtist: String
    ): Uri? {
        if (!videoFile.exists() || videoFile.length() == 0L) return null
        return try {
            val safeTitle = songTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
            val fileName = "NanzBeatles_${safeTitle}_${System.currentTimeMillis()}.mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/NanzBeatles")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return null

                resolver.openOutputStream(uri)?.use { outStream ->
                    videoFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "NanzBeatles"
                ).apply { mkdirs() }
                val targetFile = File(moviesDir, fileName)
                videoFile.copyTo(targetFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf("video/mp4"),
                    null
                )
                Uri.fromFile(targetFile)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save video to gallery")
            null
        }
    }
}
