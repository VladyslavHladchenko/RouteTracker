package com.example.routetracker.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.routetracker.data.DepartureSnapshot
import com.example.routetracker.data.RouteDeparture
import com.example.routetracker.data.RouteDirection
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "RouteTrackerUi"

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
    val statusText = when {
        isRefreshing && departures.isEmpty() -> "Loading live departures..."
        departures.isEmpty() -> snapshot?.errorMessage ?: "No upcoming direct line 7 departures."
        snapshot?.isStale == true -> "Showing cached live data"
        else -> selectedDirection.buttonLabel
    }

    RouteTrackerTheme {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            state = rememberScalingLazyListState()
        ) {
            item {
                ListHeader {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            items(RouteDirection.entries.size) { index ->
                val direction = RouteDirection.entries[index]
                DirectionButton(
                    direction = direction,
                    isSelected = direction == selectedDirection,
                    onClick = {
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
            }

            item {
                Button(
                    onClick = {
                        Log.d(TAG, "Refresh button tapped.")
                        coroutineScope.launch {
                            loadSnapshot(forceRefresh = true, requestSurfaceRefresh = true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (isRefreshing) "Refreshing..." else "Refresh")
                }
            }
        }
    }
}

@Composable
private fun DirectionButton(
    direction: RouteDirection,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
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
        Text(direction.buttonLabel)
    }
}

@Composable
private fun DepartureRow(
    departure: RouteDeparture,
    routeRepo: RouteRepository,
) {
    Text(
        text = "${routeRepo.formatDepartureClockTime(departure)}  ${departure.compactLabel}",
        style = MaterialTheme.typography.bodyLarge,
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    val previewSnapshot = remember { RouteRepository.previewSnapshot() }

    RouteTrackerTheme {
        ScalingLazyColumn {
            item {
                ListHeader {
                    Text(previewSnapshot.direction.buttonLabel)
                }
            }
            items(RouteDirection.entries.size) { index ->
                Text(RouteDirection.entries[index].buttonLabel)
            }
            items(previewSnapshot.departures.size) { index ->
                Text(previewSnapshot.departures[index].compactLabel)
            }
        }
    }
}
