package com.auramusic.app.ui.screens.settings

import android.speech.tts.Voice
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.auramusic.app.voice.VoiceFeedbackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VoiceFeedbackSettingsViewModel @Inject constructor(
    private val voiceFeedbackManager: VoiceFeedbackManager,
) : ViewModel() {

    init {
        voiceFeedbackManager.initialize()
    }

    private val _isEnabled = mutableStateOf(voiceFeedbackManager.isEnabled())
    val isEnabled: State<Boolean> = _isEnabled

    private val _availableVoices = mutableStateOf(voiceFeedbackManager.getAvailableVoices())
    val availableVoices: State<List<Voice>> = _availableVoices

    private val _selectedVoice = mutableStateOf<Voice?>(voiceFeedbackManager.getCurrentVoice())
    val selectedVoice: State<Voice?> = _selectedVoice

    private val _pitch = mutableStateOf(voiceFeedbackManager.getPitch())
    val pitch: State<Float> = _pitch

    private val _speechRate = mutableStateOf(voiceFeedbackManager.getSpeechRate())
    val speechRate: State<Float> = _speechRate

    fun setEnabled(enabled: Boolean) {
        voiceFeedbackManager.setEnabled(enabled)
        _isEnabled.value = enabled
    }

    fun setVoice(voice: Voice) {
        voiceFeedbackManager.setVoice(voice)
        _selectedVoice.value = voice
    }

    fun setPitch(pitch: Float) {
        voiceFeedbackManager.setPitch(pitch)
        this._pitch.value = pitch
    }

    fun setSpeechRate(rate: Float) {
        voiceFeedbackManager.setSpeechRate(rate)
        this._speechRate.value = rate
    }
}
