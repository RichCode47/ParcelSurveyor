package com.example.parcelsurveyor.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.FeatureRecord
import com.example.parcelsurveyor.gnss.GnssPosition
import com.example.parcelsurveyor.util.PhotoManager
import org.json.JSONObject
import java.io.File

/**
 * Composable dialog for viewing/editing an existing feature's notes and custom attributes,
 * managing photo attachments, updating its geometry via RTK GNSS, or deleting the feature.
 *
 * @property layerType The [FeatureLayerType] of the feature being edited.
 * @property feature The [FeatureRecord] being edited.
 * @property initialPhotos List of existing photo attachment file paths linked to this feature.
 * @property currentGnssPosition Optional current [GnssPosition] for re-capturing position.
 * @property onSave Callback invoked with updated notes/attributes JSON string, optional new geometry JSON, and newly added photo paths.
 * @property onDelete Callback invoked when the user confirms feature deletion.
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun FeatureEditDialog(
    layerType: FeatureLayerType,
    feature: FeatureRecord,
    initialPhotos: List<String> = emptyList(),
    currentGnssPosition: GnssPosition?,
    onSave: (newNotesJson: String, newGeometryJson: String?, newPhotoPaths: List<String>) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Parse existing feature notes/attributes JSON if possible
    val parsedAttributes = remember(feature.notes) {
        val map = mutableStateMapOf<String, String>()
        try {
            val jsonObj = JSONObject(feature.notes)
            val keys = jsonObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObj.optString(key, "")
            }
        } catch (e: Exception) {
            // Fallback for plain text notes
            map["Description"] = feature.notes
        }
        map
    }

    var updatedGeometryJson by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Photos state
    val existingPhotos = remember { mutableStateListOf<String>().apply { addAll(initialPhotos) } }
    val newPhotoPaths = remember { mutableStateListOf<String>() }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoFile != null) {
            newPhotoPaths.add(tempPhotoFile!!.absolutePath)
        }
    }

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "GlobalID: ${feature.globalId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                // Editable attributes list
                Text("Attributes", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)

                parsedAttributes.keys.forEach { key ->
                    OutlinedTextField(
                        value = parsedAttributes[key] ?: "",
                        onValueChange = { newValue -> parsedAttributes[key] = newValue },
                        label = { Text(key) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // RTK GNSS position update section for point features
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

                // Photo Attachments
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val allPhotos = existingPhotos + newPhotoPaths
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photos (${allPhotos.size})",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        OutlinedButton(
                            onClick = {
                                val photoFile = PhotoManager.createPhotoFile(context)
                                tempPhotoFile = photoFile
                                val uri = PhotoManager.getPhotoUri(context, photoFile)
                                cameraLauncher.launch(uri)
                            }
                        ) {
                            Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Photo")
                        }
                    }

                    if (allPhotos.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            items(allPhotos) { path ->
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                                ) {
                                    val bitmap = remember(path) {
                                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Attachment preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.matchParentSize()
                                        )
                                    }
                                    if (newPhotoPaths.contains(path)) {
                                        IconButton(
                                            onClick = { newPhotoPaths.remove(path) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(22.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
                    val jsonObj = JSONObject()
                    for ((k, v) in parsedAttributes) {
                        jsonObj.put(k, v)
                    }
                    onSave(jsonObj.toString(), updatedGeometryJson, newPhotoPaths.toList())
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
