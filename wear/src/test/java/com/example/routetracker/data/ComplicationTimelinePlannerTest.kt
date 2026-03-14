package com.example.routetracker.data

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComplicationTimelinePlannerTest {
    private val pragueZone: ZoneId = ZoneId.of("Europe/Prague")

    @Test
    fun `marks complication stale after 30 seconds`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val snapshot = snapshot(
            fetchedAt = now,
            departures = listOf(departure(tripId = "trip-1", departureTime = now.plusMinutes(10))),
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        assertNull(plan.defaultState.title)
        val staleWindow = plan.futureWindows.first { it.start == now.toInstant().plusSeconds(COMPLICATION_STALE_AFTER_SECONDS) }
        assertEquals("\u2022", staleWindow.state.title)
    }

    @Test
    fun `switches complication to second departure when first departs`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val firstDeparture = now.plusMinutes(5)
        val secondDeparture = now.plusMinutes(12)
        val snapshot = snapshot(
            fetchedAt = now,
            departures = listOf(
                departure(tripId = "trip-1", departureTime = firstDeparture),
                departure(tripId = "trip-2", departureTime = secondDeparture),
            ),
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        val handoffWindow = plan.futureWindows.first { it.start == firstDeparture.toInstant() }
        assertEquals(secondDeparture.toInstant(), handoffWindow.state.departureInstant)
        assertEquals("7", handoffWindow.state.fallbackText)
    }

    @Test
    fun `shows no data after last known departure`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val departureTime = now.plusMinutes(3)
        val snapshot = snapshot(
            fetchedAt = now,
            departures = listOf(departure(tripId = "trip-1", departureTime = departureTime)),
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        val emptyWindow = plan.futureWindows.first { it.start == departureTime.toInstant() }
        assertNull(emptyWindow.state.departureInstant)
        assertEquals("--", emptyWindow.state.fallbackText)
    }

    @Test
    fun `treats already stale snapshots as stale immediately`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val snapshot = snapshot(
            fetchedAt = now.minusMinutes(1),
            departures = listOf(departure(tripId = "trip-1", departureTime = now.plusMinutes(8))),
            isStale = true,
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        assertEquals("\u2022", plan.defaultState.title)
    }

    private fun snapshot(
        fetchedAt: ZonedDateTime,
        departures: List<RouteDeparture>,
        isStale: Boolean = false,
    ): DepartureSnapshot {
        return DepartureSnapshot(
            direction = RouteDirection.TO_PALMOVKA,
            departures = departures,
            fetchedAt = fetchedAt,
            isStale = isStale,
        )
    }

    private fun departure(
        tripId: String,
        departureTime: ZonedDateTime,
    ): RouteDeparture {
        return RouteDeparture(
            tripId = tripId,
            departureTime = departureTime,
            countdownMinutes = 0,
            delayMinutes = 0,
            departureBoardDetails = DepartureBoardDetails(
                departureTime = BoardStopTime(
                    scheduledTime = departureTime,
                    predictedTime = departureTime,
                ),
                originArrivalTime = null,
                delaySeconds = 0,
            ),
            vehiclePositionDetails = null,
            destinationArrivalTime = departureTime.plusMinutes(12),
        )
    }

    private fun pragueTime(
        hour: Int,
        minute: Int,
        second: Int,
    ): ZonedDateTime {
        return ZonedDateTime.of(2026, 3, 14, hour, minute, second, 0, pragueZone)
    }
}
