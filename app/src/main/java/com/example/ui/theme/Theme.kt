package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.preferences.CustomThemePalette
import com.example.data.preferences.ThemeMode

private val AlpineDarkColors = darkColorScheme(
    primary = ForestGreenLight,
    onPrimary = Color(0xFF042617),
    primaryContainer = ForestGreenContainer,
    onPrimaryContainer = Color(0xFFA8F0CE),
    secondary = WarmAmberAccent,
    onSecondary = Color(0xFF2B1B00),
    secondaryContainer = Color(0xFF3E2D12),
    onSecondaryContainer = Color(0xFFFFDEA8),
    tertiary = Color(0xFF57A8D8),
    onTertiary = Color(0xFF002438),
    background = NavyDarkBg,
    onBackground = LightGrayText,
    surface = NavySurface,
    onSurface = LightGrayText,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = MutedGrayText,
    outline = Color(0xFF2D4966),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3B0000)
)

private val AlpineLightColors = lightColorScheme(
    primary = DeepForestPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EEDD),
    onPrimaryContainer = Color(0xFF083521),
    secondary = DeepNavySecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6E4F2),
    onSecondaryContainer = DarkNavyText,
    tertiary = Color(0xFFB87414),
    onTertiary = Color.White,
    background = LightGrayBg,
    onBackground = DarkNavyText,
    surface = LightCardSurface,
    onSurface = DarkNavyText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = SlateSecondaryText,
    outline = Color(0xFFBCCAD6),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val DeepOceanDarkColors = AlpineDarkColors.copy(
    primary = OceanCyanPrimary,
    onPrimary = Color(0xFF002833),
    primaryContainer = Color(0xFF063C4D),
    onPrimaryContainer = Color(0xFFB2F1FF),
    secondary = OceanTealSecondary,
    background = OceanDarkBg,
    surface = OceanSurface,
    surfaceVariant = Color(0xFF14314D)
)

private val GoldenRiverDarkColors = AlpineDarkColors.copy(
    primary = RiverGoldPrimary,
    onPrimary = Color(0xFF2A1800),
    primaryContainer = Color(0xFF472E06),
    onPrimaryContainer = Color(0xFFFFDFAA),
    secondary = RiverSageSecondary,
    background = RiverDarkBg,
    surface = RiverSurface,
    surfaceVariant = Color(0xFF29323D)
)

private val ElectricEmeraldDarkColors = AlpineDarkColors.copy(
    primary = ElectricGreenAccent,
    onPrimary = Color(0xFF022915),
    primaryContainer = Color(0xFF0E3E28),
    onPrimaryContainer = Color(0xFFB8FFD9),
    secondary = CrownGoldAccent,
    background = ElectricNavyBg,
    surface = ElectricNavySurface,
    surfaceVariant = Color(0xFF153048)
)

val FishLogShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FishLogTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    palette: CustomThemePalette = CustomThemePalette.ALPINE_FOREST,
    isPremiumActive: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val effectivePalette = if (palette.isPremium && !isPremiumActive) {
        CustomThemePalette.ALPINE_FOREST
    } else {
        palette
    }

    val colorScheme = if (!useDark) {
        AlpineLightColors
    } else {
        when (effectivePalette) {
            CustomThemePalette.ALPINE_FOREST -> AlpineDarkColors
            CustomThemePalette.DEEP_OCEAN -> DeepOceanDarkColors
            CustomThemePalette.GOLDEN_RIVER -> GoldenRiverDarkColors
            CustomThemePalette.ELECTRIC_EMERALD -> ElectricEmeraldDarkColors
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = FishLogShapes,
        content = content
    )
}
