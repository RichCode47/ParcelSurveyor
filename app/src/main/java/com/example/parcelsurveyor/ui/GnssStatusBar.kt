package com.example.parcelsurveyor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parcelsurveyor.gnss.BluetoothDeviceInfo
import com.example.parcelsurveyor.gnss.ConnectionState
import com.example.parcelsurveyor.gnss.GnssFixQuality
import com.example.parcelsurveyor.gnss.GnssPosition

/**
 * Composable chip displaying real-time GNSS fix quality, satellite count, and Bluetooth connection status.
 *
 * @property connectionState Current Bluetooth connection state ([ConnectionState]).
 * @property position Current [GnssPosition] update containing fix quality and satellite count.
 * @property onClick Callback invoked when the chip is clicked to open device settings.
 * @property modifier Modifier for styling and sizing the chip.
 */
@Composable
fun CompactGnssChip(
    connectionState: ConnectionState,
    position: GnssPosition?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fixQuality = position?.fixQuality ?: GnssFixQuality.NO_FIX
    val badgeColor = parseHexColor(fixQuality.colorHex)

    val icon = when (connectionState) {
        ConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
        ConnectionState.CONNECTING -> Icons.Default.BluetoothSearching
        else -> Icons.Default.Bluetooth
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = badgeColor,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "GNSS Bluetooth",
                tint = Color.White,
                modifier = Modifier.padding(end = 2.dp)
            )
            Text(
                text = fixQuality.displayName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
            if (position != null && position.fixQuality != GnssFixQuality.NO_FIX) {
                Text(
                    text = "(${position.satellitesCount}s)",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * AlertDialog composable for discovering, selecting, and connecting to external Bluetooth RTK GNSS receivers.
 *
 * @property devices List of paired [BluetoothDeviceInfo] devices available.
 * @property connectionState Current Bluetooth connection state.
 * @property connectedDevice Currently connected [BluetoothDeviceInfo], if any.
 * @property onSelectDevice Callback invoked when a device is selected for connection.
 * @property onDisconnect Callback invoked when disconnecting the active receiver.
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun BluetoothDeviceDialog(
    devices: List<BluetoothDeviceInfo>,
    connectionState: ConnectionState,
    connectedDevice: BluetoothDeviceInfo?,
    onSelectDevice: (BluetoothDeviceInfo) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select External GNSS Receiver") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connectedDevice != null) {
                    Text(
                        text = "Connected: ${connectedDevice.name} (${connectedDevice.address})",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50)
                    )
                    TextButton(onClick = onDisconnect) {
                        Text("Disconnect Receiver", color = MaterialTheme.colorScheme.error)
                    }
                }

                Text("Paired Bluetooth Devices:", fontWeight = FontWeight.SemiBold)

                if (devices.isEmpty()) {
                    Text(
                        "No paired Bluetooth devices found. Pair your RTK GNSS receiver in Android Bluetooth settings first.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(devices) { dev ->
                            val isSelected = dev.address == connectedDevice?.address
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectDevice(dev) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(dev.name, fontWeight = FontWeight.Medium)
                                    Text(dev.address, style = MaterialTheme.typography.bodySmall)
                                }
                                if (isSelected) {
                                    Text("Connected", color = Color(0xFF4CAF50), fontSize = 12.sp)
                                }
                            }
                        }
                    }
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

/**
 * Parses a hex color code string into a Compose [Color].
 *
 * @property hex Hex color code string (e.g., "#4CAF50").
 * @return The parsed Compose [Color], or [Color.Gray] if parsing fails.
 */
private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray
    }
}
