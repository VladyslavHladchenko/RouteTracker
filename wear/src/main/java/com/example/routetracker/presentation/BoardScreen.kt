package com.example.routetracker.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.routetracker.data.DepartureSnapshot
import com.example.routetracker.data.RouteDeparture
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.presentation.preview.BoardPreviewCase
import com.example.routetracker.presentation.preview.BoardPreviewCaseProvider
import com.example.routetracker.presentation.preview.RouteTrackerPreviewScaffold
import com.example.routetracker.presentation.preview.WearPreviewFixtures
import com.example.routetracker.presentation.preview.WearSurfacePreview
import java.time.ZonedDateTime

@Composable
internal fun BoardScreen(
    selection: RouteSelection,
    departures: List<RouteDeparture>,
    snapshot: DepartureSnapshot?,
    statusText: String,
    currentSystemTime: ZonedDateTime,
    showSecondsEnabled: Boolean,
    autoUpdatesEnabled: Boolean,
    isRefreshing: Boolean,
    onOpenSettings: () -> Unit,
    onToggleAutoUpdates: () -> Unit,
    onOpenQuickRouteSwitch: () -> Unit,
    onOpenDepartureDetails: (RouteDeparture) -> Unit,
    onRefresh: () -> Unit,
    animateFreshnessHalo: Boolean = true,
) {
    val listState = rememberTransformingLazyColumnState()
    val emptyStateMessage = when {
        snapshot == null && isRefreshing -> "Loading live departures..."
        snapshot?.errorMessage != null -> snapshot.errorMessage
        else -> "No direct departures right now."
    }
    val freshnessHaloUiModel = buildFreshnessHaloUiModel(
        snapshot = snapshot,
        autoUpdatesEnabled = autoUpdatesEnabled,
        isRefreshing = isRefreshing,
        currentSystemTime = currentSystemTime,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        FreshnessHalo(
            uiModel = freshnessHaloUiModel,
            animate = animateFreshnessHalo,
        )
        RouteTrackerListScreen(
            state = listState,
            firstItemType = RouteTrackerColumnItemType.Surface,
            edgeButton = {
                EdgeButton(
                    onClick = onOpenQuickRouteSwitch,
                    modifier = Modifier.testTag(UiTestTags.BOARD_CHANGE_ROUTE_BUTTON),
                ) {
                    Text("Change route")
                }
            },
        ) { transformationSpec ->
            item(key = "board_summary") {
                BoardSummaryCard(
                    selection = selection,
                    statusText = statusText,
                    onOpenQuickRouteSwitch = onOpenQuickRouteSwitch,
                    onRefresh = onRefresh,
                    transformationSpec = transformationSpec,
                )
            }

            if (departures.isNotEmpty()) {
                items(
                    items = departures,
                    key = { departure -> departure.rowKey },
                ) { departure ->
                    DepartureRow(
                        selection = selection,
                        departure = departure,
                        currentSystemTime = currentSystemTime,
                        showSecondsEnabled = showSecondsEnabled,
                        onClick = {
                            onOpenDepartureDetails(departure)
                        },
                        transformationSpec = transformationSpec,
                    )
                }
            } else {
                item(key = "board_empty_state") {
                    EmptyStateCard(
                        message = emptyStateMessage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }

            item(key = "board_settings_button") {
                SettingsLauncherButton(
                    onOpenSettings = onOpenSettings,
                    transformationSpec = transformationSpec,
                )
            }
            item(key = "board_auto_updates") {
                AutoUpdatesCard(
                    autoUpdatesEnabled = autoUpdatesEnabled,
                    onToggleAutoUpdates = onToggleAutoUpdates,
                    transformationSpec = transformationSpec,
                )
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.BoardSummaryCard(
    selection: RouteSelection,
    statusText: String,
    onOpenQuickRouteSwitch: () -> Unit,
    onRefresh: () -> Unit,
    transformationSpec: TransformationSpec,
) {
    Card(
        onClick = onOpenQuickRouteSwitch,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.BOARD_SUMMARY_CARD)
            .transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = selection.headerRouteSummaryLabel,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selection.headerLineLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.testTag(UiTestTags.BOARD_REFRESH_BUTTON),
            ) {
                Icon(
                    painter = painterResource(id = android.R.drawable.stat_notify_sync),
                    contentDescription = "Refresh departures",
                )
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.SettingsLauncherButton(
    onOpenSettings: () -> Unit,
    transformationSpec: TransformationSpec,
) {
    Button(
        onClick = onOpenSettings,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.BOARD_SETTINGS_BUTTON)
            .transformedHeight(this, transformationSpec),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        transformation = SurfaceTransformation(transformationSpec),
    ) {
        Text("Settings")
    }
}

@Composable
private fun TransformingLazyColumnItemScope.DepartureRow(
    selection: RouteSelection,
    departure: RouteDeparture,
    currentSystemTime: ZonedDateTime,
    showSecondsEnabled: Boolean,
    onClick: () -> Unit,
    transformationSpec: TransformationSpec,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .testTag(UiTestTags.departureCard(departure.rowKey))
            .fillMaxWidth()
            .transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = departure.clockLabel(
                    showSeconds = showSecondsEnabled,
                    includeLine = !selection.usesFixedLine(),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            departure.boardingPlatformCompactLabel?.let { boardingPlatformLabel ->
                Text(
                    text = boardingPlatformLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = BOARDING_PLATFORM_COLOR,
                    modifier = Modifier.testTag(UiTestTags.departurePlatform(departure.rowKey)),
                    textAlign = TextAlign.End,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = departure.activityCountdownLabel(
                    referenceNow = currentSystemTime,
                    showSeconds = showSecondsEnabled,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            departure.activityDelayCompactLabel?.let { delayLabel ->
                Text(
                    text = delayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = ACTIVITY_DELAY_COLOR,
                    modifier = Modifier.testTag(UiTestTags.departureDelay(departure.rowKey)),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.AutoUpdatesCard(
    autoUpdatesEnabled: Boolean,
    onToggleAutoUpdates: () -> Unit,
    transformationSpec: TransformationSpec,
) {
    SwitchButton(
        checked = autoUpdatesEnabled,
        onCheckedChange = { onToggleAutoUpdates() },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTestTags.BOARD_AUTO_UPDATES_SWITCH)
            .transformedHeight(this, transformationSpec),
        transformation = SurfaceTransformation(transformationSpec),
        label = { Text("Auto updates") },
        secondaryLabel = {
            Text(
                if (autoUpdatesEnabled) {
                    "Refresh every 30 seconds on the board."
                } else {
                    "Pause automatic departure refreshes."
                },
            )
        },
    )
}

@WearSurfacePreview
@Composable
private fun BoardScreenPreview(
    @PreviewParameter(BoardPreviewCaseProvider::class) previewCase: BoardPreviewCase,
) {
    RouteTrackerPreviewScaffold {
        BoardScreen(
            selection = previewCase.selection,
            departures = previewCase.departures,
            snapshot = previewCase.snapshot,
            statusText = WearPreviewFixtures.statusText(
                snapshot = previewCase.snapshot,
                autoUpdatesEnabled = previewCase.autoUpdatesEnabled,
                isRefreshing = previewCase.isRefreshing,
            ),
            currentSystemTime = previewCase.currentSystemTime,
            showSecondsEnabled = false,
            autoUpdatesEnabled = previewCase.autoUpdatesEnabled,
            isRefreshing = previewCase.isRefreshing,
            onOpenSettings = {},
            onToggleAutoUpdates = {},
            onOpenQuickRouteSwitch = {},
            onOpenDepartureDetails = {},
            onRefresh = {},
            animateFreshnessHalo = false,
        )
    }
}
