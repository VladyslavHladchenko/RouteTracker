package com.example.routetracker.data

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DepartureTimingResolverTest {
    private val pragueZone: ZoneId = ZoneId.of("Europe/Prague")

    @Test
    fun `uses vehicle delay to build final departure time when available`() {
        val scheduled = pragueTime(hour = 12, minute = 0)
        val predicted = scheduled.plusMinutes(2)

        val resolved = DepartureTimingResolver.resolve(
            DepartureRealtimeInputs(
                scheduledDepartureTime = scheduled,
                predictedDepartureTime = predicted,
                boardDelaySeconds = 120,
                vehicleDelaySeconds = 300,
            )
        )

        assertEquals(scheduled.plusMinutes(5), resolved.departureTime)
        assertEquals(300, resolved.delaySeconds)
    }

    @Test
    fun `falls back to departure board predicted time when vehicle delay is missing`() {
        val scheduled = pragueTime(hour = 12, minute = 0)
        val predicted = scheduled.plusMinutes(3)

        val resolved = DepartureTimingResolver.resolve(
            DepartureRealtimeInputs(
                scheduledDepartureTime = scheduled,
                predictedDepartureTime = predicted,
                boardDelaySeconds = 180,
                vehicleDelaySeconds = null,
            )
        )

        assertEquals(predicted, resolved.departureTime)
        assertEquals(180, resolved.delaySeconds)
    }

    @Test
    fun `derives delay from predicted timestamp when board delay is not provided`() {
        val scheduled = pragueTime(hour = 12, minute = 0)
        val predicted = scheduled.plusMinutes(4)

        val resolved = DepartureTimingResolver.resolve(
            DepartureRealtimeInputs(
                scheduledDepartureTime = scheduled,
                predictedDepartureTime = predicted,
                boardDelaySeconds = null,
                vehicleDelaySeconds = null,
            )
        )

        assertEquals(predicted, resolved.departureTime)
        assertEquals(240, resolved.delaySeconds)
    }

    @Test
    fun `falls back to scheduled departure time when no realtime data exists`() {
        val scheduled = pragueTime(hour = 12, minute = 0)

        val resolved = DepartureTimingResolver.resolve(
            DepartureRealtimeInputs(
                scheduledDepartureTime = scheduled,
                predictedDepartureTime = null,
                boardDelaySeconds = null,
                vehicleDelaySeconds = null,
            )
        )

        assertEquals(scheduled, resolved.departureTime)
        assertEquals(0, resolved.delaySeconds)
    }

    @Test
    fun `route departure countdown uses resolved departure time without adding delay twice`() {
        val referenceNow = pragueTime(hour = 11, minute = 55)
        val scheduled = pragueTime(hour = 12, minute = 0)
        val predicted = scheduled.plusMinutes(2)
        val originArrivalScheduled = scheduled.minusMinutes(1)
        val originArrivalPredicted = predicted.minusMinutes(1)
        val destinationArrival = scheduled.plusMinutes(17)

        val routeDeparture = DepartureTimingResolver.resolve(
            DepartureRealtimeInputs(
                scheduledDepartureTime = scheduled,
                predictedDepartureTime = predicted,
                boardDelaySeconds = 120,
                vehicleDelaySeconds = null,
            )
        ).toRouteDeparture(
            tripId = "trip-1",
            referenceNow = referenceNow,
            departureBoardDetails = DepartureBoardDetails(
                departureTime = BoardStopTime(
                    scheduledTime = scheduled,
                    predictedTime = predicted,
                ),
                originArrivalTime = BoardStopTime(
                    scheduledTime = originArrivalScheduled,
                    predictedTime = originArrivalPredicted,
                ),
                delaySeconds = 120,
            ),
            vehiclePositionDetails = VehiclePositionDetails(
                delaySeconds = null,
                originTimestamp = null,
            ),
            destinationArrivalTime = destinationArrival,
        )

        assertEquals(7, routeDeparture.countdownMinutes)
        assertEquals("+2", routeDeparture.delayBadgeLabel)
        assertEquals("In 7 min  Delay +2 min", routeDeparture.detailStatusLabel)
        assertEquals("7", routeDeparture.countdownLabel)
        assertEquals(120, routeDeparture.departureBoardDetails.delaySeconds)
        assertEquals(scheduled, routeDeparture.departureBoardDetails.departureTime.scheduledTime)
        assertEquals(predicted, routeDeparture.departureBoardDetails.departureTime.predictedTime)
        assertEquals(originArrivalScheduled, routeDeparture.departureBoardDetails.originArrivalTime?.scheduledTime)
        assertEquals(originArrivalPredicted, routeDeparture.departureBoardDetails.originArrivalTime?.predictedTime)
        assertEquals(destinationArrival, routeDeparture.destinationArrivalTime)
    }

    @Test
    fun `activity status label includes live seconds when enabled`() {
        val referenceNow = pragueTime(hour = 11, minute = 55)
        val departureTime = ZonedDateTime.of(2026, 3, 14, 12, 2, 5, 0, pragueZone)

        val routeDeparture = RouteDeparture(
            tripId = "trip-2",
            departureTime = departureTime,
            countdownMinutes = 7,
            delayMinutes = 2,
            departureBoardDetails = DepartureBoardDetails(
                departureTime = BoardStopTime(
                    scheduledTime = departureTime.minusMinutes(2),
                    predictedTime = departureTime,
                ),
                originArrivalTime = null,
                delaySeconds = 120,
            ),
            vehiclePositionDetails = null,
            destinationArrivalTime = departureTime.plusMinutes(12),
        )

        assertEquals(
            "In 7 min 05 s  Delay +2 min",
            routeDeparture.activityStatusLabel(
                referenceNow = referenceNow,
                showSeconds = true,
            )
        )
        assertEquals(
            "In 7 min  Delay +2 min",
            routeDeparture.activityStatusLabel(
                referenceNow = referenceNow,
                showSeconds = false,
            )
        )
    }

    private fun pragueTime(
        hour: Int,
        minute: Int,
    ): ZonedDateTime {
        return ZonedDateTime.of(2026, 3, 14, hour, minute, 0, 0, pragueZone)
    }
}
