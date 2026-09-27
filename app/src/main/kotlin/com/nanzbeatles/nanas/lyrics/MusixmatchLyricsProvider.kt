package com.nanzbeatles.nanas.lyrics

import android.content.Context
import com.nanzbeatles.nanas.constants.EnableMusixmatchKey
import com.nanzbeatles.nanas.utils.dataStore
import com.nanzbeatles.nanas.utils.get
import com.nanzbeatles.musixmatch.Musixmatch

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
