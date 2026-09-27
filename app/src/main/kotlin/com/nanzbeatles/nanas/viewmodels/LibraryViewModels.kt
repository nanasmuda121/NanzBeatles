/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.nanzbeatles.nanas.viewmodels

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nanzbeatles.innertube.YouTube
import com.nanzbeatles.nanas.constants.AlbumFilter
import com.nanzbeatles.nanas.constants.AlbumFilterKey
import com.nanzbeatles.nanas.constants.AlbumSortDescendingKey
import com.nanzbeatles.nanas.constants.AlbumSortType
import com.nanzbeatles.nanas.constants.AlbumSortTypeKey
import com.nanzbeatles.nanas.constants.ArtistFilter
import com.nanzbeatles.nanas.constants.ArtistFilterKey
import com.nanzbeatles.nanas.constants.ArtistSongSortDescendingKey
import com.nanzbeatles.nanas.constants.ArtistSongSortType
import com.nanzbeatles.nanas.constants.ArtistSongSortTypeKey
import com.nanzbeatles.nanas.constants.ArtistSortDescendingKey
import com.nanzbeatles.nanas.constants.ArtistSortType
import com.nanzbeatles.nanas.constants.ArtistSortTypeKey
import com.nanzbeatles.nanas.constants.AudiobookIdsKey
import com.nanzbeatles.nanas.constants.AudiobookPositionsKey
import com.nanzbeatles.nanas.constants.HideExplicitKey
import com.nanzbeatles.nanas.constants.HideVideoSongsKey
import com.nanzbeatles.nanas.constants.LibraryFilter
import com.nanzbeatles.nanas.constants.PlaylistSortDescendingKey
import com.nanzbeatles.nanas.constants.PlaylistSortType
import com.nanzbeatles.nanas.constants.PlaylistSortTypeKey
import com.nanzbeatles.nanas.constants.SongFilter
import com.nanzbeatles.nanas.constants.SongFilterKey
import com.nanzbeatles.nanas.constants.SongSortDescendingKey
import com.nanzbeatles.nanas.constants.SongSortType
import com.nanzbeatles.nanas.constants.SongSortTypeKey
import com.nanzbeatles.nanas.constants.TopSize
import com.nanzbeatles.nanas.db.MusicDatabase
import com.nanzbeatles.nanas.db.entities.Song
import com.nanzbeatles.nanas.extensions.filterExplicit
import com.nanzbeatles.nanas.extensions.filterExplicitAlbums
import com.nanzbeatles.nanas.extensions.filterVideoSongs
import com.nanzbeatles.nanas.extensions.toEnum
import com.nanzbeatles.nanas.playback.DownloadUtil
import com.nanzbeatles.nanas.utils.SyncUtils
import com.nanzbeatles.nanas.utils.AUDIOBOOK_MIN_DURATION_SECONDS
import com.nanzbeatles.nanas.utils.dataStore
import com.nanzbeatles.nanas.utils.decodeAudiobookIds
import com.nanzbeatles.nanas.utils.decodeAudiobookPositions
import com.nanzbeatles.nanas.utils.encodeAudiobookIds
import com.nanzbeatles.nanas.utils.encodeAudiobookPositions
import com.nanzbeatles.nanas.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class LibrarySongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    downloadUtil: DownloadUtil,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allSongs =
        context.dataStore.data
            .map {
                Triple(
                    Triple(
                        it[SongFilterKey].toEnum(SongFilter.LIKED),
                        it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                        (it[SongSortDescendingKey] ?: true),
                    ),
                    it[HideExplicitKey] ?: false,
                    it[HideVideoSongsKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit, hideVideoSongs) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    SongFilter.LIBRARY -> database.songs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.LIKED -> database.likedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    SongFilter.DOWNLOADED -> database.downloadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                    // Uploaded feature is temporarily disabled
                    SongFilter.UPLOADED -> kotlinx.coroutines.flow.flowOf(emptyList())
                    // SongFilter.UPLOADED -> database.uploadedSongs(sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun syncLikedSongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedSongs() }
    }

    fun syncLibrarySongs() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLibrarySongs() }
    }

    // Uploaded feature is temporarily disabled
    fun syncUploadedSongs() {
        // viewModelScope.launch(Dispatchers.IO) { syncUtils.syncUploadedSongs() }
    }
}

