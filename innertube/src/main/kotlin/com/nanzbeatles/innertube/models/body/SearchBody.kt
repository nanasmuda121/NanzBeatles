package com.nanzbeatles.innertube.models.body

import com.nanzbeatles.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context,
    val query: String?,
    val params: String?,
)
