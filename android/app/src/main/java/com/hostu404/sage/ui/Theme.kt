package com.hostu404.sage.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF3F5E45), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8D6), onPrimaryContainer = Color(0xFF10200F),
    secondary = Color(0xFF5B6458), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6DC), onSecondaryContainer = Color(0xFF181D16),
    background = Color(0xFFFAFAF6), onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFAFAF6), onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFE3E6DD), onSurfaceVariant = Color(0xFF4A4F47),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F5EF),
    surfaceContainer = Color(0xFFEFF1E9), surfaceContainerHigh = Color(0xFFE9EBE3),
    surfaceContainerHighest = Color(0xFFE3E6DD),
    outline = Color(0xFF7A8075), outlineVariant = Color(0xFFC8CCC2),
    error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDA), onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA6C9A6), onPrimary = Color(0xFF10331A),
    primaryContainer = Color(0xFF2A4630), onPrimaryContainer = Color(0xFFC8E6C6),
    secondary = Color(0xFFC0C9BB), onSecondary = Color(0xFF2B3229),
    secondaryContainer = Color(0xFF41493E), onSecondaryContainer = Color(0xFFDCE5D6),
    background = Color(0xFF121512), onBackground = Color(0xFFE3E5DE),
    surface = Color(0xFF121512), onSurface = Color(0xFFE3E5DE),
    surfaceVariant = Color(0xFF424840), onSurfaceVariant = Color(0xFFC2C7BD),
    surfaceContainerLowest = Color(0xFF0D100D), surfaceContainerLow = Color(0xFF1A1D19),
    surfaceContainer = Color(0xFF1E221E), surfaceContainerHigh = Color(0xFF282C27),
    surfaceContainerHighest = Color(0xFF333731),
    outline = Color(0xFF8C9288), outlineVariant = Color(0xFF424840),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDA),
)

/** Colours for how likely something is: 0 = unlikely or less ... 3 = very likely or more. */
class RiskColors(val levels: List<Color>, val calm: Color)

private val LightRisk = RiskColors(
    listOf(Color(0xFF5E7D63), Color(0xFF946300), Color(0xFFB5531F), Color(0xFFB3261E)), Color(0xFF7A8075))
private val DarkRisk = RiskColors(
    listOf(Color(0xFF9CC39F), Color(0xFFE0B44C), Color(0xFFF29A6B), Color(0xFFF2A19A)), Color(0xFF8C9288))

val LocalRisk = staticCompositionLocalOf { LightRisk }

@Composable
fun SageTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRisk provides if (dark) DarkRisk else LightRisk) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}
