package io.github.mateuxlucax.caparao.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Generated with Material Color Utilities (tonal spot, the scheme Android derives from a wallpaper), so the brand
// themes read like the system's. docs/adr/0024-brand-color-option.md.

/** A brand seed color and its light and dark schemes. */
enum class BrandColor(val seed: Color, val light: ColorScheme, val dark: ColorScheme) {
    /** Coffee-leaf green, the launcher background. */
    LEAF(
        Color(0xFF1E3B2C),
        lightColorScheme(primary = Color(0xFF316A4E), onPrimary = Color(0xFFE6FFEE), primaryContainer = Color(0xFFB3F0CD), onPrimaryContainer = Color(0xFF225C41), inversePrimary = Color(0xFFBEFCD8), secondary = Color(0xFF4E6457), onSecondary = Color(0xFFE6FFEE), secondaryContainer = Color(0xFFD0E8D7), onSecondaryContainer = Color(0xFF41574A), tertiary = Color(0xFF2D676C), onTertiary = Color(0xFFE6FDFF), tertiaryContainer = Color(0xFFB5F0F4), onTertiaryContainer = Color(0xFF1E5C60), background = Color(0xFFF7FAF5), onBackground = Color(0xFF2B352F), surface = Color(0xFFF7FAF5), onSurface = Color(0xFF2B352F), surfaceVariant = Color(0xFFDBE5DC), onSurfaceVariant = Color(0xFF58615A), surfaceTint = Color(0xFF316A4E), inverseSurface = Color(0xFF0B0F0C), inverseOnSurface = Color(0xFF9A9E99), error = Color(0xFFA83836), onError = Color(0xFFFFF7F6), errorContainer = Color(0xFFFA746F), onErrorContainer = Color(0xFF6E0A12), outline = Color(0xFF737D76), outlineVariant = Color(0xFFAAB4AC), scrim = Color(0xFF000000), surfaceBright = Color(0xFFF7FAF5), surfaceContainer = Color(0xFFE9F0E8), surfaceContainerHigh = Color(0xFFE2EAE2), surfaceContainerHighest = Color(0xFFDBE5DC), surfaceContainerLow = Color(0xFFEFF5EE), surfaceContainerLowest = Color(0xFFFFFFFF), surfaceDim = Color(0xFFD3DDD4)),
        darkColorScheme(primary = Color(0xFFA3D1B5), onPrimary = Color(0xFF1E4834), primaryContainer = Color(0xFF315B45), onPrimaryContainer = Color(0xFFC0EED1), inversePrimary = Color(0xFF3E6851), secondary = Color(0xFFB4CCBC), onSecondary = Color(0xFF304539), secondaryContainer = Color(0xFF2B4034), onSecondaryContainer = Color(0xFFADC5B5), tertiary = Color(0xFFD0FCFF), onTertiary = Color(0xFF296468), tertiaryContainer = Color(0xFFB5F0F4), onTertiaryContainer = Color(0xFF1E5C60), background = Color(0xFF0B0F0C), onBackground = Color(0xFFDEE8DF), surface = Color(0xFF0B0F0C), onSurface = Color(0xFFDEE8DF), surfaceVariant = Color(0xFF1F2822), onSurfaceVariant = Color(0xFFA4AEA5), surfaceTint = Color(0xFFA3D1B5), inverseSurface = Color(0xFFF7FAF5), inverseOnSurface = Color(0xFF525652), error = Color(0xFFFA746F), onError = Color(0xFF490006), errorContainer = Color(0xFF871F21), onErrorContainer = Color(0xFFFF9993), outline = Color(0xFF6E7871), outlineVariant = Color(0xFF414A44), scrim = Color(0xFF000000), surfaceBright = Color(0xFF252E28), surfaceContainer = Color(0xFF141B17), surfaceContainerHigh = Color(0xFF1A211D), surfaceContainerHighest = Color(0xFF1F2822), surfaceContainerLow = Color(0xFF0F1511), surfaceContainerLowest = Color(0xFF000000), surfaceDim = Color(0xFF0B0F0C)),
    );
}
