package com.example.routetracker.data

import java.time.Duration
import java.time.Instant
import kotlin.math.max

const val COMPLICATION_STALE_AFTER_SECONDS = 30L
private const val COMPLICATION_TIMELINE_HORIZON_SECONDS = 120L * 60L
private const val SECOND_STALE_AFTER_SECONDS = 60L
private const val THIRD_STALE_AFTER_SECONDS = 120L

private data class StaleStatusIndicator(
    val title: String,
    val contentDescriptionSuffix: String,
)

private val STALE_STATUS_LEVELS = listOf(
    COMPLICATION_STALE_AFTER_SECONDS to StaleStatusIndicator(
        title = "\u2022",
        contentDescriptionSuffix = "Showing data older than 30 seconds.",
    ),
    SECOND_STALE_AFTER_SECONDS to StaleStatusIndicator(
        title = "\u2022\u2022",
        contentDescriptionSuffix = "Showing data older than 1 minute.",
    ),
    THIRD_STALE_AFTER_SECONDS to StaleStatusIndicator(
        title = "\u2022\u2022\u2022",
        contentDescriptionSuffix = "Showing data older than 2 minutes.",
    ),
)

private val FALLBACK_STALE_STATUS = StaleStatusIndicator(
    title = "\u2022\u2022\u2022",
    contentDescriptionSuffix = "Showing stale fallback data.",
)

data class ComplicationDisplayState(
    val departureInstant: Instant?,
    val fallbackText: String,
    val title: String?,
    val contentDescription: String,
)

data class ComplicationDisplayWindow(
    val start: Instant,
    val end: Instant,
    val state: ComplicationDisplayState,
)

data class ComplicationTimelinePlan(
    val defaultState: ComplicationDisplayState,
    val futureWindows: List<ComplicationDisplayWindow>,
)

object ComplicationTimelinePlanner {
    fun create(
        snapshot: DepartureSnapshot,
        now: Instant,
    ): ComplicationTimelinePlan {
        val changePoints = buildSet {
            add(now)
            if (!snapshot.isStale) {
                STALE_STATUS_LEVELS
                    .map { (seconds, _) -> snapshot.fetchedAt.toInstant().plusSeconds(seconds) }
                    .filter { it.isAfter(now) }
                    .forEach(::add)
            }
            snapshot.departures
                .map { it.departureTime.toInstant() }
                .filter { it.isAfter(now) }
                .forEach(::add)
            add(timelineHorizon(snapshot, now))
        }.sorted()

        var previousState: ComplicationDisplayState? = null
        var defaultState: ComplicationDisplayState? = null
        val windows = mutableListOf<ComplicationDisplayWindow>()

        for (index in 0 until changePoints.lastIndex) {
            val start = changePoints[index]
            val end = changePoints[index + 1]
            if (!start.isBefore(end)) {
                continue
            }

            val state = stateAt(
                snapshot = snapshot,
                instant = start,
            )

            if (defaultState == null) {
                defaultState = state
                previousState = state
                continue
            }

            if (state == previousState) {
                continue
            }

            windows += ComplicationDisplayWindow(
                start = start,
                end = end,
                state = state,
            )
            previousState = state
        }

        return ComplicationTimelinePlan(
            defaultState = defaultState ?: stateAt(snapshot, now),
            futureWindows = windows,
        )
    }

    private fun stateAt(
        snapshot: DepartureSnapshot,
        instant: Instant,
    ): ComplicationDisplayState {
        val staleStatus = staleStatusAt(
            snapshot = snapshot,
            instant = instant,
        )
        val departure = snapshot.departures.firstOrNull { it.departureTime.toInstant().isAfter(instant) }

        if (departure == null) {
            return ComplicationDisplayState(
                departureInstant = null,
                fallbackText = "--",
                title = staleStatus?.title,
                contentDescription = buildString {
                    append("No upcoming direct departures from ")
                    append(snapshot.selection.origin.stationName)
                    append(" to ")
                    append(snapshot.selection.destination.stationName)
                    staleStatus?.let { status ->
                        append(". ")
                        append(status.contentDescriptionSuffix)
                    }
                },
            )
        }

        return ComplicationDisplayState(
            departureInstant = departure.departureTime.toInstant(),
            fallbackText = departure.countdownMinutesAt(instant).toString(),
            title = staleStatus?.title,
            contentDescription = buildString {
                append("Next direct departure from ")
                append(snapshot.selection.origin.stationName)
                append(" to ")
                append(snapshot.selection.destination.stationName)
                append(" on line ")
                append(departure.lineShortName)
                staleStatus?.let { status ->
                    append(" ")
                    append(status.contentDescriptionSuffix)
                }
            },
        )
    }

    private fun staleStatusAt(
        snapshot: DepartureSnapshot,
        instant: Instant,
    ): StaleStatusIndicator? {
        if (snapshot.isStale) {
            return FALLBACK_STALE_STATUS
        }

        val ageSeconds = Duration.between(snapshot.fetchedAt.toInstant(), instant).seconds.coerceAtLeast(0)
        return STALE_STATUS_LEVELS
            .lastOrNull { (seconds, _) -> ageSeconds >= seconds }
            ?.second
    }

    private fun timelineHorizon(
        snapshot: DepartureSnapshot,
        now: Instant,
    ): Instant {
        val lastDepartureInstant = snapshot.departures
            .lastOrNull()
            ?.departureTime
            ?.toInstant()
            ?.plusSeconds(1)
            ?: now.plusSeconds(COMPLICATION_STALE_AFTER_SECONDS)
        val longHorizon = now.plusSeconds(COMPLICATION_TIMELINE_HORIZON_SECONDS)
        return if (lastDepartureInstant.isAfter(longHorizon)) {
            lastDepartureInstant
        } else {
            longHorizon
        }
    }
}

fun RouteDeparture.countdownMinutesAt(referenceInstant: Instant): Int {
    return max(0, java.time.Duration.between(referenceInstant, departureTime.toInstant()).toMinutes().toInt())
}
