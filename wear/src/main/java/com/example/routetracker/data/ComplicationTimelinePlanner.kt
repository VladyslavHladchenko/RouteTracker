package com.example.routetracker.data

import java.time.Duration
import java.time.Instant
import kotlin.math.max

const val COMPLICATION_STALE_AFTER_SECONDS = 30L
private const val COMPLICATION_TIMELINE_HORIZON_SECONDS = 120L * 60L
private const val SECOND_STALE_AFTER_SECONDS = 60L
private const val THIRD_STALE_AFTER_SECONDS = 120L
private const val LONG_TEXT_DEPARTURE_COUNT = 3

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
    val shortTextFallback: String,
    val shortTitle: String?,
    val longText: String,
    val longTitle: String?,
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
        val upcomingDepartures = snapshot.departures
            .filter { it.departureTime.toInstant().isAfter(instant) }
        val departure = upcomingDepartures.firstOrNull()
        val futureDepartures = upcomingDepartures.take(LONG_TEXT_DEPARTURE_COUNT)
        val longTitle = buildLongTitle(snapshot, staleStatus)

        if (departure == null) {
            return ComplicationDisplayState(
                departureInstant = null,
                shortTextFallback = "--",
                shortTitle = staleStatus?.title,
                longText = "No departures",
                longTitle = longTitle,
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
            shortTextFallback = departure.countdownMinutesAt(instant).toString(),
            shortTitle = staleStatus?.title,
            longText = buildLongText(snapshot, futureDepartures),
            longTitle = longTitle,
            contentDescription = buildString {
                append("Upcoming direct departures from ")
                append(snapshot.selection.origin.stationName)
                append(" to ")
                append(snapshot.selection.destination.stationName)
                snapshot.selection.line?.shortName?.let { lineShortName ->
                    append(" on line ")
                    append(lineShortName)
                }
                append(": ")
                append(
                    futureDepartures.joinToString(", ") { futureDeparture ->
                        if (snapshot.selection.usesFixedLine()) {
                            formatDisplayTime(futureDeparture.departureTime, showSeconds = false)
                        } else {
                            "line ${futureDeparture.lineShortName} at " +
                                formatDisplayTime(futureDeparture.departureTime, showSeconds = false)
                        }
                    }
                )
                staleStatus?.let { status ->
                    append(" ")
                    append(status.contentDescriptionSuffix)
                }
            },
        )
    }

    private fun buildLongText(
        snapshot: DepartureSnapshot,
        departures: List<RouteDeparture>,
    ): String {
        if (departures.isEmpty()) {
            return "No departures"
        }

        val includeLine = !snapshot.selection.usesFixedLine()
        return departures.joinToString(" / ") { departure ->
            val departureTime = formatDisplayTime(departure.departureTime, showSeconds = false)
            if (includeLine) {
                "${departure.lineShortName} $departureTime"
            } else {
                departureTime
            }
        }
    }

    private fun buildLongTitle(
        snapshot: DepartureSnapshot,
        staleStatus: StaleStatusIndicator?,
    ): String? {
        val baseTitle = if (snapshot.selection.usesFixedLine()) {
            snapshot.selection.line?.displayLabel
        } else {
            snapshot.selection.destination.stationName
        }

        return listOfNotNull(
            baseTitle?.takeIf { it.isNotBlank() },
            staleStatus?.title,
        ).joinToString(" ").ifBlank { null }
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
