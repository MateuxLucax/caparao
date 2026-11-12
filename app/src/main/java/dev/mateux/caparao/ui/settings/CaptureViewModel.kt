package dev.mateux.caparao.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mateux.caparao.data.diagnostics.DiagnosticCapture
import dev.mateux.caparao.data.diagnostics.CaptureWriterState
import dev.mateux.caparao.data.local.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureUiState(
    val capture: CaptureWriterState = CaptureWriterState.Idle,
    val starting: Boolean = false,
    val startFailed: Boolean = false,
    val stopFailed: Boolean = false,
    val optedIn: Boolean = false,
)

/** Controls opt-in diagnostics and the bounded brew capture that is uploaded to Sentry. */
class CaptureViewModel(
    private val capture: DiagnosticCapture,
    private val settings: SettingsStore,
    val available: Boolean = true,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CaptureUiState(optedIn = settings.remoteDiagnostics))
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            capture.refresh()
            capture.state.collect { state -> _uiState.update { it.copy(capture = state, starting = state is CaptureWriterState.Starting) } }
        }
        viewModelScope.launch {
            settings.remoteDiagnosticsChanges.collect { enabled -> _uiState.update { it.copy(optedIn = enabled) } }
        }
    }

    fun setOptedIn(enabled: Boolean) { settings.remoteDiagnostics = enabled }

    fun start() {
        if (_uiState.value.starting || _uiState.value.capture is CaptureWriterState.Recording || _uiState.value.capture is CaptureWriterState.Starting) return
        _uiState.update { it.copy(starting = true, startFailed = false, stopFailed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!capture.start()) _uiState.update { it.copy(starting = false, startFailed = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(starting = false, startFailed = true) }
            }
        }
    }

    fun stop() {
        if (_uiState.value.capture !is CaptureWriterState.Recording) return
        _uiState.update { it.copy(stopFailed = false) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                capture.stop()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(stopFailed = true) }
            }
        }
    }

}
