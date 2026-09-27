/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nanzbeatles.nanas.di

import android.content.Context
import com.nanzbeatles.nanas.db.MusicDatabase
import com.nanzbeatles.nanas.utils.SyncUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * TV-specific DI module.
 *
 * Provides dependencies that are needed by the TV flavor but may not be
 * available in the main DI graph (e.g., SyncUtils).
 */
@Module
@InstallIn(SingletonComponent::class)
object TvModule {
    @Provides
    @Singleton
    fun provideSyncUtils(
        @ApplicationContext context: Context,
        database: MusicDatabase,
    ): SyncUtils = SyncUtils(context, database)
}
