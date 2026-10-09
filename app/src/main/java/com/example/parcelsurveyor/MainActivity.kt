package com.example.parcelsurveyor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material.icons.filled.List
import com.example.parcelsurveyor.ui.BluetoothDeviceDialog
import com.example.parcelsurveyor.ui.CompactGnssChip
import com.example.parcelsurveyor.ui.DiagnosticsDialog
import com.example.parcelsurveyor.ui.FeatureAttributeDialog
import com.example.parcelsurveyor.ui.FeatureEditDialog
import com.example.parcelsurveyor.ui.FeatureListDialog
import com.example.parcelsurveyor.ui.GisMapView
import com.example.parcelsurveyor.ui.StakeoutCard
import com.example.parcelsurveyor.ui.StakeoutTargetDialog
import com.example.parcelsurveyor.ui.theme.ParcelSurveyorTheme
import com.example.parcelsurveyor.util.GisDataExporter
import com.example.parcelsurveyor.util.GisGeometryUtils
import com.example.parcelsurveyor.util.PhotoManager
import com.example.parcelsurveyor.util.StakeoutInfo
import com.example.parcelsurveyor.util.StakeoutManager
import com.example.parcelsurveyor.util.UtmConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Main application activity for the Parcel Surveyor GIS field collection application.
 */
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

