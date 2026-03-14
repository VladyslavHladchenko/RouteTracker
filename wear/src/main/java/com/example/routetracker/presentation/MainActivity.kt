package com.example.routetracker.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.routetracker.data.DepartureSnapshot
import com.example.routetracker.data.RouteDeparture
import com.example.routetracker.data.RouteDirection
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "RouteTrackerUi"
private const val MAIN_SCREEN_INDEX = 4
private const val HALF_MINUTE_MILLIS = 30_000L
private val PREVIEW_UPDATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ACTIVITY_CLOCK_MINUTES_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ACTIVITY_CLOCK_SECONDS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val routeRepo = RouteRepository(this)
        setContent {
            WearApp(routeRepo)
        }
    }
}

@Composable
fun WearApp(routeRepo: RouteRepository) {
    var selectedDirection by remember { mutableStateOf(routeRepo.getSelectedDirection()) }
    var autoUpdatesEnabled by remember { mutableStateOf(routeRepo.getAutoUpdatesEnabled()) }
    var showSecondsEnabled by remember { mutableStateOf(routeRepo.getShowSecondsEnabled()) }
    var detailsDialogAutoRefreshEnabled by remember { mutableStateOf(routeRepo.getDetailsDialogAutoRefreshEnabled()) }
    var liveSnapshotCacheLabel by remember { mutableStateOf(routeRepo.getLiveSnapshotCacheLabel()) }
    var gtfsTripDetailCacheLabel by remember { mutableStateOf(routeRepo.getGtfsTripDetailCacheLabel()) }
    var vehiclePositionCacheLabel by remember { mutableStateOf(routeRepo.getVehiclePositionCacheLabel()) }
    var isSettingsDialogOpen by remember { mutableStateOf(false) }
    var selectedDeparture by remember { mutableStateOf<RouteDeparture?>(null) }
    var snapshot by remember { mutableStateOf<DepartureSnapshot?>(null) }
    var isRefreshing by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    fun refreshSettingsState() {
        autoUpdatesEnabled = routeRepo.getAutoUpdatesEnabled()
        showSecondsEnabled = routeRepo.getShowSecondsEnabled()
        detailsDialogAutoRefreshEnabled = routeRepo.getDetailsDialogAutoRefreshEnabled()
        liveSnapshotCacheLabel = routeRepo.getLiveSnapshotCacheLabel()
        gtfsTripDetailCacheLabel = routeRepo.getGtfsTripDetailCacheLabel()
        vehiclePositionCacheLabel = routeRepo.getVehiclePositionCacheLabel()
    }

    fun applySnapshotState(newSnapshot: DepartureSnapshot?) {
        snapshot = newSnapshot
        selectedDirection = newSnapshot?.direction ?: routeRepo.getSelectedDirection()
        selectedDeparture = selectedDeparture?.let { current ->
            newSnapshot?.departures?.firstOrNull { it.tripId == current.tripId } ?: current
        }
        refreshSettingsState()
    }

    suspend fun loadSnapshot(forceRefresh: Boolean, requestSurfaceRefresh: Boolean) {
        isRefreshing = true
        Log.d(
            TAG,
            "Loading snapshot. forceRefresh=$forceRefresh requestSurfaceRefresh=$requestSurfaceRefresh direction=${selectedDirection.preferenceKey}",
        )
        snapshot = withContext(Dispatchers.IO) {
            if (requestSurfaceRefresh) {
                routeRepo.refreshDepartureSnapshot()
            } else {
                routeRepo.getDepartureSnapshot(forceRefresh = forceRefresh)
            }
        }
        applySnapshotState(snapshot)
        Log.d(TAG, "Loaded snapshot. ${snapshot?.debugSummary()}")
        isRefreshing = false
    }

    suspend fun selectDirection(direction: RouteDirection) {
        if (selectedDirection != direction) {
            Log.d(TAG, "Direction button tapped. direction=${direction.preferenceKey}")
            withContext(Dispatchers.IO) {
                routeRepo.setSelectedDirection(direction)
            }
            selectedDirection = direction
        }
        loadSnapshot(
            forceRefresh = autoUpdatesEnabled,
            requestSurfaceRefresh = false,
        )
    }

    suspend fun toggleAutoUpdates() {
        val newValue = !autoUpdatesEnabled
        Log.d(TAG, "Auto updates toggled. enabled=$newValue")
        withContext(Dispatchers.IO) {
            routeRepo.setAutoUpdatesEnabled(newValue)
        }
        refreshSettingsState()
        loadSnapshot(
            forceRefresh = newValue,
            requestSurfaceRefresh = newValue,
        )
    }

    suspend fun toggleShowSeconds() {
        val newValue = !showSecondsEnabled
        Log.d(TAG, "Show seconds toggled. enabled=$newValue")
        withContext(Dispatchers.IO) {
            routeRepo.setShowSecondsEnabled(newValue)
        }
        refreshSettingsState()
        loadSnapshot(forceRefresh = false, requestSurfaceRefresh = false)
    }

    suspend fun toggleDetailsDialogAutoRefresh() {
        val newValue = !detailsDialogAutoRefreshEnabled
        Log.d(TAG, "Details dialog auto-refresh toggled. enabled=$newValue")
        withContext(Dispatchers.IO) {
            routeRepo.setDetailsDialogAutoRefreshEnabled(newValue)
        }
        refreshSettingsState()
    }

    suspend fun cycleLiveSnapshotCache() {
        Log.d(TAG, "Cycling live snapshot cache.")
        withContext(Dispatchers.IO) {
            routeRepo.cycleLiveSnapshotCacheMillis()
        }
        refreshSettingsState()
    }

    suspend fun cycleGtfsTripDetailCache() {
        Log.d(TAG, "Cycling GTFS trip detail cache.")
        withContext(Dispatchers.IO) {
            routeRepo.cycleGtfsTripDetailCacheMillis()
        }
        refreshSettingsState()
    }

    suspend fun cycleVehiclePositionCache() {
        Log.d(TAG, "Cycling vehicle position cache.")
        withContext(Dispatchers.IO) {
            routeRepo.cycleVehiclePositionCacheMillis()
        }
        refreshSettingsState()
    }

    LaunchedEffect(routeRepo, autoUpdatesEnabled) {
        loadSnapshot(forceRefresh = false, requestSurfaceRefresh = false)
        while (autoUpdatesEnabled) {
            val delayMillis = millisUntilNextHalfMinute()
            Log.d(TAG, "Waiting ${delayMillis}ms for next aligned refresh boundary.")
            delay(delayMillis)
            if (!autoUpdatesEnabled) {
                break
            }

            isRefreshing = true
            snapshot = withContext(Dispatchers.IO) {
                routeRepo.refreshDepartureSnapshot()
            }
            applySnapshotState(snapshot)
            Log.d(TAG, "Aligned refresh finished. ${snapshot?.debugSummary()}")
            isRefreshing = false
        }
    }

    LaunchedEffect(routeRepo, selectedDeparture?.tripId, detailsDialogAutoRefreshEnabled, autoUpdatesEnabled) {
        val trackedTripId = selectedDeparture?.tripId ?: return@LaunchedEffect
        if (!detailsDialogAutoRefreshEnabled || !autoUpdatesEnabled) {
            return@LaunchedEffect
        }

        while (
            selectedDeparture?.tripId == trackedTripId &&
            detailsDialogAutoRefreshEnabled &&
            autoUpdatesEnabled
        ) {
            delay(RouteRepository.DETAILS_DIALOG_REFRESH_INTERVAL_MILLIS)
            if (
                selectedDeparture?.tripId != trackedTripId ||
                !detailsDialogAutoRefreshEnabled ||
                !autoUpdatesEnabled
            ) {
                break
            }

            Log.d(TAG, "Refreshing open details dialog for tripId=$trackedTripId")
            val refreshedSnapshot = withContext(Dispatchers.IO) {
                routeRepo.getDepartureSnapshot(forceRefresh = true)
            }
            applySnapshotState(refreshedSnapshot)
            Log.d(TAG, "Details dialog refresh finished. ${refreshedSnapshot.debugSummary()}")
        }
    }

    val departures = snapshot?.departures.orEmpty()
    val updatedLabel = snapshot?.fetchedAt?.let { routeRepo.formatStatusTime(it) }
    val statusText = snapshotStatusText(
        snapshot = snapshot,
        autoUpdatesEnabled = autoUpdatesEnabled,
        isRefreshing = isRefreshing,
        hasDepartures = departures.isNotEmpty(),
        updatedLabel = updatedLabel,
    )

    RouteTrackerTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            ActivityClockChip(
                showSeconds = showSecondsEnabled,
                modifier = Modifier
                    .fillMaxSize(),
            )

        if (isSettingsDialogOpen) {
            SettingsDialog(
                showSecondsEnabled = showSecondsEnabled,
                detailsDialogAutoRefreshEnabled = detailsDialogAutoRefreshEnabled,
                liveSnapshotCacheLabel = liveSnapshotCacheLabel,
                gtfsTripDetailCacheLabel = gtfsTripDetailCacheLabel,
                vehiclePositionCacheLabel = vehiclePositionCacheLabel,
                onToggleShowSeconds = {
                    coroutineScope.launch {
                        toggleShowSeconds()
                    }
                },
                onToggleDetailsDialogAutoRefresh = {
                    coroutineScope.launch {
                        toggleDetailsDialogAutoRefresh()
                    }
                },
                onCycleLiveSnapshotCache = {
                    coroutineScope.launch {
                        cycleLiveSnapshotCache()
                    }
                },
                onCycleGtfsTripDetailCache = {
                    coroutineScope.launch {
                        cycleGtfsTripDetailCache()
                    }
                },
                onCycleVehiclePositionCache = {
                    coroutineScope.launch {
                        cycleVehiclePositionCache()
                    }
                },
                onDismiss = {
                    isSettingsDialogOpen = false
                },
            )
        }

        selectedDeparture?.let { departure ->
            DepartureDetailsDialog(
                departure = departure,
                routeRepo = routeRepo,
                onDismiss = {
                    selectedDeparture = null
                },
            )
        }

        val listState = rememberScalingLazyListState(
            initialCenterItemIndex = MAIN_SCREEN_INDEX,
        )
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            state = listState,
        ) {
            item {
                SettingsLauncherButton(
                    onOpenSettings = {
                        isSettingsDialogOpen = true
                    },
                )
            }

            item {
                AutoUpdatesCard(
                    autoUpdatesEnabled = autoUpdatesEnabled,
                    onToggleAutoUpdates = {
                        coroutineScope.launch {
                            toggleAutoUpdates()
                        }
                    },
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {}
            }

            item {
                HeaderCard(
                    direction = selectedDirection,
                    statusText = statusText,
                )
            }

            item {
                DirectionSelector(
                    selectedDirection = selectedDirection,
                    onSelectDirection = { direction ->
                        coroutineScope.launch {
                            selectDirection(direction)
                        }
                    },
                )
            }

            if (departures.isNotEmpty()) {
                items(departures.size) { index ->
                    DepartureRow(
                        departure = departures[index],
                        routeRepo = routeRepo,
                        onClick = {
                            selectedDeparture = departures[index]
                        },
                    )
                }
            } else {
                item {
                    EmptyStateCard(snapshot?.errorMessage ?: "No direct departures right now.")
                }
            }

            item {
                Button(
                    onClick = {
                        Log.d(TAG, "Refresh button tapped.")
                        coroutineScope.launch {
                            loadSnapshot(forceRefresh = true, requestSurfaceRefresh = true)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(if (isRefreshing) "Refreshing..." else "Refresh")
                }
            }
        }
        }
    }
}