data class AudiobookLibraryItem(
    val song: Song,
    val resumePositionMs: Long,
    val pinned: Boolean,
)

@HiltViewModel
class LibraryAudiobooksViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    database: MusicDatabase,
) : ViewModel() {
    val audiobooks =
        combine(
            database.allSongs(),
            context.dataStore.data,
        ) { songs, preferences ->
            val audiobookIds = decodeAudiobookIds(preferences[AudiobookIdsKey])
            val positions = decodeAudiobookPositions(preferences[AudiobookPositionsKey])
            val hideExplicit = preferences[HideExplicitKey] ?: false
            val hideVideoSongs = preferences[HideVideoSongsKey] ?: false

            songs
                .asSequence()
                .filter { it.song.inLibrary != null || it.song.isDownloaded || it.id in audiobookIds }
                .filter { it.id in audiobookIds || it.song.duration >= AUDIOBOOK_MIN_DURATION_SECONDS }
                .filter { !hideExplicit || !it.song.explicit }
                .filter { !hideVideoSongs || !it.song.isVideo }
                .map {
                    AudiobookLibraryItem(
                        song = it,
                        resumePositionMs = positions[it.id]?.coerceIn(0L, (it.song.duration * 1000L).coerceAtLeast(0L)) ?: 0L,
                        pinned = it.id in audiobookIds,
                    )
                }
                .sortedWith(
                    compareByDescending<AudiobookLibraryItem> { it.resumePositionMs > 0L }
                        .thenByDescending { it.pinned }
                        .thenBy { it.song.song.title.lowercase() }
                )
                .toList()
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setPinned(songId: String, pinned: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                val ids = decodeAudiobookIds(preferences[AudiobookIdsKey]).toMutableSet()
                if (pinned) ids += songId else ids -= songId
                preferences[AudiobookIdsKey] = encodeAudiobookIds(ids)
            }
        }
    }

    fun clearResumePosition(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.edit { preferences ->
                val positions = decodeAudiobookPositions(preferences[AudiobookPositionsKey]).toMutableMap()
                positions -= songId
                preferences[AudiobookPositionsKey] = encodeAudiobookPositions(positions)
            }
        }
    }
}

