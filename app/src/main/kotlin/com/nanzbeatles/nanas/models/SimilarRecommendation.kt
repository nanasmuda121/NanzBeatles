/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.models

import com.nanzbeatles.innertube.models.YTItem
import com.nanzbeatles.nanas.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