private fun millisUntilNextHalfMinute(nowMillis: Long = System.currentTimeMillis()): Long {
    val remainder = nowMillis % HALF_MINUTE_MILLIS
    return if (remainder == 0L) {
        HALF_MINUTE_MILLIS
    } else {
        HALF_MINUTE_MILLIS - remainder
    }
}

@Composable
private fun SettingsLauncherButton(
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(
            onClick = onOpenSettings,
            modifier = Modifier.size(52.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Icon(
                painter = painterResource(id = android.R.drawable.ic_menu_manage),
                contentDescription = "Open settings",
            )
        }
    }
}

@Composable
private fun ActivityClockChip(
    showSeconds: Boolean,
    modifier: Modifier = Modifier,
) {
    var clockText by remember { mutableStateOf(formatActivityClock(LocalTime.now(), showSeconds)) }

    LaunchedEffect(showSeconds) {
        while (true) {
            val now = System.currentTimeMillis()
            val nextTickMillis = if (showSeconds) {
                1_000L - (now % 1_000L)
            } else {
                60_000L - (now % 60_000L)
            }
            delay(if (nextTickMillis == 0L) 1L else nextTickMillis)
            clockText = formatActivityClock(LocalTime.now(), showSeconds)
        }
    }

    CurvedLayout(
        modifier = modifier,
        anchor = 180f,
    ) {
        basicCurvedText(
            text = clockText,
            modifier = CurvedModifier.padding(8.dp),
            style = {
                CurvedTextStyle(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            },
        )
    }
}

@Composable
private fun SettingsDialog(
    showSecondsEnabled: Boolean,
    detailsDialogAutoRefreshEnabled: Boolean,
    liveSnapshotCacheLabel: String,
    gtfsTripDetailCacheLabel: String,
    vehiclePositionCacheLabel: String,
    onToggleShowSeconds: () -> Unit,
    onToggleDetailsDialogAutoRefresh: () -> Unit,
    onCycleLiveSnapshotCache: () -> Unit,
    onCycleGtfsTripDetailCache: () -> Unit,
    onCycleVehiclePositionCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Display",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onToggleShowSeconds,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                colors = if (showSecondsEnabled) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                },
            ) {
                Text(if (showSecondsEnabled) "Show seconds: On" else "Show seconds: Off")
            }
            Button(
                onClick = onToggleDetailsDialogAutoRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = if (detailsDialogAutoRefreshEnabled) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                },
            ) {
                Text(
                    if (detailsDialogAutoRefreshEnabled) {
                        "Details auto-refresh: On (10 s)"
                    } else {
                        "Details auto-refresh: Off"
                    }
                )
            }
            Text(
                text = "Cache",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onCycleLiveSnapshotCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Live snapshot: $liveSnapshotCacheLabel")
            }
            Button(
                onClick = onCycleGtfsTripDetailCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Trip detail: $gtfsTripDetailCacheLabel")
            }
            Button(
                onClick = onCycleVehiclePositionCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Vehicle live: $vehiclePositionCacheLabel")
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("Close")
            }
        }
    }
}

