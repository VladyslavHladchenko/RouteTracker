package com.example.routetracker

import android.app.Application
import com.example.routetracker.background.TransitCatalogRefreshScheduler

class RouteTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        TransitCatalogRefreshScheduler.ensureScheduled(this)
    }
}
