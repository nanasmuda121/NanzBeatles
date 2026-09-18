package com.auramusic.app.lyrics

import android.content.Context
import com.auramusic.app.constants.EnableMusixmatchKey
import com.auramusic.app.utils.dataStore
import com.auramusic.app.utils.get
import com.auramusic.musixmatch.Musixmatch

object MusixmatchLyricsProvider : LyricsProvider {
    override val name = "Musixmatch"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableMusixmatchKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Musixmatch.getLyrics(title, artist, duration, album)
}
