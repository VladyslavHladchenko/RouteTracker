package com.example.routetracker.presentation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.routetracker.data.LineSelection
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.data.StopSelection
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class)
class BoardPullToRefreshTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun boardSwipeDownAtTopTriggersRefresh() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = RouteRepository.previewSnapshot(selection)
        val routeRepo = RouteRepository(composeRule.activity)
        var refreshed = false

        composeRule.setContent {
            RouteTrackerTheme {
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
                    onRefresh = { refreshed = true },
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.BOARD_PULL_REFRESH_CONTAINER).performTouchInput {
            swipe(
                start = Offset(x = center.x, y = height * 0.16f),
                end = Offset(x = center.x, y = height * 0.84f),
                durationMillis = 500,
            )
        }
        composeRule.waitUntil(timeoutMillis = 3_000) { refreshed }
    }

    @Test
    fun tripDetailsSwipeDownAtTopTriggersRefresh() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = RouteRepository.previewSnapshot(selection)
        val routeRepo = RouteRepository(composeRule.activity)
        val departure = snapshot.departures.first()
        var refreshed = false

        composeRule.setContent {
            RouteTrackerTheme {
                DepartureDetailsScreen(
                    selection = selection,
                    departure = departure,
                    routeRepo = routeRepo,
                    currentSystemTime = snapshot.fetchedAt,
                    showSecondsEnabled = false,
                    isRefreshing = false,
                    onRefresh = { refreshed = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithTag(UiTestTags.TRIP_DETAILS_PULL_REFRESH_CONTAINER).performTouchInput {
            swipe(
                start = Offset(x = center.x, y = height * 0.16f),
                end = Offset(x = center.x, y = height * 0.84f),
                durationMillis = 500,
            )
        }
        composeRule.waitUntil(timeoutMillis = 3_000) { refreshed }
    }

    @Test
    fun tripDetailsBackgroundRefreshDoesNotShowPullIndicator() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = RouteRepository.previewSnapshot(selection)
        val routeRepo = RouteRepository(composeRule.activity)
        val departure = snapshot.departures.first()

        composeRule.setContent {
            RouteTrackerTheme {
                DepartureDetailsScreen(
                    selection = selection,
                    departure = departure,
                    routeRepo = routeRepo,
                    currentSystemTime = snapshot.fetchedAt,
                    showSecondsEnabled = false,
                    isRefreshing = true,
                    onRefresh = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(UiTestTags.TRIP_DETAILS_PULL_REFRESH_INDICATOR).assertCountEquals(0)
    }

    private fun sampleAnyPlatformSelection(): RouteSelection {
        return RouteSelection(
            origin = StopSelection(
                stationKey = "station:palmovka",
                stationName = "Palmovka",
                stopIds = listOf("stop-a", "stop-b"),
            ),
            destination = StopSelection(
                stationKey = "station:vrsovice",
                stationName = "Nadrazi Vrsovice",
                stopIds = listOf("stop-c"),
            ),
            line = LineSelection(shortName = "7"),
        )
    }
}
