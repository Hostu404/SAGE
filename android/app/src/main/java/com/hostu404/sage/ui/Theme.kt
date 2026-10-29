package com.hostu404.sage.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The website's colours: a dark dashboard, with colour only where it means something. */
object Sage {
    val bg = Color(0xFF0B0D10)
    val panel = Color(0xFF12161B)
    val panel2 = Color(0xFF181D24)
    val line = Color(0xFF232A32)
    val lineStrong = Color(0xFF34404C)
    val text = Color(0xFFE7EAEE)
    val muted = Color(0xFF98A3AD)
    val faint = Color(0xFF6B7781)

    /** How likely: unlikely or less, a real possibility, as likely as not or likely, very likely or more. */
    val chance = listOf(Color(0xFF98A3AD), Color(0xFFE5B84F), Color(0xFFF28B4B), Color(0xFFFF5B4F))
    val calm = Color(0xFF6B7781)
    val alert = Color(0xFFFF5B4F)
    val alertBg = Color(0xFF2A1214)

    /** The second country of a pair, on its map. */
    val pick2 = Color(0xFF8EA4BB)

    /** The map: no forecast, then under 1%, 1-5%, 5-20%, 20-40%, 40-80% and 80% or more. */
    val mapNone = Color(0xFF1B2129)
    val map = listOf(Color(0xFF2A3440), Color(0xFF4D4434), Color(0xFF8A5426), Color(0xFFC4651D), Color(0xFFEA4B2B), Color(0xFFFF2B3B))
    val mapLabels = listOf("Under 1%", "1–5%", "5–20%", "20–40%", "40–80%", "80%+")
}

val Mono = FontFamily.Monospace

/** Small capitals labels, as on the website: monospace and spaced out. */
fun label(size: TextUnit = 11.sp, weight: FontWeight = FontWeight.SemiBold) =
    TextStyle(fontFamily = Mono, fontSize = size, fontWeight = weight, letterSpacing = 0.08.em)

/** Numbers: monospace, so columns line up. */
fun mono(size: TextUnit, weight: FontWeight = FontWeight.SemiBold) = TextStyle(fontFamily = Mono, fontSize = size, fontWeight = weight)

private val Scheme = darkColorScheme(
    primary = Sage.text, onPrimary = Sage.bg,
    secondary = Sage.muted, onSecondary = Sage.bg,
    background = Sage.bg, onBackground = Sage.text,
    surface = Sage.bg, onSurface = Sage.text,
    surfaceVariant = Sage.panel2, onSurfaceVariant = Sage.muted,
    surfaceContainerLowest = Sage.bg, surfaceContainerLow = Sage.panel, surfaceContainer = Sage.panel,
    surfaceContainerHigh = Sage.panel2, surfaceContainerHighest = Sage.panel2,
    outline = Sage.lineStrong, outlineVariant = Sage.line,
    error = Sage.alert, onError = Sage.bg, errorContainer = Sage.alertBg, onErrorContainer = Color(0xFFFBE3E1),
)

@Composable
fun SageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography(), content = content)
}
