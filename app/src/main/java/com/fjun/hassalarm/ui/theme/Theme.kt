package com.fjun.hassalarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
    primary = Primary,
    secondary = Accent,
    primaryVariant = PrimaryDark
)

private val LightColorPalette = lightColors(
    primary = Primary,
    secondary = Accent,
    primaryVariant = PrimaryDark
)

@Composable
fun HassAlarmTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        content = content
    )
}