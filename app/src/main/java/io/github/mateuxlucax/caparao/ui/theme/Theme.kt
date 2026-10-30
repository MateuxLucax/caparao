package io.github.mateuxlucax.caparao.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material You colors, or a [brand] color's scheme when the user picks one; minSdk 31 always has dynamic color, so there
 * is no fallback scheme (docs/adr/0005-compose-material3-dynamic-color.md, docs/adr/0024-brand-color-option.md). Expressive
 * motion and Roboto Flex titles go on top (docs/adr/0025-material-3-expressive.md).
 */
@Composable
fun AppTheme(brand: BrandColor? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        brand != null -> if (dark) brand.dark else brand.light
        dark -> dynamicDarkColorScheme(context)
        else -> dynamicLightColorScheme(context)
    }
    MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive(), typography = AppTypography, content = content)
}
