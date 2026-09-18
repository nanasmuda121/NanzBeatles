/**
 * Auramusic Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.auramusic.app.ui.utils

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.auramusic.app.constants.LiquidGlassBlurRadiusKey
import com.auramusic.app.constants.LiquidGlassCornerRadiusKey
import com.auramusic.app.constants.LiquidGlassEffectKey
import com.auramusic.app.constants.LiquidGlassOpacityKey
import com.auramusic.app.utils.rememberPreference

/**
 * Liquid Glass Effect - Apple/iOS style frosted monochrome glass
 * Creates a translucent frosted glass appearance with refraction border and specular highlight.
 * Keeps foreground content 100% sharp and visible.
 *
 * @param enabled Whether the liquid glass effect is enabled
 * @param cornerRadius The corner radius for the glass effect
 * @param alpha The transparency alpha (0.0 to 1.0)
 * @param blurRadius The blur intensity (requires Android 12+)
 */
@Composable
fun Modifier.liquidGlass(
    enabled: Boolean,
    cornerRadius: Dp = 18.dp,
    alpha: Float = 0.20f,
    blurRadius: Dp = 35.dp
): Modifier {
    return this.then(
        if (enabled) {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val glassBaseAlpha = if (isDark) alpha.coerceAtLeast(0.35f) else alpha.coerceAtLeast(0.25f)
            val borderBrush = Brush.verticalGradient(
                listOf(
                    if (isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.45f),
                    if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.12f)
                )
            )
            val highlightBrush = Brush.verticalGradient(
                listOf(
                    if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.20f),
                    Color.Transparent
                )
            )
            Modifier
                .clip(RoundedCornerShape(cornerRadius))
                .border(
                    width = 1.dp,
                    brush = borderBrush,
                    shape = RoundedCornerShape(cornerRadius)
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = glassBaseAlpha),
                    shape = RoundedCornerShape(cornerRadius)
                )
                .background(
                    brush = highlightBrush,
                    shape = RoundedCornerShape(cornerRadius)
                )
        } else {
            Modifier
        }
    )
}

/**
 * Liquid Glass container - provides frosted glass background with heavy blur on Android 12+
 * while keeping children in the content block crystal clear.
 */
@Composable
fun LiquidGlassContainer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    blurRadius: Dp = 35.dp,
    content: @Composable BoxScope.() -> Unit
) {
    if (enabled) {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        val baseAlpha = if (isDark) 0.65f else 0.45f
        val borderBrush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.05f)
            )
        )
        val highlightBrush = Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.12f),
                Color.Transparent
            )
        )
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .border(
                    width = 1.dp,
                    brush = borderBrush,
                    shape = RoundedCornerShape(cornerRadius)
                )
        ) {
            // Backdrop blurred layer (Android 12+)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        color = Color(0xFF121212).copy(alpha = baseAlpha),
                        shape = RoundedCornerShape(cornerRadius)
                    )
                    .background(
                        brush = highlightBrush,
                        shape = RoundedCornerShape(cornerRadius)
                    )
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(blurRadius)
                        } else {
                            Modifier
                        }
                    )
            )
            // Crisp foreground content layer
            content()
        }
    } else {
        Box(modifier = modifier) {
            content()
        }
    }
}

/**
 * Simple frosted glass background for cards and containers
 */
@Composable
fun FrostedGlassCard(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    LiquidGlassContainer(
        enabled = enabled,
        modifier = modifier,
        cornerRadius = cornerRadius,
        blurRadius = 35.dp,
        content = content
    )
}

/**
 * Convenience modifier that reads liquid glass preferences and applies the effect.
 * Use this for quick integration in any composable.
 */
@Composable
fun Modifier.liquidGlassFromPrefs(): Modifier {
    val enabled by rememberPreference(LiquidGlassEffectKey, defaultValue = true)
    val blurRadius by rememberPreference(LiquidGlassBlurRadiusKey, defaultValue = 35f)
    val cornerRadius by rememberPreference(LiquidGlassCornerRadiusKey, defaultValue = 18f)
    val opacity by rememberPreference(LiquidGlassOpacityKey, defaultValue = 0.20f)
    return this.liquidGlass(
        enabled = enabled,
        cornerRadius = cornerRadius.dp,
        alpha = opacity,
        blurRadius = blurRadius.dp
    )
}

/**
 * Convenience container that reads liquid glass preferences and applies the effect.
 */
@Composable
fun LiquidGlassContainerFromPrefs(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val enabled by rememberPreference(LiquidGlassEffectKey, defaultValue = true)
    val blurRadius by rememberPreference(LiquidGlassBlurRadiusKey, defaultValue = 35f)
    LiquidGlassContainer(
        enabled = enabled,
        modifier = modifier,
        cornerRadius = cornerRadius,
        blurRadius = blurRadius.dp,
        content = content
    )
}
