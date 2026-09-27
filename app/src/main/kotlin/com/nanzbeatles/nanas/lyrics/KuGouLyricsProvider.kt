/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nanzbeatles.nanas.lyrics

import android.content.Context
import com.nanzbeatles.kugou.KuGou
import com.nanzbeatles.nanas.constants.EnableKugouKey
import com.nanzbeatles.nanas.utils.dataStore
import com.nanzbeatles.nanas.utils.get

object KuGouLyricsProvider : LyricsProvider {
    override val name = "KuGou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableKugouKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        KuGou.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, album, callback)
    }
}
