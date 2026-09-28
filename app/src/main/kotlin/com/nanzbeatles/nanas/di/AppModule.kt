/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import com.nanzbeatles.nanas.constants.MaxSongCacheSizeKey
import com.nanzbeatles.nanas.db.InternalDatabase
import com.nanzbeatles.nanas.db.MusicDatabase
import com.nanzbeatles.nanas.hardware.HardwareIntegrationManager
import com.nanzbeatles.nanas.listentogether.ListenTogetherClient
import com.nanzbeatles.nanas.listentogether.ListenTogetherManager
import com.nanzbeatles.nanas.utils.dataStore
import com.nanzbeatles.nanas.utils.get
import com.nanzbeatles.nanas.voice.VoiceFeedbackManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideVoiceFeedbackManager(
        @ApplicationContext context: Context,
    ): VoiceFeedbackManager = VoiceFeedbackManager(context)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @Singleton
    @Provides
    fun provideDao(
        database: InternalDatabase,
    ) = database.dao

    @Singleton
    @Provides
    fun provideInternalDatabase(
        @ApplicationContext context: Context,
    ): InternalDatabase = Room
        .databaseBuilder(context, InternalDatabase::class.java, InternalDatabase.DB_NAME)
        .addMigrations(
            com.nanzbeatles.nanas.db.MIGRATION_1_2,
            com.nanzbeatles.nanas.db.MIGRATION_21_24,
            com.nanzbeatles.nanas.db.MIGRATION_22_24,
            com.nanzbeatles.nanas.db.MIGRATION_24_25,
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Singleton
    @Provides
    fun provideDatabase(
        internalDatabase: InternalDatabase,
    ): MusicDatabase = MusicDatabase(internalDatabase)

    @Singleton
    @Provides
    fun provideDatabaseProvider(
        @ApplicationContext context: Context,
    ): DatabaseProvider = StandaloneDatabaseProvider(context)

    @Singleton
    @Provides
    @PlayerCache
    fun providePlayerCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        // On TV (Android Leanback) we behave like a streaming app such as
        // Spotify TV: the player cache is bounded to a small ring buffer so
        // long listening sessions do not silently fill device storage with
        // cached audio. The bounded LRU evictor keeps just enough on disk to
        // smooth out network hiccups / re-seeks while constantly recycling
        // older chunks.
        val isTv = context.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_LEANBACK
        )
        val cacheSize = if (isTv) {
            TV_PLAYER_CACHE_SIZE_MB
        } else {
            context.dataStore[MaxSongCacheSizeKey] ?: 1024
        }
        return SimpleCache(
            context.filesDir.resolve("exoplayer"),
            when (cacheSize) {
                -1 -> NoOpCacheEvictor()
                else -> LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024L)
            },
            databaseProvider,
        )
    }

    // Small streaming-only buffer for TV (in MB).
    private const val TV_PLAYER_CACHE_SIZE_MB = 64

    @Singleton
    @Provides
    @DownloadCache
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        return SimpleCache(
            context.filesDir.resolve("download"),
            NoOpCacheEvictor(),
            databaseProvider
        )
    }

    @Singleton
    @Provides
    fun provideListenTogetherClient(
        @ApplicationContext context: Context,
    ): ListenTogetherClient = ListenTogetherClient(context)

    @Singleton
    @Provides
    fun provideListenTogetherManager(
        @ApplicationContext context: Context,
        client: ListenTogetherClient,
    ): ListenTogetherManager = ListenTogetherManager(client, context)

    @Singleton
    @Provides
    fun providePoTokenProvider(
        @ApplicationContext context: Context,
    ): com.nanzbeatles.innertube.PoTokenProvider =
        com.nanzbeatles.nanas.playback.potoken.WebViewPoTokenProvider(context)
}
