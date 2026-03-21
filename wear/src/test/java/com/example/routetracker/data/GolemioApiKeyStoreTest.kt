package com.example.routetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GolemioApiKeyStoreTest {
    @Test
    fun `settings override takes precedence over built in token`() {
        val resolved = resolveGolemioApiKey(
            settingsOverride = "  watch-key  ",
            builtInToken = "build-key",
        )

        requireNotNull(resolved)
        assertEquals("watch-key", resolved.token)
        assertEquals(GolemioApiKeySource.SETTINGS_OVERRIDE, resolved.source)
    }

    @Test
    fun `built in token is used when override is blank`() {
        val resolved = resolveGolemioApiKey(
            settingsOverride = "   ",
            builtInToken = "  build-key  ",
        )

        requireNotNull(resolved)
        assertEquals("build-key", resolved.token)
        assertEquals(GolemioApiKeySource.BUILT_IN, resolved.source)
    }

    @Test
    fun `missing is returned when no token source is available`() {
        val resolved = resolveGolemioApiKey(
            settingsOverride = null,
            builtInToken = "",
        )

        assertNull(resolved)
    }
}
