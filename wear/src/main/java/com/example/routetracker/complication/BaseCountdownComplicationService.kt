package com.example.routetracker.complication

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataTimeline
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingTimelineComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.TimeInterval
import androidx.wear.watchface.complications.datasource.TimelineEntry
import com.example.routetracker.data.ComplicationDisplayState
import com.example.routetracker.data.ComplicationTimelinePlanner
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.presentation.MainActivity
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class BaseCountdownComplicationService : SuspendingTimelineComplicationDataSourceService() {
    protected abstract val logTag: String
    protected abstract val previewShortText: String
    protected abstract fun buildCountdownText(departureInstant: Instant): ComplicationText

    protected open val previewLongText: String = "18:33 / 18:41 / 18:49"
    protected open val previewLongTitle: String? = "Line 7"

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT,
            -> createCountdownComplicationData(
                state = ComplicationDisplayState(
                    departureInstant = null,
                    shortTextFallback = previewShortText,
                    shortTitle = null,
                    longText = previewLongText,
                    longTitle = previewLongTitle,
                    contentDescription = "Upcoming direct transport departures.",
                ),
                type = type,
                buildCountdownText = ::buildCountdownText,
            )

            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationDataTimeline {
        val routeRepo = RouteRepository(this)
        val snapshot = withContext(Dispatchers.IO) {
            routeRepo.getDepartureSnapshot()
        }
        val plan = ComplicationTimelinePlanner.create(
            snapshot = snapshot,
            now = Instant.now(),
        )
        Log.d(logTag, "Complication request. ${snapshot.debugSummary()}")
        val tapAction = buildTapAction()
        return ComplicationDataTimeline(
            defaultComplicationData = createCountdownComplicationData(
                state = plan.defaultState,
                type = request.complicationType,
                buildCountdownText = ::buildCountdownText,
                tapAction = tapAction,
            ),
            timelineEntries = plan.futureWindows.map { window ->
                TimelineEntry(
                    validity = TimeInterval(window.start, window.end),
                    complicationData = createCountdownComplicationData(
                        state = window.state,
                        type = request.complicationType,
                        buildCountdownText = ::buildCountdownText,
                        tapAction = tapAction,
                    ),
                )
            },
        )
    }

    private fun buildTapAction(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    protected fun buildMinuteCountdownText(departureInstant: Instant): ComplicationText {
        return TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.SHORT_SINGLE_UNIT,
            CountDownTimeReference(departureInstant),
        )
            .setText("^1")
            .setDisplayAsNow(false)
            .setMinimumTimeUnit(TimeUnit.MINUTES)
            .build()
    }

    protected fun buildStopwatchCountdownText(departureInstant: Instant): ComplicationText {
        return TimeDifferenceComplicationText.Builder(
            TimeDifferenceStyle.STOPWATCH,
            CountDownTimeReference(departureInstant),
        )
            .setText("^1")
            .setDisplayAsNow(false)
            .build()
    }
}
