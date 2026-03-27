package com.example.routetracker.presentation

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RevealValue
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.SwipeToRevealDefaults
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.rememberRevealState
import com.example.routetracker.data.LineOption
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.data.StationOption
import com.example.routetracker.data.formatBoardingStopCount
import com.example.routetracker.presentation.preview.LineSearchPreviewCase
import com.example.routetracker.presentation.preview.LineSearchPreviewCaseProvider
import com.example.routetracker.presentation.preview.PlatformPickerPreviewCase
import com.example.routetracker.presentation.preview.PlatformPickerPreviewCaseProvider
import com.example.routetracker.presentation.preview.QuickSwitchPreviewCase
import com.example.routetracker.presentation.preview.QuickSwitchPreviewCaseProvider
import com.example.routetracker.presentation.preview.RouteSetupHomePreviewCase
import com.example.routetracker.presentation.preview.RouteSetupHomePreviewCaseProvider
import com.example.routetracker.presentation.preview.RouteTrackerPreviewScaffold
import com.example.routetracker.presentation.preview.StationSearchPreviewCase
import com.example.routetracker.presentation.preview.StationSearchPreviewCaseProvider
import com.example.routetracker.presentation.preview.WearSurfacePreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal enum class RouteEndpointTarget {
    ORIGIN,
    DESTINATION,
}

