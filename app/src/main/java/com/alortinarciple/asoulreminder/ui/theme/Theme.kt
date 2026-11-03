package com.alortinarciple.asoulreminder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Canvas = Color(0xFFF4F5F7)
val SurfaceSoft = Color(0xFFF8F8F5)
val SurfacePressed = Color(0xFFE9EDF3)
val Ink = Color(0xFF29323A)
val Muted = Color(0xFF76818D)
val BrandPink = Color(0xFFE95492)
val SunYellow = Color(0xFFFFBD2E)
val FreshGreen = Color(0xFF2DBE8C)
val Indigo = Color(0xFF576690)

private val LightColors = lightColorScheme(
    primary = BrandPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E7),
    onPrimaryContainer = Ink,
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE4F8),
    onSecondaryContainer = Ink,
    tertiary = SunYellow,
    onTertiary = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = SurfaceSoft,
    onSurface = Ink,
    surfaceVariant = SurfacePressed,
    onSurfaceVariant = Muted,
    outline = Color(0xFFD7DCE3),
    error = Color(0xFFB3261E),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
)

@Composable
fun AsoulReminderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
