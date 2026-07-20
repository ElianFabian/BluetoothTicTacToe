package com.elianfabian.bluetoothtictactoe.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue30,
    onPrimaryContainer = Blue90,
    secondary = BlueGrey80,
    onSecondary = BlueGrey20,
    secondaryContainer = BlueGrey30,
    onSecondaryContainer = BlueGrey90,
    tertiary = LightBlue80,
    onTertiary = LightBlue20,
    tertiaryContainer = LightBlue30,
    onTertiaryContainer = LightBlue90,
    background = BlueGrey15,
    onBackground = BlueGrey95,
    surface = BlueGrey15,
    onSurface = BlueGrey95,
    surfaceVariant = BlueGrey25,
    onSurfaceVariant = BlueGrey80
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = BlueGrey40,
    onSecondary = Color.White,
    secondaryContainer = BlueGrey90,
    onSecondaryContainer = BlueGrey10,
    tertiary = LightBlue40,
    onTertiary = Color.White,
    tertiaryContainer = LightBlue90,
    onTertiaryContainer = LightBlue10,
    background = BlueGrey99,
    onBackground = BlueGrey10,
    surface = BlueGrey99,
    onSurface = BlueGrey10,
    surfaceVariant = BlueGrey95,
    onSurfaceVariant = BlueGrey40
)

@Composable
fun BluetoothTicTacToeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
