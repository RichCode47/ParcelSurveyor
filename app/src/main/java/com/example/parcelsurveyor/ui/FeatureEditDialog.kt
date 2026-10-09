package com.example.parcelsurveyor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.FeatureRecord
import com.example.parcelsurveyor.gnss.GnssPosition
import org.json.JSONObject

/**
 * Composable dialog for editing an existing feature's notes, updating its geometry via RTK GNSS,
 * or deleting the feature.
 *
 * @property layerType The [FeatureLayerType] of the feature being edited.
 * @property feature The [FeatureRecord] being edited.
 * @property currentGnssPosition Optional current [GnssPosition] for re-capturing position.
 * @property onSave Callback invoked with updated notes and optional new geometry JSON.
 * @property onDelete Callback invoked when the user confirms feature deletion.
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun FeatureEditDialog(
    layerType: FeatureLayerType,
    feature: FeatureRecord,
    currentGnssPosition: GnssPosition?,
    onSave: (newNotes: String, newGeometryJson: String?) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var notesText by remember { mutableStateOf(feature.notes) }
    var updatedGeometryJson by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Feature", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { Text("Are you sure you want to delete this ${layerType.displayName} feature? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${layerType.displayName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "GlobalID: ${feature.globalId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Feature Notes / Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (layerType.isPoint) {
                    val pos = currentGnssPosition
                    if (pos != null) {
                        OutlinedButton(
                            onClick = {
                                val geomObj = JSONObject().apply {
                                    put("lat", pos.latitude)
                                    put("lng", pos.longitude)
                                }
                                updatedGeometryJson = geomObj.toString()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Update to Current RTK Position")
                        }
                        if (updatedGeometryJson != null) {
                            Text(
                                text = "Position updated to current GNSS fix!",
                                color = Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = "Connect RTK GNSS receiver to update coordinates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Delete Feature")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(notesText, updatedGeometryJson)
                }
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
