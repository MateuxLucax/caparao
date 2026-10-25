package io.github.mateuxlucax.caparao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.mateuxlucax.caparao.ui.App
import io.github.mateuxlucax.caparao.ui.theme.AppTheme

/** The app's launcher activity. Its name is public (launcher shortcuts): do not move or rename it. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                App(BuildConfig.VERSION_NAME)
            }
        }
    }
}
