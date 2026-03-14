package com.example.routetracker.complication

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
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
    protected abstract val previewText: String
    protected abstract fun buildCountdownText(departureInstant: Instant): ComplicationText

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData(
            state = ComplicationDisplayState(
                departureInstant = null,
                fallbackText = previewText,
                title = null,
                contentDescription = "Next line 7 departure to N\u00E1dra\u017E\u00ED V\u0159\u0161ovice.",
            ),
        )
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
        return ComplicationDataTimeline(
            defaultComplicationData = createComplicationData(plan.defaultState),
            timelineEntries = plan.futureWindows.map { window ->
                TimelineEntry(
                    validity = TimeInterval(window.start, window.end),
                    complicationData = createComplicationData(window.state),
                )
            },
        )
    }

    private fun createComplicationData(
        state: ComplicationDisplayState,
    ): ComplicationData {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text: ComplicationText = state.departureInstant?.let(::buildCountdownText)
            ?: PlainComplicationText.Builder(state.fallbackText).build()

        val builder = ShortTextComplicationData.Builder(
            text = text,
            contentDescription = PlainComplicationText.Builder(state.contentDescription).build()
        )

        state.title?.let { title ->
            builder.setTitle(PlainComplicationText.Builder(title).build())
        }

        return builder
            .setTapAction(pendingIntent)
            .build()
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
