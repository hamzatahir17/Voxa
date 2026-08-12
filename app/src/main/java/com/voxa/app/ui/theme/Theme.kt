package com.voxa.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val MidnightColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = CyanOnPrimary,
    primaryContainer = CyanPrimaryContainer,
    secondary = SecondaryGreen,
    tertiary = TertiaryBlue,
    background = MidnightBackground,
    surface = MidnightSurface,
    onBackground = MidnightOnSurface,
    onSurface = MidnightOnSurface,
    surfaceVariant = MidnightSurfaceContainer,
    onSurfaceVariant = MidnightOnSurfaceVariant,
    outline = MidnightOutline,
    outlineVariant = MidnightOutlineVariant,
    error = ErrorRed
)

@Composable
fun VoxaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled to maintain brand identity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MidnightColorScheme else MidnightColorScheme // Always dark for Voxa

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}