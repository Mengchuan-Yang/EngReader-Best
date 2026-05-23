package com.engreader.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.engreader.app.model.ThemeMode

private val DarkColorScheme =
  darkColorScheme(
    primary = ReaderDarkPrimary,
    onPrimary = ReaderDarkOnPrimary,
    background = ReaderDarkBackground,
    onBackground = ReaderDarkOnBackground,
    surface = ReaderDarkSurface,
    onSurface = ReaderDarkOnSurface,
    surfaceVariant = ReaderDarkSurfaceVariant,
    onSurfaceVariant = ReaderDarkOnSurfaceVariant,
    outline = ReaderDarkOutline,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ReaderLightPrimary,
    onPrimary = ReaderLightOnPrimary,
    background = ReaderLightBackground,
    onBackground = ReaderLightOnBackground,
    surface = ReaderLightSurface,
    onSurface = ReaderLightOnSurface,
    surfaceVariant = ReaderLightSurfaceVariant,
    onSurfaceVariant = ReaderLightOnSurfaceVariant,
    outline = ReaderLightOutline,
  )

@Composable
fun EpubReaderTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val darkTheme =
    when (themeMode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.DAY -> false
      ThemeMode.NIGHT -> true
    }

  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
