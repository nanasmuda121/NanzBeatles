/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.component

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.datasource.cache.SimpleCache
import com.nanzbeatles.nanas.LocalPlayerConnection
import com.nanzbeatles.nanas.R
import com.nanzbeatles.nanas.lyrics.LyricsEntry
import com.nanzbeatles.nanas.lyricvideo.LyricVideoShareUtils
import com.nanzbeatles.nanas.models.MediaMetadata
import com.nanzbeatles.nanas.utils.ShareUtils
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.Player
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyle
import com.nanzbeatles.nanas.constants.VideoLyricsCardStyleKey
import com.nanzbeatles.nanas.extensions.toMediaItem
import com.nanzbeatles.nanas.playback.queues.ListQueue
import androidx.datastore.preferences.core.edit
import com.nanzbeatles.nanas.utils.dataStore
import com.nanzbeatles.nanas.utils.rememberEnumPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

enum class LyricVideoDuration(val seconds: Int, val label: String, val desc: String) {
    FIFTEEN(15, "15 Detik", "Instagram Story / WA Status"),
    THIRTY(30, "30 Detik", "Reels / Status Panjang"),
    SIXTY(60, "60 Detik", "TikTok / Video Pendek"),
    FULL(-1, "Lagu Penuh", "Seluruh Durasi Lagu")
}

