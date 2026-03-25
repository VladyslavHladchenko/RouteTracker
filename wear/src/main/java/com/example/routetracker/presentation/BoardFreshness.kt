package com.example.routetracker.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.PI
import kotlin.math.sin

internal const val BOARD_REFRESH_INTERVAL_MILLIS = 30_000L

private const val RING_START_ANGLE = -110f
private const val RING_SWEEP_ANGLE = 320f
private const val WAVE_BRUSH_STOP_COUNT = 32
private const val TWO_PI = (PI * 2.0).toFloat()

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

private data class WaveRingStyle(
    val activeColor: Color,
    val dimColor: Color,
    val baseAlpha: Float,
    val waveAmplitude: Float,
    val driftDurationMillis: Int,
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

    return when (freshnessBaseMode(snapshot = snapshot, autoUpdatesEnabled = autoUpdatesEnabled)) {
        FreshnessHaloMode.PAUSED,
        FreshnessHaloMode.RATE_LIMITED,
        FreshnessHaloMode.ERROR,
        -> label
        else -> if (updatedLabel != null) "$label \u00B7 $updatedLabel" else label
    }
}

@Composable
internal fun FreshnessHalo(
    uiModel: FreshnessHaloUiModel,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
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

        val waveStyle = waveRingStyleFor(
            mode = uiModel.mode,
            colors = colorScheme,
        )
        val wavePhase = rememberRepeatingPhase(
            enabled = animate && uiModel.mode != FreshnessHaloMode.PAUSED,
            durationMillis = waveStyle?.driftDurationMillis ?: 16_000,
        )
        val pulseAlpha = when (uiModel.mode) {
            FreshnessHaloMode.REFRESHING -> rememberBreathingAlpha(
                enabled = animate,
                durationMillis = 4_200,
                minAlpha = 0.88f,
                maxAlpha = 1f,
            )
            FreshnessHaloMode.RATE_LIMITED -> rememberBreathingAlpha(
                enabled = animate,
                durationMillis = 3_200,
                minAlpha = 0.84f,
                maxAlpha = 1f,
            )
            FreshnessHaloMode.ERROR -> rememberBreathingAlpha(
                enabled = animate,
                durationMillis = 2_600,
                minAlpha = 0.8f,
                maxAlpha = 1f,
            )
            else -> 1f
        }
        val displayedLiveProgress = if (uiModel.mode == FreshnessHaloMode.LIVE) {
            if (animate) {
                animateFloatAsState(
                    targetValue = uiModel.liveProgress ?: 1f,
                    animationSpec = tween(durationMillis = 1_150, easing = LinearEasing),
                    label = "freshness_live_progress",
                ).value
            } else {
                uiModel.liveProgress ?: 1f
            }
        } else {
            1f
        }
        val pausedColor = colorScheme.outline.copy(alpha = 0.78f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerInset = 8.dp.toPx()
            val outerStrokeWidth = 4.dp.toPx()
            val outerStroke = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)

            if (uiModel.mode == FreshnessHaloMode.PAUSED) {
                drawHaloArc(
                    startAngle = RING_START_ANGLE,
                    sweepAngle = RING_SWEEP_ANGLE,
                    color = pausedColor,
                    inset = outerInset,
                    stroke = outerStroke,
                )
                return@Canvas
            }

            val style = checkNotNull(waveStyle)
            drawContinuousWaveRing(
                style = style,
                wavePhase = wavePhase,
                pulseAlpha = pulseAlpha,
                liveProgress = if (uiModel.mode == FreshnessHaloMode.LIVE) displayedLiveProgress else null,
                outerInset = outerInset,
                outerStroke = outerStroke,
            )
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
    minAlpha: Float,
    maxAlpha: Float,
): Float {
    if (!enabled) {
        return maxAlpha
    }
    val transition = rememberInfiniteTransition(label = "freshness_halo_breath")
    return transition.animateFloat(
        initialValue = minAlpha,
        targetValue = maxAlpha,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "freshness_halo_breath_value",
    ).value
}

