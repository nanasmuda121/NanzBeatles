/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.nanzbeatles.nanas.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Home : Screens(
        titleId = R.string.home,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = "home"
    )

    object Videos : Screens(
        titleId = R.string.videos,
        iconIdInactive = R.drawable.slow_motion_video,
        iconIdActive = R.drawable.slow_motion_video,
        route = "videos"
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        iconIdInactive = R.drawable.library_music_outlined,
        iconIdActive = R.drawable.library_music_filled,
        route = "library"
    )

    companion object {
        val MainScreens = listOf(Home, Videos, Library)
    }
}