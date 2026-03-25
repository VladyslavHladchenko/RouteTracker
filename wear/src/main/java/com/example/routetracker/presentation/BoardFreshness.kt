package com.example.routetracker.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import com.example.routetracker.data.DepartureRefreshFailureKind
import com.example.routetracker.data.DepartureSnapshot
import java.time.Duration
import java.time.ZonedDateTime

internal const val BOARD_REFRESH_INTERVAL_MILLIS = 30_000L

internal enum class FreshnessHaloMode {
    NONE,
    LIVE,
    REFRESHING,
    CACHED,
    PAUSED,
    RATE_LIMITED,
    ERROR,
}

internal data class FreshnessHaloUiModel(
    val mode: FreshnessHaloMode,
    val liveProgress: Float? = null,
    val contentDescription: String,
)

internal fun freshnessBaseMode(
    snapshot: DepartureSnapshot?,
    autoUpdatesEnabled: Boolean,
): FreshnessHaloMode {
    if (!autoUpdatesEnabled) {
        return FreshnessHaloMode.PAUSED
    }
    if (snapshot == null) {
        return FreshnessHaloMode.NONE
    }
    return when (snapshot.refreshFailureKind) {
        DepartureRefreshFailureKind.RATE_LIMITED -> FreshnessHaloMode.RATE_LIMITED
        DepartureRefreshFailureKind.OTHER -> FreshnessHaloMode.ERROR
        null -> if (snapshot.isStale) FreshnessHaloMode.CACHED else FreshnessHaloMode.LIVE
    }
}

internal fun computeLiveFreshnessProgress(
    snapshot: DepartureSnapshot,
    currentSystemTime: ZonedDateTime,
): Float {
    val ageMillis = Duration.between(snapshot.fetchedAt, currentSystemTime)
        .toMillis()
        .coerceAtLeast(0L)
    return (1f - ageMillis.toFloat() / BOARD_REFRESH_INTERVAL_MILLIS.toFloat()).coerceIn(0f, 1f)
}

internal fun buildFreshnessHaloUiModel(
    snapshot: DepartureSnapshot?,
    autoUpdatesEnabled: Boolean,
    isRefreshing: Boolean,
    currentSystemTime: ZonedDateTime,
): FreshnessHaloUiModel {
    val baseMode = freshnessBaseMode(
        snapshot = snapshot,
        autoUpdatesEnabled = autoUpdatesEnabled,
    )
    val resolvedMode = if (isRefreshing) {
        FreshnessHaloMode.REFRESHING
    } else {
        baseMode
    }
    val liveProgress = if (baseMode == FreshnessHaloMode.LIVE && snapshot != null) {
        computeLiveFreshnessProgress(
            snapshot = snapshot,
            currentSystemTime = currentSystemTime,
        )
    } else {
        null
    }

    return FreshnessHaloUiModel(
        mode = resolvedMode,
        liveProgress = liveProgress,
        contentDescription = when (resolvedMode) {
            FreshnessHaloMode.NONE -> "Loading live departures."
            FreshnessHaloMode.LIVE -> "Live departures are current."
            FreshnessHaloMode.REFRESHING -> {
                if (snapshot == null) {
                    "Loading live departures."
                } else {
                    "Refreshing live departures."
                }
            }
            FreshnessHaloMode.CACHED -> "Showing cached departures."
            FreshnessHaloMode.PAUSED -> "Automatic updates are paused."
            FreshnessHaloMode.RATE_LIMITED -> "Live departures are temporarily rate limited."
            FreshnessHaloMode.ERROR -> "Unable to refresh live departures."
        },
    )
}

