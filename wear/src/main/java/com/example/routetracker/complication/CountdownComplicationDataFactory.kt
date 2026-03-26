package com.example.routetracker.complication

import android.app.PendingIntent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import com.example.routetracker.data.ComplicationDisplayState
import java.time.Instant

internal fun createCountdownComplicationData(
    state: ComplicationDisplayState,
    type: ComplicationType,
    buildCountdownText: (Instant) -> ComplicationText,
    tapAction: PendingIntent? = null,
): ComplicationData {
    return when (type) {
        ComplicationType.SHORT_TEXT -> {
            val text = state.departureInstant?.let(buildCountdownText)
                ?: PlainComplicationText.Builder(state.shortTextFallback).build()
            ShortTextComplicationData.Builder(
                text = text,
                contentDescription = PlainComplicationText.Builder(state.contentDescription).build(),
            ).apply {
                state.shortTitle?.let { title ->
                    setTitle(PlainComplicationText.Builder(title).build())
                }
                tapAction?.let(::setTapAction)
            }.build()
        }

        ComplicationType.LONG_TEXT -> {
            LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder(state.longText).build(),
                contentDescription = PlainComplicationText.Builder(state.contentDescription).build(),
            ).apply {
                state.longTitle?.let { title ->
                    setTitle(PlainComplicationText.Builder(title).build())
                }
                tapAction?.let(::setTapAction)
            }.build()
        }

        else -> error("Unsupported complication type: $type")
    }
}
