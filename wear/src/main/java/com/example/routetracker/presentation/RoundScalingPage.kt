package com.example.routetracker.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScrollIndicator

@Composable
internal fun RoundScalingPage(
    state: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
    content: ScalingLazyListScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            autoCentering = null,
            contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
            content = content,
        )
        ScrollIndicator(
            state,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
