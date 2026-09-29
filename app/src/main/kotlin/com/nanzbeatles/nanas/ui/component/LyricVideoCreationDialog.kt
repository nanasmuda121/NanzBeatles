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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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

    var showLayoutEditor by remember { mutableStateOf(false) }
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
            onDismiss = { showLayoutEditor = false }
        )
    }

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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.movie),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Buat Video Lirik",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Pilih bagian lagu yang ingin dijadikan video",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Quick Presets Row
                        Text(
                            text = "Preset Durasi Cepat:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))

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

                        Spacer(Modifier.height(14.dp))

                        // Selected Time Interval Card (Mulai & Selesai)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
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
                                        text = "Mulai Dari",
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
                                        modifier = Modifier.size(18.dp)
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
                                        text = "Selesai Pada",
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

                        Spacer(Modifier.height(10.dp))

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

                        // Stepper fine-tuning controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Mulai: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AssistChip(
                                    onClick = {
                                        startTimeSec = (startTimeSec - 5f).coerceAtLeast(0f)
                                        if (endTimeSec - startTimeSec < 3f) {
                                            endTimeSec = minOf(songDurationSec.toFloat(), startTimeSec + 3f)
                                        }
                                    },
                                    label = { Text("-5s") },
                                    modifier = Modifier.height(28.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                AssistChip(
                                    onClick = {
                                        startTimeSec = (startTimeSec + 5f).coerceAtMost(maxOf(0f, endTimeSec - 3f))
                                    },
                                    label = { Text("+5s") },
                                    modifier = Modifier.height(28.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Selesai: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                AssistChip(
                                    onClick = {
                                        endTimeSec = (endTimeSec - 5f).coerceAtLeast(startTimeSec + 3f)
                                    },
                                    label = { Text("-5s") },
                                    modifier = Modifier.height(28.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                AssistChip(
                                    onClick = {
                                        endTimeSec = (endTimeSec + 5f).coerceAtMost(songDurationSec.toFloat())
                                    },
                                    label = { Text("+5s") },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }

                        // Current Position button if playing
                        if (currentPlaybackPositionMs > 3000L) {
                            Spacer(Modifier.height(8.dp))
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Audio & Lyrics Start/End Preview Card
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
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    AssistChip(
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
                                        label = {
                                            Text(if (isAudioPreviewPlaying) "Berhenti" else "Dengar Audio")
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(if (isAudioPreviewPlaying) R.drawable.pause else R.drawable.ic_play),
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    )
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(
                                            text = "Lirik Mulai (${formatTime(startTimeSec)}): ",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = startLyricText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.Top) {
                                        Text(
                                            text = "Lirik Berhenti (${formatTime(endTimeSec)}): ",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                        Text(
                                            text = endLyricText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Card Model Selection (NORMAL vs KASET)
                        Text(
                            text = "Model Desain:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = cardStyle == VideoLyricsCardStyle.NORMAL,
                                onClick = { cardStyle = VideoLyricsCardStyle.NORMAL },
                                label = { Text("Normal (Sampul & Info)") },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = cardStyle == VideoLyricsCardStyle.KASET,
                                onClick = { cardStyle = VideoLyricsCardStyle.KASET },
                                label = { Text("Kaset (Piringan CD)") },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(10.dp))

                        // Fullscreen Landscape Layout Editor Button
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
                            Text("Edit Tata Letak & Ukuran (Layar Penuh)")
                        }

                        Spacer(Modifier.height(14.dp))

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss) {
                                Text("Batal")
                            }
                            Spacer(Modifier.width(8.dp))
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

                                    scope.launch {
                                        val result = LyricVideoShareUtils.generateLyricVideo(
                                            context = context,
                                            mediaMetadata = mediaMetadata,
                                            lyrics = lyrics,
                                            startTimeMs = startPosMs,
                                            durationMs = durationMs,
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
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Mulai Render")
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
