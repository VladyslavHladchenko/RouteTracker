package com.example.routetracker.data

data class StopSelection(
    val stationKey: String,
    val stationName: String,
    val platformKey: String? = null,
    val platformLabel: String? = null,
    val stopIds: List<String>,
) {
    val displayLabel: String
        get() = buildString {
            append(stationName)
            platformLabel?.takeIf { it.isNotBlank() }?.let { label ->
                append(" (")
                append(label)
                append(')')
            }
        }

    val compactPlatformLabel: String?
        get() = platformLabel
            ?.takeIf { it.isNotBlank() }
            ?.let(::compactPlatformLabel)
}

data class LineSelection(
    val shortName: String,
    val longName: String? = null,
) {
    val displayLabel: String
        get() = if (longName.isNullOrBlank()) {
            "Line $shortName"
        } else {
            "Line $shortName"
        }
}

data class RouteSelection(
    val origin: StopSelection,
    val destination: StopSelection,
    val line: LineSelection? = null,
) {
    val stableKey: String
        get() = listOf(
            origin.stationKey,
            origin.platformKey ?: "*",
            destination.stationKey,
            destination.platformKey ?: "*",
            line?.shortName ?: "*",
        ).joinToString("|")

    val headerLineLabel: String
        get() = line?.displayLabel ?: "Any direct line"

    val routeSummaryLabel: String
        get() = "${origin.stationName} to ${destination.stationName}"

    val routeSummaryWithPlatforms: String
        get() = "${origin.displayLabel} -> ${destination.displayLabel}"

    val favoriteSummaryLabel: String
        get() = buildList {
            origin.compactPlatformLabel?.let { add("From $it") }
            destination.compactPlatformLabel?.let { add("To $it") }
            add(line?.displayLabel ?: "Any line")
        }.joinToString("  |  ")

    fun usesFixedLine(): Boolean = line != null
}

private fun compactPlatformLabel(label: String): String {
    val compact = label.removePrefix("Platform ").trim()
    return compact.ifBlank { label }
}
