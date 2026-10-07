package com.example.parcelsurveyor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.FeatureRecord
import com.example.parcelsurveyor.data.GisDatabaseHelper
import com.example.parcelsurveyor.data.LatLngPoint
import com.example.parcelsurveyor.gnss.BluetoothGnssManager
import com.example.parcelsurveyor.gnss.GnssFixQuality
import com.example.parcelsurveyor.sync.AgolSyncEngine
import com.example.parcelsurveyor.ui.BluetoothDeviceDialog
import com.example.parcelsurveyor.ui.CompactGnssChip
import com.example.parcelsurveyor.ui.GisMapView
import com.example.parcelsurveyor.ui.theme.ParcelSurveyorTheme
import com.example.parcelsurveyor.util.GisDataExporter
import com.example.parcelsurveyor.util.GisGeometryUtils
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: GisDatabaseHelper
    private lateinit var syncEngine: AgolSyncEngine
    private lateinit var gnssManager: BluetoothGnssManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dbHelper = GisDatabaseHelper(this)
        syncEngine = AgolSyncEngine(dbHelper)
        gnssManager = BluetoothGnssManager()

        setContent {
            ParcelSurveyorTheme {
                GisAppScreen(
                    dbHelper = dbHelper,
                    syncEngine = syncEngine,
                    gnssManager = gnssManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        gnssManager.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisAppScreen(
    dbHelper: GisDatabaseHelper,
    syncEngine: AgolSyncEngine,
    gnssManager: BluetoothGnssManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeLayer by remember { mutableStateOf<FeatureLayerType?>(null) }
    var currentShapePoints by remember { mutableStateOf(listOf<LatLngPoint>()) }
    var unsyncedCount by remember { mutableIntStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }
    var savedFeatures by remember { mutableStateOf<List<Pair<FeatureLayerType, List<FeatureRecord>>>>(emptyList()) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showLayerPickerModal by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var followGnssLocation by remember { mutableStateOf(true) }

    val connectionState by gnssManager.connectionState.collectAsState()
    val connectedDevice by gnssManager.connectedDevice.collectAsState()
    val gnssPosition by gnssManager.currentPosition.collectAsState()

    val agolServiceUrl = "https://servicesX.arcgis.com/YOUR_ORG/arcgis/rest/services/YOUR_SVC/FeatureServer"

    fun refreshData() {
        unsyncedCount = dbHelper.getUnsyncedCount()
        savedFeatures = FeatureLayerType.ALL_LAYERS.map { layer ->
            Pair(layer, dbHelper.getAllFeatures(layer.tableName))
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    fun saveFeature(geometryJson: String) {
        val targetLayer = activeLayer ?: FeatureLayerType.DETAIL_POINT
        dbHelper.insertFeature(targetLayer.tableName, geometryJson, "Field capture")
        currentShapePoints = emptyList()
        refreshData()
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Saved to ${targetLayer.displayName}")
        }
    }

    if (showBluetoothDialog) {
        val pairedDevices = remember { gnssManager.getPairedDevices(context) }
        BluetoothDeviceDialog(
            devices = pairedDevices,
            connectionState = connectionState,
            connectedDevice = connectedDevice,
            onSelectDevice = { dev ->
                gnssManager.connectToDevice(dev, coroutineScope)
                showBluetoothDialog = false
            },
            onDisconnect = {
                gnssManager.disconnect()
                showBluetoothDialog = false
            },
            onDismiss = { showBluetoothDialog = false }
        )
    }

    // Export Format Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Survey Data", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureLayerOption(
                        title = "CSV File (.csv)",
                        description = "Coordinates list table with Point ID, Lat, Lng",
                        icon = Icons.Default.Share,
                        onClick = {
                            showExportDialog = false
                            val file = GisDataExporter.exportToCsv(context, savedFeatures)
                            GisDataExporter.shareExportedFile(context, file, "text/csv")
                        }
                    )
                    FeatureLayerOption(
                        title = "Google Earth KML (.kml)",
                        description = "Open points, lines, polygons in Google Earth",
                        icon = Icons.Default.Share,
                        onClick = {
                            showExportDialog = false
                            val file = GisDataExporter.exportToKml(context, savedFeatures)
                            GisDataExporter.shareExportedFile(context, file, "application/vnd.google-earth.kml+xml")
                        }
                    )
                    FeatureLayerOption(
                        title = "GeoJSON File (.geojson)",
                        description = "Standard GIS spatial format for QGIS / ArcGIS Pro",
                        icon = Icons.Default.Share,
                        onClick = {
                            showExportDialog = false
                            val file = GisDataExporter.exportToGeoJson(context, savedFeatures)
                            GisDataExporter.shareExportedFile(context, file, "application/geo+json")
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Layer Picker Modal on tapping '+'
    if (showLayerPickerModal) {
        AlertDialog(
            onDismissRequest = { showLayerPickerModal = false },
            title = { Text("Select Feature Layer to Collect", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeatureLayerOption(
                        title = "DetailPoint",
                        description = "Collect single detail points",
                        icon = Icons.Default.LocationOn,
                        onClick = {
                            activeLayer = FeatureLayerType.DETAIL_POINT
                            currentShapePoints = emptyList()
                            showLayerPickerModal = false
                        }
                    )
                    FeatureLayerOption(
                        title = "ParcelPoint",
                        description = "Collect parcel boundary points",
                        icon = Icons.Default.LocationOn,
                        onClick = {
                            activeLayer = FeatureLayerType.PARCEL_POINT
                            currentShapePoints = emptyList()
                            showLayerPickerModal = false
                        }
                    )
                    FeatureLayerOption(
                        title = "DetailLine",
                        description = "Draw lines or linear boundaries",
                        icon = Icons.Default.Timeline,
                        onClick = {
                            activeLayer = FeatureLayerType.DETAIL_LINE
                            currentShapePoints = emptyList()
                            showLayerPickerModal = false
                        }
                    )
                    FeatureLayerOption(
                        title = "DetailPolygon",
                        description = "Draw land parcel polygon areas",
                        icon = Icons.Default.Polyline,
                        onClick = {
                            activeLayer = FeatureLayerType.DETAIL_POLYGON
                            currentShapePoints = emptyList()
                            showLayerPickerModal = false
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLayerPickerModal = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GIS Field Collector", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    CompactGnssChip(
                        connectionState = connectionState,
                        position = gnssPosition,
                        onClick = { showBluetoothDialog = true },
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Survey Data"
                        )
                    }

                    TextButton(
                        onClick = {
                            if (unsyncedCount == 0) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("No unsynced features")
                                }
                                return@TextButton
                            }
                            isSyncing = true
                            coroutineScope.launch {
                                val result = syncEngine.syncAllLayers(agolServiceUrl)
                                isSyncing = false
                                refreshData()
                                if (result.success) {
                                    snackbarHostState.showSnackbar("Synced ${result.syncedCount} features to AGOL!")
                                } else {
                                    snackbarHostState.showSnackbar("Sync failed: ${result.errorMessage ?: "Unknown error"}")
                                }
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Sync",
                                modifier = Modifier.padding(end = 2.dp)
                            )
                            Text("Sync ($unsyncedCount)", fontSize = 12.sp)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            // Main '+' Button
            FloatingActionButton(
                onClick = { showLayerPickerModal = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Feature"
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Full Screen Satellite Map View
            GisMapView(
                activeLayer = activeLayer ?: FeatureLayerType.DETAIL_POINT,
                savedFeatures = savedFeatures,
                currentShapePoints = currentShapePoints,
                gnssPosition = gnssPosition,
                followGnssLocation = followGnssLocation,
                onMapTap = { point ->
                    val layer = activeLayer
                    if (layer == null) {
                        showLayerPickerModal = true
                    } else if (layer.isPoint) {
                        val geomObj = JSONObject().apply {
                            put("lat", point.latitude)
                            put("lng", point.longitude)
                        }
                        saveFeature(geomObj.toString())
                    } else {
                        currentShapePoints = currentShapePoints + point
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Location Follow Mode Toggle Button (Top Right of Map)
            FloatingActionButton(
                onClick = {
                    followGnssLocation = !followGnssLocation
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (followGnssLocation) "GNSS Location Auto-Follow ON" else "GNSS Location Auto-Follow OFF"
                        )
                    }
                },
                containerColor = if (followGnssLocation) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surface,
                contentColor = if (followGnssLocation) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "Toggle GNSS Follow",
                    modifier = Modifier.size(20.dp)
                )
            }

            // Active Collection Bottom Panel Overlay with Real-time Measurements
            activeLayer?.let { layer ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = when {
                                                layer.isPoint -> Icons.Default.LocationOn
                                                layer == FeatureLayerType.DETAIL_LINE -> Icons.Default.Timeline
                                                else -> Icons.Default.Polyline
                                            },
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Collecting: ${layer.displayName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    if (layer.isPoint) {
                                        Text(
                                            text = "Tap map to save point",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else if (layer == FeatureLayerType.DETAIL_LINE) {
                                        val lenMeters = GisGeometryUtils.calculateLineLengthMeters(currentShapePoints)
                                        Text(
                                            text = "${currentShapePoints.size} pts | Length: ${GisGeometryUtils.formatDistanceString(lenMeters)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else if (layer == FeatureLayerType.DETAIL_POLYGON) {
                                        val areaSqMeters = GisGeometryUtils.calculatePolygonAreaSqMeters(currentShapePoints)
                                        Text(
                                            text = "${currentShapePoints.size} pts | ${GisGeometryUtils.formatAreaSummary(areaSqMeters)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            IconButton(onClick = {
                                activeLayer = null
                                currentShapePoints = emptyList()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Collection"
                                )
                            }
                        }

                        // Bottom Actions for Line / Polygon or GNSS Capture
                        if (!layer.isPoint && currentShapePoints.isNotEmpty() || (gnssPosition != null && gnssPosition?.fixQuality != GnssFixQuality.NO_FIX)) {
                            Spacer(modifier = Modifier.padding(top = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Record RTK GNSS Location
                                val pos = gnssPosition
                                if (pos != null && pos.fixQuality != GnssFixQuality.NO_FIX) {
                                    OutlinedButton(
                                        onClick = {
                                            val pt = LatLngPoint(pos.latitude, pos.longitude)
                                            if (layer.isPoint) {
                                                val geomObj = JSONObject().apply {
                                                    put("lat", pt.latitude)
                                                    put("lng", pt.longitude)
                                                }
                                                saveFeature(geomObj.toString())
                                            } else {
                                                currentShapePoints = currentShapePoints + pt
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GpsFixed,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("RTK Point", fontSize = 12.sp)
                                    }
                                }

                                if (!layer.isPoint && currentShapePoints.isNotEmpty()) {
                                    IconButton(onClick = { currentShapePoints = emptyList() }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val jsonArr = JSONArray()
                                            for (pt in currentShapePoints) {
                                                val obj = JSONObject().apply {
                                                    put("lat", pt.latitude)
                                                    put("lng", pt.longitude)
                                                }
                                                jsonArr.put(obj)
                                            }
                                            saveFeature(jsonArr.toString())
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Save Shape (${currentShapePoints.size})", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeatureLayerOption(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
