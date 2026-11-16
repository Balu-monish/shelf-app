package com.ebooksplayer.shelf.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ebooksplayer.shelf.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = ShelfAmberDark,
    onPrimary = ShelfCream,
    secondary = ShelfInk,
    background = ShelfCream,
    surface = ShelfCream,
    onBackground = ShelfInk,
    onSurface = ShelfInk,
    error = ShelfError,
)

private val DarkColors = darkColorScheme(
    primary = ShelfAmber,
    onPrimary = ShelfInk,
    secondary = ShelfCream,
    background = ShelfInk,
    surface = ShelfInkLight,
    onBackground = ShelfCream,
    onSurface = ShelfCream,
    error = ShelfError,
)

@Composable
fun ShelfTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ShelfTypography,
        content = content,
    )
}
