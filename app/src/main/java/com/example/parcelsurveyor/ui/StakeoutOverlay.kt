package com.example.parcelsurveyor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.FeatureRecord
import com.example.parcelsurveyor.data.LatLngPoint
import com.example.parcelsurveyor.util.GisGeometryUtils
import com.example.parcelsurveyor.util.StakeoutInfo
import org.json.JSONObject
import java.util.Locale

/**
 * Data class representing a selectable target item for stakeout navigation.
 *
 * @property point Geographic coordinates ([LatLngPoint]) of the target.
 * @property name Human-readable name or label of the target.
 */
data class StakeoutTargetItem(
    val point: LatLngPoint,
    val name: String
)

/**
 * Composable card displaying real-time stakeout navigation guidance (distance, bearing,
 * and Easting/Northing offsets) relative to a selected target beacon.
 *
 * @property stakeoutInfo Calculated [StakeoutInfo] containing navigation metrics.
 * @property onStopStakeout Callback invoked when exiting stakeout navigation mode.
 * @property modifier Modifier for styling and sizing the card.
 */
@Composable
fun StakeoutCard(
    stakeoutInfo: StakeoutInfo,
    onStopStakeout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(28.dp)
                            .rotate(stakeoutInfo.bearingDegrees.toFloat())
                    )
                    Column {
                        Text(
                            text = "Target: ${stakeoutInfo.targetName}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Dist: ${GisGeometryUtils.formatDistanceString(stakeoutInfo.distanceMeters)} | Azimuth: ${String.format(Locale.US, "%.0f°", stakeoutInfo.bearingDegrees)}",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp
                        )
                    }
                }

                IconButton(onClick = onStopStakeout) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Stakeout")
                }
            }

            Text(
                text = String.format(
                    Locale.US,
                    "Offsets: dE: %+.2f m (%s) | dN: %+.2f m (%s)",
                    stakeoutInfo.deltaEastingMeters,
                    if (stakeoutInfo.deltaEastingMeters >= 0) "East" else "West",
                    stakeoutInfo.deltaNorthingMeters,
                    if (stakeoutInfo.deltaNorthingMeters >= 0) "North" else "South"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * AlertDialog composable allowing users to select a saved point feature as a stakeout navigation target.
 *
 * @property savedFeatures List of saved feature records grouped by layer type.
 * @property onSelectTarget Callback invoked when a target point is selected ([LatLngPoint] and name).
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun StakeoutTargetDialog(
    savedFeatures: List<Pair<FeatureLayerType, List<FeatureRecord>>>,
    onSelectTarget: (LatLngPoint, String) -> Unit,
    onDismiss: () -> Unit
) {
    val targetItems = mutableListOf<StakeoutTargetItem>()
    for ((layerType, records) in savedFeatures.filter { it.first.isPoint }) {
        for (rec in records) {
            try {
                val obj = JSONObject(rec.geometryJson)
                val pt = LatLngPoint(obj.getDouble("lat"), obj.getDouble("lng"))
                val name = "${layerType.displayName} (${rec.notes})"
                targetItems.add(StakeoutTargetItem(pt, name))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Target Beacon for Stakeout", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (targetItems.isEmpty()) {
                    Text("No saved point beacons found. Collect points first to enable stakeout navigation.")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(targetItems) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectTarget(item.point, item.name) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(item.name, fontWeight = FontWeight.Medium)
                                    Text("Lat: ${item.point.latitude}, Lng: ${item.point.longitude}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
