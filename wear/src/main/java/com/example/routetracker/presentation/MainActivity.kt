package com.example.routetracker.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.example.routetracker.data.DepartureSnapshot
import com.example.routetracker.data.RouteDeparture
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "RouteTrackerUi"
private const val MAIN_SCREEN_INDEX = 3
private const val INITIAL_ACTIVITY_CENTER_INDEX = MAIN_SCREEN_INDEX + 1
private const val HALF_MINUTE_MILLIS = 30_000L
private const val BOARD_ROUTE = "board"
private const val SETTINGS_ROUTE = "settings"
private const val QUICK_SWITCH_ROUTE = "quick_switch"
private const val ROUTE_SETUP_ROUTE = "route_setup"
private const val TRIP_DETAILS_ROUTE = "trip_details"
private val BOARDING_PLATFORM_COLOR = Color(0xFF70D38A)
private val ACTIVITY_DELAY_COLOR = Color(0xFFF0C44C)
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
    var currentSelection by remember { mutableStateOf(routeRepo.getCurrentRouteSelection()) }
    var favoriteRoutes by remember { mutableStateOf(routeRepo.getFavoriteRoutes()) }
    var autoUpdatesEnabled by remember { mutableStateOf(routeRepo.getAutoUpdatesEnabled()) }
    var showSecondsEnabled by remember { mutableStateOf(routeRepo.getShowSecondsEnabled()) }
    var detailsDialogAutoRefreshEnabled by remember { mutableStateOf(routeRepo.getDetailsDialogAutoRefreshEnabled()) }
    var verifiedMatchCount by remember { mutableStateOf(routeRepo.getVerifiedMatchCount()) }
    var transitCatalogLastRefreshLabel by remember { mutableStateOf(routeRepo.getTransitCatalogLastRefreshLabel()) }
    var liveSnapshotCacheLabel by remember { mutableStateOf(routeRepo.getLiveSnapshotCacheLabel()) }
    var gtfsTripDetailCacheLabel by remember { mutableStateOf(routeRepo.getGtfsTripDetailCacheLabel()) }
    var vehiclePositionCacheLabel by remember { mutableStateOf(routeRepo.getVehiclePositionCacheLabel()) }
    var routeSetupSeedSelection by remember { mutableStateOf<RouteSelection?>(null) }
    var routeSetupEditingFavoriteStableKey by remember { mutableStateOf<String?>(null) }
    var selectedDeparture by remember { mutableStateOf<RouteDeparture?>(null) }
    var snapshot by remember { mutableStateOf<DepartureSnapshot?>(null) }
    var isRefreshing by remember { mutableStateOf(true) }
    var currentSystemTime by remember { mutableStateOf(ZonedDateTime.now()) }
    val coroutineScope = rememberCoroutineScope()
    val navController = rememberSwipeDismissableNavController()
    var currentScreenRoute by remember { mutableStateOf(BOARD_ROUTE) }
    val latestSelection = rememberUpdatedState(currentSelection)
    val latestFavoriteRoutes = rememberUpdatedState(favoriteRoutes)
    val latestAutoUpdatesEnabled = rememberUpdatedState(autoUpdatesEnabled)
    val latestShowSecondsEnabled = rememberUpdatedState(showSecondsEnabled)
    val latestDetailsDialogAutoRefreshEnabled = rememberUpdatedState(detailsDialogAutoRefreshEnabled)
    val latestVerifiedMatchCount = rememberUpdatedState(verifiedMatchCount)
    val latestTransitCatalogLastRefreshLabel = rememberUpdatedState(transitCatalogLastRefreshLabel)
    val latestLiveSnapshotCacheLabel = rememberUpdatedState(liveSnapshotCacheLabel)
    val latestGtfsTripDetailCacheLabel = rememberUpdatedState(gtfsTripDetailCacheLabel)
    val latestVehiclePositionCacheLabel = rememberUpdatedState(vehiclePositionCacheLabel)
    val latestRouteSetupSeedSelection = rememberUpdatedState(routeSetupSeedSelection)
    val latestRouteSetupEditingFavoriteStableKey = rememberUpdatedState(routeSetupEditingFavoriteStableKey)
    val latestSelectedDeparture = rememberUpdatedState(selectedDeparture)
    val latestSnapshot = rememberUpdatedState(snapshot)
    val latestIsRefreshing = rememberUpdatedState(isRefreshing)
    val latestCurrentSystemTime = rememberUpdatedState(currentSystemTime)

    fun refreshSettingsState() {
        autoUpdatesEnabled = routeRepo.getAutoUpdatesEnabled()
        showSecondsEnabled = routeRepo.getShowSecondsEnabled()
        detailsDialogAutoRefreshEnabled = routeRepo.getDetailsDialogAutoRefreshEnabled()
        verifiedMatchCount = routeRepo.getVerifiedMatchCount()
        transitCatalogLastRefreshLabel = routeRepo.getTransitCatalogLastRefreshLabel()
        liveSnapshotCacheLabel = routeRepo.getLiveSnapshotCacheLabel()
        gtfsTripDetailCacheLabel = routeRepo.getGtfsTripDetailCacheLabel()
        vehiclePositionCacheLabel = routeRepo.getVehiclePositionCacheLabel()
    }

    fun refreshRouteState() {
        currentSelection = routeRepo.getCurrentRouteSelection()
        favoriteRoutes = routeRepo.getFavoriteRoutes()
    }

    fun applySnapshotState(newSnapshot: DepartureSnapshot?) {
        snapshot = newSnapshot
        currentSelection = newSnapshot?.selection ?: routeRepo.getCurrentRouteSelection()
        favoriteRoutes = routeRepo.getFavoriteRoutes()
        selectedDeparture = selectedDeparture?.let { current ->
            newSnapshot?.departures?.firstOrNull { it.tripId == current.tripId } ?: current
        }
        refreshSettingsState()
    }

    fun showPendingRouteSelection(selection: RouteSelection) {
        currentSelection = selection
        snapshot = null
        selectedDeparture = null
        isRefreshing = true
    }

    fun dismissTripDetails() {
        selectedDeparture = null
        val activeRoute = navController.currentBackStackEntry?.destination?.route ?: currentScreenRoute
        if (activeRoute != TRIP_DETAILS_ROUTE) {
            return
        }

        navController.navigate(BOARD_ROUTE) {
            launchSingleTop = true
            popUpTo(BOARD_ROUTE) {
                inclusive = false
            }
        }
    }

    fun returnToBoard() {
        navController.navigate(BOARD_ROUTE) {
            launchSingleTop = true
            popUpTo(BOARD_ROUTE) {
                inclusive = false
            }
        }
    }

    fun openRouteSetup(
        seedSelection: RouteSelection? = null,
        editingFavoriteStableKey: String? = null,
    ) {
        routeSetupSeedSelection = seedSelection
        routeSetupEditingFavoriteStableKey = editingFavoriteStableKey
        navController.navigate(ROUTE_SETUP_ROUTE)
    }

    fun clearRouteSetupContext() {
        routeSetupSeedSelection = null
        routeSetupEditingFavoriteStableKey = null
    }

    suspend fun loadSnapshot(forceRefresh: Boolean, requestSurfaceRefresh: Boolean) {
        isRefreshing = true
        try {
            Log.d(
                TAG,
                "Loading snapshot. forceRefresh=$forceRefresh requestSurfaceRefresh=$requestSurfaceRefresh route=${currentSelection.routeSummaryWithPlatforms}",
            )
            val loadedSnapshot = withContext(Dispatchers.IO) {
                if (requestSurfaceRefresh) {
                    routeRepo.refreshDepartureSnapshot()
                } else {
                    routeRepo.getDepartureSnapshot(forceRefresh = forceRefresh)
                }
            }
            applySnapshotState(loadedSnapshot)
            Log.d(TAG, "Loaded snapshot. ${loadedSnapshot.debugSummary()}")
        } catch (error: Exception) {
            Log.e(TAG, "Snapshot load failed.", error)
        } finally {
            isRefreshing = false
        }
    }

    suspend fun applyRouteSelection(selection: RouteSelection) {
        Log.d(TAG, "Applying route selection ${selection.routeSummaryWithPlatforms}")
        withContext(Dispatchers.IO) {
            routeRepo.setCurrentRouteSelection(selection)
        }
        refreshRouteState()
        loadSnapshot(
            forceRefresh = true,
            requestSurfaceRefresh = true,
        )
    }

    fun applyRouteSelectionFromUi(selection: RouteSelection) {
        coroutineScope.launch {
            showPendingRouteSelection(selection)
            returnToBoard()
            try {
                applyRouteSelection(selection)
            } catch (error: Exception) {
                Log.e(
                    TAG,
                    "Failed to apply route selection ${selection.routeSummaryWithPlatforms}.",
                    error,
                )
                refreshRouteState()
                isRefreshing = false
            }
        }
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

    suspend fun adjustVerifiedMatchCount(delta: Int) {
        Log.d(TAG, "Adjusting verified match count by $delta.")
        withContext(Dispatchers.IO) {
            routeRepo.adjustVerifiedMatchCount(delta)
        }
        refreshSettingsState()
        loadSnapshot(forceRefresh = true, requestSurfaceRefresh = true)
    }

    suspend fun refreshTransitCatalog() {
        Log.d(TAG, "Refreshing transit catalog from settings.")
        withContext(Dispatchers.IO) {
            routeRepo.refreshTransitCatalog()
        }
        refreshSettingsState()
    }

    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            currentScreenRoute = destination.route ?: BOARD_ROUTE
            if (currentScreenRoute != TRIP_DETAILS_ROUTE) {
                selectedDeparture = null
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
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

    LaunchedEffect(
        routeRepo,
        selectedDeparture?.tripId,
        detailsDialogAutoRefreshEnabled,
        autoUpdatesEnabled,
        currentScreenRoute,
    ) {
        val trackedTripId = selectedDeparture?.tripId ?: return@LaunchedEffect
        if (
            currentScreenRoute != TRIP_DETAILS_ROUTE ||
            !detailsDialogAutoRefreshEnabled ||
            !autoUpdatesEnabled
        ) {
            return@LaunchedEffect
        }

        while (
            selectedDeparture?.tripId == trackedTripId &&
            currentScreenRoute == TRIP_DETAILS_ROUTE &&
            detailsDialogAutoRefreshEnabled &&
            autoUpdatesEnabled
        ) {
            delay(RouteRepository.DETAILS_DIALOG_REFRESH_INTERVAL_MILLIS)
            if (
                selectedDeparture?.tripId != trackedTripId ||
                currentScreenRoute != TRIP_DETAILS_ROUTE ||
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

    LaunchedEffect(showSecondsEnabled) {
        currentSystemTime = ZonedDateTime.now()
        if (!showSecondsEnabled) {
            return@LaunchedEffect
        }

        while (true) {
            val nowMillis = System.currentTimeMillis()
            val nextTickMillis = 1_000L - (nowMillis % 1_000L)
            delay(if (nextTickMillis == 0L) 1L else nextTickMillis)
            currentSystemTime = ZonedDateTime.now()
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
            // Keep these surfaces as real Wear navigation destinations so swipe-dismiss
            // returns to the live board instead of finishing the activity.
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = BOARD_ROUTE,
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                composable(BOARD_ROUTE) {
                    val selectionForBoard = latestSelection.value
                    val snapshotForBoard = latestSnapshot.value
                    val isRefreshingForBoard = latestIsRefreshing.value
                    val currentSystemTimeForBoard = latestCurrentSystemTime.value
                    val showSecondsForBoard = latestShowSecondsEnabled.value
                    val autoUpdatesForBoard = latestAutoUpdatesEnabled.value
                    val departuresForBoard = snapshotForBoard?.departures.orEmpty()
                    val updatedLabelForBoard = snapshotForBoard?.fetchedAt?.let { routeRepo.formatStatusTime(it) }
                    val statusTextForBoard = snapshotStatusText(
                        snapshot = snapshotForBoard,
                        autoUpdatesEnabled = autoUpdatesForBoard,
                        isRefreshing = isRefreshingForBoard,
                        hasDepartures = departuresForBoard.isNotEmpty(),
                        updatedLabel = updatedLabelForBoard,
                    )

                    LaunchedEffect(snapshotForBoard, isRefreshingForBoard) {
                        if (snapshotForBoard == null && !isRefreshingForBoard) {
                            loadSnapshot(forceRefresh = false, requestSurfaceRefresh = false)
                        }
                    }
                    BoardScreen(
                        selection = selectionForBoard,
                        departures = departuresForBoard,
                        snapshot = snapshotForBoard,
                        statusText = statusTextForBoard,
                        routeRepo = routeRepo,
                        currentSystemTime = currentSystemTimeForBoard,
                        showSecondsEnabled = showSecondsForBoard,
                        autoUpdatesEnabled = autoUpdatesForBoard,
                        isRefreshing = isRefreshingForBoard,
                        onOpenSettings = {
                            navController.navigate(SETTINGS_ROUTE)
                        },
                        onToggleAutoUpdates = {
                            coroutineScope.launch {
                                toggleAutoUpdates()
                            }
                        },
                        onOpenQuickRouteSwitch = {
                            Log.d(TAG, "Header route launcher tapped.")
                            navController.navigate(QUICK_SWITCH_ROUTE)
                        },
                        onOpenRouteSetup = {
                            Log.d(TAG, "Header route launcher long-pressed.")
                            openRouteSetup()
                        },
                        onOpenDepartureDetails = { departure ->
                            selectedDeparture = departure
                            navController.navigate(TRIP_DETAILS_ROUTE)
                        },
                        onRefresh = {
                            Log.d(TAG, "Refresh button tapped.")
                            coroutineScope.launch {
                                loadSnapshot(forceRefresh = true, requestSurfaceRefresh = true)
                            }
                        },
                    )
                }

                composable(SETTINGS_ROUTE) {
                    val showSecondsForSettings = latestShowSecondsEnabled.value
                    val autoRefreshForSettings = latestDetailsDialogAutoRefreshEnabled.value
                    val verifiedMatchesForSettings = latestVerifiedMatchCount.value
                    val catalogRefreshLabelForSettings = latestTransitCatalogLastRefreshLabel.value
                    val liveSnapshotCacheForSettings = latestLiveSnapshotCacheLabel.value
                    val gtfsTripDetailCacheForSettings = latestGtfsTripDetailCacheLabel.value
                    val vehiclePositionCacheForSettings = latestVehiclePositionCacheLabel.value

                    SettingsScreen(
                        showSecondsEnabled = showSecondsForSettings,
                        detailsDialogAutoRefreshEnabled = autoRefreshForSettings,
                        verifiedMatchCount = verifiedMatchesForSettings,
                        transitCatalogLastRefreshLabel = catalogRefreshLabelForSettings,
                        liveSnapshotCacheLabel = liveSnapshotCacheForSettings,
                        gtfsTripDetailCacheLabel = gtfsTripDetailCacheForSettings,
                        vehiclePositionCacheLabel = vehiclePositionCacheForSettings,
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
                        onDecreaseVerifiedMatchCount = {
                            coroutineScope.launch {
                                adjustVerifiedMatchCount(-1)
                            }
                        },
                        onIncreaseVerifiedMatchCount = {
                            coroutineScope.launch {
                                adjustVerifiedMatchCount(1)
                            }
                        },
                        onRefreshTransitCatalog = {
                            coroutineScope.launch {
                                refreshTransitCatalog()
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
                            navController.popBackStack()
                        },
                    )
                }

                composable(QUICK_SWITCH_ROUTE) {
                    val selectionForQuickSwitch = latestSelection.value
                    val favoritesForQuickSwitch = latestFavoriteRoutes.value
                    QuickRouteSwitchScreen(
                        currentSelection = selectionForQuickSwitch,
                        favoriteRoutes = favoritesForQuickSwitch,
                        onApplyFavorite = { selection ->
                            applyRouteSelectionFromUi(selection)
                        },
                        onEditFavorite = { selection ->
                            openRouteSetup(
                                seedSelection = selection,
                                editingFavoriteStableKey = selection.stableKey,
                            )
                        },
                        onDeleteFavorite = { selection ->
                            coroutineScope.launch {
                                withContext(Dispatchers.IO) {
                                    routeRepo.removeFavoriteRoute(selection.stableKey)
                                }
                                refreshRouteState()
                            }
                        },
                        onOpenRouteSetup = {
                            clearRouteSetupContext()
                            navController.navigate(ROUTE_SETUP_ROUTE) {
                                popUpTo(QUICK_SWITCH_ROUTE) {
                                    inclusive = true
                                }
                            }
                        },
                    )
                }

                composable(ROUTE_SETUP_ROUTE) {
                    DisposableEffect(Unit) {
                        onDispose {
                            refreshRouteState()
                            clearRouteSetupContext()
                        }
                    }
                    val selectionForRouteSetup = latestRouteSetupSeedSelection.value ?: latestSelection.value
                    val favoritesForRouteSetup = latestFavoriteRoutes.value
                    val editingFavoriteStableKeyForRouteSetup = latestRouteSetupEditingFavoriteStableKey.value
                    RouteSetupScreen(
                        routeRepo = routeRepo,
                        currentSelection = selectionForRouteSetup,
                        favoriteRoutes = favoritesForRouteSetup,
                        editingFavoriteStableKey = editingFavoriteStableKeyForRouteSetup,
                        onApplySelection = { selection ->
                            applyRouteSelectionFromUi(selection)
                        },
                        onDismiss = {
                            refreshRouteState()
                            navController.popBackStack()
                        },
                    )
                }

                composable(TRIP_DETAILS_ROUTE) {
                    val departure = latestSelectedDeparture.value
                    if (departure == null) {
                        LaunchedEffect(Unit) {
                            dismissTripDetails()
                        }
                    } else {
                        val selectionForDetails = latestSelection.value
                        val currentSystemTimeForDetails = latestCurrentSystemTime.value
                        val showSecondsForDetails = latestShowSecondsEnabled.value
                        DepartureDetailsScreen(
                            selection = selectionForDetails,
                            departure = departure,
                            routeRepo = routeRepo,
                            currentSystemTime = currentSystemTimeForDetails,
                            showSecondsEnabled = showSecondsForDetails,
                            onDismiss = {
                                dismissTripDetails()
                            },
                        )
                    }
                }
            }

            ActivityClockChip(
                showSeconds = showSecondsEnabled,
                modifier = Modifier
                    .fillMaxSize(),
            )
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
private fun BoardScreen(
    selection: RouteSelection,
    departures: List<RouteDeparture>,
    snapshot: DepartureSnapshot?,
    statusText: String,
    routeRepo: RouteRepository,
    currentSystemTime: ZonedDateTime,
    showSecondsEnabled: Boolean,
    autoUpdatesEnabled: Boolean,
    isRefreshing: Boolean,
    onOpenSettings: () -> Unit,
    onToggleAutoUpdates: () -> Unit,
    onOpenQuickRouteSwitch: () -> Unit,
    onOpenRouteSetup: () -> Unit,
    onOpenDepartureDetails: (RouteDeparture) -> Unit,
    onRefresh: () -> Unit,
) {
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = INITIAL_ACTIVITY_CENTER_INDEX,
    )
    val emptyStateMessage = when {
        snapshot == null && isRefreshing -> "Loading live departures..."
        snapshot?.errorMessage != null -> snapshot.errorMessage
        else -> "No direct departures right now."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
        ) {
            item {
                SettingsLauncherButton(
                    onOpenSettings = onOpenSettings,
                )
            }

            item {
                AutoUpdatesCard(
                    autoUpdatesEnabled = autoUpdatesEnabled,
                    onToggleAutoUpdates = onToggleAutoUpdates,
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {}
            }

            item {
                HeaderCard(
                    selection = selection,
                    statusText = statusText,
                    onOpenQuickRouteSwitch = onOpenQuickRouteSwitch,
                    onOpenRouteSetup = onOpenRouteSetup,
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
                        routeRepo = routeRepo,
                        currentSystemTime = currentSystemTime,
                        showSecondsEnabled = showSecondsEnabled,
                        onClick = {
                            onOpenDepartureDetails(departure)
                        },
                    )
                }
            } else {
                item {
                    EmptyStateCard(emptyStateMessage)
                }
            }

            item {
                Button(
                    onClick = onRefresh,
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
private fun SettingsScreen(
    showSecondsEnabled: Boolean,
    detailsDialogAutoRefreshEnabled: Boolean,
    verifiedMatchCount: Int,
    transitCatalogLastRefreshLabel: String,
    liveSnapshotCacheLabel: String,
    gtfsTripDetailCacheLabel: String,
    vehiclePositionCacheLabel: String,
    onToggleShowSeconds: () -> Unit,
    onToggleDetailsDialogAutoRefresh: () -> Unit,
    onDecreaseVerifiedMatchCount: () -> Unit,
    onIncreaseVerifiedMatchCount: () -> Unit,
    onRefreshTransitCatalog: () -> Unit,
    onCycleLiveSnapshotCache: () -> Unit,
    onCycleGtfsTripDetailCache: () -> Unit,
    onCycleVehiclePositionCache: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                text = "Live query",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRefreshTransitCatalog,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("Refresh stop catalog")
            }
            Text(
                text = "Catalog: $transitCatalogLastRefreshLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onDecreaseVerifiedMatchCount,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("-")
                }
                Text(
                    text = "Verified matches: $verifiedMatchCount",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(2f),
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onIncreaseVerifiedMatchCount,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("+")
                }
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
    selection: RouteSelection,
    statusText: String,
    onOpenQuickRouteSwitch: () -> Unit,
    onOpenRouteSetup: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(24.dp),
            )
            .combinedClickable(
                onClick = onOpenQuickRouteSwitch,
                onLongClick = onOpenRouteSetup,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = selection.headerLineLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = selection.headerRouteSummaryLabel,
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
        Text(
            text = "Tap favorites  |  Hold full setup",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DepartureRow(
    selection: RouteSelection,
    departure: RouteDeparture,
    routeRepo: RouteRepository,
    currentSystemTime: ZonedDateTime,
    showSecondsEnabled: Boolean,
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
            if (selection.origin.platformKey == null) {
                departure.boardingPlatformCompactLabel?.let { boardingPlatformLabel ->
                    Text(
                        text = boardingPlatformLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = BOARDING_PLATFORM_COLOR,
                        textAlign = TextAlign.End,
                    )
                }
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
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun DepartureDetailsScreen(
    selection: RouteSelection,
    departure: RouteDeparture,
    routeRepo: RouteRepository,
    currentSystemTime: ZonedDateTime,
    showSecondsEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = departure.clockLabel(
                    showSeconds = showSecondsEnabled,
                    includeLine = !selection.usesFixedLine(),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = departure.activityStatusLabel(
                    referenceNow = currentSystemTime,
                    showSeconds = showSecondsEnabled,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
            DetailValueRow(
                label = "Line",
                value = departure.lineLabel,
            )
            DetailValueRow(
                label = "Boarding platform",
                value = departure.boardingPlatformCompactLabel ?: "--",
                valueColor = BOARDING_PLATFORM_COLOR,
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
    valueColor: Color? = null,
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
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
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
        BoardScreen(
            selection = previewSnapshot.selection,
            departures = previewSnapshot.departures,
            snapshot = previewSnapshot,
            statusText = snapshotStatusText(
                snapshot = previewSnapshot,
                autoUpdatesEnabled = true,
                isRefreshing = false,
                hasDepartures = previewSnapshot.departures.isNotEmpty(),
                updatedLabel = previewSnapshot.fetchedAt.format(PREVIEW_UPDATE_TIME_FORMATTER),
            ),
            routeRepo = previewRepository,
            currentSystemTime = previewSnapshot.fetchedAt,
            showSecondsEnabled = false,
            autoUpdatesEnabled = true,
            isRefreshing = false,
            onOpenSettings = {},
            onToggleAutoUpdates = {},
            onOpenQuickRouteSwitch = {},
            onOpenRouteSetup = {},
            onOpenDepartureDetails = {},
            onRefresh = {},
        )
    }
}
