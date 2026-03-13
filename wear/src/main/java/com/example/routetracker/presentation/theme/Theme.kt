package com.example.routetracker.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val AndroidNightColorScheme = ColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E5F),
    primaryContainer = Color(0xFF0F4C81),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFF81C995),
    onSecondary = Color(0xFF0F381D),
    secondaryContainer = Color(0xFF1F4D2B),
    onSecondaryContainer = Color(0xFFBDF1C6),
    tertiary = Color(0xFFF9AB72),
    onTertiary = Color(0xFF542100),
    tertiaryContainer = Color(0xFF743400),
    onTertiaryContainer = Color(0xFFFFDCC7),
    surfaceContainerLow = Color(0xFF06090D),
    surfaceContainer = Color(0xFF0D1117),
    surfaceContainerHigh = Color(0xFF151C24),
    onSurface = Color(0xFFF1F4F8),
    onSurfaceVariant = Color(0xFFB8C2CC),
    outline = Color(0xFF7E8A96),
    outlineVariant = Color(0xFF28323C),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF1F4F8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun RouteTrackerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AndroidNightColorScheme,
        content = content
    )
}
