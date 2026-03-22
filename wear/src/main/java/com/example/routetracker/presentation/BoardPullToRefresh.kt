package com.example.routetracker.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshDefaults
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.pullRefreshIndicatorTransform
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.CircularProgressIndicator as PlatformCircularProgressIndicator
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun PullToRefreshContainer(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    containerTag: String,
    indicatorTag: String,
    content: @Composable () -> Unit,
) {
    val refreshThreshold = PullRefreshDefaults.RefreshThreshold / 2
    val refreshingOffset = PullRefreshDefaults.RefreshingOffset / 2
    var pullRefreshActive by remember { mutableStateOf(false) }
    var observedRefreshStart by remember { mutableStateOf(false) }
    val state = rememberPullRefreshState(
        refreshing = pullRefreshActive,
        onRefresh = {
            if (!pullRefreshActive) {
                pullRefreshActive = true
                observedRefreshStart = false
                onRefresh()
            }
        },
        refreshThreshold = refreshThreshold,
        refreshingOffset = refreshingOffset,
    )
    val haptic = LocalHapticFeedback.current
    var thresholdHapticPlayed by remember { mutableStateOf(false) }
    val indicatorProgress = if (pullRefreshActive) 1f else state.progress
    val showIndicator = pullRefreshActive || state.progress > 0f
    val thresholdReached = !pullRefreshActive && indicatorProgress >= 1f

    LaunchedEffect(isRefreshing, pullRefreshActive, observedRefreshStart) {
        if (!pullRefreshActive) {
            return@LaunchedEffect
        }

        if (isRefreshing) {
            observedRefreshStart = true
        } else if (observedRefreshStart) {
            pullRefreshActive = false
            observedRefreshStart = false
            thresholdHapticPlayed = false
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(containerTag)
            .pullRefresh(state),
    ) {
        content()
        if (showIndicator) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .pullRefreshIndicatorTransform(
                        state = state,
                        scale = false,
                    )
                    .padding(top = 18.dp)
                    .testTag(indicatorTag),
            ) {
                PullRefreshIndicator(
                    progress = indicatorProgress,
                    isRefreshing = pullRefreshActive,
                )
            }
        }
    }
}

@Composable
internal fun PullRefreshIndicator(
    progress: Float,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    if (!isRefreshing && clampedProgress == 0f) {
        return
    }

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
