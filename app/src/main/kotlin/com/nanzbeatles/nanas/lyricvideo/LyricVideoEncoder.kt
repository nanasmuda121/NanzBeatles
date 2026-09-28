/**
 * NanzBeatles Project (C) 2026
 * Licensed under GPL-3.0
 */

package com.nanzbeatles.nanas.lyricvideo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.nanzbeatles.nanas.lyrics.LyricsEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * High-performance on-device video encoder for NanzBeatles Lyric Videos.
 * Encodes 960x540 H.264 video using format-agnostic YUV via Image API and
 * muxes the original local audio into a finalized MP4 file with MediaMuxer.
 */
object LyricVideoEncoder {

    private const val TAG = "LyricVideoEncoder"
    private const val MIME_TYPE_VIDEO = MediaFormat.MIMETYPE_VIDEO_AVC // video/avc
    private const val VIDEO_WIDTH = 1280
    private const val VIDEO_HEIGHT = 720
    private const val VIDEO_FPS = 30
    private const val VIDEO_BITRATE = 4_000_000 // 4 Mbps for crisp 720p HD
    private const val I_FRAME_INTERVAL = 1 // Keyframe every second
    private const val TIMEOUT_US = 10_000L

    data class EncodeConfig(
        val audioFile: File,
        val outputFile: File,
        val coverBitmap: Bitmap?,
        val lyrics: List<LyricsEntry>?,
        val songTitle: String,
        val songArtist: String,
        val startTimeMs: Long,
        val durationMs: Long,
        val amplitudes: FloatArray,
        val caseBitmap: Bitmap? = null
    )

