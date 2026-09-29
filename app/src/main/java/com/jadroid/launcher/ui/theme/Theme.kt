package com.jadroid.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.jadroid.launcher.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Orange50,
    onPrimary = Color.White,
    primaryContainer = Orange90,
    onPrimaryContainer = Orange10,
    secondary = Orange40,
    onSecondary = Color.White,
    secondaryContainer = Orange95,
    onSecondaryContainer = Orange20,
    tertiary = Orange30,
    onTertiary = Color.White,
    background = DaySurface,
    onBackground = Neutral10,
    surface = DaySurface,
    onSurface = Neutral10,
    surfaceVariant = DaySurfaceVariant,
    onSurfaceVariant = Neutral30,
    outline = Neutral50,
    outlineVariant = Neutral80,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Orange60,
    onPrimary = Orange10,
    primaryContainer = Orange30,
    onPrimaryContainer = Orange90,
    secondary = Orange70,
    onSecondary = Orange10,
    secondaryContainer = Orange30,
    onSecondaryContainer = Orange90,
    tertiary = Orange80,
    onTertiary = Orange20,
    background = NightSurface,
    onBackground = Neutral90,
    surface = NightSurface,
    onSurface = Neutral90,
    surfaceVariant = NightSurfaceVariant,
    onSurfaceVariant = Neutral80,
    outline = Neutral60,
    outlineVariant = Neutral30,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun JadroidTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
