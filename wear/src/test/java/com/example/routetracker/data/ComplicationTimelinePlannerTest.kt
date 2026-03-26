package com.example.routetracker.data

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplicationTimelinePlannerTest {
    private val pragueZone: ZoneId = ZoneId.of("Europe/Prague")

    @Test
    fun `marks complication stale with one dot after 30 seconds`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val snapshot = snapshot(
            fetchedAt = now,
            departures = listOf(departure(tripId = "trip-1", departureTime = now.plusMinutes(10))),
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        assertNull(plan.defaultState.shortTitle)
        val staleWindow = plan.futureWindows.first { it.start == now.toInstant().plusSeconds(COMPLICATION_STALE_AFTER_SECONDS) }
        assertEquals("\u2022", staleWindow.state.shortTitle)
        assertTrue(staleWindow.state.contentDescription.contains("Showing data older than 30 seconds."))
    }

    @Test
    fun `escalates complication to two dots after one minute`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val snapshot = snapshot(
            fetchedAt = now,
            departures = listOf(departure(tripId = "trip-1", departureTime = now.plusMinutes(10))),
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        val staleWindow = plan.futureWindows.first { it.start == now.toInstant().plusSeconds(60) }
        assertEquals("\u2022\u2022", staleWindow.state.shortTitle)
        assertTrue(staleWindow.state.contentDescription.contains("Showing data older than 1 minute."))
    }

    @Test
    fun `escalates complication to three dots after two minutes`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val snapshot = snapshot(
            fetchedAt = now,
            departures = listOf(departure(tripId = "trip-1", departureTime = now.plusMinutes(10))),
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        val staleWindow = plan.futureWindows.first { it.start == now.toInstant().plusSeconds(120) }
        assertEquals("\u2022\u2022\u2022", staleWindow.state.shortTitle)
        assertTrue(staleWindow.state.contentDescription.contains("Showing data older than 2 minutes."))
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
        assertEquals("7", handoffWindow.state.shortTextFallback)
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
        assertEquals("--", emptyWindow.state.shortTextFallback)
    }

    @Test
    fun `treats already stale snapshots as maximally stale immediately`() {
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

        assertEquals("\u2022\u2022\u2022", plan.defaultState.shortTitle)
        assertTrue(plan.defaultState.contentDescription.contains("Showing stale fallback data."))
    }

    @Test
    fun `builds long text departure list for fixed line selections`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val snapshot = snapshot(
            fetchedAt = now,
            departures = listOf(
                departure(tripId = "trip-1", departureTime = now.plusMinutes(5), lineShortName = "7"),
                departure(tripId = "trip-2", departureTime = now.plusMinutes(12), lineShortName = "7"),
                departure(tripId = "trip-3", departureTime = now.plusMinutes(19), lineShortName = "7"),
                departure(tripId = "trip-4", departureTime = now.plusMinutes(26), lineShortName = "7"),
            ),
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        assertEquals("12:05 / 12:12 / 12:19", plan.defaultState.longText)
        assertEquals("Line 7", plan.defaultState.longTitle)
    }

    @Test
    fun `builds long text departure list with line prefixes for any-line selections`() {
        val now = pragueTime(hour = 12, minute = 0, second = 0)
        val snapshot = snapshot(
            fetchedAt = now,
            selection = RouteRepository.defaultRouteToPalmovka().copy(line = null),
            departures = listOf(
                departure(tripId = "trip-1", departureTime = now.plusMinutes(5), lineShortName = "7"),
                departure(tripId = "trip-2", departureTime = now.plusMinutes(12), lineShortName = "10"),
                departure(tripId = "trip-3", departureTime = now.plusMinutes(19), lineShortName = "16"),
            ),
        )

        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = now.toInstant(),
        )

        assertEquals("7 12:05 / 10 12:12 / 16 12:19", plan.defaultState.longText)
        assertEquals("Palmovka", plan.defaultState.longTitle)
    }

    private fun snapshot(
        fetchedAt: ZonedDateTime,
        selection: RouteSelection = RouteRepository.defaultRouteToPalmovka(),
        departures: List<RouteDeparture>,
        isStale: Boolean = false,
    ): DepartureSnapshot {
        return DepartureSnapshot(
            selection = selection,
            departures = departures,
            fetchedAt = fetchedAt,
            isStale = isStale,
        )
    }

    private fun departure(
        tripId: String,
        departureTime: ZonedDateTime,
        lineShortName: String = "7",
    ): RouteDeparture {
        return RouteDeparture(
            tripId = tripId,
            lineShortName = lineShortName,
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
