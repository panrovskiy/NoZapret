package com.example.nozapret.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
)

@Composable
fun NoZapretTheme(
    themeMode: String = "System",
    customPrimaryColor: Color = Color(0xFF6750A4),
    customThemeBase: String = "System",
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = themeMode == "System",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "Light" -> false
        "Dark" -> true
        "Custom" -> when (customThemeBase) {
            "Light" -> false
            "Dark" -> true
            else -> isSystemInDarkTheme()
        }
        else -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        themeMode == "Custom" -> {
            if (isDark) darkColorScheme(primary = customPrimaryColor)
            else lightColorScheme(primary = customPrimaryColor)
        }
        dynamicColor -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
