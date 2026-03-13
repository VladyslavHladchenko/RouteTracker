package com.example.routetracker.complication

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.presentation.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "RouteTrackerComp"

class MainComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        val previewSnapshot = RouteRepository.previewSnapshot()
        return createComplicationData(
            text = previewSnapshot.complicationText(),
            title = previewSnapshot.complicationDelayText(),
            contentDescription = "Next line 7 departure ${previewSnapshot.direction.buttonLabel}.",
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val routeRepo = RouteRepository(this)
        val snapshot = withContext(Dispatchers.IO) {
            routeRepo.getDepartureSnapshot()
        }
        val firstDeparture = snapshot.departures.firstOrNull()
        Log.d(TAG, "Complication request. ${snapshot.debugSummary()}")
        return createComplicationData(
            text = snapshot.complicationText(),
            title = snapshot.complicationDelayText(),
            contentDescription = firstDeparture?.accessibilityLabel
                ?: snapshot.errorMessage
                ?: "No upcoming direct line 7 departures ${snapshot.direction.buttonLabel}.",
        )
    }

    private fun createComplicationData(
        text: String,
        title: String?,
        contentDescription: String,
    ): ComplicationData {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build()
        )

        title?.let { delayText ->
            builder.setTitle(PlainComplicationText.Builder(delayText).build())
        }

        return builder
            .setTapAction(pendingIntent)
            .build()
    }
}
