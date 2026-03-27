package com.example.routetracker.presentation.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.example.routetracker.data.BoardStopTime
import com.example.routetracker.data.DepartureRefreshFailureKind
import com.example.routetracker.data.DepartureSnapshot
import com.example.routetracker.data.LineOption
import com.example.routetracker.data.LineSelection
import com.example.routetracker.data.PlatformOption
import com.example.routetracker.data.RouteDeparture
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.data.RouteSelection
import com.example.routetracker.data.StationOption
import com.example.routetracker.data.StopSelection
import com.example.routetracker.data.VehiclePositionDetails
import com.example.routetracker.presentation.DepartureDetailsUiState
import com.example.routetracker.presentation.RouteEndpointTarget
import com.example.routetracker.presentation.snapshotStatusText
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal object WearPreviewFixtures {
    val fixedNow: ZonedDateTime = ZonedDateTime.of(
        2026,
        3,
        16,
        12,
        0,
        0,
        0,
        ZoneId.of("Europe/Prague"),
    )

    private val statusTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun anyPlatformSelection(): RouteSelection = createSelection(
        originStationName = "Palmovka",
        destinationStationName = "Nadrazi Vrsovice",
        originPlatform = null,
        destinationPlatform = null,
        line = "7",
    )

    fun pinnedSelection(): RouteSelection = createSelection(
        originStationName = "Palmovka",
        destinationStationName = "Nadrazi Vrsovice",
        originPlatform = "2",
        destinationPlatform = "4",
        line = "7",
    )

    fun longLabelSelection(): RouteSelection = createSelection(
        originStationName = "Namesti Bratri Synku - smer Kubanske namesti",
        destinationStationName = "Nadrazi Holesovice - vystup A",
        originPlatform = "12",
        destinationPlatform = "Sever",
        line = "26",
    )

    fun previewSnapshot(
        selection: RouteSelection = anyPlatformSelection(),
        now: ZonedDateTime = fixedNow,
    ): DepartureSnapshot {
        return RouteRepository.previewSnapshot(selection = selection, now = now)
    }

    fun boardCases(): List<BoardPreviewCase> {
        val defaultSelection = anyPlatformSelection()
        val baseSnapshot = previewSnapshot(defaultSelection)
        val pinnedSelection = pinnedSelection()
        val pinnedSnapshot = previewSnapshot(pinnedSelection)
        val longLabelSelection = longLabelSelection()
        val longLabelSnapshot = previewSnapshot(longLabelSelection)

        val platformDelayDeparture = baseSnapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
            delayMinutes = 1,
        )
        val pinnedPlatformDelayDeparture = pinnedSnapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
            delayMinutes = 1,
        )

        return listOf(
            BoardPreviewCase(
                name = "Populated",
                selection = defaultSelection,
                snapshot = baseSnapshot,
                departures = baseSnapshot.departures,
                currentSystemTime = baseSnapshot.fetchedAt.plusSeconds(8),
                autoUpdatesEnabled = true,
                isRefreshing = false,
            ),
            BoardPreviewCase(
                name = "Loading",
                selection = defaultSelection,
                snapshot = null,
                departures = emptyList(),
                currentSystemTime = baseSnapshot.fetchedAt,
                autoUpdatesEnabled = true,
                isRefreshing = true,
            ),
            BoardPreviewCase(
                name = "Paused",
                selection = defaultSelection,
                snapshot = baseSnapshot.copy(isStale = true, errorMessage = "Updates paused."),
                departures = baseSnapshot.departures,
                currentSystemTime = baseSnapshot.fetchedAt.plusSeconds(12),
                autoUpdatesEnabled = false,
                isRefreshing = false,
            ),
            BoardPreviewCase(
                name = "Rate Limited",
                selection = defaultSelection,
                snapshot = baseSnapshot.copy(
                    isStale = true,
                    errorMessage = "Showing cached data. Rate limited.",
                    refreshFailureKind = DepartureRefreshFailureKind.RATE_LIMITED,
                ),
                departures = baseSnapshot.departures,
                currentSystemTime = baseSnapshot.fetchedAt.plusSeconds(35),
                autoUpdatesEnabled = true,
                isRefreshing = false,
            ),
            BoardPreviewCase(
                name = "Platform Delay",
                selection = defaultSelection,
                snapshot = baseSnapshot.copy(departures = listOf(platformDelayDeparture)),
                departures = listOf(platformDelayDeparture),
                currentSystemTime = baseSnapshot.fetchedAt.plusSeconds(8),
                autoUpdatesEnabled = true,
                isRefreshing = false,
            ),
            BoardPreviewCase(
                name = "Pinned Platform Delay",
                selection = pinnedSelection,
                snapshot = pinnedSnapshot.copy(departures = listOf(pinnedPlatformDelayDeparture)),
                departures = listOf(pinnedPlatformDelayDeparture),
                currentSystemTime = pinnedSnapshot.fetchedAt.plusSeconds(8),
                autoUpdatesEnabled = true,
                isRefreshing = false,
            ),
            BoardPreviewCase(
                name = "Long Labels",
                selection = longLabelSelection,
                snapshot = longLabelSnapshot,
                departures = longLabelSnapshot.departures,
                currentSystemTime = longLabelSnapshot.fetchedAt.plusSeconds(5),
                autoUpdatesEnabled = true,
                isRefreshing = false,
            ),
        )
    }

    fun settingsData(): SettingsPreviewData {
        return SettingsPreviewData(
            showSecondsEnabled = true,
            detailsDialogAutoRefreshEnabled = true,
            verifiedMatchCount = 4,
            transitCatalogLastRefreshLabel = "Today 11:52",
            liveSnapshotCacheLabel = "2 s",
            gtfsTripDetailCacheLabel = "1 min",
            vehiclePositionCacheLabel = "2 s",
            apiKeySourceLabel = "Built-in key",
        )
    }

    fun apiKeyCases(): List<ApiKeyPreviewCase> {
        return listOf(
            ApiKeyPreviewCase(
                name = "Built In",
                value = "",
                sourceLabel = "Built-in key",
            ),
            ApiKeyPreviewCase(
                name = "Watch Override",
                value = "pid-demo-key-1234",
                sourceLabel = "Watch override",
            ),
        )
    }

    fun quickSwitchCases(): List<QuickSwitchPreviewCase> {
        val currentSelection = pinnedSelection()
        return listOf(
            QuickSwitchPreviewCase(
                name = "Favorites",
                currentSelection = currentSelection,
                favoriteRoutes = listOf(
                    currentSelection,
                    anyPlatformSelection().copy(line = null),
                ),
            ),
            QuickSwitchPreviewCase(
                name = "Empty",
                currentSelection = currentSelection,
                favoriteRoutes = emptyList(),
            ),
        )
    }

    fun routeSetupHomeCases(): List<RouteSetupHomePreviewCase> {
        val currentSelection = pinnedSelection()
        val favorites = listOf(currentSelection, anyPlatformSelection().copy(line = null))
        return listOf(
            RouteSetupHomePreviewCase(
                name = "Normal",
                draftSelection = currentSelection,
                favoriteRoutes = favorites,
                isEditingFavorite = false,
                isCatalogLoading = false,
                catalogError = null,
            ),
            RouteSetupHomePreviewCase(
                name = "Loading",
                draftSelection = currentSelection,
                favoriteRoutes = favorites,
                isEditingFavorite = false,
                isCatalogLoading = true,
                catalogError = null,
            ),
            RouteSetupHomePreviewCase(
                name = "Error",
                draftSelection = currentSelection,
                favoriteRoutes = favorites,
                isEditingFavorite = false,
                isCatalogLoading = false,
                catalogError = "Unable to load stops. Check network and retry.",
            ),
        )
    }

    fun stationSearchCases(): List<StationSearchPreviewCase> {
        return listOf(
            StationSearchPreviewCase(
                name = "Empty Query",
                target = RouteEndpointTarget.ORIGIN,
                query = "",
                results = emptyList(),
                isCatalogLoading = false,
                catalogError = null,
            ),
            StationSearchPreviewCase(
                name = "Results",
                target = RouteEndpointTarget.DESTINATION,
                query = "nad",
                results = sampleStations(),
                isCatalogLoading = false,
                catalogError = null,
            ),
            StationSearchPreviewCase(
                name = "Error",
                target = RouteEndpointTarget.ORIGIN,
                query = "pal",
                results = emptyList(),
                isCatalogLoading = false,
                catalogError = "Unable to load stops. Check network and retry.",
            ),
        )
    }

    fun platformPickerCases(): List<PlatformPickerPreviewCase> {
        return listOf(
            PlatformPickerPreviewCase(
                name = "Stress",
                target = RouteEndpointTarget.ORIGIN,
                station = stressStation(),
            ),
        )
    }

    fun lineSearchCases(): List<LineSearchPreviewCase> {
        return listOf(
            LineSearchPreviewCase(
                name = "Any Line",
                query = "",
                results = emptyList(),
                isCatalogLoading = false,
                catalogError = null,
            ),
            LineSearchPreviewCase(
                name = "Results",
                query = "7",
                results = sampleLines(),
                isCatalogLoading = false,
                catalogError = null,
            ),
        )
    }

    fun departureDetailsCases(): List<DepartureDetailsPreviewCase> {
        val selection = anyPlatformSelection()
        val snapshot = previewSnapshot(selection)
        val departure = snapshot.departures.first().copy(
            boardedStopId = "origin-stop-2",
            boardedPlatformLabel = "Platform 2",
        )
        val unavailableVehicleDeparture = departure.copy(vehiclePositionDetails = null)

        return listOf(
            DepartureDetailsPreviewCase(
                name = "Vehicle Present",
                uiState = departureDetailsUiState(
                    selection = selection,
                    departure = departure,
                    currentSystemTime = fixedNow,
                    showSecondsEnabled = false,
                    isRefreshing = false,
                ),
            ),
            DepartureDetailsPreviewCase(
                name = "Vehicle Unavailable",
                uiState = departureDetailsUiState(
                    selection = selection,
                    departure = unavailableVehicleDeparture,
                    currentSystemTime = fixedNow,
                    showSecondsEnabled = false,
                    isRefreshing = true,
                ),
            ),
        )
    }

    fun populatedTileSnapshot(): DepartureSnapshot = previewSnapshot(anyPlatformSelection())

    fun fallbackTileSnapshot(): DepartureSnapshot = previewSnapshot(anyPlatformSelection()).copy(
        departures = emptyList(),
    )

    fun departureDetailsUiState(
        selection: RouteSelection,
        departure: RouteDeparture,
        currentSystemTime: ZonedDateTime,
        showSecondsEnabled: Boolean,
        isRefreshing: Boolean,
    ): DepartureDetailsUiState {
        return DepartureDetailsUiState(
            clockLabel = departure.clockLabel(
                showSeconds = showSecondsEnabled,
                includeLine = !selection.usesFixedLine(),
            ),
            statusLabel = departure.activityStatusLabel(
                referenceNow = currentSystemTime,
                showSeconds = showSecondsEnabled,
            ),
            refreshHint = if (isRefreshing) {
                "Refreshing live detail..."
            } else {
                "Tap refresh to update live detail."
            },
            boardingPlatform = departure.boardingPlatformCompactLabel ?: "--",
            destinationArrival = departure.destinationArrivalTime?.format(detailFormatter) ?: "--",
            lineLabel = departure.lineLabel,
            departureScheduled = departure.departureBoardDetails.departureTime.scheduledTime.format(detailFormatter),
            departurePredicted = departure.departureBoardDetails.departureTime.predictedTime?.format(detailFormatter)
                ?: "--",
            departureDelay = formatDelaySeconds(departure.departureBoardDetails.delaySeconds),
            originArrivalScheduled = departure.departureBoardDetails.originArrivalTime?.scheduledTime?.format(detailFormatter)
                ?: "--",
            originArrivalPredicted = departure.departureBoardDetails.originArrivalTime?.predictedTime?.format(detailFormatter)
                ?: "--",
            vehicleStatusLabel = if (departure.vehiclePositionDetails != null) "Delay" else "Status",
            vehicleStatusValue = departure.vehiclePositionDetails?.let { formatDelaySeconds(it.delaySeconds) }
                ?: "Not available",
            vehicleOriginTimestamp = departure.vehiclePositionDetails?.originTimestamp?.format(detailFormatter),
        )
    }

    fun statusText(
        snapshot: DepartureSnapshot?,
        autoUpdatesEnabled: Boolean,
        isRefreshing: Boolean,
    ): String {
        return snapshotStatusText(
            snapshot = snapshot,
            autoUpdatesEnabled = autoUpdatesEnabled,
            isRefreshing = isRefreshing,
            updatedLabel = snapshot?.fetchedAt?.format(statusTimeFormatter),
        )
    }

    private fun createSelection(
        originStationName: String,
        destinationStationName: String,
        originPlatform: String?,
        destinationPlatform: String?,
        line: String?,
    ): RouteSelection {
        return RouteSelection(
            origin = StopSelection(
                stationKey = "station:${originStationName.lowercase().replace(' ', '-')}",
                stationName = originStationName,
                platformKey = originPlatform?.lowercase(),
                platformLabel = originPlatform?.let { "Platform $it" },
                stopIds = listOf("stop-a", "stop-b"),
            ),
            destination = StopSelection(
                stationKey = "station:${destinationStationName.lowercase().replace(' ', '-')}",
                stationName = destinationStationName,
                platformKey = destinationPlatform?.lowercase(),
                platformLabel = destinationPlatform?.let { "Platform $it" },
                stopIds = listOf("stop-c"),
            ),
            line = line?.let(::LineSelection),
        )
    }

    private fun sampleStations(): List<StationOption> {
        return listOf(
            StationOption.create(
                stationKey = "station:vrsovice",
                stationName = "Nadrazi Vrsovice",
                stopIds = listOf("stop-c"),
                platforms = listOf(
                    PlatformOption("1", "Platform 1", listOf("stop-c")),
                    PlatformOption("2", "Platform 2", listOf("stop-d")),
                ),
            ),
            StationOption.create(
                stationKey = "station:namesti-bratri-synku",
                stationName = "Namesti Bratri Synku",
                stopIds = listOf("stop-e", "stop-f", "stop-g"),
                platforms = listOf(
                    PlatformOption("A", "Platform A", listOf("stop-e")),
                    PlatformOption("B", "Platform B", listOf("stop-f")),
                    PlatformOption("C", "Platform C", listOf("stop-g")),
                    PlatformOption("D", "Platform D", listOf("stop-h")),
                ),
            ),
        )
    }

    private fun stressStation(): StationOption {
        return StationOption.create(
            stationKey = "station:prague-main-terminal",
            stationName = "Praha hlavni nadrazi severni vestibul",
            stopIds = (1..8).map { "stop-$it" },
            platforms = listOf(
                PlatformOption("1", "Platform 1", listOf("stop-1")),
                PlatformOption("2", "Platform 2", listOf("stop-2")),
                PlatformOption("3", "Platform 3", listOf("stop-3")),
                PlatformOption("4", "Platform 4", listOf("stop-4")),
                PlatformOption("5", "Platform 5", listOf("stop-5")),
                PlatformOption("6", "Platform 6", listOf("stop-6")),
            ),
        )
    }

    private fun sampleLines(): List<LineOption> {
        return listOf(
            LineOption.create(
                shortName = "7",
                longName = "Palmovka - Nadrazi Vrsovice",
                routeType = 0,
            ),
            LineOption.create(
                shortName = "10",
                longName = "Sidliste Dablice - Strasnicka",
                routeType = 0,
            ),
            LineOption.create(
                shortName = "26",
                longName = "Divoka Sarka - Nadrazi Hostivar",
                routeType = 0,
            ),
        )
    }

    private fun formatDelaySeconds(delaySeconds: Int?): String {
        if (delaySeconds == null) {
            return "--"
        }
        if (delaySeconds == 0) {
            return "On time"
        }

        val sign = if (delaySeconds > 0) "+" else "-"
        val absoluteMinutes = kotlin.math.abs(delaySeconds) / 60
        return "$sign${absoluteMinutes} min"
    }

    private val detailFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
}

