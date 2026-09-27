/**
 * NanzBeatles Project (C) 2026
 * Licensed under GPL-3.0
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

    var selectedDuration by remember { mutableStateOf(LyricVideoDuration.THIRTY) }
    var startFromCurrentPosition by remember { mutableStateOf(currentPlaybackPositionMs > 5000L) }

    var isGenerating by remember { mutableStateOf(false) }
    var currentStage by remember { mutableStateOf("Menyiapkan...") }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var generatedVideoFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val songDurationSec = mediaMetadata.duration.takeIf { it > 0 } ?: 180

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
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (generatedVideoFile != null) {
                        // --- STATE 3: COMPLETED ---
                        Icon(
                            painter = painterResource(R.drawable.check_circle),
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
                        Text(
                            text = "${mediaMetadata.title} • ${mediaMetadata.artist}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Bagikan ke:",
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
                                        mediaMetadata.artist,
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
                                        mediaMetadata.artist,
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
                            Text(
                                text = "Buat Video Lirik",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = "Pilih Durasi Video:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LyricVideoDuration.entries.forEach { option ->
                                val isSelected = selectedDuration == option
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedDuration = option },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(
                                                text = option.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = option.desc,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isSelected)
                                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                painter = painterResource(R.drawable.check),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Start position toggle
                        if (currentPlaybackPositionMs > 5000L) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { startFromCurrentPosition = !startFromCurrentPosition }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Mulai dari posisi saat ini",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    val currentPosSec = currentPlaybackPositionMs / 1000L
                                    Text(
                                        text = "Posisi: %02d:%02d".format(currentPosSec / 60, currentPosSec % 60),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = startFromCurrentPosition,
                                    onCheckedChange = { startFromCurrentPosition = it }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }

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
                                    isGenerating = true
                                    errorMessage = null

                                    val durationSec = if (selectedDuration.seconds > 0) {
                                        minOf(selectedDuration.seconds, songDurationSec)
                                    } else {
                                        songDurationSec
                                    }

                                    val startPosMs = if (startFromCurrentPosition) {
                                        val maxStart = (songDurationSec - durationSec) * 1000L
                                        minOf(currentPlaybackPositionMs, maxOf(0L, maxStart))
                                    } else {
                                        0L
                                    }

                                    scope.launch {
                                        val result = LyricVideoShareUtils.generateLyricVideo(
                                            context = context,
                                            mediaMetadata = mediaMetadata,
                                            lyrics = lyrics,
                                            startTimeMs = startPosMs,
                                            durationMs = durationSec * 1000L,
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
