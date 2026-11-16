package dev.mateux.caparao.ui.coffees

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
internal fun CoffeeBagLevelBar(leftGrams: Double, weightGrams: Double, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { (leftGrams / weightGrams).toFloat() },
        modifier = modifier.fillMaxWidth().testTag("coffee-bag-level"),
        drawStopIndicator = {},
    )
}
