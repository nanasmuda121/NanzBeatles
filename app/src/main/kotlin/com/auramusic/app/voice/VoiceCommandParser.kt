package com.auramusic.app.voice

object VoiceCommandParser {

    private val defaultWakeWords = listOf("hey beatles", "halo beatles", "ok beatles", "beatles", "hey aura", "aura")

    data class WakeWordMatch(
        val detected: Boolean,
        val remainingText: String
    )

    fun extractWakeWord(text: String, customWakeWord: String = "beatles"): WakeWordMatch {
        val lowerText = text.lowercase().trim()

        // Check default wake phrases first (longer phrases first to avoid partial matches)
        for (wake in defaultWakeWords.filter { it != "beatles" && it != "aura" }) {
            if (lowerText.startsWith(wake)) {
                return WakeWordMatch(true, lowerText.removePrefix(wake).trim())
            }
        }

        // Check custom wake word only as a full-word prefix
        val customWake = customWakeWord.lowercase().trim()
        if (customWake.isNotEmpty()) {
            val wakePatterns = listOf("hey $customWake", "halo $customWake", "ok $customWake", customWake)
            for (pattern in wakePatterns) {
                if (lowerText.startsWith(pattern)) {
                    val remaining = lowerText.removePrefix(pattern)
                    // Ensure it's a full word match (followed by space, end, or punctuation)
                    if (remaining.isEmpty() || remaining[0] == ' ' || remaining[0].isWhitespace()) {
                        return WakeWordMatch(true, remaining.trim())
                    }
                }
            }
        }

        return WakeWordMatch(false, lowerText)
    }

    fun containsWakeWord(text: String, customWakeWord: String = "beatles"): Boolean {
        val lowerText = text.lowercase().trim()
        val customWake = customWakeWord.lowercase().trim()
        if (customWake.isNotEmpty() && lowerText.contains(customWake)) return true
        return defaultWakeWords.any { lowerText.contains(it) }
    }

