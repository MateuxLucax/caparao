package io.github.mateuxlucax.caparao.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorder
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticRecorderState
import io.github.mateuxlucax.caparao.data.diagnostics.DiagnosticShare
import io.github.mateuxlucax.caparao.data.export.ExportedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val recorder: DiagnosticRecorderState = DiagnosticRecorderState.Idle,
    val starting: Boolean = false,
    val startFailed: Boolean = false,
    val stopFailed: Boolean = false,
    val shareFile: ExportedFile? = null,
    val shareFailed: Boolean = false,
)

/** Starts, stops, and explicitly shares the app's bounded local diagnostic capture. */
class DiagnosticsViewModel(
    private val recorder: DiagnosticRecorder,
    private val diagnosticShare: DiagnosticShare,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiagnosticsUiState())
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            recorder.refresh()
            recorder.state.collect { state -> _uiState.update { it.copy(recorder = state, starting = state is DiagnosticRecorderState.Starting) } }
        }
    }

    fun start() {
        if (_uiState.value.starting || _uiState.value.recorder is DiagnosticRecorderState.Recording || _uiState.value.recorder is DiagnosticRecorderState.Starting) return
        _uiState.update { it.copy(starting = true, startFailed = false, stopFailed = false, shareFile = null, shareFailed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!recorder.start()) _uiState.update { it.copy(starting = false, startFailed = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(starting = false, startFailed = true) }
            }
        }
    }

    fun stop() {
        if (_uiState.value.recorder !is DiagnosticRecorderState.Recording) return
        _uiState.update { it.copy(stopFailed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recorder.stop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(stopFailed = true) }
            }
        }
    }

    fun share() {
        val file = runCatching { diagnosticShare.latest() }.getOrNull()
        _uiState.update { it.copy(shareFile = file, shareFailed = file == null) }
    }

    fun shared() = _uiState.update { it.copy(shareFile = null) }
}
