package com.nanzbeatles.innertube.models.body

import com.nanzbeatles.innertube.models.Context
import com.nanzbeatles.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
