package dev.mateux.caparao.ui.components

import androidx.compose.ui.graphics.Path

/** Shared server silhouettes for the filter checklist and guided brew illustrations. */
internal object ServerArt {
    fun bodyPath(top: Float = 60.5f): Path = Path().apply {
        moveTo(46f, top)
        lineTo(74f, top)
        lineTo(74f, 64f)
        quadraticTo(74f, 66.5f, 75.5f, 68.5f)
        lineTo(84.5f, 81f)
        quadraticTo(87f, 84.5f, 87f, 88f)
        lineTo(87f, 90f)
        quadraticTo(87f, 96.5f, 80.5f, 96.5f)
        lineTo(39.5f, 96.5f)
        quadraticTo(33f, 96.5f, 33f, 90f)
        lineTo(33f, 88f)
        quadraticTo(33f, 84.5f, 35.5f, 81f)
        lineTo(44.5f, 68.5f)
        quadraticTo(46f, 66.5f, 46f, 64f)
        close()
    }

    fun handlePath(): Path = Path().apply {
        moveTo(74f, 69.5f)
        lineTo(89f, 69.5f)
        quadraticTo(96f, 69.5f, 96f, 76.5f)
        lineTo(96f, 80f)
        quadraticTo(96f, 87f, 89f, 87f)
        lineTo(83f, 87f)
    }
}
