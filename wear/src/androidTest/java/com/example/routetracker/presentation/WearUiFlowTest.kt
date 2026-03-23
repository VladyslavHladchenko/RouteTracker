package com.example.routetracker.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
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
    fun boardUsesVisiblePrimaryActions() {
        val snapshot = RouteRepository.previewSnapshot(selection = sampleAnyPlatformSelection())
        var quickSwitchOpened = false
        var refreshed = false

        setRouteTrackerContent {
            BoardScreen(
                selection = snapshot.selection,
                departures = snapshot.departures,
                snapshot = snapshot,
                statusText = "Live | Updated",
                currentSystemTime = snapshot.fetchedAt,
                showSecondsEnabled = false,
                autoUpdatesEnabled = true,
                isRefreshing = false,
                onOpenSettings = {},
                onToggleAutoUpdates = {},
                onOpenQuickRouteSwitch = { quickSwitchOpened = true },
                onOpenDepartureDetails = {},
                onRefresh = { refreshed = true },
            )
        }

        composeRule.onNodeWithTag(UiTestTags.BOARD_CHANGE_ROUTE_BUTTON).performClick()
        composeRule.onNodeWithTag(UiTestTags.BOARD_REFRESH_BUTTON).performClick()

        composeRule.runOnIdle {
            assertTrue("Change route should be visible and tappable.", quickSwitchOpened)
            assertTrue("Refresh should be visible and tappable.", refreshed)
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

        setRouteTrackerContent {
            BoardScreen(
                selection = selection,
                departures = listOf(departure),
                snapshot = snapshot.copy(departures = listOf(departure)),
                statusText = "Live | Updated",
                currentSystemTime = snapshot.fetchedAt,
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

        composeRule.onNodeWithTag(
            UiTestTags.departurePlatform(departure.rowKey),
            useUnmergedTree = true,
        ).assertTextEquals("2")
        composeRule.onNodeWithTag(
            UiTestTags.departureDelay(departure.rowKey),
            useUnmergedTree = true,
        ).assertIsDisplayed().assertTextEquals("+1m")
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
    fun quickSwitchSwapButtonAppliesSwappedSelection() {
        val currentSelection = samplePinnedSelection()
        var swappedSelection: RouteSelection? = null

        setRouteTrackerContent {
            QuickRouteSwitchScreen(
                currentSelection = currentSelection,
                favoriteRoutes = emptyList(),
                onSwapRoute = { swappedSelection = it },
                onApplyFavorite = {},
                onEditFavorite = {},
                onDeleteFavorite = {},
                onOpenRouteSetup = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.QUICK_SWITCH_SWAP_BUTTON).performClick()
        composeRule.runOnIdle {
            val applied = swappedSelection
            requireNotNull(applied)
            assertEquals(currentSelection.destination, applied.origin)
            assertEquals(currentSelection.origin, applied.destination)
            assertEquals(currentSelection.line, applied.line)
        }
    }

    @Test
    fun favoriteSwipeRevealsEditAndDeleteActions() {
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
            .performTouchInput { swipeLeft() }

        composeRule.onNodeWithTag(
            UiTestTags.favoriteRouteEditAction(favorite.stableKey),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            UiTestTags.favoriteRouteDeleteAction(favorite.stableKey),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun routeSetupApplyUsesEdgeButton_andNoCloseFooter() {
        val currentSelection = samplePinnedSelection()
        var applied = false

        setRouteTrackerContent {
            RouteSetupHomePage(
                draftSelection = currentSelection,
                favoriteRoutes = listOf(currentSelection),
                isEditingFavorite = false,
                isCatalogLoading = false,
                catalogError = null,
                onChooseOrigin = {},
                onChooseDestination = {},
                onChooseLine = {},
                onToggleFavorite = {},
                onApplyFavorite = {},
                onApplyRoute = { applied = true },
                onRetryCatalog = {},
                onClose = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.ROUTE_SETUP_APPLY_BUTTON).performClick()
        composeRule.onNodeWithText("Close").assertDoesNotExist()
        composeRule.runOnIdle {
            assertTrue("Apply route should be driven by the edge button.", applied)
        }
    }

    @Test
    fun tripDetailsShowsBoardingPlatformAndRefresh_withoutCloseFooter() {
        val selection = sampleAnyPlatformSelection()
        val snapshot = RouteRepository.previewSnapshot(selection = selection)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
        )
        val routeRepo = RouteRepository(context)
        var refreshCount = 0

        setRouteTrackerContent {
            DepartureDetailsScreen(
                selection = selection,
                departure = departure,
                routeRepo = routeRepo,
                currentSystemTime = ZonedDateTime.now(),
                showSecondsEnabled = false,
                isRefreshing = false,
                onRefresh = { refreshCount += 1 },
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("Boarding platform").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.TRIP_DETAILS_REFRESH_BUTTON).performClick()
        composeRule.onNodeWithText("Close").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, refreshCount)
        }
    }

    @Test
    fun apiKeyScreenKeepsSemanticActions_withoutBackFooter() {
        setRouteTrackerContent {
            ApiKeySettingsScreen(
                value = "demo",
                sourceLabel = "Watch override",
                onValueChange = {},
                onSave = {},
                onUseBuiltIn = {},
                onDismiss = {},
            )
        }

        composeRule.onNodeWithTag(UiTestTags.SETTINGS_API_KEY_SAVE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.SETTINGS_API_KEY_CLEAR_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithText("Back").assertDoesNotExist()
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
                stationName = "Nadrazi Vrsovice",
                platformKey = "platform-4",
                platformLabel = "Platform 4",
                stopIds = listOf("stop-c"),
            ),
        )
    }

    private fun setRouteTrackerContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            RouteTrackerTheme {
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

    private object FixedTimeSource : TimeSource {
        @Composable
        override fun currentTime(): String = "18:30"
    }
}
