package com.example.routetracker.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.StateBuilders
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.tiles.DeviceParametersBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.renderer.TileRenderer
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import com.example.routetracker.complication.MainComplicationService
import com.example.routetracker.presentation.preview.WearPreviewFixtures
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import com.example.routetracker.tile.buildPreviewTile
import com.example.routetracker.tile.tileResourcesForPreview
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.util.concurrent.MoreExecutors
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class WearScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardWithDepartures() {
        setBoardScreenContent(boardCase("Populated"))
        captureScreen("small-round/board_with_departures.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardWithPlatformAndDelay() {
        setBoardScreenContent(boardCase("Platform Delay"))
        captureScreen("small-round/board_with_platform_delay.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardWithPinnedPlatformAndDelay() {
        setBoardScreenContent(boardCase("Pinned Platform Delay"))
        captureScreen("small-round/board_with_pinned_platform_delay.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardLongLabels() {
        setBoardScreenContent(boardCase("Long Labels"))
        captureScreen("small-round/board_long_labels.png")
    }

    @Test
    @Config(qualifiers = LARGE_ROUND_QUALIFIERS)
    fun largeRound_boardLoadingState() {
        setBoardScreenContent(boardCase("Loading"))
        captureScreen("large-round/board_loading_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardPausedState() {
        setBoardScreenContent(boardCase("Paused"))
        captureScreen("small-round/board_paused_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardRateLimitedState() {
        setBoardScreenContent(boardCase("Rate Limited"))
        captureScreen("small-round/board_rate_limited_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardCachedState() {
        val populatedCase = boardCase("Populated")
        val snapshot = checkNotNull(populatedCase.snapshot).copy(isStale = true)
        setBoardScreenContent(
            populatedCase.copy(
                snapshot = snapshot,
                currentSystemTime = snapshot.fetchedAt.plusSeconds(35),
            ),
        )
        captureScreen("small-round/board_cached_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardErrorState() {
        val populatedCase = boardCase("Populated")
        val snapshot = checkNotNull(populatedCase.snapshot).copy(
            departures = emptyList(),
            isStale = true,
            errorMessage = "Unable to load live departures.",
            refreshFailureKind = com.example.routetracker.data.DepartureRefreshFailureKind.OTHER,
        )
        setBoardScreenContent(
            populatedCase.copy(
                snapshot = snapshot,
                departures = emptyList(),
                currentSystemTime = snapshot.fetchedAt.plusSeconds(35),
            ),
        )
        captureScreen("small-round/board_error_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_settingsDefault() {
        val previewData = WearPreviewFixtures.settingsData()
        setRouteTrackerContent {
            SettingsScreen(
                showSecondsEnabled = previewData.showSecondsEnabled,
                detailsDialogAutoRefreshEnabled = previewData.detailsDialogAutoRefreshEnabled,
                verifiedMatchCount = previewData.verifiedMatchCount,
                transitCatalogLastRefreshLabel = previewData.transitCatalogLastRefreshLabel,
                liveSnapshotCacheLabel = previewData.liveSnapshotCacheLabel,
                gtfsTripDetailCacheLabel = previewData.gtfsTripDetailCacheLabel,
                vehiclePositionCacheLabel = previewData.vehiclePositionCacheLabel,
                apiKeySourceLabel = previewData.apiKeySourceLabel,
                onToggleShowSeconds = {},
                onToggleDetailsDialogAutoRefresh = {},
                onDecreaseVerifiedMatchCount = {},
                onIncreaseVerifiedMatchCount = {},
                onRefreshTransitCatalog = {},
                onCycleLiveSnapshotCache = {},
                onCycleGtfsTripDetailCache = {},
                onCycleVehiclePositionCache = {},
                onEditApiKey = {},
                onDismiss = {},
            )
        }
        captureScreen("small-round/settings_default.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_apiKeySettingsOverride() {
        val previewCase = WearPreviewFixtures.apiKeyCases().first { it.name == "Watch Override" }
        setRouteTrackerContent(showTimeText = false) {
            ApiKeySettingsScreen(
                value = previewCase.value,
                sourceLabel = previewCase.sourceLabel,
                onValueChange = {},
                onSave = {},
                onUseBuiltIn = {},
                onDismiss = {},
            )
        }
        captureScreen("small-round/settings_api_key.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_apiKeySettingsBuiltIn() {
        val previewCase = WearPreviewFixtures.apiKeyCases().first { it.name == "Built In" }
        setRouteTrackerContent(showTimeText = false) {
            ApiKeySettingsScreen(
                value = previewCase.value,
                sourceLabel = previewCase.sourceLabel,
                onValueChange = {},
                onSave = {},
                onUseBuiltIn = {},
                onDismiss = {},
            )
        }
        captureScreen("small-round/settings_api_key_built_in.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_quickSwitchFavorites() {
        val previewCase = WearPreviewFixtures.quickSwitchCases().first { it.name == "Favorites" }
        setRouteTrackerContent {
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
        captureScreen("small-round/quick_switch.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_quickSwitchEmpty() {
        val previewCase = WearPreviewFixtures.quickSwitchCases().first { it.name == "Empty" }
        setRouteTrackerContent {
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
        captureScreen("small-round/quick_switch_empty.png")
    }

    @Test
    @Config(qualifiers = LARGE_ROUND_QUALIFIERS)
    fun largeRound_routeSetupHome() {
        val previewCase = WearPreviewFixtures.routeSetupHomeCases().first { it.name == "Normal" }
        setRouteTrackerContent {
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
        captureScreen("large-round/route_setup_home.png")
    }

    @Test
    @Config(qualifiers = LARGE_ROUND_QUALIFIERS)
    fun largeRound_routeSetupLoading() {
        val previewCase = WearPreviewFixtures.routeSetupHomeCases().first { it.name == "Loading" }
        setRouteTrackerContent {
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
        captureScreen("large-round/route_setup_loading.png")
    }

    @Test
    @Config(qualifiers = LARGE_ROUND_QUALIFIERS)
    fun largeRound_routeSetupError() {
        val previewCase = WearPreviewFixtures.routeSetupHomeCases().first { it.name == "Error" }
        setRouteTrackerContent {
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
        captureScreen("large-round/route_setup_error.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_stationSearchResults() {
        val previewCase = WearPreviewFixtures.stationSearchCases().first { it.name == "Results" }
        setRouteTrackerContent(showTimeText = false) {
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
        captureScreen("small-round/station_search_results.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_stationSearchEmpty() {
        val previewCase = WearPreviewFixtures.stationSearchCases().first { it.name == "Empty Query" }
        setRouteTrackerContent(showTimeText = false) {
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
        captureScreen("small-round/station_search_empty.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_stationSearchError() {
        val previewCase = WearPreviewFixtures.stationSearchCases().first { it.name == "Error" }
        setRouteTrackerContent(showTimeText = false) {
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
        captureScreen("small-round/station_search_error.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_platformPickerStress() {
        val previewCase = WearPreviewFixtures.platformPickerCases().first()
        setRouteTrackerContent(showTimeText = false) {
            RoutePlatformPickerPage(
                target = previewCase.target,
                station = previewCase.station,
                onSelectPlatform = {},
                onBack = {},
            )
        }
        captureScreen("small-round/platform_picker_stress.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_lineSearchAnyLine() {
        val previewCase = WearPreviewFixtures.lineSearchCases().first { it.name == "Any Line" }
        setRouteTrackerContent(showTimeText = false) {
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
        captureScreen("small-round/line_search_any_line.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_lineSearchResults() {
        val previewCase = WearPreviewFixtures.lineSearchCases().first { it.name == "Results" }
        setRouteTrackerContent(showTimeText = false) {
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
        captureScreen("small-round/line_search_results.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_tripDetails() {
        val previewCase = WearPreviewFixtures.departureDetailsCases().first { it.name == "Vehicle Present" }
        setRouteTrackerContent {
            DepartureDetailsScreen(
                uiState = previewCase.uiState,
                onRefresh = {},
                onDismiss = {},
            )
        }
        captureScreen("small-round/trip_details.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_tripDetailsVehicleUnavailable() {
        val previewCase = WearPreviewFixtures.departureDetailsCases().first { it.name == "Vehicle Unavailable" }
        setRouteTrackerContent {
            DepartureDetailsScreen(
                uiState = previewCase.uiState,
                onRefresh = {},
                onDismiss = {},
            )
        }
        captureScreen("small-round/trip_details_vehicle_unavailable.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_tilePopulated() {
        setTilePreviewContent(
            snapshot = WearPreviewFixtures.populatedTileSnapshot(),
            width = SMALL_ROUND_SIZE_DP,
            height = SMALL_ROUND_SIZE_DP,
        )
        captureScreen("small-round/tile_populated.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_tileFallback() {
        setTilePreviewContent(
            snapshot = WearPreviewFixtures.fallbackTileSnapshot(),
            width = SMALL_ROUND_SIZE_DP,
            height = SMALL_ROUND_SIZE_DP,
        )
        captureScreen("small-round/tile_fallback.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_complicationShortTextPreview() {
        setComplicationPreviewContent(
            complicationData = mainComplicationPreview(ComplicationType.SHORT_TEXT),
            width = 84,
            height = 84,
        )
        captureScreen("small-round/complication_short_text_preview.png")
    }

    @Test
    @Config(qualifiers = LARGE_ROUND_QUALIFIERS)
    fun largeRound_complicationLongTextPreview() {
        setComplicationPreviewContent(
            complicationData = mainComplicationPreview(ComplicationType.LONG_TEXT),
            width = 232,
            height = 84,
        )
        captureScreen("large-round/complication_long_text_preview.png")
    }

    private fun boardCase(name: String) = WearPreviewFixtures.boardCases().first { it.name == name }

    private fun captureScreen(relativePath: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = screenshotFile(relativePath).absolutePath,
            roborazziOptions = RoborazziOptions(taskType = screenshotTaskType()),
        )
    }

    private fun setRouteTrackerContent(
        showTimeText: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            RouteTrackerTheme {
                RoundScreenScreenshotFrame {
                    RouteTrackerAppScaffold(
                        timeText = {
                            if (showTimeText) {
                                TimeText(timeSource = FixedTimeSource)
                            }
                        },
                    ) {
                        content()
                    }
                }
            }
        }
    }

    private fun setBoardScreenContent(previewCase: com.example.routetracker.presentation.preview.BoardPreviewCase) {
        setRouteTrackerContent {
            BoardScreen(
                selection = previewCase.selection,
                departures = previewCase.departures,
                snapshot = previewCase.snapshot,
                statusText = WearPreviewFixtures.statusText(
                    snapshot = previewCase.snapshot,
                    autoUpdatesEnabled = previewCase.autoUpdatesEnabled,
                    isRefreshing = previewCase.isRefreshing,
                ),
                currentSystemTime = previewCase.currentSystemTime,
                showSecondsEnabled = false,
                autoUpdatesEnabled = previewCase.autoUpdatesEnabled,
                isRefreshing = previewCase.isRefreshing,
                onOpenSettings = {},
                onToggleAutoUpdates = {},
                onOpenQuickRouteSwitch = {},
                onOpenDepartureDetails = {},
                onRefresh = {},
                animateFreshnessHalo = false,
            )
        }
    }

    private fun setTilePreviewContent(
        snapshot: com.example.routetracker.data.DepartureSnapshot,
        width: Int,
        height: Int,
    ) {
        val bitmap = renderTileBitmap(
            snapshot = snapshot,
            width = width,
            height = height,
        )
        composeRule.setContent {
            RoundScreenScreenshotFrame {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun setComplicationPreviewContent(
        complicationData: ComplicationData,
        width: Int,
        height: Int,
    ) {
        val bitmap = renderComplicationBitmap(
            complicationData = complicationData,
            width = width,
            height = height,
        )
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                )
            }
        }
    }

    @Composable
    private fun RoundScreenScreenshotFrame(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            ) {
                content()
            }
        }
    }

    private fun renderTileBitmap(
        snapshot: com.example.routetracker.data.DepartureSnapshot,
        width: Int,
        height: Int,
    ): Bitmap {
        val deviceParameters = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(width)
            .setScreenHeightDp(height)
            .setScreenDensity(1f)
            .setDevicePlatform(DeviceParametersBuilders.DEVICE_PLATFORM_WEAR_OS)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .build()
        val tileRequest = RequestBuilders.TileRequest.Builder()
            .setTileId(1)
            .setDeviceParameters(deviceParameters)
            .build()
        val resourcesRequest = RequestBuilders.ResourcesRequest.Builder()
            .setTileId(1)
            .setVersion("0")
            .setDeviceParameters(deviceParameters)
            .build()
        val tile = buildPreviewTile(
            requestParams = tileRequest,
            context = composeRule.activity,
            snapshot = snapshot,
            showSecondsEnabled = false,
        )
        val layout = checkNotNull(tile.tileTimeline)
            .timelineEntries
            .first()
            .layout
        val resources = tileResourcesForPreview(resourcesRequest)
        val renderer = TileRenderer(
            composeRule.activity,
            MoreExecutors.directExecutor(),
        ) { _: StateBuilders.State -> }
        val parent = FrameLayout(composeRule.activity).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
        }
        val view = renderer.inflateAsync(
            layout,
            resources,
            parent,
        ).get()
        attachAndMeasureView(parent, view, width, height)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            parent.draw(Canvas(bitmap))
        }
    }

    private fun attachAndMeasureView(
        parent: FrameLayout,
        child: View,
        width: Int,
        height: Int,
    ) {
        if (child.parent == null) {
            parent.addView(
                child,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        parent.measure(widthSpec, heightSpec)
        parent.layout(0, 0, width, height)
        child.measure(widthSpec, heightSpec)
        child.layout(0, 0, width, height)
    }

    private fun renderComplicationBitmap(
        complicationData: ComplicationData,
        width: Int,
        height: Int,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val drawable = ComplicationDrawable(composeRule.activity).apply {
            setBounds(Rect(0, 0, width, height))
            currentTime = FIXED_NOW.toInstant()
            setComplicationData(complicationData, false)
        }
        drawable.draw(Canvas(bitmap))
        return bitmap
    }

    private fun screenshotFile(relativePath: String): File {
        val moduleDir = System.getProperty("routeTracker.moduleDir")
            ?.let(::File)
            ?: File(".")
        return moduleDir.resolve("src/test/screenshots/$relativePath").also { file ->
            file.parentFile?.mkdirs()
        }
    }

    private fun screenshotTaskType(): RoborazziTaskType {
        return when (System.getProperty("routeTracker.screenshotTaskType")) {
            "record" -> RoborazziTaskType.Record
            "verify" -> RoborazziTaskType.Verify
            else -> RoborazziTaskType.Compare
        }
    }

    private fun mainComplicationPreview(type: ComplicationType): ComplicationData {
        return checkNotNull(MainComplicationService().getPreviewData(type)) {
            "Missing preview data for $type"
        }
    }

    private object FixedTimeSource : TimeSource {
        @Composable
        override fun currentTime(): String = "18:30"
    }

    private companion object {
        const val SMALL_ROUND_QUALIFIERS = "w220dp-h220dp-round"
        const val LARGE_ROUND_QUALIFIERS = "w280dp-h280dp-round"
        const val SMALL_ROUND_SIZE_DP = 220
        val FIXED_NOW: ZonedDateTime = ZonedDateTime.of(
            2026,
            3,
            16,
            12,
            0,
            0,
            0,
            ZoneId.of("Europe/Prague"),
        )
    }
}
