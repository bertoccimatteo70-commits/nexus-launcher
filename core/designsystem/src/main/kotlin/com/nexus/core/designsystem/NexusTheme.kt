package com.nexus.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexus.core.model.AccentPalette

/** Immutable visual configuration derived from user settings and automations. */
data class NexusThemeConfig(
    val darkTheme: Boolean = true,
    val palette: AccentPalette = AccentPalette.ION,
    val useDynamicColor: Boolean = true,
    val highContrast: Boolean = false,
    val fontScale: Float = 1f,
    val largeTouchTargets: Boolean = false,
    val blurEnabled: Boolean = false,
    val reduceMotion: Boolean = false,
    val animationSpeed: Float = 1f,
)

data class NexusMetrics(
    val iconSizeDp: Int = 54,
    val largeTouchTargets: Boolean = false,
    val blurEnabled: Boolean = false,
    val reduceMotion: Boolean = false,
    val animationSpeed: Float = 1f,
    val fontScale: Float = 1f,
) {
    val touchTargetDp: Int get() = if (largeTouchTargets) 56 else 48

    fun motion(baseMillis: Int): Int {
        if (reduceMotion) return 0
        return (baseMillis * animationSpeed).toInt().coerceIn(40, 2_000)
    }
}

val LocalNexusMetrics = staticCompositionLocalOf { NexusMetrics() }

object NexusColors {
    val void = Color(0xFF05070E)
    val deep = Color(0xFF0A111D)
    val ion = Color(0xFF35E0D5)
    val flare = Color(0xFFFF5C7A)
    val solar = Color(0xFFFFC24B)
    val ultraviolet = Color(0xFF9B8CFF)
    val mint = Color(0xFF54E6A5)
    val success = Color(0xFF54E6A5)
    val warning = Color(0xFFFFC24B)
    val danger = Color(0xFFFF6B87)
    val steel = Color(0xFF8FA0C2)
}

private val NexusShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun NexusTheme(config: NexusThemeConfig, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = when {
        config.useDynamicColor && config.darkTheme -> dynamicDarkColorScheme(context)
        config.useDynamicColor -> dynamicLightColorScheme(context)
        config.darkTheme -> nexusDarkScheme(config.palette, config.highContrast)
        else -> nexusLightScheme(config.palette, config.highContrast)
    }
    val scheme = if (config.highContrast) {
        base.copy(
            onBackground = if (config.darkTheme) Color.White else Color.Black,
            onSurface = if (config.darkTheme) Color.White else Color.Black,
            outline = if (config.darkTheme) Color(0xFFC9D4E6) else Color(0xFF2A3546),
        )
    } else {
        base
    }
    val metrics = NexusMetrics(
        largeTouchTargets = config.largeTouchTargets,
        blurEnabled = config.blurEnabled,
        reduceMotion = config.reduceMotion,
        animationSpeed = config.animationSpeed,
        fontScale = config.fontScale,
    )
    CompositionLocalProvider(LocalNexusMetrics provides metrics) {
        MaterialTheme(
            colorScheme = scheme,
            typography = nexusTypography(config.fontScale),
            shapes = NexusShapes,
            content = content,
        )
    }
}

