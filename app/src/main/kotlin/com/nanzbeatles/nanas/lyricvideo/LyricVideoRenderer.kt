/**
 * NanzBeatles Project (C) 2026
 * Licensed under GPL-3.0
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
import com.nanzbeatles.nanas.lyrics.LyricsEntry
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
    private val artistHandle: String = "@NanzBeatles"
) {

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

    private val lyricsActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = height * 0.070f // ~50px on 720p
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        setShadowLayer(10f, 0f, 3f, Color.parseColor("#A0000000"))
    }

    private val lyricsUpcomingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(95, 255, 255, 255)
        textSize = height * 0.070f
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val lyricsNextLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        textSize = height * 0.042f // ~30px
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.LEFT
    }

    private val lyricsPrevPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(110, 255, 255, 255)
        textSize = height * 0.050f // ~36px
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
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
    private val lyricsX = width * 0.065f // Shifted slightly left for better optical balance
    private val lyricsY = height * 0.470f
    private val lyricsMaxWidth = width * 0.46f

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
            return
        }
        if (cover == cachedSourceCover && circularCoverBitmap != null) {
            return
        }

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

        // 2. Constantly Spinning CD Disc Cover
        renderSpinningDisc(canvas, currentTimeMs)

        // 3. Spindle Clamp Hub
        renderSpindleHub(canvas)

        // 4. Jewel Case Overlay (Realistic Acrylic or Procedural)
        renderJewelCase(canvas)

        // 5. "NanzBeatles" Brand Text Above Case
        renderBrandText(canvas)

        // 6. Vertical 90° Rotated Handle "@Xxxtentaction"
        renderArtistHandle(canvas)

        // 7. 3-Cluster Audio Reactive Waveform Bars
        renderWaveform(canvas, currentTimeMs, amplitude)

        // 8. Large White Left-Aligned Lyrics
        renderLyrics(canvas, currentTimeMs, lyrics, songTitle, songArtist)
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
        val progress: Float,
        val hasTrailingSpace: Boolean = true
    )

    private fun renderLyrics(
        canvas: Canvas,
        currentTimeMs: Long,
        lyrics: List<LyricsEntry>?,
        songTitle: String,
        songArtist: String
    ) {
        val activeIndex = lyrics?.indexOfLast { it.time <= currentTimeMs && it.text.isNotBlank() } ?: -1
        val activeEntry = if (activeIndex >= 0) lyrics?.get(activeIndex) else null
        val prevEntry = if (activeIndex > 0) lyrics?.subList(0, activeIndex)?.lastOrNull { it.text.isNotBlank() } else null
        val nextEntry = if (activeIndex >= 0 && activeIndex + 1 < (lyrics?.size ?: 0)) {
            lyrics?.subList(activeIndex + 1, lyrics.size)?.firstOrNull { it.text.isNotBlank() }
        } else null

        if (activeEntry != null) {
            val timedWords = mutableListOf<TimedWord>()
            val wordTimings = activeEntry.words?.filter { it.text.isNotBlank() }

            if (!wordTimings.isNullOrEmpty()) {
                // Syllable / word-by-word timestamps from provider
                for (w in wordTimings) {
                    val wStartMs = (w.startTime * 1000).toLong()
                    val wEndMs = (w.endTime * 1000).toLong()
                    val wDur = (wEndMs - wStartMs).coerceAtLeast(1L)
                    val rawProg = when {
                        currentTimeMs >= wEndMs -> 1f
                        currentTimeMs < wStartMs -> 0f
                        else -> ((currentTimeMs - wStartMs).toFloat() / wDur).coerceIn(0f, 1f)
                    }
                    val smoothProg = rawProg * rawProg * (3f - 2f * rawProg)
                    timedWords.add(TimedWord(w.text, smoothProg, w.hasTrailingSpace))
                }
            } else {
                // Progressive word highlight for standard LRC lines
                val rawWords = activeEntry.text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                val lineStartMs = activeEntry.time
                val estimatedLineDur = (rawWords.size * 420L).coerceIn(1600L, 5000L)
                val maxAllowedDur = nextEntry?.let { (it.time - lineStartMs).coerceAtLeast(400L) } ?: 4000L
                val lineDur = minOf(estimatedLineDur, maxAllowedDur)
                val lineProgress = ((currentTimeMs - lineStartMs).toFloat() / lineDur).coerceIn(0f, 1f)

                val count = rawWords.size
                for ((idx, w) in rawWords.withIndex()) {
                    val wStart = idx.toFloat() / count
                    val wEnd = (idx + 1).toFloat() / count
                    val rawProg = when {
                        lineProgress >= wEnd -> 1f
                        lineProgress <= wStart -> 0f
                        else -> ((lineProgress - wStart) / (wEnd - wStart)).coerceIn(0f, 1f)
                    }
                    val smoothProg = rawProg * rawProg * (3f - 2f * rawProg)
                    timedWords.add(TimedWord(w, smoothProg, hasTrailingSpace = true))
                }
            }

            // Smooth Verse / Line Transition Animation (Gliding scroll + Crossfade)
            val timeSinceLineStartMs = (currentTimeMs - activeEntry.time).coerceAtLeast(0L)
            val transitionDurationMs = 380f // 380ms silky smooth transition window
            val transProgress = (timeSinceLineStartMs / transitionDurationMs).coerceIn(0f, 1f)
            // Cubic ease-out curve for natural, Apple Music style deceleration
            val easeOutT = 1f - (1f - transProgress) * (1f - transProgress) * (1f - transProgress)

            val slideDistance = height * 0.048f // ~35px on 720p
            val activeSlideOffsetY = (1f - easeOutT) * slideDistance
            val activeAlpha = (0.20f + 0.80f * easeOutT).coerceIn(0f, 1f)

            // Wrap words into at most 2 visual lines
            val linesOfWords = wrapTimedWords(timedWords, lyricsMaxWidth, lyricsActivePaint, maxLines = 2)
            val lineHeight = lyricsActivePaint.textSize * 1.30f
            val totalActiveHeight = linesOfWords.size * lineHeight
            val activeCenterY = lyricsY + activeSlideOffsetY
            val activeTopY = activeCenterY - (totalActiveHeight / 2f)
            var currentY = activeTopY + (lyricsActivePaint.textSize * 0.85f)
            val spaceWidth = lyricsActivePaint.measureText(" ")

            // 1. Draw Exiting Previous Verse (floating upward and dissolving)
            if (prevEntry != null && transProgress < 1.0f) {
                val prevAlpha = ((1f - easeOutT) * 115).toInt().coerceIn(0, 255)
                val prevSlideOffsetY = -easeOutT * (slideDistance * 1.25f)
                lyricsPrevPaint.color = Color.argb(prevAlpha, 255, 255, 255)
                val prevLines = wrapText(prevEntry.text.trim(), lyricsMaxWidth, lyricsPrevPaint, maxLines = 1)
                if (prevLines.isNotEmpty()) {
                    val prevLineY = activeTopY - (height * 0.022f) + prevSlideOffsetY
                    canvas.drawText(prevLines[0], lyricsX, prevLineY, lyricsPrevPaint)
                }
            }

            // 2. Draw Active Verse with smooth syllable / word highlight & enter fade
            val curAlpha = (255 * activeAlpha).toInt()
            val upcAlpha = (95 * activeAlpha).toInt()
            val activeWordPaint = Paint(lyricsActivePaint).apply {
                alpha = curAlpha
                setShadowLayer(10f * activeAlpha, 0f, 3f, Color.parseColor("#A0000000"))
            }
            val upcomingWordPaint = Paint(lyricsUpcomingPaint).apply {
                alpha = upcAlpha
            }

            for (line in linesOfWords) {
                var curX = lyricsX
                for (tw in line) {
                    val wordWidth = lyricsActivePaint.measureText(tw.text)

                    when {
                        tw.progress >= 1f -> {
                            // Fully sung: pure glowing white
                            canvas.drawText(tw.text, curX, currentY, activeWordPaint)
                        }
                        tw.progress <= 0f -> {
                            // Upcoming: dimmed translucent white
                            canvas.drawText(tw.text, curX, currentY, upcomingWordPaint)
                        }
                        else -> {
                            // Actively being sung: smooth linear gradient sweep wipe!
                            val sweepPaint = Paint(lyricsActivePaint).apply {
                                val pSpread = 0.08f
                                val p0 = (tw.progress - pSpread).coerceAtLeast(0f)
                                val p1 = (tw.progress + pSpread).coerceAtMost(1f)
                                shader = LinearGradient(
                                    curX, 0f, curX + wordWidth, 0f,
                                    intArrayOf(
                                        Color.argb(curAlpha, 255, 255, 255),
                                        Color.argb(curAlpha, 255, 255, 255),
                                        Color.argb(upcAlpha, 255, 255, 255),
                                        Color.argb(upcAlpha, 255, 255, 255)
                                    ),
                                    floatArrayOf(0f, p0, p1, 1f),
                                    Shader.TileMode.CLAMP
                                )
                                setShadowLayer(10f * activeAlpha, 0f, 3f, Color.parseColor("#A0000000"))
                            }
                            canvas.drawText(tw.text, curX, currentY, sweepPaint)
                        }
                    }
                    curX += wordWidth + (if (tw.hasTrailingSpace) spaceWidth else 0f)
                }
                currentY += lineHeight
            }

            // 3. Draw Next Upcoming Verse in subtle dim font (gliding smoothly into position)
            if (nextEntry != null && nextEntry.text.isNotBlank()) {
                val nextLineText = nextEntry.text.trim()
                val nextLines = wrapText(nextLineText, lyricsMaxWidth, lyricsNextLinePaint, maxLines = 1)
                if (nextLines.isNotEmpty()) {
                    val nextSlideOffsetY = (1f - easeOutT) * (slideDistance * 0.55f)
                    val nextAlpha = ((0.30f + 0.70f * easeOutT) * 90).toInt().coerceIn(0, 90)
                    lyricsNextLinePaint.alpha = nextAlpha
                    val nextLineY = currentY + (height * 0.026f) + nextSlideOffsetY
                    canvas.drawText(nextLines[0], lyricsX, nextLineY, lyricsNextLinePaint)
                }
            }
        } else {
            // Instrumental or pre-intro section
            val titleLines = wrapText(songTitle, lyricsMaxWidth, lyricsActivePaint, maxLines = 2)
            val lineHeight = lyricsActivePaint.textSize * 1.25f
            var currentY = lyricsY - (lineHeight / 2f) + (lyricsActivePaint.textSize * 0.85f)

            for (line in titleLines) {
                canvas.drawText(line, lyricsX, currentY, lyricsActivePaint)
                currentY += lineHeight
            }

            if (songArtist.isNotBlank()) {
                canvas.drawText(songArtist, lyricsX, currentY + (height * 0.025f), lyricsNextLinePaint)
            }
        }
    }

    private fun wrapTimedWords(
        words: List<TimedWord>,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int
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
                    var lastLineWidth = 0f
                    for (j in i until words.size) {
                        val rw = words[j]
                        val rwWidth = paint.measureText(rw.text)
                        val rAdded = rwWidth + (if (rw.hasTrailingSpace) spaceWidth else 0f)
                        if (lastLineWidth + rAdded <= maxWidth || lastLine.isEmpty()) {
                            lastLine.add(rw)
                            lastLineWidth += rAdded
                        } else {
                            break
                        }
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
    private fun wrapText(text: String, maxWidth: Float, paint: Paint, maxLines: Int): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
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
                    if (lines.size == maxLines - 1) {
                        val remainingWords = words.subList(i, words.size).joinToString(" ")
                        var lastLine = remainingWords
                        while (paint.measureText("$lastLine…") > maxWidth && lastLine.isNotEmpty()) {
                            lastLine = lastLine.dropLast(1).trimEnd()
                        }
                        lines.add(if (lastLine.length < remainingWords.length) "$lastLine…" else lastLine)
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
