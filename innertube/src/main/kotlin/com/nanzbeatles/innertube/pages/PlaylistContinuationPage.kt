package com.nanzbeatles.innertube.pages

import com.nanzbeatles.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
