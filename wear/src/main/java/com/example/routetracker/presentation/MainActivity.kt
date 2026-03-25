package com.example.routetracker.presentation

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
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
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
private val BOARDING_PLATFORM_COLOR = Color(0xFF70D38A)
private val ACTIVITY_DELAY_COLOR = Color(0xFFF0C44C)
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
                    val showSecondsForSettings = latestShowSecondsEnabled.value
                    val autoRefreshForSettings = latestDetailsDialogAutoRefreshEnabled.value
                    val verifiedMatchesForSettings = latestVerifiedMatchCount.value
                    val catalogRefreshLabelForSettings = latestTransitCatalogLastRefreshLabel.value
                    val liveSnapshotCacheForSettings = latestLiveSnapshotCacheLabel.value
                    val gtfsTripDetailCacheForSettings = latestGtfsTripDetailCacheLabel.value
                    val vehiclePositionCacheForSettings = latestVehiclePositionCacheLabel.value
                    val apiKeySourceForSettings = apiKeySourceLabel

                    SettingsScreen(
                        showSecondsEnabled = showSecondsForSettings,
                        detailsDialogAutoRefreshEnabled = autoRefreshForSettings,
                        verifiedMatchCount = verifiedMatchesForSettings,
                        transitCatalogLastRefreshLabel = catalogRefreshLabelForSettings,
                        liveSnapshotCacheLabel = liveSnapshotCacheForSettings,
                        gtfsTripDetailCacheLabel = gtfsTripDetailCacheForSettings,
                        vehiclePositionCacheLabel = vehiclePositionCacheForSettings,
                        apiKeySourceLabel = apiKeySourceForSettings,
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
                        onValueChange = { newValue ->
                            apiKeyDraft = newValue
                        },
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
                    val selectionForQuickSwitch = latestSelection.value
                    val favoritesForQuickSwitch = latestFavoriteRoutes.value
                    QuickRouteSwitchScreen(
                        currentSelection = selectionForQuickSwitch,
                        favoriteRoutes = favoritesForQuickSwitch,
                        onSwapRoute = { selection ->
                            applyRouteSelectionFromUi(selection)
                        },
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
                            isRefreshing = latestIsRefreshing.value,
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
            Column(
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
internal fun SettingsScreen(
    showSecondsEnabled: Boolean,
    detailsDialogAutoRefreshEnabled: Boolean,
    verifiedMatchCount: Int,
    transitCatalogLastRefreshLabel: String,
    liveSnapshotCacheLabel: String,
    gtfsTripDetailCacheLabel: String,
    vehiclePositionCacheLabel: String,
    apiKeySourceLabel: String,
    onToggleShowSeconds: () -> Unit,
    onToggleDetailsDialogAutoRefresh: () -> Unit,
    onDecreaseVerifiedMatchCount: () -> Unit,
    onIncreaseVerifiedMatchCount: () -> Unit,
    onRefreshTransitCatalog: () -> Unit,
    onCycleLiveSnapshotCache: () -> Unit,
    onCycleGtfsTripDetailCache: () -> Unit,
    onCycleVehiclePositionCache: () -> Unit,
    onEditApiKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()

    RouteTrackerListScreen(state = listState) { transformationSpec ->
        item(key = "settings_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Settings")
            }
        }
        item(key = "settings_show_seconds") {
            SwitchButton(
                checked = showSecondsEnabled,
                onCheckedChange = { onToggleShowSeconds() },
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                label = { Text("Show seconds") },
                secondaryLabel = { Text("Use second-by-second countdowns") },
            )
        }
        item(key = "settings_auto_refresh") {
            SwitchButton(
                checked = detailsDialogAutoRefreshEnabled,
                onCheckedChange = { onToggleDetailsDialogAutoRefresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                label = { Text("Details auto-refresh") },
                secondaryLabel = {
                    Text(if (detailsDialogAutoRefreshEnabled) "Refresh open trip details every 10 seconds" else "Only refresh details when requested")
                },
            )
        }
        item(key = "settings_live_query_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Live query")
            }
        }
        item(key = "settings_api_key") {
            Card(
                onClick = onEditApiKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_API_KEY_BUTTON)
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Text(
                    text = "API key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = apiKeySourceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        item(key = "settings_catalog_refresh") {
            Button(
                onClick = onRefreshTransitCatalog,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Refresh stop catalog")
            }
        }
        item(key = "settings_catalog_status") {
            DetailValueRow(
                label = "Catalog synced",
                value = transitCatalogLastRefreshLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "settings_verified_matches") {
            VerifiedMatchCard(
                verifiedMatchCount = verifiedMatchCount,
                onDecreaseVerifiedMatchCount = onDecreaseVerifiedMatchCount,
                onIncreaseVerifiedMatchCount = onIncreaseVerifiedMatchCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "settings_cache_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Cache")
            }
        }
        item(key = "settings_live_snapshot_cache") {
            Button(
                onClick = onCycleLiveSnapshotCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Live snapshot: $liveSnapshotCacheLabel")
            }
        }
        item(key = "settings_trip_detail_cache") {
            Button(
                onClick = onCycleGtfsTripDetailCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Trip detail: $gtfsTripDetailCacheLabel")
            }
        }
        item(key = "settings_vehicle_cache") {
            Button(
                onClick = onCycleVehiclePositionCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Vehicle live: $vehiclePositionCacheLabel")
            }
        }
    }
}

@Composable
internal fun ApiKeySettingsScreen(
    value: String,
    sourceLabel: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onUseBuiltIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()

    RouteTrackerListScreen(
        state = listState,
        timeText = {},
    ) { transformationSpec ->
        item(key = "api_key_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Golemio API key")
            }
        }
        item(key = "api_key_notice") {
            EmptyStateCard(
                message = "Advanced setting. Watch override replaces the built-in key for this device.",
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "api_key_source") {
            DetailValueRow(
                label = "Current source",
                value = sourceLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "api_key_input") {
            ApiKeyInputCard(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "api_key_save") {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_API_KEY_SAVE_BUTTON)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Save key")
            }
        }
        item(key = "api_key_clear") {
            Button(
                onClick = onUseBuiltIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_API_KEY_CLEAR_BUTTON)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Use built-in key")
            }
        }
    }
}

