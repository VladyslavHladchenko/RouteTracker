package com.example.routetracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimedMemoryCacheTest {
    @Test
    fun `returns cached value while entry is fresh`() {
        val cache = TimedMemoryCache<String, String>()
        cache.put(
            key = "trip",
            value = "cached",
            nowElapsedRealtime = 1_000L,
        )

        val value = cache.getIfFresh(
            key = "trip",
            ttlMillis = 2_000L,
            nowElapsedRealtime = 2_500L,
        )

        assertEquals("cached", value)
    }

    @Test
    fun `returns null when entry is expired`() {
        val cache = TimedMemoryCache<String, String>()
        cache.put(
            key = "trip",
            value = "cached",
            nowElapsedRealtime = 1_000L,
        )

        val value = cache.getIfFresh(
            key = "trip",
            ttlMillis = 2_000L,
            nowElapsedRealtime = 3_000L,
        )

        assertNull(value)
    }

    @Test
    fun `returns null when cache is disabled`() {
        val cache = TimedMemoryCache<String, String>()
        cache.put(
            key = "trip",
            value = "cached",
            nowElapsedRealtime = 1_000L,
        )

        val value = cache.getIfFresh(
            key = "trip",
            ttlMillis = 0L,
            nowElapsedRealtime = 1_500L,
        )

        assertNull(value)
    }
}
