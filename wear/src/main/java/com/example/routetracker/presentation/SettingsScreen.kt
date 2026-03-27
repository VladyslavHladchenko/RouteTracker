package com.example.routetracker.presentation

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import com.example.routetracker.presentation.preview.ApiKeyPreviewCase
import com.example.routetracker.presentation.preview.ApiKeyPreviewCaseProvider
import com.example.routetracker.presentation.preview.RouteTrackerPreviewScaffold
import com.example.routetracker.presentation.preview.WearPreviewFixtures
import com.example.routetracker.presentation.preview.WearSurfacePreview

@Composable
internal fun SettingsScreen(
    showSecondsEnabled: Boolean,
    detailsDialogAutoRefreshEnabled: Boolean,
    verifiedMatchCount: Int,
    transitCatalogLastRefreshLabel: String,
    liveSnapshotCacheLabel: String,
    gtfsTripDetailCacheLabel: String,
    vehiclePositionCacheLabel: String,
    apiKeySourceLabel: String,
    onToggleShowSeconds: () -> Unit,
    onToggleDetailsDialogAutoRefresh: () -> Unit,
    onDecreaseVerifiedMatchCount: () -> Unit,
    onIncreaseVerifiedMatchCount: () -> Unit,
    onRefreshTransitCatalog: () -> Unit,
    onCycleLiveSnapshotCache: () -> Unit,
    onCycleGtfsTripDetailCache: () -> Unit,
    onCycleVehiclePositionCache: () -> Unit,
    onEditApiKey: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()

    RouteTrackerListScreen(state = listState) { transformationSpec ->
        item(key = "settings_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Settings")
            }
        }
        item(key = "settings_show_seconds") {
            SwitchButton(
                checked = showSecondsEnabled,
                onCheckedChange = { onToggleShowSeconds() },
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                label = { Text("Show seconds") },
                secondaryLabel = { Text("Use second-by-second countdowns") },
            )
        }
        item(key = "settings_auto_refresh") {
            SwitchButton(
                checked = detailsDialogAutoRefreshEnabled,
                onCheckedChange = { onToggleDetailsDialogAutoRefresh() },
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                label = { Text("Details auto-refresh") },
                secondaryLabel = {
                    Text(
                        if (detailsDialogAutoRefreshEnabled) {
                            "Refresh open trip details every 10 seconds"
                        } else {
                            "Only refresh details when requested"
                        },
                    )
                },
            )
        }
        item(key = "settings_live_query_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Live query")
            }
        }
        item(key = "settings_api_key") {
            Card(
                onClick = onEditApiKey,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_API_KEY_BUTTON)
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            ) {
                Text(
                    text = "API key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = apiKeySourceLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        item(key = "settings_catalog_refresh") {
            Button(
                onClick = onRefreshTransitCatalog,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Refresh stop catalog")
            }
        }
        item(key = "settings_catalog_status") {
            DetailValueRow(
                label = "Catalog synced",
                value = transitCatalogLastRefreshLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "settings_verified_matches") {
            VerifiedMatchCard(
                verifiedMatchCount = verifiedMatchCount,
                onDecreaseVerifiedMatchCount = onDecreaseVerifiedMatchCount,
                onIncreaseVerifiedMatchCount = onIncreaseVerifiedMatchCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "settings_cache_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Cache")
            }
        }
        item(key = "settings_live_snapshot_cache") {
            Button(
                onClick = onCycleLiveSnapshotCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Live snapshot: $liveSnapshotCacheLabel")
            }
        }
        item(key = "settings_trip_detail_cache") {
            Button(
                onClick = onCycleGtfsTripDetailCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Trip detail: $gtfsTripDetailCacheLabel")
            }
        }
        item(key = "settings_vehicle_cache") {
            Button(
                onClick = onCycleVehiclePositionCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Vehicle live: $vehiclePositionCacheLabel")
            }
        }
    }
}

