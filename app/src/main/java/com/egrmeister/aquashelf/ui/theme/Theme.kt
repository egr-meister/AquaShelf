package com.egrmeister.aquashelf.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Warm kitchen-shelf palette. Text colors keep WCAG AA contrast on cream. */
object ShelfColors {
    val Cream = Color(0xFFFAF4E8)
    val CreamDeep = Color(0xFFF3E9D6)
    val Surface = Color(0xFFFFFCF6)
    val Cabinet = Color(0xFFFFF9EE)
    val CabinetEdge = Color(0xFFE6D6BA)
    val Wood = Color(0xFFD9BD8F)
    val WoodDark = Color(0xFFB8955F)
    val WoodShadow = Color(0x26000000)

    val Slate = Color(0xFF2E3A46)
    val SlateSoft = Color(0xFF515D69)
    val SlateFaint = Color(0xFF7A848E)

    val GlassFill = Color(0xFF86A9CB)
    val GlassLight = Color(0xFFE3ECF5)
    val GlassLine = Color(0xFF4C6B8D)

    val BottleFill = Color(0xFF72B2A9)
    val BottleLight = Color(0xFFCBE6E1)
    val BottleLine = Color(0xFF2F5E62)

    val VesselFill = Color(0xFFC2B3A1)
    val VesselLight = Color(0xFFEDE5DA)
    val VesselLine = Color(0xFF6E6152)

    val Accent = Color(0xFF3F6E8F)
    val AccentContainer = Color(0xFFDCE8F2)
    val Error = Color(0xFFA3332B)
}

private val scheme = lightColorScheme(
    primary = ShelfColors.Accent,
    onPrimary = Color.White,
    primaryContainer = ShelfColors.AccentContainer,
    onPrimaryContainer = ShelfColors.Slate,
    secondary = ShelfColors.BottleLine,
    onSecondary = Color.White,
    secondaryContainer = ShelfColors.BottleLight,
    onSecondaryContainer = ShelfColors.Slate,
    tertiary = ShelfColors.WoodDark,
    background = ShelfColors.Cream,
    onBackground = ShelfColors.Slate,
    surface = ShelfColors.Surface,
    onSurface = ShelfColors.Slate,
    surfaceVariant = ShelfColors.CreamDeep,
    onSurfaceVariant = ShelfColors.SlateSoft,
    surfaceContainer = ShelfColors.CreamDeep,
    surfaceContainerLow = ShelfColors.Surface,
    surfaceContainerHigh = ShelfColors.CreamDeep,
    surfaceContainerHighest = ShelfColors.CabinetEdge,
    outline = Color(0xFFB9AC97),
    outlineVariant = ShelfColors.CabinetEdge,
    error = ShelfColors.Error,
)

private val base = Typography()

private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
)

/** True when the user asked for reduced decorative animation in Settings. */
val LocalReducedAnimation = compositionLocalOf { false }

/** True when the device uses a 24-hour clock. */
val LocalUse24Hour = compositionLocalOf { false }

@Composable
fun AquaShelfTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
