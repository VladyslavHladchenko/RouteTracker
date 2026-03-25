package com.example.routetracker.presentation

import com.example.routetracker.data.DepartureRefreshFailureKind
import com.example.routetracker.data.RouteRepository
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessHaloUiModelTest {
    private val fixedNow: ZonedDateTime = ZonedDateTime.of(
        2026,
        3,
        16,
        12,
        0,
        0,
        0,
        ZoneId.of("Europe/Prague"),
    )

    @Test
    fun `refreshing overrides paused halo state`() {
        val snapshot = RouteRepository.previewSnapshot(now = fixedNow).copy(isStale = true)

        val uiModel = buildFreshnessHaloUiModel(
            snapshot = snapshot,
            autoUpdatesEnabled = false,
            isRefreshing = true,
            currentSystemTime = fixedNow.plusSeconds(5),
        )

        assertEquals(FreshnessHaloMode.REFRESHING, uiModel.mode)
    }

    @Test
    fun `paused remains distinct from cached`() {
        val snapshot = RouteRepository.previewSnapshot(now = fixedNow).copy(isStale = true)

        val pausedMode = freshnessBaseMode(
            snapshot = snapshot,
            autoUpdatesEnabled = false,
        )
        val cachedMode = freshnessBaseMode(
            snapshot = snapshot,
            autoUpdatesEnabled = true,
        )

        assertEquals(FreshnessHaloMode.PAUSED, pausedMode)
        assertEquals(FreshnessHaloMode.CACHED, cachedMode)
    }

    @Test
    fun `rate limited and generic failures map to different halo states`() {
        val rateLimitedSnapshot = RouteRepository.previewSnapshot(now = fixedNow).copy(
            isStale = true,
            refreshFailureKind = DepartureRefreshFailureKind.RATE_LIMITED,
        )
        val errorSnapshot = RouteRepository.previewSnapshot(now = fixedNow).copy(
            isStale = true,
            refreshFailureKind = DepartureRefreshFailureKind.OTHER,
        )

        assertEquals(
            FreshnessHaloMode.RATE_LIMITED,
            freshnessBaseMode(snapshot = rateLimitedSnapshot, autoUpdatesEnabled = true),
        )
        assertEquals(
            FreshnessHaloMode.ERROR,
            freshnessBaseMode(snapshot = errorSnapshot, autoUpdatesEnabled = true),
        )
    }

    @Test
    fun `live progress clamps at fresh midcycle and overdue`() {
        val snapshot = RouteRepository.previewSnapshot(now = fixedNow)

        val freshProgress = computeLiveFreshnessProgress(
            snapshot = snapshot,
            currentSystemTime = fixedNow.minusSeconds(5),
        )
        val midProgress = computeLiveFreshnessProgress(
            snapshot = snapshot,
            currentSystemTime = fixedNow.plusSeconds(15),
        )
        val overdueProgress = computeLiveFreshnessProgress(
            snapshot = snapshot,
            currentSystemTime = fixedNow.plusSeconds(45),
        )

        assertEquals(1f, freshProgress, 0.0001f)
        assertEquals(0.5f, midProgress, 0.0001f)
        assertTrue(overdueProgress == 0f)
    }
}