private fun DrawScope.drawContinuousWaveRing(
    style: WaveRingStyle,
    wavePhase: Float,
    pulseAlpha: Float,
    liveProgress: Float?,
    outerInset: Float,
    outerStroke: Stroke,
) {
    val fullBrush = waveRingBrush(
        lowColor = style.dimColor.copy(alpha = pulseAlpha * style.baseAlpha),
        highColor = style.activeColor.copy(alpha = pulseAlpha * (style.baseAlpha + style.waveAmplitude)),
        wavePhase = wavePhase,
    )

    if (liveProgress == null) {
        drawHaloArc(
            startAngle = RING_START_ANGLE,
            sweepAngle = RING_SWEEP_ANGLE,
            brush = fullBrush,
            inset = outerInset,
            stroke = outerStroke,
        )
        return
    }

    val dimBrush = waveRingBrush(
        lowColor = style.dimColor.copy(alpha = pulseAlpha * 0.18f),
        highColor = style.dimColor.copy(alpha = pulseAlpha * 0.42f),
        wavePhase = wavePhase,
    )
    drawHaloArc(
        startAngle = RING_START_ANGLE,
        sweepAngle = RING_SWEEP_ANGLE,
        brush = dimBrush,
        inset = outerInset,
        stroke = outerStroke,
    )

    val activeSweepAngle = (RING_SWEEP_ANGLE * liveProgress.coerceIn(0f, 1f))
    if (activeSweepAngle <= 0f) {
        return
    }

    drawHaloArc(
        startAngle = RING_START_ANGLE,
        sweepAngle = activeSweepAngle,
        brush = fullBrush,
        inset = outerInset,
        stroke = outerStroke,
    )
}

private fun waveRingBrush(
    lowColor: Color,
    highColor: Color,
    wavePhase: Float,
): Brush {
    val colorStops = Array(WAVE_BRUSH_STOP_COUNT + 1) { index ->
        val fraction = index / WAVE_BRUSH_STOP_COUNT.toFloat()
        val wave = combinedWave(
            angleRadians = fraction * TWO_PI,
            wavePhase = wavePhase,
        )
        fraction to lerpColor(
            start = lowColor,
            stop = highColor,
            fraction = wave,
        )
    }
    return Brush.sweepGradient(colorStops = colorStops)
}

private fun combinedWave(
    angleRadians: Float,
    wavePhase: Float,
): Float {
    val driftRadians = wavePhase * TWO_PI
    val broadWave = (sin(angleRadians * 2f - driftRadians) + 1f) * 0.5f
    val detailWave = (sin(angleRadians * 5f + driftRadians * 0.6f) + 1f) * 0.5f
    return (broadWave * 0.78f + detailWave * 0.22f).coerceIn(0f, 1f)
}

private fun waveRingStyleFor(
    mode: FreshnessHaloMode,
    colors: androidx.wear.compose.material3.ColorScheme,
): WaveRingStyle? {
    return when (mode) {
        FreshnessHaloMode.NONE,
        FreshnessHaloMode.PAUSED,
        -> null
        FreshnessHaloMode.LIVE -> WaveRingStyle(
            activeColor = colors.primary,
            dimColor = lerpColor(colors.outlineVariant, colors.primary, 0.28f),
            baseAlpha = 0.58f,
            waveAmplitude = 0.18f,
            driftDurationMillis = 16_000,
        )
        FreshnessHaloMode.REFRESHING -> WaveRingStyle(
            activeColor = lerpColor(colors.secondary, colors.primary, 0.28f),
            dimColor = lerpColor(colors.outlineVariant, colors.secondary, 0.32f),
            baseAlpha = 0.68f,
            waveAmplitude = 0.2f,
            driftDurationMillis = 9_000,
        )
        FreshnessHaloMode.CACHED -> WaveRingStyle(
            activeColor = colors.tertiary,
            dimColor = lerpColor(colors.outlineVariant, colors.tertiary, 0.24f),
            baseAlpha = 0.54f,
            waveAmplitude = 0.16f,
            driftDurationMillis = 18_000,
        )
        FreshnessHaloMode.RATE_LIMITED -> WaveRingStyle(
            activeColor = colors.tertiary,
            dimColor = lerpColor(colors.outlineVariant, colors.tertiary, 0.26f),
            baseAlpha = 0.62f,
            waveAmplitude = 0.18f,
            driftDurationMillis = 14_000,
        )
        FreshnessHaloMode.ERROR -> WaveRingStyle(
            activeColor = colors.error,
            dimColor = lerpColor(colors.outlineVariant, colors.error, 0.28f),
            baseAlpha = 0.62f,
            waveAmplitude = 0.18f,
            driftDurationMillis = 12_000,
        )
    }
}

private fun lerpColor(
    start: Color,
    stop: Color,
    fraction: Float,
): Color {
    return Color(
        red = lerpFloat(start.red, stop.red, fraction),
        green = lerpFloat(start.green, stop.green, fraction),
        blue = lerpFloat(start.blue, stop.blue, fraction),
        alpha = lerpFloat(start.alpha, stop.alpha, fraction),
    )
}

private fun lerpFloat(
    start: Float,
    stop: Float,
    fraction: Float,
): Float {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    return start + (stop - start) * clampedFraction
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

private fun DrawScope.drawHaloArc(
    startAngle: Float,
    sweepAngle: Float,
    brush: Brush,
    inset: Float,
    stroke: Stroke,
) {
    drawArc(
        brush = brush,
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
