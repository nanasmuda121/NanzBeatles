/**
 * AuraMusic Project (C) 2026
 * Enhanced animations and transitions for smooth UI interactions
 */

package com.auramusic.app.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

// Standard animation specs for consistency
@Stable
object AuraAnimations {
    val FastTween = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val MediumTween = tween<Float>(durationMillis = 300, easing = FastOutSlowInEasing)
    val SlowTween = tween<Float>(durationMillis = 500, easing = FastOutSlowInEasing)
    val BounceSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)
    val LinearTween = tween<Float>(durationMillis = 300, easing = LinearEasing)
}

// Enhanced screen transitions
object AuraTransitions {
    val SlideInFromRight = slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))

    val SlideOutToLeft = slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))

    val SlideInFromLeft = slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))

    val SlideOutToRight = slideOutHorizontally(
        targetOffsetX = { it },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))

    val ScaleIn = scaleIn(
        initialScale = 0.9f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
    ) + fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))

    val ScaleOut = scaleOut(
        targetScale = 0.9f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))

    val FadeInUp = slideInVertically(
        initialOffsetY = { it / 4 },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeIn(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))

    val FadeOutDown = slideOutVertically(
        targetOffsetY = { it / 4 },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    ) + fadeOut(animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing))
}

// Enhanced animated visibility with better defaults
@Composable
fun AuraAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    enter: EnterTransition = AuraTransitions.ScaleIn,
    exit: ExitTransition = AuraTransitions.ScaleOut,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        content = content
    )
}

// Staggered animation for lists
@Composable
fun StaggeredAnimatedVisibility(
    visible: Boolean,
    index: Int,
    modifier: Modifier = Modifier,
    delayMillis: Int = 50,
    content: @Composable () -> Unit
) {
    AuraAnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(
            initialOffsetY = { it / 4 },
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * delayMillis,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 300,
                delayMillis = index * delayMillis,
                easing = FastOutSlowInEasing
            )
        ),
        exit = AuraTransitions.FadeOutDown,
        content = { content() }
    )
}

// Enhanced container with automatic animations
@Composable
fun AnimatedContainer(
    visible: Boolean,
    modifier: Modifier = Modifier,
    transitionType: String = "scale", // "scale", "slide", "fade"
    content: @Composable () -> Unit
) {
    val (enter, exit) = when (transitionType) {
        "slide" -> AuraTransitions.FadeInUp to AuraTransitions.FadeOutDown
        "fade" -> fadeIn(AuraAnimations.MediumTween) to fadeOut(AuraAnimations.MediumTween)
        else -> AuraTransitions.ScaleIn to AuraTransitions.ScaleOut
    }

    AuraAnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit
    ) {
        content()
    }
}