internal data class BoardPreviewCase(
    val name: String,
    val selection: RouteSelection,
    val snapshot: DepartureSnapshot?,
    val departures: List<RouteDeparture>,
    val currentSystemTime: ZonedDateTime,
    val autoUpdatesEnabled: Boolean,
    val isRefreshing: Boolean,
)

internal data class SettingsPreviewData(
    val showSecondsEnabled: Boolean,
    val detailsDialogAutoRefreshEnabled: Boolean,
    val verifiedMatchCount: Int,
    val transitCatalogLastRefreshLabel: String,
    val liveSnapshotCacheLabel: String,
    val gtfsTripDetailCacheLabel: String,
    val vehiclePositionCacheLabel: String,
    val apiKeySourceLabel: String,
)

internal data class ApiKeyPreviewCase(
    val name: String,
    val value: String,
    val sourceLabel: String,
)

internal data class QuickSwitchPreviewCase(
    val name: String,
    val currentSelection: RouteSelection,
    val favoriteRoutes: List<RouteSelection>,
)

internal data class RouteSetupHomePreviewCase(
    val name: String,
    val draftSelection: RouteSelection,
    val favoriteRoutes: List<RouteSelection>,
    val isEditingFavorite: Boolean,
    val isCatalogLoading: Boolean,
    val catalogError: String?,
)

