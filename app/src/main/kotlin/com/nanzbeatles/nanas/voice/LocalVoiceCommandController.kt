package com.nanzbeatles.nanas.voice

import androidx.compose.runtime.staticCompositionLocalOf

interface VoiceCommandController {
    fun requestManualActivation()
}

val LocalVoiceCommandController = staticCompositionLocalOf<VoiceCommandController> {
    object : VoiceCommandController {
        override fun requestManualActivation() {}
    }
}
