package com.example.routetracker.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.routetracker.data.DepartureSnapshot
import com.example.routetracker.data.RouteDeparture
import com.example.routetracker.data.RouteDirection
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "RouteTrackerUi"
private val UPDATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
        loadSnapshot(forceRefresh = true, requestSurfaceRefresh = false)
    }

    LaunchedEffect(routeRepo) {
        loadSnapshot(forceRefresh = false, requestSurfaceRefresh = false)
        while (true) {
            delay(30_000L)
            snapshot = withContext(Dispatchers.IO) {
                routeRepo.getDepartureSnapshot(forceRefresh = true)
            }
            selectedDirection = snapshot?.direction ?: routeRepo.getSelectedDirection()
        }
    }

    val departures = snapshot?.departures.orEmpty()
    val statusText = snapshotStatusText(
        snapshot = snapshot,
        isRefreshing = isRefreshing,
        hasDepartures = departures.isNotEmpty(),
    )

    RouteTrackerTheme {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            state = rememberScalingLazyListState(),
        ) {
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
                    DepartureRow(departures[index])
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
            text = departure.clockLabel,
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

private fun snapshotStatusText(
    snapshot: DepartureSnapshot?,
    isRefreshing: Boolean,
    hasDepartures: Boolean,
): String {
    if (snapshot == null || (isRefreshing && !hasDepartures)) {
        return "Loading"
    }
    if (snapshot.errorMessage != null && !hasDepartures) {
        return snapshot.errorMessage
    }

    val freshnessLabel = if (snapshot.isStale) "Cached" else "Live"
    val updatedLabel = snapshot.fetchedAt.format(UPDATE_TIME_FORMATTER)
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
    val previewSnapshot = remember { RouteRepository.previewSnapshot() }

    RouteTrackerTheme {
        ScalingLazyColumn(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            item {
                HeaderCard(
                    direction = previewSnapshot.direction,
                    statusText = snapshotStatusText(
                        snapshot = previewSnapshot,
                        isRefreshing = false,
                        hasDepartures = previewSnapshot.departures.isNotEmpty(),
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
                DepartureRow(previewSnapshot.departures[index])
            }
        }
    }
}
