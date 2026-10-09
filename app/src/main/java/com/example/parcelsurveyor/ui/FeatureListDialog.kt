package com.example.parcelsurveyor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.parcelsurveyor.data.LatLngPoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * Data class representing a searchable feature list item in the browser dialog.
 *
 * @property layerType The [FeatureLayerType] of the feature.
 * @property record The underlying [FeatureRecord].
 * @property centerPoint Representative geographic coordinate for zooming to the feature.
 * @property searchText Combined search text for filtering.
 */
data class FeatureListItem(
    val layerType: FeatureLayerType,
    val record: FeatureRecord,
    val centerPoint: LatLngPoint,
    val searchText: String
)

/**
 * Composable dialog providing a searchable list of all saved survey features across all layers,
 * displaying synchronization status badges and allowing tap-to-zoom map navigation.
 *
 * @property savedFeatures List of saved feature records grouped by layer type.
 * @property onZoomToFeature Callback invoked with the [LatLngPoint] to center/zoom the map on.
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun FeatureListDialog(
    savedFeatures: List<Pair<FeatureLayerType, List<FeatureRecord>>>,
    onZoomToFeature: (LatLngPoint) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val allItems = remember(savedFeatures) {
        val list = mutableListOf<FeatureListItem>()
        for ((layerType, records) in savedFeatures) {
            for (rec in records) {
                try {
                    val centerPt = if (layerType.isPoint) {
                        val obj = JSONObject(rec.geometryJson)
                        LatLngPoint(obj.getDouble("lat"), obj.getDouble("lng"))
                    } else {
                        val arr = JSONArray(rec.geometryJson)
                        if (arr.length() > 0) {
                            val first = arr.getJSONObject(0)
                            LatLngPoint(first.getDouble("lat"), first.getDouble("lng"))
                        } else {
                            LatLngPoint(0.0, 0.0)
                        }
                    }
                    val searchStr = "${layerType.displayName} ${rec.notes} ${rec.globalId}".lowercase()
                    list.add(FeatureListItem(layerType, rec, centerPt, searchStr))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        list
    }

    val filteredItems = remember(allItems, searchQuery) {
        if (searchQuery.isBlank()) {
            allItems
        } else {
            allItems.filter { it.searchText.contains(searchQuery.lowercase()) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved Survey Features (${allItems.size})", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search features...") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (filteredItems.isEmpty()) {
                    Text(
                        text = "No features found.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredItems) { item ->
                            val isSynced = item.record.syncStatus == 0
                            Surface(
                                onClick = {
                                    onZoomToFeature(item.centerPoint)
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${item.layerType.displayName}: ${item.record.notes}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "ID: ${item.record.globalId.take(12)}...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.Gray
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (isSynced) Color(0xFF4CAF50) else Color(0xFFFFC107)
                                    ) {
                                        Text(
                                            text = if (isSynced) "Synced" else "Local",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
