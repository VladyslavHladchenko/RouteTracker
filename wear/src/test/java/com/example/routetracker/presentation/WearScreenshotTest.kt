package com.example.routetracker.presentation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.example.routetracker.data.LineSelection
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.data.StopSelection
import com.example.routetracker.presentation.theme.RouteTrackerTheme
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
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection)
        val routeRepo = RouteRepository(composeRule.activity)

        setRouteTrackerContent {
            BoardScreen(
                selection = selection,
                departures = snapshot.departures,
                snapshot = snapshot,
                statusText = "Live | 18:30",
                routeRepo = routeRepo,
                currentSystemTime = snapshot.fetchedAt,
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
        captureScreen("small-round/board_with_departures.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardWithPlatformAndDelay() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection)
        val routeRepo = RouteRepository(composeRule.activity)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
            delayMinutes = 1,
        )

        setRouteTrackerContent {
            BoardScreen(
                selection = selection,
                departures = listOf(departure),
                snapshot = snapshot.copy(departures = listOf(departure)),
                statusText = "Live | 18:30",
                routeRepo = routeRepo,
                currentSystemTime = snapshot.fetchedAt,
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
        captureScreen("small-round/board_with_platform_delay.png")
    }

    @Test
    @Config(qualifiers = SMALL_ROUND_QUALIFIERS)
    fun smallRound_boardWithPinnedPlatformAndDelay() {
        val selection = samplePinnedSelection()
        val snapshot = previewSnapshot(selection)
        val routeRepo = RouteRepository(composeRule.activity)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
            delayMinutes = 1,
        )

        setRouteTrackerContent {
            BoardScreen(
                selection = selection,
                departures = listOf(departure),
                snapshot = snapshot.copy(departures = listOf(departure)),
                statusText = "Live | 18:30",
                routeRepo = routeRepo,
                currentSystemTime = snapshot.fetchedAt,
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
        captureScreen("small-round/board_with_pinned_platform_delay.png")
    }

    @Test
    @Config(qualifiers = LARGE_ROUND_QUALIFIERS)
    fun largeRound_boardLoadingState() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = previewSnapshot(selection)
        val routeRepo = RouteRepository(composeRule.activity)

        setRouteTrackerContent {
            BoardScreen(
                selection = selection,
                departures = emptyList(),
                snapshot = null,
                statusText = "Loading",
                routeRepo = routeRepo,
                currentSystemTime = snapshot.fetchedAt,
                showSecondsEnabled = false,
                autoUpdatesEnabled = true,
                isRefreshing = true,
                onOpenSettings = {},
                onToggleAutoUpdates = {},
                onOpenQuickRouteSwitch = {},
                onOpenRouteSetup = {},
                onOpenDepartureDetails = {},
                onRefresh = {},
            )
        }
        captureScreen("large-round/board_loading_state.png")
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
                // Keep the legacy favorite-list framing stable here; swap behavior is covered in WearUiFlowTest.
                showSwapAction = false,
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
                onDismiss = {},
            )
        }
        captureScreen("small-round/trip_details.png")
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
                stationName = "Nádraží Vršovice",
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

    private companion object {
        const val SMALL_ROUND_QUALIFIERS = "w220dp-h220dp-round"
        const val LARGE_ROUND_QUALIFIERS = "w280dp-h280dp-round"
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