private fun formatActivityClock(
    time: LocalTime,
    showSeconds: Boolean,
): String {
    return time.format(
        if (showSeconds) ACTIVITY_CLOCK_SECONDS_FORMATTER else ACTIVITY_CLOCK_MINUTES_FORMATTER
    )
}

@Composable
private fun HeaderCard(
    direction: RouteDirection,
    statusText: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Line ${RouteRepository.LINE_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "To ${direction.tileLabel}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DirectionSelector(
    selectedDirection: RouteDirection,
    onSelectDirection: (RouteDirection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(8.dp),
    ) {
        Text(
            text = "Direction",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteDirection.entries.forEach { direction ->
                DirectionButton(
                    direction = direction,
                    isSelected = direction == selectedDirection,
                    onClick = { onSelectDirection(direction) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DirectionButton(
    direction: RouteDirection,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (isSelected) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )
        },
    ) {
        Text(direction.tileLabel)
    }
}

@Composable
private fun DepartureRow(
    departure: RouteDeparture,
    routeRepo: RouteRepository,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = routeRepo.formatDepartureClockTime(departure),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = departure.detailStatusLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DepartureDetailsDialog(
    departure: RouteDeparture,
    routeRepo: RouteRepository,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .verticalScroll(rememberScrollState())
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = routeRepo.formatDepartureClockTime(departure),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = departure.detailStatusLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
            DetailValueRow(
                label = "Destination arrival",
                value = routeRepo.formatDetailTime(departure.destinationArrivalTime),
            )
            DetailSectionTitle(
                title = "Departure board",
                topPadding = 12.dp,
            )
            DetailValueRow(
                label = "Departure scheduled",
                value = routeRepo.formatDetailTime(departure.departureBoardDetails.departureTime.scheduledTime),
            )
            DetailValueRow(
                label = "Departure predicted",
                value = routeRepo.formatDetailTime(departure.departureBoardDetails.departureTime.predictedTime),
            )
            DetailValueRow(
                label = "Delay",
                value = routeRepo.formatDelaySeconds(departure.departureBoardDetails.delaySeconds),
            )
            DetailValueRow(
                label = "Origin arrival scheduled",
                value = routeRepo.formatDetailTime(departure.departureBoardDetails.originArrivalTime?.scheduledTime),
            )
            DetailValueRow(
                label = "Origin arrival predicted",
                value = routeRepo.formatDetailTime(departure.departureBoardDetails.originArrivalTime?.predictedTime),
            )
            DetailSectionTitle(
                title = "Vehicle positions",
                topPadding = 12.dp,
            )
            departure.vehiclePositionDetails?.let { vehicleDetails ->
                DetailValueRow(
                    label = "Delay",
                    value = routeRepo.formatDelaySeconds(vehicleDetails.delaySeconds),
                )
                DetailValueRow(
                    label = "origin_timestamp",
                    value = routeRepo.formatDetailTime(vehicleDetails.originTimestamp),
                )
            } ?: DetailValueRow(
                label = "Status",
                value = "Not available",
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Text("Close")
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(
    title: String,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun DetailValueRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutoUpdatesCard(
    autoUpdatesEnabled: Boolean,
    onToggleAutoUpdates: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Updates",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onToggleAutoUpdates,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = if (autoUpdatesEnabled) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            },
        ) {
            Text(if (autoUpdatesEnabled) "Auto updates: On" else "Auto updates: Off")
        }
    }
}

private fun snapshotStatusText(
    snapshot: DepartureSnapshot?,
    autoUpdatesEnabled: Boolean,
    isRefreshing: Boolean,
    hasDepartures: Boolean,
    updatedLabel: String?,
): String {
    if (!autoUpdatesEnabled) {
        return if (updatedLabel != null && hasDepartures) {
            "Paused | $updatedLabel"
        } else {
            "Updates paused"
        }
    }

    if (snapshot == null || (isRefreshing && !hasDepartures)) {
        return "Loading"
    }
    if (snapshot.errorMessage != null && !hasDepartures) {
        return snapshot.errorMessage
    }

    val freshnessLabel = if (snapshot.isStale) "Cached" else "Live"
    return if (isRefreshing) {
        "$freshnessLabel | $updatedLabel | Refreshing"
    } else {
        "$freshnessLabel | $updatedLabel"
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    val previewContext = LocalContext.current
    val previewSnapshot = remember { RouteRepository.previewSnapshot() }
    val previewRepository = remember { RouteRepository(previewContext) }

    RouteTrackerTheme {
        ScalingLazyColumn(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
            state = rememberScalingLazyListState(
                initialCenterItemIndex = MAIN_SCREEN_INDEX,
            ),
        ) {
            item {
                SettingsLauncherButton(
                    onOpenSettings = {},
                )
            }
            item {
                AutoUpdatesCard(
                    autoUpdatesEnabled = true,
                    onToggleAutoUpdates = {},
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {}
            }
            item {
                HeaderCard(
                    direction = previewSnapshot.direction,
                    statusText = snapshotStatusText(
                        snapshot = previewSnapshot,
                        autoUpdatesEnabled = true,
                        isRefreshing = false,
                        hasDepartures = previewSnapshot.departures.isNotEmpty(),
                        updatedLabel = previewSnapshot.fetchedAt.format(PREVIEW_UPDATE_TIME_FORMATTER),
                    ),
                )
            }
            item {
                DirectionSelector(
                    selectedDirection = previewSnapshot.direction,
                    onSelectDirection = {},
                )
            }
            items(previewSnapshot.departures.size) { index ->
                DepartureRow(
                    departure = previewSnapshot.departures[index],
                    routeRepo = previewRepository,
                    onClick = {},
                )
            }
        }
    }
}
