package com.example.routetracker.data

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.example.routetracker.complication.MainComplicationService
import com.example.routetracker.complication.StopwatchComplicationService
import com.example.routetracker.tile.MainTileService
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "RouteRepository"
private const val PREFS_NAME = "route_prefs"
private const val PREF_LEGACY_DIRECTION = "selected_direction"
private const val PREF_CURRENT_ROUTE_SELECTION = "current_route_selection"
private const val PREF_FAVORITE_ROUTE_SELECTIONS = "favorite_route_selections"
private const val PREF_AUTO_UPDATES_ENABLED = "auto_updates_enabled"
private const val PREF_SHOW_SECONDS = "show_seconds"
private const val PREF_DETAILS_DIALOG_AUTO_REFRESH_ENABLED = "details_dialog_auto_refresh_enabled"
private const val PREF_LIVE_SNAPSHOT_CACHE_MILLIS = "live_snapshot_cache_millis"
private const val PREF_GTFS_TRIP_DETAIL_CACHE_MILLIS = "gtfs_trip_detail_cache_millis"
private const val PREF_VEHICLE_POSITION_CACHE_MILLIS = "vehicle_position_cache_millis"
private const val PREF_VERIFIED_MATCH_COUNT = "verified_match_count"

private val DISPLAY_CLOCK_MINUTES_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DISPLAY_CLOCK_SECONDS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val DETAIL_CLOCK_MINUTES_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")
private val DETAIL_CLOCK_SECONDS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss")
private val PRAGUE_ZONE: ZoneId = ZoneId.of("Europe/Prague")

private data class CacheDurationOption(
    val millis: Long,
    val label: String,
)

private data class TripDetailCacheKey(
    val tripId: String,
    val serviceDate: String,
)

private sealed interface VehiclePositionCacheValue {
    data class Present(val body: JSONObject) : VehiclePositionCacheValue
    object Missing : VehiclePositionCacheValue
}

fun formatDisplayTime(
    time: ZonedDateTime,
    showSeconds: Boolean,
): String {
    return time.format(
        if (showSeconds) DISPLAY_CLOCK_SECONDS_FORMATTER else DISPLAY_CLOCK_MINUTES_FORMATTER
    )
}

class RouteRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val apiClient = GolemioApiClient()
    private val catalogRepository = TransitCatalogRepository(context, apiClient)

    companion object {
        private const val SEARCH_WINDOW_MINUTES = 120
        private const val DEFAULT_LIVE_SNAPSHOT_CACHE_MILLIS = 2_000L
        private const val DEFAULT_GTFS_TRIP_DETAIL_CACHE_MILLIS = 60_000L
        private const val DEFAULT_VEHICLE_POSITION_CACHE_MILLIS = 2_000L
        private const val MAX_FAVORITES = 8
        const val DETAILS_DIALOG_REFRESH_INTERVAL_MILLIS = 10_000L
        const val LINE_NAME = "7"
        const val DEFAULT_VERIFIED_MATCH_COUNT = 3

        private val LIVE_SNAPSHOT_CACHE_OPTIONS = listOf(
            CacheDurationOption(0L, "Off"),
            CacheDurationOption(2_000L, "2 s"),
            CacheDurationOption(5_000L, "5 s"),
            CacheDurationOption(10_000L, "10 s"),
        )
        private val GTFS_TRIP_DETAIL_CACHE_OPTIONS = listOf(
            CacheDurationOption(0L, "Off"),
            CacheDurationOption(30_000L, "30 s"),
            CacheDurationOption(60_000L, "1 min"),
            CacheDurationOption(300_000L, "5 min"),
        )
        private val VEHICLE_POSITION_CACHE_OPTIONS = listOf(
            CacheDurationOption(0L, "Off"),
            CacheDurationOption(2_000L, "2 s"),
            CacheDurationOption(5_000L, "5 s"),
            CacheDurationOption(10_000L, "10 s"),
        )
        private const val MIN_VERIFIED_MATCH_COUNT = 1
        private const val MAX_VERIFIED_MATCH_COUNT = 10
        private val snapshotLock = Any()

        @Volatile
        private var cachedSnapshot: DepartureSnapshot? = null

        @Volatile
        private var cacheTimestampElapsedRealtime: Long = 0L

        private val tripDetailCache = TimedMemoryCache<TripDetailCacheKey, JSONObject>()
        private val vehiclePositionCache = TimedMemoryCache<String, VehiclePositionCacheValue>()

        fun defaultRouteToNadraziVrsovice(): RouteSelection {
            return RouteSelection(
                origin = StopSelection(
                    stationKey = "legacy:U529Z1P",
                    stationName = "Palmovka",
                    stopIds = listOf("U529Z1P"),
                ),
                destination = StopSelection(
                    stationKey = "legacy:U463Z1P",
                    stationName = "Nádraží Vršovice",
                    stopIds = listOf("U463Z1P"),
                ),
                line = LineSelection(shortName = LINE_NAME),
            )
        }

        fun defaultRouteToPalmovka(): RouteSelection {
            return RouteSelection(
                origin = StopSelection(
                    stationKey = "legacy:U463Z2P",
                    stationName = "Nádraží Vršovice",
                    stopIds = listOf("U463Z2P"),
                ),
                destination = StopSelection(
                    stationKey = "legacy:U529Z2P",
                    stationName = "Palmovka",
                    stopIds = listOf("U529Z2P"),
                ),
                line = LineSelection(shortName = LINE_NAME),
            )
        }

        fun defaultRouteSelection(): RouteSelection = defaultRouteToNadraziVrsovice()

        fun previewSnapshot(
            selection: RouteSelection = defaultRouteSelection(),
            now: ZonedDateTime = ZonedDateTime.now(PRAGUE_ZONE),
        ): DepartureSnapshot {
            return DepartureSnapshot(
                selection = selection,
                departures = listOf(
                    previewDeparture(
                        tripId = "preview-1",
                        lineShortName = selection.line?.shortName ?: "7",
                        scheduledTime = now.plusMinutes(2),
                        predictedTime = now.plusMinutes(3),
                        countdownMinutes = 3,
                        delayMinutes = 1,
                    ),
                    previewDeparture(
                        tripId = "preview-2",
                        lineShortName = selection.line?.shortName ?: "10",
                        scheduledTime = now.plusMinutes(11),
                        predictedTime = now.plusMinutes(11),
                        countdownMinutes = 11,
                        delayMinutes = 0,
                    ),
                    previewDeparture(
                        tripId = "preview-3",
                        lineShortName = selection.line?.shortName ?: "16",
                        scheduledTime = now.plusMinutes(20),
                        predictedTime = now.plusMinutes(19),
                        countdownMinutes = 19,
                        delayMinutes = -1,
                    ),
                ),
                fetchedAt = now,
                requestedMatchCount = DEFAULT_VERIFIED_MATCH_COUNT,
            )
        }

        private fun previewDeparture(
            tripId: String,
            lineShortName: String,
            scheduledTime: ZonedDateTime,
            predictedTime: ZonedDateTime?,
            countdownMinutes: Int,
            delayMinutes: Int,
        ): RouteDeparture {
            val boardDelaySeconds = delayMinutes * 60
            val resolvedDepartureTime = predictedTime ?: scheduledTime
            return RouteDeparture(
                tripId = tripId,
                lineShortName = lineShortName,
                departureTime = resolvedDepartureTime,
                countdownMinutes = countdownMinutes,
                delayMinutes = delayMinutes,
                departureBoardDetails = DepartureBoardDetails(
                    departureTime = BoardStopTime(
                        scheduledTime = scheduledTime,
                        predictedTime = predictedTime,
                    ),
                    originArrivalTime = BoardStopTime(
                        scheduledTime = scheduledTime.minusMinutes(1),
                        predictedTime = predictedTime?.minusMinutes(1),
                    ),
                    delaySeconds = boardDelaySeconds,
                ),
                vehiclePositionDetails = VehiclePositionDetails(
                    delaySeconds = boardDelaySeconds,
                    originTimestamp = resolvedDepartureTime.minusSeconds(20),
                ),
                destinationArrivalTime = resolvedDepartureTime.plusMinutes(12),
            )
        }
    }

    init {
        migrateLegacyDirectionIfNeeded()
    }

    fun getCurrentRouteSelection(): RouteSelection {
        val storedSelection = prefs.getString(PREF_CURRENT_ROUTE_SELECTION, null)
            ?.let(::routeSelectionFromJsonString)
            ?: defaultRouteSelection()
        return rebindSelectionToCachedCatalog(storedSelection)
    }

    fun setCurrentRouteSelection(selection: RouteSelection) {
        val reboundSelection = rebindSelectionToCachedCatalog(selection)
        val currentSelection = getCurrentRouteSelection()
        if (currentSelection.stableKey == reboundSelection.stableKey) {
            Log.d(TAG, "Route selection already active: ${reboundSelection.routeSummaryWithPlatforms}")
            return
        }

        prefs.edit {
            putString(PREF_CURRENT_ROUTE_SELECTION, routeSelectionToJsonString(reboundSelection))
        }

        cachedSnapshot = null
        cacheTimestampElapsedRealtime = 0L
        Log.d(TAG, "Route selection changed to ${reboundSelection.routeSummaryWithPlatforms}")
        requestSurfaceRefresh()
    }

    fun getFavoriteRoutes(): List<RouteSelection> {
        val rawFavorites = prefs.getString(PREF_FAVORITE_ROUTE_SELECTIONS, null)
            ?.let(::routeSelectionListFromJsonString)
            .orEmpty()
        return rawFavorites
            .map(::rebindSelectionToCachedCatalog)
            .distinctBy { it.stableKey }
    }

    fun isFavoriteRoute(selection: RouteSelection): Boolean {
        return getFavoriteRoutes().any { it.stableKey == selection.stableKey }
    }

    fun toggleFavoriteRoute(selection: RouteSelection): Boolean {
        val reboundSelection = rebindSelectionToCachedCatalog(selection)
        val favorites = getFavoriteRoutes().toMutableList()
        val existingIndex = favorites.indexOfFirst { it.stableKey == reboundSelection.stableKey }
        val nowFavorite = if (existingIndex >= 0) {
            favorites.removeAt(existingIndex)
            false
        } else {
            favorites.add(0, reboundSelection)
            while (favorites.size > MAX_FAVORITES) {
                favorites.removeLast()
            }
            true
        }

        prefs.edit {
            putString(PREF_FAVORITE_ROUTE_SELECTIONS, routeSelectionListToJsonString(favorites))
        }
        Log.d(TAG, "Favorite routes changed. route=${reboundSelection.routeSummaryWithPlatforms} favorite=$nowFavorite")
        return nowFavorite
    }

    fun loadTransitCatalog(forceRefresh: Boolean = false): TransitCatalog {
        return catalogRepository.getCatalog(forceRefresh = forceRefresh)
    }

    fun refreshTransitCatalog(): TransitCatalog {
        return catalogRepository.getCatalog(forceRefresh = true)
    }

    fun peekTransitCatalogInMemory(): TransitCatalog? {
        return catalogRepository.peekMemoryCatalog()
    }

    fun getCachedTransitCatalog(): TransitCatalog? {
        return catalogRepository.getCachedCatalog()
    }

    fun clearTransitCatalogMemoryCache() {
        catalogRepository.clearMemoryCache()
    }

    fun isTransitCatalogRefreshDue(): Boolean {
        return catalogRepository.isCatalogRefreshDue()
    }

    fun getTransitCatalogLastRefreshTime(): ZonedDateTime? {
        return catalogRepository.getLastCatalogFetchedAt()
    }

    fun getTransitCatalogLastRefreshLabel(): String {
        return getTransitCatalogLastRefreshTime()?.let(::formatStatusTime) ?: "Never"
    }

    fun searchStations(
        query: String,
        limit: Int = 12,
    ): List<StationOption> {
        return loadTransitCatalog().searchStations(query, limit)
    }

    fun searchLines(
        query: String,
        limit: Int = 12,
    ): List<LineOption> {
        return loadTransitCatalog().searchLines(query, limit)
    }

    fun resolveStopSelection(
        stationKey: String,
        platformKey: String?,
    ): StopSelection? {
        val station = loadTransitCatalog().stationByKey(stationKey) ?: return null
        return station.resolveSelection(platformKey)
    }

    fun getAutoUpdatesEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_UPDATES_ENABLED, true)
    }

    fun getShowSecondsEnabled(): Boolean {
        return prefs.getBoolean(PREF_SHOW_SECONDS, false)
    }

    fun getDetailsDialogAutoRefreshEnabled(): Boolean {
        return prefs.getBoolean(PREF_DETAILS_DIALOG_AUTO_REFRESH_ENABLED, true)
    }

    fun getLiveSnapshotCacheMillis(): Long {
        return prefs.getLong(PREF_LIVE_SNAPSHOT_CACHE_MILLIS, DEFAULT_LIVE_SNAPSHOT_CACHE_MILLIS)
    }

    fun getGtfsTripDetailCacheMillis(): Long {
        return prefs.getLong(PREF_GTFS_TRIP_DETAIL_CACHE_MILLIS, DEFAULT_GTFS_TRIP_DETAIL_CACHE_MILLIS)
    }

    fun getVehiclePositionCacheMillis(): Long {
        return prefs.getLong(PREF_VEHICLE_POSITION_CACHE_MILLIS, DEFAULT_VEHICLE_POSITION_CACHE_MILLIS)
    }

    fun getVerifiedMatchCount(): Int {
        return prefs.getInt(PREF_VERIFIED_MATCH_COUNT, DEFAULT_VERIFIED_MATCH_COUNT)
            .coerceIn(MIN_VERIFIED_MATCH_COUNT, MAX_VERIFIED_MATCH_COUNT)
    }

    fun getLiveSnapshotCacheLabel(): String {
        return cacheDurationLabel(getLiveSnapshotCacheMillis())
    }

    fun getGtfsTripDetailCacheLabel(): String {
        return cacheDurationLabel(getGtfsTripDetailCacheMillis())
    }

    fun getVehiclePositionCacheLabel(): String {
        return cacheDurationLabel(getVehiclePositionCacheMillis())
    }

    fun getVerifiedMatchCountLabel(): String {
        return getVerifiedMatchCount().toString()
    }

    fun setAutoUpdatesEnabled(enabled: Boolean) {
        if (getAutoUpdatesEnabled() == enabled) {
            Log.d(TAG, "Auto updates already set to $enabled")
            return
        }

        prefs.edit {
            putBoolean(PREF_AUTO_UPDATES_ENABLED, enabled)
        }

        Log.d(TAG, "Auto updates changed to $enabled")
        requestSurfaceRefresh()
    }

    fun setShowSecondsEnabled(enabled: Boolean) {
        if (getShowSecondsEnabled() == enabled) {
            Log.d(TAG, "Show seconds already set to $enabled")
            return
        }

        prefs.edit {
            putBoolean(PREF_SHOW_SECONDS, enabled)
        }

        Log.d(TAG, "Show seconds changed to $enabled")
        requestSurfaceRefresh()
    }

    fun setDetailsDialogAutoRefreshEnabled(enabled: Boolean) {
        if (getDetailsDialogAutoRefreshEnabled() == enabled) {
            Log.d(TAG, "Details dialog auto-refresh already set to $enabled")
            return
        }

        prefs.edit {
            putBoolean(PREF_DETAILS_DIALOG_AUTO_REFRESH_ENABLED, enabled)
        }

        Log.d(TAG, "Details dialog auto-refresh changed to $enabled")
    }

    fun cycleLiveSnapshotCacheMillis(): Long {
        val nextValue = nextCacheDuration(
            currentMillis = getLiveSnapshotCacheMillis(),
            options = LIVE_SNAPSHOT_CACHE_OPTIONS,
        )
        prefs.edit { putLong(PREF_LIVE_SNAPSHOT_CACHE_MILLIS, nextValue) }
        Log.d(TAG, "Live snapshot cache changed to ${cacheDurationLabel(nextValue)}")
        requestSurfaceRefresh()
        return nextValue
    }

    fun cycleGtfsTripDetailCacheMillis(): Long {
        val nextValue = nextCacheDuration(
            currentMillis = getGtfsTripDetailCacheMillis(),
            options = GTFS_TRIP_DETAIL_CACHE_OPTIONS,
        )
        prefs.edit { putLong(PREF_GTFS_TRIP_DETAIL_CACHE_MILLIS, nextValue) }
        Log.d(TAG, "GTFS trip detail cache changed to ${cacheDurationLabel(nextValue)}")
        requestSurfaceRefresh()
        return nextValue
    }

    fun cycleVehiclePositionCacheMillis(): Long {
        val nextValue = nextCacheDuration(
            currentMillis = getVehiclePositionCacheMillis(),
            options = VEHICLE_POSITION_CACHE_OPTIONS,
        )
        prefs.edit { putLong(PREF_VEHICLE_POSITION_CACHE_MILLIS, nextValue) }
        Log.d(TAG, "Vehicle position cache changed to ${cacheDurationLabel(nextValue)}")
        requestSurfaceRefresh()
        return nextValue
    }

    fun adjustVerifiedMatchCount(delta: Int): Int {
        val nextValue = (getVerifiedMatchCount() + delta)
            .coerceIn(MIN_VERIFIED_MATCH_COUNT, MAX_VERIFIED_MATCH_COUNT)
        prefs.edit { putInt(PREF_VERIFIED_MATCH_COUNT, nextValue) }
        synchronized(snapshotLock) {
            cachedSnapshot = null
            cacheTimestampElapsedRealtime = 0L
        }
        Log.d(TAG, "Verified match count changed to $nextValue")
        requestSurfaceRefresh()
        return nextValue
    }

    fun getDepartureSnapshot(forceRefresh: Boolean = false): DepartureSnapshot {
        val selection = getCurrentRouteSelection()
        val verifiedMatchCount = getVerifiedMatchCount()
        if (selection.origin.stopIds.isEmpty() || selection.destination.stopIds.isEmpty()) {
            return DepartureSnapshot(
                selection = selection,
                departures = emptyList(),
                fetchedAt = ZonedDateTime.now(PRAGUE_ZONE),
                requestedMatchCount = verifiedMatchCount,
                isStale = true,
                errorMessage = "Set route first.",
            )
        }

        val snapshotCacheMillis = getLiveSnapshotCacheMillis()
        if (!forceRefresh && !getAutoUpdatesEnabled()) {
            return pausedSnapshot(
                selection = selection,
                cached = cachedSnapshot,
            )
        }

        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        val cached = cachedSnapshot
        if (!forceRefresh &&
            snapshotCacheMillis > 0L &&
            cached != null &&
            cached.selection.stableKey == selection.stableKey &&
            nowElapsedRealtime - cacheTimestampElapsedRealtime < snapshotCacheMillis
        ) {
            Log.d(TAG, "Returning cached snapshot with ${cached.departures.size} departures for ${selection.routeSummaryWithPlatforms}.")
            return cached
        }

        synchronized(snapshotLock) {
            val synchronizedCacheMillis = getLiveSnapshotCacheMillis()
            if (!forceRefresh && !getAutoUpdatesEnabled()) {
                return pausedSnapshot(
                    selection = selection,
                    cached = cachedSnapshot,
                )
            }

            val refreshedElapsedRealtime = SystemClock.elapsedRealtime()
            val currentCached = cachedSnapshot
            if (!forceRefresh &&
                synchronizedCacheMillis > 0L &&
                currentCached != null &&
                currentCached.selection.stableKey == selection.stableKey &&
                refreshedElapsedRealtime - cacheTimestampElapsedRealtime < synchronizedCacheMillis
            ) {
                Log.d(TAG, "Returning cached snapshot after synchronized check for ${selection.routeSummaryWithPlatforms}.")
                return currentCached
            }

            Log.d(TAG, "Refreshing snapshot. forceRefresh=$forceRefresh route=${selection.routeSummaryWithPlatforms}")
            return try {
                fetchDepartureSnapshot(selection).also { snapshot ->
                    cachedSnapshot = snapshot
                    cacheTimestampElapsedRealtime = refreshedElapsedRealtime
                    Log.d(TAG, "Snapshot refreshed successfully with ${snapshot.departures.size} departures at ${snapshot.fetchedAt}.")
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to refresh snapshot.", error)
                currentCached?.takeIf { it.selection.stableKey == selection.stableKey }?.copy(
                    isStale = true,
                    errorMessage = "Showing cached data.",
                ) ?: DepartureSnapshot(
                    selection = selection,
                    departures = emptyList(),
                    fetchedAt = ZonedDateTime.now(PRAGUE_ZONE),
                    requestedMatchCount = verifiedMatchCount,
                    isStale = true,
                    errorMessage = "Unable to load live departures.",
                )
            }
        }
    }

    fun refreshDepartureSnapshot(): DepartureSnapshot {
        Log.d(TAG, "Manual refresh requested.")
        val snapshot = getDepartureSnapshot(forceRefresh = true)
        requestSurfaceRefresh()
        return snapshot
    }

    fun formatDepartureClockTime(departure: RouteDeparture): String {
        return formatDisplayTime(departure.departureTime, getShowSecondsEnabled())
    }

    fun formatStatusTime(timestamp: ZonedDateTime): String {
        return formatDisplayTime(timestamp, getShowSecondsEnabled())
    }

    fun formatDetailTime(timestamp: ZonedDateTime?): String {
        timestamp ?: return "--"
        val formatter = when {
            timestamp.toLocalDate() == LocalDate.now(PRAGUE_ZONE) -> {
                if (getShowSecondsEnabled()) DISPLAY_CLOCK_SECONDS_FORMATTER else DISPLAY_CLOCK_MINUTES_FORMATTER
            }

            getShowSecondsEnabled() -> DETAIL_CLOCK_SECONDS_FORMATTER
            else -> DETAIL_CLOCK_MINUTES_FORMATTER
        }
        return timestamp.format(formatter)
    }

    fun formatDelaySeconds(seconds: Int?): String {
        seconds ?: return "--"
        val sign = when {
            seconds > 0 -> "+"
            seconds < 0 -> "-"
            else -> ""
        }
        val absoluteSeconds = abs(seconds)
        return when {
            absoluteSeconds >= 60 && absoluteSeconds % 60 == 0 -> {
                "$sign${absoluteSeconds / 60} min"
            }

            else -> "$sign${absoluteSeconds} s"
        }
    }

    private fun requestSurfaceRefresh() {
        try {
            TileService.getUpdater(context).requestUpdate(MainTileService::class.java)
            Log.d(TAG, "Requested tile refresh.")
        } catch (_: Exception) {
            Log.w(TAG, "Tile refresh request failed.")
        }

        try {
            val complicationServices = listOf(
                MainComplicationService::class.java,
                StopwatchComplicationService::class.java,
            )
            complicationServices.forEach { serviceClass ->
                val componentName = ComponentName(context, serviceClass)
                ComplicationDataSourceUpdateRequester
                    .create(context, componentName)
                    .requestUpdateAll()
            }
            Log.d(TAG, "Requested complication refresh.")
        } catch (_: Exception) {
            Log.w(TAG, "Complication refresh request failed.")
        }
    }

    private fun pausedSnapshot(
        selection: RouteSelection,
        cached: DepartureSnapshot?,
    ): DepartureSnapshot {
        val matchingCached = cached?.takeIf { it.selection.stableKey == selection.stableKey }
        return matchingCached?.copy(
            isStale = true,
            errorMessage = "Updates paused.",
        ) ?: DepartureSnapshot(
            selection = selection,
            departures = emptyList(),
            fetchedAt = ZonedDateTime.now(PRAGUE_ZONE),
            requestedMatchCount = getVerifiedMatchCount(),
            isStale = true,
            errorMessage = "Updates paused.",
        )
    }

    private fun fetchDepartureSnapshot(selection: RouteSelection): DepartureSnapshot {
        val requestedMatchCount = getVerifiedMatchCount()
        val departureResult = fetchDirectDepartures(
            selection = selection,
            maxResults = requestedMatchCount,
        )
        val snapshot = DepartureSnapshot(
            selection = selection,
            departures = departureResult.departures,
            fetchedAt = departureResult.referenceNow,
            requestedMatchCount = requestedMatchCount,
        )
        Log.d(TAG, "Fetched snapshot summary: ${snapshot.debugSummary()}")
        return snapshot
    }

    private fun fetchDirectDepartures(
        selection: RouteSelection,
        maxResults: Int,
    ): DepartureQueryResult {
        // A route selection can represent one platform or an entire station on each end.
        // We therefore query all selected source stop IDs together, then verify direct
        // reachability against the selected destination stop set trip-by-trip.
        val deviceNow = ZonedDateTime.now(PRAGUE_ZONE)
        val boardLimit = max(50, maxResults * 12)
        val sourceStopIds = selection.origin.stopIds.distinct().sorted()
        val destinationStopIds = selection.destination.stopIds.toSet()
        val sourceStopIdSet = sourceStopIds.toSet()
        val selectedLine = selection.line?.shortName?.trim()
        val boardPayload = fetchDepartureBoard(deviceNow, boardLimit, sourceStopIds)
        val departures = boardPayload.optJSONArray("departures") ?: JSONArray()
        val matches = mutableListOf<RouteDeparture>()
        val acceptedDepartureKeys = mutableSetOf<String>()

        var skippedWrongLine = 0
        var skippedWrongStop = 0
        var skippedMissingTime = 0
        var skippedMissingTripId = 0
        var skippedMissingBoardingIndex = 0
        var skippedMissingDestination = 0
        var skippedCanceled = 0
        var skippedDuplicateBoarding = 0

        for (index in 0 until departures.length()) {
            val departure = departures.optJSONObject(index) ?: continue
            val route = departure.optJSONObject("route")
            val stop = departure.optJSONObject("stop")
            val trip = departure.optJSONObject("trip")

            val routeShortName = route?.optSanitizedString("short_name").orEmpty()
            if (!selectedLine.isNullOrBlank() && !routeShortName.equals(selectedLine, ignoreCase = true)) {
                skippedWrongLine += 1
                continue
            }

            val boardedStopId = stop?.optSanitizedString("id").orEmpty()
            if (boardedStopId !in sourceStopIdSet) {
                skippedWrongStop += 1
                continue
            }

            val boardStopTime = parseBoardStopTime(departure, "departure_timestamp")
            if (boardStopTime == null) {
                skippedMissingTime += 1
                continue
            }

            val boardArrivalTime = parseBoardStopTime(departure, "arrival_timestamp")
            val tripId = trip?.optSanitizedString("id").orEmpty()
            if (tripId.isBlank()) {
                skippedMissingTripId += 1
                continue
            }

            val serviceDate = boardStopTime.bestAvailableTime.toLocalDate().toString()
            val tripDetail = fetchTripDetail(
                tripId = tripId,
                serviceDate = serviceDate,
            )
            val stopTimes = tripDetail.optJSONArray("stop_times") ?: JSONArray()
            val boardingIndex = findBoardingIndex(
                stopTimes = stopTimes,
                boardedStopId = boardedStopId,
                boardingReferenceTime = boardStopTime.bestAvailableTime,
            )
            if (boardingIndex == null) {
                skippedMissingBoardingIndex += 1
                continue
            }

            val destinationStopTime = findDestinationStopTime(
                stopTimes = stopTimes,
                boardingIndex = boardingIndex,
                destinationStopIds = destinationStopIds,
            )
            if (destinationStopTime == null) {
                skippedMissingDestination += 1
                continue
            }

            var canceled = optionalBoolean(trip, "is_canceled")
            val boardDelaySeconds = extractBoardDelaySeconds(departure)
            var vehicleDelaySeconds: Int? = null
            var vehicleOriginTimestamp: ZonedDateTime? = null

            fetchVehiclePosition(tripId)?.let { vehiclePosition ->
                val lastPosition = vehiclePosition
                    .optJSONObject("properties")
                    ?.optJSONObject("last_position")
                vehicleDelaySeconds = optionalInt(lastPosition?.optJSONObject("delay"), "actual")
                vehicleOriginTimestamp = parseOptionalOffsetTimestamp(lastPosition?.optString("origin_timestamp"))
                optionalBoolean(lastPosition, "is_canceled")?.let { canceled = it }
            }

            if (canceled == true) {
                skippedCanceled += 1
                continue
            }

            val resolvedTiming = DepartureTimingResolver.resolve(
                DepartureRealtimeInputs(
                    scheduledDepartureTime = boardStopTime.scheduledTime,
                    predictedDepartureTime = boardStopTime.predictedTime,
                    boardDelaySeconds = boardDelaySeconds,
                    vehicleDelaySeconds = vehicleDelaySeconds,
                )
            )
            val destinationArrivalTime = resolveArrivalTime(
                destinationStopTime = destinationStopTime,
                serviceDayAnchor = boardStopTime.scheduledTime,
                delaySeconds = resolvedTiming.delaySeconds,
            )
            val boardedPlatformLabel = resolveBoardedPlatformLabel(
                originSelection = selection.origin,
                boardedStopId = boardedStopId,
            )
            val matchedDeparture = resolvedTiming.toRouteDeparture(
                tripId = tripId,
                lineShortName = routeShortName.ifBlank { selectedLine ?: "?" },
                referenceNow = deviceNow,
                boardedStopId = boardedStopId,
                boardedPlatformLabel = boardedPlatformLabel,
                departureBoardDetails = DepartureBoardDetails(
                    departureTime = boardStopTime,
                    originArrivalTime = boardArrivalTime,
                    delaySeconds = boardDelaySeconds,
                ),
                vehiclePositionDetails = if (vehicleDelaySeconds != null || vehicleOriginTimestamp != null) {
                    VehiclePositionDetails(
                        delaySeconds = vehicleDelaySeconds,
                        originTimestamp = vehicleOriginTimestamp,
                    )
                } else {
                    null
                },
                destinationArrivalTime = destinationArrivalTime,
            )
            val acceptedDepartureKey = "$tripId|$boardedStopId"
            if (!acceptedDepartureKeys.add(acceptedDepartureKey)) {
                skippedDuplicateBoarding += 1
                continue
            }
            matches += matchedDeparture
            Log.d(
                TAG,
                "Accepted departure: route=${selection.routeSummaryWithPlatforms} tripId=$tripId line=${matchedDeparture.lineShortName} label=${matchedDeparture.compactLabel} at ${matchedDeparture.departureTime}",
            )

            if (matches.size >= maxResults) {
                break
            }
        }

        Log.d(
            TAG,
            "Route scan done. route=${selection.routeSummaryWithPlatforms} boardCount=${departures.length()} matches=${matches.size} " +
                "skippedWrongLine=$skippedWrongLine skippedWrongStop=$skippedWrongStop " +
                "skippedMissingTime=$skippedMissingTime skippedMissingTripId=$skippedMissingTripId " +
                "skippedMissingBoardingIndex=$skippedMissingBoardingIndex skippedMissingDestination=$skippedMissingDestination " +
                "skippedCanceled=$skippedCanceled skippedDuplicateBoarding=$skippedDuplicateBoarding",
        )
        return DepartureQueryResult(
            departures = matches.sortedBy { it.departureTime }.take(maxResults),
            referenceNow = deviceNow,
        )
    }

    private fun fetchDepartureBoard(
        now: ZonedDateTime,
        boardLimit: Int,
        sourceStopIds: List<String>,
    ): JSONObject {
        val params = buildList {
            sourceStopIds.forEach { add("ids[]" to it) }
            add("timeFrom" to now.toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS).toString())
            add("minutesAfter" to SEARCH_WINDOW_MINUTES.toString())
            add("limit" to boardLimit.toString())
            add("order" to "real")
        }

        return try {
            apiClient.getObject("/v2/pid/departureboards", params).body
        } catch (error: ApiException) {
            if (!error.isTimeFromOutOfRange()) {
                throw error
            }

            Log.w(TAG, "Departure board rejected timeFrom. Retrying without timeFrom.")
            val fallbackParams = params.filterNot { it.first == "timeFrom" }
            apiClient.getObject("/v2/pid/departureboards", fallbackParams).body
        }
    }

    private fun fetchTripDetail(
        tripId: String,
        serviceDate: String,
    ): JSONObject {
        val cacheKey = TripDetailCacheKey(tripId = tripId, serviceDate = serviceDate)
        val ttlMillis = getGtfsTripDetailCacheMillis()
        val nowElapsedRealtime = SystemClock.elapsedRealtime()

        tripDetailCache.getIfFresh(
            key = cacheKey,
            ttlMillis = ttlMillis,
            nowElapsedRealtime = nowElapsedRealtime,
        )?.let { cachedTripDetail ->
            Log.d(TAG, "Using cached GTFS trip detail for tripId=$tripId serviceDate=$serviceDate")
            return cachedTripDetail
        }

        val tripDetail = apiClient.getObject(
            "/v2/gtfs/trips/$tripId",
            listOf(
                "date" to serviceDate,
                "includeStops" to "true",
                "includeStopTimes" to "true",
                "includeRoute" to "true",
            ),
        ).body

        if (ttlMillis > 0L) {
            tripDetailCache.put(
                key = cacheKey,
                value = tripDetail,
                nowElapsedRealtime = nowElapsedRealtime,
            )
        }

        return tripDetail
    }

    private fun fetchVehiclePosition(tripId: String): JSONObject? {
        val ttlMillis = getVehiclePositionCacheMillis()
        val nowElapsedRealtime = SystemClock.elapsedRealtime()

        vehiclePositionCache.getIfFresh(
            key = tripId,
            ttlMillis = ttlMillis,
            nowElapsedRealtime = nowElapsedRealtime,
        )?.let { cachedValue ->
            return when (cachedValue) {
                is VehiclePositionCacheValue.Present -> {
                    Log.d(TAG, "Using cached vehicle position for tripId=$tripId")
                    cachedValue.body
                }

                VehiclePositionCacheValue.Missing -> {
                    Log.d(TAG, "Using cached vehicle-position miss for tripId=$tripId")
                    null
                }
            }
        }

        return try {
            val vehiclePosition = apiClient.getObject(
                "/v2/vehiclepositions/$tripId",
                listOf("preferredTimezone" to "Europe/Prague"),
                retryOnRateLimit = false,
            ).body
            if (ttlMillis > 0L) {
                vehiclePositionCache.put(
                    key = tripId,
                    value = VehiclePositionCacheValue.Present(vehiclePosition),
                    nowElapsedRealtime = nowElapsedRealtime,
                )
            }
            vehiclePosition
        } catch (error: ApiException) {
            if (error.statusCode == 404 || error.statusCode == 429) {
                if (error.statusCode == 404) {
                    Log.d(TAG, "Vehicle position not found for tripId=$tripId")
                } else {
                    Log.w(TAG, "Vehicle position rate-limited for tripId=$tripId. Falling back to departure board data.")
                }
                if (ttlMillis > 0L) {
                    vehiclePositionCache.put(
                        key = tripId,
                        value = VehiclePositionCacheValue.Missing,
                        nowElapsedRealtime = nowElapsedRealtime,
                    )
                }
                null
            } else {
                throw error
            }
        }
    }

    private fun parseBoardStopTime(
        entry: JSONObject,
        fieldName: String,
    ): BoardStopTime? {
        val timestamps = entry.optJSONObject(fieldName) ?: return null
        val scheduledValue = timestamps.optSanitizedString("scheduled") ?: return null
        val predictedValue = timestamps.optSanitizedString("predicted")
        return BoardStopTime(
            scheduledTime = OffsetDateTime.parse(scheduledValue).atZoneSameInstant(PRAGUE_ZONE),
            predictedTime = predictedValue?.let { OffsetDateTime.parse(it).atZoneSameInstant(PRAGUE_ZONE) },
        )
    }

    private fun parseOptionalOffsetTimestamp(value: String?): ZonedDateTime? {
        return value
            .sanitizeApiValue()
            ?.let { sanitizedValue -> OffsetDateTime.parse(sanitizedValue).atZoneSameInstant(PRAGUE_ZONE) }
    }

    private fun extractBoardDelaySeconds(entry: JSONObject): Int? {
        optionalInt(entry.optJSONObject("delay"), "seconds")?.let { return it }

        val timestamps = entry.optJSONObject("departure_timestamp") ?: return null
        val predicted = timestamps.optSanitizedString("predicted") ?: return null
        val scheduled = timestamps.optSanitizedString("scheduled") ?: return null

        return Duration.between(
            OffsetDateTime.parse(scheduled),
            OffsetDateTime.parse(predicted),
        ).seconds.toInt()
    }

    private fun findBoardingIndex(
        stopTimes: JSONArray,
        boardedStopId: String,
        boardingReferenceTime: ZonedDateTime,
    ): Int? {
        var fallbackIndex: Int? = null

        for (index in 0 until stopTimes.length()) {
            val stopTime = stopTimes.optJSONObject(index) ?: continue
            if (stopTime.optString("stop_id") != boardedStopId) {
                continue
            }

            fallbackIndex = index
            val departureValue = stopTime.optString("departure_time")
            if (departureValue.isBlank()) {
                continue
            }

            val scheduledDeparture = combineServiceTime(boardingReferenceTime, departureValue)
            if (!scheduledDeparture.plusMinutes(1).isBefore(boardingReferenceTime)) {
                return index
            }
        }

        return fallbackIndex
    }

    private fun findDestinationStopTime(
        stopTimes: JSONArray,
        boardingIndex: Int,
        destinationStopIds: Set<String>,
    ): JSONObject? {
        for (index in boardingIndex + 1 until stopTimes.length()) {
            val stopTime = stopTimes.optJSONObject(index) ?: continue
            if (stopTime.optString("stop_id") in destinationStopIds) {
                return stopTime
            }
        }
        return null
    }

    private fun resolveArrivalTime(
        destinationStopTime: JSONObject,
        serviceDayAnchor: ZonedDateTime,
        delaySeconds: Int,
    ): ZonedDateTime? {
        val arrivalServiceTime = destinationStopTime.optString("arrival_time")
            .ifBlank { destinationStopTime.optString("departure_time") }
        if (arrivalServiceTime.isBlank()) {
            return null
        }
        return combineServiceTime(serviceDayAnchor, arrivalServiceTime)
            .plusSeconds(delaySeconds.toLong())
    }

    private fun resolveBoardedPlatformLabel(
        originSelection: StopSelection,
        boardedStopId: String,
    ): String? {
        originSelection.platformLabel?.takeIf { it.isNotBlank() }?.let { return it }

        val catalog = catalogRepository.peekMemoryCatalog() ?: catalogRepository.getCachedCatalog()
        val station = catalog?.stationByKey(originSelection.stationKey) ?: return null
        return station.platformLabelForStop(boardedStopId)
    }

    private fun combineServiceTime(
        anchor: ZonedDateTime,
        hhmmss: String,
    ): ZonedDateTime {
        val parts = hhmmss.split(':')
        require(parts.size == 3) { "Unsupported service time: $hhmmss" }

        val totalHours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val seconds = parts[2].toInt()
        val extraDays = totalHours / 24
        val normalizedHours = totalHours % 24

        return anchor.toLocalDate()
            .atStartOfDay(PRAGUE_ZONE)
            .plusDays(extraDays.toLong())
            .plusHours(normalizedHours.toLong())
            .plusMinutes(minutes.toLong())
            .plusSeconds(seconds.toLong())
    }

    private fun cacheDurationLabel(durationMillis: Long): String {
        (LIVE_SNAPSHOT_CACHE_OPTIONS + GTFS_TRIP_DETAIL_CACHE_OPTIONS + VEHICLE_POSITION_CACHE_OPTIONS)
            .firstOrNull { it.millis == durationMillis }
            ?.let { return it.label }

        return when {
            durationMillis <= 0L -> "Off"
            durationMillis % 60_000L == 0L -> "${durationMillis / 60_000L} min"
            durationMillis % 1_000L == 0L -> "${durationMillis / 1_000L} s"
            else -> "${durationMillis} ms"
        }
    }

    private fun nextCacheDuration(
        currentMillis: Long,
        options: List<CacheDurationOption>,
    ): Long {
        val currentIndex = options.indexOfFirst { it.millis == currentMillis }
        return if (currentIndex == -1) {
            options.first().millis
        } else {
            options[(currentIndex + 1) % options.size].millis
        }
    }

    private fun optionalBoolean(
        jsonObject: JSONObject?,
        key: String,
    ): Boolean? {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.isNull(key)) {
            return null
        }
        return jsonObject.getBoolean(key)
    }

    private fun optionalInt(
        jsonObject: JSONObject?,
        key: String,
    ): Int? {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.isNull(key)) {
            return null
        }
        return jsonObject.getInt(key)
    }

    private fun migrateLegacyDirectionIfNeeded() {
        if (prefs.contains(PREF_CURRENT_ROUTE_SELECTION)) {
            return
        }

        val migratedSelection = when (prefs.getString(PREF_LEGACY_DIRECTION, null)) {
            "to_palmovka" -> defaultRouteToPalmovka()
            "to_nadrazi_vrsovice" -> defaultRouteToNadraziVrsovice()
            else -> defaultRouteSelection()
        }
        prefs.edit {
            putString(PREF_CURRENT_ROUTE_SELECTION, routeSelectionToJsonString(migratedSelection))
        }
        Log.d(TAG, "Migrated legacy direction preference to route selection.")
    }

    private fun rebindSelectionToCachedCatalog(selection: RouteSelection): RouteSelection {
        val catalog = catalogRepository.getCachedCatalog() ?: return selection

        fun rebindStopSelection(stopSelection: StopSelection): StopSelection {
            val station = catalog.stationByKey(stopSelection.stationKey) ?: return stopSelection
            return station.resolveSelection(stopSelection.platformKey)
        }

        val reboundLine = selection.line?.let { storedLine ->
            catalog.lines
                .firstOrNull { it.shortName.equals(storedLine.shortName, ignoreCase = true) }
                ?.toSelection()
                ?: storedLine
        }

        return RouteSelection(
            origin = rebindStopSelection(selection.origin),
            destination = rebindStopSelection(selection.destination),
            line = reboundLine,
        )
    }

    private fun routeSelectionToJsonString(selection: RouteSelection): String {
        return JSONObject().apply {
            put("origin", stopSelectionToJson(selection.origin))
            put("destination", stopSelectionToJson(selection.destination))
            put(
                "line",
                selection.line?.let { line ->
                    JSONObject().apply {
                        put("shortName", line.shortName)
                        put("longName", line.longName)
                    }
                },
            )
        }.toString()
    }

    private fun routeSelectionFromJsonString(rawValue: String): RouteSelection? {
        return runCatching {
            val json = JSONObject(rawValue)
            val origin = stopSelectionFromJson(json.getJSONObject("origin"))
            val destination = stopSelectionFromJson(json.getJSONObject("destination"))
            val line = json.optJSONObject("line")?.let { lineJson ->
                LineSelection(
                    shortName = lineJson.getString("shortName"),
                    longName = lineJson.optString("longName").ifBlank { null },
                )
            }
            RouteSelection(
                origin = origin,
                destination = destination,
                line = line,
            )
        }.getOrNull()
    }

    private fun routeSelectionListToJsonString(selections: List<RouteSelection>): String {
        return JSONArray().apply {
            selections.forEach { selection ->
                put(JSONObject(routeSelectionToJsonString(selection)))
            }
        }.toString()
    }

    private fun routeSelectionListFromJsonString(rawValue: String): List<RouteSelection> {
        return runCatching {
            val array = JSONArray(rawValue)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    routeSelectionFromJsonString(item.toString())?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun stopSelectionToJson(selection: StopSelection): JSONObject {
        return JSONObject().apply {
            put("stationKey", selection.stationKey)
            put("stationName", selection.stationName)
            put("platformKey", selection.platformKey)
            put("platformLabel", selection.platformLabel)
            put("stopIds", JSONArray(selection.stopIds))
        }
    }

    private fun stopSelectionFromJson(json: JSONObject): StopSelection {
        return StopSelection(
            stationKey = json.getString("stationKey"),
            stationName = json.getString("stationName"),
            platformKey = json.optString("platformKey").ifBlank { null },
            platformLabel = json.optString("platformLabel").ifBlank { null },
            stopIds = buildList {
                val array = json.optJSONArray("stopIds") ?: JSONArray()
                for (index in 0 until array.length()) {
                    val stopId = array.optString(index)
                    if (stopId.isNotBlank()) {
                        add(stopId)
                    }
                }
            },
        )
    }
}

data class RouteDeparture(
    val tripId: String,
    val lineShortName: String,
    val departureTime: ZonedDateTime,
    val countdownMinutes: Int,
    val delayMinutes: Int,
    val boardedStopId: String? = null,
    val boardedPlatformLabel: String? = null,
    val departureBoardDetails: DepartureBoardDetails,
    val vehiclePositionDetails: VehiclePositionDetails?,
    val destinationArrivalTime: ZonedDateTime?,
) {
    val rowKey: String
        get() = "$tripId@${boardedStopId ?: "*"}@${departureTime.toInstant().toEpochMilli()}"

    val compactLabel: String
        get() = "$lineShortName:$countdownMinutes [${delayMinutes.formatDelay()}]"

    val lineLabel: String
        get() = "Line $lineShortName"

    val countdownLabel: String
        get() = countdownMinutes.toString()

    val listTimeLabel: String
        get() = when (countdownMinutes) {
            0 -> "now"
            1 -> "1 min"
            else -> "$countdownMinutes min"
        }

    val delayBadgeLabel: String?
        get() = delayMinutes.takeIf { it > 0 }?.let { "+$it" }

    val boardingPlatformDisplayLabel: String?
        get() = boardedPlatformLabel?.takeIf { it.isNotBlank() } ?: boardedStopId?.takeIf { it.isNotBlank() }

    val detailStatusLabel: String
        get() = buildString {
            append(
                when (countdownMinutes) {
                    0 -> "Due now"
                    1 -> "In 1 min"
                    else -> "In $countdownMinutes min"
                }
            )
            delayBadgeLabel?.let { badge ->
                append("  Delay ")
                append(badge)
                append(" min")
            }
        }

    fun clockLabel(
        showSeconds: Boolean,
        includeLine: Boolean,
    ): String {
        return if (includeLine) {
            "$lineShortName  ${formatDisplayTime(departureTime, showSeconds)}"
        } else {
            formatDisplayTime(departureTime, showSeconds)
        }
    }

    fun activityStatusLabel(
        referenceNow: ZonedDateTime,
        showSeconds: Boolean,
    ): String {
        val baseLabel = if (showSeconds) {
            liveCountdownLabel(referenceNow)
        } else {
            when (countdownMinutes) {
                0 -> "Due now"
                1 -> "In 1 min"
                else -> "In $countdownMinutes min"
            }
        }

        return buildString {
            append(baseLabel)
            delayBadgeLabel?.let { badge ->
                append("  Delay ")
                append(badge)
                append(" min")
            }
        }
    }

    fun tileLineLabel(
        showSeconds: Boolean,
        includeLine: Boolean,
    ): String {
        return buildString {
            append(clockLabel(showSeconds, includeLine))
            append("  ")
            append(listTimeLabel)
            delayBadgeLabel?.let { badge ->
                append("  ")
                append(badge)
            }
        }
    }

    private fun liveCountdownLabel(referenceNow: ZonedDateTime): String {
        val remainingSeconds = max(0L, Duration.between(referenceNow, departureTime).seconds)
        if (remainingSeconds == 0L) {
            return "Due now"
        }

        val minutesPart = remainingSeconds / 60
        val secondsPart = remainingSeconds % 60
        return when {
            minutesPart > 0L -> "In $minutesPart min ${secondsPart.toString().padStart(2, '0')} s"
            else -> "In $secondsPart s"
        }
    }
}

data class DepartureBoardDetails(
    val departureTime: BoardStopTime,
    val originArrivalTime: BoardStopTime?,
    val delaySeconds: Int?,
)

data class VehiclePositionDetails(
    val delaySeconds: Int?,
    val originTimestamp: ZonedDateTime?,
)

data class DepartureSnapshot(
    val selection: RouteSelection,
    val departures: List<RouteDeparture>,
    val fetchedAt: ZonedDateTime,
    val requestedMatchCount: Int = RouteRepository.DEFAULT_VERIFIED_MATCH_COUNT,
    val isStale: Boolean = false,
    val errorMessage: String? = null,
) {
    fun tileLines(showSeconds: Boolean = false): List<String> {
        val includeLine = !selection.usesFixedLine()
        val liveLines = departures
            .take(requestedMatchCount)
            .map { it.tileLineLabel(showSeconds, includeLine) }
        return if (liveLines.isNotEmpty()) {
            liveLines
        } else {
            val placeholderPrefix = if (includeLine) "--  " else ""
            List(requestedMatchCount) {
                if (showSeconds) {
                    "$placeholderPrefix--:--:--  -- min"
                } else {
                    "$placeholderPrefix--:--  -- min"
                }
            }
        }
    }

    fun debugSummary(): String {
        return "route=${selection.routeSummaryWithPlatforms} departures=${departures.map { it.compactLabel }} isStale=$isStale errorMessage=$errorMessage"
    }
}

private data class DepartureQueryResult(
    val departures: List<RouteDeparture>,
    val referenceNow: ZonedDateTime,
)

private fun Int.formatDelay(): String {
    return if (this > 0) {
        "+$this"
    } else {
        toString()
    }
}

private fun ApiException.isTimeFromOutOfRange(): Boolean {
    return statusCode == 400 &&
        responseBody.contains("timeFrom", ignoreCase = true) &&
        (
            responseBody.contains("Parameter timeFrom must be in interval", ignoreCase = true) ||
                responseBody.contains("[-6h; +2d]", ignoreCase = true)
            )
}
