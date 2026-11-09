package io.github.mateuxlucax.caparao

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The package rules of #95: model <- data <- brew <- sync <- ui, only ui uses resources
 * (`R`, `BuildConfig`), and no layer refers to the root package (entry points and wiring). Also keeps a `Context` out
 * of every ViewModel constructor (#328). Checks imports and fully qualified names written in the code.
 */
class LayerDependencyTest {
    private val base = "io.github.mateuxlucax.caparao"

    /** Layer packages (relative to [base]) that each layer must not use. */
    private val forbiddenLayers = mapOf(
        "model" to listOf("data", "brew", "sync", "ui"),
        "data" to listOf("brew", "sync", "ui"),
        "brew" to listOf("sync", "ui"),
        "sync" to listOf("ui"),
        "ui" to emptyList(),
    )

    /** Root package names each layer may still use: the generated resource classes, in `ui` only. */
    private val allowedRoot = mapOf("ui" to setOf("R", "BuildConfig"))

    /** A fully qualified reference to something in the app, e.g. `io.github...caparao.data.Foo`. */
    private val reference = Regex("""\b${Regex.escape(base)}\.([A-Za-z_][\w.]*|\*)""")

    /** A ViewModel class extending `ViewModel` directly or through a `*ViewModel` base, across lines, never across classes. */
    private val viewModelDeclaration = Regex(
        """\bclass\s+(\w+)\s*(?:<[^>]*>)?\s*\(((?:(?!\bclass\b)[\s\S])*?)\)\s*:\s*(?:[\w.]+\.)?[\w.]*ViewModel\s*\(""",
    )

    /** A constructor parameter of type `Context`, however it is spelled. */
    private val contextParameter = Regex("""\bContext\b""")

    @Test
    fun layersOnlyUseLayersBelowThem() {
        val violations = sources().flatMap { (layer, path, lines) ->
            references(lines).filter { violates(layer, it) }.map { "$path uses $base.$it" }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun onlyTheSentryImplementationImportsTheSentrySdk() {
        // #333: everything else reports through `Diagnostics`, so a different backend or an SDK bump touches one file.
        val sentryImport = Regex("""^\s*import\s+io\.sentry""")
        val root = File("src/main/java")
        val importers = root.walk()
            .filter { it.extension == "kt" }
            .filter { file -> file.readLines().any(sentryImport::containsMatchIn) }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toList()

        assertEquals(listOf("io/github/mateuxlucax/caparao/data/diagnostics/SentryDiagnostics.kt"), importers)
    }

    @Test
    fun ruleCatchesUpwardImportsRootImportsAndFullyQualifiedNames() {
        val lines = listOf(
            "package $base.data.scale",
            "import $base.MainActivity",
            "import $base.model.BrewMode",
            "import $base.brew.EspressoDetector",
            "val x = $base.ui.App::class.java",
            "import $base.*",
        )
        val found = references(lines).filter { violates("data", it) }
        assertEquals(listOf("MainActivity", "brew.EspressoDetector", "ui.App", "*"), found)
        assertEquals(listOf("R"), references(listOf("import $base.R")).filter { !violates("ui", it) })
        assertEquals(listOf("R"), references(listOf("import $base.R")).filter { violates("brew", it) })
    }

    @Test
    fun noViewModelTakesAContext() {
        val violations = sources().flatMap { (_, path, lines) ->
            viewModelsTakingContext(lines.joinToString("\n")).map { name -> "$path: $name takes a Context" }
        }
        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun viewModelRuleCatchesAContextParameterInAnyConstructorShape() {
        val source = """
            class TakesContext(private val context: Context, private val repository: BrewRepository) : ViewModel() {
            class TakesNoContext(private val repository: BrewRepository) : ViewModel() {
            class TakesContextCallback(
                private val onShare: (Context) -> Unit,
                private val repository: BrewRepository,
            ) : ViewModel() {
        """.trimIndent()
        assertEquals(listOf("TakesContext", "TakesContextCallback"), viewModelsTakingContext(source))
    }

    /** Names of the classes extending a `*ViewModel` whose primary constructor mentions a `Context`. */
    private fun viewModelsTakingContext(source: String): List<String> =
        viewModelDeclaration.findAll(source).mapNotNull { match ->
            val (name, parameters) = match.destructured
            name.takeIf { contextParameter.containsMatchIn(parameters) }
        }.toList()

    /** (layer, path, lines) of every Kotlin file inside a layer package; root files may use anything. */
    private fun sources(): List<Triple<String, String, List<String>>> {
        // Unit tests run in the module directory.
        val root = File("src/main/java/" + base.replace('.', '/'))
        return root.walk().filter { it.extension == "kt" }.mapNotNull { file ->
            val relative = file.relativeTo(root).path
            val layer = relative.substringBefore(File.separatorChar, missingDelimiterValue = "")
            if (layer.isEmpty()) return@mapNotNull null
            Triple(layer, relative, file.readLines())
        }.toList().also { check(it.isNotEmpty()) { "no sources found under $root" } }
    }

    /** What each line refers to below [base] (e.g. `data.scale.DotProtocol`), ignoring the package line. */
    private fun references(lines: List<String>): List<String> = lines
        .filterNot { it.startsWith("package ") }
        .flatMap { line -> reference.findAll(line).map { it.groupValues[1] }.toList() }

    private fun violates(layer: String, referenced: String): Boolean {
        val first = referenced.substringBefore('.')
        // Packages are lowercase; anything else directly below base (a class, `*`) is in the root package.
        val inRoot = first == "*" || first.first().isUpperCase()
        if (inRoot) return first !in allowedRoot[layer].orEmpty()
        val forbidden = forbiddenLayers[layer] ?: error("unknown layer package '$layer'; add it to the rules")
        return first in forbidden
    }
}
