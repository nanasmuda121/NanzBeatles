package com.nanzbeatles.innertube.pages

import com.nanzbeatles.innertube.MixesPage
import com.nanzbeatles.innertube.PodcastsPage
import com.nanzbeatles.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
    val podcasts: List<PodcastsPage.PodcastSection> = emptyList(),
    val mixes: List<MixesPage.MixSection> = emptyList(),
)
