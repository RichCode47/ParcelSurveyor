package com.example.parcelsurveyor

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.FeatureRecord
import com.example.parcelsurveyor.data.GisDatabaseHelper
import com.example.parcelsurveyor.data.LatLngPoint
import com.example.parcelsurveyor.sync.AgolSyncEngine
import com.example.parcelsurveyor.ui.GisMapView
import com.example.parcelsurveyor.ui.theme.ParcelSurveyorTheme
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: GisDatabaseHelper
    private lateinit var syncEngine: AgolSyncEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        dbHelper = GisDatabaseHelper(this)
        syncEngine = AgolSyncEngine(dbHelper)

        setContent {
            ParcelSurveyorTheme {
                GisAppScreen(
                    dbHelper = dbHelper,
                    syncEngine = syncEngine
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GisAppScreen(
    dbHelper: GisDatabaseHelper,
    syncEngine: AgolSyncEngine
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var activeLayer by remember { mutableStateOf(FeatureLayerType.DETAIL_POINT) }
    var currentShapePoints by remember { mutableStateOf(listOf<LatLngPoint>()) }
    var unsyncedCount by remember { mutableIntStateOf(0) }
    var isSyncing by remember { mutableStateOf(false) }
    var savedFeatures by remember { mutableStateOf<List<Pair<FeatureLayerType, List<FeatureRecord>>>>(emptyList()) }

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
        dbHelper.insertFeature(activeLayer.tableName, geometryJson, "Field capture")
        currentShapePoints = emptyList()
        refreshData()
        coroutineScope.launch {
            snackbarHostState.showSnackbar("Saved to ${activeLayer.displayName}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GIS Field Collector MVP") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
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
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Sync",
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text("Sync ($unsyncedCount)")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!activeLayer.isPoint && currentShapePoints.isNotEmpty()) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { currentShapePoints = emptyList() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Clear Shape",
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
                            }
                        ) {
                            Text("Save ${activeLayer.displayName} (${currentShapePoints.size} pts)")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Layer Selector Horizontal Chips
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureLayerType.ALL_LAYERS.forEach { layer ->
                        val selected = activeLayer == layer
                        FilterChip(
                            selected = selected,
                            onClick = {
                                activeLayer = layer
                                currentShapePoints = emptyList()
                            },
                            label = { Text(layer.displayName) }
                        )
                    }
                }
            }

            // Interactive GIS Map View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                GisMapView(
                    activeLayer = activeLayer,
                    savedFeatures = savedFeatures,
                    currentShapePoints = currentShapePoints,
                    onMapTap = { point ->
                        if (activeLayer.isPoint) {
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
            }
        }
    }
}
