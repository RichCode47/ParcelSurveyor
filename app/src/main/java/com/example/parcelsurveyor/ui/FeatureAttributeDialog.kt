package com.example.parcelsurveyor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.parcelsurveyor.data.FeatureLayerType
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Composable dialog for collecting dynamic field attributes (description, condition, date, and feature type)
 * prior to saving a survey feature.
 *
 * @property layerType The active [FeatureLayerType] being collected.
 * @property onSave Callback invoked with the serialized JSON attributes string.
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun FeatureAttributeDialog(
    layerType: FeatureLayerType,
    onSave: (attributesJson: String) -> Unit,
    onDismiss: () -> Unit
) {
    var description by remember { mutableStateOf("Field survey capture") }
    var condition by remember { mutableStateOf("Good") }
    var currentDate by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
    }
    var featureType by remember {
        mutableStateOf(if (layerType == FeatureLayerType.DETAIL_LINE) "Fence" else "Boundary")
    }

    var showTypeDropdown by remember { mutableStateOf(false) }
    var showConditionDropdown by remember { mutableStateOf(false) }

    val typeOptions = when (layerType) {
        FeatureLayerType.DETAIL_LINE -> listOf("Fence", "Road Edge", "Drainage", "Power Line", "Other")
        FeatureLayerType.DETAIL_POLYGON -> listOf("Land Parcel", "Building", "Water Body", "Vegetation", "Other")
        else -> listOf("Boundary Marker", "Control Point", "Utility Pole", "Tree", "Other")
    }

    val conditionOptions = listOf("Excellent", "Good", "Fair", "Poor", "Damaged")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attributes: ${layerType.displayName}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Feature Type Picker
                Column {
                    Text("Feature Type / Category", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { showTypeDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(featureType)
                    }
                    DropdownMenu(
                        expanded = showTypeDropdown,
                        onDismissRequest = { showTypeDropdown = false }
                    ) {
                        typeOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    featureType = option
                                    showTypeDropdown = false
                                }
                            )
                        }
                    }
                }

                // Condition Picker
                Column {
                    Text("Condition", fontWeight = FontWeight.SemiBold)
                    OutlinedButton(
                        onClick = { showConditionDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(condition)
                    }
                    DropdownMenu(
                        expanded = showConditionDropdown,
                        onDismissRequest = { showConditionDropdown = false }
                    ) {
                        conditionOptions.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt) },
                                onClick = {
                                    condition = opt
                                    showConditionDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = currentDate,
                    onValueChange = { currentDate = it },
                    label = { Text("Survey Timestamp") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val jsonObj = JSONObject().apply {
                        put("Description", description)
                        put("FeatureType", featureType)
                        put("Condition", condition)
                        put("SurveyDate", currentDate)
                    }
                    onSave(jsonObj.toString())
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
}
