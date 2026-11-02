package io.github.mateuxlucax.caparao.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.export.BrewExportFiles
import io.github.mateuxlucax.caparao.data.export.ExportedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExportUiState(
    val working: Boolean = false,
    /** The file to hand to the share sheet, once. */
    val file: ExportedFile? = null,
    val failed: Boolean = false,
)

/** "Export" in Settings: every brew in a zip, built off the main thread (#76). */
class ExportViewModel(private val export: BrewExportFiles) : ViewModel() {
    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    fun export(includeSamples: Boolean) {
        if (_uiState.value.working) return
        _uiState.value = ExportUiState(working = true)
        viewModelScope.launch {
            _uiState.value = try {
                ExportUiState(file = export.all(includeSamples))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                ExportUiState(failed = true)
            }
        }
    }

    /** The chooser opened; clear the one-shot file. */
    fun shared() = _uiState.update { it.copy(file = null) }
}
