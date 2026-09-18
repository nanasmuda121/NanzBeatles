/**
 * AuraMusic Project (C) 2026
 * Licensed under GPL-3.0. See LICENSE file for details.
 */

package com.auramusic.app.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFE0E0E0)

@Composable
fun NanzBeatlesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    selectedFont: String = "OUTFIT",
    fontScale: Float = 1f,
    fontBoldness: Float = 0f,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    // Convert seed color to pure monochrome (grayscale) to ensure 100% monochrome design
    val monoSeed = remember(themeColor) {
        val lum = themeColor.luminance()
        Color(lum, lum, lum, 1f)
    }

    // Generate clean monochrome color scheme
    val baseColorScheme = rememberDynamicColorScheme(
        seedColor = monoSeed,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.Monochrome
    )

    // Apply pureBlack modification and ensure maximum contrast (black text on white cards, etc.)
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        val scheme = if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
        if (!darkTheme) {
            scheme.copy(
                onPrimary = if (scheme.primary.luminance() > 0.5f) Color.Black else Color.White,
                onPrimaryContainer = if (scheme.primaryContainer.luminance() > 0.5f) Color.Black else Color.White,
                onSecondary = if (scheme.secondary.luminance() > 0.5f) Color.Black else Color.White,
                onSecondaryContainer = if (scheme.secondaryContainer.luminance() > 0.5f) Color.Black else Color.White,
                onTertiary = if (scheme.tertiary.luminance() > 0.5f) Color.Black else Color.White,
                onTertiaryContainer = if (scheme.tertiaryContainer.luminance() > 0.5f) Color.Black else Color.White,
                onSurface = Color.Black,
                onSurfaceVariant = Color(0xFF2B2B2B),
                onBackground = Color.Black,
            )
        } else {
            scheme.copy(
                onPrimary = if (scheme.primary.luminance() > 0.5f) Color.Black else Color.White,
                onPrimaryContainer = if (scheme.primaryContainer.luminance() > 0.5f) Color.Black else Color.White,
                onSecondary = if (scheme.secondary.luminance() > 0.5f) Color.Black else Color.White,
                onSecondaryContainer = if (scheme.secondaryContainer.luminance() > 0.5f) Color.Black else Color.White,
                onTertiary = if (scheme.tertiary.luminance() > 0.5f) Color.Black else Color.White,
                onTertiaryContainer = if (scheme.tertiaryContainer.luminance() > 0.5f) Color.Black else Color.White,
                onSurface = Color(0xFFEEEEEE),
                onSurfaceVariant = Color(0xFFBDBDBD),
            )
        }
    }

    val fontFamily = when (selectedFont) {
        "DEFAULT" -> FontFamily.Default
        "MANROPE" -> Manrope
        "SPACE_GROTESK" -> SpaceGrotesk
        "POPPINS", "ROBOTO", "INTER", "OUTFIT" -> Outfit
        else -> Outfit
    }
    val typography = remember(fontFamily, fontScale, fontBoldness) {
        AppTypography.withFontFamily(fontFamily).scaledBy(fontScale).boldedBy(fontBoldness)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}

@Composable
fun AuraMusicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    selectedFont: String = "OUTFIT",
    fontScale: Float = 1f,
    fontBoldness: Float = 0f,
    content: @Composable () -> Unit,
) = NanzBeatlesTheme(
    darkTheme = darkTheme,
    pureBlack = pureBlack,
    themeColor = themeColor,
    selectedFont = selectedFont,
    fontScale = fontScale,
    fontBoldness = fontBoldness,
    content = content
)

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    val c = Color(rankedColors.first())
    val lum = c.luminance()
    return Color(lum, lum, lum, 1f)
}

fun Bitmap.extractGradientColors(): List<Color> {
    return listOf(Color(0xFF3A3A3C), Color(0xFF1C1C1E))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}

private fun androidx.compose.material3.Typography.withFontFamily(fontFamily: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily)
)

private fun androidx.compose.material3.Typography.scaledBy(scale: Float) =
    if (scale <= 0f || scale == 1f) {
        this
    } else {
        copy(
            displayLarge = displayLarge.scaledBy(scale),
            displayMedium = displayMedium.scaledBy(scale),
            displaySmall = displaySmall.scaledBy(scale),
            headlineLarge = headlineLarge.scaledBy(scale),
            headlineMedium = headlineMedium.scaledBy(scale),
            headlineSmall = headlineSmall.scaledBy(scale),
            titleLarge = titleLarge.scaledBy(scale),
            titleMedium = titleMedium.scaledBy(scale),
            titleSmall = titleSmall.scaledBy(scale),
            bodyLarge = bodyLarge.scaledBy(scale),
            bodyMedium = bodyMedium.scaledBy(scale),
            bodySmall = bodySmall.scaledBy(scale),
            labelLarge = labelLarge.scaledBy(scale),
            labelMedium = labelMedium.scaledBy(scale),
            labelSmall = labelSmall.scaledBy(scale)
        )
    }

private fun TextStyle.scaledBy(scale: Float) =
    copy(
        fontSize = (fontSize.value * scale.coerceIn(0.5f, 3f)).sp,
        lineHeight = (lineHeight.value * scale.coerceIn(0.5f, 3f)).sp,
        letterSpacing = (letterSpacing.value * scale.coerceIn(0.5f, 3f)).sp,
    )

private fun androidx.compose.material3.Typography.boldedBy(boldness: Float) =
    if (boldness <= 0f) this
    else copy(
        displayLarge = displayLarge.boldedBy(boldness),
        displayMedium = displayMedium.boldedBy(boldness),
        displaySmall = displaySmall.boldedBy(boldness),
        headlineLarge = headlineLarge.boldedBy(boldness),
        headlineMedium = headlineMedium.boldedBy(boldness),
        headlineSmall = headlineSmall.boldedBy(boldness),
        titleLarge = titleLarge.boldedBy(boldness),
        titleMedium = titleMedium.boldedBy(boldness),
        titleSmall = titleSmall.boldedBy(boldness),
        bodyLarge = bodyLarge.boldedBy(boldness),
        bodyMedium = bodyMedium.boldedBy(boldness),
        bodySmall = bodySmall.boldedBy(boldness),
        labelLarge = labelLarge.boldedBy(boldness),
        labelMedium = labelMedium.boldedBy(boldness),
        labelSmall = labelSmall.boldedBy(boldness)
    )

private fun TextStyle.boldedBy(boldness: Float) = copy(
    fontWeight = when (fontWeight) {
        FontWeight.Thin -> lerp(FontWeight.Thin, FontWeight.Black, boldness)
        FontWeight.Light -> lerp(FontWeight.Light, FontWeight.Black, boldness)
        FontWeight.Normal -> lerp(FontWeight.Normal, FontWeight.Black, boldness)
        FontWeight.Medium -> lerp(FontWeight.Medium, FontWeight.Black, boldness)
        FontWeight.SemiBold -> lerp(FontWeight.SemiBold, FontWeight.Black, boldness)
        FontWeight.Bold -> FontWeight.Bold
        FontWeight.Black -> FontWeight.Black
        else -> fontWeight
    }
)

private fun lerp(a: FontWeight, b: FontWeight, fraction: Float): FontWeight {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    return when {
        clampedFraction <= 0f -> a
        clampedFraction >= 1f -> b
        else -> FontWeight((a.weight + (b.weight - a.weight) * clampedFraction).toInt())
    }
}
