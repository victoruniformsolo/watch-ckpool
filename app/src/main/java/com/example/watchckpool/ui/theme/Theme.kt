package com.example.watchckpool.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// This is your custom hardcoded Dark Theme using your new colors
private val CustomDarkColorScheme = darkColorScheme(
    primary = PurpleGrey80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    error = RedPrimary,
    errorContainer = RedDark,
    onErrorContainer = RedContainer,
    onPrimaryContainer = GreenContainer
)

// This is your custom hardcoded Light Theme using your new colors
private val CustomLightColorScheme = lightColorScheme(
    primary = PurpleGrey40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    error = RedPrimary,
    errorContainer = RedContainer,
    onErrorContainer = RedDark,
    onPrimaryContainer = Green40
)

@Composable
fun WatchCKPoolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ (Material You)
    // It picks colors from the user's wallpaper.
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {

      val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> CustomDarkColorScheme
        else -> CustomLightColorScheme
    }


    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
