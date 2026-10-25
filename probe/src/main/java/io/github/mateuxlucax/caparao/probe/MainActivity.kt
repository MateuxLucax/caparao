package io.github.mateuxlucax.caparao.probe

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Single throwaway screen: connection controls, live values, session recording and a tail of the raw frames. */
class MainActivity : ComponentActivity() {
    private lateinit var session: SessionLog
    private lateinit var client: DotClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionLog(this)
        client = DotClient(this, session)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { ProbeScreen(client, session) }
            }
        }
    }

    override fun onDestroy() {
        client.release()
        super.onDestroy()
    }
}

private val PERMISSIONS = arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

@Composable
private fun ProbeScreen(client: DotClient, session: SessionLog) {
    val context = LocalView.current.context
    var label by remember { mutableStateOf("espresso 18g") }
    var recording by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf("") }

    // Keep the screen on only while recording, so a 5 minute idle capture is not cut short by the screen timeout.
    val view = LocalView.current
    androidx.compose.runtime.DisposableEffect(recording) {
        view.keepScreenOn = recording
        onDispose { view.keepScreenOn = false }
    }

    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) client.connect() else client.mark("permissions_denied")
    }
    fun connect() {
        val ok = PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        if (ok) client.connect() else askPermissions.launch(PERMISSIONS)
    }

    Column(
        Modifier.safeDrawingPadding().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("State: ${client.state}", style = MaterialTheme.typography.titleMedium)
        Text("Weight: ${client.weight?.let { "%.1f g".format(it) } ?: "-"}", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Flow: ${client.flow?.let { "%.1f g/s".format(it) } ?: "-"}   Timer: ${client.timer?.let { "$it s" } ?: "-"}   " +
                "Battery: ${client.battery?.let { "$it %" } ?: "-"}",
        )

        Buttons {
            Button(onClick = ::connect) { Text("Connect") }
            OutlinedButton(onClick = client::disconnect) { Text("Disconnect") }
        }
        Buttons {
            Button(onClick = client::tare) { Text("Tare") }
            OutlinedButton(onClick = client::readBattery) { Text("Battery") }
            OutlinedButton(onClick = client::readModel) { Text("Model") }
        }
        Buttons {
            OutlinedButton(onClick = client::timerStart) { Text("Timer start") }
            OutlinedButton(onClick = client::timerPause) { Text("Pause") }
            OutlinedButton(onClick = client::timerReset) { Text("Reset") }
        }

        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Session label") },
            enabled = !recording,
            singleLine = true,
        )
        Buttons {
            if (!recording) {
                Button(onClick = {
                    file = session.start(label)
                    recording = true
                    client.mark("session_start")
                }) { Text("Start session") }
            } else {
                Button(onClick = {
                    client.mark("session_stop")
                    session.stop()
                    recording = false
                }) { Text("Stop session") }
            }
        }
        Text(if (recording) "Recording: $file" else if (file.isNotEmpty()) "Saved: $file" else "Not recording")

        Text(
            client.tail.joinToString("\n"),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 12.sp,
        )
    }
}

@Composable
private fun Buttons(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
