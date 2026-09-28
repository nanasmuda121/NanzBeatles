/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.tv

import android.content.Context
import com.nanzbeatles.nanas.db.MusicDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point that lets TV-only Composables access singletons (database)
 * without going through a per-id ViewModel. Detail screens and the search
 * field reach for the database directly via this entry point so we can pass
 * navigation arguments (album/artist/playlist ids) without nav-compose.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TvAppEntryPoint {
    fun database(): MusicDatabase
}

internal fun Context.tvDatabase(): MusicDatabase =
    EntryPointAccessors.fromApplication(applicationContext, TvAppEntryPoint::class.java)
        .database()