    fun parseCommand(text: String, wakeWord: String = "beatles"): VoiceCommand {
        val lowerText = text.lowercase().trim()
        
        // Check for wake word and extract command after it
        val match = extractWakeWord(lowerText, wakeWord)
        val commandText = if (match.detected) {
            if (match.remainingText.isEmpty()) return VoiceCommand.WakeWordDetected
            match.remainingText
        } else {
            lowerText
        }
        
        // PlaySearch: "play" or "putar" or "mainkan" followed by something
        if (commandText.startsWith("play ") || commandText.startsWith("putar ") || commandText.startsWith("mainkan ")) {
            val query = commandText
                .removePrefix("play")
                .removePrefix("putar")
                .removePrefix("mainkan")
                .trim()
            if (query.isNotEmpty()) {
                return VoiceCommand.PlaySearch(query)
            }
        }

        // Search commands (search/find/cari)
        if (commandText.contains("search") || commandText.contains("find") || commandText.contains("cari")) {
            val query = commandText
                .replace("search for", "")
                .replace("search", "")
                .replace("find", "")
                .replace("cari lagu", "")
                .replace("cari", "")
                .trim()
            if (query.isNotEmpty()) {
                return VoiceCommand.Search(query)
            }
        }

        // Seek forward
        val forwardMatch = Regex("skip ([0-9]+) (second|seconds|minute|minutes|detik|menit)|forward ([0-9]+) (second|seconds|minute|minutes|detik|menit)|maju ([0-9]+) (detik|menit)").find(commandText)
        if (forwardMatch != null) {
            val num = forwardMatch.groupValues[1].ifEmpty { forwardMatch.groupValues[3] }.ifEmpty { forwardMatch.groupValues[5] }.toIntOrNull() ?: 30
            val unitStr = forwardMatch.groupValues[2].ifEmpty { forwardMatch.groupValues[4] }.ifEmpty { forwardMatch.groupValues[6] }
            val unit = if (unitStr.contains("minute") || unitStr.contains("menit")) 60 else 1
            return VoiceCommand.SeekForward(num * unit * 1000L)
        }
        
        // Seek backward
        val backwardMatch = Regex("go back|rewind|mundur|back ([0-9]+) (second|seconds|minute|minutes|detik|menit)|mundur ([0-9]+) (detik|menit)").find(commandText)
        if (backwardMatch != null) {
            val num = backwardMatch.groupValues[1].ifEmpty { backwardMatch.groupValues[3] }.toIntOrNull() ?: 10
            val unitStr = backwardMatch.groupValues[2].ifEmpty { backwardMatch.groupValues[4] }
            val unit = if (unitStr.contains("minute") || unitStr.contains("menit")) 60 else 1
            return VoiceCommand.SeekBackward(num * unit * 1000L)
        }

        // Playback commands
        return when {
            // Play commands
            commandText.contains("play") || commandText.contains("start") || commandText.contains("resume") || commandText.contains("putar") || commandText.contains("lanjutkan") -> {
                VoiceCommand.Play
            }
            
            // Pause commands
            commandText.contains("pause") || commandText.contains("stop") || commandText.contains("jeda") || commandText.contains("berhenti") -> {
                VoiceCommand.Pause
            }
            
            // Toggle play/pause
            commandText.contains("toggle") && commandText.contains("play") -> {
                VoiceCommand.TogglePlayPause
            }
            
            // Next commands
            commandText.contains("next") || commandText.contains("skip") || commandText.contains("forward") || commandText.contains("berikutnya") || commandText.contains("selanjutnya") || commandText.contains("lewati") -> {
                VoiceCommand.Next
            }
            
            // Previous commands  
            commandText.contains("previous") || commandText.contains("back") || commandText.contains("last") || commandText.contains("sebelumnya") || commandText.contains("kembali") -> {
                VoiceCommand.Previous
            }
            
            // Shuffle commands
            commandText.contains("shuffle on") || commandText.contains("acak aktif") || commandText.contains("nyalakan acak") -> {
                VoiceCommand.ShuffleOn
            }
            commandText.contains("shuffle off") || commandText.contains("acak mati") || commandText.contains("matikan acak") -> {
                VoiceCommand.ShuffleOff
            }
            commandText.contains("shuffle") || commandText.contains("acak") -> {
                VoiceCommand.Shuffle
            }
            
            // Repeat commands
            commandText.contains("repeat one") || commandText.contains("loop one") || commandText.contains("ulang satu") -> {
                VoiceCommand.RepeatOne
            }
            commandText.contains("repeat all") || commandText.contains("loop all") || commandText.contains("ulang semua") -> {
                VoiceCommand.RepeatAll
            }
            commandText.contains("repeat off") || commandText.contains("loop off") || commandText.contains("matikan ulang") -> {
                VoiceCommand.RepeatOff
            }
            commandText.contains("repeat") || commandText.contains("loop") || commandText.contains("ulang") -> {
                VoiceCommand.Repeat
            }
            
            // Volume commands
            commandText.contains("volume up") || commandText.contains("louder") || commandText.contains("increase volume") || commandText.contains("volume higher") || commandText.contains("naikkan volume") || commandText.contains("besarkan volume") || commandText.contains("tambah volume") -> {
                VoiceCommand.VolumeUp
            }
            commandText.contains("volume down") || commandText.contains("quieter") || commandText.contains("decrease volume") || commandText.contains("volume lower") || commandText.contains("turunkan volume") || commandText.contains("kecilkan volume") || commandText.contains("kurangi volume") -> {
                VoiceCommand.VolumeDown
            }
            commandText.contains("mute") || commandText.contains("silent") || commandText.contains("bisukan") || commandText.contains("senyap") -> {
                VoiceCommand.Mute
            }
            commandText.contains("unmute") || commandText.contains("bunyikan") -> {
                VoiceCommand.Unmute
            }
            
            // Speed commands
            commandText.contains("speed up") || commandText.contains("faster") || commandText.contains("percepat") -> {
                VoiceCommand.SpeedUp
            }
            commandText.contains("slow down") || commandText.contains("slower") || commandText.contains("perlambat") -> {
                VoiceCommand.SlowDown
            }
            commandText.contains("normal speed") || commandText.contains("reset speed") || commandText.contains("kecepatan normal") -> {
                VoiceCommand.ResetSpeed
            }
            
            // Settings commands
            commandText.contains("dark mode on") || commandText.contains("dark theme on") || commandText.contains("enable dark mode") || commandText.contains("tema gelap aktif") || commandText.contains("mode gelap aktif") -> {
                VoiceCommand.SetDarkMode(true)
            }
            commandText.contains("dark mode off") || commandText.contains("dark theme off") || commandText.contains("disable dark mode") || commandText.contains("tema gelap mati") -> {
                VoiceCommand.SetDarkMode(false)
            }
            commandText.contains("dark mode") || commandText.contains("dark theme") || commandText.contains("mode gelap") || commandText.contains("tema gelap") -> {
                VoiceCommand.SetDarkMode(true)
            }
            commandText.contains("light mode on") || commandText.contains("light theme on") || commandText.contains("enable light mode") || commandText.contains("mode terang aktif") -> {
                VoiceCommand.SetDarkMode(false)
            }
            commandText.contains("light mode off") || commandText.contains("light theme off") -> {
                VoiceCommand.SetDarkMode(true)
            }
            commandText.contains("light mode") || commandText.contains("light theme") || commandText.contains("mode terang") || commandText.contains("tema terang") -> {
                VoiceCommand.SetDarkMode(false)
            }
            commandText.contains("toggle theme") || commandText.contains("switch theme") || commandText.contains("ganti tema") || commandText.contains("ubah tema") -> {
                VoiceCommand.ToggleTheme
            }
            
            // Lyrics commands
            commandText.contains("show lyrics") || commandText.contains("lyrics on") || commandText.contains("enable lyrics") || commandText.contains("tampilkan lirik") || commandText.contains("buka lirik") -> {
                VoiceCommand.ShowLyrics
            }
            commandText.contains("hide lyrics") || commandText.contains("lyrics off") || commandText.contains("disable lyrics") || commandText.contains("sembunyikan lirik") || commandText.contains("tutup lirik") -> {
                VoiceCommand.HideLyrics
            }
            commandText.contains("toggle lyrics") || commandText.contains("ganti lirik") -> {
                VoiceCommand.ToggleLyrics
            }
            
            // Video commands
            commandText.contains("video on") || commandText.contains("show video") || commandText.contains("enable video") || commandText.contains("tampilkan video") || commandText.contains("putar video") -> {
                VoiceCommand.EnableVideo
            }
            commandText.contains("video off") || commandText.contains("hide video") || commandText.contains("disable video") || commandText.contains("sembunyikan video") || commandText.contains("matikan video") -> {
                VoiceCommand.DisableVideo
            }
            commandText.contains("toggle video") || commandText.contains("ganti video") -> {
                VoiceCommand.ToggleVideo
            }
            
            // Like commands
            commandText.contains("like") || commandText.contains("favorite") || commandText.contains("love") || commandText.contains("suka") || commandText.contains("favorit") -> {
                VoiceCommand.ToggleLike
            }
            
            // Queue commands
            commandText.contains("show queue") || commandText.contains("view queue") || commandText.contains("open queue") || commandText.contains("tampilkan antrean") || commandText.contains("buka antrean") || commandText.contains("lihat antrean") -> {
                VoiceCommand.ShowQueue
            }
            commandText.contains("clear queue") || commandText.contains("bersihkan antrean") || commandText.contains("hapus antrean") -> {
                VoiceCommand.ClearQueue
            }
            commandText.contains("add to queue") || commandText.contains("queue this") || commandText.contains("tambahkan ke antrean") || commandText.contains("masukkan antrean") -> {
                VoiceCommand.AddToQueue
            }
            
            // Download commands
            commandText.contains("download this song") || commandText.contains("download song") || commandText.contains("download track") || commandText.contains("unduh lagu ini") || commandText.contains("unduh lagu") -> {
                VoiceCommand.DownloadCurrentSong
            }
            commandText.contains("download playlist") || commandText.contains("unduh daftar putar") || commandText.contains("unduh playlist") -> {
                VoiceCommand.DownloadCurrentPlaylist
            }
            commandText.contains("download album") || commandText.contains("unduh album") -> {
                VoiceCommand.DownloadCurrentAlbum
            }
            
            // Open commands
            commandText.contains("go home") || commandText.contains("open home") || commandText.contains("ke beranda") || commandText.contains("buka beranda") -> {
                VoiceCommand.OpenHome
            }
            commandText.contains("go library") || commandText.contains("open library") || commandText.contains("ke pustaka") || commandText.contains("buka pustaka") -> {
                VoiceCommand.OpenLibrary
            }
            commandText.contains("go search") || commandText.contains("open search") || commandText.contains("ke pencarian") || commandText.contains("buka pencarian") -> {
                VoiceCommand.OpenSearch
            }
            commandText.contains("go settings") || commandText.contains("open settings") || commandText.contains("ke pengaturan") || commandText.contains("buka pengaturan") -> {
                VoiceCommand.OpenSettings
            }
            
            // Unknown command
            else -> VoiceCommand.Unknown(text)
        }
    }
}