@Composable
internal fun QuickRouteSwitchScreen(
    currentSelection: RouteSelection,
    favoriteRoutes: List<RouteSelection>,
    onSwapRoute: (RouteSelection) -> Unit,
    onApplyFavorite: (RouteSelection) -> Unit,
    onEditFavorite: (RouteSelection) -> Unit,
    onDeleteFavorite: (RouteSelection) -> Unit,
    onOpenRouteSetup: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    val coroutineScope = rememberCoroutineScope()

    RouteTrackerListScreen(
        state = listState,
        firstItemType = RouteTrackerColumnItemType.Surface,
        edgeButton = {
            EdgeButton(
                onClick = onOpenRouteSetup,
                modifier = Modifier.testTag(UiTestTags.QUICK_SWITCH_NEW_ROUTE_BUTTON),
            ) {
                Text("New route")
            }
        },
    ) { transformationSpec ->
        item(key = "quick_switch_current") {
            CurrentRouteQuickSwitchRow(
                currentSelection = currentSelection,
                listState = listState,
                coroutineScope = coroutineScope,
                transformationSpec = transformationSpec,
                onSwapRoute = onSwapRoute,
                onOpenRouteSetup = onOpenRouteSetup,
            )
        }
        if (favoriteRoutes.isEmpty()) {
            item(key = "quick_switch_empty") {
                RouteSetupInfoCard(
                    title = "No favorites",
                    value = "Save routes in the full setup, then switch them from here.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            }
        } else {
            items(
                items = favoriteRoutes,
                key = { favorite -> favorite.stableKey },
            ) { favorite ->
                FavoriteQuickSwitchRow(
                    selection = favorite,
                    isCurrent = favorite.stableKey == currentSelection.stableKey,
                    listState = listState,
                    coroutineScope = coroutineScope,
                    transformationSpec = transformationSpec,
                    onApplyFavorite = { onApplyFavorite(favorite) },
                    onEditFavorite = { onEditFavorite(favorite) },
                    onDeleteFavorite = { onDeleteFavorite(favorite) },
                )
            }
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
    val listState = rememberTransformingLazyColumnState()
    RouteSetupHomeScreen(
        listState = listState,
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
    )
}

@Composable
internal fun RouteStationSearchPage(
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
    RouteStationSearchScreen(
        listState = rememberTransformingLazyColumnState(),
        target = target,
        query = query,
        results = results,
        isCatalogLoading = isCatalogLoading,
        catalogError = catalogError,
        onQueryChange = onQueryChange,
        onSelectStation = onSelectStation,
        onRetryCatalog = onRetryCatalog,
        onBack = onBack,
    )
}

@Composable
internal fun RoutePlatformPickerPage(
    target: RouteEndpointTarget,
    station: StationOption,
    onSelectPlatform: (String?) -> Unit,
    onBack: () -> Unit,
) {
    RoutePlatformPickerScreen(
        listState = rememberTransformingLazyColumnState(),
        target = target,
        station = station,
        onSelectPlatform = onSelectPlatform,
        onBack = onBack,
    )
}

@Composable
internal fun RouteLineSearchPage(
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
    RouteLineSearchScreen(
        listState = rememberTransformingLazyColumnState(),
        query = query,
        results = results,
        isCatalogLoading = isCatalogLoading,
        catalogError = catalogError,
        onQueryChange = onQueryChange,
        onSelectAnyLine = onSelectAnyLine,
        onSelectLine = onSelectLine,
        onRetryCatalog = onRetryCatalog,
        onBack = onBack,
    )
}

@Composable
private fun TransformingLazyColumnItemScope.CurrentRouteQuickSwitchRow(
    currentSelection: RouteSelection,
    listState: TransformingLazyColumnState,
    coroutineScope: CoroutineScope,
    transformationSpec: TransformationSpec,
    onSwapRoute: (RouteSelection) -> Unit,
    onOpenRouteSetup: () -> Unit,
) {
    val revealState = rememberRevealState()
    val swappedSelection = remember(currentSelection) { currentSelection.swappedEndpoints() }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && revealState.currentValue != RevealValue.Covered) {
            coroutineScope.launch {
                revealState.animateTo(RevealValue.Covered)
            }
        }
    }

    SwipeToReveal(
        primaryAction = {
            PrimaryActionButton(
                onClick = { onSwapRoute(swappedSelection) },
                icon = {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_rotate),
                        contentDescription = "Swap direction",
                    )
                },
                text = { Text("Swap") },
                modifier = Modifier
                    .height(SwipeToRevealDefaults.LargeActionButtonHeight)
                    .testTag(UiTestTags.QUICK_SWITCH_SWAP_BUTTON),
            )
        },
        secondaryAction = {
            SecondaryActionButton(
                onClick = onOpenRouteSetup,
                icon = {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_edit),
                        contentDescription = "Edit route",
                    )
                },
                modifier = Modifier
                    .height(SwipeToRevealDefaults.LargeActionButtonHeight)
                    .testTag(UiTestTags.QUICK_SWITCH_EDIT_BUTTON),
            )
        },
        onSwipePrimaryAction = { onSwapRoute(swappedSelection) },
        revealState = revealState,
        modifier = Modifier
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                with(transformationSpec) {
                    applyContainerTransformation(scrollProgress)
                }
                compositingStrategy = CompositingStrategy.ModulateAlpha
                clip = false
            },
    ) {
        Card(
            onClick = onOpenRouteSetup,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(UiTestTags.QUICK_SWITCH_CURRENT_ROUTE_CARD)
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Edit route") {
                            onOpenRouteSetup()
                            true
                        },
                        CustomAccessibilityAction("Swap direction") {
                            onSwapRoute(swappedSelection)
                            true
                        },
                    )
                },
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current route",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = currentSelection.routeSummaryLabel,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Text(
                        text = currentSelection.favoriteSummaryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(start = 10.dp)
                        .height(28.dp)
                        .width(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(99.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun RouteSetupHomeScreen(
    listState: TransformingLazyColumnState,
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
) {
    val isFavorite = favoriteRoutes.any { it.stableKey == draftSelection.stableKey }

    RouteTrackerListScreen(
        state = listState,
        edgeButton = {
            EdgeButton(
                onClick = onApplyRoute,
                modifier = Modifier.testTag(UiTestTags.ROUTE_SETUP_APPLY_BUTTON),
            ) {
                Text("Apply route")
            }
        },
    ) { transformationSpec ->
        item(key = "route_setup_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Route setup")
            }
        }
        item(key = "route_setup_intro") {
            RouteSetupInfoCard(
                title = "Direct routes only",
                value = "Pick origin, destination, and optional line for the live board.",
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }

        if (isCatalogLoading) {
            item(key = "route_setup_loading") {
                RouteSetupInfoCard(
                    title = "Syncing stops",
                    value = "Updating local stop and line suggestions.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            }
        } else if (catalogError != null) {
            item(key = "route_setup_error") {
                RouteSetupInfoCard(
                    title = "Catalog error",
                    value = catalogError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                )
            }
            item(key = "route_setup_retry") {
                ActionButton(
                    label = "Retry sync",
                    onClick = onRetryCatalog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }

        item(key = "route_setup_origin") {
            RouteSetupValueCard(
                title = "From",
                value = draftSelection.origin.displayLabel,
                onClick = onChooseOrigin,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
        item(key = "route_setup_destination") {
            RouteSetupValueCard(
                title = "To",
                value = draftSelection.destination.displayLabel,
                onClick = onChooseDestination,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
        item(key = "route_setup_line") {
            RouteSetupValueCard(
                title = "Line",
                value = draftSelection.line?.displayLabel ?: "Any line",
                onClick = onChooseLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
        item(key = "route_setup_favorite_toggle") {
            ActionButton(
                label = when {
                    isEditingFavorite -> "Update favorite"
                    isFavorite -> "Remove favorite"
                    else -> "Save favorite"
                },
                onClick = onToggleFavorite,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }

        if (favoriteRoutes.isNotEmpty()) {
            item(key = "route_setup_favorites_header") {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text("Saved routes")
                }
            }
            items(
                items = favoriteRoutes,
                key = { favorite -> favorite.stableKey },
            ) { favorite ->
                FavoriteRouteCard(
                    selection = favorite,
                    onClick = { onApplyFavorite(favorite) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                )
            }
        }
    }
}

@Composable
internal fun RouteStationSearchScreen(
    listState: TransformingLazyColumnState,
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
    RouteTrackerListScreen(
        state = listState,
        timeText = {},
        edgeButton = {
            EdgeButton(onClick = onBack) {
                Text("Back")
            }
        },
    ) { transformationSpec ->
        item(key = "route_station_search_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text(if (target == RouteEndpointTarget.ORIGIN) "Choose origin" else "Choose destination")
            }
        }
        item(key = "route_station_search_field") {
            SearchFieldCard(
                value = query,
                placeholder = "Type stop name",
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }

        when {
            isCatalogLoading -> {
                item(key = "route_station_search_loading") {
                    RouteSetupInfoCard(
                        title = "Loading",
                        value = "Stops and lines are syncing.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }

            catalogError != null -> {
                item(key = "route_station_search_error") {
                    RouteSetupInfoCard(
                        title = "Catalog error",
                        value = catalogError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
                item(key = "route_station_search_retry") {
                    ActionButton(
                        label = "Retry sync",
                        onClick = onRetryCatalog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }

            query.isBlank() -> {
                item(key = "route_station_search_hint") {
                    RouteSetupInfoCard(
                        title = "Search",
                        value = "Type at least one letter. Accent-insensitive search is enabled.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }

            results.isEmpty() -> {
                item(key = "route_station_search_empty") {
                    RouteSetupInfoCard(
                        title = "No matches",
                        value = "Try a shorter or broader stop name.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }

            else -> {
                items(
                    items = results,
                    key = { station -> station.stationKey },
                ) { station ->
                    SuggestionCard(
                        title = station.stationName,
                        subtitle = station.searchSubtitle,
                        onClick = { onSelectStation(station) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
    }
}

@Composable
internal fun RoutePlatformPickerScreen(
    listState: TransformingLazyColumnState,
    target: RouteEndpointTarget,
    station: StationOption,
    onSelectPlatform: (String?) -> Unit,
    onBack: () -> Unit,
) {
    RouteTrackerListScreen(
        state = listState,
        timeText = {},
        edgeButton = {
            EdgeButton(onClick = onBack) {
                Text("Back")
            }
        },
    ) { transformationSpec ->
        item(key = "route_platform_picker_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text(if (target == RouteEndpointTarget.ORIGIN) "Origin platform" else "Destination platform")
            }
        }
        item(key = "route_platform_picker_station") {
            RouteSetupInfoCard(
                title = "Station",
                value = station.stationName,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "route_platform_picker_any") {
            SuggestionCard(
                title = "Any platform",
                subtitle = station.anyPlatformSubtitle,
                onClick = { onSelectPlatform(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
        items(
            items = station.platforms,
            key = { platform -> platform.platformKey },
        ) { platform ->
            SuggestionCard(
                title = platform.label,
                subtitle = formatBoardingStopCount(platform.stopIds.size),
                onClick = { onSelectPlatform(platform.platformKey) },
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }
    }
}

@Composable
internal fun RouteLineSearchScreen(
    listState: TransformingLazyColumnState,
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
    RouteTrackerListScreen(
        state = listState,
        timeText = {},
        edgeButton = {
            EdgeButton(onClick = onBack) {
                Text("Back")
            }
        },
    ) { transformationSpec ->
        item(key = "route_line_search_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Choose line")
            }
        }
        item(key = "route_line_search_field") {
            SearchFieldCard(
                value = query,
                placeholder = "Type line number or name",
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "route_line_any") {
            SuggestionCard(
                title = "Any line",
                subtitle = "Show the next direct tram regardless of line.",
                onClick = onSelectAnyLine,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            )
        }

        when {
            isCatalogLoading -> {
                item(key = "route_line_loading") {
                    RouteSetupInfoCard(
                        title = "Loading",
                        value = "Lines are syncing.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }

            catalogError != null -> {
                item(key = "route_line_error") {
                    RouteSetupInfoCard(
                        title = "Catalog error",
                        value = catalogError,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
                item(key = "route_line_retry") {
                    ActionButton(
                        label = "Retry sync",
                        onClick = onRetryCatalog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }

            results.isEmpty() -> {
                item(key = "route_line_empty") {
                    RouteSetupInfoCard(
                        title = "No matches",
                        value = "Try a shorter line query.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                    )
                }
            }

            else -> {
                items(
                    items = results,
                    key = { line -> line.displayLabel },
                ) { line ->
                    SuggestionCard(
                        title = line.displayLabel,
                        subtitle = line.longName ?: "Direct departures on line ${line.shortName}",
                        onClick = { onSelectLine(line) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFieldCard(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        RouteSetupPanel(modifier = modifier) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value.ifBlank { "Preview search field" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        return
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    RouteSetupPanel(modifier = modifier) {
        Text(
            text = placeholder,
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    SuggestionCard(
        title = title,
        subtitle = value,
        onClick = onClick,
        modifier = modifier,
        transformation = transformation,
    )
}

@Composable
private fun FavoriteRouteCard(
    selection: RouteSelection,
    subtitle: String = selection.favoriteSummaryLabel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    SuggestionCard(
        title = selection.routeSummaryLabel,
        subtitle = subtitle,
        testTag = UiTestTags.favoriteRouteCard(selection.stableKey),
        onClick = onClick,
        modifier = modifier,
        transformation = transformation,
    )
}

@Composable
private fun RouteSetupInfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    RouteSetupPanel(modifier = modifier) {
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
    testTag: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(modifier),
        transformation = transformation,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
    emphasize: Boolean = false,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(modifier),
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
        transformation = transformation,
    ) {
        Text(label)
    }
}

@Composable
private fun TransformingLazyColumnItemScope.FavoriteQuickSwitchRow(
    selection: RouteSelection,
    isCurrent: Boolean,
    listState: TransformingLazyColumnState,
    coroutineScope: CoroutineScope,
    transformationSpec: TransformationSpec,
    onApplyFavorite: () -> Unit,
    onEditFavorite: () -> Unit,
    onDeleteFavorite: () -> Unit,
) {
    val revealState = rememberRevealState()

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && revealState.currentValue != RevealValue.Covered) {
            coroutineScope.launch {
                revealState.animateTo(RevealValue.Covered)
            }
        }
    }

    SwipeToReveal(
        primaryAction = {
            PrimaryActionButton(
                onClick = onDeleteFavorite,
                icon = {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_delete),
                        contentDescription = "Delete favorite",
                    )
                },
                text = { Text("Delete") },
                modifier = Modifier
                    .height(SwipeToRevealDefaults.LargeActionButtonHeight)
                    .testTag(UiTestTags.favoriteRouteDeleteAction(selection.stableKey)),
            )
        },
        secondaryAction = {
            SecondaryActionButton(
                onClick = onEditFavorite,
                icon = {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_edit),
                        contentDescription = "Edit favorite",
                    )
                },
                modifier = Modifier
                    .height(SwipeToRevealDefaults.LargeActionButtonHeight)
                    .testTag(UiTestTags.favoriteRouteEditAction(selection.stableKey)),
            )
        },
        onSwipePrimaryAction = onDeleteFavorite,
        revealState = revealState,
        modifier = Modifier
            .transformedHeight(this, transformationSpec)
            .graphicsLayer {
                with(transformationSpec) {
                    applyContainerTransformation(scrollProgress)
                }
                compositingStrategy = CompositingStrategy.ModulateAlpha
                clip = false
            },
    ) {
        FavoriteRouteCard(
            selection = selection,
            subtitle = buildString {
                append(selection.favoriteSummaryLabel)
                if (isCurrent) {
                    append(" \u00B7 Current")
                }
            },
            onClick = onApplyFavorite,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction("Edit favorite") {
                            onEditFavorite()
                            true
                        },
                        CustomAccessibilityAction("Delete favorite") {
                            onDeleteFavorite()
                            true
                        },
                    )
                },
        )
    }
}

@Composable
private fun RouteSetupPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@WearSurfacePreview
@Composable
private fun QuickRouteSwitchScreenPreview(
    @PreviewParameter(QuickSwitchPreviewCaseProvider::class) previewCase: QuickSwitchPreviewCase,
) {
    RouteTrackerPreviewScaffold {
        QuickRouteSwitchScreen(
            currentSelection = previewCase.currentSelection,
            favoriteRoutes = previewCase.favoriteRoutes,
            onSwapRoute = {},
            onApplyFavorite = {},
            onEditFavorite = {},
            onDeleteFavorite = {},
            onOpenRouteSetup = {},
        )
    }
}

@WearSurfacePreview
@Composable
private fun RouteSetupHomePagePreview(
    @PreviewParameter(RouteSetupHomePreviewCaseProvider::class) previewCase: RouteSetupHomePreviewCase,
) {
    RouteTrackerPreviewScaffold {
        RouteSetupHomePage(
            draftSelection = previewCase.draftSelection,
            favoriteRoutes = previewCase.favoriteRoutes,
            isEditingFavorite = previewCase.isEditingFavorite,
            isCatalogLoading = previewCase.isCatalogLoading,
            catalogError = previewCase.catalogError,
            onChooseOrigin = {},
            onChooseDestination = {},
            onChooseLine = {},
            onToggleFavorite = {},
            onApplyFavorite = {},
            onApplyRoute = {},
            onRetryCatalog = {},
            onClose = {},
        )
    }
}

@WearSurfacePreview
@Composable
private fun RouteStationSearchPagePreview(
    @PreviewParameter(StationSearchPreviewCaseProvider::class) previewCase: StationSearchPreviewCase,
) {
    RouteTrackerPreviewScaffold(showTimeText = false) {
        RouteStationSearchPage(
            target = previewCase.target,
            query = previewCase.query,
            results = previewCase.results,
            isCatalogLoading = previewCase.isCatalogLoading,
            catalogError = previewCase.catalogError,
            onQueryChange = {},
            onSelectStation = {},
            onRetryCatalog = {},
            onBack = {},
        )
    }
}

@WearSurfacePreview
@Composable
private fun RoutePlatformPickerPagePreview(
    @PreviewParameter(PlatformPickerPreviewCaseProvider::class) previewCase: PlatformPickerPreviewCase,
) {
    RouteTrackerPreviewScaffold(showTimeText = false) {
        RoutePlatformPickerPage(
            target = previewCase.target,
            station = previewCase.station,
            onSelectPlatform = {},
            onBack = {},
        )
    }
}

@WearSurfacePreview
@Composable
private fun RouteLineSearchPagePreview(
    @PreviewParameter(LineSearchPreviewCaseProvider::class) previewCase: LineSearchPreviewCase,
) {
    RouteTrackerPreviewScaffold(showTimeText = false) {
        RouteLineSearchPage(
            query = previewCase.query,
            results = previewCase.results,
            isCatalogLoading = previewCase.isCatalogLoading,
            catalogError = previewCase.catalogError,
            onQueryChange = {},
            onSelectAnyLine = {},
            onSelectLine = {},
            onRetryCatalog = {},
            onBack = {},
        )
    }
}
