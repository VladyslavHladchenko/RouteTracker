package com.example.routetracker.data

import java.time.ZonedDateTime

data class PlatformOption(
    val platformKey: String,
    val label: String,
    val stopIds: List<String>,
)

data class StationOption(
    val stationKey: String,
    val stationName: String,
    val stopIds: List<String>,
    val platforms: List<PlatformOption>,
    private val normalizedSearchText: String,
) {
    val searchSubtitle: String
        get() {
            val platformCodes = platforms
                .mapNotNull { platform -> platform.platformKey.sanitizeApiValue() }
                .distinct()
            return when {
                platformCodes.isEmpty() -> "Any platform"
                platformCodes.size <= 3 -> "Platforms ${platformCodes.joinToString(", ")}"
                else -> "Platforms ${platformCodes.take(3).joinToString(", ")} +${platformCodes.size - 3}"
            }
        }

    val anyPlatformSubtitle: String
        get() = if (platforms.isEmpty()) {
            formatBoardingStopCount(stopIds.size)
        } else {
            searchSubtitle
        }

    fun matches(query: String): Int? {
        return SearchRanking.score(query, normalizedSearchText)
    }

    fun platformLabelForStop(stopId: String): String? {
        return platforms.firstOrNull { platform -> stopId in platform.stopIds }?.label
    }

    fun resolveSelection(platformKey: String?): StopSelection {
        val selectedPlatform = platforms.firstOrNull { it.platformKey == platformKey }
        return StopSelection(
            stationKey = stationKey,
            stationName = stationName,
            platformKey = selectedPlatform?.platformKey,
            platformLabel = selectedPlatform?.label,
            stopIds = selectedPlatform?.stopIds ?: anyPlatformStopIds(),
        )
    }

    private fun anyPlatformStopIds(): List<String> {
        if (platforms.isEmpty()) {
            return stopIds
        }

        return platforms
            .asSequence()
            .flatMap { platform -> platform.stopIds.asSequence() }
            .distinct()
            .sorted()
            .toList()
    }

    companion object {
        fun create(
            stationKey: String,
            stationName: String,
            stopIds: List<String>,
            platforms: List<PlatformOption>,
        ): StationOption {
            val searchText = buildString {
                append(stationName)
                if (platforms.isNotEmpty()) {
                    append(' ')
                    append(platforms.joinToString(" ") { it.label })
                }
            }
            return StationOption(
                stationKey = stationKey,
                stationName = stationName,
                stopIds = stopIds.sorted(),
                platforms = platforms.sortedBy { it.label },
                normalizedSearchText = SearchNormalizer.normalize(searchText),
            )
        }
    }
}

internal fun formatBoardingStopCount(count: Int): String {
    return when (count) {
        1 -> "1 boarding stop"
        else -> "$count boarding stops"
    }
}

data class LineOption(
    val shortName: String,
    val longName: String? = null,
    val routeType: Int? = null,
    private val normalizedSearchText: String,
) {
    val displayLabel: String
        get() = if (longName.isNullOrBlank()) {
            "Line $shortName"
        } else {
            "Line $shortName"
        }

    fun matches(query: String): Int? {
        return SearchRanking.score(query, normalizedSearchText)
    }

    fun toSelection(): LineSelection {
        return LineSelection(
            shortName = shortName,
            longName = longName,
        )
    }

    companion object {
        fun create(
            shortName: String,
            longName: String?,
            routeType: Int?,
        ): LineOption {
            val searchText = buildString {
                append(shortName)
                if (!longName.isNullOrBlank()) {
                    append(' ')
                    append(longName)
                }
            }
            return LineOption(
                shortName = shortName,
                longName = longName,
                routeType = routeType,
                normalizedSearchText = SearchNormalizer.normalize(searchText),
            )
        }
    }
}

data class TransitCatalog(
    val fetchedAt: ZonedDateTime,
    val stations: List<StationOption>,
    val lines: List<LineOption>,
) {
    fun searchStations(
        query: String,
        limit: Int = 12,
    ): List<StationOption> {
        val normalizedQuery = SearchNormalizer.normalize(query)
        if (normalizedQuery.isBlank()) {
            return emptyList()
        }

        return stations.asSequence()
            .mapNotNull { station ->
                station.matches(normalizedQuery)?.let { score -> score to station }
            }
            .sortedWith(
                compareBy<Pair<Int, StationOption>>(
                    { it.first },
                    { it.second.stationName.length },
                    { -it.second.platforms.size },
                    { -it.second.stopIds.size },
                    { SearchNormalizer.normalize(it.second.stationName) },
                )
            )
            .map { it.second }
            .take(limit)
            .toList()
    }

    fun searchLines(
        query: String,
        limit: Int = 12,
    ): List<LineOption> {
        val normalizedQuery = SearchNormalizer.normalize(query)
        val ranked = lines.asSequence()
            .mapNotNull { line ->
                line.matches(normalizedQuery).let { score ->
                    if (normalizedQuery.isBlank()) {
                        0 to line
                    } else {
                        score?.let { it to line }
                    }
                }
            }
            .sortedWith(
                compareBy<Pair<Int, LineOption>>(
                    { it.first },
                    { it.second.shortName.toIntOrNull() ?: Int.MAX_VALUE },
                    { it.second.shortName },
                )
            )
            .map { it.second }

        return ranked.take(limit).toList()
    }

    fun stationByKey(stationKey: String): StationOption? {
        return stations.firstOrNull { it.stationKey == stationKey }
    }

}
