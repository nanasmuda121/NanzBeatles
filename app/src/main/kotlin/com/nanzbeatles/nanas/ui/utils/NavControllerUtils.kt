/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.utils

import androidx.navigation.NavController
import com.nanzbeatles.nanas.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.mapNotNull { it?.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
