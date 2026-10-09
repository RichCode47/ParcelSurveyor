package com.example.parcelsurveyor.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.example.parcelsurveyor.data.AttributeFieldSchema
import com.example.parcelsurveyor.data.AttributeFieldType
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.LayerSchemaRepository
import com.example.parcelsurveyor.util.PhotoManager
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composable dialog for collecting dynamic field attributes based on layer schema definitions,
 * supporting dynamic custom fields and photo attachment capture.
 *
 * @property layerType The active [FeatureLayerType] being collected.
 * @property onSave Callback invoked with the serialized JSON attributes string and photo file paths.
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun FeatureAttributeDialog(
    layerType: FeatureLayerType,
    onSave: (attributesJson: String, photoPaths: List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val schema = remember(layerType) { LayerSchemaRepository.getSchemaForLayer(layerType) }

    // State map storing attribute key -> value
    val attributeValues = remember {
        mutableStateMapOf<String, String>().apply {
            schema.forEach { field ->
                put(
                    field.key,
                    if (field.fieldType == AttributeFieldType.DATE) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                    } else {
                        field.defaultValue
                    }
                )
            }
        }
    }

    // List of additional custom field definitions added dynamically by the user
    val customFields = remember { mutableStateListOf<AttributeFieldSchema>() }
    var showAddCustomFieldDialog by remember { mutableStateOf(false) }

    // List of captured photo file paths
    val capturedPhotoPaths = remember { mutableStateListOf<String>() }
    var tempPhotoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoFile != null) {
            capturedPhotoPaths.add(tempPhotoFile!!.absolutePath)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Attributes: ${layerType.displayName}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Render predefined schema fields
                (schema + customFields).forEach { field ->
                    RenderAttributeField(
                        schema = field,
                        value = attributeValues[field.key] ?: "",
                        onValueChange = { newValue ->
                            attributeValues[field.key] = newValue
                        }
                    )
                }

                // Section to add dynamic custom fields
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Custom Fields",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    TextButton(onClick = { showAddCustomFieldDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Field")
                    }
                }

                // Photo attachments section
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Photo Attachments (${capturedPhotoPaths.size})",
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take Photo")
                        }
                    }

                    if (capturedPhotoPaths.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            items(capturedPhotoPaths) { path ->
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
                                    IconButton(
                                        onClick = { capturedPhotoPaths.remove(path) },
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val jsonObj = JSONObject()
                    for ((k, v) in attributeValues) {
                        jsonObj.put(k, v)
                    }
                    onSave(jsonObj.toString(), capturedPhotoPaths.toList())
                }
            ) {
                Text("Save Feature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    // Dialog for adding dynamic custom fields
    if (showAddCustomFieldDialog) {
        AddCustomFieldDialog(
            onAddField = { newField ->
                customFields.add(newField)
                attributeValues[newField.key] = newField.defaultValue
                showAddCustomFieldDialog = false
            },
            onDismiss = { showAddCustomFieldDialog = false }
        )
    }
}

/**
 * Helper composable rendering an individual attribute field control based on its [AttributeFieldType].
 */
@Composable
private fun RenderAttributeField(
    schema: AttributeFieldSchema,
    value: String,
    onValueChange: (String) -> Unit
) {
    when (schema.fieldType) {
        AttributeFieldType.TEXT, AttributeFieldType.NUMERIC, AttributeFieldType.DATE -> {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(schema.label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = schema.fieldType != AttributeFieldType.TEXT
            )
        }

        AttributeFieldType.PICKLIST -> {
            var expanded by remember { mutableStateOf(false) }
            Column {
                Text(schema.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(value.ifEmpty { "Select ${schema.label}" })
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    schema.options.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onValueChange(option)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        AttributeFieldType.CHECKBOX -> {
            val isChecked = value.toBooleanStrictOrNull() ?: false
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onValueChange((!isChecked).toString()) }
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { onValueChange(it.toString()) }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(schema.label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Composable dialog allowing the surveyor to dynamically define an ad-hoc custom attribute field.
 */
@Composable
private fun AddCustomFieldDialog(
    onAddField: (AttributeFieldSchema) -> Unit,
    onDismiss: () -> Unit
) {
    var fieldLabel by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(AttributeFieldType.TEXT) }
    var optionsText by remember { mutableStateOf("") }
    var showTypeMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Field", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fieldLabel,
                    onValueChange = { fieldLabel = it },
                    label = { Text("Field Label / Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text("Field Type", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    OutlinedButton(
                        onClick = { showTypeMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedType.name)
                    }
                    DropdownMenu(
                        expanded = showTypeMenu,
                        onDismissRequest = { showTypeMenu = false }
                    ) {
                        AttributeFieldType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name) },
                                onClick = {
                                    selectedType = type
                                    showTypeMenu = false
                                }
                            )
                        }
                    }
                }

                if (selectedType == AttributeFieldType.PICKLIST) {
                    OutlinedTextField(
                        value = optionsText,
                        onValueChange = { optionsText = it },
                        label = { Text("Options (comma-separated)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = fieldLabel.isNotBlank(),
                onClick = {
                    val key = fieldLabel.replace(" ", "_")
                    val options = if (selectedType == AttributeFieldType.PICKLIST) {
                        optionsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    } else emptyList()

                    onAddField(
                        AttributeFieldSchema(
                            key = key,
                            label = fieldLabel,
                            fieldType = selectedType,
                            options = options
                        )
                    )
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
