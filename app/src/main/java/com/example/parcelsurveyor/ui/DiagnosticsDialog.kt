package com.example.parcelsurveyor.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parcelsurveyor.gnss.ConnectionState
import com.example.parcelsurveyor.gnss.GnssPosition
import java.util.Locale

/**
 * Composable dialog displaying live GNSS diagnostics (receiver connection state, fix quality,
 * HDOP, satellite count, coordinates, and diagnostics bundle sharing).
 *
 * @property connectionState Current Bluetooth connection state.
 * @property currentPosition Current [GnssPosition] update.
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun DiagnosticsDialog(
    connectionState: ConnectionState,
    currentPosition: GnssPosition?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val diagnosticsText = buildString {
        append("--- PARCEL SURVEYOR GNSS DIAGNOSTICS ---\n")
        append("Connection State: $connectionState\n")
        if (currentPosition != null) {
            append("Source: ${if (currentPosition.isBluetooth) "External Bluetooth RTK" else "Internal GPS Fallback"}\n")
            append("Fix Quality: ${currentPosition.fixQuality.displayName} (Code ${currentPosition.fixQuality.code})\n")
            append("Latitude: ${currentPosition.latitude}\n")
            append("Longitude: ${currentPosition.longitude}\n")
            append("Altitude: ${currentPosition.altitude} m\n")
            append("Satellites: ${currentPosition.satellitesCount}\n")
            append("HDOP: ${currentPosition.hdop}\n")
            append("Timestamp: ${currentPosition.timestamp}\n")
        } else {
            append("Position: No fix available yet.\n")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GNSS Live Diagnostics", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = diagnosticsText,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Button(
                    onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, diagnosticsText)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Diagnostics Bundle")
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Share Diagnostic Bundle")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
