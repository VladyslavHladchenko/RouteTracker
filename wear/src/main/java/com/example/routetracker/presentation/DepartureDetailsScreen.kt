package com.example.routetracker.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.routetracker.data.RouteDeparture
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.presentation.preview.DepartureDetailsPreviewCase
import com.example.routetracker.presentation.preview.DepartureDetailsPreviewCaseProvider
import com.example.routetracker.presentation.preview.RouteTrackerPreviewScaffold
import com.example.routetracker.presentation.preview.WearPreviewFixtures
import com.example.routetracker.presentation.preview.WearSurfacePreview
import java.time.ZonedDateTime

internal data class DepartureDetailsUiState(
    val clockLabel: String,
    val statusLabel: String,
    val refreshHint: String,
    val boardingPlatform: String,
    val destinationArrival: String,
    val lineLabel: String,
    val departureScheduled: String,
    val departurePredicted: String,
    val departureDelay: String,
    val originArrivalScheduled: String,
    val originArrivalPredicted: String,
    val vehicleStatusLabel: String,
    val vehicleStatusValue: String,
    val vehicleOriginTimestamp: String?,
)

@Composable
internal fun DepartureDetailsScreen(
    uiState: DepartureDetailsUiState,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()

    RouteTrackerListScreen(
        state = listState,
        firstItemType = RouteTrackerColumnItemType.Surface,
    ) { transformationSpec ->
        item(key = "trip_details_summary") {
            DepartureDetailsSummaryCard(
                uiState = uiState,
                onRefresh = onRefresh,
                transformationSpec = transformationSpec,
            )
        }
        item(key = "trip_details_platform") {
            DetailValueRow(
                label = "Boarding platform",
                value = uiState.boardingPlatform,
                valueColor = BOARDING_PLATFORM_COLOR,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_arrival") {
            DetailValueRow(
                label = "Destination arrival",
                value = uiState.destinationArrival,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_line") {
            DetailValueRow(
                label = "Line",
                value = uiState.lineLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_board_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Departure board")
            }
        }
        item(key = "trip_details_departure_scheduled") {
            DetailValueRow(
                label = "Scheduled",
                value = uiState.departureScheduled,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_departure_predicted") {
            DetailValueRow(
                label = "Predicted",
                value = uiState.departurePredicted,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_delay") {
            DetailValueRow(
                label = "Delay",
                value = uiState.departureDelay,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_origin_arrival_scheduled") {
            DetailValueRow(
                label = "Origin arrival scheduled",
                value = uiState.originArrivalScheduled,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_origin_arrival_predicted") {
            DetailValueRow(
                label = "Origin arrival predicted",
                value = uiState.originArrivalPredicted,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_vehicle_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Vehicle position")
            }
        }
        item(key = "trip_details_vehicle_status") {
            DetailValueRow(
                label = uiState.vehicleStatusLabel,
                value = uiState.vehicleStatusValue,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        uiState.vehicleOriginTimestamp?.let { originTimestamp ->
            item(key = "trip_details_vehicle_origin_timestamp") {
                DetailValueRow(
                    label = "Origin timestamp",
                    value = originTimestamp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}

internal fun RouteRepository.buildDepartureDetailsUiState(
    selection: RouteSelection,
    departure: RouteDeparture,
    currentSystemTime: ZonedDateTime,
    showSecondsEnabled: Boolean,
    isRefreshing: Boolean,
): DepartureDetailsUiState {
    return DepartureDetailsUiState(
        clockLabel = departure.clockLabel(
            showSeconds = showSecondsEnabled,
            includeLine = !selection.usesFixedLine(),
        ),
        statusLabel = departure.activityStatusLabel(
            referenceNow = currentSystemTime,
            showSeconds = showSecondsEnabled,
        ),
        refreshHint = if (isRefreshing) {
            "Refreshing live detail..."
        } else {
            "Tap refresh to update live detail."
        },
        boardingPlatform = departure.boardingPlatformCompactLabel ?: "--",
        destinationArrival = formatDetailTime(departure.destinationArrivalTime),
        lineLabel = departure.lineLabel,
        departureScheduled = formatDetailTime(departure.departureBoardDetails.departureTime.scheduledTime),
        departurePredicted = formatDetailTime(departure.departureBoardDetails.departureTime.predictedTime),
        departureDelay = formatDelaySeconds(departure.departureBoardDetails.delaySeconds),
        originArrivalScheduled = formatDetailTime(departure.departureBoardDetails.originArrivalTime?.scheduledTime),
        originArrivalPredicted = formatDetailTime(departure.departureBoardDetails.originArrivalTime?.predictedTime),
        vehicleStatusLabel = if (departure.vehiclePositionDetails != null) "Delay" else "Status",
        vehicleStatusValue = departure.vehiclePositionDetails?.let { formatDelaySeconds(it.delaySeconds) }
            ?: "Not available",
        vehicleOriginTimestamp = departure.vehiclePositionDetails?.originTimestamp?.let(::formatDetailTime),
    )
}

@Composable
private fun TransformingLazyColumnItemScope.DepartureDetailsSummaryCard(
    uiState: DepartureDetailsUiState,
    onRefresh: () -> Unit,
    transformationSpec: TransformationSpec,
) {
    Card(
        onClick = onRefresh,
        modifier = Modifier
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.Top,
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = uiState.clockLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = uiState.statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = uiState.refreshHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag(UiTestTags.TRIP_DETAILS_REFRESH_BUTTON),
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.stat_notify_sync),
                    contentDescription = "Refresh trip details",
                )
            }
        }
    }
}

@WearSurfacePreview
@Composable
private fun DepartureDetailsScreenPreview(
    @PreviewParameter(DepartureDetailsPreviewCaseProvider::class) previewCase: DepartureDetailsPreviewCase,
) {
    RouteTrackerPreviewScaffold {
        DepartureDetailsScreen(
            uiState = previewCase.uiState,
            onRefresh = {},
            onDismiss = {},
        )
    }
}
