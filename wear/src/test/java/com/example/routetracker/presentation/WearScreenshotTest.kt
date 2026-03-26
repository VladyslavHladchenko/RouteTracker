package com.example.routetracker.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import com.example.routetracker.complication.MainComplicationService
import com.example.routetracker.data.DepartureRefreshFailureKind
import com.example.routetracker.data.LineSelection
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.data.StopSelection
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection)

        setBoardScreenContent(
            selection = selection,
            snapshot = snapshot,
            currentSystemTime = snapshot.fetchedAt.plusSeconds(8),
            autoUpdatesEnabled = true,
            isRefreshing = false,
        )
        captureScreen("small-round/board_with_departures.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardWithPlatformAndDelay() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
            delayMinutes = 1,
        )

        setBoardScreenContent(
            selection = selection,
            snapshot = snapshot.copy(departures = listOf(departure)),
            currentSystemTime = snapshot.fetchedAt.plusSeconds(8),
            autoUpdatesEnabled = true,
            isRefreshing = false,
        )
        captureScreen("small-round/board_with_platform_delay.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardWithPinnedPlatformAndDelay() {
        val selection = samplePinnedSelection()
        val snapshot = previewSnapshot(selection)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
            delayMinutes = 1,
        )

        setBoardScreenContent(
            selection = selection,
            snapshot = snapshot.copy(departures = listOf(departure)),
            currentSystemTime = snapshot.fetchedAt.plusSeconds(8),
            autoUpdatesEnabled = true,
            isRefreshing = false,
        )
        captureScreen("small-round/board_with_pinned_platform_delay.png")
    }

    @Test
    @Config(qualifiers = LARGE_ROUND_QUALIFIERS)
    fun largeRound_boardLoadingState() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection)

        setBoardScreenContent(
            selection = selection,
            snapshot = null,
            currentSystemTime = snapshot.fetchedAt,
            autoUpdatesEnabled = true,
            isRefreshing = true,
        )
        captureScreen("large-round/board_loading_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardPausedState() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection).copy(isStale = true, errorMessage = "Updates paused.")

        setBoardScreenContent(
            selection = selection,
            snapshot = snapshot,
            currentSystemTime = snapshot.fetchedAt.plusSeconds(12),
            autoUpdatesEnabled = false,
            isRefreshing = false,
        )
        captureScreen("small-round/board_paused_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardCachedState() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection).copy(isStale = true)

        setBoardScreenContent(
            selection = selection,
            snapshot = snapshot,
            currentSystemTime = snapshot.fetchedAt.plusSeconds(35),
            autoUpdatesEnabled = true,
            isRefreshing = false,
        )
        captureScreen("small-round/board_cached_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardRateLimitedState() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection).copy(
            isStale = true,
            errorMessage = "Showing cached data. Rate limited.",
            refreshFailureKind = DepartureRefreshFailureKind.RATE_LIMITED,
        )

        setBoardScreenContent(
            selection = selection,
            snapshot = snapshot,
            currentSystemTime = snapshot.fetchedAt.plusSeconds(35),
            autoUpdatesEnabled = true,
            isRefreshing = false,
        )
        captureScreen("small-round/board_rate_limited_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardErrorState() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection).copy(
            departures = emptyList(),
            isStale = true,
            errorMessage = "Unable to load live departures.",
            refreshFailureKind = DepartureRefreshFailureKind.OTHER,
        )

        setBoardScreenContent(
            selection = selection,
            snapshot = snapshot,
            currentSystemTime = snapshot.fetchedAt.plusSeconds(35),
            autoUpdatesEnabled = true,
            isRefreshing = false,
        )
        captureScreen("small-round/board_error_state.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_quickSwitchFavorites() {
        val currentSelection = samplePinnedSelection()
        val favoriteRoutes = listOf(
            currentSelection,
            sampleAnyPlatformSelection().copy(line = null),
        )

        setRouteTrackerContent {
            QuickRouteSwitchScreen(
                currentSelection = currentSelection,
                favoriteRoutes = favoriteRoutes,
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
    fun smallRound_apiKeySettings() {
        setRouteTrackerContent {
            ApiKeySettingsScreen(
                value = "pid-demo-key-1234",
                sourceLabel = "Watch override",
                onValueChange = {},
                onSave = {},
                onUseBuiltIn = {},
                onDismiss = {},
            )
        }
        captureScreen("small-round/settings_api_key.png")
    }

    @Test
    @Config(qualifiers = LARGE_ROUND_QUALIFIERS)
    fun largeRound_routeSetupHome() {
        val currentSelection = samplePinnedSelection()
        val favorites = listOf(currentSelection, sampleAnyPlatformSelection().copy(line = null))

        setRouteTrackerContent {
            RouteSetupHomePage(
                draftSelection = currentSelection,
                favoriteRoutes = favorites,
                isEditingFavorite = false,
                isCatalogLoading = false,
                catalogError = null,
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
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_tripDetails() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection)
        val routeRepo = RouteRepository(composeRule.activity)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
        )

        setRouteTrackerContent {
            DepartureDetailsScreen(
                selection = selection,
                departure = departure,
                routeRepo = routeRepo,
                currentSystemTime = snapshot.fetchedAt,
                showSecondsEnabled = false,
                isRefreshing = false,
                onRefresh = {},
                onDismiss = {},
            )
        }
        captureScreen("small-round/trip_details.png")
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

    private fun captureScreen(relativePath: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = screenshotFile(relativePath).absolutePath,
            roborazziOptions = RoborazziOptions(taskType = screenshotTaskType()),
        )
    }

    private fun setRouteTrackerContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            RouteTrackerTheme {
                RoundScreenScreenshotFrame {
                    RouteTrackerAppScaffold(
                        timeText = {
                            TimeText(timeSource = FixedTimeSource)
                        },
                    ) {
                        content()
                    }
                }
            }
        }
    }

    private fun setBoardScreenContent(
        selection: RouteSelection,
        snapshot: com.example.routetracker.data.DepartureSnapshot?,
        currentSystemTime: ZonedDateTime,
        autoUpdatesEnabled: Boolean,
        isRefreshing: Boolean,
    ) {
        setRouteTrackerContent {
            BoardScreen(
                selection = selection,
                departures = snapshot?.departures.orEmpty(),
                snapshot = snapshot,
                statusText = snapshotStatusText(
                    snapshot = snapshot,
                    autoUpdatesEnabled = autoUpdatesEnabled,
                    isRefreshing = isRefreshing,
                    updatedLabel = snapshot?.fetchedAt?.format(SCREENSHOT_STATUS_TIME_FORMATTER),
                ),
                currentSystemTime = currentSystemTime,
                showSecondsEnabled = false,
                autoUpdatesEnabled = autoUpdatesEnabled,
                isRefreshing = isRefreshing,
                onOpenSettings = {},
                onToggleAutoUpdates = {},
                onOpenQuickRouteSwitch = {},
                onOpenDepartureDetails = {},
                onRefresh = {},
                animateFreshnessHalo = false,
            )
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
        // Keep screenshot baselines honest by showing the same circular viewport as the watch.
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

    private fun createSelection(
        originPlatform: String? = null,
        destinationPlatform: String? = null,
        line: String? = "7",
    ): RouteSelection {
        return RouteSelection(
            origin = StopSelection(
                stationKey = "station:palmovka",
                stationName = "Palmovka",
                platformKey = originPlatform?.lowercase(),
                platformLabel = originPlatform?.let { "Platform $it" },
                stopIds = listOf("stop-a", "stop-b"),
            ),
            destination = StopSelection(
                stationKey = "station:vrsovice",
                stationName = "Nadrazi Vrsovice",
                platformKey = destinationPlatform?.lowercase(),
                platformLabel = destinationPlatform?.let { "Platform $it" },
                stopIds = listOf("stop-c"),
            ),
            line = line?.let(::LineSelection),
        )
    }

    private fun sampleAnyPlatformSelection(): RouteSelection = createSelection(
        originPlatform = null,
        destinationPlatform = null,
        line = "7",
    )

    private fun samplePinnedSelection(): RouteSelection = createSelection(
        originPlatform = "2",
        destinationPlatform = "4",
        line = "7",
    )

    private fun previewSnapshot(selection: RouteSelection) =
        RouteRepository.previewSnapshot(selection = selection, now = FIXED_NOW)

    private object FixedTimeSource : TimeSource {
        @Composable
        override fun currentTime(): String = "18:30"
    }

    private companion object {
        const val SMALL_ROUND_QUALIFIERS = "w220dp-h220dp-round"
        const val LARGE_ROUND_QUALIFIERS = "w280dp-h280dp-round"
        val SCREENSHOT_STATUS_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
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
