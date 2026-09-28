/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.lyrics

import android.content.Context
import com.nanzbeatles.music.betterlyrics.BetterLyrics
import com.nanzbeatles.nanas.constants.EnableBetterLyricsKey
import com.nanzbeatles.nanas.utils.dataStore
import com.nanzbeatles.nanas.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)
}
