package com.example.routetracker.complication

import androidx.wear.watchface.complications.data.ComplicationText
import java.time.Instant

private const val TAG = "RouteTrackerComp"

class MainComplicationService : BaseCountdownComplicationService() {
    override val logTag: String = TAG
    override val previewShortText: String = "8"

    override fun buildCountdownText(departureInstant: Instant): ComplicationText {
        return buildMinuteCountdownText(departureInstant)
    }
}
