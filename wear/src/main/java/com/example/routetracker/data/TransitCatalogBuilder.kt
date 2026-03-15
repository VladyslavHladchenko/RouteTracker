package com.example.routetracker.data

import java.time.ZonedDateTime

data class GtfsStopRecord(
    val stopId: String,
    val stopName: String?,
    val platformCode: String?,
    val parentStation: String?,
    val aswNode: Int?,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class GtfsRouteRecord(
    val shortName: String,
    val longName: String?,
    val routeType: Int?,
)

private data class PreliminaryStation(
    val stationKey: String,
    val stationName: String,
    val normalizedStationName: String,
    val stopIds: List<String>,
    val platforms: List<PlatformOption>,
    val latitude: Double?,
    val longitude: Double?,
)

private data class StationCluster(
    val stations: MutableList<PreliminaryStation>,
    var latitude: Double?,
    var longitude: Double?,
)

object TransitCatalogBuilder {
    private const val NEARBY_SAME_NAME_MERGE_DISTANCE_METERS = 450.0

    /**
     * Groups raw GTFS stops into searchable stations with optional platform filters.
     *
     * A watch search result should represent the station name users know, while platform
     * selection stays a second step. We therefore group by parent station when possible,
     * fall back to ASW node for surface stops, and only then keep raw stop groups separate
     * until the later nearby-same-name merge pass combines the user-visible station result.
     */
    fun build(
        fetchedAt: ZonedDateTime,
        stops: List<GtfsStopRecord>,
        routes: List<GtfsRouteRecord>,
    ): TransitCatalog {
        val stopsById = stops.associateBy { it.stopId }
        val parentStopIds = stops
            .mapNotNull { it.parentStation }
            .toSet()
        val hierarchyDepthByStopId = stops.associate { stop ->
            stop.stopId to hierarchyDepth(
                stop = stop,
                stopsById = stopsById,
            )
        }
        val hierarchyAnchorByStopId = stops.associate { stop ->
            stop.stopId to hierarchyAnchorId(
                stop = stop,
                stopsById = stopsById,
                parentStopIds = parentStopIds,
            )
        }
        val preferredAnchorByNode = stops
            .asSequence()
            .mapNotNull { stop ->
                val aswNode = stop.aswNode ?: return@mapNotNull null
                val anchorId = hierarchyAnchorByStopId[stop.stopId] ?: return@mapNotNull null
                aswNode to anchorId
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, anchorIds) ->
                anchorIds
                    .groupingBy { it }
                    .eachCount()
                    .maxWithOrNull(compareBy<Map.Entry<String, Int>>({ it.value }, { it.key.length }))
                    ?.key
            }
        val preferredNameByNode = stops
            .asSequence()
            .filter { it.aswNode != null }
            .groupBy { it.aswNode!! }
            .mapValues { (_, groupedStops) ->
                bestStationName(
                    stops = groupedStops,
                    hierarchyDepthByStopId = hierarchyDepthByStopId,
                )
            }

        val preliminaryStations = stops
            .groupBy { stop ->
                stationKey(
                    stop = stop,
                    hierarchyAnchorByStopId = hierarchyAnchorByStopId,
                    preferredAnchorByNode = preferredAnchorByNode,
                )
            }
            .map { (stationKey, groupedStops) ->
                val stationName = bestStationName(
                    stops = groupedStops,
                    hierarchyDepthByStopId = hierarchyDepthByStopId,
                )
                    ?: groupedStops
                        .firstNotNullOfOrNull { stop -> stop.aswNode?.let(preferredNameByNode::get) }
                    ?: groupedStops.first().stopId

                val stationStopIds = groupedStops
                    .map { it.stopId }
                    .distinct()
                    .sorted()

                val platforms = groupedStops
                    .filter { !it.platformCode.isNullOrBlank() }
                    .groupBy { it.platformCode!!.trim() }
                    .map { (platformCode, platformStops) ->
                        PlatformOption(
                            platformKey = platformCode,
                            label = "Platform $platformCode",
                            stopIds = platformStops.map { it.stopId }.distinct().sorted(),
                        )
                    }

                PreliminaryStation(
                    stationKey = stationKey,
                    stationName = stationName,
                    normalizedStationName = SearchNormalizer.normalize(stationName),
                    stopIds = stationStopIds,
                    platforms = platforms,
                    latitude = groupedStops.mapNotNull { it.latitude }.averageOrNull(),
                    longitude = groupedStops.mapNotNull { it.longitude }.averageOrNull(),
                )
            }

        val stations = mergeNearbyStationsWithSameName(preliminaryStations)
            .sortedBy { SearchNormalizer.normalize(it.stationName) }

        val lines = routes
            .filter { it.shortName.isNotBlank() }
            .groupBy { it.shortName.trim() }
            .map { (shortName, groupedRoutes) ->
                val longName = groupedRoutes
                    .mapNotNull { it.longName?.trim()?.takeIf(String::isNotBlank) }
                    .distinct()
                    .minByOrNull { it.length }
                val routeType = groupedRoutes.firstNotNullOfOrNull { it.routeType }
                LineOption.create(
                    shortName = shortName,
                    longName = longName,
                    routeType = routeType,
                )
            }
            .sortedWith(compareBy({ it.shortName.toIntOrNull() ?: Int.MAX_VALUE }, { it.shortName }))

        return TransitCatalog(
            fetchedAt = fetchedAt,
            stations = stations,
            lines = lines,
        )
    }

    private fun bestStationName(
        stops: List<GtfsStopRecord>,
        hierarchyDepthByStopId: Map<String, Int>,
    ): String? {
        val candidates = stops
            .asSequence()
            .mapNotNull { stop ->
                val name = stop.stopName?.trim()?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                val depth = hierarchyDepthByStopId[stop.stopId] ?: Int.MAX_VALUE
                depth to name
            }
            .toList()

        val shallowestDepth = candidates.minOfOrNull { it.first } ?: return null
        return candidates
            .asSequence()
            .filter { it.first == shallowestDepth }
            .map { it.second }
            .groupingBy { it }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>>({ it.value }, { it.key.length }))
            ?.key
    }

    private fun stationKey(
        stop: GtfsStopRecord,
        hierarchyAnchorByStopId: Map<String, String?>,
        preferredAnchorByNode: Map<Int, String?>,
    ): String {
        stop.aswNode
            ?.let(preferredAnchorByNode::get)
            ?.let { anchorId ->
                return "parent:$anchorId"
            }

        hierarchyAnchorByStopId[stop.stopId]?.let { anchorId ->
            return "parent:$anchorId"
        }

        stop.aswNode?.let { node ->
            return "asw:$node"
        }

        return "stop:${stop.stopId}"
    }

    /**
     * Resolves the top-most GTFS station hierarchy anchor.
     *
     * The stop feed uses nested parent_station chains:
     * station -> platform -> platform track / exit.
     * We collapse the full chain so a watch search result represents one real station,
     * not intermediary nodes like "Kolej 1".
     */
    private fun hierarchyAnchorId(
        stop: GtfsStopRecord,
        stopsById: Map<String, GtfsStopRecord>,
        parentStopIds: Set<String>,
    ): String? {
        val firstParent = stop.parentStation?.trim().orEmpty()
        if (firstParent.isNotEmpty()) {
            val visited = mutableSetOf(stop.stopId)
            var currentId = stop.stopId
            var parentId: String? = firstParent
            while (!parentId.isNullOrBlank() && visited.add(parentId)) {
                currentId = parentId
                parentId = stopsById[parentId]?.parentStation?.trim()?.takeIf(String::isNotEmpty)
            }
            return currentId
        }

        return stop.stopId.takeIf { stopId -> parentStopIds.contains(stopId) }
    }

    private fun hierarchyDepth(
        stop: GtfsStopRecord,
        stopsById: Map<String, GtfsStopRecord>,
    ): Int {
        val visited = mutableSetOf(stop.stopId)
        var depth = 0
        var parentId = stop.parentStation?.trim()?.takeIf(String::isNotEmpty)
        while (!parentId.isNullOrBlank() && visited.add(parentId)) {
            depth += 1
            parentId = stopsById[parentId]?.parentStation?.trim()?.takeIf(String::isNotEmpty)
        }
        return depth
    }

    /**
     * Some interchanges expose multiple GTFS station groups with the same public name:
     * for example metro and surface platforms can be separate hierarchies. On the watch
     * it is much friendlier to show one "Želivského" result containing all nearby
     * platforms, while still keeping unrelated same-name stops in different towns separate.
     */
    private fun mergeNearbyStationsWithSameName(stations: List<PreliminaryStation>): List<StationOption> {
        return stations
            .groupBy { it.normalizedStationName }
            .values
            .flatMap(::clusterNearbyStations)
            .map(::toStationOption)
    }

    private fun clusterNearbyStations(stations: List<PreliminaryStation>): List<StationCluster> {
        val clusters = mutableListOf<StationCluster>()
        stations.forEach { station ->
            val matchingCluster = clusters.firstOrNull { cluster ->
                areStationsMergeable(cluster, station)
            }
            if (matchingCluster == null) {
                clusters += StationCluster(
                    stations = mutableListOf(station),
                    latitude = station.latitude,
                    longitude = station.longitude,
                )
            } else {
                matchingCluster.stations += station
                matchingCluster.latitude = listOfNotNull(matchingCluster.latitude, station.latitude).averageOrNull()
                matchingCluster.longitude = listOfNotNull(matchingCluster.longitude, station.longitude).averageOrNull()
            }
        }
        return clusters
    }

    private fun areStationsMergeable(
        cluster: StationCluster,
        station: PreliminaryStation,
    ): Boolean {
        val clusterLat = cluster.latitude
        val clusterLon = cluster.longitude
        val stationLat = station.latitude
        val stationLon = station.longitude
        if (clusterLat == null || clusterLon == null || stationLat == null || stationLon == null) {
            return false
        }
        return distanceMeters(
            lat1 = clusterLat,
            lon1 = clusterLon,
            lat2 = stationLat,
            lon2 = stationLon,
        ) <= NEARBY_SAME_NAME_MERGE_DISTANCE_METERS
    }

    private fun toStationOption(cluster: StationCluster): StationOption {
        val mergedStations = cluster.stations
        val mergedStationName = mergedStations
            .groupingBy { it.stationName }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<String, Int>>({ it.value }, { it.key.length }))
            ?.key
            ?: mergedStations.first().stationName

        val mergedPlatforms = mergedStations
            .flatMap { it.platforms }
            .groupBy { platform -> platform.platformKey }
            .map { (platformKey, groupedPlatforms) ->
                val label = groupedPlatforms
                    .map { it.label }
                    .distinct()
                    .sorted()
                    .firstOrNull()
                    ?: "Platform $platformKey"
                PlatformOption(
                    platformKey = platformKey,
                    label = label,
                    stopIds = groupedPlatforms
                        .flatMap { it.stopIds }
                        .distinct()
                        .sorted(),
                )
            }
            .sortedBy { it.label }

        val mergedStopIds = mergedStations
            .flatMap { it.stopIds }
            .distinct()
            .sorted()

        val mergedStationKey = if (mergedStations.size == 1) {
            mergedStations.first().stationKey
        } else {
            "merged:${mergedStations.map { it.stationKey }.sorted().joinToString("+")}"
        }

        return StationOption.create(
            stationKey = mergedStationKey,
            stationName = mergedStationName,
            stopIds = mergedStopIds,
            platforms = mergedPlatforms,
        )
    }

    private fun Iterable<Double>.averageOrNull(): Double? {
        val values = toList()
        if (values.isEmpty()) {
            return null
        }
        return values.average()
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val earthRadiusMeters = 6_371_000.0
        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(latDistance / 2).let { sinLat ->
            sinLat * sinLat
        } + kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(lonDistance / 2).let { sinLon ->
                sinLon * sinLon
            }
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earthRadiusMeters * c
    }
}
