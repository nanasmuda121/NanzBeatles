/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.lyricvideo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyle
import com.nanzbeatles.nanas.lyrics.LyricsEntry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 100% faithful Canvas renderer for the NanzBeatles Lyric Video Generator.
 * Implements the exact visual specification from the reference canvas video:
 * - 1280x720 HD resolution
 * - Solid pure black background (#000000)
 * - Ultra-realistic acrylic CD jewel case overlay
 * - Constantly spinning circular album art disc (360° every 6s)
 * - Concentric CD spindle clamp hub with radial clamp teeth and center spindle hole
 * - Bold center-aligned "NanzBeatles" brand text directly above the case
 * - Vertical 90° clockwise rotated "@Xxxtentaction" handle on the right
 * - 3-cluster audio-reactive waveform bars with frequency marker lines beneath the case
 * - Large white left-aligned lyrics text with auto word-wrap
 */
class LyricVideoRenderer(
    val width: Int = 1280,
    val height: Int = 720,
    private val caseBitmap: Bitmap? = null,
    private val brandText: String = "NanzBeatles",
    private val artistHandle: String = "@NanzBeatles",
    var cardStyle: VideoLyricsCardStyle = VideoLyricsCardStyle.KASET,
    lyricsScale: Float = 1.0f,
    lyricsOffsetX: Float = 0f,
    lyricsOffsetY: Float = 0f,
    var cardScale: Float = 1.0f,
    var cardOffsetX: Float = 0f,
    var cardOffsetY: Float = 0f,
    var cardAlpha: Float = 1.0f,
    var lyricsSpacingScale: Float = 1.0f,
    var showUpcomingLyrics: Boolean = true,
    var lyricsRotation: Float = 0f,
    var cardRotation: Float = 0f
) {

    var lyricsScale: Float = lyricsScale
        set(value) {
            if (field != value) {
                field = value
                entryLinesCache.clear()
            }
        }

    var lyricsOffsetX: Float = lyricsOffsetX
        set(value) {
            if (field != value) {
                field = value
                entryLinesCache.clear()
            }
        }

    var lyricsOffsetY: Float = lyricsOffsetY


    // Paints
    private val bgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = height * 0.040f // ~29px on 720p
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.03f
        setShadowLayer(6f, 0f, 2f, Color.parseColor("#90000000"))
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = height * 0.046f // ~33px on 720p
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.03f
    }

    private val cassetteFramePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(70, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = height * 0.005f
    }

    private val cassetteBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val cassetteDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 203, 213, 225)
        style = Paint.Style.STROKE
        strokeWidth = height * 0.003f
    }

    private val screwPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(130, 203, 213, 225)
        style = Paint.Style.FILL
    }

    private val waveformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = height * 0.007f // ~5px on 720p
    }

    // Proportional lyrics typography
    private val activeTextSize = height * 0.076f // ~54.7px on 720p (Large, modern & readable)
    private val secTextSize = height * 0.054f    // ~38.8px on 720p (Upcoming preview, ~1.4x zoom into active)
    private val prevTextSize = height * 0.046f   // ~33.1px on 720p (Previous line context)

    private val lyricsActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = activeTextSize
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        setShadowLayer(14f, 0f, 4f, Color.parseColor("#A0000000"))
    }

    private val lyricsUpcomingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 255, 255, 255)
        textSize = activeTextSize
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val lyricsNextLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(115, 255, 255, 255)
        textSize = secTextSize
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }

    private val lyricsPrevPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 255, 255, 255)
        textSize = prevTextSize
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }

    private val hubOuterRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 220, 220, 220)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val hubInnerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 180, 180, 180)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val hubSpokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 200, 200, 200)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }

    private val centerHolePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val centerHoleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 160, 160, 160)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    // Exact geometry coordinates matched with reference video
    private val discCenterX = width * 0.725f // ~928px on 1280
    private val discCenterY = height * 0.512f // ~368px on 720
    private val discRadius = height * 0.285f  // ~205px on 720

    // Jewel case dimensions & position
    private val caseWidth = discRadius * 2.31f
    private val caseHeight = discRadius * 2.15f
    private val caseRect = RectF(
        discCenterX - (caseWidth * 0.53f),
        discCenterY - (caseHeight * 0.50f),
        discCenterX + (caseWidth * 0.47f),
        discCenterY + (caseHeight * 0.50f)
    )

    // Waveform geometry (3 clusters beneath the case)
    private val waveformBaseY = height * 0.850f // ~612px on 720
    private val clusterCenters = floatArrayOf(
        discCenterX - (discRadius * 0.68f), // ~788px
        discCenterX,                        // ~928px
        discCenterX + (discRadius * 0.68f)  // ~1068px
    )

    // Text positions
    private val brandTextY = caseRect.top - (height * 0.035f)
    private val handleX = width * 0.952f
    private val lyricsX = width * 0.065f // ~83px on 1280 (Clear left margin)
    private val lyricsMaxWidth = width * 0.460f // ~588px (Generous width for multi-line lyrics)

    // Symmetrical vertical slots for 100% continuous, zero-teleport lyrics scrolling
    private val slotActiveY = height * 0.460f // ~331px on 720p (Optical focal center)
    private val stepDistance = height * 0.185f // ~133px on 720p (Uniform slot distance)
    private val slotPrevY = slotActiveY - stepDistance // ~198px on 720p
    private val slotNextY = slotActiveY + stepDistance // ~464px on 720p

    // Pre-allocated circular disc bitmap
    private var circularCoverBitmap: Bitmap? = null
    private var cachedSourceCover: Bitmap? = null

    /**
     * Prepares and caches circular cropped cover art.
     */
    fun setCoverBitmap(cover: Bitmap?) {
        if (cover == null) {
            circularCoverBitmap = null
            cachedSourceCover = null
            entryLinesCache.clear()
            return
        }
        if (cover == cachedSourceCover && circularCoverBitmap != null) {
            return
        }

        entryLinesCache.clear()

        cachedSourceCover = cover
        val size = (discRadius * 2).toInt()
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())

        // Circle mask
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        // Scale and crop center
        val minDim = minOf(cover.width, cover.height)
        val cropX = (cover.width - minDim) / 2
        val cropY = (cover.height - minDim) / 2
        val centerSrcRect = Rect(cropX, cropY, cropX + minDim, cropY + minDim)

        canvas.drawBitmap(cover, centerSrcRect, rect, paint)

        circularCoverBitmap = output
    }

    /**
     * Renders a complete frame on the given Canvas.
     */
    fun renderFrame(
        canvas: Canvas,
        currentTimeMs: Long,
        amplitude: Float,
        lyrics: List<LyricsEntry>?,
        songTitle: String = "NanzBeatles",
        songArtist: String = ""
    ) {
        // 1. Solid Pure Black Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // 2. Render Card / Cover Art / CD Jewel Case if visible
        if (cardAlpha > 0.005f) {
            val cardLayerAlpha = (cardAlpha.coerceIn(0f, 1f) * 255).toInt()
            val saveCount = if (cardLayerAlpha < 255) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), cardLayerAlpha)
            } else {
                canvas.save()
            }

            canvas.translate(cardOffsetX, cardOffsetY)
            if (cardRotation != 0f) {
                canvas.rotate(cardRotation, discCenterX, discCenterY)
            }
            if (cardScale != 1.0f) {
                canvas.scale(cardScale, cardScale, discCenterX, discCenterY)
            }

            if (cardStyle == VideoLyricsCardStyle.NORMAL) {
                // Normal card mode: clean square album art + song title + artist below + sleek audio waveform
                renderNormalCard(canvas, songTitle, songArtist, currentTimeMs, amplitude)
            } else {
                // Kaset / CD jewel case mode
                renderSpinningDisc(canvas, currentTimeMs)
                renderSpindleHub(canvas)
                renderJewelCase(canvas)
                renderBrandText(canvas)
                renderArtistHandle(canvas)
                renderWaveform(canvas, currentTimeMs, amplitude)
            }

            canvas.restoreToCount(saveCount)
        }

        // 3. Large White Left-Aligned Lyrics
        renderLyrics(canvas, currentTimeMs, lyrics, songTitle, songArtist)
    }

    private fun renderNormalCard(
        canvas: Canvas,
        songTitle: String,
        songArtist: String,
        currentTimeMs: Long,
        amplitude: Float
    ) {
        val cx = discCenterX
        val cy = discCenterY
        val cardSize = discRadius * 1.82f // ~373px on 720p
        val cardCornerRadius = height * 0.035f

        val cardLeft = cx - (cardSize / 2f)
        val cardTop = cy - (cardSize * 0.62f)
        val cardRight = cardLeft + cardSize
        val cardBottom = cardTop + cardSize
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)

        // 1. Soft Drop Shadow behind cover
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40000000")
            setShadowLayer(24f, 0f, 8f, Color.parseColor("#C0000000"))
        }
        canvas.drawRoundRect(cardRect, cardCornerRadius, cardCornerRadius, shadowPaint)

        // 2. Draw Rounded Album Art Cover
        val cover = cachedSourceCover
        if (cover != null && !cover.isRecycled) {
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(cardRect, cardCornerRadius, cardCornerRadius, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            val minDim = minOf(cover.width, cover.height)
            val cropX = (cover.width - minDim) / 2
            val cropY = (cover.height - minDim) / 2
            val srcRect = Rect(cropX, cropY, cropX + minDim, cropY + minDim)
            canvas.drawBitmap(cover, srcRect, cardRect, null)
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cardLeft, cardTop, cardRight, cardBottom,
                    Color.parseColor("#1E293B"), Color.parseColor("#0F172A"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(cardRect, cardCornerRadius, cardCornerRadius, placeholderPaint)
        }

        // 3. Subtle stroke border around cover
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(45, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawRoundRect(cardRect, cardCornerRadius, cardCornerRadius, borderPaint)

        // 4. Song Title below cover ("nama musik di bawahnya")
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = height * 0.046f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(8f, 0f, 2f, Color.parseColor("#A0000000"))
        }
        val maxTextWidth = cardSize * 1.15f
        var displayTitle = songTitle
        while (titlePaint.measureText("$displayTitle…") > maxTextWidth && displayTitle.isNotEmpty()) {
            displayTitle = displayTitle.dropLast(1).trimEnd()
        }
        if (displayTitle.length < songTitle.length) displayTitle = "$displayTitle…"

        val titleY = cardBottom + (height * 0.052f)
        canvas.drawText(displayTitle, cx, titleY, titlePaint)

        // 5. Artist Name below song title ("artisnya juga")
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(195, 203, 213, 225)
            textSize = height * 0.035f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        var displayArtist = if (songArtist.isNotBlank()) songArtist else "NanzBeatles"
        while (artistPaint.measureText("$displayArtist…") > maxTextWidth && displayArtist.isNotEmpty()) {
            displayArtist = displayArtist.dropLast(1).trimEnd()
        }
        if (displayArtist.length < songArtist.length) displayArtist = "$displayArtist…"

        val artistY = titleY + (height * 0.040f)
        canvas.drawText(displayArtist, cx, artistY, artistPaint)

        // 6. Sleek Audio Waveform below artist
        val waveBaseY = artistY + (height * 0.050f)
        val barCount = 15
        val barSpacing = height * 0.016f
        val startX = cx - ((barCount - 1) * barSpacing / 2f)
        val minH = height * 0.012f
        val maxH = height * 0.050f

        for (b in 0 until barCount) {
            val bx = startX + (b * barSpacing)
            val centerDist = abs(b - (barCount / 2f)) / (barCount / 2f)
            val bell = exp(-centerDist * centerDist * 2.0).toFloat()
            val phase = (b * 0.5) + (currentTimeMs * 0.008)
            val waveAmp = (amplitude * 0.75f + (sin(phase) * 0.25).toFloat()).coerceIn(0.15f, 1.0f)
            val barH = minH + (waveAmp * (maxH - minH) * bell)

            val yTop = waveBaseY - (barH / 2f)
            val yBot = waveBaseY + (barH / 2f)
            canvas.drawLine(bx, yTop, bx, yBot, waveformPaint)
        }
    }

    private fun renderSpinningDisc(canvas: Canvas, currentTimeMs: Long) {
        // One full 360-degree rotation every 6.0 seconds (60 deg/sec)
        val rotationAngle = (currentTimeMs % 6000L) / 6000f * 360f

        canvas.save()
        canvas.rotate(rotationAngle, discCenterX, discCenterY)

        // Draw album art circular bitmap
        val cover = circularCoverBitmap
        if (cover != null && !cover.isRecycled) {
            val left = discCenterX - discRadius
            val top = discCenterY - discRadius
            canvas.drawBitmap(cover, left, top, null)
        } else {
            // Elegant default record label pattern
            val defaultRecordPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    discCenterX - discRadius, discCenterY - discRadius,
                    discCenterX + discRadius, discCenterY + discRadius,
                    Color.parseColor("#1E293B"), Color.parseColor("#0F172A"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawCircle(discCenterX, discCenterY, discRadius, defaultRecordPaint)
        }

        // CD outer silver rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 200, 200, 200)
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
        }
        canvas.drawCircle(discCenterX, discCenterY, discRadius, rimPaint)

        canvas.restore()
    }

    private fun renderSpindleHub(canvas: Canvas) {
        val hubR = discRadius * 0.25f // ~52px
        val innerR = discRadius * 0.17f // ~35px
        val holeR = discRadius * 0.07f // ~15px

        // Hub clamp outer and inner plastic rings
        canvas.drawCircle(discCenterX, discCenterY, hubR, hubOuterRingPaint)
        canvas.drawCircle(discCenterX, discCenterY, innerR, hubInnerRingPaint)

        // Radial clamp teeth spokes
        for (angle in 0 until 360 step 60) {
            val rad = Math.toRadians(angle.toDouble())
            val x1 = discCenterX + (innerR * 0.68f * cos(rad)).toFloat()
            val y1 = discCenterY + (innerR * 0.68f * sin(rad)).toFloat()
            val x2 = discCenterX + (innerR * cos(rad)).toFloat()
            val y2 = discCenterY + (innerR * sin(rad)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, hubSpokePaint)
        }

        // Center spindle hole
        canvas.drawCircle(discCenterX, discCenterY, holeR, centerHolePaint)
        canvas.drawCircle(discCenterX, discCenterY, holeR, centerHoleBorderPaint)
    }

    private fun renderJewelCase(canvas: Canvas) {
        if (caseBitmap != null && !caseBitmap.isRecycled) {
            val destRect = RectF(caseRect.left, caseRect.top, caseRect.right, caseRect.bottom)
            canvas.drawBitmap(caseBitmap, null, destRect, null)
            return
        }

        // Fallback procedural acrylic jewel case
        val cornerRadius = height * 0.035f
        canvas.drawRoundRect(caseRect, cornerRadius, cornerRadius, cassetteBgPaint)
        canvas.drawRoundRect(caseRect, cornerRadius, cornerRadius, cassetteFramePaint)

        val innerMargin = height * 0.018f
        val innerRect = RectF(
            caseRect.left + innerMargin,
            caseRect.top + innerMargin,
            caseRect.right - innerMargin,
            caseRect.bottom - innerMargin
        )
        canvas.drawRoundRect(innerRect, cornerRadius * 0.7f, cornerRadius * 0.7f, cassetteDetailPaint)

        val screwRadius = height * 0.009f
        val screwOffset = height * 0.024f
        val corners = listOf(
            caseRect.left + screwOffset to caseRect.top + screwOffset,
            caseRect.right - screwOffset to caseRect.top + screwOffset,
            caseRect.left + screwOffset to caseRect.bottom - screwOffset,
            caseRect.right - screwOffset to caseRect.bottom - screwOffset
        )
        for ((cx, cy) in corners) {
            canvas.drawCircle(cx, cy, screwRadius, screwPaint)
        }

        val highlightPath = Path().apply {
            moveTo(caseRect.left + (caseRect.width() * 0.25f), caseRect.top)
            lineTo(caseRect.left + (caseRect.width() * 0.55f), caseRect.top)
            lineTo(caseRect.left + (caseRect.width() * 0.15f), caseRect.bottom)
            lineTo(caseRect.left, caseRect.bottom)
            close()
        }
        val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(12, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawPath(highlightPath, glassPaint)
    }

    private fun renderBrandText(canvas: Canvas) {
        canvas.drawText(brandText, discCenterX, brandTextY, brandPaint)
    }

    private fun renderArtistHandle(canvas: Canvas) {
        val handle = if (artistHandle.isNotBlank()) {
            if (artistHandle.startsWith("@")) artistHandle else "@$artistHandle"
        } else {
            "@NanzBeatles"
        }
        canvas.save()
        // Rotate 90 degrees clockwise at handle position so text flows top to bottom
        canvas.translate(handleX, discCenterY)
        canvas.rotate(90f)
        canvas.drawText(handle, 0f, 0f, handlePaint)
        canvas.restore()
    }

    private fun renderWaveform(canvas: Canvas, currentTimeMs: Long, baseAmp: Float) {
        val barSpacing = height * 0.015f // ~11px on 720p
        val maxClusterH = height * 0.082f // ~59px
        val minClusterH = height * 0.018f // ~13px

        for ((clusterIdx, clusterX) in clusterCenters.withIndex()) {
            val clusterPhase = (clusterIdx * 1.5) + (currentTimeMs * 0.007)
            val clusterAmp = (baseAmp * 0.75f + (sin(clusterPhase) * 0.25).toFloat()).coerceIn(0.12f, 1.0f)

            // 9 bars per cluster (-4 to +4)
            for (b in -4..4) {
                val bx = clusterX + (b * barSpacing)
                val bell = exp(-(b * b) / 5.5).toFloat()
                val barH = minClusterH + (clusterAmp * (maxClusterH - minClusterH) * bell)

                val yTop = waveformBaseY - (barH / 2f)
                val yBot = if (b == 0) {
                    // Center bar in each cluster acts as frequency marker extending lower
                    waveformBaseY + (height * 0.11f)
                } else {
                    waveformBaseY + (barH / 2f)
                }

                canvas.drawLine(bx, yTop, bx, yBot, waveformPaint)
            }
        }
    }

    private data class TimedWord(
        val text: String,
        val startMs: Long,
        val endMs: Long,
        val hasTrailingSpace: Boolean = true
    ) {
        fun progressAt(currentTimeMs: Long): Float {
            if (currentTimeMs >= endMs) return 1f
            if (currentTimeMs < startMs) return 0f
            val dur = (endMs - startMs).coerceAtLeast(1L)
            val raw = ((currentTimeMs - startMs).toFloat() / dur).coerceIn(0f, 1f)
            return raw * raw * (3f - 2f * raw)
        }
    }

    private enum class EntryRenderStyle {
        UPCOMING,
        ACTIVE,
        PREVIOUS
    }

    private val entryLinesCache = mutableMapOf<LyricsEntry, List<List<TimedWord>>>()

    private fun getWrappedLines(entry: LyricsEntry, nextEntry: LyricsEntry?): List<List<TimedWord>> {
        return entryLinesCache.getOrPut(entry) {
            val timedWords = mutableListOf<TimedWord>()
            val wordTimings = entry.words?.filter { it.text.isNotBlank() }

            if (!wordTimings.isNullOrEmpty()) {
                for ((wIdx, w) in wordTimings.withIndex()) {
                    val isLast = (wIdx == wordTimings.size - 1)
                    val sMs = (w.startTime * 1000).toLong()
                    val rawEndMs = (w.endTime * 1000).toLong()
                    val safeEndMs = if (isLast) maxOf(rawEndMs, sMs + 450L) else maxOf(rawEndMs, sMs + 250L)
                    timedWords.add(TimedWord(w.text, sMs, safeEndMs, w.hasTrailingSpace))
                }
            } else {
                val rawWords = entry.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val lineStartMs = entry.time
                val estimatedDur = (rawWords.size * 420L).coerceIn(1800L, 5000L)
                val maxDur = nextEntry?.let { (it.time - lineStartMs).coerceAtLeast(600L) } ?: 4000L
                val lineDur = minOf(estimatedDur, maxDur)
                val count = rawWords.size
                for ((idx, w) in rawWords.withIndex()) {
                    val sMs = lineStartMs + (idx * lineDur / count)
                    val eMs = lineStartMs + ((idx + 1) * lineDur / count)
                    timedWords.add(TimedWord(w, sMs, eMs, hasTrailingSpace = idx < count - 1))
                }
            }
            lyricsActivePaint.textSize = activeTextSize * lyricsScale
            wrapTimedWords(timedWords, lyricsMaxWidth, lyricsActivePaint, maxLines = 3)
        }
    }

    private fun renderLyrics(
        canvas: Canvas,
        currentTimeMs: Long,
        lyrics: List<LyricsEntry>?,
        songTitle: String,
        songArtist: String
    ) {
        val validLyrics = lyrics?.filter { it.text.isNotBlank() } ?: emptyList()

        val scaledActiveTextSize = activeTextSize * lyricsScale
        val scaledSecTextSize = secTextSize * lyricsScale
        val scaledPrevTextSize = prevTextSize * lyricsScale

        // Dynamic slot distance scaling proportionally with lyricsScale and user line spacing:
        val scaledStepDistance = stepDistance * lyricsScale * lyricsSpacingScale
        val curSlotActiveY = slotActiveY + lyricsOffsetY
        val curSlotPrevY = curSlotActiveY - scaledStepDistance
        val curSlotNextY = curSlotActiveY + scaledStepDistance

        canvas.save()
        if (lyricsRotation != 0f) {
            val lyricPivotX = lyricsX + lyricsOffsetX + (lyricsMaxWidth / 2f)
            val lyricPivotY = curSlotActiveY
            canvas.rotate(lyricsRotation, lyricPivotX, lyricPivotY)
        }

        try {
            if (validLyrics.isEmpty()) {
                renderTitleCard(canvas, songTitle, songArtist, curSlotActiveY, 1.0f)
                return
            }

            val activeIndex = validLyrics.indexOfLast { it.time <= currentTimeMs }
            val transitionDurationMs = 440f

            if (activeIndex == -1) {
                val firstEntry = validLyrics.first()
                val nextAfterFirst = validLyrics.getOrNull(1)
                val firstLines = getWrappedLines(firstEntry, nextAfterFirst)
                val timeUntilFirstMs = firstEntry.time - currentTimeMs

                if (timeUntilFirstMs in 0L..440L) {
                    val p = 1f - (timeUntilFirstMs.toFloat() / transitionDurationMs).coerceIn(0f, 1f)
                    val ease = 1f - (1f - p) * (1f - p) * (1f - p)
                    val scrollOffset = (1f - ease) * scaledStepDistance

                    renderTitleCard(canvas, songTitle, songArtist, curSlotPrevY + scrollOffset, (1f - ease).coerceIn(0f, 1f))

                    val activeCenterY = curSlotActiveY + scrollOffset
                    val curTextSize = scaledSecTextSize + (scaledActiveTextSize - scaledSecTextSize) * ease
                    val activeAlpha = (0.45f + 0.55f * ease).coerceIn(0f, 1f)
                    renderEntry(canvas, firstLines, currentTimeMs, activeCenterY, curTextSize, activeAlpha, EntryRenderStyle.ACTIVE)
                } else {
                    renderTitleCard(canvas, songTitle, songArtist, curSlotActiveY, 1.0f)
                    if (showUpcomingLyrics) {
                        renderEntry(canvas, firstLines, currentTimeMs, curSlotNextY, scaledSecTextSize, 0.45f, EntryRenderStyle.UPCOMING)
                    }
                }
                return
            }

            val activeEntry = validLyrics[activeIndex]
            val prevEntry = if (activeIndex > 0) validLyrics[activeIndex - 1] else null
            val nextEntry = if (activeIndex + 1 < validLyrics.size) validLyrics[activeIndex + 1] else null
            val olderEntry = if (activeIndex > 1) validLyrics[activeIndex - 2] else null
            val futureEntry = if (activeIndex + 2 < validLyrics.size) validLyrics[activeIndex + 2] else null

            val activeLines = getWrappedLines(activeEntry, nextEntry)
            val prevLines = prevEntry?.let { getWrappedLines(it, activeEntry) }
            val nextLines = nextEntry?.let { getWrappedLines(it, futureEntry) }
            val olderLines = olderEntry?.let { getWrappedLines(it, prevEntry) }

            val timeSinceLineStartMs = (currentTimeMs - activeEntry.time).coerceAtLeast(0L)
            val transProgress = (timeSinceLineStartMs / transitionDurationMs).coerceIn(0f, 1f)
            val ease = 1f - (1f - transProgress) * (1f - transProgress) * (1f - transProgress)
            val scrollOffset = (1f - ease) * scaledStepDistance

            // 1. Older previous line exiting upward
            if (olderEntry != null && olderLines != null && ease < 1.0f) {
                val olderAlpha = (0.35f * (1f - ease)).coerceIn(0f, 1f)
                val olderCenterY = (curSlotPrevY - scaledStepDistance) + scrollOffset
                renderEntry(canvas, olderLines, currentTimeMs, olderCenterY, scaledPrevTextSize, olderAlpha, EntryRenderStyle.PREVIOUS)
            }

            // 2. Previous line gliding from slotActiveY to slotPrevY
            val prevCenterY = curSlotPrevY + scrollOffset
            val curPrevTextSize = scaledActiveTextSize - (scaledActiveTextSize - scaledPrevTextSize) * ease
            val prevAlpha = (1.0f - (0.62f * ease)).coerceIn(0f, 1f)
            if (prevEntry != null && prevLines != null) {
                renderEntry(canvas, prevLines, currentTimeMs, prevCenterY, curPrevTextSize, prevAlpha, EntryRenderStyle.PREVIOUS)
            } else if (activeIndex == 0 && prevAlpha > 0.05f) {
                renderTitleCard(canvas, songTitle, songArtist, prevCenterY, prevAlpha)
            }

            // 3. Active line gliding from slotNextY to slotActiveY ("geser + maju sikit")
            val activeCenterY = curSlotActiveY + scrollOffset
            val curActiveTextSize = scaledSecTextSize + (scaledActiveTextSize - scaledSecTextSize) * ease
            val activeAlpha = (0.45f + 0.55f * ease).coerceIn(0f, 1f)
            renderEntry(canvas, activeLines, currentTimeMs, activeCenterY, curActiveTextSize, activeAlpha, EntryRenderStyle.ACTIVE)

            // 4. Next line appearing at slotNextY (already in its exact lines!)
            if (showUpcomingLyrics && nextEntry != null && nextLines != null) {
                val nextCenterY = curSlotNextY + scrollOffset
                val nextAlpha = if (ease >= 1.0f) 0.45f else (0.45f * ease).coerceIn(0f, 1f)
                renderEntry(canvas, nextLines, currentTimeMs, nextCenterY, scaledSecTextSize, nextAlpha, EntryRenderStyle.UPCOMING)
            }
        } finally {
            canvas.restore()
        }
    }

    private fun renderEntry(
        canvas: Canvas,
        lines: List<List<TimedWord>>,
        currentTimeMs: Long,
        centerY: Float,
        targetTextSize: Float,
        alpha: Float,
        style: EntryRenderStyle
    ) {
        if (alpha <= 0.01f || lines.isEmpty()) return

        lyricsActivePaint.textSize = targetTextSize
        val spaceWidth = lyricsActivePaint.measureText(" ")

        // Autoscale so the longest line fits comfortably within lyricsMaxWidth
        val maxLineWidth = lines.maxOfOrNull { line ->
            line.sumOf { (lyricsActivePaint.measureText(it.text) + (if (it.hasTrailingSpace) spaceWidth else 0f)).toDouble() }.toFloat()
        } ?: 0f

        val autoscale = if (maxLineWidth > lyricsMaxWidth) {
            (lyricsMaxWidth / maxLineWidth).coerceIn(0.78f, 1.0f)
        } else 1.0f

        val effectiveTextSize = targetTextSize * autoscale
        val lineHeight = effectiveTextSize * 1.25f * lyricsSpacingScale
        val lineCount = lines.size
        val totalBlockHeight = (lineCount - 1) * lineHeight
        var currentY = centerY - (totalBlockHeight / 2f) + (effectiveTextSize * 0.35f)

        val curAlpha = (255 * alpha).toInt().coerceIn(0, 255)
        val dimAlpha = (115 * alpha).toInt().coerceIn(0, 255)
        val prevAlpha = (95 * alpha).toInt().coerceIn(0, 255)

        when (style) {
            EntryRenderStyle.UPCOMING -> {
                lyricsNextLinePaint.textSize = effectiveTextSize
                lyricsNextLinePaint.alpha = dimAlpha
                val spW = lyricsNextLinePaint.measureText(" ")
                for (line in lines) {
                    var curX = lyricsX + lyricsOffsetX
                    for (tw in line) {
                        canvas.drawText(tw.text, curX, currentY, lyricsNextLinePaint)
                        curX += lyricsNextLinePaint.measureText(tw.text) + (if (tw.hasTrailingSpace) spW else 0f)
                    }
                    currentY += lineHeight
                }
            }
            EntryRenderStyle.PREVIOUS -> {
                lyricsPrevPaint.textSize = effectiveTextSize
                lyricsPrevPaint.alpha = prevAlpha
                val spW = lyricsPrevPaint.measureText(" ")
                for (line in lines) {
                    var curX = lyricsX + lyricsOffsetX
                    for (tw in line) {
                        canvas.drawText(tw.text, curX, currentY, lyricsPrevPaint)
                        curX += lyricsPrevPaint.measureText(tw.text) + (if (tw.hasTrailingSpace) spW else 0f)
                    }
                    currentY += lineHeight
                }
            }
            EntryRenderStyle.ACTIVE -> {
                lyricsActivePaint.textSize = effectiveTextSize
                lyricsUpcomingPaint.textSize = effectiveTextSize

                val activeWordPaint = Paint(lyricsActivePaint).apply {
                    this.alpha = curAlpha
                    setShadowLayer(14f * alpha, 0f, 3f, Color.parseColor("#A0000000"))
                }
                val upcomingWordPaint = Paint(lyricsUpcomingPaint).apply {
                    this.alpha = dimAlpha
                }
                val spW = lyricsActivePaint.measureText(" ")

                for (line in lines) {
                    var curX = lyricsX + lyricsOffsetX
                    for (tw in line) {
                        val wordWidth = lyricsActivePaint.measureText(tw.text)
                        val p = tw.progressAt(currentTimeMs)
                        when {
                            p >= 1f -> {
                                canvas.drawText(tw.text, curX, currentY, activeWordPaint)
                            }
                            p <= 0f -> {
                                canvas.drawText(tw.text, curX, currentY, upcomingWordPaint)
                            }
                            else -> {
                                val sweepPaint = Paint(activeWordPaint).apply {
                                    val pSpread = 0.08f
                                    val p0 = (p - pSpread).coerceAtLeast(0f)
                                    val p1 = (p + pSpread).coerceAtMost(1f)
                                    shader = LinearGradient(
                                        curX, 0f, curX + wordWidth, 0f,
                                        intArrayOf(
                                            Color.argb(curAlpha, 255, 255, 255),
                                            Color.argb(curAlpha, 255, 255, 255),
                                            Color.argb(dimAlpha, 255, 255, 255),
                                            Color.argb(dimAlpha, 255, 255, 255)
                                        ),
                                        floatArrayOf(0f, p0, p1, 1f),
                                        Shader.TileMode.CLAMP
                                    )
                                }
                                canvas.drawText(tw.text, curX, currentY, sweepPaint)
                            }
                        }
                        curX += wordWidth + (if (tw.hasTrailingSpace) spW else 0f)
                    }
                    currentY += lineHeight
                }
            }
        }
    }

    private fun renderTitleCard(
        canvas: Canvas,
        title: String,
        artist: String,
        centerY: Float,
        alpha: Float
    ) {
        if (alpha <= 0.01f) return
        val cardAlpha = (255 * alpha).toInt().coerceIn(0, 255)
        lyricsActivePaint.textSize = activeTextSize * lyricsScale
        lyricsActivePaint.alpha = cardAlpha

        val titleLines = wrapText(title, lyricsMaxWidth, lyricsActivePaint, maxLines = 2)
        val lineHeight = lyricsActivePaint.textSize * 1.25f * lyricsSpacingScale
        val hasArtist = artist.isNotBlank()
        val totalH = (titleLines.size * lineHeight) + (if (hasArtist) secTextSize * lyricsScale * 1.25f else 0f)

        var curY = centerY - (totalH / 2f) + (lyricsActivePaint.textSize * 0.75f)
        for (line in titleLines) {
            canvas.drawText(line, lyricsX + lyricsOffsetX, curY, lyricsActivePaint)
            curY += lineHeight
        }

        if (hasArtist) {
            lyricsNextLinePaint.textSize = secTextSize * lyricsScale
            lyricsNextLinePaint.alpha = (cardAlpha * 0.75f).toInt().coerceIn(0, 255)
            canvas.drawText(artist, lyricsX + lyricsOffsetX, curY + (height * 0.015f), lyricsNextLinePaint)
        }
    }

    private fun wrapTimedWords(
        words: List<TimedWord>,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int = 3
    ): List<List<TimedWord>> {
        val lines = mutableListOf<MutableList<TimedWord>>()
        var currentLine = mutableListOf<TimedWord>()
        var currentLineWidth = 0f
        val spaceWidth = paint.measureText(" ")

        for (i in words.indices) {
            val word = words[i]
            val wordWidth = paint.measureText(word.text)
            val addedWidth = wordWidth + (if (word.hasTrailingSpace) spaceWidth else 0f)

            if (currentLineWidth + addedWidth <= maxWidth || currentLine.isEmpty()) {
                currentLine.add(word)
                currentLineWidth += addedWidth
            } else {
                lines.add(currentLine)
                if (lines.size >= maxLines - 1) {
                    val lastLine = mutableListOf<TimedWord>()
                    for (j in i until words.size) {
                        lastLine.add(words[j])
                    }
                    lines.add(lastLine)
                    return lines
                }
                currentLine = mutableListOf(word)
                currentLineWidth = addedWidth
            }
        }

        if (currentLine.isNotEmpty() && lines.size < maxLines) {
            lines.add(currentLine)
        }

        return lines
    }

    /**
     * Splits text into at most [maxLines] lines respecting word boundaries.
     */
    private fun wrapText(text: String, maxWidth: Float, paint: Paint, maxLines: Int = 1): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (i in words.indices) {
            val word = words[i]
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    if (lines.size >= maxLines) {
                        val lastIdx = lines.lastIndex
                        var elLine = lines[lastIdx]
                        while (paint.measureText("$elLine…") > maxWidth && elLine.isNotEmpty()) {
                            elLine = elLine.dropLast(1).trimEnd()
                        }
                        lines[lastIdx] = if (elLine.length < lines[lastIdx].length) "$elLine…" else lines[lastIdx]
                        return lines
                    }
                    currentLine = StringBuilder(word)
                } else {
                    lines.add(word)
                    if (lines.size >= maxLines) return lines
                    currentLine = StringBuilder()
                }
            }
        }

        if (currentLine.isNotEmpty() && lines.size < maxLines) {
            lines.add(currentLine.toString())
        }

        return lines
    }
}
