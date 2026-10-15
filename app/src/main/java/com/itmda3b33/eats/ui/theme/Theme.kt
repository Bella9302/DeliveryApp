package com.itmda3b33.eats.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/*
 * Orange in light mode, purple in dark mode, matching the existing designs.
 * Every text colour comes from the colour scheme (onBackground, onSurface ...), never a fixed colour,
 * so headings stay readable in both themes.
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFFEA580C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF7C2D12),
    // Used for links such as "Forgot password?"; darker than primary so small text has enough contrast.
    secondary = Color(0xFFC2410C),
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFECE6EE),
    onSurfaceVariant = Color(0xFF55525A),
    outline = Color(0xFF9A979E),
    outlineVariant = Color(0xFFDCD8DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C3AED),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B1F70),
    onPrimaryContainer = Color(0xFFEDE4FF),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1C0F3A),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0A0A0A),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF353438),
    onSurfaceVariant = Color(0xFFBDBAC2),
    outline = Color(0xFF6E6B73),
    outlineVariant = Color(0xFF2E2D31),
)

/** True inside a dark theme. Used by the logo, which uses different colours per theme. */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val BaseTypography = Typography()

private val EatsTypography = BaseTypography.copy(
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

@Composable
fun EatsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = EatsTypography,
            content = content,
        )
    }
}
