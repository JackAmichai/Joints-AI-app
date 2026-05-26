package com.example.ui.theme

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
    primary = ClinicalTeal,
    secondary = MintSecondary,
    tertiary = GreenSuccess,
    background = SlateBg,
    surface = SlateCard,
    error = CoralAlarm,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextTitle,
    onSurface = TextTitle
)

private val LightColorScheme = darkColorScheme( // Enforce dark scheme as default
    primary = ClinicalTeal,
    secondary = MintSecondary,
    tertiary = GreenSuccess,
    background = SlateBg,
    surface = SlateCard,
    error = CoralAlarm,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = TextTitle,
    onSurface = TextTitle
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force the premium, elegant dark healthcare mode
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme
  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
