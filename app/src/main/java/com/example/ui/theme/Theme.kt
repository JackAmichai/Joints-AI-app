package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkClinicalScheme = darkColorScheme(
    primary = ClinicalTeal,
    onPrimary = TextOnAccent,
    primaryContainer = ClinicalTealDark,
    onPrimaryContainer = ClinicalTealLight,
    secondary = MintSecondary,
    onSecondary = TextOnAccent,
    secondaryContainer = Color(0xFF1A3B38),
    onSecondaryContainer = MintSecondary,
    tertiary = GreenSuccess,
    onTertiary = TextOnAccent,
    error = CoralAlarm,
    onError = Color.White,
    errorContainer = CoralAlarmSurface,
    onErrorContainer = CoralAlarm,
    background = SlateBg,
    onBackground = TextTitle,
    surface = SlateCard,
    onSurface = TextTitle,
    surfaceVariant = SlateBgElevated,
    onSurfaceVariant = TextBody,
    outline = SlateBorder,
    outlineVariant = Color(0xFF1E3248),
    inverseSurface = TextTitle,
    inverseOnSurface = SlateBg,
    surfaceTint = ClinicalTeal,
    scrim = ShadowDark
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkClinicalScheme,
        typography = Typography,
        content = content
    )
}
