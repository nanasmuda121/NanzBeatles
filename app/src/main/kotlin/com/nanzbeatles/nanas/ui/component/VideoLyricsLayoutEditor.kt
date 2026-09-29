/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.core.edit
import com.nanzbeatles.nanas.R
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyle
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyleKey
import com.nanzbeatles.nanas.constants.VideoLyricsOffsetXPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsOffsetYPercentKey
import com.nanzbeatles.nanas.constants.VideoLyricsScalePercentKey
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Fullscreen Landscape Layout Editor for VideoLyrics.
 * - Automatically enters immersive landscape mode upon opening
 * - Allows real-time adjustment of lyrics scale (1% - 100%)
 * - Allows dragging / slider positioning of layout (Offset X & Y)
 * - Toggles between NORMAL card (clean cover + song title + artist) and KASET (spinning disc + jewel case)
 * - Provides Restore (Reset) and Save buttons
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
    val activity = remember(context) { context.findActivity() }

    // Force Landscape & Immersive Mode while editor is visible
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        try {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } catch (_: Exception) {}

        val window = activity?.window
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        try {
            insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {}

        onDispose {
            try {
                activity?.requestedOrientation = originalOrientation
                insetsController?.show(WindowInsetsCompat.Type.systemBars())
            } catch (_: Exception) {}
        }
    }

    var cardStyle by remember { mutableStateOf(initialCardStyle) }
    var scalePercent by remember { mutableIntStateOf(80) }
    var offsetXPercent by remember { mutableIntStateOf(0) }
    var offsetYPercent by remember { mutableIntStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }

    // Load initial saved preferences
    LaunchedEffect(Unit) {
        val prefs = context.dataStore.data.first()
        val styleStr = prefs[VideoLyricsCardStyleKey] ?: initialCardStyle.name
        cardStyle = try { VideoLyricsCardStyle.valueOf(styleStr) } catch (e: Exception) { initialCardStyle }
        scalePercent = prefs[VideoLyricsScalePercentKey] ?: 80
        offsetXPercent = prefs[VideoLyricsOffsetXPercentKey] ?: 0
        offsetYPercent = prefs[VideoLyricsOffsetYPercentKey] ?: 0
        isLoaded = true
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

    // Sync renderer properties
    LaunchedEffect(cardStyle, scalePercent, offsetXPercent, offsetYPercent, coverBitmap) {
        renderer.cardStyle = cardStyle
        renderer.lyricsScale = (scalePercent / 80f).coerceIn(0.4f, 1.5f)
        renderer.lyricsOffsetX = (offsetXPercent / 100f) * 200f
        renderer.lyricsOffsetY = (offsetYPercent / 100f) * 150f
        renderer.setCoverBitmap(coverBitmap)
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
            Row(modifier = Modifier.fillMaxSize()) {
                // LEFT SIDE: Interactive 16:9 Canvas Preview
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                        .background(Color.Black)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                // Drag gesture directly adjusts position
                                val newX = (offsetXPercent + (dragAmount.x * 0.25f)).roundToInt().coerceIn(-100, 100)
                                val newY = (offsetYPercent + (dragAmount.y * 0.25f)).roundToInt().coerceIn(-100, 100)
                                offsetXPercent = newX
                                offsetYPercent = newY
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        val curTime = previewTimeMs % 12000L
                        val curAmp = animatedAmp
                        val currentCardStyle = cardStyle
                        val currentScale = (scalePercent / 80f).coerceIn(0.4f, 1.5f)
                        val currentOffsetX = (offsetXPercent / 100f) * 200f
                        val currentOffsetY = (offsetYPercent / 100f) * 150f

                        renderer.cardStyle = currentCardStyle
                        renderer.lyricsScale = currentScale
                        renderer.lyricsOffsetX = currentOffsetX
                        renderer.lyricsOffsetY = currentOffsetY
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

                    // Touch gesture guidance overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "💡 Geser layar untuk memindahkan letak lirik",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                // RIGHT SIDE: Control Panel & Settings
                Card(
                    modifier = Modifier
                        .weight(1.0f)
                        .fillMaxHeight()
                        .padding(12.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Top Bar Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                                        Icon(
                                            painter = painterResource(R.drawable.arrow_back),
                                            contentDescription = "Kembali",
                                            tint = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Tata Letak Video",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Tombol Restore (Reset)
                                AssistChip(
                                    onClick = {
                                        cardStyle = VideoLyricsCardStyle.NORMAL
                                        scalePercent = 80
                                        offsetXPercent = 0
                                        offsetYPercent = 0
                                        onStyleChanged?.invoke(VideoLyricsCardStyle.NORMAL)
                                        Toast.makeText(context, "Tata letak di-reset ke default", Toast.LENGTH_SHORT).show()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.restore),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text("Reset") }
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            // 1. Pilihan Gaya Card (Normal vs Kaset)
                            Text(
                                text = "Gaya Tampilan Card / CD:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
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
                                    label = { Text("Normal (Gambar)") },
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

                            Spacer(Modifier.height(14.dp))

                            // 2. Perbesar Ukuran (geser 1% - 100%)
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
                                valueRange = 1f..100f,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(Modifier.height(10.dp))

                            // 3. Geser Tata Letak (Horizontal X & Vertikal Y)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Posisi Horizontal (X):",
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
                                    text = "Posisi Vertikal (Y):",
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

                            // Quick center position helper
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                AssistChip(
                                    onClick = {
                                        offsetXPercent = 0
                                        offsetYPercent = 0
                                    },
                                    label = { Text("Pusatkan Posisi") },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        // Bottom Actions (Batal & Simpan)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Batal")
                            }

                            // Tombol Save
                            Button(
                                onClick = {
                                    scope.launch {
                                        context.dataStore.edit { prefs ->
                                            prefs[VideoLyricsCardStyleKey] = cardStyle.name
                                            prefs[VideoLyricsScalePercentKey] = scalePercent
                                            prefs[VideoLyricsOffsetXPercentKey] = offsetXPercent
                                            prefs[VideoLyricsOffsetYPercentKey] = offsetYPercent
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
