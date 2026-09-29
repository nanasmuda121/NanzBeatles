/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.component

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.edit
import com.nanzbeatles.nanas.R
import com.nanzbeatles.nanas.constants.VideoLyricsCardAlphaPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardOffsetXPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardOffsetYPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardRotationKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardScalePercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyle
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyleKey
import com.nanzbeatles.nanas.constants.VideoLyricsLineSpacingPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsLyricsRotationKey
import com.nanzbeatles.nanas.constants.VideoLyricsOffsetXPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsOffsetYPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsScalePercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsShowUpcomingKey
import com.nanzbeatles.nanas.lyrics.LyricsEntry
import com.nanzbeatles.nanas.lyricvideo.LyricVideoRenderer
import com.nanzbeatles.nanas.lyricvideo.LyricVideoShareUtils
import com.nanzbeatles.nanas.models.MediaMetadata
import com.nanzbeatles.nanas.utils.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Responsive Layout Editor for VideoLyrics.
 * - Dynamic 16:9 Live Canvas preview in the background (fits both portrait & landscape)
 * - Centered semi-transparent floating control card (adjustable Lirik, Sampul, and Gaya)
 * - Dynamic line-spacing for lyrics that automatically scales with text size
 * - Cover size, position, and opacity controls
 * - Toggleable control panel to view full unobstructed preview
 */
