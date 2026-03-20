package com.example.routetracker.presentation

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.example.routetracker.data.formatBoardingStopCount
import com.example.routetracker.data.LineOption
import com.example.routetracker.data.LineSelection
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.data.StationOption
import com.example.routetracker.data.StopSelection
import com.example.routetracker.data.TransitCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROUTE_SETUP_TAG = "RouteSetupUi"

private enum class RouteEndpointTarget {
    ORIGIN,
    DESTINATION,
}

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
    // Use the in-memory catalog immediately when available; disk/network loading happens off the UI thread below.
    var catalog by remember { mutableStateOf(routeRepo.peekTransitCatalogInMemory()) }
    var isCatalogLoading by remember { mutableStateOf(catalog == null) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var stationQuery by remember { mutableStateOf("") }
    var lineQuery by remember { mutableStateOf("") }
    var stationResults by remember { mutableStateOf<List<StationOption>>(emptyList()) }
    var lineResults by remember { mutableStateOf<List<LineOption>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

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

    RoundScalingPage(state = listState) {
        when (val currentPage = page) {
            RouteSetupPage.Home -> {
                routeSetupHomePage(
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
                    onApplyFavorite = { selection ->
                        onApplySelection(selection)
                    },
                    onApplyRoute = {
                        onApplySelection(draftSelection)
                    },
                    onRetryCatalog = {
                        coroutineScope.launch {
                            loadCatalog(forceRefresh = true)
                        }
                    },
                    onClose = onDismiss,
                )
            }

            is RouteSetupPage.StationSearch -> {
                routeStationSearchPage(
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
                routePlatformPickerPage(
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
                routeLineSearchPage(
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

@Composable
internal fun QuickRouteSwitchScreen(
    currentSelection: RouteSelection,
    favoriteRoutes: List<RouteSelection>,
    showSwapAction: Boolean = true,
    onSwapRoute: (RouteSelection) -> Unit,
    onApplyFavorite: (RouteSelection) -> Unit,
    onEditFavorite: (RouteSelection) -> Unit,
    onDeleteFavorite: (RouteSelection) -> Unit,
    onOpenRouteSetup: () -> Unit,
) {
    var selectedFavoriteForMenu by remember(favoriteRoutes) { mutableStateOf<RouteSelection?>(null) }
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)

    BackHandler(enabled = selectedFavoriteForMenu != null) {
        selectedFavoriteForMenu = null
    }

    RoundScalingPage(state = listState) {
        item {
            Text(
                text = "Route switch",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                textAlign = TextAlign.Center,
            )
        }
        item {
            Text(
                text = currentSelection.routeSummaryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
        if (showSwapAction) {
            item {
                ActionButton(
                    label = "Swap from / to",
                    topPadding = 10.dp,
                    testTag = UiTestTags.QUICK_SWITCH_SWAP_BUTTON,
                    onClick = { onSwapRoute(currentSelection.swappedEndpoints()) },
                )
            }
        }

        val favoriteForMenu = selectedFavoriteForMenu
        if (favoriteForMenu != null) {
            item {
                RouteSetupInfoCard(
                    title = "Favorite options",
                    value = favoriteForMenu.routeSummaryWithPlatforms,
                    topPadding = 10.dp,
                )
            }
            item {
                ActionButton(
                    label = "Edit favorite",
                    topPadding = 10.dp,
                    emphasize = true,
                    onClick = {
                        selectedFavoriteForMenu = null
                        onEditFavorite(favoriteForMenu)
                    },
                )
            }
            item {
                ActionButton(
                    label = "Delete favorite",
                    topPadding = 8.dp,
                    onClick = {
                        selectedFavoriteForMenu = null
                        onDeleteFavorite(favoriteForMenu)
                    },
                )
            }
            item {
                ActionButton(
                    label = "Cancel",
                    topPadding = 8.dp,
                    onClick = {
                        selectedFavoriteForMenu = null
                    },
                )
            }
        } else if (favoriteRoutes.isEmpty()) {
            item {
                RouteSetupInfoCard(
                    title = "No favorites",
                    value = "Save routes in the full setup, then switch them from here.",
                    topPadding = 10.dp,
                )
            }
        } else {
            item {
                Text(
                    text = "Favorites",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .padding(top = 10.dp),
                    textAlign = TextAlign.Center,
                )
            }
            favoriteRoutes.forEach { favorite ->
                item {
                    FavoriteRouteCard(
                        selection = favorite,
                        subtitle = buildString {
                            append(favorite.favoriteSummaryLabel)
                            if (favorite.stableKey == currentSelection.stableKey) {
                                append(" · Current")
                            }
                        },
                        onClick = { onApplyFavorite(favorite) },
                        onLongClick = {
                            selectedFavoriteForMenu = favorite
                        },
                    )
                }
            }
        }

        item {
            ActionButton(
                label = "New route",
                topPadding = 12.dp,
                emphasize = true,
                testTag = UiTestTags.QUICK_SWITCH_NEW_ROUTE_BUTTON,
                onClick = onOpenRouteSetup,
            )
        }
    }
}

@Composable
internal fun RouteSetupHomePage(
    draftSelection: RouteSelection,
    favoriteRoutes: List<RouteSelection>,
    isEditingFavorite: Boolean,
    isCatalogLoading: Boolean,
    catalogError: String?,
    onChooseOrigin: () -> Unit,
    onChooseDestination: () -> Unit,
    onChooseLine: () -> Unit,
    onToggleFavorite: () -> Unit,
    onApplyFavorite: (RouteSelection) -> Unit,
    onApplyRoute: () -> Unit,
    onRetryCatalog: () -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberScalingLazyListState(initialCenterItemIndex = 0)
    RoundScalingPage(state = listState) {
        routeSetupHomePage(
            draftSelection = draftSelection,
            favoriteRoutes = favoriteRoutes,
            isEditingFavorite = isEditingFavorite,
            isCatalogLoading = isCatalogLoading,
            catalogError = catalogError,
            onChooseOrigin = onChooseOrigin,
            onChooseDestination = onChooseDestination,
            onChooseLine = onChooseLine,
            onToggleFavorite = onToggleFavorite,
            onApplyFavorite = onApplyFavorite,
            onApplyRoute = onApplyRoute,
            onRetryCatalog = onRetryCatalog,
            onClose = onClose,
        )
    }
}

private fun ScalingLazyListScope.routeSetupHomePage(
    draftSelection: RouteSelection,
    favoriteRoutes: List<RouteSelection>,
    isEditingFavorite: Boolean,
    isCatalogLoading: Boolean,
    catalogError: String?,
    onChooseOrigin: () -> Unit,
    onChooseDestination: () -> Unit,
    onChooseLine: () -> Unit,
    onToggleFavorite: () -> Unit,
    onApplyFavorite: (RouteSelection) -> Unit,
    onApplyRoute: () -> Unit,
    onRetryCatalog: () -> Unit,
    onClose: () -> Unit,
) {
    val isFavorite = favoriteRoutes.any { it.stableKey == draftSelection.stableKey }

    item {
        Text(
            text = "Route setup",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            textAlign = TextAlign.Center,
        )
    }
    item {
        Text(
            text = "Direct routes only",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(top = 2.dp),
            textAlign = TextAlign.Center,
        )
    }

    if (isCatalogLoading) {
        item {
            RouteSetupInfoCard(
                title = "Syncing stops",
                value = "Updating local stop and line suggestions.",
                topPadding = 10.dp,
            )
        }
    } else if (catalogError != null) {
        item {
            RouteSetupInfoCard(
                title = "Catalog error",
                value = catalogError,
                topPadding = 10.dp,
            )
        }
        item {
            ActionButton(
                label = "Retry sync",
                topPadding = 8.dp,
                onClick = onRetryCatalog,
            )
        }
    }

    item {
        RouteSetupValueCard(
            title = "From",
            value = draftSelection.origin.displayLabel,
            topPadding = 10.dp,
            onClick = onChooseOrigin,
        )
    }
    item {
        RouteSetupValueCard(
            title = "To",
            value = draftSelection.destination.displayLabel,
            topPadding = 8.dp,
            onClick = onChooseDestination,
        )
    }
    item {
        RouteSetupValueCard(
            title = "Line",
            value = draftSelection.line?.displayLabel ?: "Any line",
            topPadding = 8.dp,
            onClick = onChooseLine,
        )
    }

    item {
        ActionButton(
            label = when {
                isEditingFavorite -> "Update favorite"
                isFavorite -> "Remove favorite"
                else -> "Save favorite"
            },
            topPadding = 12.dp,
            onClick = onToggleFavorite,
        )
    }
    item {
        ActionButton(
            label = "Apply route",
            topPadding = 8.dp,
            emphasize = true,
            onClick = onApplyRoute,
        )
    }

    if (favoriteRoutes.isNotEmpty()) {
        item {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(top = 12.dp),
                textAlign = TextAlign.Center,
            )
        }
        favoriteRoutes.forEach { favorite ->
            item {
                FavoriteRouteCard(
                    selection = favorite,
                    onClick = { onApplyFavorite(favorite) },
                )
            }
        }
    }

    item {
        ActionButton(
            label = "Close",
            topPadding = 12.dp,
            onClick = onClose,
        )
    }
}

private fun ScalingLazyListScope.routeStationSearchPage(
    target: RouteEndpointTarget,
    query: String,
    results: List<StationOption>,
    isCatalogLoading: Boolean,
    catalogError: String?,
    onQueryChange: (String) -> Unit,
    onSelectStation: (StationOption) -> Unit,
    onRetryCatalog: () -> Unit,
    onBack: () -> Unit,
) {
    item {
        Text(
            text = if (target == RouteEndpointTarget.ORIGIN) "Choose origin" else "Choose destination",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            textAlign = TextAlign.Center,
        )
    }
    item {
        SearchFieldCard(
            value = query,
            placeholder = "Type stop name",
            topPadding = 10.dp,
            onValueChange = onQueryChange,
        )
    }

    when {
        isCatalogLoading -> {
            item {
                RouteSetupInfoCard(
                    title = "Loading",
                    value = "Stops and lines are syncing.",
                    topPadding = 10.dp,
                )
            }
        }

        catalogError != null -> {
            item {
                RouteSetupInfoCard(
                    title = "Catalog error",
                    value = catalogError,
                    topPadding = 10.dp,
                )
            }
            item {
                ActionButton(
                    label = "Retry sync",
                    topPadding = 8.dp,
                    onClick = onRetryCatalog,
                )
            }
        }

        query.isBlank() -> {
            item {
                RouteSetupInfoCard(
                    title = "Search",
                    value = "Type at least one letter. Accent-insensitive search is enabled.",
                    topPadding = 10.dp,
                )
            }
        }

        results.isEmpty() -> {
            item {
                RouteSetupInfoCard(
                    title = "No matches",
                    value = "Try a shorter or broader stop name.",
                    topPadding = 10.dp,
                )
            }
        }

        else -> {
            results.forEach { station ->
                item {
                    SuggestionCard(
                        title = station.stationName,
                        subtitle = station.searchSubtitle,
                        onClick = { onSelectStation(station) },
                    )
                }
            }
        }
    }

    item {
        ActionButton(
            label = "Back",
            topPadding = 12.dp,
            onClick = onBack,
        )
    }
}

private fun ScalingLazyListScope.routePlatformPickerPage(
    target: RouteEndpointTarget,
    station: StationOption,
    onSelectPlatform: (String?) -> Unit,
    onBack: () -> Unit,
) {
    item {
        Text(
            text = if (target == RouteEndpointTarget.ORIGIN) "Origin platform" else "Destination platform",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            textAlign = TextAlign.Center,
        )
    }
    item {
        Text(
            text = station.stationName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
    }

    item {
        SuggestionCard(
            title = "Any platform",
            subtitle = station.anyPlatformSubtitle,
            topPadding = 10.dp,
            onClick = { onSelectPlatform(null) },
        )
    }
    station.platforms.forEach { platform ->
        item {
            SuggestionCard(
                title = platform.label,
                subtitle = formatBoardingStopCount(platform.stopIds.size),
                onClick = { onSelectPlatform(platform.platformKey) },
            )
        }
    }

    item {
        ActionButton(
            label = "Back",
            topPadding = 12.dp,
            onClick = onBack,
        )
    }
}

private fun ScalingLazyListScope.routeLineSearchPage(
    query: String,
    results: List<LineOption>,
    isCatalogLoading: Boolean,
    catalogError: String?,
    onQueryChange: (String) -> Unit,
    onSelectAnyLine: () -> Unit,
    onSelectLine: (LineOption) -> Unit,
    onRetryCatalog: () -> Unit,
    onBack: () -> Unit,
) {
    item {
        Text(
            text = "Choose line",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
            textAlign = TextAlign.Center,
        )
    }
    item {
        SearchFieldCard(
            value = query,
            placeholder = "Type line number or name",
            topPadding = 10.dp,
            onValueChange = onQueryChange,
        )
    }

    item {
        SuggestionCard(
            title = "Any line",
            subtitle = "Show the next direct tram regardless of line.",
            topPadding = 10.dp,
            onClick = onSelectAnyLine,
        )
    }

    when {
        isCatalogLoading -> {
            item {
                RouteSetupInfoCard(
                    title = "Loading",
                    value = "Lines are syncing.",
                    topPadding = 8.dp,
                )
            }
        }

        catalogError != null -> {
            item {
                RouteSetupInfoCard(
                    title = "Catalog error",
                    value = catalogError,
                    topPadding = 8.dp,
                )
            }
            item {
                ActionButton(
                    label = "Retry sync",
                    topPadding = 8.dp,
                    onClick = onRetryCatalog,
                )
            }
        }

        results.isEmpty() -> {
            item {
                RouteSetupInfoCard(
                    title = "No matches",
                    value = "Try a shorter line query.",
                    topPadding = 8.dp,
                )
            }
        }

        else -> {
            results.forEach { line ->
                item {
                    SuggestionCard(
                        title = line.displayLabel,
                        subtitle = line.longName ?: "Direct departures on line ${line.shortName}",
                        onClick = { onSelectLine(line) },
                    )
                }
            }
        }
    }

    item {
        ActionButton(
            label = "Back",
            topPadding = 12.dp,
            onClick = onBack,
        )
    }
}

@Composable
private fun SearchFieldCard(
    value: String,
    placeholder: String,
    topPadding: androidx.compose.ui.unit.Dp,
    onValueChange: (String) -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = topPadding)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Samsung's watch keyboard behaves more reliably with a native EditText than a
        // custom Compose text field, especially while the IME is still composing text.
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
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    hint = "Search..."
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                    imeOptions = EditorInfo.IME_ACTION_SEARCH
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
private fun RouteSetupValueCard(
    title: String,
    value: String,
    topPadding: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    SuggestionCard(
        title = title,
        subtitle = value,
        topPadding = topPadding,
        onClick = onClick,
    )
}

@Composable
private fun FavoriteRouteCard(
    selection: RouteSelection,
    subtitle: String = selection.favoriteSummaryLabel,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    SuggestionCard(
        title = selection.routeSummaryLabel,
        subtitle = subtitle,
        topPadding = 8.dp,
        testTag = UiTestTags.favoriteRouteCard(selection.stableKey),
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

@Composable
private fun RouteSetupInfoCard(
    title: String,
    value: String,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = topPadding)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SuggestionCard(
    title: String,
    subtitle: String,
    topPadding: androidx.compose.ui.unit.Dp = 8.dp,
    testTag: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = topPadding)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(22.dp),
            )
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick,
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    topPadding: androidx.compose.ui.unit.Dp,
    emphasize: Boolean = false,
    testTag: String? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(top = topPadding),
        colors = if (emphasize) {
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
        Text(label)
    }
}
