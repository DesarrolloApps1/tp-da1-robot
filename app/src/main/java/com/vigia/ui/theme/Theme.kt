package com.vigia.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Navy = Color(0xFF0B1220)
    val NavySurface = Color(0xFF131C2E)
    val NavyRaised = Color(0xFF1C2740)
    val Line = Color(0xFF2E3A55)
    val Ink = Color(0xFFE4E9F2)
    val Muted = Color(0xFF9AA5BA)
    val Blue = Color(0xFF7FB2FF)
    val BlueDeep = Color(0xFF0E3A73)
    val Green = Color(0xFF4CC38A)
    val Amber = Color(0xFFF2B53A)
    val Red = Color(0xFFF2685C)
    val Grey = Color(0xFF7A8396)
    val Paper = Color(0xFFF6F7FA)
    val PaperInk = Color(0xFF141A26)
}

// Colores de estado que comparten todas las pantallas
object StatusColors {
    val ok = Palette.Green
    val warning = Palette.Amber
    val error = Palette.Red
    val stale = Palette.Grey
}

private val darkColors = darkColorScheme(
    primary = Palette.Blue,
    onPrimary = Palette.Navy,
    primaryContainer = Palette.BlueDeep,
    onPrimaryContainer = Palette.Ink,
    secondary = Palette.Green,
    onSecondary = Palette.Navy,
    // Indicador de la pestaña activa y filtros elegidos
    secondaryContainer = Palette.BlueDeep,
    onSecondaryContainer = Palette.Ink,
    tertiary = Palette.Amber,
    onTertiary = Palette.Navy,
    background = Palette.Navy,
    onBackground = Palette.Ink,
    surface = Palette.NavySurface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.NavyRaised,
    // Sin estos, diálogos, hojas y chips usan el gris violáceo por defecto de Material 3
    surfaceContainerLowest = Palette.Navy,
    surfaceContainerLow = Palette.NavySurface,
    surfaceContainer = Palette.NavySurface,
    surfaceContainerHigh = Palette.NavyRaised,
    surfaceContainerHighest = Palette.NavyRaised,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    error = Palette.Red,
    onError = Palette.Navy,
)

private val lightColors = lightColorScheme(
    primary = Palette.BlueDeep,
    onPrimary = Palette.Paper,
    secondary = Color(0xFF1F7A52),
    secondaryContainer = Color(0xFFD6E3F7),
    onSecondaryContainer = Palette.BlueDeep,
    tertiary = Color(0xFF9A6A00),
    background = Palette.Paper,
    onBackground = Palette.PaperInk,
    surface = Color.White,
    onSurface = Palette.PaperInk,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Palette.Paper,
    surfaceContainer = Color(0xFFEFF2F7),
    surfaceContainerHigh = Color(0xFFE8ECF3),
    surfaceContainerHighest = Color(0xFFE1E6EF),
    error = Color(0xFFB3261E),
)

// Oscuro por defecto: el guardia trabaja de noche en una central con poca luz
@Composable
fun VigiaTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) darkColors else lightColors, content = content)
}
