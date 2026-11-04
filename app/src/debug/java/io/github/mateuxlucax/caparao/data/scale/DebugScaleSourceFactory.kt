package io.github.mateuxlucax.caparao.data.scale

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import io.github.mateuxlucax.caparao.AppContainer
import io.github.mateuxlucax.caparao.MainActivity

/** Reflection entry point from main source so this factory is absent from release APKs. */
object DebugScaleSourceFactory {
    @JvmStatic
    fun install(activity: MainActivity, container: AppContainer, intent: Intent) {
        val scenario = intent.getStringExtra(MainActivity.EXTRA_SCRIPTED)
        val apiEnabled = intent.getBooleanExtra(MainActivity.EXTRA_SCRIPTED_API, false)
        require(scenario != null || apiEnabled) { "A scenario or scripted API extra is required" }
        val json = scenario?.let { name ->
            activity.assets.open("scenarios/$name.json").bufferedReader().use { it.readText() }
        } ?: """{"name":"api","events":[{"at_ms":0,"type":"connected"},{"at_ms":0,"type":"weight","grams":0}]}"""
        val source = ScriptedScaleSource(
            activity.lifecycleScope,
            json,
            intent.getDoubleExtra("script_speed", 1.0),
            intent.getBooleanExtra("script_paused", apiEnabled),
        )
        container.installDebugScaleSource(source)
        if (apiEnabled) ScriptedScaleHttpServer(source)
    }
}
