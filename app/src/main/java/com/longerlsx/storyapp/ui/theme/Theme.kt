package com.longerlsx.storyapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = StoryLightPrimary,
    onPrimary = StoryLightOnPrimary,
    background = StoryLightBackground,
    onBackground = StoryLightOnBackground,
    surface = StoryLightSurface,
    onSurface = StoryLightOnBackground,
)

private val DarkColors = darkColorScheme(
    primary = StoryDarkPrimary,
    onPrimary = StoryDarkOnPrimary,
    background = StoryDarkBackground,
    onBackground = StoryDarkOnBackground,
    surface = StoryDarkSurface,
    onSurface = StoryDarkOnBackground,
)

@Composable
fun StoryAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = StoryTypography,
        content = content,
    )
}