@Composable
private fun ApiKeyInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    PanelSurface(modifier = modifier) {
        Text(
            text = "Paste key",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            factory = { context ->
                EditText(context).apply {
                    setSingleLine(true)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    background = null
                    setPadding(0, 0, 0, 0)
                    setTextColor(textColor)
                    setHintTextColor(hintColor)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    hint = "Paste API key"
                    inputType =
                        InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) = Unit

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) = Unit

                            override fun afterTextChanged(s: Editable?) {
                                onValueChange(s?.toString().orEmpty())
                            }
                        },
                    )
                }
            },
            update = { editText ->
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(editText.text.length)
                }
            },
        )
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
internal fun DepartureDetailsScreen(
    selection: RouteSelection,
    departure: RouteDeparture,
    routeRepo: RouteRepository,
    currentSystemTime: ZonedDateTime,
    showSecondsEnabled: Boolean,
    isRefreshing: Boolean,
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
                selection = selection,
                departure = departure,
                currentSystemTime = currentSystemTime,
                showSecondsEnabled = showSecondsEnabled,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                transformationSpec = transformationSpec,
            )
        }
        item(key = "trip_details_platform") {
            DetailValueRow(
                label = "Boarding platform",
                value = departure.boardingPlatformCompactLabel ?: "--",
                valueColor = BOARDING_PLATFORM_COLOR,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_arrival") {
            DetailValueRow(
                label = "Destination arrival",
                value = routeRepo.formatDetailTime(departure.destinationArrivalTime),
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_line") {
            DetailValueRow(
                label = "Line",
                value = departure.lineLabel,
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
                value = routeRepo.formatDetailTime(departure.departureBoardDetails.departureTime.scheduledTime),
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_departure_predicted") {
            DetailValueRow(
                label = "Predicted",
                value = routeRepo.formatDetailTime(departure.departureBoardDetails.departureTime.predictedTime),
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_delay") {
            DetailValueRow(
                label = "Delay",
                value = routeRepo.formatDelaySeconds(departure.departureBoardDetails.delaySeconds),
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_origin_arrival_scheduled") {
            DetailValueRow(
                label = "Origin arrival scheduled",
                value = routeRepo.formatDetailTime(departure.departureBoardDetails.originArrivalTime?.scheduledTime),
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "trip_details_origin_arrival_predicted") {
            DetailValueRow(
                label = "Origin arrival predicted",
                value = routeRepo.formatDetailTime(departure.departureBoardDetails.originArrivalTime?.predictedTime),
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
            departure.vehiclePositionDetails?.let { vehicleDetails ->
                DetailValueRow(
                    label = "Delay",
                    value = routeRepo.formatDelaySeconds(vehicleDetails.delaySeconds),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            } ?: DetailValueRow(
                label = "Status",
                value = "Not available",
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        departure.vehiclePositionDetails?.let { vehicleDetails ->
            item(key = "trip_details_vehicle_origin_timestamp") {
                DetailValueRow(
                    label = "Origin timestamp",
                    value = routeRepo.formatDetailTime(vehicleDetails.originTimestamp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            }
        }
    }
}

@Composable
private fun TransformingLazyColumnItemScope.DepartureDetailsSummaryCard(
    selection: RouteSelection,
    departure: RouteDeparture,
    currentSystemTime: ZonedDateTime,
    showSecondsEnabled: Boolean,
    isRefreshing: Boolean,
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
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = departure.clockLabel(
                        showSeconds = showSecondsEnabled,
                        includeLine = !selection.usesFixedLine(),
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = departure.activityStatusLabel(
                        referenceNow = currentSystemTime,
                        showSeconds = showSecondsEnabled,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (isRefreshing) "Refreshing live detail..." else "Tap refresh to update live detail.",
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

@Composable
private fun DetailValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    PanelSurface(modifier = modifier) {
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
private fun EmptyStateCard(
    message: String,
    modifier: Modifier = Modifier,
) {
    PanelSurface(modifier = modifier) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun VerifiedMatchCard(
    verifiedMatchCount: Int,
    onDecreaseVerifiedMatchCount: () -> Unit,
    onIncreaseVerifiedMatchCount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = "Verified matches",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = verifiedMatchCount.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = "Tune exact direction matching when live data is ambiguous.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onDecreaseVerifiedMatchCount,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text("-1")
            }
            Button(
                onClick = onIncreaseVerifiedMatchCount,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Text("+1")
            }
        }
    }
}

@Composable
private fun PanelSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(
                color = containerColor,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DefaultPreview() {
    val previewSnapshot = remember { RouteRepository.previewSnapshot() }

    RouteTrackerTheme {
        RouteTrackerAppScaffold {
            BoardScreen(
                selection = previewSnapshot.selection,
                departures = previewSnapshot.departures,
                snapshot = previewSnapshot,
                statusText = snapshotStatusText(
                    snapshot = previewSnapshot,
                    autoUpdatesEnabled = true,
                    isRefreshing = false,
                    updatedLabel = previewSnapshot.fetchedAt.format(PREVIEW_UPDATE_TIME_FORMATTER),
                ),
                currentSystemTime = previewSnapshot.fetchedAt,
                showSecondsEnabled = false,
                autoUpdatesEnabled = true,
                isRefreshing = false,
                onOpenSettings = {},
                onToggleAutoUpdates = {},
                onOpenQuickRouteSwitch = {},
                onOpenDepartureDetails = {},
                onRefresh = {},
            )
        }
    }
}