internal fun snapshotStatusText(
    snapshot: DepartureSnapshot?,
    autoUpdatesEnabled: Boolean,
    isRefreshing: Boolean,
    updatedLabel: String?,
): String {
    if (snapshot == null) {
        return "Loading"
    }

    val label = when (freshnessBaseMode(snapshot = snapshot, autoUpdatesEnabled = autoUpdatesEnabled)) {
        FreshnessHaloMode.NONE -> "Loading"
        FreshnessHaloMode.LIVE -> "Live"
        FreshnessHaloMode.REFRESHING -> "Live"
        FreshnessHaloMode.CACHED -> "Cached"
        FreshnessHaloMode.PAUSED -> "Paused"
        FreshnessHaloMode.RATE_LIMITED -> "Rate limited"
        FreshnessHaloMode.ERROR -> "Error"
    }

    if (isRefreshing && snapshot.departures.isEmpty() && snapshot.refreshFailureKind == null) {
        return "Loading"
    }

    if (
        freshnessBaseMode(snapshot = snapshot, autoUpdatesEnabled = autoUpdatesEnabled) in
            setOf(
                FreshnessHaloMode.PAUSED,
                FreshnessHaloMode.RATE_LIMITED,
                FreshnessHaloMode.ERROR,
            )
    ) {
        return label
    }

    return if (updatedLabel != null) {
        "$label \u00B7 $updatedLabel"
    } else {
        label
    }
}

