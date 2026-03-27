package com.example.routetracker.presentation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.NavController
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.example.routetracker.data.DepartureSnapshot
import com.example.routetracker.data.RouteDeparture
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "RouteTrackerUi"
private const val BOARD_ROUTE = "board"
private const val SETTINGS_ROUTE = "settings"
private const val SETTINGS_API_KEY_ROUTE = "settings_api_key"
private const val QUICK_SWITCH_ROUTE = "quick_switch"
private const val ROUTE_SETUP_ROUTE = "route_setup"
private const val TRIP_DETAILS_ROUTE = "trip_details"

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
    var apiKeySourceLabel by remember { mutableStateOf(routeRepo.getApiKeySourceLabel()) }
    var apiKeyDraft by remember { mutableStateOf(routeRepo.getApiKeyOverride()) }
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
        apiKeySourceLabel = routeRepo.getApiKeySourceLabel()
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
        loadSnapshot(forceRefresh = true, requestSurfaceRefresh = true)
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
        loadSnapshot(forceRefresh = newValue, requestSurfaceRefresh = newValue)
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

    suspend fun saveApiKeyOverride(value: String) {
        Log.d(TAG, "Saving Golemio API key override from settings.")
        withContext(Dispatchers.IO) {
            routeRepo.setApiKeyOverride(value)
        }
        apiKeyDraft = routeRepo.getApiKeyOverride()
        refreshSettingsState()
        loadSnapshot(forceRefresh = true, requestSurfaceRefresh = true)
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

    LaunchedEffect(Unit) {
        currentSystemTime = ZonedDateTime.now()
        while (true) {
            val nowMillis = System.currentTimeMillis()
            val nextTickMillis = 1_000L - (nowMillis % 1_000L)
            delay(if (nextTickMillis == 0L) 1L else nextTickMillis)
            currentSystemTime = ZonedDateTime.now()
        }
    }

    RouteTrackerTheme {
        RouteTrackerAppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = BOARD_ROUTE,
                modifier = Modifier.fillMaxSize(),
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
                            Log.d(TAG, "Change route tapped.")
                            navController.navigate(QUICK_SWITCH_ROUTE)
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
                    SettingsScreen(
                        showSecondsEnabled = latestShowSecondsEnabled.value,
                        detailsDialogAutoRefreshEnabled = latestDetailsDialogAutoRefreshEnabled.value,
                        verifiedMatchCount = latestVerifiedMatchCount.value,
                        transitCatalogLastRefreshLabel = latestTransitCatalogLastRefreshLabel.value,
                        liveSnapshotCacheLabel = latestLiveSnapshotCacheLabel.value,
                        gtfsTripDetailCacheLabel = latestGtfsTripDetailCacheLabel.value,
                        vehiclePositionCacheLabel = latestVehiclePositionCacheLabel.value,
                        apiKeySourceLabel = apiKeySourceLabel,
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
                        onEditApiKey = {
                            apiKeyDraft = routeRepo.getApiKeyOverride()
                            navController.navigate(SETTINGS_API_KEY_ROUTE)
                        },
                        onDismiss = {
                            navController.popBackStack()
                        },
                    )
                }

                composable(SETTINGS_API_KEY_ROUTE) {
                    ApiKeySettingsScreen(
                        value = apiKeyDraft,
                        sourceLabel = apiKeySourceLabel,
                        onValueChange = { apiKeyDraft = it },
                        onSave = {
                            coroutineScope.launch {
                                saveApiKeyOverride(apiKeyDraft)
                                navController.popBackStack()
                            }
                        },
                        onUseBuiltIn = {
                            coroutineScope.launch {
                                saveApiKeyOverride("")
                                navController.popBackStack()
                            }
                        },
                        onDismiss = {
                            navController.popBackStack()
                        },
                    )
                }

                composable(QUICK_SWITCH_ROUTE) {
                    QuickRouteSwitchScreen(
                        currentSelection = latestSelection.value,
                        favoriteRoutes = latestFavoriteRoutes.value,
                        onSwapRoute = { applyRouteSelectionFromUi(it) },
                        onApplyFavorite = { applyRouteSelectionFromUi(it) },
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
                    RouteSetupScreen(
                        routeRepo = routeRepo,
                        currentSelection = selectionForRouteSetup,
                        favoriteRoutes = latestFavoriteRoutes.value,
                        editingFavoriteStableKey = latestRouteSetupEditingFavoriteStableKey.value,
                        onApplySelection = { applyRouteSelectionFromUi(it) },
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
                        val detailsUiState = routeRepo.buildDepartureDetailsUiState(
                            selection = latestSelection.value,
                            departure = departure,
                            currentSystemTime = latestCurrentSystemTime.value,
                            showSecondsEnabled = latestShowSecondsEnabled.value,
                            isRefreshing = latestIsRefreshing.value,
                        )
                        DepartureDetailsScreen(
                            uiState = detailsUiState,
                            onRefresh = {
                                coroutineScope.launch {
                                    loadSnapshot(forceRefresh = true, requestSurfaceRefresh = true)
                                }
                            },
                            onDismiss = {
                                dismissTripDetails()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun millisUntilNextHalfMinute(nowMillis: Long = System.currentTimeMillis()): Long {
    val remainder = nowMillis % BOARD_REFRESH_INTERVAL_MILLIS
    return if (remainder == 0L) {
        BOARD_REFRESH_INTERVAL_MILLIS
    } else {
        BOARD_REFRESH_INTERVAL_MILLIS - remainder
    }
}
