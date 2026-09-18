/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.auramusic.app.eq.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Karaoke / "Sing"-style vocal remover.
 *
 * This is the strongest practical real-time, on-device, no-FFT approach.
 * It is not Apple-Music-Sing-quality on every song (Apple uses a server-side
 * ML stem-separator and even then "doesn't fully remove vocals" — see
 * apple.com/newsroom 2022-12-06 footnote 1). Without an ML model the
 * physics limit is reached when widened/reverberant vocals leak via the
 * side channel. This implementation gets as close as DSP allows while
 * keeping the instrumental at perceived original loudness.
 *
 * Pipeline (per stereo sample, all maths in normalised float):
 *
 *   1. Decompose into mid/side:
 *        mid  = (L + R) / 2     (centred: vocals, kick, bass, snare body)
 *        side = (L - R) / 2     (stereo content: guitars, hats, reverb)
 *
 *   2. Split the centre into three bands using exact-summing residuals
 *      so the bands always recombine without comb artefacts:
 *        low      = LP(mid, 180 Hz)         (kick + bass — never touched)
 *        residual = mid - low
 *        presence = LP(residual, 4.2 kHz)   (vocal body — main suppression)
 *        air      = residual - presence     (sibilance / cymbals — light)
 *
 *   3. Estimate "centre dominance" per band via fast envelope followers
 *      and only suppress where the centre actually outweighs the sides.
 *      This stops centred snares / instruments from being gutted when
 *      no vocal is present, which was why "the beats sound suppressed".
 *
 *   4. Recombine with a modest +2.6 dB side lift (equal-power-ish, not
 *      +6 dB which over-widens and amplifies leaked reverb).
 *
 *   5. Short-term RMS loudness make-up so quiet sections aren't dropped,
 *      followed by a soft peak limiter to avoid clipping.
 */
@UnstableApi
class VocalSuppressionAudioProcessor : BaseAudioProcessor() {

    @Volatile private var vocalSuppressionEnabled = false
    @Volatile private var suppressionStrength = 1.0f

    private var sampleRate = 44100
    private var channelCount = 2

    // Band-split filters on the mid signal.
    private var lpBass: BiquadFilter? = null
    private var lpPresence: BiquadFilter? = null

    // Envelope followers (one-pole, asymmetric attack/release).
    private val envPresence = EnvFollower()
    private val envAir = EnvFollower()
    private val envSide = EnvFollower()
    private val envInPow = EnvFollower()
    private val envOutPow = EnvFollower()
    private val envMakeup = EnvFollower()

    // Smoothed gain applied to each suppression band, prevents zipper noise.
    private var smoothedGPresence = 1f
    private var smoothedGAir = 1f
    private var gainSmoothCoeff = 0f

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount

        if (channelCount == 2) {
            lpBass = BiquadFilter.lowPass(180f, sampleRate, 0.707f)
            lpPresence = BiquadFilter.lowPass(4200f, sampleRate, 0.707f)

            // Envelope time-constants tuned to musical material.
            envPresence.configure(sampleRate, attackMs = 5f, releaseMs = 60f)
            envAir.configure(sampleRate, attackMs = 5f, releaseMs = 80f)
            envSide.configure(sampleRate, attackMs = 5f, releaseMs = 80f)
            envInPow.configure(sampleRate, attackMs = 20f, releaseMs = 150f)
            envOutPow.configure(sampleRate, attackMs = 20f, releaseMs = 150f)
            envMakeup.configure(sampleRate, attackMs = 30f, releaseMs = 200f)

            // ~5 ms one-pole smoothing for the per-band suppression gain.
            gainSmoothCoeff = exp(-1f / (0.005f * sampleRate))
        } else {
            lpBass = null
            lpPresence = null
        }

        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Pass-through when disabled or for mono sources (centre extraction
        // requires two distinct channels).
        if (!vocalSuppressionEnabled || channelCount != 2) {
            val out = replaceOutputBuffer(remaining)
            out.put(inputBuffer)
            out.flip()
            return
        }

        processStereo(inputBuffer)
    }

    private fun processStereo(inputBuffer: ByteBuffer) {
        val byteCount = inputBuffer.remaining()
        // 2 bytes/sample × 2 channels = 4 bytes per stereo frame.
        val frameCount = byteCount / 4

        val output = replaceOutputBuffer(byteCount)

        val srcOrder = inputBuffer.order()
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)

        val lpB = lpBass!!
        val lpP = lpPresence!!
        val strength = suppressionStrength

        // Side make-up gain. ~+2.6 dB. Equal-power compensation when mid is
        // attenuated would be +3 dB; we stay just under that so reverb tails
        // aren't pushed forward.
        val sideGain = 1.35f
        // Hard cap on RMS make-up so we don't blow up silent passages.
        val makeupCeiling = 1.45f

        val gSmooth = gainSmoothCoeff
        val invNorm = 1f / 32768f