/**
 * Root Composable screen for the Parcel Surveyor application.
 */
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
    var showStakeoutDialog by remember { mutableStateOf(false) }
    var followGnssLocation by remember { mutableStateOf(true) }

    var currentPhotoFile by remember { mutableStateOf<File?>(null) }
    var activeStakeoutTarget by remember { mutableStateOf<Pair<LatLngPoint, String>?>(null) }
    var selectedFeatureForEdit by remember { mutableStateOf<Pair<FeatureLayerType, FeatureRecord>?>(null) }

    // SharedPreferences draft auto-save and restoration state.
    // Why SharedPreferences beats DB for draft state:
    // Draft-in-progress points and active layer are ephemeral, volatile user input. Storing them in permanent spatial SQLite tables
    // adds unnecessary relational schema overhead, migration complexity, and database bloat for temporary work-in-progress data.
    // SharedPreferences provides fast, lightweight key-value persistence specifically designed for app preferences and ephemeral state.
    val prefs = remember { context.getSharedPreferences("parcel_surveyor_draft", Context.MODE_PRIVATE) }
    val shapeHistory = remember { mutableStateListOf<List<LatLngPoint>>() }

    var showAttributeDialog by remember { mutableStateOf(false) }
    var pendingGeometryJson by remember { mutableStateOf<String?>(null) }
    var showFeatureListDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var targetMapCenter by remember { mutableStateOf<LatLngPoint?>(null) }

    val connectionState by gnssManager.connectionState.collectAsState()
    val connectedDevice by gnssManager.connectedDevice.collectAsState()
    val gnssPosition by gnssManager.currentPosition.collectAsState()

    val agolServiceUrl = "https://servicesX.arcgis.com/YOUR_ORG/arcgis/rest/services/YOUR_SVC/FeatureServer"

    /**
     * Refreshes local feature counts and loaded feature collections from the database asynchronously.
     */
    fun refreshData() {
        coroutineScope.launch(Dispatchers.IO) {
            val count = dbHelper.getUnsyncedCount()
            val features = FeatureLayerType.ALL_LAYERS.map { layer ->
                Pair(layer, dbHelper.getAllFeatures(layer.tableName))
            }
            withContext(Dispatchers.Main) {
                unsyncedCount = count
                savedFeatures = features
            }
        }
    }

    val breadcrumbPoints = remember { mutableStateListOf<LatLngPoint>() }
    var showDiagnosticsDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            gnssManager.startInternalGps(context)
        }
    }

    // Restore draft and request permissions on initial load
    LaunchedEffect(Unit) {
        refreshData()
        val finePermission = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (finePermission != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            gnssManager.startInternalGps(context)
        }

        val savedLayerName = prefs.getString("active_layer", null)
        val savedPointsJson = prefs.getString("draft_points", null)
        if (savedLayerName != null && savedPointsJson != null) {
            activeLayer = FeatureLayerType.fromTableName(savedLayerName)
            try {
                val arr = JSONArray(savedPointsJson)
                val list = mutableListOf<LatLngPoint>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(LatLngPoint(obj.getDouble("lat"), obj.getDouble("lng")))
                }
                currentShapePoints = list
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    // Breadcrumb trail logging
    LaunchedEffect(gnssPosition) {
        val pos = gnssPosition
        if (pos != null && pos.fixQuality != GnssFixQuality.NO_FIX) {
            val pt = LatLngPoint(pos.latitude, pos.longitude)
            if (breadcrumbPoints.isEmpty() || GisGeometryUtils.calculateDistanceMeters(breadcrumbPoints.last(), pt) >= 1.0) {
                breadcrumbPoints.add(pt)
            }
        }
    }

    // Save draft on change
    LaunchedEffect(activeLayer, currentShapePoints) {
        val editor = prefs.edit()
        if (activeLayer != null && currentShapePoints.isNotEmpty()) {
            editor.putString("active_layer", activeLayer?.tableName)
            val arr = JSONArray()
            for (pt in currentShapePoints) {
                arr.put(JSONObject().apply {
                    put("lat", pt.latitude)
                    put("lng", pt.longitude)
                })
            }
            editor.putString("draft_points", arr.toString())
        } else {
            editor.clear()
        }
        editor.apply()
    }

    fun clearDraft() {
        prefs.edit().clear().apply()
        activeLayer = null
        currentShapePoints = emptyList()
        shapeHistory.clear()
        currentPhotoFile = null
    }

    fun addVertexWithUndo(newPoint: LatLngPoint) {
        if (shapeHistory.size >= 20) {
            shapeHistory.removeAt(0)
        }
        shapeHistory.add(currentShapePoints)
        currentShapePoints = currentShapePoints + newPoint
    }

    fun undoLastVertex() {
        if (shapeHistory.isNotEmpty()) {
            currentShapePoints = shapeHistory.removeAt(shapeHistory.size - 1)
        }
    }

    fun saveFeatureWithAttributes(geometryJson: String, attributesJson: String, photoPath: String? = null) {
        val targetLayer = activeLayer ?: FeatureLayerType.DETAIL_POINT
        coroutineScope.launch(Dispatchers.IO) {
            val globalId = dbHelper.insertFeature(targetLayer.tableName, geometryJson, attributesJson)
            if (photoPath != null) {
                dbHelper.addAttachment(globalId, photoPath)
            }
            val count = dbHelper.getUnsyncedCount()
            val features = FeatureLayerType.ALL_LAYERS.map { layer ->
                Pair(layer, dbHelper.getAllFeatures(layer.tableName))
            }
            withContext(Dispatchers.Main) {
                clearDraft()
                unsyncedCount = count
                savedFeatures = features
                snackbarHostState.showSnackbar("Saved ${targetLayer.displayName} with attributes!")
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val photo = currentPhotoFile
            if (photo != null) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Site photo captured!")
                }
            }
        }
    }

    if (showBluetoothDialog) {
        var pairedDevices by remember { mutableStateOf<List<com.example.parcelsurveyor.gnss.BluetoothDeviceInfo>>(emptyList()) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val devices = gnssManager.getPairedDevices(context)
                withContext(Dispatchers.Main) {
                    pairedDevices = devices
                }
            }
        }
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
                            coroutineScope.launch(Dispatchers.IO) {
                                val file = GisDataExporter.exportToCsv(context, savedFeatures)
                                withContext(Dispatchers.Main) {
                                    GisDataExporter.shareExportedFile(context, file, "text/csv")
                                }
                            }
                        }
                    )
                    FeatureLayerOption(
                        title = "Google Earth KML (.kml)",
                        description = "Open points, lines, polygons in Google Earth",
                        icon = Icons.Default.Share,
                        onClick = {
                            showExportDialog = false
                            coroutineScope.launch(Dispatchers.IO) {
                                val file = GisDataExporter.exportToKml(context, savedFeatures)
                                withContext(Dispatchers.Main) {
                                    GisDataExporter.shareExportedFile(context, file, "application/vnd.google-earth.kml+xml")
                                }
                            }
                        }
                    )
                    FeatureLayerOption(
                        title = "GeoJSON File (.geojson)",
                        description = "Standard GIS spatial format for QGIS / ArcGIS Pro",
                        icon = Icons.Default.Share,
                        onClick = {
                            showExportDialog = false
                            coroutineScope.launch(Dispatchers.IO) {
                                val file = GisDataExporter.exportToGeoJson(context, savedFeatures)
                                withContext(Dispatchers.Main) {
                                    GisDataExporter.shareExportedFile(context, file, "application/geo+json")
                                }
                            }
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

    // Stakeout Target Dialog
    if (showStakeoutDialog) {
        StakeoutTargetDialog(
            savedFeatures = savedFeatures,
            onSelectTarget = { pt, name ->
                activeStakeoutTarget = Pair(pt, name)
                showStakeoutDialog = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Stakeout target set: $name")
                }
            },
            onDismiss = { showStakeoutDialog = false }
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

    // Attribute Form Dialog
    if (showAttributeDialog && pendingGeometryJson != null) {
        FeatureAttributeDialog(
            layerType = activeLayer ?: FeatureLayerType.DETAIL_POINT,
            onSave = { attributesJson ->
                val geom = pendingGeometryJson!!
                pendingGeometryJson = null
                showAttributeDialog = false
                saveFeatureWithAttributes(geom, attributesJson, currentPhotoFile?.absolutePath)
            },
            onDismiss = {
                showAttributeDialog = false
                pendingGeometryJson = null
            }
        )
    }

    // Feature List & Search Dialog
    if (showFeatureListDialog) {
        FeatureListDialog(
            savedFeatures = savedFeatures,
            onZoomToFeature = { pt ->
                targetMapCenter = pt
            },
            onDismiss = { showFeatureListDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GIS Surveyor", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    CompactGnssChip(
                        connectionState = connectionState,
                        position = gnssPosition,
                        onClick = { showBluetoothDialog = true },
                        modifier = Modifier.padding(end = 2.dp)
                    )

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
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Sync",
                                modifier = Modifier.padding(end = 2.dp)
                            )
                            Text("Sync ($unsyncedCount)", fontSize = 11.sp)
                        }
                    }

                    // Overflow 3-dot Menu for Secondary Survey Tools
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Tools"
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Search Features") },
                                onClick = {
                                    showOverflowMenu = false
                                    showFeatureListDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Stakeout Target") },
                                onClick = {
                                    showOverflowMenu = false
                                    showStakeoutDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Data") },
                                onClick = {
                                    showOverflowMenu = false
                                    showExportDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("GNSS Diagnostics") },
                                onClick = {
                                    showOverflowMenu = false
                                    showDiagnosticsDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Map, contentDescription = null) }
                            )
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
                targetCenterPoint = targetMapCenter,
                breadcrumbPoints = breadcrumbPoints,
                onMapTap = { point ->
                    targetMapCenter = null // reset target center on map interaction
                    val layer = activeLayer
                    if (layer == null) {
                        showLayerPickerModal = true
                    } else if (layer.isPoint) {
                        val geomObj = JSONObject().apply {
                            put("lat", point.latitude)
                            put("lng", point.longitude)
                        }
                        pendingGeometryJson = geomObj.toString()
                        showAttributeDialog = true
                    } else {
                        addVertexWithUndo(point)
                    }
                },
                onFeatureClick = { layerType, rec ->
                    selectedFeatureForEdit = Pair(layerType, rec)
                },
                modifier = Modifier.fillMaxSize()
            )

            // Diagnostics Dialog
            if (showDiagnosticsDialog) {
                DiagnosticsDialog(
                    connectionState = connectionState,
                    currentPosition = gnssPosition,
                    onDismiss = { showDiagnosticsDialog = false }
                )
            }

            // Feature Edit Dialog
            selectedFeatureForEdit?.let { (layerType, record) ->
                FeatureEditDialog(
                    layerType = layerType,
                    feature = record,
                    currentGnssPosition = gnssPosition,
                    onSave = { newNotes, newGeomJson ->
                        val geomToSave = newGeomJson ?: record.geometryJson
                        coroutineScope.launch(Dispatchers.IO) {
                            dbHelper.updateFeature(layerType.tableName, record.globalId, geomToSave, newNotes)
                            val count = dbHelper.getUnsyncedCount()
                            val features = FeatureLayerType.ALL_LAYERS.map { layer ->
                                Pair(layer, dbHelper.getAllFeatures(layer.tableName))
                            }
                            withContext(Dispatchers.Main) {
                                selectedFeatureForEdit = null
                                unsyncedCount = count
                                savedFeatures = features
                                snackbarHostState.showSnackbar("Updated ${layerType.displayName} (Sync pending)")
                            }
                        }
                    },
                    onDelete = {
                        coroutineScope.launch(Dispatchers.IO) {
                            dbHelper.deleteFeature(layerType.tableName, record.globalId)
                            val count = dbHelper.getUnsyncedCount()
                            val features = FeatureLayerType.ALL_LAYERS.map { layer ->
                                Pair(layer, dbHelper.getAllFeatures(layer.tableName))
                            }
                            withContext(Dispatchers.Main) {
                                selectedFeatureForEdit = null
                                unsyncedCount = count
                                savedFeatures = features
                                snackbarHostState.showSnackbar("Deleted ${layerType.displayName}")
                            }
                        }
                    },
                    onDismiss = { selectedFeatureForEdit = null }
                )
            }

            // Module 2: Live UTM Coordinate Readout Bar (Top Center)
            val currentPos = gnssPosition
            if (currentPos != null && currentPos.fixQuality != GnssFixQuality.NO_FIX) {
                val utm = remember(currentPos.latitude, currentPos.longitude) {
                    UtmConverter.fromLatLng(currentPos.latitude, currentPos.longitude)
                }
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = utm.formattedString,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

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

            // Module 3: Stakeout Overlay Card
            activeStakeoutTarget?.let { target ->
                val pos = gnssPosition
                if (pos != null && pos.fixQuality != GnssFixQuality.NO_FIX) {
                    val stakeoutInfo = remember(pos.latitude, pos.longitude, target) {
                        StakeoutManager.calculateStakeout(
                            currentPos = LatLngPoint(pos.latitude, pos.longitude),
                            targetPos = target.first,
                            targetName = target.second
                        )
                    }
                    StakeoutCard(
                        stakeoutInfo = stakeoutInfo,
                        onStopStakeout = { activeStakeoutTarget = null },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp)
                    )
                }
            }

            // Active Collection Bottom Panel Overlay with Real-time Measurements & Photo Capture
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
                                            text = if (currentPhotoFile != null) "Photo attached! Tap map to save" else "Tap map to save point",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (currentPhotoFile != null) Color(0xFF4CAF50) else Color.Unspecified
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

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Camera Photo Button
                                IconButton(onClick = {
                                    val photoFile = PhotoManager.createPhotoFile(context)
                                    currentPhotoFile = photoFile
                                    val photoUri = PhotoManager.getPhotoUri(context, photoFile)
                                    cameraLauncher.launch(photoUri)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Capture Site Photo",
                                        tint = if (currentPhotoFile != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                    )
                                }

                                IconButton(onClick = {
                                    clearDraft()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cancel Collection"
                                    )
                                }
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
                                                pendingGeometryJson = geomObj.toString()
                                                showAttributeDialog = true
                                            } else {
                                                addVertexWithUndo(pt)
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

                                // Undo Button (Max history stack of 20 vertices)
                                if (!layer.isPoint && currentShapePoints.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { undoLastVertex() }
                                    ) {
                                        Text("Undo (${shapeHistory.size})", fontSize = 11.sp)
                                    }

                                    IconButton(onClick = { clearDraft() }) {
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
                                            pendingGeometryJson = jsonArr.toString()
                                            showAttributeDialog = true
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

/**
 * Reusable Composable option item for selecting feature layers or export formats in dialogs.
 */
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
