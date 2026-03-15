package com.example.routetracker.presentation

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
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
internal fun RouteSetupOverlay(
    routeRepo: RouteRepository,
    currentSelection: RouteSelection,
    favoriteRoutes: List<RouteSelection>,
    onApplySelection: (RouteSelection) -> Unit,
    onDismiss: () -> Unit,
) {
    var page by remember(currentSelection.stableKey) { mutableStateOf<RouteSetupPage>(RouteSetupPage.Home) }
    var draftSelection by remember(currentSelection.stableKey) { mutableStateOf(currentSelection) }
    var localFavorites by remember(favoriteRoutes) { mutableStateOf(favoriteRoutes) }
    var catalog by remember { mutableStateOf(routeRepo.getCachedTransitCatalog()) }
    var isCatalogLoading by remember { mutableStateOf(catalog == null) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var stationQuery by remember { mutableStateOf("") }
    var lineQuery by remember { mutableStateOf("") }
    var stationResults by remember { mutableStateOf<List<StationOption>>(emptyList()) }
    var lineResults by remember { mutableStateOf<List<LineOption>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

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
        if (catalog == null || routeRepo.isTransitCatalogRefreshDue()) {
            loadCatalog(forceRefresh = catalog != null)
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
        scrollState.scrollTo(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val currentPage = page) {
                RouteSetupPage.Home -> {
                    RouteSetupHomePage(
                        draftSelection = draftSelection,
                        favoriteRoutes = localFavorites,
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
                                    routeRepo.toggleFavoriteRoute(draftSelection)
                                }
                                localFavorites = routeRepo.getFavoriteRoutes()
                            }
                        },
                        onApplyFavorite = { selection ->
                            onApplySelection(selection)
                            onDismiss()
                        },
                        onApplyRoute = {
                            onApplySelection(draftSelection)
                            onDismiss()
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
                    RouteStationSearchPage(
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
                    RoutePlatformPickerPage(
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
                    RouteLineSearchPage(
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
private fun RouteSetupHomePage(
    draftSelection: RouteSelection,
    favoriteRoutes: List<RouteSelection>,
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

    Text(
        text = "Route setup",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Text(
        text = "Direct routes only",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        textAlign = TextAlign.Center,
    )

    if (isCatalogLoading) {
        RouteSetupInfoCard(
            title = "Syncing stops",
            value = "Updating local stop and line suggestions.",
            topPadding = 10.dp,
        )
    } else if (catalogError != null) {
        RouteSetupInfoCard(
            title = "Catalog error",
            value = catalogError,
            topPadding = 10.dp,
        )
        ActionButton(
            label = "Retry sync",
            topPadding = 8.dp,
            onClick = onRetryCatalog,
        )
    }

    RouteSetupValueCard(
        title = "From",
        value = draftSelection.origin.displayLabel,
        topPadding = 10.dp,
        onClick = onChooseOrigin,
    )
    RouteSetupValueCard(
        title = "To",
        value = draftSelection.destination.displayLabel,
        topPadding = 8.dp,
        onClick = onChooseDestination,
    )
    RouteSetupValueCard(
        title = "Line",
        value = draftSelection.line?.displayLabel ?: "Any line",
        topPadding = 8.dp,
        onClick = onChooseLine,
    )

    ActionButton(
        label = if (isFavorite) "Remove favorite" else "Save favorite",
        topPadding = 12.dp,
        onClick = onToggleFavorite,
    )
    ActionButton(
        label = "Apply route",
        topPadding = 8.dp,
        emphasize = true,
        onClick = onApplyRoute,
    )

    if (favoriteRoutes.isNotEmpty()) {
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            textAlign = TextAlign.Center,
        )
        favoriteRoutes.forEach { favorite ->
            FavoriteRouteCard(
                selection = favorite,
                onClick = { onApplyFavorite(favorite) },
            )
        }
    }

    ActionButton(
        label = "Close",
        topPadding = 12.dp,
        onClick = onClose,
    )
}

@Composable
private fun RouteStationSearchPage(
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
    Text(
        text = if (target == RouteEndpointTarget.ORIGIN) "Choose origin" else "Choose destination",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    SearchFieldCard(
        value = query,
        placeholder = "Type stop name",
        topPadding = 10.dp,
        onValueChange = onQueryChange,
    )

    when {
        isCatalogLoading -> {
            RouteSetupInfoCard(
                title = "Loading",
                value = "Stops and lines are syncing.",
                topPadding = 10.dp,
            )
        }

        catalogError != null -> {
            RouteSetupInfoCard(
                title = "Catalog error",
                value = catalogError,
                topPadding = 10.dp,
            )
            ActionButton(
                label = "Retry sync",
                topPadding = 8.dp,
                onClick = onRetryCatalog,
            )
        }

        query.isBlank() -> {
            RouteSetupInfoCard(
                title = "Search",
                value = "Type at least one letter. Accent-insensitive search is enabled.",
                topPadding = 10.dp,
            )
        }

        results.isEmpty() -> {
            RouteSetupInfoCard(
                title = "No matches",
                value = "Try a shorter or broader stop name.",
                topPadding = 10.dp,
            )
        }

        else -> {
            results.forEach { station ->
                SuggestionCard(
                    title = station.stationName,
                    subtitle = station.searchSubtitle,
                    onClick = { onSelectStation(station) },
                )
            }
        }
    }

    ActionButton(
        label = "Back",
        topPadding = 12.dp,
        onClick = onBack,
    )
}

@Composable
private fun RoutePlatformPickerPage(
    target: RouteEndpointTarget,
    station: StationOption,
    onSelectPlatform: (String?) -> Unit,
    onBack: () -> Unit,
) {
    Text(
        text = if (target == RouteEndpointTarget.ORIGIN) "Origin platform" else "Destination platform",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Text(
        text = station.stationName,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        textAlign = TextAlign.Center,
    )

    SuggestionCard(
        title = "Any platform",
        subtitle = "${station.stopIds.size} stop IDs",
        topPadding = 10.dp,
        onClick = { onSelectPlatform(null) },
    )
    station.platforms.forEach { platform ->
        SuggestionCard(
            title = platform.label,
            subtitle = "${platform.stopIds.size} stop IDs",
            onClick = { onSelectPlatform(platform.platformKey) },
        )
    }

    ActionButton(
        label = "Back",
        topPadding = 12.dp,
        onClick = onBack,
    )
}

@Composable
private fun RouteLineSearchPage(
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
    Text(
        text = "Choose line",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    SearchFieldCard(
        value = query,
        placeholder = "Type line number or name",
        topPadding = 10.dp,
        onValueChange = onQueryChange,
    )

    SuggestionCard(
        title = "Any line",
        subtitle = "Show the next direct tram regardless of line.",
        topPadding = 10.dp,
        onClick = onSelectAnyLine,
    )

    when {
        isCatalogLoading -> {
            RouteSetupInfoCard(
                title = "Loading",
                value = "Lines are syncing.",
                topPadding = 8.dp,
            )
        }

        catalogError != null -> {
            RouteSetupInfoCard(
                title = "Catalog error",
                value = catalogError,
                topPadding = 8.dp,
            )
            ActionButton(
                label = "Retry sync",
                topPadding = 8.dp,
                onClick = onRetryCatalog,
            )
        }

        results.isEmpty() -> {
            RouteSetupInfoCard(
                title = "No matches",
                value = "Try a shorter line query.",
                topPadding = 8.dp,
            )
        }

        else -> {
            results.forEach { line ->
                SuggestionCard(
                    title = line.displayLabel,
                    subtitle = line.longName ?: "Direct departures on line ${line.shortName}",
                    onClick = { onSelectLine(line) },
                )
            }
        }
    }

    ActionButton(
        label = "Back",
        topPadding = 12.dp,
        onClick = onBack,
    )
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
            .padding(top = topPadding)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
    onClick: () -> Unit,
) {
    SuggestionCard(
        title = selection.routeSummaryLabel,
        subtitle = selection.line?.displayLabel ?: "Any line",
        topPadding = 8.dp,
        onClick = onClick,
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
            .padding(top = topPadding)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
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
