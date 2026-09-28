/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.db.entities

sealed class LocalItem {
    abstract val id: String
    abstract val title: String
    abstract val thumbnailUrl: String?
}
