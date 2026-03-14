package com.example.routetracker.data

private data class TimedCacheEntry<V>(
    val value: V,
    val storedAtElapsedRealtime: Long,
)

class TimedMemoryCache<K, V> {
    private val entries = mutableMapOf<K, TimedCacheEntry<V>>()

    fun getIfFresh(
        key: K,
        ttlMillis: Long,
        nowElapsedRealtime: Long,
    ): V? {
        if (ttlMillis <= 0L) {
            return null
        }

        synchronized(entries) {
            val entry = entries[key] ?: return null
            return if (nowElapsedRealtime - entry.storedAtElapsedRealtime < ttlMillis) {
                entry.value
            } else {
                entries.remove(key)
                null
            }
        }
    }

    fun put(
        key: K,
        value: V,
        nowElapsedRealtime: Long,
    ) {
        synchronized(entries) {
            entries[key] = TimedCacheEntry(
                value = value,
                storedAtElapsedRealtime = nowElapsedRealtime,
            )
        }
    }

    fun clear() {
        synchronized(entries) {
            entries.clear()
        }
    }
}
