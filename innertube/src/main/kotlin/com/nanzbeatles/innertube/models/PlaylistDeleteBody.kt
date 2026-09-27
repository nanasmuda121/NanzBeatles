package com.nanzbeatles.innertube.models.body

import com.nanzbeatles.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String
)
