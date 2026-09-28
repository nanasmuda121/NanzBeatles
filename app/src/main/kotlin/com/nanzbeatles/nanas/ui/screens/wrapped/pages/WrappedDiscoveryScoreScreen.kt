/**
 * NanzBeatles Project (C) 2026
 */

package com.nanzbeatles.nanas.ui.screens.wrapped.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nanzbeatles.nanas.ui.screens.wrapped.components.AnimatedBackground
import com.nanzbeatles.nanas.ui.screens.wrapped.components.ShapeType
import com.nanzbeatles.nanas.ui.theme.bbh_bartle
import kotlinx.coroutines.delay

@Composable
fun WrappedDiscoveryScoreScreen(
    discoveryScore: Int,
    uniqueArtistCount: Int,
    isVisible: Boolean
) {
    var visible by remember { mutableStateOf(false) }
    val animatedScore = remember { Animatable(0f) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(200)
            visible = true
            if (discoveryScore > 0) {
                animatedScore.animateTo(
                    targetValue = discoveryScore.toFloat(),
                    animationSpec = tween(1500, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    val verdict = when {
        discoveryScore >= 20 -> "Penjelajah Musik Luar Biasa"
        discoveryScore >= 10 -> "Pendengar Petualang"
        discoveryScore >= 5 -> "Penjelajah Penasaran"
        discoveryScore >= 1 -> "Mulai Mencoba Hal Baru"
        else -> "Setia pada Favorit Anda"
    }

    val subtitle = when {
        discoveryScore >= 20 -> "Anda menjelajah banyak artis baru bulan ini"
        discoveryScore >= 10 -> "Anda gemar menemukan suara-suara baru"
        discoveryScore >= 5 -> "Perpaduan menarik antara musik baru dan lama"
        discoveryScore >= 1 -> "Anda mulai mencicipi hal baru"
        else -> "Tidak ada salahnya setia pada lagu yang Anda sukai"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedBackground(shapeTypes = listOf(ShapeType.Circle))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 200)) + slideInVertically(animationSpec = tween(1000, delayMillis = 200))
            ) {
                Text(
                    text = "Skor Penemuan",
                    style = TextStyle(
                        fontFamily = bbh_bartle,
                        fontSize = 40.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 48.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 400)) + slideInVertically(animationSpec = tween(1000, delayMillis = 400))
            ) {
                Text(
                    text = animatedScore.value.toInt().toString(),
                    style = TextStyle(
                        fontFamily = bbh_bartle,
                        fontSize = 96.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 600)) + slideInVertically(animationSpec = tween(1000, delayMillis = 600))
            ) {
                Text(
                    text = "artis baru ditemukan",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 800)) + slideInVertically(animationSpec = tween(1000, delayMillis = 800))
            ) {
                Text(
                    text = verdict,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 1000)) + slideInVertically(animationSpec = tween(1000, delayMillis = 1000))
            ) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(1000, delayMillis = 1200)) + slideInVertically(animationSpec = tween(1000, delayMillis = 1200))
            ) {
                Text(
                    text = "Dari total $uniqueArtistCount artis",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}
