package dev.mateux.caparao

import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.mateux.caparao.data.scale.ReplayScaleSource
import dev.mateux.caparao.data.scale.ScaleSource
import dev.mateux.caparao.data.scale.ScriptedScaleHttpServer
import dev.mateux.caparao.data.scale.ScriptedScaleSource
import kotlinx.coroutines.CoroutineScope

/** Debug and benchmark startup: scale simulation and fixture seeding stay out of shipping code. */
class DebugScaleHooks : DebugHooks {
    private var intent: Intent? = null
    private var fresh = false

    override fun prepare(intent: Intent, savedInstanceState: Bundle?) {
        this.intent = intent
        fresh = savedInstanceState == null
    }

    override fun createScale(context: Context, scope: CoroutineScope): ScaleSource? {
        val intent = intent ?: return null
        intent.getStringExtra(EXTRA_REPLAY)?.let { session ->
            val lines = context.assets.open("sessions/$session.jsonl").bufferedReader().use { it.readLines() }
            return ReplayScaleSource(
                scope, lines, intent.getLongExtra(EXTRA_REPLAY_AT_MS, 0),
                intent.getBooleanExtra(EXTRA_REPLAY_PAUSED, false),
                intent.getDoubleExtra(EXTRA_REPLAY_SPEED, 1.0),
                intent.getBooleanExtra(EXTRA_REPLAY_REPEAT, true),
            )
        }
        val scenario = intent.getStringExtra(EXTRA_SCRIPTED)
        val apiEnabled = intent.getBooleanExtra(EXTRA_SCRIPTED_API, false)
        if (scenario == null && !apiEnabled) return null
        val json = scenario?.let { context.assets.open("scenarios/$it.json").bufferedReader().use { it.readText() } }
            ?: """{"name":"api","events":[{"at_ms":0,"type":"connected"},{"at_ms":0,"type":"weight","grams":0}]}"""
        val source = ScriptedScaleSource(
            scope, json, intent.getDoubleExtra("script_speed", 1.0),
            intent.getBooleanExtra("script_paused", apiEnabled),
        )
        if (apiEnabled) ScriptedScaleHttpServer.install(source)
        return source
    }

    override fun onActivityCreate(container: AppContainer) {
        val intent = intent ?: return
        if (fresh && intent.getBooleanExtra(EXTRA_BENCHMARK_ONBOARDED, false)) container.settings.onboardingDone = true
        if (fresh && intent.getBooleanExtra(EXTRA_BENCHMARK_SEED_BREWS, false)) BenchmarkFixtures.seed(container)
    }

    companion object {
        const val EXTRA_REPLAY = "replay"
        const val EXTRA_REPLAY_AT_MS = "replay_at_ms"
        const val EXTRA_REPLAY_PAUSED = "replay_paused"
        const val EXTRA_REPLAY_SPEED = "replay_speed"
        const val EXTRA_REPLAY_REPEAT = "replay_repeat"
        const val EXTRA_SCRIPTED = "scripted"
        const val EXTRA_SCRIPTED_API = "scripted_api"
        const val EXTRA_BENCHMARK_ONBOARDED = "benchmark_onboarded"
        const val EXTRA_BENCHMARK_SEED_BREWS = "benchmark_seed_brews"
    }
}
