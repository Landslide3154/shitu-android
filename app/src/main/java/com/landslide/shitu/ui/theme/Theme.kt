package com.landslide.shitu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF1F6FEB)

private val Light = lightColorScheme(
    primary = Blue,
    secondary = Color(0xFF3B82F6),
    tertiary = Color(0xFFF5A623),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF93C5FD),
    tertiary = Color(0xFFFFC46B),
)

@Composable
fun ShituTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