@Composable
fun VideoLyricsLayoutEditor(
    mediaMetadata: MediaMetadata?,
    lyrics: List<LyricsEntry>?,
    initialCardStyle: VideoLyricsCardStyle = VideoLyricsCardStyle.NORMAL,
    onStyleChanged: ((VideoLyricsCardStyle) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cardStyle by rememberSaveable { mutableStateOf(initialCardStyle) }
    var scalePercent by rememberSaveable { mutableIntStateOf(80) }
    var offsetXPercent by rememberSaveable { mutableIntStateOf(0) }
    var offsetYPercent by rememberSaveable { mutableIntStateOf(0) }
    var cardScalePercent by rememberSaveable { mutableIntStateOf(100) }
    var cardOffsetXPercent by rememberSaveable { mutableIntStateOf(0) }
    var cardOffsetYPercent by rememberSaveable { mutableIntStateOf(0) }
    var cardAlphaPercent by rememberSaveable { mutableIntStateOf(100) }
    var lineSpacingPercent by rememberSaveable { mutableIntStateOf(100) }
    var showUpcomingLyrics by rememberSaveable { mutableStateOf(true) }
    var lyricsRotation by rememberSaveable { mutableIntStateOf(0) }
    var cardRotation by rememberSaveable { mutableIntStateOf(0) }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0 = Lirik, 1 = Sampul, 2 = Gaya
    var showControls by rememberSaveable { mutableStateOf(true) }

    // Load initial saved preferences
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        val styleStr = prefs[VideoLyricsCardStyleKey] ?: initialCardStyle.name
        cardStyle = try { VideoLyricsCardStyle.valueOf(styleStr) } catch (e: Exception) { initialCardStyle }
        scalePercent = prefs[VideoLyricsScalePercentKey] ?: 80
        offsetXPercent = prefs[VideoLyricsOffsetXPercentKey] ?: 0
        offsetYPercent = prefs[VideoLyricsOffsetYPercentKey] ?: 0
        cardScalePercent = prefs[VideoLyricsCardScalePercentKey] ?: 100
        cardOffsetXPercent = prefs[VideoLyricsCardOffsetXPercentKey] ?: 0
        cardOffsetYPercent = prefs[VideoLyricsCardOffsetYPercentKey] ?: 0
        cardAlphaPercent = prefs[VideoLyricsCardAlphaPercentKey] ?: 100
        lineSpacingPercent = prefs[VideoLyricsLineSpacingPercentKey] ?: 100
        showUpcomingLyrics = prefs[VideoLyricsShowUpcomingKey] ?: true
        lyricsRotation = prefs[VideoLyricsLyricsRotationKey] ?: 0
        cardRotation = prefs[VideoLyricsCardRotationKey] ?: 0
    }

    // Cover art bitmap for preview
    var coverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        if (mediaMetadata?.thumbnailUrl != null) {
            coverBitmap = LyricVideoShareUtils.loadCoverBitmap(context, mediaMetadata.thumbnailUrl)
        }
    }

    // Reference LyricVideoRenderer (1280x720 reference resolution)
    val renderer = remember {
        LyricVideoRenderer(
            width = 1280,
            height = 720,
            brandText = "NanzBeatles",
            artistHandle = "@" + (mediaMetadata?.artists?.firstOrNull()?.name ?: "NanzBeatles")
        )
    }

    // Live frame ticker for animated preview
    var previewTimeMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            previewTimeMs = System.currentTimeMillis() - startTime
            delay(33L) // ~30 fps preview
        }
    }

    // Sample preview lyrics if none provided
    val sampleLyrics = remember(lyrics) {
        if (!lyrics.isNullOrEmpty()) lyrics else listOf(
            LyricsEntry(0L, "Nikmati lantunan musik bersama NanzBeatles"),
            LyricsEntry(3000L, "Tata letak VideoLyrics dapat Anda geser"),
            LyricsEntry(6500L, "Sesuaikan ukuran lirik dari 1% hingga 100%"),
            LyricsEntry(10000L, "Pilih gaya tampilan Kaset atau Kartu Normal")
        )
    }

    val animatedAmp = remember(previewTimeMs) {
        0.45f + 0.35f * kotlin.math.sin(previewTimeMs * 0.008f).toFloat()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // LAYER 1: Fullscreen 16:9 Live Canvas Preview
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedTab) {
                            detectTransformGestures { _, pan, zoom, rotation ->
                                if (selectedTab == 0) {
                                    // Adjust Lirik
                                    // 1. Pan (geser bebas ke mana saja)
                                    val deltaX = pan.x * 0.25f
                                    val deltaY = pan.y * 0.25f
                                    offsetXPercent = (offsetXPercent + deltaX.roundToInt()).coerceIn(-100, 100)
                                    offsetYPercent = (offsetYPercent + deltaY.roundToInt()).coerceIn(-100, 100)

                                    // 2. Zoom in / Zoom out pakai 2 jari
                                    if (zoom != 1.0f) {
                                        scalePercent = (scalePercent * zoom).roundToInt().coerceIn(20, 200)
                                    }

                                    // 3. Rotasi pakai 2 jari
                                    if (rotation != 0.0f) {
                                        val newRot = (lyricsRotation + rotation.roundToInt())
                                        lyricsRotation = when {
                                            newRot > 180 -> newRot - 360
                                            newRot < -180 -> newRot + 360
                                            else -> newRot
                                        }
                                    }
                                } else {
                                    // Adjust Sampul
                                    // 1. Pan (geser bebas ke mana saja)
                                    val deltaX = pan.x * 0.25f
                                    val deltaY = pan.y * 0.25f
                                    cardOffsetXPercent = (cardOffsetXPercent + deltaX.roundToInt()).coerceIn(-100, 100)
                                    cardOffsetYPercent = (cardOffsetYPercent + deltaY.roundToInt()).coerceIn(-100, 100)

                                    // 2. Zoom in / Zoom out pakai 2 jari
                                    if (zoom != 1.0f) {
                                        cardScalePercent = (cardScalePercent * zoom).roundToInt().coerceIn(20, 200)
                                    }

                                    // 3. Rotasi pakai 2 jari
                                    if (rotation != 0.0f) {
                                        val newRot = (cardRotation + rotation.roundToInt())
                                        cardRotation = when {
                                            newRot > 180 -> newRot - 360
                                            newRot < -180 -> newRot + 360
                                            else -> newRot
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val canvasModifier = if (maxWidth * 9f > maxHeight * 16f) {
                        Modifier
                            .fillMaxHeight()
                            .aspectRatio(16f / 9f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    }

                    Canvas(modifier = canvasModifier) {
                        val curTime = previewTimeMs % 12000L
                        val curAmp = animatedAmp
                        val currentCardStyle = cardStyle
                        val currentScale = (scalePercent / 80f).coerceIn(0.2f, 2.5f)
                        val currentOffsetX = (offsetXPercent / 100f) * 600f
                        val currentOffsetY = (offsetYPercent / 100f) * 340f
                        val currentCardScale = (cardScalePercent / 100f).coerceIn(0.2f, 2.5f)
                        val currentCardOffsetX = (cardOffsetXPercent / 100f) * 600f
                        val currentCardOffsetY = (cardOffsetYPercent / 100f) * 340f
                        val currentCardAlpha = (cardAlphaPercent / 100f).coerceIn(0f, 1f)
                        val currentSpacing = (lineSpacingPercent / 100f).coerceIn(0.4f, 2.5f)

                        renderer.cardStyle = currentCardStyle
                        renderer.lyricsScale = currentScale
                        renderer.lyricsOffsetX = currentOffsetX
                        renderer.lyricsOffsetY = currentOffsetY
                        renderer.cardScale = currentCardScale
                        renderer.cardOffsetX = currentCardOffsetX
                        renderer.cardOffsetY = currentCardOffsetY
                        renderer.cardAlpha = currentCardAlpha
                        renderer.lyricsSpacingScale = currentSpacing
                        renderer.showUpcomingLyrics = showUpcomingLyrics
                        renderer.lyricsRotation = lyricsRotation.toFloat()
                        renderer.cardRotation = cardRotation.toFloat()
                        if (coverBitmap != null) {
                            renderer.setCoverBitmap(coverBitmap)
                        }

                        drawIntoCanvas { composeCanvas ->
                            val nativeCanvas = composeCanvas.nativeCanvas
                            nativeCanvas.save()
                            nativeCanvas.scale(size.width / 1280f, size.height / 720f)
                            renderer.renderFrame(
                                canvas = nativeCanvas,
                                currentTimeMs = curTime,
                                amplitude = curAmp,
                                lyrics = sampleLyrics,
                                songTitle = mediaMetadata?.title ?: "NanzBeatles Music",
                                songArtist = mediaMetadata?.artists?.joinToString { it.name } ?: "Beatles Audio"
                            )
                            nativeCanvas.restore()
                        }
                    }
                }

                // LAYER 2: Floating Pill Button & Mode Toggle if panel is hidden
                AnimatedVisibility(
                    visible = !showControls,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        tonalElevation = 6.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedTab == 0,
                                onClick = { selectedTab = 0 },
                                label = { Text("Lirik") }
                            )
                            FilterChip(
                                selected = selectedTab == 1,
                                onClick = { selectedTab = 1 },
                                label = { Text("Sampul") }
                            )
                            FilledTonalButton(
                                onClick = { showControls = true },
                                shape = CircleShape
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.tune),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Pengaturan")
                            }
                        }
                    }
                }

                // LAYER 3: Centered Semi-Transparent Floating Control Card
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Card(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth(0.88f)
                            .fillMaxHeight(0.92f)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        elevation = CardDefaults.cardElevation(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Tata Letak Video",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AssistChip(
                                        onClick = { showControls = false },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.close),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        label = { Text("Sembunyikan") },
                                        modifier = Modifier.height(32.dp)
                                    )

                                    AssistChip(
                                        onClick = {
                                            cardStyle = VideoLyricsCardStyle.NORMAL
                                            scalePercent = 80
                                            offsetXPercent = 0
                                            offsetYPercent = 0
                                            lineSpacingPercent = 100
                                            showUpcomingLyrics = true
                                            lyricsRotation = 0
                                            cardScalePercent = 100
                                            cardOffsetXPercent = 0
                                            cardOffsetYPercent = 0
                                            cardAlphaPercent = 100
                                            cardRotation = 0
                                            onStyleChanged?.invoke(VideoLyricsCardStyle.NORMAL)
                                            Toast.makeText(context, "Tata letak di-reset ke default", Toast.LENGTH_SHORT).show()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.restore),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        label = { Text("Reset") },
                                        modifier = Modifier.height(32.dp)
                                    )
                                }
                            }

                                Spacer(Modifier.height(8.dp))

                                // Category Selector Chips: [Lirik] [Sampul] [Gaya]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedTab == 0,
                                        onClick = { selectedTab = 0 },
                                        label = { Text("Lirik") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = selectedTab == 1,
                                        onClick = { selectedTab = 1 },
                                        label = { Text("Sampul") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = selectedTab == 2,
                                        onClick = { selectedTab = 2 },
                                        label = { Text("Gaya") },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                // Tab Content inside scrollable container
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    when (selectedTab) {
                                        0 -> {
                                            // TAB LIRIK
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Ukuran Lirik:",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "$scalePercent%",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Slider(
                                                value = scalePercent.toFloat(),
                                                onValueChange = { scalePercent = it.roundToInt() },
                                                valueRange = 20f..200f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Jarak Antar Lirik:",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "$lineSpacingPercent%",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Slider(
                                                value = lineSpacingPercent.toFloat(),
                                                onValueChange = { lineSpacingPercent = it.roundToInt() },
                                                valueRange = 50f..250f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(Modifier.height(6.dp))

                                            Text(
                                                text = "Jenis Tampilan Lirik:",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterChip(
                                                    selected = showUpcomingLyrics,
                                                    onClick = { showUpcomingLyrics = true },
                                                    label = { Text("Ada Preview Bawah") },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                FilterChip(
                                                    selected = !showUpcomingLyrics,
                                                    onClick = { showUpcomingLyrics = false },
                                                    label = { Text("Tanpa Preview Bawah") },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }

                                            Spacer(Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Rotasi Lirik:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${if (lyricsRotation > 0) "+$lyricsRotation" else "$lyricsRotation"}°",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Slider(
                                                value = lyricsRotation.toFloat(),
                                                onValueChange = { lyricsRotation = it.roundToInt() },
                                                valueRange = -180f..180f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Posisi Horizontal Lirik (X):",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${if (offsetXPercent > 0) "+$offsetXPercent" else offsetXPercent}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Slider(
                                                value = offsetXPercent.toFloat(),
                                                onValueChange = { offsetXPercent = it.roundToInt() },
                                                valueRange = -100f..100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Posisi Vertikal Lirik (Y):",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${if (offsetYPercent > 0) "+$offsetYPercent" else offsetYPercent}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Slider(
                                                value = offsetYPercent.toFloat(),
                                                onValueChange = { offsetYPercent = it.roundToInt() },
                                                valueRange = -100f..100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                                            ) {
                                                AssistChip(
                                                    onClick = {
                                                        offsetXPercent = 0
                                                        offsetYPercent = 0
                                                    },
                                                    label = { Text("Pusatkan") },
                                                    modifier = Modifier.height(28.dp)
                                                )
                                                AssistChip(
                                                    onClick = { lyricsRotation = 0 },
                                                    label = { Text("Reset Rotasi") },
                                                    modifier = Modifier.height(28.dp)
                                                )
                                                AssistChip(
                                                    onClick = { lineSpacingPercent = 100 },
                                                    label = { Text("Reset Jarak") },
                                                    modifier = Modifier.height(28.dp)
                                                )
                                            }
                                        }
                                        1 -> {
                                            // TAB SAMPUL / COVER
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Ukuran Sampul:",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "$cardScalePercent%",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Slider(
                                                value = cardScalePercent.toFloat(),
                                                onValueChange = { cardScalePercent = it.roundToInt() },
                                                valueRange = 30f..200f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Rotasi Sampul:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${if (cardRotation > 0) "+$cardRotation" else "$cardRotation"}°",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Slider(
                                                value = cardRotation.toFloat(),
                                                onValueChange = { cardRotation = it.roundToInt() },
                                                valueRange = -180f..180f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Posisi Horizontal Sampul (X):",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${if (cardOffsetXPercent > 0) "+$cardOffsetXPercent" else cardOffsetXPercent}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Slider(
                                                value = cardOffsetXPercent.toFloat(),
                                                onValueChange = { cardOffsetXPercent = it.roundToInt() },
                                                valueRange = -100f..100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Posisi Vertikal Sampul (Y):",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "${if (cardOffsetYPercent > 0) "+$cardOffsetYPercent" else cardOffsetYPercent}%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Slider(
                                                value = cardOffsetYPercent.toFloat(),
                                                onValueChange = { cardOffsetYPercent = it.roundToInt() },
                                                valueRange = -100f..100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Spacer(Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Opasitas Sampul:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "$cardAlphaPercent%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Slider(
                                                value = cardAlphaPercent.toFloat(),
                                                onValueChange = { cardAlphaPercent = it.roundToInt() },
                                                valueRange = 0f..100f,
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                                            ) {
                                                AssistChip(
                                                    onClick = {
                                                        cardOffsetXPercent = 0
                                                        cardOffsetYPercent = 0
                                                    },
                                                    label = { Text("Pusatkan") },
                                                    modifier = Modifier.height(28.dp)
                                                )
                                                AssistChip(
                                                    onClick = { cardRotation = 0 },
                                                    label = { Text("Reset Rotasi") },
                                                    modifier = Modifier.height(28.dp)
                                                )
                                            }
                                        }
                                        2 -> {
                                            // TAB GAYA
                                            Text(
                                                text = "Pilih Gaya Tampilan:",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilterChip(
                                                    selected = cardStyle == VideoLyricsCardStyle.NORMAL,
                                                    onClick = {
                                                        cardStyle = VideoLyricsCardStyle.NORMAL
                                                        onStyleChanged?.invoke(VideoLyricsCardStyle.NORMAL)
                                                    },
                                                    label = { Text("Normal (Sampul)") },
                                                    leadingIcon = {
                                                        Icon(
                                                            painter = painterResource(R.drawable.music_note),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                                FilterChip(
                                                    selected = cardStyle == VideoLyricsCardStyle.KASET,
                                                    onClick = {
                                                        cardStyle = VideoLyricsCardStyle.KASET
                                                        onStyleChanged?.invoke(VideoLyricsCardStyle.KASET)
                                                    },
                                                    label = { Text("Kaset (CD)") },
                                                    leadingIcon = {
                                                        Icon(
                                                            painter = painterResource(R.drawable.album),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                            Spacer(Modifier.height(8.dp))

                            // Bottom Actions (Batal & Simpan)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Batal")
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            context.dataStore.edit { prefs ->
                                                prefs[VideoLyricsCardStyleKey] = cardStyle.name
                                                prefs[VideoLyricsScalePercentKey] = scalePercent
                                                prefs[VideoLyricsOffsetXPercentKey] = offsetXPercent
                                                prefs[VideoLyricsOffsetYPercentKey] = offsetYPercent
                                                prefs[VideoLyricsCardScalePercentKey] = cardScalePercent
                                                prefs[VideoLyricsCardOffsetXPercentKey] = cardOffsetXPercent
                                                prefs[VideoLyricsCardOffsetYPercentKey] = cardOffsetYPercent
                                                prefs[VideoLyricsCardAlphaPercentKey] = cardAlphaPercent
                                                prefs[VideoLyricsLineSpacingPercentKey] = lineSpacingPercent
                                                prefs[VideoLyricsShowUpcomingKey] = showUpcomingLyrics
                                                prefs[VideoLyricsLyricsRotationKey] = lyricsRotation
                                                prefs[VideoLyricsCardRotationKey] = cardRotation
                                            }
                                            withContext(Dispatchers.Main) {
                                                onStyleChanged?.invoke(cardStyle)
                                                Toast.makeText(context, "Tata letak VideoLyrics berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Simpan", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
