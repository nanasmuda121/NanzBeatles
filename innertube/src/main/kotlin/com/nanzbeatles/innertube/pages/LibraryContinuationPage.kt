package com.nanzbeatles.innertube.pages

import com.nanzbeatles.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
