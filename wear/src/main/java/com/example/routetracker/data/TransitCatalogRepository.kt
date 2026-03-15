package com.example.routetracker.data

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.json.JSONArray
import org.json.JSONObject

private const val CATALOG_TAG = "TransitCatalogRepo"
private const val GTFS_PAGE_LIMIT = 10_000
private const val STATIC_CATALOG_TTL_HOURS = 24L
private const val STATIC_CATALOG_FILE_NAME = "transit_catalog_v3.json"
private const val STATIC_CATALOG_META_FILE_NAME = "transit_catalog_v3_meta.json"
private val PRAGUE_ZONE: ZoneId = ZoneId.of("Europe/Prague")

class TransitCatalogRepository(
    private val context: Context,
    private val apiClient: GolemioApiClient = GolemioApiClient(),
) {
    @Volatile
    private var cachedCatalog: TransitCatalog? = null

    fun getCatalog(forceRefresh: Boolean = false): TransitCatalog {
        synchronized(this) {
            val diskCatalog = cachedCatalog ?: readCatalogFromDisk()?.also { cachedCatalog = it }
            if (!forceRefresh && diskCatalog != null && !isRefreshDue(diskCatalog)) {
                return diskCatalog
            }

            return try {
                fetchCatalogFromNetwork().also { catalog ->
                    cachedCatalog = catalog
                    writeCatalogToDisk(catalog)
                    Log.d(
                        CATALOG_TAG,
                        "Catalog refreshed. stations=${catalog.stations.size} lines=${catalog.lines.size} fetchedAt=${catalog.fetchedAt}",
                    )
                }
            } catch (error: Exception) {
                if (diskCatalog != null) {
                    Log.w(CATALOG_TAG, "Catalog refresh failed. Falling back to cached catalog.", error)
                    diskCatalog
                } else {
                    throw error
                }
            }
        }
    }

    fun getCachedCatalog(): TransitCatalog? {
        return cachedCatalog ?: synchronized(this) {
            cachedCatalog ?: readCatalogFromDisk()?.also { cachedCatalog = it }
        }
    }

    fun clearMemoryCache() {
        synchronized(this) {
            cachedCatalog = null
        }
    }

    fun getLastCatalogFetchedAt(): ZonedDateTime? {
        return readCatalogMetadataFetchedAt() ?: getCachedCatalog()?.fetchedAt
    }

    fun prefetchIfNeeded() {
        val cached = getCachedCatalog()
        if (cached != null && !isRefreshDue(cached)) {
            return
        }

        try {
            getCatalog(forceRefresh = cached != null)
        } catch (error: Exception) {
            Log.w(CATALOG_TAG, "Background catalog prefetch failed.", error)
        }
    }

    fun isCatalogRefreshDue(): Boolean {
        return isRefreshDue(getCachedCatalog())
    }

    private fun isRefreshDue(catalog: TransitCatalog?): Boolean {
        catalog ?: return true
        return Duration.between(catalog.fetchedAt, ZonedDateTime.now(PRAGUE_ZONE)).toHours() >= STATIC_CATALOG_TTL_HOURS
    }

    private fun fetchCatalogFromNetwork(): TransitCatalog {
        val fetchedAt = ZonedDateTime.now(PRAGUE_ZONE)
        val stops = fetchAllStops()
        val routes = fetchAllRoutes()
        return TransitCatalogBuilder.build(
            fetchedAt = fetchedAt,
            stops = stops,
            routes = routes,
        )
    }

    private fun fetchAllStops(): List<GtfsStopRecord> {
        // Golemio caps GTFS stop pages at 10k rows, so Prague needs multiple paged requests.
        val result = mutableListOf<GtfsStopRecord>()
        var offset = 0
        while (true) {
            val response = apiClient.getObject(
                path = "/v2/gtfs/stops",
                params = listOf(
                    "limit" to GTFS_PAGE_LIMIT.toString(),
                    "offset" to offset.toString(),
                ),
            )
            val features = response.body.optJSONArray("features") ?: JSONArray()
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val properties = feature.optJSONObject("properties") ?: continue
                val coordinates = feature
                    .optJSONObject("geometry")
                    ?.optJSONArray("coordinates")
                val stopId = properties.optSanitizedString("stop_id")
                if (stopId == null) {
                    continue
                }

                val aswNode = properties
                    .optJSONObject("asw_id")
                    ?.takeIf { it.has("node") && !it.isNull("node") }
                    ?.getInt("node")

                result += GtfsStopRecord(
                    stopId = stopId,
                    stopName = properties.optSanitizedString("stop_name"),
                    platformCode = properties.optSanitizedString("platform_code"),
                    parentStation = properties.optSanitizedString("parent_station"),
                    aswNode = aswNode,
                    latitude = coordinates?.takeIf { it.length() >= 2 }?.optDouble(1)?.takeUnless(Double::isNaN),
                    longitude = coordinates?.takeIf { it.length() >= 2 }?.optDouble(0)?.takeUnless(Double::isNaN),
                )
            }

            Log.d(CATALOG_TAG, "Fetched stop page offset=$offset size=${features.length()}")
            if (features.length() < GTFS_PAGE_LIMIT) {
                break
            }
            offset += GTFS_PAGE_LIMIT
        }
        return result
    }

    private fun fetchAllRoutes(): List<GtfsRouteRecord> {
        // Routes fit into one page today, but this stays paged so the logic matches stops.
        val result = mutableListOf<GtfsRouteRecord>()
        var offset = 0
        while (true) {
            val response = apiClient.getArray(
                path = "/v2/gtfs/routes",
                params = listOf(
                    "limit" to GTFS_PAGE_LIMIT.toString(),
                    "offset" to offset.toString(),
                ),
            )
            val routes = response.body
            for (index in 0 until routes.length()) {
                val route = routes.optJSONObject(index) ?: continue
                val shortName = route.optSanitizedString("route_short_name")
                if (shortName == null) {
                    continue
                }
                result += GtfsRouteRecord(
                    shortName = shortName,
                    longName = route.optSanitizedString("route_long_name"),
                    routeType = route.takeIf { it.has("route_type") && !it.isNull("route_type") }?.getInt("route_type"),
                )
            }

            Log.d(CATALOG_TAG, "Fetched route page offset=$offset size=${routes.length()}")
            if (routes.length() < GTFS_PAGE_LIMIT) {
                break
            }
            offset += GTFS_PAGE_LIMIT
        }
        return result
    }

    private fun readCatalogFromDisk(): TransitCatalog? {
        val file = catalogFile()
        if (!file.exists()) {
            return null
        }

        return runCatching {
            val json = JSONObject(file.readText())
            val fetchedAt = ZonedDateTime.parse(json.getString("fetchedAt"))
            val stations = json.optJSONArray("stations") ?: JSONArray()
            val lines = json.optJSONArray("lines") ?: JSONArray()

            TransitCatalog(
                fetchedAt = fetchedAt,
                stations = buildList {
                    for (index in 0 until stations.length()) {
                        val station = stations.optJSONObject(index) ?: continue
                        val platformsJson = station.optJSONArray("platforms") ?: JSONArray()
                        val platforms = buildList {
                            for (platformIndex in 0 until platformsJson.length()) {
                                val platform = platformsJson.optJSONObject(platformIndex) ?: continue
                                add(
                                    PlatformOption(
                                        platformKey = platform.getString("platformKey"),
                                        label = platform.getString("label"),
                                        stopIds = jsonStringList(platform.optJSONArray("stopIds")),
                                    )
                                )
                            }
                        }
                        add(
                            StationOption.create(
                                stationKey = station.getString("stationKey"),
                                stationName = station.getString("stationName"),
                                stopIds = jsonStringList(station.optJSONArray("stopIds")),
                                platforms = platforms,
                            )
                        )
                    }
                },
                lines = buildList {
                    for (index in 0 until lines.length()) {
                        val line = lines.optJSONObject(index) ?: continue
                        add(
                            LineOption.create(
                                shortName = line.getString("shortName"),
                                longName = line.optString("longName").ifBlank { null },
                                routeType = line.takeIf { it.has("routeType") && !it.isNull("routeType") }?.getInt("routeType"),
                            )
                        )
                    }
                },
            )
        }.onFailure { error ->
            Log.w(CATALOG_TAG, "Failed to read cached transit catalog.", error)
        }.getOrNull()
    }

    private fun writeCatalogToDisk(catalog: TransitCatalog) {
        val json = JSONObject().apply {
            put("fetchedAt", catalog.fetchedAt.toString())
            put(
                "stations",
                JSONArray().apply {
                    catalog.stations.forEach { station ->
                        put(
                            JSONObject().apply {
                                put("stationKey", station.stationKey)
                                put("stationName", station.stationName)
                                put("stopIds", JSONArray(station.stopIds))
                                put(
                                    "platforms",
                                    JSONArray().apply {
                                        station.platforms.forEach { platform ->
                                            put(
                                                JSONObject().apply {
                                                    put("platformKey", platform.platformKey)
                                                    put("label", platform.label)
                                                    put("stopIds", JSONArray(platform.stopIds))
                                                }
                                            )
                                        }
                                    }
                                )
                            }
                        )
                    }
                }
            )
            put(
                "lines",
                JSONArray().apply {
                    catalog.lines.forEach { line ->
                        put(
                            JSONObject().apply {
                                put("shortName", line.shortName)
                                put("longName", line.longName)
                                put("routeType", line.routeType)
                            }
                        )
                    }
                }
            )
        }

        catalogFile().writeText(json.toString())
        catalogMetaFile().writeText(
            JSONObject().apply {
                put("fetchedAt", catalog.fetchedAt.toString())
            }.toString()
        )
    }

    private fun catalogFile(): File {
        return File(context.filesDir, STATIC_CATALOG_FILE_NAME)
    }

    private fun catalogMetaFile(): File {
        return File(context.filesDir, STATIC_CATALOG_META_FILE_NAME)
    }

    private fun readCatalogMetadataFetchedAt(): ZonedDateTime? {
        val metaFile = catalogMetaFile()
        if (!metaFile.exists()) {
            return null
        }

        return runCatching {
            JSONObject(metaFile.readText()).getString("fetchedAt")
        }.mapCatching(ZonedDateTime::parse)
            .onFailure { error ->
                Log.w(CATALOG_TAG, "Failed to read transit catalog metadata.", error)
            }
            .getOrNull()
    }

    private fun jsonStringList(array: JSONArray?): List<String> {
        if (array == null) {
            return emptyList()
        }

        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).sanitizeApiValue()
                if (value != null) {
                    add(value)
                }
            }
        }
    }
}