    /**
     * Executes the end-to-end rendering and encoding process.
     */
    suspend fun encodeLyricVideo(
        config: EncodeConfig,
        onProgress: (stage: String, progress: Float) -> Unit
    ): Result<File> = withContext(Dispatchers.Default) {
        val tempVideoFile = File(config.outputFile.parentFile, "temp_video_${System.currentTimeMillis()}.mp4")

        try {
            val totalFrames = maxOf(1, ((config.durationMs / 1000.0) * VIDEO_FPS).toInt())
            val frameDurationUs = 1_000_000L / VIDEO_FPS

            // Setup Video Encoder
            val videoFormat = MediaFormat.createVideoFormat(MIME_TYPE_VIDEO, VIDEO_WIDTH, VIDEO_HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            val encoder = MediaCodec.createEncoderByType(MIME_TYPE_VIDEO)
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val artistHandle = if (config.songArtist.isNotBlank()) {
                val clean = config.songArtist.trim()
                if (clean.startsWith("@")) clean else "@$clean"
            } else {
                "@NanzBeatles"
            }

            val renderer = LyricVideoRenderer(
                width = VIDEO_WIDTH,
                height = VIDEO_HEIGHT,
                caseBitmap = config.caseBitmap,
                brandText = "NanzBeatles",
                artistHandle = artistHandle
            ).apply {
                setCoverBitmap(config.coverBitmap)
            }

            val frameBitmap = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
            val frameCanvas = Canvas(frameBitmap)
            val argbPixels = IntArray(VIDEO_WIDTH * VIDEO_HEIGHT)

            val muxer = MediaMuxer(tempVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var isMuxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            var currentFrame = 0
            var isInputEOS = false
            var isOutputEOS = false

            Timber.tag(TAG).d("Starting video encode loop for $totalFrames frames")

            while (!isOutputEOS && coroutineContext.isActive) {
                // Feed input frames
                if (!isInputEOS) {
                    val inputBufferIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        if (currentFrame >= totalFrames) {
                            encoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                currentFrame * frameDurationUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEOS = true
                        } else {
                            val presentationTimeUs = currentFrame * frameDurationUs
                            val frameTimeMs = config.startTimeMs + (currentFrame * 1000L / VIDEO_FPS)

                            // Lookup amplitude for this frame
                            val ampIndex = (frameTimeMs * VIDEO_FPS / 1000L).toInt()
                            val amplitude = config.amplitudes.getOrElse(ampIndex) { 0.25f }

                            // Render frame to Bitmap
                            renderer.renderFrame(
                                frameCanvas,
                                frameTimeMs,
                                amplitude,
                                config.lyrics,
                                config.songTitle,
                                config.songArtist
                            )

                            // Copy Bitmap to Image YUV or InputBuffer NV12
                            val inputImage = try { encoder.getInputImage(inputBufferIndex) } catch (e: Exception) { null }
                            frameBitmap.getPixels(argbPixels, 0, VIDEO_WIDTH, 0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
                            if (inputImage != null) {
                                copyArgbToImageYuv(argbPixels, inputImage, VIDEO_WIDTH, VIDEO_HEIGHT)
                            } else {
                                val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                                if (inputBuffer != null) {
                                    copyArgbToNv12(argbPixels, inputBuffer, VIDEO_WIDTH, VIDEO_HEIGHT)
                                }
                            }

                            val frameBytesSize = (VIDEO_WIDTH * VIDEO_HEIGHT * 3) / 2
                            encoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                frameBytesSize,
                                presentationTimeUs,
                                0
                            )

                            currentFrame++
                            val encodeProgress = (currentFrame.toFloat() / totalFrames).coerceIn(0f, 1f)
                            onProgress("Merender video lirik...", 0.40f + 0.45f * encodeProgress)
                        }
                    }
                }

                // Drain output packets
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    isMuxerStarted = true
                    Timber.tag(TAG).d("Video encoder format changed, started muxer with track $videoTrackIndex")
                } else if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }

                    val encodedData = encoder.getOutputBuffer(outputBufferIndex)
                    if (encodedData != null && bufferInfo.size > 0 && isMuxerStarted) {
                        encodedData.position(bufferInfo.offset)
                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                }
            }

            try {
                encoder.stop()
                encoder.release()
            } catch (ignored: Exception) {}

            if (isMuxerStarted) {
                try {
                    muxer.stop()
                    muxer.release()
                } catch (ignored: Exception) {}
            }

            frameBitmap.recycle()

            if (!tempVideoFile.exists() || tempVideoFile.length() < 1024L) {
                error("Perangkat tidak dapat menghasilkan rekaman video. Silakan periksa izin atau coba lagi.")
            }

            onProgress("Menggabungkan audio & video...", 0.88f)

            // Step 4: Final Muxing with Original Audio Track
            muxVideoWithOriginalAudio(
                videoFile = tempVideoFile,
                audioFile = config.audioFile,
                outputFile = config.outputFile,
                startTimeMs = config.startTimeMs,
                durationMs = config.durationMs
            )

            tempVideoFile.delete()
            onProgress("Selesai!", 1.0f)
            Result.success(config.outputFile)

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Encoding failed")
            tempVideoFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Muxes the encoded video track with the trimmed original audio track from the local audio file.
     */
    private fun muxVideoWithOriginalAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        startTimeMs: Long,
        durationMs: Long
    ) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            if (!videoFile.exists() || videoFile.length() < 1024L) {
                error("Berkas video sementara tidak valid (${videoFile.length()} bytes)")
            }
            videoExtractor.setDataSource(videoFile.absolutePath)
            val videoTrack = selectFirstTrack(videoExtractor, "video/")
            if (videoTrack == -1) error("Video track not found in rendered video")
            videoExtractor.selectTrack(videoTrack)
            val videoFormat = videoExtractor.getTrackFormat(videoTrack)

            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            if (audioFile.exists() && audioFile.length() > 0) {
                try {
                    audioExtractor.setDataSource(audioFile.absolutePath)
                    audioTrack = selectFirstTrack(audioExtractor, "audio/")
                    if (audioTrack != -1) {
                        audioExtractor.selectTrack(audioTrack)
                        audioFormat = audioExtractor.getTrackFormat(audioTrack)
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Could not open audio file for muxing")
                }
            }

            if (outputFile.exists()) outputFile.delete()
            outputFile.parentFile?.mkdirs()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideoTrack = muxer.addTrack(videoFormat)
            val outAudioTrack = if (audioFormat != null) {
                try {
                    muxer.addTrack(audioFormat)
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Muxer does not support audio format: $audioFormat")
                    -1
                }
            } else -1

            if (outAudioTrack == -1 || audioTrack == -1) {
                error("Format audio tidak didukung oleh MediaMuxer atau trek audio tidak ditemukan. Pastikan stream audio AAC/MP4 tersedia.")
            }

            muxer.start()

            // Seek audio to starting point
            val startUs = startTimeMs * 1000L
            val endUs = (startTimeMs + durationMs) * 1000L

            audioExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (audioExtractor.sampleTime in 0 until startUs) {
                // Keep the boundary sample covering startUs to avoid losing the first syllable
                if (audioExtractor.sampleTime >= startUs - 12_000L) {
                    break
                }
                audioExtractor.advance()
            }

            // Interleaved copying: Video & Audio in presentation timestamp order
            val videoBuffer = ByteBuffer.allocateDirect(1024 * 1024)
            val audioBuffer = ByteBuffer.allocateDirect(256 * 1024)
            val videoInfo = MediaCodec.BufferInfo()
            val audioInfo = MediaCodec.BufferInfo()

            var videoDone = false
            var audioDone = false

            while (!videoDone || !audioDone) {
                val currentVideoTime = if (!videoDone) videoExtractor.sampleTime else Long.MAX_VALUE
                val rawAudioTime = if (!audioDone) audioExtractor.sampleTime else Long.MAX_VALUE
                val currentAudioTime = if (rawAudioTime in 0..endUs) {
                    maxOf(0L, rawAudioTime - startUs)
                } else {
                    Long.MAX_VALUE
                }

                if (!videoDone && (audioDone || currentVideoTime <= currentAudioTime)) {
                    videoBuffer.clear()
                    val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
                    if (sampleSize < 0) {
                        videoDone = true
                    } else {
                        videoInfo.offset = 0
                        videoInfo.size = sampleSize
                        videoInfo.presentationTimeUs = videoExtractor.sampleTime
                        videoInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(outVideoTrack, videoBuffer, videoInfo)
                        videoExtractor.advance()
                    }
                } else if (!audioDone) {
                    audioBuffer.clear()
                    val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                    val sampleTime = audioExtractor.sampleTime

                    if (sampleSize < 0 || sampleTime > endUs) {
                        audioDone = true
                    } else {
                        audioInfo.offset = 0
                        audioInfo.size = sampleSize
                        audioInfo.presentationTimeUs = maxOf(0L, sampleTime - startUs)
                        audioInfo.flags = audioExtractor.sampleFlags
                        muxer.writeSampleData(outAudioTrack, audioBuffer, audioInfo)
                        audioExtractor.advance()
                    }
                }
            }

            Timber.tag(TAG).d("Muxed final video file: ${outputFile.length()} bytes")

        } finally {
            try { videoExtractor.release() } catch (ignored: Exception) {}
            try { audioExtractor.release() } catch (ignored: Exception) {}
            try {
                muxer?.stop()
                muxer?.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun selectFirstTrack(extractor: MediaExtractor, prefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith(prefix)) return i
        }
        return -1
    }

