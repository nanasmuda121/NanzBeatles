/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.di

import com.nanzbeatles.nanas.voice.VoiceCommandManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface VoiceCommandEntryPoint {
    fun voiceCommandManager(): VoiceCommandManager
}
