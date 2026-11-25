package com.kirimly.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.kirimly.app.R
import com.kirimly.app.data.model.AppPalette
import com.kirimly.app.data.model.ThemeMode

/** Seed tones for one palette in one brightness. */
private data class PaletteTones(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val surfaceVariant: Color
)

private fun lightTones(palette: AppPalette): PaletteTones = when (palette) {
    AppPalette.MIDNIGHT_MONO -> PaletteTones(
        Color(0xFF040404), Color(0xFFEFEDEF), Color(0xFFDDDBDD), Color(0xFF040404),
        Color(0xFF2B2B2B), Color(0xFFDDDBDD), Color(0xFF040404),
        Color(0xFF1C5A9E), Color(0xFFD9EAFF), Color(0xFF04213F),
        Color(0xFFEFEDEF), Color(0xFFDDDBDD)
    )
    AppPalette.OCEAN_COBALT -> PaletteTones(
        Color(0xFF1F5FD6), Color.White, Color(0xFFDCE6FF), Color(0xFF001A43),
        Color(0xFF4F6083), Color(0xFFD8E2FF), Color(0xFF0A1B3C),
        Color(0xFF00838F), Color(0xFFC6F2F7), Color(0xFF00363C),
        Color(0xFFF8F9FF), Color(0xFFE1E2EC)
    )
    AppPalette.EMERALD_MINT -> PaletteTones(
        Color(0xFF0F7B5C), Color.White, Color(0xFFB8F2D9), Color(0xFF002116),
        Color(0xFF4C6358), Color(0xFFCEE9DA), Color(0xFF092016),
        Color(0xFF3C6472), Color(0xFFC0E9FA), Color(0xFF001F28),
        Color(0xFFF5FBF6), Color(0xFFDBE5DD)
    )
    AppPalette.SUNSET_CORAL -> PaletteTones(
        Color(0xFFC8463D), Color.White, Color(0xFFFFDAD5), Color(0xFF410002),
        Color(0xFF775651), Color(0xFFFFDAD5), Color(0xFF2C1512),
        Color(0xFF9A5B00), Color(0xFFFFDDB8), Color(0xFF2A1700),
        Color(0xFFFFF8F6), Color(0xFFF5DDDA)
    )
    AppPalette.ROYAL_VIOLET -> PaletteTones(
        Color(0xFF6A3FD0), Color.White, Color(0xFFE9DDFF), Color(0xFF22005D),
        Color(0xFF625B71), Color(0xFFE8DEF8), Color(0xFF1E192B),
        Color(0xFF9A3C78), Color(0xFFFFD8EC), Color(0xFF3B0029),
        Color(0xFFFDF8FF), Color(0xFFE6E0EC)
    )
    AppPalette.GRAPHITE_SLATE -> PaletteTones(
        Color(0xFF3D4F63), Color.White, Color(0xFFD3E4F9), Color(0xFF0B1D2E),
        Color(0xFF5A5F66), Color(0xFFDEE3EB), Color(0xFF171C22),
        Color(0xFF6B5778), Color(0xFFF2DAFF), Color(0xFF251431),
        Color(0xFFF8F9FB), Color(0xFFDFE2E8)
    )
}

