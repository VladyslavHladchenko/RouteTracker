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
import com.example.routetracker.tile.MainTileService
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

private const val TAG = "RouteRepository"
private const val MAX_LOG_BODY_LENGTH = 500
private const val PREFS_NAME = "route_prefs"
private const val PREF_DIRECTION = "selected_direction"
private const val PREF_AUTO_UPDATES_ENABLED = "auto_updates_enabled"
private const val PREF_SHOW_SECONDS = "show_seconds"
private const val PREF_LIVE_SNAPSHOT_CACHE_MILLIS = "live_snapshot_cache_millis"
private const val PREF_GTFS_TRIP_DETAIL_CACHE_MILLIS = "gtfs_trip_detail_cache_millis"
private const val PREF_VEHICLE_POSITION_CACHE_MILLIS = "vehicle_position_cache_millis"
private val DISPLAY_CLOCK_MINUTES_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DISPLAY_CLOCK_SECONDS_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

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

fun formatDisplayTime(time: ZonedDateTime, showSeconds: Boolean): String {
    return time.format(
        if (showSeconds) DISPLAY_CLOCK_SECONDS_FORMATTER else DISPLAY_CLOCK_MINUTES_FORMATTER
    )
}

enum class RouteDirection(
    val preferenceKey: String,
    val buttonLabel: String,
    val tileLabel: String,
    val sourceStopId: String,
    val destinationStopId: String,
) {
    TO_PALMOVKA(
        preferenceKey = "to_palmovka",
        buttonLabel = "to Palmovka",
        tileLabel = "Palmovka",
        sourceStopId = "U463Z2P",
        destinationStopId = "U529Z2P",
    ),
    TO_NADRAZI_VRSOVICE(
        preferenceKey = "to_nadrazi_vrsovice",
        buttonLabel = "to N\u00E1dra\u017E\u00ED V\u0159\u0161ovice",
        tileLabel = "V\u0159\u0161ovice",
        sourceStopId = "U529Z1P",
        destinationStopId = "U463Z1P",
    );

    companion object {
        fun fromPreference(value: String?): RouteDirection {
            return entries.firstOrNull { it.preferenceKey == value } ?: TO_NADRAZI_VRSOVICE
        }
    }
}

class RouteRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val API_TOKEN =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6NDk0NywiaWF0IjoxNzczMTcxMDAxLCJleHAiOjExNzczMTcxMDAxLCJpc3MiOiJnb2xlbWlvIiwianRpIjoiMzA3M2I1NGItZTg1OC00MDFmLTllODMtYjA4ZWU0NTZhOTI2In0.x-b_ZZPX-yVnS24ABAMulVxB8cmeNviWd6aW7lTDldI"
        private const val API_BASE_URL = "https://api.golemio.cz"
        private const val SEARCH_WINDOW_MINUTES = 120
        private const val DEFAULT_TIMEOUT_MILLIS = 10_000
        private const val DEFAULT_LIVE_SNAPSHOT_CACHE_MILLIS = 2_000L
        private const val DEFAULT_GTFS_TRIP_DETAIL_CACHE_MILLIS = 60_000L
        private const val DEFAULT_VEHICLE_POSITION_CACHE_MILLIS = 2_000L

        const val LINE_NAME = "7"
        const val MAX_RESULTS = 3

        private val PRAGUE_ZONE: ZoneId = ZoneId.of("Europe/Prague")
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
        @Volatile
        private var cachedSnapshot: DepartureSnapshot? = null

        @Volatile
        private var cacheTimestampElapsedRealtime: Long = 0L
        private val tripDetailCache = TimedMemoryCache<TripDetailCacheKey, JSONObject>()
        private val vehiclePositionCache = TimedMemoryCache<String, VehiclePositionCacheValue>()

        fun previewSnapshot(
            direction: RouteDirection = RouteDirection.TO_NADRAZI_VRSOVICE,
            now: ZonedDateTime = ZonedDateTime.now(PRAGUE_ZONE),
        ): DepartureSnapshot {
            return DepartureSnapshot(
                direction = direction,
                departures = listOf(
                    RouteDeparture("preview-1", now.plusMinutes(3), 3, 1),
                    RouteDeparture("preview-2", now.plusMinutes(11), 11, 0),
                    RouteDeparture("preview-3", now.plusMinutes(19), 19, -1),
                ),
                fetchedAt = now,
            )
        }
    }

    fun getSelectedDirection(): RouteDirection {
        return RouteDirection.fromPreference(prefs.getString(PREF_DIRECTION, null))
    }

    fun getAutoUpdatesEnabled(): Boolean {
        return prefs.getBoolean(PREF_AUTO_UPDATES_ENABLED, true)
    }

    fun getShowSecondsEnabled(): Boolean {
        return prefs.getBoolean(PREF_SHOW_SECONDS, false)
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

    fun getLiveSnapshotCacheLabel(): String {
        return cacheDurationLabel(getLiveSnapshotCacheMillis())
    }

    fun getGtfsTripDetailCacheLabel(): String {
        return cacheDurationLabel(getGtfsTripDetailCacheMillis())
    }

    fun getVehiclePositionCacheLabel(): String {
        return cacheDurationLabel(getVehiclePositionCacheMillis())
    }

    fun setSelectedDirection(direction: RouteDirection) {
        val currentDirection = getSelectedDirection()
        if (currentDirection == direction) {
            Log.d(TAG, "Direction already selected: ${direction.preferenceKey}")
            return
        }

        prefs.edit {
            putString(PREF_DIRECTION, direction.preferenceKey)
        }

        cachedSnapshot = null
        cacheTimestampElapsedRealtime = 0L
        Log.d(TAG, "Direction changed to ${direction.preferenceKey}")
        requestSurfaceRefresh()
    }

    fun setAutoUpdatesEnabled(enabled: Boolean) {
        val currentValue = getAutoUpdatesEnabled()
        if (currentValue == enabled) {
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
        val currentValue = getShowSecondsEnabled()
        if (currentValue == enabled) {
            Log.d(TAG, "Show seconds already set to $enabled")
            return
        }

        prefs.edit {
            putBoolean(PREF_SHOW_SECONDS, enabled)
        }

        Log.d(TAG, "Show seconds changed to $enabled")
        requestSurfaceRefresh()
    }

    fun cycleLiveSnapshotCacheMillis(): Long {
        val nextValue = nextCacheDuration(
            currentMillis = getLiveSnapshotCacheMillis(),
            options = LIVE_SNAPSHOT_CACHE_OPTIONS,
        )
        prefs.edit {
            putLong(PREF_LIVE_SNAPSHOT_CACHE_MILLIS, nextValue)
        }
        Log.d(TAG, "Live snapshot cache changed to ${cacheDurationLabel(nextValue)}")
        requestSurfaceRefresh()
        return nextValue
    }

    fun cycleGtfsTripDetailCacheMillis(): Long {
        val nextValue = nextCacheDuration(
            currentMillis = getGtfsTripDetailCacheMillis(),
            options = GTFS_TRIP_DETAIL_CACHE_OPTIONS,
        )
        prefs.edit {
            putLong(PREF_GTFS_TRIP_DETAIL_CACHE_MILLIS, nextValue)
        }
        Log.d(TAG, "GTFS trip detail cache changed to ${cacheDurationLabel(nextValue)}")
        requestSurfaceRefresh()
        return nextValue
    }

    fun cycleVehiclePositionCacheMillis(): Long {
        val nextValue = nextCacheDuration(
            currentMillis = getVehiclePositionCacheMillis(),
            options = VEHICLE_POSITION_CACHE_OPTIONS,
        )
        prefs.edit {
            putLong(PREF_VEHICLE_POSITION_CACHE_MILLIS, nextValue)
        }
        Log.d(TAG, "Vehicle position cache changed to ${cacheDurationLabel(nextValue)}")
        requestSurfaceRefresh()
        return nextValue
    }

    fun getDepartureSnapshot(forceRefresh: Boolean = false): DepartureSnapshot {
        val selectedDirection = getSelectedDirection()
        val snapshotCacheMillis = getLiveSnapshotCacheMillis()
        if (!forceRefresh && !getAutoUpdatesEnabled()) {
            return pausedSnapshot(
                direction = selectedDirection,
                cached = cachedSnapshot,
            )
        }

        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        val cached = cachedSnapshot
        if (!forceRefresh &&
            snapshotCacheMillis > 0L &&
            cached != null &&
            cached.direction == selectedDirection &&
            nowElapsedRealtime - cacheTimestampElapsedRealtime < snapshotCacheMillis
        ) {
            Log.d(TAG, "Returning cached snapshot with ${cached.departures.size} departures for ${selectedDirection.preferenceKey}.")
            return cached
        }

        synchronized(RouteRepository::class.java) {
            val synchronizedSnapshotCacheMillis = getLiveSnapshotCacheMillis()
            if (!forceRefresh && !getAutoUpdatesEnabled()) {
                return pausedSnapshot(
                    direction = selectedDirection,
                    cached = cachedSnapshot,
                )
            }

            val refreshedElapsedRealtime = SystemClock.elapsedRealtime()
            val currentCached = cachedSnapshot
            if (!forceRefresh &&
                synchronizedSnapshotCacheMillis > 0L &&
                currentCached != null &&
                currentCached.direction == selectedDirection &&
                refreshedElapsedRealtime - cacheTimestampElapsedRealtime < synchronizedSnapshotCacheMillis
            ) {
                Log.d(TAG, "Returning cached snapshot after synchronized check for ${selectedDirection.preferenceKey}.")
                return currentCached
            }

            Log.d(TAG, "Refreshing snapshot. forceRefresh=$forceRefresh direction=${selectedDirection.preferenceKey}")
            return try {
                fetchDepartureSnapshot(selectedDirection).also { snapshot ->
                    cachedSnapshot = snapshot
                    cacheTimestampElapsedRealtime = refreshedElapsedRealtime
                    Log.d(
                        TAG,
                        "Snapshot refreshed successfully with ${snapshot.departures.size} departures at ${snapshot.fetchedAt}.",
                    )
                }
            } catch (error: Exception) {
                Log.e(TAG, "Failed to refresh snapshot.", error)
                currentCached?.takeIf { it.direction == selectedDirection }?.copy(
                    isStale = true,
                    errorMessage = "Showing cached data.",
                ) ?: DepartureSnapshot(
                    direction = selectedDirection,
                    departures = emptyList(),
                    fetchedAt = ZonedDateTime.now(PRAGUE_ZONE),
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

    private fun requestSurfaceRefresh() {
        try {
            TileService.getUpdater(context)
                .requestUpdate(MainTileService::class.java)
            Log.d(TAG, "Requested tile refresh.")
        } catch (_: Exception) {
            Log.w(TAG, "Tile refresh request failed.")
        }

        try {
            val componentName = ComponentName(context, MainComplicationService::class.java)
            ComplicationDataSourceUpdateRequester
                .create(context, componentName)
                .requestUpdateAll()
            Log.d(TAG, "Requested complication refresh.")
        } catch (_: Exception) {
            Log.w(TAG, "Complication refresh request failed.")
        }
    }

    private fun pausedSnapshot(
        direction: RouteDirection,
        cached: DepartureSnapshot?,
    ): DepartureSnapshot {
        val matchingCached = cached?.takeIf { it.direction == direction }
        return matchingCached?.copy(
            isStale = true,
            errorMessage = "Updates paused.",
        ) ?: DepartureSnapshot(
            direction = direction,
            departures = emptyList(),
            fetchedAt = ZonedDateTime.now(PRAGUE_ZONE),
            isStale = true,
            errorMessage = "Updates paused.",
        )
    }

    private fun fetchDepartureSnapshot(direction: RouteDirection): DepartureSnapshot {
        val departureResult = fetchDirectDepartures(direction)
        val snapshot = DepartureSnapshot(
            direction = direction,
            departures = departureResult.departures,
            fetchedAt = departureResult.referenceNow,
        )
        Log.d(TAG, "Fetched snapshot summary: ${snapshot.debugSummary()}")
        return snapshot
    }

    private fun fetchDirectDepartures(direction: RouteDirection): DepartureQueryResult {
        val deviceNow = ZonedDateTime.now(PRAGUE_ZONE)
        val boardLimit = max(50, MAX_RESULTS * 12)
        val boardResponse = fetchDepartureBoard(deviceNow, boardLimit, direction.sourceStopId)
        val payload = boardResponse.body
        val referenceNow = deviceNow

        val departures = payload.optJSONArray("departures") ?: JSONArray()
        val matches = mutableListOf<RouteDeparture>()
        var skippedWrongLine = 0
        var skippedWrongStop = 0
        var skippedMissingTime = 0
        var skippedMissingTripId = 0
        var skippedMissingBoardingIndex = 0
        var skippedMissingDestination = 0
        var skippedCanceled = 0

        for (index in 0 until departures.length()) {
            val departure = departures.optJSONObject(index) ?: continue
            val route = departure.optJSONObject("route")
            val stop = departure.optJSONObject("stop")
            val trip = departure.optJSONObject("trip")

            if (!route?.optString("short_name").orEmpty().equals(LINE_NAME, ignoreCase = true)) {
                skippedWrongLine += 1
                continue
            }
            if (stop?.optString("id").orEmpty() != direction.sourceStopId) {
                skippedWrongStop += 1
                continue
            }

            val boardStopTime = parseBoardStopTime(departure, "departure_timestamp")
            if (boardStopTime == null) {
                skippedMissingTime += 1
                continue
            }
            val tripId = trip?.optString("id").orEmpty()
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
                boardedStopId = direction.sourceStopId,
                boardingReferenceTime = boardStopTime.bestAvailableTime,
            )
            if (boardingIndex == null) {
                skippedMissingBoardingIndex += 1
                continue
            }
            if (!reachesDestination(stopTimes, boardingIndex, direction.destinationStopId)) {
                skippedMissingDestination += 1
                continue
            }

            var canceled = optionalBoolean(trip, "is_canceled")
            val boardDelaySeconds = extractBoardDelaySeconds(departure)
            var vehicleDelaySeconds: Int? = null

            fetchVehiclePosition(tripId)?.let { vehiclePosition: JSONObject ->
                val lastPosition = vehiclePosition
                    .optJSONObject("properties")
                    ?.optJSONObject("last_position")
                vehicleDelaySeconds = optionalInt(lastPosition?.optJSONObject("delay"), "actual")
                optionalBoolean(lastPosition, "is_canceled")?.let { canceled = it }
            }

            if (canceled == true) {
                skippedCanceled += 1
                continue
            }

            val resolvedTiming = resolveDepartureTiming(
                boardStopTime = boardStopTime,
                boardDelaySeconds = boardDelaySeconds,
                vehicleDelaySeconds = vehicleDelaySeconds,
            )
            val matchedDeparture = resolvedTiming.toRouteDeparture(
                tripId = tripId,
                referenceNow = referenceNow,
            )
            matches += matchedDeparture
            Log.d(
                TAG,
                "Accepted departure: direction=${direction.preferenceKey} tripId=$tripId label=${matchedDeparture.compactLabel} at ${matchedDeparture.departureTime}",
            )

            if (matches.size >= MAX_RESULTS) {
                break
            }
        }

        Log.d(
            TAG,
            "Route scan done. direction=${direction.preferenceKey} boardCount=${departures.length()} matches=${matches.size} " +
                "skippedWrongLine=$skippedWrongLine skippedWrongStop=$skippedWrongStop " +
                "skippedMissingTime=$skippedMissingTime skippedMissingTripId=$skippedMissingTripId " +
                "skippedMissingBoardingIndex=$skippedMissingBoardingIndex " +
                "skippedMissingDestination=$skippedMissingDestination skippedCanceled=$skippedCanceled",
        )
        return DepartureQueryResult(
            departures = matches.sortedBy { it.departureTime }.take(MAX_RESULTS),
            referenceNow = referenceNow,
        )
    }

    private fun fetchDepartureBoard(
        now: ZonedDateTime,
        boardLimit: Int,
        sourceStopId: String,
    ): ApiJsonResponse {
        val params = linkedMapOf(
            "ids" to sourceStopId,
            "timeFrom" to now.toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS).toString(),
            "minutesAfter" to SEARCH_WINDOW_MINUTES.toString(),
            "limit" to boardLimit.toString(),
            "order" to "real",
        )

        return try {
            apiGetResponseObject("/v2/pid/departureboards", params)
        } catch (error: ApiException) {
            if (!error.isTimeFromOutOfRange()) {
                throw error
            }

            Log.w(
                TAG,
                "Departure board rejected timeFrom=${params["timeFrom"]}. Retrying without timeFrom. body=${error.responseBody.truncatedForLog()}",
            )
            val fallbackParams = LinkedHashMap(params).apply {
                remove("timeFrom")
            }
            apiGetResponseObject("/v2/pid/departureboards", fallbackParams)
        }
    }

    private fun apiGetObject(path: String, params: Map<String, String>): JSONObject {
        return apiGetResponseObject(path, params).body
    }

    private fun fetchTripDetail(
        tripId: String,
        serviceDate: String,
    ): JSONObject {
        val cacheKey = TripDetailCacheKey(
            tripId = tripId,
            serviceDate = serviceDate,
        )
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

        val tripDetail = apiGetObject(
            "/v2/gtfs/trips/$tripId",
            mapOf(
                "date" to serviceDate,
                "includeStops" to "true",
                "includeStopTimes" to "true",
                "includeRoute" to "true",
            )
        )

        if (ttlMillis > 0L) {
            tripDetailCache.put(
                key = cacheKey,
                value = tripDetail,
                nowElapsedRealtime = nowElapsedRealtime,
            )
        }

        return tripDetail
    }

    private fun apiGetResponseObject(path: String, params: Map<String, String>): ApiJsonResponse {
        val query = params.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.urlEncode()}"
        }
        val url = URL(
            buildString {
                append(API_BASE_URL)
                append(path)
                if (query.isNotEmpty()) {
                    append('?')
                    append(query)
                }
            }
        )

        repeat(4) { attempt ->
            Log.d(TAG, "GET $path attempt=${attempt + 1} params=$params")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = DEFAULT_TIMEOUT_MILLIS
                readTimeout = DEFAULT_TIMEOUT_MILLIS
                setRequestProperty("accept", "application/json")
                setRequestProperty("X-Access-Token", API_TOKEN)
            }

            try {
                val statusCode = connection.responseCode
                val responseBody = readResponseBody(connection, statusCode)

                if (statusCode == 429) {
                    val retryAfterSeconds = connection.getHeaderField("Retry-After")
                        ?.toDoubleOrNull()
                        ?: (1.5 * (attempt + 1))
                    Log.w(TAG, "Rate limited on $path. retryAfterSeconds=$retryAfterSeconds")
                    Thread.sleep(min((retryAfterSeconds * 1_000).toLong(), 6_000L))
                    return@repeat
                }

                if (statusCode !in 200..299) {
                    if (statusCode == 404 && path.startsWith("/v2/vehiclepositions/")) {
                        Log.d(
                            TAG,
                            "Request returned no live vehicle position. path=$path status=$statusCode body=${responseBody.truncatedForLog()}",
                        )
                    } else {
                        Log.e(
                            TAG,
                            "Request failed. path=$path status=$statusCode body=${responseBody.truncatedForLog()}",
                        )
                    }
                    throw ApiException(
                        statusCode = statusCode,
                        message = "HTTP $statusCode for $path",
                        responseBody = responseBody,
                    )
                }

                Log.d(TAG, "Request succeeded. path=$path status=$statusCode")
                return ApiJsonResponse(
                    body = JSONObject(responseBody),
                )
            } finally {
                connection.disconnect()
            }
        }

        throw IOException("Golemio API rate-limited the request too many times.")
    }

    private fun fetchVehiclePosition(tripId: String): JSONObject? {
        val ttlMillis = getVehiclePositionCacheMillis()
        val nowElapsedRealtime = SystemClock.elapsedRealtime()

        vehiclePositionCache.getIfFresh(
            key = tripId,
            ttlMillis = ttlMillis,
            nowElapsedRealtime = nowElapsedRealtime,
        )?.let { cachedValue ->
            when (cachedValue) {
                is VehiclePositionCacheValue.Present -> {
                    Log.d(TAG, "Using cached vehicle position for tripId=$tripId")
                    return cachedValue.body
                }

                VehiclePositionCacheValue.Missing -> {
                    Log.d(TAG, "Using cached vehicle-position miss for tripId=$tripId")
                    return null
                }
            }
        }

        return try {
            val vehiclePosition = apiGetObject(
                "/v2/vehiclepositions/$tripId",
                mapOf("preferredTimezone" to "Europe/Prague")
            )
            if (ttlMillis > 0L) {
                vehiclePositionCache.put(
                    key = tripId,
                    value = VehiclePositionCacheValue.Present(vehiclePosition),
                    nowElapsedRealtime = nowElapsedRealtime,
                )
            }
            vehiclePosition
        } catch (error: ApiException) {
            if (error.statusCode == 404) {
                Log.d(TAG, "Vehicle position not found for tripId=$tripId")
                if (ttlMillis > 0L) {
                    vehiclePositionCache.put(
                        key = tripId,
                        value = VehiclePositionCacheValue.Missing,
                        nowElapsedRealtime = nowElapsedRealtime,
                    )
                }
                null
            } else {
                Log.e(
                    TAG,
                    "Vehicle position lookup failed for tripId=$tripId body=${error.responseBody.truncatedForLog()}",
                )
                throw error
            }
        }
    }

    private fun resolveDepartureTiming(
        boardStopTime: BoardStopTime,
        boardDelaySeconds: Int?,
        vehicleDelaySeconds: Int?,
    ): ResolvedDepartureTiming {
        return DepartureTimingResolver.resolve(
            DepartureRealtimeInputs(
                scheduledDepartureTime = boardStopTime.scheduledTime,
                predictedDepartureTime = boardStopTime.predictedTime,
                boardDelaySeconds = boardDelaySeconds,
                vehicleDelaySeconds = vehicleDelaySeconds,
            )
        )
    }

    private fun readResponseBody(connection: HttpURLConnection, statusCode: Int): String {
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun parseBoardStopTime(entry: JSONObject, fieldName: String): BoardStopTime? {
        val timestamps = entry.optJSONObject(fieldName) ?: return null
        val scheduledValue = timestamps.optString("scheduled")
        if (scheduledValue.isBlank()) {
            return null
        }

        val predictedValue = timestamps.optString("predicted").ifBlank { null }
        return BoardStopTime(
            scheduledTime = OffsetDateTime.parse(scheduledValue).atZoneSameInstant(PRAGUE_ZONE),
            predictedTime = predictedValue?.let { OffsetDateTime.parse(it).atZoneSameInstant(PRAGUE_ZONE) },
        )
    }

    private fun extractBoardDelaySeconds(entry: JSONObject): Int? {
        optionalInt(entry.optJSONObject("delay"), "seconds")?.let { return it }

        val timestamps = entry.optJSONObject("departure_timestamp") ?: return null
        val predicted = timestamps.optString("predicted")
        val scheduled = timestamps.optString("scheduled")
        if (predicted.isBlank() || scheduled.isBlank()) {
            return null
        }

        return java.time.Duration.between(
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

    private fun reachesDestination(stopTimes: JSONArray, boardingIndex: Int, destinationStopId: String): Boolean {
        for (index in boardingIndex + 1 until stopTimes.length()) {
            val stopTime = stopTimes.optJSONObject(index) ?: continue
            if (stopTime.optString("stop_id") == destinationStopId) {
                return true
            }
        }
        return false
    }

    private fun combineServiceTime(anchor: ZonedDateTime, hhmmss: String): ZonedDateTime {
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

    private fun optionalBoolean(jsonObject: JSONObject?, key: String): Boolean? {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.isNull(key)) {
            return null
        }
        return jsonObject.getBoolean(key)
    }

    private fun optionalInt(jsonObject: JSONObject?, key: String): Int? {
        if (jsonObject == null || !jsonObject.has(key) || jsonObject.isNull(key)) {
            return null
        }
        return jsonObject.getInt(key)
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8)
}

data class RouteDeparture(
    val tripId: String,
    val departureTime: ZonedDateTime,
    val countdownMinutes: Int,
    val delayMinutes: Int,
) {
    val compactLabel: String
        get() = "$countdownMinutes [${delayMinutes.formatDelay()}]"

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

    fun tileLineLabel(showSeconds: Boolean): String {
        return buildString {
            append(formatDisplayTime(departureTime, showSeconds))
            append("  ")
            append(listTimeLabel)
            delayBadgeLabel?.let { badge ->
                append("  ")
                append(badge)
            }
        }
    }

    val complicationDelayLabel: String?
        get() = delayMinutes.takeIf { it > 0 }?.formatDelay()

    val accessibilityLabel: String
        get() = buildString {
            append("Departure in $countdownMinutes minutes.")
            if (delayMinutes > 0) {
                append(" Delay ${delayMinutes.formatDelay()} minutes.")
            }
        }
}

data class DepartureSnapshot(
    val direction: RouteDirection,
    val departures: List<RouteDeparture>,
    val fetchedAt: ZonedDateTime,
    val isStale: Boolean = false,
    val errorMessage: String? = null,
) {
    fun tileLines(showSeconds: Boolean = false): List<String> {
        val liveLines = departures.take(RouteRepository.MAX_RESULTS).map { it.tileLineLabel(showSeconds) }
        return if (liveLines.isNotEmpty()) {
            liveLines
        } else {
            List(RouteRepository.MAX_RESULTS) { if (showSeconds) "--:--:--  -- min" else "--:--  -- min" }
        }
    }

    fun complicationText(): String {
        return departures.firstOrNull()?.countdownLabel ?: "--"
    }

    fun complicationDelayText(): String? {
        return null
    }

    fun debugSummary(): String {
        return "direction=${direction.preferenceKey} departures=${departures.map { it.compactLabel }} isStale=$isStale errorMessage=$errorMessage"
    }
}

private fun Int.formatDelay(): String {
    return if (this > 0) {
        "+$this"
    } else {
        toString()
    }
}

private class ApiException(
    val statusCode: Int,
    val responseBody: String,
    message: String,
) : IOException(message)

private data class ApiJsonResponse(
    val body: JSONObject,
)

private data class DepartureQueryResult(
    val departures: List<RouteDeparture>,
    val referenceNow: ZonedDateTime,
)

private fun String.truncatedForLog(): String {
    return if (length <= MAX_LOG_BODY_LENGTH) this else take(MAX_LOG_BODY_LENGTH) + "..."
}

private fun ApiException.isTimeFromOutOfRange(): Boolean {
    return statusCode == 400 &&
        responseBody.contains("\"timeFrom\"", ignoreCase = true) &&
        responseBody.contains("Parameter timeFrom must be in interval", ignoreCase = true)
}
