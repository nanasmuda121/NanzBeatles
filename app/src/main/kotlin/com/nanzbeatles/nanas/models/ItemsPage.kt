/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.models

import com.nanzbeatles.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
