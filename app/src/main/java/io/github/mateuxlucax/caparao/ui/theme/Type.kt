package io.github.mateuxlucax.caparao.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.mateuxlucax.caparao.R
import kotlin.math.roundToInt

/** Roboto Flex, bundled so it works offline; one variable file, its weight axis set per weight (docs/adr/0025-material-3-expressive.md). */
val RobotoFlex = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
        Font(R.font.roboto_flex, it, variationSettings = FontVariation.Settings(FontVariation.weight(it.weight)))
    },
)

/** Roboto Flex for display, headline and title; body and label stay on the default Roboto. */
val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = RobotoFlex),
        displayMedium = displayMedium.copy(fontFamily = RobotoFlex),
        displaySmall = displaySmall.copy(fontFamily = RobotoFlex),
        headlineLarge = headlineLarge.copy(fontFamily = RobotoFlex),
        headlineMedium = headlineMedium.copy(fontFamily = RobotoFlex),
        headlineSmall = headlineSmall.copy(fontFamily = RobotoFlex),
        titleLarge = titleLarge.copy(fontFamily = RobotoFlex),
        titleMedium = titleMedium.copy(fontFamily = RobotoFlex),
        titleSmall = titleSmall.copy(fontFamily = RobotoFlex),
    )
}

/** Tabular figures in Roboto Flex, for the weight and timer readouts. */
val TabularFlex = TextStyle(fontFamily = RobotoFlex, fontFeatureSettings = "tnum")

private val flexCache = mutableMapOf<Pair<Int, Int>, FontFamily>()

/**
 * Roboto Flex at a [weight] (wght 100..1000) and [width] (wdth 25..151) with tabular figures, for the numbers and titles
 * the app wants to stand out. One typeface per value pair, cached.
 */
fun flex(weight: Int, width: Int = 100): TextStyle {
    val w = weight.coerceIn(100, 1000)
    val family = flexCache.getOrPut(w to width) {
        FontFamily(
            Font(
                R.font.roboto_flex,
                FontWeight(w),
                variationSettings = FontVariation.Settings(FontVariation.weight(w), FontVariation.width(width.toFloat())),
            ),
        )
    }
    return TextStyle(fontFamily = family, fontWeight = FontWeight(w), fontFeatureSettings = "tnum")
}

/**
 * [flex] whose weight and width spring to the given values, bouncing a little past them. Snapped to steps of 20 and 5 so
 * a spring builds a handful of typefaces, not one per frame. Compose skips the spring when animations are off.
 */
@Composable
fun animatedFlex(weight: Int, width: Int = 100): TextStyle {
    val spec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)
    val w by animateFloatAsState(weight.toFloat(), spec, label = "wght")
    val wd by animateFloatAsState(width.toFloat(), spec, label = "wdth")
    return flex((w / 20).roundToInt() * 20, (wd / 5).roundToInt() * 5)
}
