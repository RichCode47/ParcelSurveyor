package com.example.parcelsurveyor.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parcelsurveyor.data.LatLngPoint
import com.example.parcelsurveyor.util.GisGeometryUtils
import com.example.parcelsurveyor.util.TraverseEngine
import com.example.parcelsurveyor.util.TraverseLeg
import com.example.parcelsurveyor.util.TraverseResult
import java.util.Locale

/**
 * Composable dialog providing automated traverse closure calculations, relative precision ratio analysis,
 * polygon area computations, and Bowditch (Compass Rule) coordinate balancing.
 *
 * @property draftPoints List of map vertex coordinates currently drawn in active draft mode.
 * @property onApplyBalancedShape Callback invoked when user applies Bowditch balanced coordinates to the map.
 * @property onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun TraverseCalculatorDialog(
    draftPoints: List<LatLngPoint> = emptyList(),
    onApplyBalancedShape: (List<LatLngPoint>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(if (draftPoints.size >= 3) 0 else 1) }

    // Manual legs state
    var startLatText by remember { mutableStateOf("-15.4167") }
    var startLngText by remember { mutableStateOf("28.2833") }

    val manualLegs = remember {
        mutableStateListOf(
            TraverseLeg("Leg 1", 100.0, 90.0),
            TraverseLeg("Leg 2", 100.0, 0.0),
            TraverseLeg("Leg 3", 100.0, 270.0),
            TraverseLeg("Leg 4", 100.0, 180.0)
        )
    }

    // Computed traverse result
    val currentResult: TraverseResult? = remember(selectedTabIndex, draftPoints, manualLegs, startLatText, startLngText) {
        if (selectedTabIndex == 0 && draftPoints.size >= 3) {
            TraverseEngine.computeTraverseFromPoints(draftPoints)
        } else if (selectedTabIndex == 1 && manualLegs.isNotEmpty()) {
            val startLat = startLatText.toDoubleOrNull() ?: -15.4167
            val startLng = startLngText.toDoubleOrNull() ?: 28.2833
            TraverseEngine.computeTraverse(LatLngPoint(startLat, startLng), manualLegs)
        } else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Traverse & Area Engine", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TabRow(selectedTabIndex = selectedTabIndex) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Map Draft (${draftPoints.size} pts)") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Manual Legs") }
                    )
                }

                if (selectedTabIndex == 0) {
                    if (draftPoints.size < 3) {
                        Text(
                            text = "Draw at least 3 points on the map to calculate traverse closure.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else if (currentResult != null) {
                        TraverseReportCard(result = currentResult)
                    }
                } else {
                    // Manual legs input mode
                    Text("Starting Coordinate", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = startLatText,
                            onValueChange = { startLatText = it },
                            label = { Text("Start Lat") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = startLngText,
                            onValueChange = { startLngText = it },
                            label = { Text("Start Lng") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Traverse Legs (${manualLegs.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        TextButton(
                            onClick = {
                                val nextNum = manualLegs.size + 1
                                manualLegs.add(TraverseLeg("Leg $nextNum", 50.0, 90.0))
                            }
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Leg")
                        }
                    }

                    manualLegs.forEachIndexed { index, leg ->
                        var distText by remember(leg) { mutableStateOf(leg.distanceMeters.toString()) }
                        var bearingText by remember(leg) { mutableStateOf(leg.bearingDegrees.toString()) }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(leg.label, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.width(50.dp))
                                OutlinedTextField(
                                    value = distText,
                                    onValueChange = {
                                        distText = it
                                        val d = it.toDoubleOrNull() ?: leg.distanceMeters
                                        manualLegs[index] = leg.copy(distanceMeters = d)
                                    },
                                    label = { Text("Dist (m)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = bearingText,
                                    onValueChange = {
                                        bearingText = it
                                        val b = it.toDoubleOrNull() ?: leg.bearingDegrees
                                        manualLegs[index] = leg.copy(bearingDegrees = b)
                                    },
                                    label = { Text("Bearing (°)") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                IconButton(
                                    onClick = { if (manualLegs.size > 1) manualLegs.removeAt(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    if (currentResult != null) {
                        TraverseReportCard(result = currentResult)
                    }
                }
            }
        },
        confirmButton = {
            if (currentResult != null && selectedTabIndex == 0) {
                Button(
                    onClick = {
                        onApplyBalancedShape(currentResult.adjustedLatLngPoints)
                        onDismiss()
                    }
                ) {
                    Icon(imageVector = Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Apply Bowditch Balancing")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Composable report card summarizing traverse closure stats and area calculations.
 */
@Composable
private fun TraverseReportCard(result: TraverseResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Traverse Summary", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Perimeter:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(GisGeometryUtils.formatDistanceString(result.totalPerimeterMeters), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Linear Misclosure (Ec):", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    String.format(Locale.US, "%.3f m (Azimuth: %.1f°)", result.linearMisclosureMeters, result.misclosureAzimuthDegrees),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.linearMisclosureMeters < 0.1) Color(0xFF2E7D32) else Color(0xFFC62828)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Precision Ratio:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    result.precisionRatioString,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("Area Computations", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Unadjusted Area:", fontSize = 11.sp)
                Text(GisGeometryUtils.formatAreaSummary(result.unadjustedAreaSqMeters), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Bowditch Adjusted Area:", fontSize = 11.sp)
                Text(GisGeometryUtils.formatAreaSummary(result.adjustedAreaSqMeters), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1B5E20))
            }
        }
    }
}
