package com.auramusic.app.voice

import android.content.Context
import android.media.AudioManager
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.auramusic.app.playback.ExoDownloadService
import com.auramusic.app.playback.PlayerConnection
import com.auramusic.app.playback.queues.YouTubeQueue
import com.auramusic.app.utils.dataStore
import com.auramusic.app.models.toMediaMetadata
import com.auramusic.innertube.YouTube
import com.auramusic.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

object VoiceCommandActionExecutor {

    suspend fun execute(
        command: VoiceCommand,
        playerConnection: PlayerConnection?,
        onSearch: (String) -> Unit,
        onNavigate: (String) -> Unit,
    ): String = withContext(Dispatchers.Main) {
        when (command) {
            is VoiceCommand.Search -> {
                onSearch(command.query)
                "Mencari \"${command.query}\""
            }
            is VoiceCommand.WakeWordDetected -> "Mendengarkan..."
            is VoiceCommand.Unknown -> "Saya tidak mengerti itu"
            is VoiceCommand.PlaySearch -> {
                val conn = playerConnection ?: return@withContext "Tidak ada pemutar terhubung"
                val result = withContext(Dispatchers.IO) {
                    YouTube.search(command.query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                }
                val firstSong = result?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                if (firstSong != null) {
                    val metadata = firstSong.toMediaMetadata()
                    conn.playQueue(YouTubeQueue.radio(metadata))
                    "Memutar ${firstSong.title}"
                } else {
                    "Tidak ada hasil untuk \"${command.query}\""
                }
            }

            // Navigation
            is VoiceCommand.ShowQueue -> { onNavigate("queue"); "Membuka antrean" }
            is VoiceCommand.OpenHome -> { onNavigate("home"); "Membuka beranda" }
            is VoiceCommand.OpenLibrary -> { onNavigate("library"); "Membuka pustaka" }
            is VoiceCommand.OpenSearch -> { onNavigate("search"); "Membuka pencarian" }
            is VoiceCommand.OpenSettings -> { onNavigate("settings"); "Membuka pengaturan" }
            
            // Playback commands
            else -> {
                val conn = playerConnection ?: return@withContext "Tidak ada pemutar terhubung"
                executePlaybackCommand(command, conn)
            }
        }
    }

    private suspend fun executePlaybackCommand(
        command: VoiceCommand,
        conn: PlayerConnection,
    ): String {
        val player = conn.player
        val context = conn.service as? Context ?: return "Kesalahan"

        return when (command) {
            is VoiceCommand.Play -> {
                if (player.playbackState == ExoPlayer.STATE_IDLE) player.prepare()
                player.playWhenReady = true
                "Memutar"
            }
            is VoiceCommand.Pause -> {
                player.playWhenReady = false
                "Dijeda"
            }
            is VoiceCommand.TogglePlayPause -> {
                if (player.isPlaying) {
                    player.playWhenReady = false
                    "Dijeda"
                } else {
                    if (player.playbackState == ExoPlayer.STATE_IDLE) player.prepare()
                    player.playWhenReady = true
                    "Memutar"
                }
            }
            is VoiceCommand.Next -> { conn.seekToNext(); "Lagu berikutnya" }
            is VoiceCommand.Previous -> { conn.seekToPrevious(); "Lagu sebelumnya" }
            is VoiceCommand.Shuffle -> {
                val current = conn.shuffleModeEnabled.value
                player.shuffleModeEnabled = !current
                if (!current) "Acak aktif" else "Acak nonaktif"
            }
            is VoiceCommand.ShuffleOn -> { player.shuffleModeEnabled = true; "Acak aktif" }
            is VoiceCommand.ShuffleOff -> { player.shuffleModeEnabled = false; "Acak nonaktif" }
            is VoiceCommand.Repeat -> {
                player.repeatMode = when (player.repeatMode) {
                    ExoPlayer.REPEAT_MODE_OFF -> ExoPlayer.REPEAT_MODE_ALL
                    ExoPlayer.REPEAT_MODE_ALL -> ExoPlayer.REPEAT_MODE_ONE
                    else -> ExoPlayer.REPEAT_MODE_OFF
                }
                when (player.repeatMode) {
                    ExoPlayer.REPEAT_MODE_ONE -> "Ulangi satu"
                    ExoPlayer.REPEAT_MODE_ALL -> "Ulangi semua"
                    else -> "Ulangi nonaktif"
                }
            }
            is VoiceCommand.RepeatOne -> { player.repeatMode = ExoPlayer.REPEAT_MODE_ONE; "Ulangi satu" }
            is VoiceCommand.RepeatAll -> { player.repeatMode = ExoPlayer.REPEAT_MODE_ALL; "Ulangi semua" }
            is VoiceCommand.RepeatOff -> { player.repeatMode = ExoPlayer.REPEAT_MODE_OFF; "Ulangi nonaktif" }
            is VoiceCommand.SeekForward -> {
                val newPos = (player.currentPosition + command.milliseconds).coerceAtMost(player.duration)
                player.seekTo(newPos)
                "Maju cepat"
            }
            is VoiceCommand.SeekBackward -> {
                val newPos = (player.currentPosition - command.milliseconds).coerceAtLeast(0)
                player.seekTo(newPos)
                "Mundur cepat"
            }
            is VoiceCommand.VolumeUp -> {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustVolume(AudioManager.ADJUST_RAISE, 0)
                } catch (_: Exception) {
                    player.volume = (player.volume + 0.1f).coerceAtMost(1f)
                }
                "Volume naik"
            }
            is VoiceCommand.VolumeDown -> {
                try {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.adjustVolume(AudioManager.ADJUST_LOWER, 0)
                } catch (_: Exception) {
                    player.volume = (player.volume - 0.1f).coerceAtLeast(0f)
                }
                "Volume turun"
            }
            is VoiceCommand.Mute -> { conn.setMuted(true); "Dibisukan" }
            is VoiceCommand.Unmute -> { conn.setMuted(false); "Batal bisu" }
            is VoiceCommand.SpeedUp -> {
                val newSpeed = (player.playbackParameters.speed * 1.25f).coerceAtMost(2.0f)
                player.setPlaybackSpeed(newSpeed)
                "Percepat"
            }
            is VoiceCommand.SlowDown -> {
                val newSpeed = (player.playbackParameters.speed * 0.75f).coerceAtLeast(0.5f)
                player.setPlaybackSpeed(newSpeed)
                "Perlambat"
            }
            is VoiceCommand.ResetSpeed -> { player.setPlaybackSpeed(1.0f); "Kecepatan normal" }
            is VoiceCommand.ToggleLike -> { conn.toggleLike(); "Suka diubah" }
            is VoiceCommand.ClearQueue -> { player.clearMediaItems(); "Antrean dibersihkan" }
            is VoiceCommand.AddToQueue -> "Ditambahkan ke antrean"
            
            // Settings
            is VoiceCommand.SetDarkMode -> {
                val darkModeKey = stringPreferencesKey("darkMode")
                context.dataStore.edit { prefs ->
                    prefs[darkModeKey] = if (command.enabled) "ON" else "OFF"
                }
                if (command.enabled) "Mode gelap aktif" else "Mode terang aktif"
            }
            is VoiceCommand.ToggleTheme -> {
                val darkModeKey = stringPreferencesKey("darkMode")
                val current = context.dataStore.data.map { prefs -> prefs[darkModeKey] ?: "AUTO" }.first()
                val newMode = when (current) {
                    "AUTO" -> "ON"
                    "ON" -> "OFF"
                    "OFF" -> "AUTO"
                    else -> "AUTO"
                }
                context.dataStore.edit { prefs -> prefs[darkModeKey] = newMode }
                "Tema diubah"
            }
            is VoiceCommand.ShowLyrics -> {
                val key = booleanPreferencesKey("showLyrics")
                context.dataStore.edit { it[key] = true }
                "Lirik ditampilkan"
            }
            is VoiceCommand.HideLyrics -> {
                val key = booleanPreferencesKey("showLyrics")
                context.dataStore.edit { it[key] = false }
                "Lirik disembunyikan"
            }
            is VoiceCommand.ToggleLyrics -> {
                val key = booleanPreferencesKey("showLyrics")
                val current = context.dataStore.data.map { it[key] ?: false }.first()
                context.dataStore.edit { it[key] = !current }
                "Lirik diubah"
            }
            is VoiceCommand.EnableVideo -> { conn.toggleVideoMode(); "Video aktif" }
            is VoiceCommand.DisableVideo -> { conn.toggleVideoMode(); "Video nonaktif" }
            is VoiceCommand.ToggleVideo -> { conn.toggleVideoMode(); "Video diubah" }

            // Download commands
            is VoiceCommand.DownloadCurrentSong -> {
                val service = conn.service
                val mediaMetadata = service.currentMediaMetadata.value
                if (mediaMetadata == null) {
                    "Tidak ada lagu yang sedang diputar"
                } else {
                    val songId = mediaMetadata.id
                    val isDownloaded = withContext(Dispatchers.IO) {
                        service.database.song(songId).first()?.song?.isDownloaded ?: false
                    }
                    if (isDownloaded) {
                        "Lagu ini sudah diunduh"
                    } else {
                        val downloadRequest = DownloadRequest.Builder(songId, songId.toUri())
                            .setCustomCacheKey(songId)
                            .setData(mediaMetadata.title.toByteArray())
                            .build()
                        DownloadService.sendAddDownload(
                            service,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false,
                        )
                        "Mengunduh \"${mediaMetadata.title}\""
                    }
                }
            }
            is VoiceCommand.DownloadCurrentPlaylist -> {
                val service = conn.service
                val player = service.player
                val mediaItems = mutableListOf<androidx.media3.common.MediaItem>()
                for (i in 0 until player.mediaItemCount) {
                    mediaItems.add(player.getMediaItemAt(i))
                }
                if (mediaItems.size <= 1) {
                    return "Antrean kosong atau hanya memiliki satu lagu"
                }
                var downloadCount = 0
                val skippedList = mutableListOf<String>()
                mediaItems.forEach { item ->
                    val songId = item.mediaId
                    val isDownloaded = withContext(Dispatchers.IO) {
                        service.database.song(songId).first()?.song?.isDownloaded ?: false
                    }
                    if (!isDownloaded) {
                        val title = item.mediaMetadata.title?.toString() ?: "download"
                        val downloadRequest = DownloadRequest.Builder(songId, songId.toUri())
                            .setCustomCacheKey(songId)
                            .setData(title.toByteArray())
                            .build()
                        DownloadService.sendAddDownload(
                            service,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false,
                        )
                        downloadCount++
                    } else {
                        item.mediaMetadata.title?.toString()?.let { skippedList.add(it) }
                    }
                }
                val skipped = skippedList.size
                when {
                    downloadCount > 0 -> "Mengunduh $downloadCount lagu antrean" + if (skipped > 0) " ($skipped sudah diunduh)" else ""
                    skipped == mediaItems.size -> "Semua lagu sudah diunduh"
                    else -> "Tidak ada lagu untuk diunduh"
                }
            }
            is VoiceCommand.DownloadCurrentAlbum -> {
                val service = conn.service
                val mediaMetadata = service.currentMediaMetadata.value
                if (mediaMetadata?.album == null) {
                    return "Lagu saat ini tidak memiliki informasi album"
                }
                val albumId = mediaMetadata.album.id
                val albumSongsFlow = service.database.albumSongs(albumId)
                val albumSongs = albumSongsFlow.first()
                if (albumSongs.isEmpty()) {
                    return "Tidak ada lagu di dalam album"
                }
                var downloadCount = 0
                albumSongs.forEach { song ->
                    val songId = song.song.id
                    val isDownloaded = withContext(Dispatchers.IO) {
                        service.database.song(songId).first()?.song?.isDownloaded ?: false
                    }
                    if (!isDownloaded) {
                        val downloadRequest = DownloadRequest.Builder(songId, songId.toUri())
                            .setCustomCacheKey(songId)
                            .setData(song.song.title.toByteArray())
                            .build()
                        DownloadService.sendAddDownload(
                            service,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false,
                        )
                        downloadCount++
                    }
                }
                val total = albumSongs.size
                val already = total - downloadCount
                when {
                    downloadCount > 0 -> "Mengunduh album \"${mediaMetadata.album.title}\": $downloadCount lagu" + if (already > 0) " ($already sudah diunduh)" else ""
                    else -> "Semua lagu di album ini sudah diunduh"
                }
            }

            else -> "Selesai"
        }
    }
}
