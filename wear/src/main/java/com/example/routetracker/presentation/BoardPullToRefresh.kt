package com.example.routetracker.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator as PlatformCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BoardPullToRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    var pullRefreshActive by remember { mutableStateOf(false) }
    var observedRefreshStart by remember { mutableStateOf(false) }
    var thresholdHapticPlayed by remember { mutableStateOf(false) }
    val thresholdReached = !isRefreshing && state.distanceFraction >= 1f

    LaunchedEffect(isRefreshing, pullRefreshActive, observedRefreshStart) {
        if (pullRefreshActive && isRefreshing) {
            observedRefreshStart = true
        } else if (pullRefreshActive && observedRefreshStart && !isRefreshing) {
            pullRefreshActive = false
            observedRefreshStart = false
        }
    }

    LaunchedEffect(thresholdReached) {
        if (thresholdReached && !thresholdHapticPlayed) {
            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            thresholdHapticPlayed = true
        } else if (!thresholdReached) {
            thresholdHapticPlayed = false
        }
    }

    PullToRefreshBox(
        isRefreshing = pullRefreshActive,
        onRefresh = {
            if (!isRefreshing && !pullRefreshActive) {
                pullRefreshActive = true
                onRefresh()
            }
        },
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTestTags.BOARD_PULL_REFRESH_CONTAINER),
        indicator = {
            BoardPullRefreshIndicator(
                progress = if (pullRefreshActive) 1f else state.distanceFraction,
                isRefreshing = pullRefreshActive,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .testTag(UiTestTags.BOARD_PULL_REFRESH_INDICATOR),
            )
        },
        content = {
            content()
        },
    )
}

@Composable
internal fun BoardPullRefreshIndicator(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (!isRefreshing && clampedProgress == 0f) {
        return
    }

    val pullTravelPx = with(LocalDensity.current) { 28.dp.toPx() }
    val scale = if (isRefreshing) 1f else 0.72f + (clampedProgress * 0.28f)
    val alpha = if (isRefreshing) 1f else 0.25f + (clampedProgress * 0.75f)
    val locked = clampedProgress >= 1f
    val containerColor = if (isRefreshing || locked) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = if (isRefreshing || locked) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .graphicsLayer {
                translationY = if (isRefreshing) pullTravelPx else pullTravelPx * clampedProgress
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(
                color = containerColor,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isRefreshing) {
            PlatformCircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = contentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                painter = painterResource(id = android.R.drawable.stat_notify_sync),
                contentDescription = "Pull to refresh",
                tint = contentColor,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        rotationZ = clampedProgress * 180f
                    },
            )
        }
    }
}
