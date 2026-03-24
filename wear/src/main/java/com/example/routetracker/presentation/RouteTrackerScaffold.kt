package com.example.routetracker.presentation

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
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

internal enum class RouteTrackerColumnItemType {
    ListHeader,
    Surface,
}

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
    firstItemType: RouteTrackerColumnItemType = RouteTrackerColumnItemType.ListHeader,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val transformationSpec = rememberTransformationSpec()
    val contentPadding = rememberRouteTrackerContentPadding(
        firstItemType = firstItemType,
        hasEdgeButton = edgeButton != null,
    )

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
private fun rememberRouteTrackerContentPadding(
    firstItemType: RouteTrackerColumnItemType,
    hasEdgeButton: Boolean,
): PaddingValues {
    val configuration = LocalConfiguration.current
    val horizontalPadding = responsiveInset(
        screenSizeDp = configuration.screenWidthDp,
        fraction = 0.06f,
        min = 12.dp,
        max = 18.dp,
    )
    val topPadding = when (firstItemType) {
        RouteTrackerColumnItemType.ListHeader -> responsiveInset(
            screenSizeDp = configuration.screenHeightDp,
            fraction = 0.025f,
            min = 4.dp,
            max = 8.dp,
        )
        RouteTrackerColumnItemType.Surface -> responsiveInset(
            screenSizeDp = configuration.screenHeightDp,
            fraction = 0.095f,
            min = 20.dp,
            max = 28.dp,
        )
    }
    val bottomPadding = if (hasEdgeButton) {
        responsiveInset(
            screenSizeDp = configuration.screenHeightDp,
            fraction = 0.03f,
            min = 6.dp,
            max = 10.dp,
        )
    } else {
        responsiveInset(
            screenSizeDp = configuration.screenHeightDp,
            fraction = 0.055f,
            min = 10.dp,
            max = 16.dp,
        )
    }

    return PaddingValues(
        start = horizontalPadding,
        end = horizontalPadding,
        top = topPadding,
        bottom = bottomPadding,
    )
}

private fun responsiveInset(
    screenSizeDp: Int,
    fraction: Float,
    min: Dp,
    max: Dp,
): Dp {
    return (screenSizeDp * fraction).dp.coerceIn(min, max)
}
