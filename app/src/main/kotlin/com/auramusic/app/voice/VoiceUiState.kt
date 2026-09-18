package com.auramusic.app.voice

data class VoiceUiState(
    val isVisible: Boolean = false,
    val mode: VoiceMode = VoiceMode.WAKE_WORD,
    val phase: VoicePhase = VoicePhase.IDLE,
    val amplitude: Float = 0f,
    val recognizedText: String = "",
    val feedbackText: String = "",
    val errorMessage: String? = null,
)

enum class VoiceMode { WAKE_WORD, COMMAND, MANUAL }

enum class VoicePhase { IDLE, GREETING, LISTENING, PROCESSING, FEEDBACK, ERROR }
