package com.example.routetracker.data

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.roundToInt

data class BoardStopTime(
    val scheduledTime: ZonedDateTime,
    val predictedTime: ZonedDateTime?,
) {
    val bestAvailableTime: ZonedDateTime
        get() = predictedTime ?: scheduledTime
}

data class DepartureRealtimeInputs(
    val scheduledDepartureTime: ZonedDateTime,
    val predictedDepartureTime: ZonedDateTime?,
    val boardDelaySeconds: Int?,
    val vehicleDelaySeconds: Int?,
)

data class ResolvedDepartureTiming(
    val departureTime: ZonedDateTime,
    val delaySeconds: Int,
) {
    val delayMinutes: Int
        get() = (delaySeconds / 60.0).roundToInt()

    fun countdownMinutes(referenceNow: ZonedDateTime): Int {
        return max(0, Duration.between(referenceNow, departureTime).toMinutes().toInt())
    }

    fun toRouteDeparture(
        tripId: String,
        lineShortName: String,
        referenceNow: ZonedDateTime,
        boardedStopId: String? = null,
        boardedPlatformLabel: String? = null,
        departureBoardDetails: DepartureBoardDetails,
        vehiclePositionDetails: VehiclePositionDetails?,
        destinationArrivalTime: ZonedDateTime?,
    ): RouteDeparture {
        return RouteDeparture(
            tripId = tripId,
            lineShortName = lineShortName,
            departureTime = departureTime,
            countdownMinutes = countdownMinutes(referenceNow),
            delayMinutes = delayMinutes,
            boardedStopId = boardedStopId,
            boardedPlatformLabel = boardedPlatformLabel,
            departureBoardDetails = departureBoardDetails,
            vehiclePositionDetails = vehiclePositionDetails,
            destinationArrivalTime = destinationArrivalTime,
        )
    }
}

object DepartureTimingResolver {
    fun resolve(inputs: DepartureRealtimeInputs): ResolvedDepartureTiming {
        val resolvedDelaySeconds = inputs.vehicleDelaySeconds
            ?: inputs.boardDelaySeconds
            ?: derivedDelaySeconds(
                scheduledDepartureTime = inputs.scheduledDepartureTime,
                predictedDepartureTime = inputs.predictedDepartureTime,
            )
            ?: 0

        val resolvedDepartureTime = when {
            inputs.vehicleDelaySeconds != null -> {
                inputs.scheduledDepartureTime.plusSeconds(inputs.vehicleDelaySeconds.toLong())
            }

            inputs.predictedDepartureTime != null -> inputs.predictedDepartureTime
            else -> inputs.scheduledDepartureTime
        }

        return ResolvedDepartureTiming(
            departureTime = resolvedDepartureTime,
            delaySeconds = resolvedDelaySeconds,
        )
    }

    private fun derivedDelaySeconds(
        scheduledDepartureTime: ZonedDateTime,
        predictedDepartureTime: ZonedDateTime?,
    ): Int? {
        predictedDepartureTime ?: return null
        return Duration.between(scheduledDepartureTime, predictedDepartureTime).seconds.toInt()
    }
}
