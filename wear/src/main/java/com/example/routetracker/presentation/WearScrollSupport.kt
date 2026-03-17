package com.example.routetracker.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable

@Composable
internal fun Modifier.wearRotaryScrollable(scrollState: ScrollState): Modifier {
    val focusRequester = remember { FocusRequester() }
    val rotaryBehavior = RotaryScrollableDefaults.behavior(scrollState)

    // These screens use regular scroll states, so we opt them into Wear rotary/touch-bezel
    // scrolling explicitly instead of rewriting each surface as a lazy list.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    return this
        .focusRequester(focusRequester)
        .focusable()
        .rotaryScrollable(
            behavior = rotaryBehavior,
            focusRequester = focusRequester,
            reverseDirection = false,
        )
}
