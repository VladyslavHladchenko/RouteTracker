package com.example.routetracker.background

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.routetracker.data.TransitCatalogRepository
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

private const val WORK_TAG = "CatalogRefreshWorker"
private const val UNIQUE_WORK_NAME = "transit_catalog_daily_refresh"
private const val REFRESH_HOUR = 1

class TransitCatalogRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val catalog = TransitCatalogRepository(applicationContext).getCatalog(forceRefresh = true)
            Log.d(WORK_TAG, "Background transit catalog refresh finished at ${catalog.fetchedAt}.")
            Result.success()
        } catch (error: Exception) {
            Log.w(WORK_TAG, "Background transit catalog refresh failed.", error)
            Result.retry()
        }
    }
}

object TransitCatalogRefreshScheduler {
    fun ensureScheduled(context: Context) {
        val request = PeriodicWorkRequestBuilder<TransitCatalogRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            // Periodic work is inexact on Wear OS; this only nudges the daily run toward
            // the next early-morning maintenance window instead of activity startup.
            .setInitialDelay(initialDelayUntilNextWindow())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun initialDelayUntilNextWindow(now: ZonedDateTime = ZonedDateTime.now()): Duration {
        val nextWindow = now
            .withHour(REFRESH_HOUR)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .let { candidate ->
                if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
            }
        return Duration.between(now, nextWindow)
    }
}
