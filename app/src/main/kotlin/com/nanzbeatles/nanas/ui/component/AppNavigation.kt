/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.component

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nanzbeatles.nanas.constants.LiquidGlassApplyNavBarKey
import com.nanzbeatles.nanas.constants.LiquidGlassEffectKey
import com.nanzbeatles.nanas.ui.screens.Screens
import com.nanzbeatles.nanas.utils.rememberPreference

@Immutable
private data class NavItemState(
    val isSelected: Boolean,
    val iconRes: Int
)

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    return navigationItems.any { it.route == screenRoute } && 
           currentRoute.startsWith("$screenRoute/")
}

@Composable
fun AppNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (pureBlack && isDark) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    
    NavigationRail(
        modifier = modifier,
        containerColor = containerColor
    ) {
        navigationItems.forEach { screen ->
            val isSelected = remember(currentRoute, screen.route) {
                isRouteSelected(currentRoute, screen.route, navigationItems)
            }
            val iconRes = remember(isSelected, screen) {
                if (isSelected) screen.iconIdActive else screen.iconIdInactive
            }
            
            NavigationRailItem(
                selected = isSelected,
                onClick = { onItemClick(screen, isSelected) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = if (isDark) Color.White else Color.Black,
                    selectedTextColor = if (isDark) Color.White else Color.Black,
                    unselectedIconColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF616161),
                    unselectedTextColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF616161),
                    indicatorColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                ),
                icon = {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = stringResource(screen.titleId)
                    )
                }
            )
        }
    }
}

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
) {
    val liquidGlassEnabled by rememberPreference(LiquidGlassEffectKey, defaultValue = true)
    val liquidGlassApplyNavBar by rememberPreference(LiquidGlassApplyNavBarKey, defaultValue = true)
    val isGlass = liquidGlassEnabled && liquidGlassApplyNavBar
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Box(modifier = modifier) {
        if (isGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(30.dp)
                        } else {
                            Modifier
                        }
                    )
                    .background(
                        if (isDark) Color(0xFF121212).copy(alpha = 0.88f)
                        else Color(0xFFF7F7F7).copy(alpha = 0.94f)
                    )
                    .border(
                        BorderStroke(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                listOf(
                                    if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                    )
            )
        }

        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = if (isGlass) Color.Transparent else if (pureBlack && isDark) Color.Black else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (isDark) Color.White else Color.Black
        ) {
            navigationItems.forEach { screen ->
                val isSelected = remember(currentRoute, screen.route) {
                    isRouteSelected(currentRoute, screen.route, navigationItems)
                }
                val iconRes = remember(isSelected, screen) {
                    if (isSelected) screen.iconIdActive else screen.iconIdInactive
                }
                
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onItemClick(screen, isSelected) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = if (isDark) Color.White else Color.Black,
                        selectedTextColor = if (isDark) Color.White else Color.Black,
                        unselectedIconColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF616161),
                        unselectedTextColor = if (isDark) Color(0xFF9E9E9E) else Color(0xFF616161),
                        indicatorColor = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.10f)
                    ),
                    icon = {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = stringResource(screen.titleId)
                        )
                    },
                    label = if (!slimNav) {
                        {
                            Text(
                                text = stringResource(screen.titleId),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else null
                )
            }
        }
    }
}