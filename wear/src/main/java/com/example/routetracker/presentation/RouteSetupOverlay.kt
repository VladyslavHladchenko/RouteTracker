package com.example.routetracker.presentation

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import com.example.routetracker.data.LineOption
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.data.StationOption
import com.example.routetracker.data.StopSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROUTE_SETUP_TAG = "RouteSetupUi"

private sealed interface RouteSetupPage {
    data object Home : RouteSetupPage
    data class StationSearch(val target: RouteEndpointTarget) : RouteSetupPage
    data class PlatformPicker(
        val target: RouteEndpointTarget,
        val station: StationOption,
    ) : RouteSetupPage

    data object LineSearch : RouteSetupPage
}

@Composable
internal fun RouteSetupScreen(
    routeRepo: RouteRepository,
    currentSelection: RouteSelection,
    favoriteRoutes: List<RouteSelection>,
    editingFavoriteStableKey: String? = null,
    onApplySelection: (RouteSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember(currentSelection.stableKey) { mutableStateOf<RouteSetupPage>(RouteSetupPage.Home) }
    var draftSelection by remember(currentSelection.stableKey) { mutableStateOf(currentSelection) }
    var localFavorites by remember(favoriteRoutes) { mutableStateOf(favoriteRoutes) }
    var editingFavoriteKey by remember(currentSelection.stableKey, editingFavoriteStableKey) {
        mutableStateOf(editingFavoriteStableKey)
    }
    var catalog by remember { mutableStateOf(routeRepo.peekTransitCatalogInMemory()) }
    var isCatalogLoading by remember { mutableStateOf(catalog == null) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var stationQuery by remember { mutableStateOf("") }
    var lineQuery by remember { mutableStateOf("") }
    var stationResults by remember { mutableStateOf<List<StationOption>>(emptyList()) }
    var lineResults by remember { mutableStateOf<List<LineOption>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberTransformingLazyColumnState()

    suspend fun loadCatalog(forceRefresh: Boolean) {
        isCatalogLoading = true
        if (catalog == null) {
            catalogError = null
        }
        try {
            catalog = withContext(Dispatchers.IO) {
                routeRepo.loadTransitCatalog(forceRefresh = forceRefresh)
            }
            catalogError = null
        } catch (error: Exception) {
            Log.w(ROUTE_SETUP_TAG, "Failed to load route setup catalog.", error)
            if (catalog == null) {
                catalogError = "Unable to load stops. Check network and retry."
            }
        } finally {
            isCatalogLoading = false
        }
    }

    LaunchedEffect(Unit) {
        val initialCatalog = routeRepo.peekTransitCatalogInMemory() ?: withContext(Dispatchers.IO) {
            routeRepo.getCachedTransitCatalog()
        }
        if (initialCatalog != null) {
            catalog = initialCatalog
            catalogError = null
            isCatalogLoading = false
            val refreshDue = withContext(Dispatchers.IO) {
                routeRepo.isTransitCatalogRefreshDue()
            }
            if (refreshDue) {
                loadCatalog(forceRefresh = true)
            }
        } else {
            loadCatalog(forceRefresh = false)
        }
    }

    LaunchedEffect(page, stationQuery, catalog) {
        val activeCatalog = catalog ?: run {
            stationResults = emptyList()
            return@LaunchedEffect
        }
        if (page !is RouteSetupPage.StationSearch) {
            stationResults = emptyList()
            return@LaunchedEffect
        }

        val normalizedQuery = stationQuery.trim()
        if (normalizedQuery.isBlank()) {
            stationResults = emptyList()
            return@LaunchedEffect
        }

        delay(120L)
        stationResults = withContext(Dispatchers.Default) {
            activeCatalog.searchStations(normalizedQuery)
        }
    }

    LaunchedEffect(page, lineQuery, catalog) {
        val activeCatalog = catalog ?: run {
            lineResults = emptyList()
            return@LaunchedEffect
        }
        if (page != RouteSetupPage.LineSearch) {
            lineResults = emptyList()
            return@LaunchedEffect
        }

        delay(120L)
        lineResults = withContext(Dispatchers.Default) {
            activeCatalog.searchLines(lineQuery.trim())
        }
    }

    LaunchedEffect(page) {
        listState.scrollToItem(0)
    }

    BackHandler(enabled = page != RouteSetupPage.Home) {
        page = when (val currentPage = page) {
            RouteSetupPage.Home -> RouteSetupPage.Home
            is RouteSetupPage.PlatformPicker -> RouteSetupPage.StationSearch(currentPage.target)
            is RouteSetupPage.StationSearch -> RouteSetupPage.Home
            RouteSetupPage.LineSearch -> RouteSetupPage.Home
        }
    }

    when (val currentPage = page) {
        RouteSetupPage.Home -> {
            RouteSetupHomePage(
                draftSelection = draftSelection,
                favoriteRoutes = localFavorites,
                isEditingFavorite = editingFavoriteKey != null,
                isCatalogLoading = isCatalogLoading,
                catalogError = catalogError,
                onChooseOrigin = {
                    stationQuery = ""
                    page = RouteSetupPage.StationSearch(RouteEndpointTarget.ORIGIN)
                },
                onChooseDestination = {
                    stationQuery = ""
                    page = RouteSetupPage.StationSearch(RouteEndpointTarget.DESTINATION)
                },
                onChooseLine = {
                    lineQuery = ""
                    page = RouteSetupPage.LineSearch
                },
                onToggleFavorite = {
                    coroutineScope.launch {
                        withContext(Dispatchers.IO) {
                            if (editingFavoriteKey != null) {
                                val savedSelection = routeRepo.updateFavoriteRoute(
                                    originalStableKey = editingFavoriteKey!!,
                                    selection = draftSelection,
                                )
                                editingFavoriteKey = savedSelection.stableKey
                            } else {
                                routeRepo.toggleFavoriteRoute(draftSelection)
                            }
                        }
                        localFavorites = routeRepo.getFavoriteRoutes()
                    }
                },
                onApplyFavorite = onApplySelection,
                onApplyRoute = { onApplySelection(draftSelection) },
                onRetryCatalog = {
                    coroutineScope.launch {
                        loadCatalog(forceRefresh = true)
                    }
                },
                onClose = onDismiss,
            )
        }

        is RouteSetupPage.StationSearch -> {
            RouteStationSearchScreen(
                listState = listState,
                target = currentPage.target,
                query = stationQuery,
                results = stationResults,
                isCatalogLoading = isCatalogLoading,
                catalogError = catalogError,
                onQueryChange = { stationQuery = it },
                onSelectStation = { station ->
                    if (station.platforms.isEmpty()) {
                        draftSelection = draftSelection.withEndpoint(
                            target = currentPage.target,
                            selection = station.resolveSelection(platformKey = null),
                        )
                        page = RouteSetupPage.Home
                    } else {
                        page = RouteSetupPage.PlatformPicker(
                            target = currentPage.target,
                            station = station,
                        )
                    }
                },
                onRetryCatalog = {
                    coroutineScope.launch {
                        loadCatalog(forceRefresh = true)
                    }
                },
                onBack = {
                    page = RouteSetupPage.Home
                },
            )
        }

        is RouteSetupPage.PlatformPicker -> {
            RoutePlatformPickerScreen(
                listState = listState,
                target = currentPage.target,
                station = currentPage.station,
                onSelectPlatform = { platformKey ->
                    draftSelection = draftSelection.withEndpoint(
                        target = currentPage.target,
                        selection = currentPage.station.resolveSelection(platformKey),
                    )
                    page = RouteSetupPage.Home
                },
                onBack = {
                    page = RouteSetupPage.StationSearch(currentPage.target)
                },
            )
        }

        RouteSetupPage.LineSearch -> {
            RouteLineSearchScreen(
                listState = listState,
                query = lineQuery,
                results = lineResults,
                isCatalogLoading = isCatalogLoading,
                catalogError = catalogError,
                onQueryChange = { lineQuery = it },
                onSelectAnyLine = {
                    draftSelection = draftSelection.copy(line = null)
                    page = RouteSetupPage.Home
                },
                onSelectLine = { line ->
                    draftSelection = draftSelection.copy(line = line.toSelection())
                    page = RouteSetupPage.Home
                },
                onRetryCatalog = {
                    coroutineScope.launch {
                        loadCatalog(forceRefresh = true)
                    }
                },
                onBack = {
                    page = RouteSetupPage.Home
                },
            )
        }
    }
}

private fun RouteSelection.withEndpoint(
    target: RouteEndpointTarget,
    selection: StopSelection,
): RouteSelection {
    return when (target) {
        RouteEndpointTarget.ORIGIN -> copy(origin = selection)
        RouteEndpointTarget.DESTINATION -> copy(destination = selection)
    }
}
