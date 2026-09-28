/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.di

import com.nanzbeatles.nanas.lyrics.LyricsHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface LyricsHelperEntryPoint {
    fun lyricsHelper(): LyricsHelper
}