sealed class VoiceCommand {
    // Playback
    data object Play : VoiceCommand()
    data object Pause : VoiceCommand()
    data object TogglePlayPause : VoiceCommand()
    data object Next : VoiceCommand()
    data object Previous : VoiceCommand()
    data object Shuffle : VoiceCommand()
    data object ShuffleOn : VoiceCommand()
    data object ShuffleOff : VoiceCommand()
    data object Repeat : VoiceCommand()
    data object RepeatOne : VoiceCommand()
    data object RepeatAll : VoiceCommand()
    data object RepeatOff : VoiceCommand()
    
    // Seek
    data class SeekForward(val milliseconds: Long) : VoiceCommand()
    data class SeekBackward(val milliseconds: Long) : VoiceCommand()
    
    // Volume
    data object VolumeUp : VoiceCommand()
    data object VolumeDown : VoiceCommand()
    data object Mute : VoiceCommand()
    data object Unmute : VoiceCommand()
    
    // Speed
    data object SpeedUp : VoiceCommand()
    data object SlowDown : VoiceCommand()
    data object ResetSpeed : VoiceCommand()
    
    // Search
    data class Search(val query: String) : VoiceCommand()
    data class PlaySearch(val query: String) : VoiceCommand()
    
    // Settings
    data class SetDarkMode(val enabled: Boolean) : VoiceCommand()
    data object ToggleTheme : VoiceCommand()
    
    // Download commands
    data object DownloadCurrentSong : VoiceCommand()
    data object DownloadCurrentPlaylist : VoiceCommand()
    data object DownloadCurrentAlbum : VoiceCommand()
    
    // Lyrics
    data object ShowLyrics : VoiceCommand()
    data object HideLyrics : VoiceCommand()
    data object ToggleLyrics : VoiceCommand()
    
    // Video
    data object EnableVideo : VoiceCommand()
    data object DisableVideo : VoiceCommand()
    data object ToggleVideo : VoiceCommand()
    
    // Media
    data object ToggleLike : VoiceCommand()
    data object ShowQueue : VoiceCommand()
    data object ClearQueue : VoiceCommand()
    data object AddToQueue : VoiceCommand()
    
    // Navigation
    data object OpenHome : VoiceCommand()
    data object OpenLibrary : VoiceCommand()
    data object OpenSearch : VoiceCommand()
    data object OpenSettings : VoiceCommand()
    
    // Wake word
    data object WakeWordDetected : VoiceCommand()
    
    // Unknown
    data class Unknown(val text: String) : VoiceCommand()
}
