package com.nanzbeatles.innertube.models.body

import com.nanzbeatles.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)
