package com.example.routetracker.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec

@Composable
internal fun RouteTrackerAppScaffold(
    timeText: @Composable () -> Unit = { TimeText() },
    content: @Composable BoxScope.() -> Unit,
) {
    AppScaffold(timeText = timeText, content = content)
}

@Composable
internal fun RouteTrackerListScreen(
    state: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    timeText: (@Composable () -> Unit)? = null,
    edgeButton: (@Composable BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val transformationSpec = rememberTransformationSpec()
    val contentPadding = rememberRouteTrackerContentPadding()

    if (edgeButton != null) {
        ScreenScaffold(
            scrollState = state,
            contentPadding = contentPadding,
            timeText = timeText,
            edgeButton = edgeButton,
        ) { scaffoldPadding ->
            TransformingLazyColumn(
                state = state,
                contentPadding = scaffoldPadding,
            ) {
                content(transformationSpec)
            }
        }
    } else {
        ScreenScaffold(
            scrollState = state,
            contentPadding = contentPadding,
            timeText = timeText,
        ) { scaffoldPadding ->
            TransformingLazyColumn(
                state = state,
                contentPadding = scaffoldPadding,
            ) {
                content(transformationSpec)
            }
        }
    }
}

@Composable
private fun rememberRouteTrackerContentPadding(): PaddingValues {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val horizontalPadding = when {
        screenWidthDp <= 220 -> 10.dp
        screenWidthDp <= 260 -> 12.dp
        else -> 14.dp
    }

    return PaddingValues(
        start = horizontalPadding,
        end = horizontalPadding,
        top = 10.dp,
        bottom = 12.dp,
    )
}
