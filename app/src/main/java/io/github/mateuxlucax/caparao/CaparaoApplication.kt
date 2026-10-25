package io.github.mateuxlucax.caparao

import android.app.Application

/** Owns the [AppContainer], so the activity and the screens' ViewModels share one object graph. */
class CaparaoApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
