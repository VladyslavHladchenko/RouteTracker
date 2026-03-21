package com.example.routetracker.data

import android.content.Context
import androidx.core.content.edit
import com.example.routetracker.BuildConfig

internal const val ROUTE_PREFS_NAME = "route_prefs"
private const val PREF_GOLEMIO_API_KEY_OVERRIDE = "golemio_api_key_override"

internal enum class GolemioApiKeySource {
    SETTINGS_OVERRIDE,
    BUILT_IN,
    MISSING,
}

internal data class ResolvedGolemioApiKey(
    val token: String,
    val source: GolemioApiKeySource,
)

internal fun resolveGolemioApiKey(
    settingsOverride: String?,
    builtInToken: String?,
): ResolvedGolemioApiKey? {
    val normalizedOverride = settingsOverride.orEmpty().trim()
    if (normalizedOverride.isNotEmpty()) {
        return ResolvedGolemioApiKey(
            token = normalizedOverride,
            source = GolemioApiKeySource.SETTINGS_OVERRIDE,
        )
    }

    val normalizedBuiltInToken = builtInToken.orEmpty().trim()
    if (normalizedBuiltInToken.isNotEmpty()) {
        return ResolvedGolemioApiKey(
            token = normalizedBuiltInToken,
            source = GolemioApiKeySource.BUILT_IN,
        )
    }

    return null
}

internal class GolemioApiKeyStore(context: Context) {
    private val prefs = context.getSharedPreferences(ROUTE_PREFS_NAME, Context.MODE_PRIVATE)

    fun getOverride(): String {
        return prefs.getString(PREF_GOLEMIO_API_KEY_OVERRIDE, null).orEmpty().trim()
    }

    fun setOverride(value: String) {
        val normalizedValue = value.trim()
        prefs.edit {
            if (normalizedValue.isEmpty()) {
                remove(PREF_GOLEMIO_API_KEY_OVERRIDE)
            } else {
                putString(PREF_GOLEMIO_API_KEY_OVERRIDE, normalizedValue)
            }
        }
    }

    fun getResolved(): ResolvedGolemioApiKey? {
        return resolveGolemioApiKey(
            settingsOverride = getOverride(),
            builtInToken = BuildConfig.GOLEMIO_API_KEY,
        )
    }

    fun getSource(): GolemioApiKeySource {
        return getResolved()?.source ?: GolemioApiKeySource.MISSING
    }

    fun getSourceLabel(): String {
        return when (getSource()) {
            GolemioApiKeySource.SETTINGS_OVERRIDE -> "Watch override"
            GolemioApiKeySource.BUILT_IN -> "Built-in key"
            GolemioApiKeySource.MISSING -> "Missing"
        }
    }

    fun requireApiToken(): String {
        return getResolved()?.token ?: error(
            "Missing Golemio API key. Set it in watch settings or provide golemioApiKey/GOLEMIO_API_KEY at build time."
        )
    }
}

internal fun createGolemioApiClient(context: Context): GolemioApiClient {
    val apiKeyStore = GolemioApiKeyStore(context)
    return GolemioApiClient {
        apiKeyStore.requireApiToken()
    }
}
