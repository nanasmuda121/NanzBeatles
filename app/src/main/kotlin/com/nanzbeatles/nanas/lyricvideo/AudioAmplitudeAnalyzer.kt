/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.lyricvideo

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline PCM audio analyzer that extracts per-frame amplitude envelope
 * for rendering dynamic, real-audio-reactive waveforms in exported lyric videos.
 */
object AudioAmplitudeAnalyzer {

    private const val TAG = "AudioAmplitudeAnalyzer"
    private const val TIMEOUT_US = 5000L

    /**
     * Extracts normalized amplitude per frame [0.0f..1.0f] for the given audio file.
     *
     * @param audioFile Local audio file
     * @param fps Video frame rate (typically 30 fps)
     * @param totalDurationMs Expected duration in milliseconds
     * @return FloatArray with one normalized amplitude value per video frame
     */
    fun analyzeAmplitudes(
        audioFile: File,
        fps: Int = 30,
        totalDurationMs: Long = 0L,
        onProgress: ((Float) -> Unit)? = null
    ): FloatArray {
        if (!audioFile.exists() || audioFile.length() == 0L) {
            Timber.tag(TAG).w("Audio file not available or empty, using procedural fallback")
            return generateProceduralAmplitudes(fps, totalDurationMs)
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(audioFile.absolutePath)

            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            var mimeType = ""

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    mimeType = mime
                    break
                }
            }

            if (audioTrackIndex == -1 || audioFormat == null) {
                Timber.tag(TAG).w("No audio track found in file, using procedural fallback")
                return generateProceduralAmplitudes(fps, totalDurationMs)
            }

            extractor.selectTrack(audioTrackIndex)

            val trackDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
                audioFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                totalDurationMs * 1000L
            }

            val estimatedTotalFrames = maxOf(1, ((trackDurationUs / 1_000_000.0) * fps).toInt())
            val frameDurationUs = 1_000_000L / fps

            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(audioFormat, null, null, 0)
            codec.start()

            val rawAmplitudes = mutableListOf<Float>()
            var currentFrameSamplesSum = 0.0
            var currentFrameSampleCount = 0L
            var currentFrameIndex = 0

            val bufferInfo = MediaCodec.BufferInfo()
            var isInputEOS = false
            var isOutputEOS = false

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    val inputBufferIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex) ?: continue
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isInputEOS = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                if (outputBufferIndex >= 0) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isOutputEOS = true
                    }

                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)

                        val presentationTimeUs = bufferInfo.presentationTimeUs
                        val targetFrame = (presentationTimeUs / frameDurationUs).toInt()

                        while (outputBuffer.remaining() >= 2) {
                            val sample = outputBuffer.short.toDouble()
                            currentFrameSamplesSum += sample * sample
                            currentFrameSampleCount++
                        }

                        if (targetFrame > currentFrameIndex && currentFrameSampleCount > 0) {
                            val rms = sqrt(currentFrameSamplesSum / currentFrameSampleCount)
                            val normalized = (rms / 32768.0).toFloat().coerceIn(0.0f, 1.0f)
                            
                            while (currentFrameIndex < targetFrame) {
                                rawAmplitudes.add(normalized)
                                currentFrameIndex++
                            }

                            currentFrameSamplesSum = 0.0
                            currentFrameSampleCount = 0L

                            if (estimatedTotalFrames > 0) {
                                onProgress?.invoke(
                                    (currentFrameIndex.toFloat() / estimatedTotalFrames).coerceIn(0f, 1f)
                                )
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputBufferIndex, false)
                }
            }

            // Flush remaining frame if any
            if (currentFrameSampleCount > 0) {
                val rms = sqrt(currentFrameSamplesSum / currentFrameSampleCount)
                rawAmplitudes.add((rms / 32768.0).toFloat().coerceIn(0.0f, 1.0f))
            }

            if (rawAmplitudes.isEmpty()) {
                return generateProceduralAmplitudes(fps, totalDurationMs)
            }

            // Normalize peak values to fill good visual range [0.15f..1.0f]
            val maxAmp = rawAmplitudes.maxOrNull() ?: 1.0f
            val scale = if (maxAmp > 0.001f) 1.0f / maxAmp else 1.0f

            val result = FloatArray(rawAmplitudes.size) { i ->
                val scaled = (rawAmplitudes[i] * scale).coerceIn(0.0f, 1.0f)
                // Add soft noise floor for aesthetic visuals
                0.12f + 0.88f * scaled
            }

            Timber.tag(TAG).d("Decoded ${result.size} amplitude frames for video")
            return result

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during offline PCM decoding, falling back to procedural amplitude")
            return generateProceduralAmplitudes(fps, totalDurationMs)
        } finally {
            try {
                codec?.stop()
                codec?.release()
                extractor.release()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Procedural fallback generating natural, rhythmic visual pulses
     * when an audio stream cannot be decoded offline.
     */
    fun generateProceduralAmplitudes(fps: Int, durationMs: Long): FloatArray {
        val count = maxOf(30, ((durationMs / 1000.0) * fps).toInt())
        return FloatArray(count) { frame ->
            val t = frame.toDouble() / fps
            // Combination of musical beats (approx 120 BPM = 2 Hz + sub harmonics)
            val beat1 = abs(sin(t * Math.PI * 2.0))
            val beat2 = abs(sin(t * Math.PI * 4.0)) * 0.5
            val beat3 = abs(sin(t * Math.PI * 0.67)) * 0.3
            ((beat1 + beat2 + beat3) / 1.8).toFloat().coerceIn(0.15f, 1.0f)
        }
    }
}