@HiltViewModel
class LibraryArtistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val artistDetailFetchAttempted = mutableSetOf<String>()

    val allArtists =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                    it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                    it[ArtistSortDescendingKey] ?: true,
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filter, sortType, descending) ->
                when (filter) {
                    ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncArtistsSubscriptions() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allArtists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter { artist ->
                        val isStale = Duration.between(
                            artist.lastUpdateTime,
                            LocalDateTime.now()
                        ) > Duration.ofDays(10)
                        val missingSubscriberCount =
                            artist.bookmarkedAt != null &&
                                artist.isYouTubeArtist &&
                                !artist.isPrivatelyOwnedArtist &&
                                artist.subscriberCountText.isNullOrBlank()

                        (artist.thumbnailUrl == null || isStale || missingSubscriberCount) &&
                            artistDetailFetchAttempted.add(artist.id)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryAlbumsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allAlbums =
        context.dataStore.data
            .map {
                Pair(
                    Triple(
                        it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                        it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                        (it[AlbumSortDescendingKey] ?: true),
                    ),
                    it[HideExplicitKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (filterSort, hideExplicit) ->
                val (filter, sortType, descending) = filterSort
                when (filter) {
                    AlbumFilter.LIKED -> database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    AlbumFilter.LIBRARY -> database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                    // Uploaded feature is temporarily disabled
                    AlbumFilter.UPLOADED -> kotlinx.coroutines.flow.flowOf(emptyList())
                    // AlbumFilter.UPLOADED -> database.albumsUploaded(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncLikedAlbums() }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            allAlbums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
    }
}
@HiltViewModel
class LibraryPlaylistsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    val allPlaylists =
        context.dataStore.data
            .map {
                it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CREATE_DATE) to (it[PlaylistSortDescendingKey]
                    ?: true)
            }.distinctUntilChanged()
            .flatMapLatest { (sortType, descending) ->
                database.playlists(sortType, descending)
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) { syncUtils.syncSavedPlaylists() }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
}

@HiltViewModel
class ArtistSongsViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val artistId = savedStateHandle.get<String>("artistId")!!
    val artist =
        database
            .artist(artistId)
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val songs =
        context.dataStore.data
            .map {
                Triple(
                    it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (it[ArtistSongSortDescendingKey]
                        ?: true),
                    it[HideExplicitKey] ?: false,
                    it[HideVideoSongsKey] ?: false
                )
            }.distinctUntilChanged()
            .flatMapLatest { (sortDesc, hideExplicit, hideVideoSongs) ->
                val (sortType, descending) = sortDesc
                database.artistSongs(artistId, sortType, descending).map { it.filterExplicit(hideExplicit).filterVideoSongs(hideVideoSongs) }
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@HiltViewModel
class LibraryMixViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    database: MusicDatabase,
    private val syncUtils: SyncUtils,
) : ViewModel() {
    private val artistDetailFetchAttempted = mutableSetOf<String>()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    val syncAllLibrary = {
         viewModelScope.launch(Dispatchers.IO) {
             syncUtils.tryAutoSync()
         }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            syncUtils.performFullSyncSuspend()
            _isRefreshing.value = false
        }
    }

    val topValue =
        context.dataStore.data
            .map { it[TopSize] ?: "50" }
            .distinctUntilChanged()
    var artists =
        database
            .artistsBookmarked(
                ArtistSortType.CREATE_DATE,
                true,
            ).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var albums = context.dataStore.data
        .map { it[HideExplicitKey] ?: false }
        .distinctUntilChanged()
        .flatMapLatest { hideExplicit ->
            database.albumsLiked(AlbumSortType.CREATE_DATE, true).map { it.filterExplicitAlbums(hideExplicit) }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    var playlists = database.playlists(PlaylistSortType.CREATE_DATE, true)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            albums.collect { albums ->
                albums
                    .filter {
                        it.album.songCount == 0
                    }.forEach { album ->
                        YouTube
                            .album(album.id)
                            .onSuccess { albumPage ->
                                database.query {
                                    update(album.album, albumPage, album.artists)
                                }
                            }.onFailure {
                                reportException(it)
                                if (it.message?.contains("NOT_FOUND") == true) {
                                    database.query {
                                        delete(album.album)
                                    }
                                }
                            }
                    }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            artists.collect { artists ->
                artists
                    .map { it.artist }
                    .filter { artist ->
                        val isStale = Duration.between(
                            artist.lastUpdateTime,
                            LocalDateTime.now(),
                        ) > Duration.ofDays(10)
                        val missingSubscriberCount =
                            artist.bookmarkedAt != null &&
                                artist.isYouTubeArtist &&
                                !artist.isPrivatelyOwnedArtist &&
                                artist.subscriberCountText.isNullOrBlank()

                        (artist.thumbnailUrl == null || isStale || missingSubscriberCount) &&
                            artistDetailFetchAttempted.add(artist.id)
                    }.forEach { artist ->
                        YouTube.artist(artist.id).onSuccess { artistPage ->
                            database.query {
                                update(artist, artistPage)
                            }
                        }
                    }
            }
        }
    }
}

@HiltViewModel
class LibraryViewModel
@Inject
constructor() : ViewModel() {
    private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
    val filter: MutableState<LibraryFilter> = curScreen
}