    /**
     * Converts ARGB pixels to format-agnostic YUV Image planes (NV12 / I420).
     */
    private fun copyArgbToImageYuv(argb: IntArray, image: Image, width: Int, height: Int) {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vPixelStride = vPlane.pixelStride

        // Fast Y plane fill
        for (y in 0 until height) {
            val yOffset = y * yRowStride
            val argbRowOffset = y * width
            for (x in 0 until width) {
                val c = argb[argbRowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yVal = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuffer.put(yOffset + x, yVal.coerceIn(0, 255).toByte())
            }
        }

        // Subsampled Chroma (U and V)
        for (y in 0 until height / 2) {
            val uRowOffset = y * uRowStride
            val vRowOffset = y * vRowStride
            val argbRowOffset = (y * 2) * width
            for (x in 0 until width / 2) {
                val c = argb[argbRowOffset + (x * 2)]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val uVal = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val vVal = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                uBuffer.put(uRowOffset + (x * uPixelStride), uVal.coerceIn(0, 255).toByte())
                vBuffer.put(vRowOffset + (x * vPixelStride), vVal.coerceIn(0, 255).toByte())
            }
        }
    }

    /**
     * Converts ARGB pixels to standard NV12 ByteBuffer when Image API is not supported.
     */
    private fun copyArgbToNv12(argb: IntArray, buffer: ByteBuffer, width: Int, height: Int) {
        buffer.clear()
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val c = argb[rowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val yVal = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                buffer.put(yIndex++, yVal.coerceIn(0, 255).toByte())

                if (y % 2 == 0 && x % 2 == 0) {
                    val uVal = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val vVal = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    buffer.put(uvIndex++, uVal.coerceIn(0, 255).toByte())
                    buffer.put(uvIndex++, vVal.coerceIn(0, 255).toByte())
                }
            }
        }
    }
}
