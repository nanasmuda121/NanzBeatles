/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nanzbeatles.nanas.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.nanzbeatles.innertube.models.AlbumItem
import com.nanzbeatles.innertube.models.ArtistItem
import com.nanzbeatles.innertube.models.EpisodeItem
import com.nanzbeatles.innertube.models.PlaylistItem
import com.nanzbeatles.innertube.models.PodcastItem
import com.nanzbeatles.innertube.models.SongItem
import com.nanzbeatles.nanas.LocalPlayerAwareWindowInsets
import com.nanzbeatles.nanas.LocalPlayerConnection
import com.nanzbeatles.nanas.R
import com.nanzbeatles.nanas.constants.GridItemSize
import com.nanzbeatles.nanas.constants.GridItemsSizeKey
import com.nanzbeatles.nanas.constants.GridThumbnailHeight
import com.nanzbeatles.nanas.models.toMediaMetadata
import com.nanzbeatles.nanas.playback.queues.YouTubeQueue
import com.nanzbeatles.nanas.ui.component.IconButton
import com.nanzbeatles.nanas.ui.component.LocalMenuState
import com.nanzbeatles.nanas.ui.component.YouTubeGridItem
import com.nanzbeatles.nanas.ui.component.shimmer.GridItemPlaceHolder
import com.nanzbeatles.nanas.ui.component.shimmer.ShimmerHost
import com.nanzbeatles.nanas.ui.menu.YouTubeAlbumMenu
import com.nanzbeatles.nanas.ui.menu.YouTubeArtistMenu
import com.nanzbeatles.nanas.ui.menu.YouTubePlaylistMenu
import com.nanzbeatles.nanas.ui.menu.YouTubeSongMenu
import com.nanzbeatles.nanas.ui.utils.backToMain
import com.nanzbeatles.nanas.utils.rememberEnumPreference
import com.nanzbeatles.nanas.viewmodels.YouTubeBrowseViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val browseResult by viewModel.result.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val allItems = browseResult?.items?.flatMap { it.items } ?: emptyList()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        if (browseResult == null) {
            items(8) {
                ShimmerHost {
                    GridItemPlaceHolder(fillMaxWidth = true)
                }
            }
        }

        items(
            items = allItems.distinctBy { it.id },
            key = { it.id }
        ) { item ->
            YouTubeGridItem(
                item = item,
                isActive = when (item) {
                    is SongItem -> mediaMetadata?.id == item.id
                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                    else -> false
                },
                isPlaying = isPlaying,
                fillMaxWidth = true,
                coroutineScope = coroutineScope,
                modifier = Modifier
                    .combinedClickable(
                        onClick = {
                            when (item) {
                                is SongItem -> {
                                    if (item.id == mediaMetadata?.id) {
                                        playerConnection.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue.radio(item.toMediaMetadata())
                                        )
                                    }
                                }
                                is AlbumItem -> navController.navigate("album/${item.id}")
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                                is PodcastItem -> navController.navigate("online_podcast/${item.id}")
                                is EpisodeItem -> playerConnection.playQueue(
                                    YouTubeQueue.radio(item.toMediaMetadata())
                                )
                            }
                        },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                when (item) {
                                    is SongItem ->
                                        YouTubeSongMenu(
                                            song = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    is AlbumItem ->
                                        YouTubeAlbumMenu(
                                            albumItem = item,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    is ArtistItem ->
                                        YouTubeArtistMenu(
                                            artist = item,
                                            onDismiss = menuState::dismiss,
                                        )
                                    is PlaylistItem ->
                                        YouTubePlaylistMenu(
                                            playlist = item,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    is PodcastItem ->
                                        YouTubePlaylistMenu(
                                            playlist = item.asPlaylistItem(),
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    is EpisodeItem ->
                                        YouTubeSongMenu(
                                            song = item.asSongItem(),
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                }
                            }
                        }
                    )
                    .animateItem()
            )
        }
    }

    TopAppBar(
        title = { Text(browseResult?.title.orEmpty()) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )
}
