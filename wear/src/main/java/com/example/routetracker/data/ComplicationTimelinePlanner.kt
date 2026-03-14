package com.example.routetracker.data

import java.time.Instant
import kotlin.math.max

const val COMPLICATION_STALE_AFTER_SECONDS = 30L
private const val COMPLICATION_TIMELINE_HORIZON_SECONDS = 120L * 60L
private const val STALE_STATUS_MARKER = "\u2022"

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
        val staleAt = snapshot.fetchedAt.toInstant().plusSeconds(COMPLICATION_STALE_AFTER_SECONDS)
        val changePoints = buildSet {
            add(now)
            if (staleAt.isAfter(now)) {
                add(staleAt)
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
                staleAt = staleAt,
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
            defaultState = defaultState ?: stateAt(snapshot, now, staleAt),
            futureWindows = windows,
        )
    }

    private fun stateAt(
        snapshot: DepartureSnapshot,
        instant: Instant,
        staleAt: Instant,
    ): ComplicationDisplayState {
        val isStale = snapshot.isStale || !instant.isBefore(staleAt)
        val departure = snapshot.departures.firstOrNull { it.departureTime.toInstant().isAfter(instant) }
        val marker = if (isStale) STALE_STATUS_MARKER else null

        if (departure == null) {
            return ComplicationDisplayState(
                departureInstant = null,
                fallbackText = "--",
                title = marker,
                contentDescription = buildString {
                    append("No upcoming direct line 7 departures ")
                    append(snapshot.direction.buttonLabel)
                    if (isStale) {
                        append(". Showing data older than 30 seconds.")
                    }
                },
            )
        }

        return ComplicationDisplayState(
            departureInstant = departure.departureTime.toInstant(),
            fallbackText = departure.countdownMinutesAt(instant).toString(),
            title = marker,
            contentDescription = buildString {
                append("Next direct line 7 departure ")
                append(snapshot.direction.buttonLabel)
                append(".")
                if (isStale) {
                    append(" Showing data older than 30 seconds.")
                }
            },
        )
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
