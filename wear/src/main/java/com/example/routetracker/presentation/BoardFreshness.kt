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
private const val WAVE_SEGMENT_COUNT = 72
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
    val glowColor: Color,
    val waveBaseAlpha: Float,
    val waveAmplitude: Float,
    val glowBaseAlpha: Float,
    val glowAmplitude: Float,
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
        val trackColor = colorScheme.outlineVariant.copy(alpha = 0.56f)
        val pausedColor = colorScheme.outline.copy(alpha = 0.78f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerInset = 8.dp.toPx()
            val glowInset = 14.dp.toPx()
            val outerStrokeWidth = 3.5.dp.toPx()
            val glowStrokeWidth = 10.dp.toPx()
            val outerStroke = Stroke(width = outerStrokeWidth, cap = StrokeCap.Round)
            val glowStroke = Stroke(width = glowStrokeWidth, cap = StrokeCap.Round)

            drawHaloArc(
                startAngle = RING_START_ANGLE,
                sweepAngle = RING_SWEEP_ANGLE,
                color = trackColor,
                inset = outerInset,
                stroke = outerStroke,
            )

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
            drawMovingWaveRing(
                style = style,
                wavePhase = wavePhase,
                pulseAlpha = pulseAlpha,
                liveProgress = if (uiModel.mode == FreshnessHaloMode.LIVE) displayedLiveProgress else null,
                outerInset = outerInset,
                glowInset = glowInset,
                outerStroke = outerStroke,
                glowStroke = glowStroke,
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

private fun DrawScope.drawMovingWaveRing(
    style: WaveRingStyle,
    wavePhase: Float,
    pulseAlpha: Float,
    liveProgress: Float?,
    outerInset: Float,
    glowInset: Float,
    outerStroke: Stroke,
    glowStroke: Stroke,
) {
    val segmentAngle = RING_SWEEP_ANGLE / WAVE_SEGMENT_COUNT
    val visibleSegmentAngle = segmentAngle * 0.86f
    val segmentOffset = (segmentAngle - visibleSegmentAngle) / 2f

    for (index in 0 until WAVE_SEGMENT_COUNT) {
        val fraction = (index + 0.5f) / WAVE_SEGMENT_COUNT.toFloat()
        val angleRadians = fraction * TWO_PI
        val wave = combinedWave(
            angleRadians = angleRadians,
            wavePhase = wavePhase,
        )

        val freshnessBlend = liveProgress?.let { progress ->
            liveFreshnessBlend(
                fraction = fraction,
                progress = progress,
            )
        } ?: 1f
        val presence = if (liveProgress != null) {
            lerpFloat(start = 0.24f, stop = 1f, fraction = freshnessBlend)
        } else {
            1f
        }
        val segmentColor = lerpColor(
            start = style.dimColor,
            stop = style.activeColor,
            fraction = freshnessBlend,
        )

        val outerAlpha =
            pulseAlpha * presence * (style.waveBaseAlpha + style.waveAmplitude * wave)
        val glowAlpha =
            pulseAlpha *
                lerpFloat(
                    start = style.glowBaseAlpha * 0.55f,
                    stop = style.glowBaseAlpha + style.glowAmplitude * wave,
                    fraction = presence,
                )

        val segmentStart = RING_START_ANGLE + segmentAngle * index + segmentOffset

        drawHaloArc(
            startAngle = segmentStart,
            sweepAngle = visibleSegmentAngle,
            color = style.glowColor.copy(alpha = glowAlpha),
            inset = glowInset,
            stroke = glowStroke,
        )
        drawHaloArc(
            startAngle = segmentStart,
            sweepAngle = visibleSegmentAngle,
            color = segmentColor.copy(alpha = outerAlpha),
            inset = outerInset,
            stroke = outerStroke,
        )
    }
}

private fun combinedWave(
    angleRadians: Float,
    wavePhase: Float,
): Float {
    val driftRadians = wavePhase * TWO_PI
    val slowWave = (sin(angleRadians * 2.4f - driftRadians) + 1f) * 0.5f
    val detailWave = (sin(angleRadians * 5.6f + driftRadians * 0.72f) + 1f) * 0.5f
    return (slowWave * 0.7f + detailWave * 0.3f).coerceIn(0f, 1f)
}

private fun liveFreshnessBlend(
    fraction: Float,
    progress: Float,
): Float {
    val transitionWidth = 0.06f
    val start = (progress - transitionWidth).coerceAtLeast(0f)
    val end = (progress + transitionWidth).coerceAtMost(1f)
    return 1f - smoothStep(
        edge0 = start,
        edge1 = end,
        value = fraction,
    )
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
            glowColor = colors.primary.copy(alpha = 1f),
            waveBaseAlpha = 0.24f,
            waveAmplitude = 0.34f,
            glowBaseAlpha = 0.03f,
            glowAmplitude = 0.16f,
            driftDurationMillis = 16_000,
        )
        FreshnessHaloMode.REFRESHING -> WaveRingStyle(
            activeColor = lerpColor(colors.secondary, colors.primary, 0.28f),
            dimColor = lerpColor(colors.outlineVariant, colors.secondary, 0.32f),
            glowColor = colors.secondary.copy(alpha = 1f),
            waveBaseAlpha = 0.36f,
            waveAmplitude = 0.42f,
            glowBaseAlpha = 0.08f,
            glowAmplitude = 0.22f,
            driftDurationMillis = 9_000,
        )
        FreshnessHaloMode.CACHED -> WaveRingStyle(
            activeColor = colors.tertiary,
            dimColor = lerpColor(colors.outlineVariant, colors.tertiary, 0.24f),
            glowColor = colors.tertiary.copy(alpha = 1f),
            waveBaseAlpha = 0.2f,
            waveAmplitude = 0.26f,
            glowBaseAlpha = 0.03f,
            glowAmplitude = 0.12f,
            driftDurationMillis = 18_000,
        )
        FreshnessHaloMode.RATE_LIMITED -> WaveRingStyle(
            activeColor = colors.tertiary,
            dimColor = lerpColor(colors.outlineVariant, colors.tertiary, 0.26f),
            glowColor = colors.tertiary.copy(alpha = 1f),
            waveBaseAlpha = 0.28f,
            waveAmplitude = 0.3f,
            glowBaseAlpha = 0.06f,
            glowAmplitude = 0.16f,
            driftDurationMillis = 14_000,
        )
        FreshnessHaloMode.ERROR -> WaveRingStyle(
            activeColor = colors.error,
            dimColor = lerpColor(colors.outlineVariant, colors.error, 0.28f),
            glowColor = colors.error.copy(alpha = 1f),
            waveBaseAlpha = 0.28f,
            waveAmplitude = 0.3f,
            glowBaseAlpha = 0.06f,
            glowAmplitude = 0.16f,
            driftDurationMillis = 12_000,
        )
    }
}

private fun smoothStep(
    edge0: Float,
    edge1: Float,
    value: Float,
): Float {
    if (edge0 == edge1) {
        return if (value < edge0) 0f else 1f
    }
    val clamped = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return clamped * clamped * (3f - 2f * clamped)
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
