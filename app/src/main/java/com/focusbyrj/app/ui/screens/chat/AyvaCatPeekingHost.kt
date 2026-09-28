package com.focusbyrj.app.ui.screens.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AyvaCatFloatingView(
    isVisible: Boolean,
    isActionPlaying: Boolean,
    currentActionAsset: String,
    onDismissAction: () -> Unit,
    onCatTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(500)),
        exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400)),
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = isActionPlaying,
            transitionSpec = {
                (fadeIn(tween(350)) + scaleIn(initialScale = 0.7f, animationSpec = tween(350)))
                    .togetherWith(fadeOut(tween(250)) + scaleOut(targetScale = 0.7f, animationSpec = tween(250)))
            },
            label = "cat_view_transition"
        ) { playingAction ->
            if (playingAction) {
                CatActionLottieView(
                    assetName = currentActionAsset,
                    onDismiss = onDismissAction
                )
            } else {
                CatLottiePeekingView(onTap = onCatTap)
            }
        }
    }
}

@Composable
fun CatLottiePeekingView(
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(com.focusbyrj.app.R.raw.cat_animation)
    )
    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(composition) {
        val comp = composition ?: return@LaunchedEffect
        val dur = comp.duration.toLong().coerceIn(1500L, 4000L)
        while (true) {
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = dur.toInt(),
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
            delay(1200L)
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(
                    durationMillis = (dur * 0.75f).toInt().coerceAtLeast(1000),
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
            delay(800L)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }

    Box(
        modifier = modifier
            .scale(scaleAnim.value)
            .width(96.dp)
            .height(55.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                coroutineScope.launch {
                    scaleAnim.animateTo(
                        targetValue = 1.15f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    )
                    scaleAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    )
                }
                onTap()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        LottieAnimation(
            composition = composition,
            progress = { animProgress.value },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CatActionLottieView(
    assetName: String = "cat_action.lottie",
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(assetName)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    val startTime = remember { System.currentTimeMillis() }

    val targetSize = when (assetName) {
        "cat_error.lottie" -> 250.dp
        "cat_angry.lottie" -> 195.dp
        "cat_dance.lottie", "cat_dancing.lottie" -> 185.dp
        else -> 175.dp
    }
    val contentScale = if (assetName == "cat_error.lottie") 1.3f else 1.0f

    Box(
        modifier = modifier
            .size(targetSize)
            .scale(contentScale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (System.currentTimeMillis() - startTime >= 2000L) {
                    onDismiss()
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun MorningBriefLottieHeader(
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("cat_morning.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = !isScrolling,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            renderMode = RenderMode.HARDWARE
        )
    }
}

@Composable
fun EveningBriefHeader(
    modifier: Modifier = Modifier,
    messageId: String? = null,
    isScrolling: Boolean = false
) {
    EveningBriefLottieHeader(modifier = modifier, isScrolling = isScrolling)
}

@Composable
fun EveningBriefLottieHeader(
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("cat_evening.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = !isScrolling,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            renderMode = RenderMode.HARDWARE
        )
    }
}

@Composable
fun CatAngryLottieHeader(
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("cat_angry.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = !isScrolling,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEF4444).copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            renderMode = RenderMode.HARDWARE
        )
    }
}
