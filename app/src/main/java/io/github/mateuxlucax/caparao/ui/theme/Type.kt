package io.github.mateuxlucax.caparao.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import io.github.mateuxlucax.caparao.R

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
