package com.mono.fitness.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Luxury Monochrome Palette
val MonoBlack = Color(0xFF000000)
val MonoWhite = Color(0xFFFFFFFF)
val MonoDarkGray = Color(0xFF121212)
val MonoGray70 = Color(0xFF1E1E1E)
val MonoGray60 = Color(0xFF2C2C2E)
val MonoGray40 = Color(0xFF8E8E93)
val MonoGray20 = Color(0xFFAEA7A7)

val BubbleShape = RoundedCornerShape(24.dp)
val PillShape = RoundedCornerShape(50)
val CardShape = RoundedCornerShape(24.dp)

private val DarkColors = darkColorScheme(
    primary = MonoWhite,
    onPrimary = MonoBlack,
    secondary = MonoGray40,
    onSecondary = MonoWhite,
    background = MonoBlack,
    onBackground = MonoWhite,
    surface = MonoGray70,
    onSurface = MonoWhite,
    surfaceVariant = MonoGray60,
    onSurfaceVariant = MonoGray40,
    outline = MonoWhite.copy(alpha = 0.08f),
    error = MonoWhite,
    onError = MonoBlack
)

private val LightColors = lightColorScheme(
    primary = MonoBlack,
    onPrimary = MonoWhite,
    secondary = MonoGray40,
    onSecondary = MonoBlack,
    background = MonoWhite,
    onBackground = MonoBlack,
    surface = Color(0xFFF2F2F7),
    onSurface = MonoBlack,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = MonoBlack.copy(alpha = 0.6f),
    outline = MonoBlack.copy(alpha = 0.1f),
    error = MonoBlack,
    onError = MonoWhite
)

private val MonoTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        letterSpacing = (-1).sp,
        lineHeight = 48.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.sp
    )
)

@Composable
fun MonoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MonoTypography,
        content = content
    )
}

val monoSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

/**
 * Luxury Monochrome Surface.
 */
@Composable
fun FrostedPanel(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    elevated: Boolean = true,
    content: @Composable () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    
    Box(
        modifier = modifier
            .then(if (elevated) Modifier.shadow(4.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(scheme.surface)
            .border(
                width = 1.dp,
                color = scheme.outline,
                shape = shape
            )
    ) {
        content()
    }
}

@Composable
fun MonoScaffoldBackground(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.background)
    ) {
        content()
    }
}

fun Modifier.pill(): Modifier = clip(PillShape)
fun Modifier.circle(): Modifier = clip(CircleShape)

fun canUseRenderBlur(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
