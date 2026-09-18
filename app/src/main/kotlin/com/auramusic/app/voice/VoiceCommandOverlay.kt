package com.auramusic.app.voice

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun VoiceCommandOverlay(
    state: VoiceUiState,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(250)) + slideInVertically(tween(350)) { it / 3 },
        exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { it / 4 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp, start = 24.dp, end = 24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Feedback text (command result)
                AnimatedVisibility(
                    visible = state.phase == VoicePhase.FEEDBACK && state.feedbackText.isNotEmpty(),
                    enter = fadeIn(tween(200)) + expandVertically(),
                    exit = fadeOut(tween(150)) + shrinkVertically()
                ) {
                    Text(
                        text = state.feedbackText,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Recognized text (live transcription)
                AnimatedVisibility(
                    visible = state.recognizedText.isNotEmpty() && state.phase != VoicePhase.FEEDBACK,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(100))
                ) {
                    Text(
                        text = "\"${state.recognizedText}\"",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 20.dp)
                            .fillMaxWidth()
                    )
                }

                // Animated wave visualizer
                VoiceWaveVisualizer(
                    phase = state.phase,
                    amplitude = state.amplitude,
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Status text
                Text(
                    text = when (state.phase) {
                        VoicePhase.GREETING -> state.feedbackText.ifEmpty { "Hello! How can I help you today?" }
                        VoicePhase.LISTENING -> "Listening..."
                        VoicePhase.PROCESSING -> "Processing..."
                        VoicePhase.FEEDBACK -> ""
                        VoicePhase.ERROR -> state.errorMessage ?: "Something went wrong"
                        VoicePhase.IDLE -> "Say something..."
                    },
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Hint
                AnimatedVisibility(
                    visible = state.phase == VoicePhase.LISTENING && state.recognizedText.isEmpty(),
                    enter = fadeIn(tween(500, delayMillis = 800)),
                    exit = fadeOut(tween(150))
                ) {
                    Text(
                        text = "Try: \"Play\" · \"Next\" · \"Search songs\" · \"Volume up\"",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceWaveVisualizer(
    phase: VoicePhase,
    amplitude: Float,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")

    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val normalizedAmplitude = ((amplitude + 2f) / 12f).coerceIn(0f, 1f)

    val isActive = phase == VoicePhase.LISTENING || phase == VoicePhase.PROCESSING
    val isProcessing = phase == VoicePhase.PROCESSING
    val isGreeting = phase == VoicePhase.GREETING
    val isFeedback = phase == VoicePhase.FEEDBACK || isGreeting
    val isError = phase == VoicePhase.ERROR

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error

    val activeColor = when {
        isError -> errorColor
        isFeedback -> secondaryColor
        isProcessing -> secondaryColor
        else -> primaryColor
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val centerY = size.height / 2f
        val width = size.width
        val blobCount = 5
        val blobSpacing = width / (blobCount + 1)

        for (i in 0 until blobCount) {
            val x = blobSpacing * (i + 1)
            val phaseOffset = i * 72f
            val timeRad = Math.toRadians((time + phaseOffset).toDouble())

            val waveOffset = if (isActive) {
                (sin(timeRad) * 12f * (1f + normalizedAmplitude)).toFloat()
            } else if (isFeedback) {
                (sin(timeRad) * 6f).toFloat()
            } else {
                (sin(timeRad) * 3f).toFloat()
            }

            val baseRadius = if (isActive) {
                14f + normalizedAmplitude * 10f
            } else if (isFeedback) {
                12f
            } else {
                8f
            }

            val scale = if (isActive) pulseScale else 1f
            val radius = baseRadius * scale

            val blobAlpha = if (isActive) {
                0.5f + normalizedAmplitude * 0.4f + abs(sin(timeRad)).toFloat() * 0.1f
            } else if (isFeedback) {
                0.6f
            } else {
                0.3f
            }

            val color = when {
                i % 3 == 0 -> activeColor
                i % 3 == 1 -> secondaryColor
                else -> tertiaryColor
            }

            // Glow circle (larger, more transparent)
            drawCircle(
                color = color.copy(alpha = (blobAlpha * 0.3f).coerceIn(0f, 1f)),
                radius = radius * 2.2f,
                center = Offset(x, centerY + waveOffset)
            )

            // Main blob
            drawCircle(
                color = color.copy(alpha = blobAlpha.coerceIn(0f, 1f)),
                radius = radius,
                center = Offset(x, centerY + waveOffset)
            )

            // Inner bright core
            drawCircle(
                color = Color.White.copy(alpha = (blobAlpha * 0.5f).coerceIn(0f, 1f)),
                radius = radius * 0.4f,
                center = Offset(x, centerY + waveOffset)
            )
        }

        // Connecting wave line
        if (isActive || isFeedback) {
            val path = androidx.compose.ui.graphics.Path()
            var firstPoint = true
            for (px in 0..width.toInt() step 3) {
                val xf = px.toFloat()
                val t = Math.toRadians(time * 2 + xf * 0.5)
                val waveH = if (isActive) {
                    (sin(t) * 4f * (1f + normalizedAmplitude * 0.5f)).toFloat()
                } else {
                    (sin(t) * 2f).toFloat()
                }
                val y = centerY + waveH
                if (firstPoint) {
                    path.moveTo(xf, y)
                    firstPoint = false
                } else {
                    path.lineTo(xf, y)
                }
            }
            drawPath(
                path = path,
                color = activeColor.copy(alpha = 0.25f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )
        }
    }
}
