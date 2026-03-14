package com.example.routetracker.complication

import androidx.wear.watchface.complications.data.ComplicationText
import java.time.Instant

private const val TAG = "RouteTrackerCompStopwatch"

class StopwatchComplicationService : BaseCountdownComplicationService() {
    override val logTag: String = TAG
    override val previewText: String = "08:15"

    override fun buildCountdownText(departureInstant: Instant): ComplicationText {
        return buildStopwatchCountdownText(departureInstant)
    }
}
