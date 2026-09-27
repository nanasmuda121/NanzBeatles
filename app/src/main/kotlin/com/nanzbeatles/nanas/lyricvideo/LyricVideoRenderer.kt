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
    private val artistHandle: String = "@Xxxtentaction"
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

    private val lyricsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = height * 0.078f // ~56px on 720p
        typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        setShadowLayer(10f, 0f, 3f, Color.parseColor("#A0000000"))
    }

    private val lyricsSecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        textSize = height * 0.045f
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
    private val lyricsX = width * 0.095f
    private val lyricsY = height * 0.480f
    private val lyricsMaxWidth = width * 0.45f

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
        canvas.save()
        // Rotate 90 degrees clockwise at handle position so text flows top to bottom
        canvas.translate(handleX, discCenterY)
        canvas.rotate(90f)
        canvas.drawText(artistHandle, 0f, 0f, handlePaint)
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

    private fun renderLyrics(
        canvas: Canvas,
        currentTimeMs: Long,
        lyrics: List<LyricsEntry>?,
        songTitle: String,
        songArtist: String
    ) {
        // Find active lyrics entry
        val activeEntry = lyrics?.findLast { it.time <= currentTimeMs && it.text.isNotBlank() }
        val activeText = activeEntry?.text?.trim()

        if (!activeText.isNullOrEmpty()) {
            val lines = wrapText(activeText, lyricsMaxWidth, lyricsPaint, maxLines = 2)
            val lineHeight = lyricsPaint.textSize * 1.25f

            var currentY = lyricsY - ((lines.size - 1) * lineHeight / 2f)
            for (line in lines) {
                canvas.drawText(line, lyricsX, currentY, lyricsPaint)
                currentY += lineHeight
            }
        } else {
            // Instrumental or pre-intro section
            val titleLines = wrapText(songTitle, lyricsMaxWidth, lyricsPaint, maxLines = 2)
            val lineHeight = lyricsPaint.textSize * 1.2f
            var currentY = lyricsY - (lineHeight / 2f)

            for (line in titleLines) {
                canvas.drawText(line, lyricsX, currentY, lyricsPaint)
                currentY += lineHeight
            }

            if (songArtist.isNotBlank()) {
                canvas.drawText(songArtist, lyricsX, currentY + (height * 0.04f), lyricsSecondaryPaint)
            }
        }
    }

    /**
     * Splits text into at most [maxLines] lines respecting word boundaries.
     */
    private fun wrapText(text: String, maxWidth: Float, paint: Paint, maxLines: Int): List<String> {
        val words = text.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    if (lines.size == maxLines - 1) {
                        val remainingWords = words.subList(words.indexOf(word), words.size).joinToString(" ")
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
