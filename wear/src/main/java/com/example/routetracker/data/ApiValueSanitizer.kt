package com.example.routetracker.data

import org.json.JSONObject

/**
 * Golemio sometimes serializes missing values as JSON null and some callers read them back
 * through `optString`, which turns them into the literal text `"null"`.
 *
 * Centralizing the cleanup keeps parsing code simple and prevents crashes when date parsers
 * receive `"null"` instead of an actual ISO timestamp.
 */
internal fun String?.sanitizeApiValue(): String? {
    val trimmed = this?.trim().orEmpty()
    if (trimmed.isBlank()) {
        return null
    }
    return trimmed.takeUnless { it.equals("null", ignoreCase = true) }
}

internal fun JSONObject.optSanitizedString(name: String): String? {
    if (!has(name) || isNull(name)) {
        return null
    }
    return optString(name).sanitizeApiValue()
}