@Composable
fun LyricVideoCreationDialog(
    mediaMetadata: MediaMetadata,
    lyrics: List<LyricsEntry>?,
    currentPlaybackPositionMs: Long = 0L,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val downloadCache: SimpleCache? = playerConnection?.service?.downloadCache
    val playerCache: SimpleCache? = playerConnection?.service?.playerCache

    val songDurationSec = mediaMetadata.duration.takeIf { it > 0 } ?: 180

    val initialStartSec = (currentPlaybackPositionMs / 1000f).coerceIn(0f, maxOf(0f, songDurationSec.toFloat() - 30f))
    val initialEndSec = minOf(songDurationSec.toFloat(), initialStartSec + 30f)

    var startTimeSec by remember { mutableFloatStateOf(initialStartSec) }
    var endTimeSec by remember { mutableFloatStateOf(initialEndSec) }

    var isGenerating by remember { mutableStateOf(false) }
    var currentStage by remember { mutableStateOf("Menyiapkan...") }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var generatedVideoFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showLayoutEditor by rememberSaveable { mutableStateOf(false) }
    var isAudioPreviewPlaying by remember { mutableStateOf(false) }
    var cardStyle by rememberEnumPreference(VideoLyricsCardStyleKey, VideoLyricsCardStyle.NORMAL)

    LaunchedEffect(isAudioPreviewPlaying, startTimeSec, endTimeSec) {
        if (isAudioPreviewPlaying) {
            val startMs = (startTimeSec * 1000L).toLong()
            val endMs = (endTimeSec * 1000L).toLong()
            // Wait slightly for player seek to initiate
            delay(150L)
            while (isAudioPreviewPlaying) {
                delay(150L)
                val player = playerConnection?.player ?: break
                val current = player.currentPosition

                // Only stop if player is READY (or ended) and past endMs, and past startMs
                if ((player.playbackState == Player.STATE_READY && current >= endMs && current >= startMs) ||
                    player.playbackState == Player.STATE_ENDED
                ) {
                    player.pause()
                    isAudioPreviewPlaying = false
                    break
                }

                // If user paused playback from external controls (playWhenReady == false and not buffering/seeking)
                if (!player.playWhenReady && player.playbackState != Player.STATE_BUFFERING) {
                    isAudioPreviewPlaying = false
                    break
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isAudioPreviewPlaying) {
                playerConnection?.player?.pause()
            }
        }
    }

    if (showLayoutEditor) {
        VideoLyricsLayoutEditor(
            mediaMetadata = mediaMetadata,
            lyrics = lyrics,
            initialCardStyle = cardStyle,
            onStyleChanged = { newStyle -> cardStyle = newStyle },
            onDismiss = { showLayoutEditor = false }
        )
    } else {
        Dialog(
        onDismissRequest = {
            if (!isGenerating) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isGenerating,
            dismissOnClickOutside = !isGenerating,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (generatedVideoFile != null) {
                        // --- STATE 3: COMPLETED ---
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Video Lirik Berhasil Dibuat!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        val artistNames = mediaMetadata.artists.joinToString { it.name }
                        Text(
                            text = "${mediaMetadata.title} • $artistNames",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        var isSavedToGallery by remember { mutableStateOf(false) }

                        Spacer(Modifier.height(18.dp))

                        // Primary Save to Gallery Button
                        Button(
                            onClick = {
                                val uri = LyricVideoShareUtils.saveVideoToGallery(
                                    context = context,
                                    videoFile = generatedVideoFile!!,
                                    songTitle = mediaMetadata.title,
                                    songArtist = artistNames
                                )
                                if (uri != null) {
                                    isSavedToGallery = true
                                    Toast.makeText(context, "Video berhasil disimpan ke Galeri (Movies/NanzBeatles)!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Gagal menyimpan video ke Galeri", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSavedToGallery) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painter = painterResource(if (isSavedToGallery) R.drawable.check else R.drawable.download),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isSavedToGallery) "Tersimpan di Galeri" else "Unduh / Simpan ke Galeri",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                text = "  atau bagikan ke  ",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "Pilih Platform:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))

                        // Sharing platform grid
                        val platforms = listOf(
                            SharePlatformItem(ShareUtils.SharePlatform.WHATSAPP, R.drawable.whatsapp, MaterialTheme.colorScheme.onSurfaceVariant),
                            SharePlatformItem(ShareUtils.SharePlatform.INSTAGRAM, R.drawable.instagram, MaterialTheme.colorScheme.onSurfaceVariant),
                            SharePlatformItem(ShareUtils.SharePlatform.TIKTOK, R.drawable.tiktok, MaterialTheme.colorScheme.onSurfaceVariant),
                            SharePlatformItem(ShareUtils.SharePlatform.X, R.drawable.x_logo, MaterialTheme.colorScheme.onSurfaceVariant),
                            SharePlatformItem(ShareUtils.SharePlatform.TELEGRAM, R.drawable.telegram, MaterialTheme.colorScheme.onSurfaceVariant),
                            SharePlatformItem(ShareUtils.SharePlatform.GENERIC, R.drawable.share, MaterialTheme.colorScheme.primary)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            platforms.take(3).forEach { p ->
                                PlatformIconItem(p) {
                                    LyricVideoShareUtils.shareLyricVideo(
                                        context,
                                        generatedVideoFile!!,
                                        mediaMetadata.title,
                                        artistNames,
                                        p.platform
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            platforms.drop(3).forEach { p ->
                                PlatformIconItem(p) {
                                    LyricVideoShareUtils.shareLyricVideo(
                                        context,
                                        generatedVideoFile!!,
                                        mediaMetadata.title,
                                        artistNames,
                                        p.platform
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Selesai")
                        }

                    } else if (isGenerating) {
                        // --- STATE 2: GENERATING PROGRESS ---
                        CircularProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Membuat Video Lirik...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = currentStage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(
                            progress = { currentProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${(currentProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Proses dilakukan 100% offline di perangkat Anda.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )

                    } else {
                        // --- STATE 1: SELECTION OPTIONS ---
                        fun formatTime(sec: Float): String {
                            val totalSec = sec.toInt().coerceAtLeast(0)
                            return "%02d:%02d".format(totalSec / 60, totalSec % 60)
                        }

                        // 1. Header with Song Details & Dismiss Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.movie),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Buat Video Lirik",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val artistNames = mediaMetadata.artists.joinToString { it.name }
                                Text(
                                    text = "${mediaMetadata.title} • $artistNames",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Tutup",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))

                        // 2. Model Desain & Tata Letak
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Model Tampilan Desain:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = cardStyle == VideoLyricsCardStyle.NORMAL,
                                    onClick = {
                                        cardStyle = VideoLyricsCardStyle.NORMAL
                                        scope.launch {
                                            context.dataStore.edit { it[VideoLyricsCardStyleKey] = VideoLyricsCardStyle.NORMAL.name }
                                        }
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
                                        scope.launch {
                                            context.dataStore.edit { it[VideoLyricsCardStyleKey] = VideoLyricsCardStyle.KASET.name }
                                        }
                                    },
                                    label = { Text("Kaset (Piringan CD)") },
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

                            // Dynamic Helper/Badge explaining the selected model
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (cardStyle == VideoLyricsCardStyle.NORMAL) {
                                        "ℹ️ Normal: Sampul album persegi modern dengan nama musik & artis di bawahnya."
                                    } else {
                                        "ℹ️ Kaset: Piringan CD album berputar dalam casing akrilik kaset vintage."
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { showLayoutEditor = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.tune),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Ubah Tata Letak & Ukuran (Layar Penuh)")
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // 3. Durasi Video & Timeline
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Durasi Video:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Quick Presets Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val curDuration = (endTimeSec - startTimeSec).toInt()
                                val presets = listOf(
                                    15 to "15s",
                                    30 to "30s",
                                    60 to "60s",
                                    -1 to "Penuh"
                                )
                                presets.forEach { (dur, label) ->
                                    val isSelected = if (dur == -1) {
                                        startTimeSec <= 1f && endTimeSec >= songDurationSec.toFloat() - 1f
                                    } else {
                                        curDuration == dur
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            if (dur == -1) {
                                                startTimeSec = 0f
                                                endTimeSec = songDurationSec.toFloat()
                                            } else {
                                                val targetEnd = startTimeSec + dur.toFloat()
                                                if (targetEnd <= songDurationSec.toFloat()) {
                                                    endTimeSec = targetEnd
                                                } else {
                                                    startTimeSec = maxOf(0f, songDurationSec.toFloat() - dur.toFloat())
                                                    endTimeSec = songDurationSec.toFloat()
                                                }
                                            }
                                        },
                                        label = {
                                            Text(
                                                text = label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(Modifier.height(4.dp))

                            // Time Interval Summary Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.Start) {
                                        Text(
                                            text = "Mulai",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatTime(startTimeSec),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val selDurationSec = (endTimeSec - startTimeSec).toInt().coerceAtLeast(1)
                                        Icon(
                                            painter = painterResource(R.drawable.arrow_forward),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "$selDurationSec detik",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Selesai",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatTime(endTimeSec),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // RangeSlider for Full Timeline Dragging
                            RangeSlider(
                                value = startTimeSec..endTimeSec,
                                onValueChange = { range ->
                                    if (range.endInclusive - range.start >= 3f) {
                                        startTimeSec = range.start
                                        endTimeSec = range.endInclusive
                                    }
                                },
                                valueRange = 0f..songDurationSec.toFloat(),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Current Position button if playing (NO -5s or +5s chips!)
                            if (currentPlaybackPositionMs > 3000L) {
                                val curSec = currentPlaybackPositionMs / 1000f
                                AssistChip(
                                    onClick = {
                                        val selDur = (endTimeSec - startTimeSec).coerceAtLeast(10f)
                                        startTimeSec = curSec.coerceIn(0f, maxOf(0f, songDurationSec.toFloat() - 3f))
                                        endTimeSec = minOf(songDurationSec.toFloat(), startTimeSec + selDur)
                                    },
                                    label = {
                                        Text("Mulai dari posisi saat ini (${formatTime(curSec)})")
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_play),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // 4. Pratinjau Audio & Lirik
                        val startLyricText = remember(lyrics, startTimeSec) {
                            val startMs = (startTimeSec * 1000L).toLong()
                            lyrics?.findLast { it.time <= startMs }?.text
                                ?: lyrics?.firstOrNull()?.text
                                ?: "(Tidak ada lirik)"
                        }
                        val endLyricText = remember(lyrics, endTimeSec) {
                            val endMs = (endTimeSec * 1000L).toLong()
                            lyrics?.findLast { it.time <= endMs }?.text
                                ?: lyrics?.lastOrNull()?.text
                                ?: "(Tidak ada lirik)"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            painter = painterResource(R.drawable.mic),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Pratinjau Lirik & Audio",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            val player = playerConnection?.player
                                            if (player != null) {
                                                if (isAudioPreviewPlaying) {
                                                    player.pause()
                                                    isAudioPreviewPlaying = false
                                                } else {
                                                    if (player.currentMediaItem?.mediaId != mediaMetadata.id) {
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = mediaMetadata.title,
                                                                items = listOf(mediaMetadata.toMediaItem())
                                                            )
                                                        )
                                                    }
                                                    player.seekTo((startTimeSec * 1000L).toLong())
                                                    player.play()
                                                    isAudioPreviewPlaying = true
                                                }
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(if (isAudioPreviewPlaying) R.drawable.pause else R.drawable.ic_play),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isAudioPreviewPlaying) "Berhenti" else "Putar Audio",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "Mulai (${formatTime(startTimeSec)}): ",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = startLyricText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = "Selesai (${formatTime(endTimeSec)}): ",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = endLyricText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        // 5. Error message (if any)
                        if (errorMessage != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = errorMessage ?: "",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        // 6. Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
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
                                    if (isAudioPreviewPlaying) {
                                        playerConnection?.player?.pause()
                                        isAudioPreviewPlaying = false
                                    }
                                    isGenerating = true
                                    errorMessage = null

                                    val startPosMs = (startTimeSec * 1000L).toLong()
                                    val durationMs = ((endTimeSec - startTimeSec) * 1000L).toLong().coerceAtLeast(1000L)

                                    val currentCardStyle = cardStyle
                                    scope.launch {
                                        context.dataStore.edit { prefs ->
                                            prefs[VideoLyricsCardStyleKey] = currentCardStyle.name
                                        }
                                        val result = LyricVideoShareUtils.generateLyricVideo(
                                            context = context,
                                            mediaMetadata = mediaMetadata,
                                            lyrics = lyrics,
                                            startTimeMs = startPosMs,
                                            durationMs = durationMs,
                                            cardStyle = currentCardStyle,
                                            downloadCache = downloadCache,
                                            playerCache = playerCache,
                                            onProgress = { stage, prog ->
                                                currentStage = stage
                                                currentProgress = prog
                                            }
                                        )

                                        if (result.isSuccess) {
                                            generatedVideoFile = result.getOrNull()
                                        } else {
                                            isGenerating = false
                                            errorMessage = "Gagal membuat video: ${result.exceptionOrNull()?.message}"
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.movie),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Mulai Render", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun PlatformIconItem(
    item: SharePlatformItem,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(item.iconRes),
                contentDescription = item.platform.displayName,
                modifier = Modifier.size(24.dp),
                colorFilter = ColorFilter.tint(item.color)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.platform.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