private fun nexusTypography(scale: Float): Typography {
    fun style(
        size: Int,
        weight: FontWeight = FontWeight.Normal,
        lineHeight: Int = (size * 1.24f).toInt(),
        spacing: Float = 0f,
        family: FontFamily = FontFamily.SansSerif,
    ): TextStyle = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size * scale).sp,
        lineHeight = (lineHeight * scale).sp,
        letterSpacing = spacing.sp,
    )

    return Typography(
        displayLarge = style(56, FontWeight.SemiBold, 60, -2f),
        displayMedium = style(44, FontWeight.SemiBold, 48, -1.5f),
        displaySmall = style(34, FontWeight.SemiBold, 38, -1f),
        headlineLarge = style(30, FontWeight.SemiBold, 34, -0.8f),
        headlineMedium = style(26, FontWeight.SemiBold, 30, -0.6f),
        headlineSmall = style(22, FontWeight.SemiBold, 26, -0.4f),
        titleLarge = style(20, FontWeight.SemiBold, 24),
        titleMedium = style(17, FontWeight.Medium, 22),
        titleSmall = style(15, FontWeight.Medium, 20),
        bodyLarge = style(16, FontWeight.Normal, 22),
        bodyMedium = style(14, FontWeight.Normal, 20),
        bodySmall = style(12, FontWeight.Normal, 17),
        labelLarge = style(13, FontWeight.Medium, 16, 0.6f, FontFamily.Monospace),
        labelMedium = style(11, FontWeight.Medium, 14, 0.8f, FontFamily.Monospace),
        labelSmall = style(10, FontWeight.Medium, 13, 1f, FontFamily.Monospace),
    )
}

private fun darkAccent(palette: AccentPalette): Color = when (palette) {
    AccentPalette.ION -> NexusColors.ion
    AccentPalette.FLARE -> Color(0xFFFF6B87)
    AccentPalette.SOLAR -> NexusColors.solar
    AccentPalette.ULTRAVIOLET -> NexusColors.ultraviolet
    AccentPalette.MINT -> NexusColors.mint
}

private fun lightAccent(palette: AccentPalette): Color = when (palette) {
    AccentPalette.ION -> Color(0xFF006E69)
    AccentPalette.FLARE -> Color(0xFFB02243)
    AccentPalette.SOLAR -> Color(0xFF7A5100)
    AccentPalette.ULTRAVIOLET -> Color(0xFF4B3CB8)
    AccentPalette.MINT -> Color(0xFF006B45)
}

private fun nexusDarkScheme(palette: AccentPalette, highContrast: Boolean) = darkColorScheme(
    primary = darkAccent(palette),
    onPrimary = Color(0xFF03110F),
    primaryContainer = lerp(NexusColors.deep, darkAccent(palette), 0.20f),
    onPrimaryContainer = lerp(Color.White, darkAccent(palette), 0.45f),
    secondary = NexusColors.steel,
    onSecondary = Color(0xFF081018),
    secondaryContainer = Color(0xFF1A2434),
    onSecondaryContainer = Color(0xFFDCE6F6),
    tertiary = NexusColors.solar,
    onTertiary = Color(0xFF241700),
    background = NexusColors.void,
    onBackground = Color(0xFFE9EFFA),
    surface = NexusColors.deep,
    onSurface = Color(0xFFE9EFFA),
    surfaceVariant = Color(0xFF151F30),
    onSurfaceVariant = Color(0xFFA9B6CB),
    outline = if (highContrast) Color(0xFFC9D4E6) else Color(0xFF3A4658),
    outlineVariant = Color(0xFF232D3D),
    error = NexusColors.danger,
    onError = Color(0xFF1B0007),
    errorContainer = Color(0xFF3A0D17),
    onErrorContainer = Color(0xFFFFD9E0),
)

private fun nexusLightScheme(palette: AccentPalette, highContrast: Boolean) = lightColorScheme(
    primary = lightAccent(palette),
    onPrimary = Color.White,
    primaryContainer = lerp(Color.White, lightAccent(palette), 0.16f),
    onPrimaryContainer = lerp(Color.Black, lightAccent(palette), 0.35f),
    secondary = Color(0xFF3A4A63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E9F4),
    onSecondaryContainer = Color(0xFF111A26),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    background = Color(0xFFF3F5F9),
    onBackground = Color(0xFF0B1220),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1220),
    surfaceVariant = Color(0xFFE7ECF4),
    onSurfaceVariant = Color(0xFF48546A),
    outline = if (highContrast) Color(0xFF1B2636) else Color(0xFF97A3B7),
    outlineVariant = Color(0xFFD7DFEA),
    error = Color(0xFFB02243),
    onError = Color.White,
    errorContainer = Color(0xFFFFDDE4),
    onErrorContainer = Color(0xFF3D0311),
)
