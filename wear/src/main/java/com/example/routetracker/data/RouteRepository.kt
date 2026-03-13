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
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val TAG = "RouteRepository"
private const val MAX_LOG_BODY_LENGTH = 500
private const val PREFS_NAME = "route_prefs"
private const val PREF_DIRECTION = "selected_direction"

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
        private const val CACHE_WINDOW_MILLIS = 2_000L

        const val LINE_NAME = "7"
        const val MAX_RESULTS = 3

        private val PRAGUE_ZONE: ZoneId = ZoneId.of("Europe/Prague")
        private val CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        @Volatile
        private var cachedSnapshot: DepartureSnapshot? = null

        @Volatile
        private var cacheTimestampElapsedRealtime: Long = 0L

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

    fun getDepartureSnapshot(forceRefresh: Boolean = false): DepartureSnapshot {
        val selectedDirection = getSelectedDirection()
        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        val cached = cachedSnapshot
        if (!forceRefresh &&
            cached != null &&
            cached.direction == selectedDirection &&
            nowElapsedRealtime - cacheTimestampElapsedRealtime < CACHE_WINDOW_MILLIS
        ) {
            Log.d(TAG, "Returning cached snapshot with ${cached.departures.size} departures for ${selectedDirection.preferenceKey}.")
            return cached
        }

        synchronized(RouteRepository::class.java) {
            val refreshedElapsedRealtime = SystemClock.elapsedRealtime()
            val currentCached = cachedSnapshot
            if (!forceRefresh &&
                currentCached != null &&
                currentCached.direction == selectedDirection &&
                refreshedElapsedRealtime - cacheTimestampElapsedRealtime < CACHE_WINDOW_MILLIS
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
        return departure.departureTime.format(CLOCK_FORMATTER)
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

            val departureTime = parseBoardTimestamp(departure, "departure_timestamp")
            if (departureTime == null) {
                skippedMissingTime += 1
                continue
            }
            val tripId = trip?.optString("id").orEmpty()
            if (tripId.isBlank()) {
                skippedMissingTripId += 1
                continue
            }

            val tripDetail = apiGetObject(
                "/v2/gtfs/trips/$tripId",
                mapOf(
                    "date" to departureTime.toLocalDate().toString(),
                    "includeStops" to "true",
                    "includeStopTimes" to "true",
                    "includeRoute" to "true",
                )
            )
            val stopTimes = tripDetail.optJSONArray("stop_times") ?: JSONArray()
            val boardingIndex = findBoardingIndex(stopTimes, direction.sourceStopId, departureTime)
            if (boardingIndex == null) {
                skippedMissingBoardingIndex += 1
                continue
            }
            if (!reachesDestination(stopTimes, boardingIndex, direction.destinationStopId)) {
                skippedMissingDestination += 1
                continue
            }

            var canceled = optionalBoolean(trip, "is_canceled")
            var delaySeconds = extractBoardDelaySeconds(departure)

            fetchVehiclePosition(tripId)?.let { vehiclePosition: JSONObject ->
                val lastPosition = vehiclePosition
                    .optJSONObject("properties")
                    ?.optJSONObject("last_position")
                delaySeconds = optionalInt(lastPosition?.optJSONObject("delay"), "actual") ?: delaySeconds
                optionalBoolean(lastPosition, "is_canceled")?.let { canceled = it }
            }

            if (canceled == true) {
                skippedCanceled += 1
                continue
            }

            val matchedDeparture = RouteDeparture(
                tripId = tripId,
                departureTime = departureTime,
                minutesUntilDeparture = minutesUntilDeparture(referenceNow, departureTime),
                delayMinutes = (delaySeconds / 60.0).roundToInt(),
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
        return try {
            apiGetObject(
                "/v2/vehiclepositions/$tripId",
                mapOf("preferredTimezone" to "Europe/Prague")
            )
        } catch (error: ApiException) {
            if (error.statusCode == 404) {
                Log.d(TAG, "Vehicle position not found for tripId=$tripId")
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

    private fun readResponseBody(connection: HttpURLConnection, statusCode: Int): String {
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun parseBoardTimestamp(entry: JSONObject, fieldName: String): ZonedDateTime? {
        val timestamps = entry.optJSONObject(fieldName) ?: return null
        val value = timestamps.optString("predicted").ifBlank {
            timestamps.optString("scheduled")
        }
        if (value.isBlank()) {
            return null
        }
        return OffsetDateTime.parse(value).atZoneSameInstant(PRAGUE_ZONE)
    }

    private fun extractBoardDelaySeconds(entry: JSONObject): Int {
        optionalInt(entry.optJSONObject("delay"), "seconds")?.let { return it }

        val timestamps = entry.optJSONObject("departure_timestamp") ?: return 0
        val predicted = timestamps.optString("predicted")
        val scheduled = timestamps.optString("scheduled")
        if (predicted.isBlank() || scheduled.isBlank()) {
            return 0
        }

        return Duration.between(
            OffsetDateTime.parse(scheduled),
            OffsetDateTime.parse(predicted),
        ).seconds.toInt()
    }

    private fun findBoardingIndex(
        stopTimes: JSONArray,
        boardedStopId: String,
        departureTime: ZonedDateTime,
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

            val scheduledDeparture = combineServiceTime(departureTime, departureValue)
            if (!scheduledDeparture.plusMinutes(1).isBefore(departureTime)) {
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

    private fun minutesUntilDeparture(now: ZonedDateTime, departureTime: ZonedDateTime): Int {
        return max(0, Duration.between(now, departureTime).toMinutes().toInt())
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
    val minutesUntilDeparture: Int,
    val delayMinutes: Int,
) {
    val compactLabel: String
        get() = "$minutesUntilDeparture [${delayMinutes.formatDelay()}]"

    val minutesLabel: String
        get() = minutesUntilDeparture.toString()

    val complicationDelayLabel: String?
        get() = delayMinutes.takeIf { it > 0 }?.formatDelay()

    val accessibilityLabel: String
        get() = buildString {
            append("Departure in $minutesUntilDeparture minutes.")
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
    fun tileLines(): List<String> {
        val liveLines = departures.take(RouteRepository.MAX_RESULTS).map { it.compactLabel }
        return if (liveLines.isNotEmpty()) {
            liveLines
        } else {
            List(RouteRepository.MAX_RESULTS) { "-- [--]" }
        }
    }

    fun complicationText(): String {
        return departures.firstOrNull()?.minutesLabel ?: "--"
    }

    fun complicationDelayText(): String? {
        return departures.firstOrNull()?.complicationDelayLabel
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