@Composable
internal fun ApiKeySettingsScreen(
    value: String,
    sourceLabel: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onUseBuiltIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()

    RouteTrackerListScreen(
        state = listState,
        timeText = {},
    ) { transformationSpec ->
        item(key = "api_key_header") {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Golemio API key")
            }
        }
        item(key = "api_key_notice") {
            EmptyStateCard(
                message = "Advanced setting. Watch override replaces the built-in key for this device.",
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "api_key_source") {
            DetailValueRow(
                label = "Current source",
                value = sourceLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "api_key_input") {
            ApiKeyInputCard(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
            )
        }
        item(key = "api_key_save") {
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_API_KEY_SAVE_BUTTON)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Save key")
            }
        }
        item(key = "api_key_clear") {
            Button(
                onClick = onUseBuiltIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.SETTINGS_API_KEY_CLEAR_BUTTON)
                    .transformedHeight(this, transformationSpec),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text("Use built-in key")
            }
        }
    }
}

@Composable
private fun ApiKeyInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalInspectionMode.current) {
        PanelSurface(modifier = modifier) {
            Text(
                text = "Paste key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value.ifBlank { "Preview key field" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        return
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()

    PanelSurface(modifier = modifier) {
        Text(
            text = "Paste key",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            factory = { context ->
                EditText(context).apply {
                    setSingleLine(true)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    background = null
                    setPadding(0, 0, 0, 0)
                    setTextColor(textColor)
                    setHintTextColor(hintColor)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                    hint = "Paste API key"
                    inputType =
                        InputType.TYPE_CLASS_TEXT or
                            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    imeOptions = EditorInfo.IME_ACTION_DONE
                    addTextChangedListener(
                        object : TextWatcher {
                            override fun beforeTextChanged(
                                s: CharSequence?,
                                start: Int,
                                count: Int,
                                after: Int,
                            ) = Unit

                            override fun onTextChanged(
                                s: CharSequence?,
                                start: Int,
                                before: Int,
                                count: Int,
                            ) = Unit

                            override fun afterTextChanged(s: Editable?) {
                                onValueChange(s?.toString().orEmpty())
                            }
                        },
                    )
                }
            },
            update = { editText ->
                if (editText.text.toString() != value) {
                    editText.setText(value)
                    editText.setSelection(editText.text.length)
                }
            },
        )
    }
}

@Composable
private fun VerifiedMatchCard(
    verifiedMatchCount: Int,
    onDecreaseVerifiedMatchCount: () -> Unit,
    onIncreaseVerifiedMatchCount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PanelSurface(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = "Verified matches",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = verifiedMatchCount.toString(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = "Tune exact direction matching when live data is ambiguous.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onDecreaseVerifiedMatchCount,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.filledTonalButtonColors(),
            ) {
                Text("-1")
            }
            Button(
                onClick = onIncreaseVerifiedMatchCount,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Text("+1")
            }
        }
    }
}

@WearSurfacePreview
@Composable
private fun SettingsScreenPreview() {
    val previewData = WearPreviewFixtures.settingsData()
    RouteTrackerPreviewScaffold {
        SettingsScreen(
            showSecondsEnabled = previewData.showSecondsEnabled,
            detailsDialogAutoRefreshEnabled = previewData.detailsDialogAutoRefreshEnabled,
            verifiedMatchCount = previewData.verifiedMatchCount,
            transitCatalogLastRefreshLabel = previewData.transitCatalogLastRefreshLabel,
            liveSnapshotCacheLabel = previewData.liveSnapshotCacheLabel,
            gtfsTripDetailCacheLabel = previewData.gtfsTripDetailCacheLabel,
            vehiclePositionCacheLabel = previewData.vehiclePositionCacheLabel,
            apiKeySourceLabel = previewData.apiKeySourceLabel,
            onToggleShowSeconds = {},
            onToggleDetailsDialogAutoRefresh = {},
            onDecreaseVerifiedMatchCount = {},
            onIncreaseVerifiedMatchCount = {},
            onRefreshTransitCatalog = {},
            onCycleLiveSnapshotCache = {},
            onCycleGtfsTripDetailCache = {},
            onCycleVehiclePositionCache = {},
            onEditApiKey = {},
            onDismiss = {},
        )
    }
}

@WearSurfacePreview
@Composable
private fun ApiKeySettingsScreenPreview(
    @PreviewParameter(ApiKeyPreviewCaseProvider::class) previewCase: ApiKeyPreviewCase,
) {
    RouteTrackerPreviewScaffold(showTimeText = false) {
        ApiKeySettingsScreen(
            value = previewCase.value,
            sourceLabel = previewCase.sourceLabel,
            onValueChange = {},
            onSave = {},
            onUseBuiltIn = {},
            onDismiss = {},
        )
    }
}
