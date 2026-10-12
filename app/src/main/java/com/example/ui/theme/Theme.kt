package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Màu ngữ nghĩa dùng cho phản hồi đúng/sai, thích ứng sáng - tối. */
@Immutable
data class FeedbackColors(
  val success: Color,
  val successContainer: Color,
  val warning: Color,
  val isDark: Boolean
)

val LocalFeedbackColors = staticCompositionLocalOf {
  FeedbackColors(SuccessLight, SuccessContainerLight, BrandAmber, false)
}

val MaterialTheme.feedback: FeedbackColors
  @Composable @ReadOnlyComposable get() = LocalFeedbackColors.current

private val DarkColorScheme =
  darkColorScheme(
    primary = BrandIndigoDark,
    onPrimary = Color(0xFF14104A),
    primaryContainer = BrandIndigoContainerDark,
    onPrimaryContainer = Color(0xFFE3E0FF),
    secondary = BrandTealDark,
    onSecondary = Color(0xFF00302C),
    secondaryContainer = BrandTealContainerDark,
    onSecondaryContainer = Color(0xFFBFF5EE),
    tertiary = BrandAmber,
    background = CanvasDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = InkVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFFDAD9),
    inverseSurface = InkDark,
    inverseOnSurface = SurfaceDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BrandIndigo,
    onPrimary = Color.White,
    primaryContainer = BrandIndigoContainer,
    onPrimaryContainer = Color(0xFF1A1259),
    secondary = BrandTeal,
    onSecondary = Color.White,
    secondaryContainer = BrandTealContainer,
    onSecondaryContainer = Color(0xFF00403D),
    tertiary = BrandAmber,
    background = CanvasLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = InkVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = ErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF5C1D20),
    inverseSurface = InkLight,
    inverseOnSurface = SurfaceLight
  )

private val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color tắt mặc định để giữ nhận diện thương hiệu thống nhất giữa các máy.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  val feedbackColors =
    if (darkTheme) FeedbackColors(SuccessDark, SuccessContainerDark, BrandAmber, true)
    else FeedbackColors(SuccessLight, SuccessContainerLight, BrandAmber, false)

  CompositionLocalProvider(LocalFeedbackColors provides feedbackColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = AppShapes, content = content)
  }
}