private fun darkTones(palette: AppPalette): PaletteTones = when (palette) {
    AppPalette.MIDNIGHT_MONO -> PaletteTones(
        Color(0xFFEDEDED), Color(0xFF040404), Color(0xFF1D1D1D), Color(0xFFF9F8F9),
        Color(0xFFE5E5E4), Color(0xFF1D1D1D), Color(0xFFF9F8F9),
        Color(0xFFA3CFFF), Color(0xFF0F2742), Color(0xFFD6E9FF),
        Color(0xFF000000), Color(0xFF1D1D1D)
    )
    AppPalette.OCEAN_COBALT -> PaletteTones(
        Color(0xFFB0C6FF), Color(0xFF002D6F), Color(0xFF0F449C), Color(0xFFDCE6FF),
        Color(0xFFBAC6EA), Color(0xFF37476A), Color(0xFFD8E2FF),
        Color(0xFF7FD6E0), Color(0xFF004F57), Color(0xFFC6F2F7),
        Color(0xFF111318), Color(0xFF44474F)
    )
    AppPalette.EMERALD_MINT -> PaletteTones(
        Color(0xFF7FD9B4), Color(0xFF003828), Color(0xFF005A42), Color(0xFFB8F2D9),
        Color(0xFFB3CCBF), Color(0xFF344B40), Color(0xFFCEE9DA),
        Color(0xFFA4CDDD), Color(0xFF234C5A), Color(0xFFC0E9FA),
        Color(0xFF0F1512), Color(0xFF404944)
    )
    AppPalette.SUNSET_CORAL -> PaletteTones(
        Color(0xFFFFB4AA), Color(0xFF690005), Color(0xFF93231F), Color(0xFFFFDAD5),
        Color(0xFFE7BDB7), Color(0xFF5D3F3B), Color(0xFFFFDAD5),
        Color(0xFFFFB95F), Color(0xFF653E00), Color(0xFFFFDDB8),
        Color(0xFF1A1110), Color(0xFF534341)
    )
    AppPalette.ROYAL_VIOLET -> PaletteTones(
        Color(0xFFD0BCFF), Color(0xFF3A0092), Color(0xFF5225B6), Color(0xFFE9DDFF),
        Color(0xFFCCC2DC), Color(0xFF4A4458), Color(0xFFE8DEF8),
        Color(0xFFFFAFD8), Color(0xFF7C225F), Color(0xFFFFD8EC),
        Color(0xFF141218), Color(0xFF49454F)
    )
    AppPalette.GRAPHITE_SLATE -> PaletteTones(
        Color(0xFFB7C8DD), Color(0xFF213243), Color(0xFF34475B), Color(0xFFD3E4F9),
        Color(0xFFC2C7CF), Color(0xFF42474E), Color(0xFFDEE3EB),
        Color(0xFFD7BEE4), Color(0xFF523F5F), Color(0xFFF2DAFF),
        Color(0xFF111316), Color(0xFF43474E)
    )
}

/**
 * Midnight Mono: black ground, near-white type and actions, hairline structure.
 * Colour appears only for status (accent #A3CFFF as tertiary, error red).
 */
private fun midnightMonoScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = Color(0xFFEDEDED),
        onPrimary = Color(0xFF040404),
        primaryContainer = Color(0xFF1D1D1D),
        onPrimaryContainer = Color(0xFFF9F8F9),
        inversePrimary = Color(0xFF040404),
        secondary = Color(0xFFE5E5E4),
        onSecondary = Color(0xFF040404),
        secondaryContainer = Color(0xFF1D1D1D),
        onSecondaryContainer = Color(0xFFF9F8F9),
        tertiary = Color(0xFFA3CFFF),
        onTertiary = Color(0xFF040404),
        tertiaryContainer = Color(0xFF0F2742),
        onTertiaryContainer = Color(0xFFD6E9FF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF9F8F9),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFF9F8F9),
        surfaceVariant = Color(0xFF1D1D1D),
        onSurfaceVariant = Color(0xFFD6D6D5),
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFFEFEDEF),
        inverseOnSurface = Color(0xFF040404),
        error = Color(0xFFFF8A80),
        onError = Color(0xFF040404),
        errorContainer = Color(0xFF2A0B0B),
        onErrorContainer = Color(0xFFFFD6D2),
        outline = Color(0xFF8C8C8B),
        outlineVariant = Color(0xFF1D1D1D),
        scrim = Color.Black,
        surfaceBright = Color(0xFF1D1D1D),
        surfaceDim = Color(0xFF000000),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0B0B0B),
        surfaceContainer = Color(0xFF0B0B0B),
        surfaceContainerHigh = Color(0xFF141414),
        surfaceContainerHighest = Color(0xFF1D1D1D)
    )
} else {
    lightColorScheme(
        primary = Color(0xFF040404),
        onPrimary = Color(0xFFEFEDEF),
        primaryContainer = Color(0xFFDDDBDD),
        onPrimaryContainer = Color(0xFF040404),
        inversePrimary = Color(0xFFEDEDED),
        secondary = Color(0xFF2B2B2B),
        onSecondary = Color(0xFFEFEDEF),
        secondaryContainer = Color(0xFFDDDBDD),
        onSecondaryContainer = Color(0xFF040404),
        tertiary = Color(0xFF1C5A9E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD9EAFF),
        onTertiaryContainer = Color(0xFF04213F),
        background = Color(0xFFEFEDEF),
        onBackground = Color(0xFF040404),
        surface = Color(0xFFEFEDEF),
        onSurface = Color(0xFF040404),
        surfaceVariant = Color(0xFFDDDBDD),
        onSurfaceVariant = Color(0xFF3A3A3A),
        surfaceTint = Color.Transparent,
        inverseSurface = Color(0xFF040404),
        inverseOnSurface = Color(0xFFF9F8F9),
        error = Color(0xFFB3261E),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF6E6E6E),
        outlineVariant = Color(0xFFD4D2D4),
        scrim = Color.Black,
        surfaceBright = Color(0xFFF8F7F8),
        surfaceDim = Color(0xFFE2E0E2),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8F7F8),
        surfaceContainer = Color(0xFFF8F7F8),
        surfaceContainerHigh = Color(0xFFE8E6E8),
        surfaceContainerHighest = Color(0xFFDDDBDD)
    )
}

