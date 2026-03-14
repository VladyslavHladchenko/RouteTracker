package com.example.routetracker.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.window.Dialog
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
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
import com.example.routetracker.data.formatDisplayTime
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "RouteTrackerUi"
private const val MAIN_SCREEN_INDEX = 4
private const val HALF_MINUTE_MILLIS = 30_000L
private val PREVIEW_UPDATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
    var isSettingsDialogOpen by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<DepartureSnapshot?>(null) }
    var isRefreshing by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

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
        selectedDirection = snapshot?.direction ?: routeRepo.getSelectedDirection()
        autoUpdatesEnabled = routeRepo.getAutoUpdatesEnabled()
        showSecondsEnabled = routeRepo.getShowSecondsEnabled()
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
        autoUpdatesEnabled = newValue
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
        showSecondsEnabled = newValue
        loadSnapshot(forceRefresh = false, requestSurfaceRefresh = false)
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
            selectedDirection = snapshot?.direction ?: routeRepo.getSelectedDirection()
            autoUpdatesEnabled = routeRepo.getAutoUpdatesEnabled()
            Log.d(TAG, "Aligned refresh finished. ${snapshot?.debugSummary()}")
            isRefreshing = false
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
        if (isSettingsDialogOpen) {
            SettingsDialog(
                showSecondsEnabled = showSecondsEnabled,
                onToggleShowSeconds = {
                    coroutineScope.launch {
                        toggleShowSeconds()
                    }
                },
                onDismiss = {
                    isSettingsDialogOpen = false
                },
            )
        }

        val listState = rememberScalingLazyListState(
            initialCenterItemIndex = MAIN_SCREEN_INDEX,
        )
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
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
private fun SettingsDialog(
    showSecondsEnabled: Boolean,
    onToggleShowSeconds: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
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
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
) {
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
                )
            }
        }
    }
}
