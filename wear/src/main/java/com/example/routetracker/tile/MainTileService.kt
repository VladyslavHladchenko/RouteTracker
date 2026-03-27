package com.example.routetracker.tile

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.RequestBuilders.ResourcesRequest
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tooling.preview.Preview
import androidx.wear.tiles.tooling.preview.TilePreviewData
import androidx.wear.tooling.preview.devices.WearDevices
import com.example.routetracker.data.DepartureSnapshot
import com.example.routetracker.data.RouteRepository
import com.example.routetracker.presentation.MainActivity
import com.example.routetracker.presentation.preview.WearPreviewFixtures
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private const val RESOURCES_VERSION = "0"
private const val TAG = "RouteTrackerTile"

class MainTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.submit(
            Callable {
                val routeRepository = RouteRepository(this)
                val snapshot = routeRepository.getDepartureSnapshot()
                val showSecondsEnabled = routeRepository.getShowSecondsEnabled()
                Log.d(TAG, "Tile request received. ${snapshot.debugSummary()}")
                tile(requestParams, this, snapshot, showSecondsEnabled)
            },
            tileExecutor,
        )
    }

    override fun onTileResourcesRequest(requestParams: ResourcesRequest): ListenableFuture<Resources> =
        Futures.immediateFuture(resources(requestParams))

    companion object {
        private val tileExecutor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }
}

private fun resources(requestParams: ResourcesRequest): Resources {
    return Resources.Builder()
        .setVersion(RESOURCES_VERSION)
        .build()
}

internal fun tileResourcesForPreview(requestParams: ResourcesRequest): Resources = resources(requestParams)

private fun tile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    snapshot: DepartureSnapshot,
    showSecondsEnabled: Boolean,
): TileBuilders.Tile {
    Log.d(TAG, "Rendering tile lines=${snapshot.tileLines(showSecondsEnabled)}")

    return TileBuilders.Tile.Builder()
        .setResourcesVersion(RESOURCES_VERSION)
        .setFreshnessIntervalMillis(30_000L)
        .setTileTimeline(
            TimelineBuilders.Timeline.fromLayoutElement(
                materialScope(context, requestParams.deviceConfiguration) {
                    primaryLayout(
                        mainSlot = {
                            stackedDepartureLines(snapshot, showSecondsEnabled)
                        },
                        onClick = clickable(
                            ActionBuilders.launchAction(
                                ComponentName(context, MainActivity::class.java)
                            )
                        )
                    )
                }
            )
        )
        .build()
}

internal fun buildPreviewTile(
    requestParams: RequestBuilders.TileRequest,
    context: Context,
    snapshot: DepartureSnapshot,
    showSecondsEnabled: Boolean,
): TileBuilders.Tile = tile(
    requestParams = requestParams,
    context = context,
    snapshot = snapshot,
    showSecondsEnabled = showSecondsEnabled,
)

private fun MaterialScope.stackedDepartureLines(
    snapshot: DepartureSnapshot,
    showSecondsEnabled: Boolean,
): LayoutElementBuilders.LayoutElement {
    val title = snapshot.selection.destination.stationName
    val subtitle = snapshot.selection.line?.displayLabel ?: "Any direct line"
    return LayoutElementBuilders.Column.Builder()
        .apply {
            addContent(
                text(
                    title.layoutString,
                    typography = Typography.BODY_SMALL,
                )
            )
            addContent(
                text(
                    subtitle.layoutString,
                    typography = Typography.BODY_SMALL,
                )
            )
            snapshot.tileLines(showSecondsEnabled).forEach { line ->
                addContent(
                    text(
                        line.layoutString,
                        typography = Typography.BODY_SMALL,
                    )
                )
            }
        }
        .build()
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tilePreview(context: Context) = TilePreviewData(::resources) {
    buildPreviewTile(
        requestParams = it,
        context = context,
        snapshot = WearPreviewFixtures.populatedTileSnapshot(),
        showSecondsEnabled = false,
    )
}

@Preview(device = WearDevices.SMALL_ROUND)
@Preview(device = WearDevices.LARGE_ROUND)
fun tileFallbackPreview(context: Context) = TilePreviewData(::resources) {
    buildPreviewTile(
        requestParams = it,
        context = context,
        snapshot = WearPreviewFixtures.fallbackTileSnapshot(),
        showSecondsEnabled = false,
    )
}
