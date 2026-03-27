package com.example.routetracker.presentation.preview

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.example.routetracker.presentation.RouteTrackerAppScaffold
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun RouteTrackerPreviewScaffold(
    showTimeText: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    RouteTrackerTheme {
        RouteTrackerAppScaffold(
            timeText = {
                if (showTimeText) {
                    TimeText(timeSource = FixedPreviewTimeSource)
                }
            },
            content = content,
        )
    }
}

private object FixedPreviewTimeSource : TimeSource {
    private val fixedNow = ZonedDateTime.of(
        2026,
        3,
        16,
        18,
        30,
        0,
        0,
        ZoneId.of("Europe/Prague"),
    )
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")

    @Composable
    override fun currentTime(): String = fixedNow.format(formatter)
}
