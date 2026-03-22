package com.example.routetracker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.routetracker.data.LineSelection
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.data.StopSelection
import com.example.routetracker.presentation.theme.RouteTrackerTheme
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearUiFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun headerTapOpensQuickSwitch_andLongPressOpensFullSetup() {
        val snapshot = RouteRepository.previewSnapshot(selection = sampleAnyPlatformSelection())
        val routeRepo = RouteRepository(context)
        var quickSwitchOpened = false
        var fullSetupOpened = false

        setRouteTrackerContent {
            BoardScreen(
                selection = snapshot.selection,
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
                onOpenQuickRouteSwitch = { quickSwitchOpened = true },
                onOpenRouteSetup = { fullSetupOpened = true },
                onOpenDepartureDetails = {},
                onRefresh = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.HEADER_CARD).performClick()
        composeRule.runOnIdle {
            assertTrue("Quick switch should open on tap.", quickSwitchOpened)
        }

        composeRule.onNodeWithTag(UiTestTags.HEADER_CARD).performTouchInput {
            longClick()
        }
        composeRule.runOnIdle {
            assertTrue("Full route setup should open on long press.", fullSetupOpened)
        }
    }

    @Test
    fun boardRowShowsCompactPlatformAndDelayBadge() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = RouteRepository.previewSnapshot(selection = selection)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
            delayMinutes = 1,
        )
        val routeRepo = RouteRepository(context)

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

        composeRule.onNodeWithTag(
            UiTestTags.departurePlatform(departure.rowKey),
            useUnmergedTree = true,
        )
            .assertTextEquals("2")
        composeRule.onNodeWithTag(
            UiTestTags.departureDelay(departure.rowKey),
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .assertTextEquals("+1m")
    }

    @Test
    fun boardRowShowsPlatformWhenOriginPlatformIsPreselected() {
        val selection = samplePinnedSelection()
        val snapshot = RouteRepository.previewSnapshot(selection = selection)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
            delayMinutes = 1,
        )
        val routeRepo = RouteRepository(context)

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

        composeRule.onNodeWithTag(
            UiTestTags.departurePlatform(departure.rowKey),
            useUnmergedTree = true,
        )
            .assertTextEquals("2")
        composeRule.onNodeWithTag(
            UiTestTags.departureDelay(departure.rowKey),
            useUnmergedTree = true,
        )
            .assertIsDisplayed()
            .assertTextEquals("+1m")
    }

    @Test
    fun quickSwitchShowsFavoritesBeforeNewRoute_andAppliesFavorite() {
        val currentSelection = samplePinnedSelection()
        val favoriteA = currentSelection
        val favoriteB = sampleAnyPlatformSelection().copy(line = null)
        var appliedSelection: RouteSelection? = null

        setRouteTrackerContent {
            QuickRouteSwitchScreen(
                currentSelection = currentSelection,
                favoriteRoutes = listOf(favoriteA, favoriteB),
                onSwapRoute = {},
                onApplyFavorite = { appliedSelection = it },
                onEditFavorite = {},
                onDeleteFavorite = {},
                onOpenRouteSetup = {},
            )
        }

        val firstFavorite = composeRule.onNodeWithTag(UiTestTags.favoriteRouteCard(favoriteA.stableKey))
        val newRoute = composeRule.onNodeWithTag(UiTestTags.QUICK_SWITCH_NEW_ROUTE_BUTTON)

        firstFavorite.assertIsDisplayed()
        newRoute.assertIsDisplayed()

        val favoriteTop = firstFavorite.fetchSemanticsNode().boundsInRoot.top
        val newRouteTop = newRoute.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Favorites should appear above the New route action.", favoriteTop < newRouteTop)

        firstFavorite.performClick()
        composeRule.runOnIdle {
            assertEquals(favoriteA.stableKey, appliedSelection?.stableKey)
        }
    }

    @Test
    fun quickSwitchSwapAppliesSelectionBeforeFavorites() {
        val currentSelection = samplePinnedSelection()
        val favorite = sampleAnyPlatformSelection().copy(line = null)
        var swappedSelection: RouteSelection? = null

        setRouteTrackerContent {
            QuickRouteSwitchScreen(
                currentSelection = currentSelection,
                favoriteRoutes = listOf(favorite),
                onSwapRoute = { swappedSelection = it },
                onApplyFavorite = {},
                onEditFavorite = {},
                onDeleteFavorite = {},
                onOpenRouteSetup = {},
            )
        }

        val swapButton = composeRule.onNodeWithTag(UiTestTags.QUICK_SWITCH_SWAP_BUTTON)
        val firstFavorite = composeRule.onNodeWithTag(UiTestTags.favoriteRouteCard(favorite.stableKey))

        swapButton.assertIsDisplayed()
        firstFavorite.assertIsDisplayed()

        val swapTop = swapButton.fetchSemanticsNode().boundsInRoot.top
        val favoriteTop = firstFavorite.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Swap action should appear above favorites.", swapTop < favoriteTop)

        swapButton.performTouchInput { doubleClick(center) }
        composeRule.runOnIdle {
            val applied = swappedSelection
            requireNotNull(applied)
            assertEquals(currentSelection.destination, applied.origin)
            assertEquals(currentSelection.origin, applied.destination)
            assertEquals(currentSelection.line, applied.line)
        }
    }

    @Test
    fun favoriteLongPressOpensEditAndDeleteMenu() {
        val favorite = sampleAnyPlatformSelection()

        setRouteTrackerContent {
            QuickRouteSwitchScreen(
                currentSelection = favorite,
                favoriteRoutes = listOf(favorite),
                onSwapRoute = {},
                onApplyFavorite = {},
                onEditFavorite = {},
                onDeleteFavorite = {},
                onOpenRouteSetup = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.favoriteRouteCard(favorite.stableKey))
            .performTouchInput { longClick() }

        composeRule.onNodeWithText("Edit favorite").assertIsDisplayed()
        composeRule.onNodeWithText("Delete favorite").assertIsDisplayed()
    }

    @Test
    fun tripDetailsShowsBoardingPlatformAndCloseDismisses() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = RouteRepository.previewSnapshot(selection = selection)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
        )
        val routeRepo = RouteRepository(context)
        var dismissed = false

        setRouteTrackerContent {
            DepartureDetailsScreen(
                selection = selection,
                departure = departure,
                routeRepo = routeRepo,
                currentSystemTime = ZonedDateTime.now(),
                showSecondsEnabled = false,
                isRefreshing = false,
                onRefresh = {},
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithText("Boarding platform").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        clickTagAfterScrolling(UiTestTags.TRIP_DETAILS_CLOSE_BUTTON)
        composeRule.runOnIdle {
            assertTrue("Close should dismiss trip details.", dismissed)
        }
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
                stationName = "Nádraží Vršovice",
                stopIds = listOf("stop-c"),
            ),
            line = LineSelection(shortName = "7"),
        )
    }

    private fun samplePinnedSelection(): RouteSelection {
        return sampleAnyPlatformSelection().copy(
            origin = StopSelection(
                stationKey = "station:palmovka",
                stationName = "Palmovka",
                platformKey = "platform-2",
                platformLabel = "Platform 2",
                stopIds = listOf("stop-b"),
            ),
            destination = StopSelection(
                stationKey = "station:vrsovice",
                stationName = "Nádraží Vršovice",
                platformKey = "platform-4",
                platformLabel = "Platform 4",
                stopIds = listOf("stop-c"),
            ),
        )
    }

    private fun setRouteTrackerContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            RouteTrackerTheme {
                content()
            }
        }
    }

    private fun clickTagAfterScrolling(tag: String, maxSwipes: Int = 6) {
        var lastError: AssertionError? = null
        repeat(maxSwipes + 1) { attempt ->
            try {
                composeRule.onNodeWithTag(tag).performClick()
                return
            } catch (error: AssertionError) {
                lastError = error
                if (attempt == maxSwipes) {
                    return@repeat
                }
                composeRule.onRoot().performTouchInput { swipeUp() }
                composeRule.waitForIdle()
            }
        }
        throw lastError ?: AssertionError("Expected tag $tag to be clickable.")
    }
}