        for (i in 0 until frameCount) {
            val l = inputBuffer.short.toFloat() * invNorm
            val r = inputBuffer.short.toFloat() * invNorm

            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f

            // Exact-summing 3-band split of the mid signal.
            val low = lpB.process(mid)
            val residual = mid - low
            val presence = lpP.process(residual)
            val air = residual - presence

            // Per-band envelopes used to estimate centre dominance.
            val pE = envPresence.process(abs(presence))
            val aE = envAir.process(abs(air))
            val sE = envSide.process(abs(side))

            // How "centre-dominant" each band is right now. When a band is
            // dominated by stereo content we leave it alone — this is what
            // stops centred snares / panned drums from being gutted.
            val pDom = pE / (pE + 1.2f * sE + 1e-6f)
            val aDom = aE / (aE + 1.8f * sE + 1e-6f)

            val gP = 1f - strength * smoothstep(pDom, 0.55f, 0.85f)
            val gA = 1f - 0.35f * strength * smoothstep(aDom, 0.75f, 0.95f)

            // One-pole smoothing of the suppression gains.
            smoothedGPresence = gSmooth * smoothedGPresence + (1f - gSmooth) * gP
            smoothedGAir = gSmooth * smoothedGAir + (1f - gSmooth) * gA

            val keptMid = low + smoothedGPresence * presence + smoothedGAir * air

            var rawL = keptMid + sideGain * side
            var rawR = keptMid - sideGain * side

            // Short-term RMS loudness make-up.
            val inPow = 0.5f * (l * l + r * r)
            val outPow = 0.5f * (rawL * rawL + rawR * rawR)
            val inE = envInPow.process(inPow)
            val outE = envOutPow.process(outPow)
            val target = sqrt((inE + 1e-9f) / (outE + 1e-9f))
                .coerceIn(1f, makeupCeiling)
            val makeup = envMakeup.process(target)

            rawL *= makeup
            rawR *= makeup

            // Soft peak limiter (better than hard clamp — it pulls down both
            // channels together so the stereo image is preserved).
            val peak = max(abs(rawL), abs(rawR))
            if (peak > 0.99f) {
                val g = 0.99f / peak
                rawL *= g
                rawR *= g
            }

            output.putShort((rawL * 32767f).toInt().coerceIn(-32768, 32767).toShort())
            output.putShort((rawR * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }

        inputBuffer.order(srcOrder)
        output.flip()
    }

    override fun onFlush() {
        lpBass?.reset()
        lpPresence?.reset()
        envPresence.reset()
        envAir.reset()
        envSide.reset()
        envInPow.reset()
        envOutPow.reset()
        envMakeup.reset()
        smoothedGPresence = 1f
        smoothedGAir = 1f
    }

    override fun onReset() {
        lpBass = null
        lpPresence = null
    }

    fun enable(strength: Float = 1.0f) {
        suppressionStrength = strength.coerceIn(0f, 1f)
        vocalSuppressionEnabled = true
        flush()
    }

    fun disable() {
        vocalSuppressionEnabled = false
        flush()
    }

    fun isEnabled(): Boolean = vocalSuppressionEnabled
    fun getSuppressionStrength(): Float = suppressionStrength

    private fun smoothstep(x: Float, e0: Float, e1: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * One-pole envelope follower with separate attack and release time
     * constants. Operates on the rectified magnitude of its input.
     */
    private class EnvFollower {
        private var attackCoeff = 0f
        private var releaseCoeff = 0f
        private var y = 0f

        fun configure(sampleRate: Int, attackMs: Float, releaseMs: Float) {
            attackCoeff = exp(-1f / ((attackMs / 1000f) * sampleRate))
            releaseCoeff = exp(-1f / ((releaseMs / 1000f) * sampleRate))
        }

        fun process(x: Float): Float {
            y = if (x > y) attackCoeff * y + (1f - attackCoeff) * x
            else releaseCoeff * y + (1f - releaseCoeff) * x
            return y
        }

        fun reset() { y = 0f }
    }

    /** RBJ biquad — only the LP variant is needed here. */
    private class BiquadFilter(
        private val b0: Float,
        private val b1: Float,
        private val b2: Float,
        private val a1: Float,
        private val a2: Float,
    ) {
        private var x1 = 0f
        private var x2 = 0f
        private var y1 = 0f
        private var y2 = 0f

        fun process(x0: Float): Float {
            val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x0
            y2 = y1; y1 = y0
            return y0
        }

        fun reset() {
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }

        companion object {
            fun lowPass(freq: Float, sampleRate: Int, q: Float): BiquadFilter {
                val w0 = 2f * PI.toFloat() * freq / sampleRate
                val cosW = cos(w0)
                val alpha = sin(w0) / (2f * q)
                val a0 = 1f + alpha
                val b0 = ((1f - cosW) / 2f) / a0
                val b1 = (1f - cosW) / a0
                val b2 = ((1f - cosW) / 2f) / a0
                val a1 = (-2f * cosW) / a0
                val a2 = (1f - alpha) / a0
                return BiquadFilter(b0, b1, b2, a1, a2)
            }
        }
    }
}