@Composable
internal fun FreshnessHalo(
    uiModel: FreshnessHaloUiModel,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics {
                contentDescription = uiModel.contentDescription
            },
    ) {
        if (uiModel.mode == FreshnessHaloMode.NONE) {
            return@Box
        }

        val refreshingPhase = rememberRepeatingPhase(
            enabled = animate && uiModel.mode == FreshnessHaloMode.REFRESHING,
            durationMillis = 3_500,
        )
        val rateLimitedAlpha = rememberBreathingAlpha(
            enabled = animate && uiModel.mode == FreshnessHaloMode.RATE_LIMITED,
            durationMillis = 2_800,
        )
        val errorAlpha = rememberBreathingAlpha(
            enabled = animate && uiModel.mode == FreshnessHaloMode.ERROR,
            durationMillis = 2_200,
        )

        val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)
        val accentColor = when (uiModel.mode) {
            FreshnessHaloMode.LIVE -> MaterialTheme.colorScheme.primary
            FreshnessHaloMode.REFRESHING -> MaterialTheme.colorScheme.secondary
            FreshnessHaloMode.CACHED -> MaterialTheme.colorScheme.tertiary
            FreshnessHaloMode.PAUSED -> MaterialTheme.colorScheme.outline
            FreshnessHaloMode.RATE_LIMITED -> MaterialTheme.colorScheme.tertiary
            FreshnessHaloMode.ERROR -> MaterialTheme.colorScheme.error
            FreshnessHaloMode.NONE -> Color.Transparent
        }
        val glowColor = accentColor.copy(
            alpha = when (uiModel.mode) {
                FreshnessHaloMode.LIVE -> 0.22f
                FreshnessHaloMode.REFRESHING -> 0.30f
                FreshnessHaloMode.CACHED -> 0.14f
                FreshnessHaloMode.PAUSED -> 0f
                FreshnessHaloMode.RATE_LIMITED -> 0.24f * rateLimitedAlpha
                FreshnessHaloMode.ERROR -> 0.28f * errorAlpha
                FreshnessHaloMode.NONE -> 0f
            },
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerInset = 8.dp.toPx()
            val glowInset = 14.dp.toPx()
            val outerStrokeWidth = 3.5.dp.toPx()
            val glowStrokeWidth = 10.dp.toPx()
            val ringStartAngle = -110f
            val ringSweepAngle = 320f
            val outerStroke = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)
            val pausedStroke = Stroke(
                width = outerStrokeWidth,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(10.dp.toPx(), 7.dp.toPx()),
                ),
            )

            drawHaloArc(
                startAngle = ringStartAngle,
                sweepAngle = ringSweepAngle,
                color = trackColor,
                inset = outerInset,
                stroke = outerStroke,
            )

            when (uiModel.mode) {
                FreshnessHaloMode.LIVE -> {
                    val progress = uiModel.liveProgress ?: 1f
                    val accentSweep = ringSweepAngle * progress
                    if (accentSweep > 0f) {
                        drawHaloArc(
                            startAngle = ringStartAngle,
                            sweepAngle = accentSweep,
                            color = glowColor,
                            inset = glowInset,
                            stroke = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round),
                        )
                        drawHaloArc(
                            startAngle = ringStartAngle,
                            sweepAngle = accentSweep,
                            color = accentColor,
                            inset = outerInset,
                            stroke = outerStroke,
                        )
                    }
                }
                FreshnessHaloMode.REFRESHING -> {
                    val rotation = refreshingPhase * 360f
                    drawHaloArc(
                        startAngle = ringStartAngle + rotation,
                        sweepAngle = 164f,
                        color = glowColor,
                        inset = glowInset,
                        stroke = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round),
                    )
                    drawHaloArc(
                        startAngle = ringStartAngle + rotation + 18f,
                        sweepAngle = 110f,
                        color = accentColor,
                        inset = outerInset,
                        stroke = outerStroke,
                    )
                }
                FreshnessHaloMode.CACHED -> {
                    drawHaloArc(
                        startAngle = ringStartAngle,
                        sweepAngle = 210f,
                        color = glowColor,
                        inset = glowInset,
                        stroke = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round),
                    )
                    drawHaloArc(
                        startAngle = ringStartAngle,
                        sweepAngle = 210f,
                        color = accentColor.copy(alpha = 0.92f),
                        inset = outerInset,
                        stroke = outerStroke,
                    )
                }
                FreshnessHaloMode.PAUSED -> {
                    drawHaloArc(
                        startAngle = ringStartAngle,
                        sweepAngle = ringSweepAngle,
                        color = accentColor.copy(alpha = 0.68f),
                        inset = outerInset,
                        stroke = pausedStroke,
                    )
                }
                FreshnessHaloMode.RATE_LIMITED -> {
                    drawHaloArc(
                        startAngle = ringStartAngle,
                        sweepAngle = 300f,
                        color = glowColor,
                        inset = glowInset,
                        stroke = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round),
                    )
                    drawHaloArc(
                        startAngle = ringStartAngle,
                        sweepAngle = 300f,
                        color = accentColor.copy(alpha = rateLimitedAlpha),
                        inset = outerInset,
                        stroke = outerStroke,
                    )
                }
                FreshnessHaloMode.ERROR -> {
                    drawHaloArc(
                        startAngle = ringStartAngle,
                        sweepAngle = 300f,
                        color = glowColor,
                        inset = glowInset,
                        stroke = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round),
                    )
                    drawHaloArc(
                        startAngle = ringStartAngle,
                        sweepAngle = 300f,
                        color = accentColor.copy(alpha = errorAlpha),
                        inset = outerInset,
                        stroke = outerStroke,
                    )
                }
                FreshnessHaloMode.NONE -> Unit
            }
        }
    }
}

@Composable
private fun rememberRepeatingPhase(
    enabled: Boolean,
    durationMillis: Int,
): Float {
    if (!enabled) {
        return 0f
    }
    val transition = rememberInfiniteTransition(label = "freshness_halo_phase")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "freshness_halo_phase_value",
    ).value
}

@Composable
private fun rememberBreathingAlpha(
    enabled: Boolean,
    durationMillis: Int,
): Float {
    if (!enabled) {
        return 1f
    }
    val transition = rememberInfiniteTransition(label = "freshness_halo_breath")
    return transition.animateFloat(
        initialValue = 0.66f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "freshness_halo_breath_value",
    ).value
}

private fun DrawScope.drawHaloArc(
    startAngle: Float,
    sweepAngle: Float,
    color: Color,
    inset: Float,
    stroke: Stroke,
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = Size(
            width = size.width - (inset * 2),
            height = size.height - (inset * 2),
        ),
        style = stroke,
    )
}