internal data class StationSearchPreviewCase(
    val name: String,
    val target: RouteEndpointTarget,
    val query: String,
    val results: List<StationOption>,
    val isCatalogLoading: Boolean,
    val catalogError: String?,
)

internal data class PlatformPickerPreviewCase(
    val name: String,
    val target: RouteEndpointTarget,
    val station: StationOption,
)

internal data class LineSearchPreviewCase(
    val name: String,
    val query: String,
    val results: List<LineOption>,
    val isCatalogLoading: Boolean,
    val catalogError: String?,
)

internal data class DepartureDetailsPreviewCase(
    val name: String,
    val uiState: DepartureDetailsUiState,
)

internal class BoardPreviewCaseProvider : PreviewParameterProvider<BoardPreviewCase> {
    override val values: Sequence<BoardPreviewCase> = WearPreviewFixtures.boardCases().asSequence()
}

internal class ApiKeyPreviewCaseProvider : PreviewParameterProvider<ApiKeyPreviewCase> {
    override val values: Sequence<ApiKeyPreviewCase> = WearPreviewFixtures.apiKeyCases().asSequence()
}

internal class QuickSwitchPreviewCaseProvider : PreviewParameterProvider<QuickSwitchPreviewCase> {
    override val values: Sequence<QuickSwitchPreviewCase> = WearPreviewFixtures.quickSwitchCases().asSequence()
}

internal class RouteSetupHomePreviewCaseProvider : PreviewParameterProvider<RouteSetupHomePreviewCase> {
    override val values: Sequence<RouteSetupHomePreviewCase> =
        WearPreviewFixtures.routeSetupHomeCases().asSequence()
}

internal class StationSearchPreviewCaseProvider : PreviewParameterProvider<StationSearchPreviewCase> {
    override val values: Sequence<StationSearchPreviewCase> =
        WearPreviewFixtures.stationSearchCases().asSequence()
}

internal class PlatformPickerPreviewCaseProvider : PreviewParameterProvider<PlatformPickerPreviewCase> {
    override val values: Sequence<PlatformPickerPreviewCase> =
        WearPreviewFixtures.platformPickerCases().asSequence()
}

internal class LineSearchPreviewCaseProvider : PreviewParameterProvider<LineSearchPreviewCase> {
    override val values: Sequence<LineSearchPreviewCase> =
        WearPreviewFixtures.lineSearchCases().asSequence()
}

internal class DepartureDetailsPreviewCaseProvider : PreviewParameterProvider<DepartureDetailsPreviewCase> {
    override val values: Sequence<DepartureDetailsPreviewCase> =
        WearPreviewFixtures.departureDetailsCases().asSequence()
}