fun kirimlyColorScheme(palette: AppPalette, dark: Boolean): ColorScheme {
    if (palette == AppPalette.MIDNIGHT_MONO) return midnightMonoScheme(dark)
    return if (dark) {
        val t = darkTones(palette)
        darkColorScheme(
            primary = t.primary,
            onPrimary = t.onPrimary,
            primaryContainer = t.primaryContainer,
            onPrimaryContainer = t.onPrimaryContainer,
            inversePrimary = lightTones(palette).primary,
            secondary = t.secondary,
            onSecondary = Color(0xFF1B1F27),
            secondaryContainer = t.secondaryContainer,
            onSecondaryContainer = t.onSecondaryContainer,
            tertiary = t.tertiary,
            onTertiary = Color(0xFF1B1F27),
            tertiaryContainer = t.tertiaryContainer,
            onTertiaryContainer = t.onTertiaryContainer,
            background = t.background,
            onBackground = Color(0xFFE3E4EA),
            surface = t.background,
            onSurface = Color(0xFFE3E4EA),
            surfaceVariant = t.surfaceVariant,
            onSurfaceVariant = Color(0xFFC5C7D0),
            surfaceTint = t.primary,
            inverseSurface = Color(0xFFE3E4EA),
            inverseOnSurface = Color(0xFF2E3036),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = Color(0xFF8E9099),
            outlineVariant = lerp(t.surfaceVariant, t.background, 0.35f),
            scrim = Color.Black,
            surfaceBright = lerp(t.background, Color.White, 0.14f),
            surfaceDim = t.background,
            surfaceContainerLowest = lerp(t.background, Color.Black, 0.35f),
            surfaceContainerLow = lerp(t.background, t.primary, 0.05f),
            surfaceContainer = lerp(t.background, t.primary, 0.08f),
            surfaceContainerHigh = lerp(t.background, t.primary, 0.12f),
            surfaceContainerHighest = lerp(t.background, t.primary, 0.16f)
        )
    } else {
        val t = lightTones(palette)
        lightColorScheme(
            primary = t.primary,
            onPrimary = t.onPrimary,
            primaryContainer = t.primaryContainer,
            onPrimaryContainer = t.onPrimaryContainer,
            inversePrimary = darkTones(palette).primary,
            secondary = t.secondary,
            onSecondary = Color.White,
            secondaryContainer = t.secondaryContainer,
            onSecondaryContainer = t.onSecondaryContainer,
            tertiary = t.tertiary,
            onTertiary = Color.White,
            tertiaryContainer = t.tertiaryContainer,
            onTertiaryContainer = t.onTertiaryContainer,
            background = t.background,
            onBackground = Color(0xFF1A1C21),
            surface = t.background,
            onSurface = Color(0xFF1A1C21),
            surfaceVariant = t.surfaceVariant,
            onSurfaceVariant = Color(0xFF454852),
            surfaceTint = t.primary,
            inverseSurface = Color(0xFF2E3036),
            inverseOnSurface = Color(0xFFF0F0F7),
            error = Color(0xFFBA1A1A),
            onError = Color.White,
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            outline = Color(0xFF757782),
            outlineVariant = lerp(t.surfaceVariant, t.background, 0.25f),
            scrim = Color.Black,
            surfaceBright = t.background,
            surfaceDim = lerp(t.background, Color.Black, 0.08f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(t.background, t.primary, 0.03f),
            surfaceContainer = lerp(t.background, t.primary, 0.06f),
            surfaceContainerHigh = lerp(t.background, t.primary, 0.09f),
            surfaceContainerHighest = lerp(t.background, t.primary, 0.12f)
        )
    }
}

/** Host Grotesk variable font (wght 300–800), bundled in res/font under the SIL OFL 1.1. */
@OptIn(ExperimentalTextApi::class)
private fun hostGrotesk(weight: Int): Font = Font(
    resId = R.font.host_grotesk,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

val HostGrotesk = FontFamily(
    hostGrotesk(300),
    hostGrotesk(400),
    hostGrotesk(500),
    hostGrotesk(600),
    hostGrotesk(700)
)

private val KirimlyTypography = Typography().let { base ->
    fun TextStyle.host(weight: FontWeight, tracking: Double = 0.0): TextStyle =
        copy(fontFamily = HostGrotesk, fontWeight = weight, letterSpacing = tracking.sp)
    base.copy(
        displayLarge = base.displayLarge.host(FontWeight.Medium, -1.6),
        displayMedium = base.displayMedium.host(FontWeight.Medium, -1.2),
        displaySmall = base.displaySmall.host(FontWeight.Medium, -0.9),
        headlineLarge = base.headlineLarge.host(FontWeight.Medium, -0.8),
        headlineMedium = base.headlineMedium.host(FontWeight.Medium, -0.6),
        headlineSmall = base.headlineSmall.host(FontWeight.Medium, -0.4),
        titleLarge = base.titleLarge.host(FontWeight.Medium, -0.2),
        titleMedium = base.titleMedium.host(FontWeight.Medium, -0.1),
        titleSmall = base.titleSmall.host(FontWeight.Medium),
        bodyLarge = base.bodyLarge.host(FontWeight.Normal).copy(lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.host(FontWeight.Normal),
        bodySmall = base.bodySmall.host(FontWeight.Normal),
        labelLarge = base.labelLarge.host(FontWeight.Medium),
        labelMedium = base.labelMedium.host(FontWeight.Medium),
        labelSmall = base.labelSmall.host(FontWeight.Medium)
    )
}

val MonoDigits = TextStyle(
    fontFamily = HostGrotesk,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    letterSpacing = 6.sp,
    fontFeatureSettings = "tnum"
)

/** Sharp surfaces everywhere; controls (buttons, pills) stay fully rounded by Material default. */
private val SharpCorner = RoundedCornerShape(0.dp)

private val KirimlyShapes = Shapes(
    extraSmall = SharpCorner,
    small = SharpCorner,
    medium = SharpCorner,
    large = SharpCorner,
    extraLarge = SharpCorner
)

@Composable
fun KirimlyTheme(
    themeMode: ThemeMode,
    palette: AppPalette,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = kirimlyColorScheme(palette, dark)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KirimlyTypography,
        shapes = KirimlyShapes,
        content = content
    )
}
