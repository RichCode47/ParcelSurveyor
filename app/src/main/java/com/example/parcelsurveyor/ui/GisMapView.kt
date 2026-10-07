package com.example.parcelsurveyor.ui

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.parcelsurveyor.gnss.GnssFixQuality
import com.example.parcelsurveyor.gnss.GnssPosition
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.FeatureRecord
import com.example.parcelsurveyor.data.LatLngPoint
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

// Custom TileSource for Esri World Imagery Satellite Tiles
val ESRI_WORLD_IMAGERY_TILE_SOURCE = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x"
    }
}

@Composable
fun GisMapView(
    activeLayer: FeatureLayerType,
    savedFeatures: List<Pair<FeatureLayerType, List<FeatureRecord>>>,
    currentShapePoints: List<LatLngPoint>,
    gnssPosition: GnssPosition? = null,
    followGnssLocation: Boolean = true,
    onMapTap: (LatLngPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(ESRI_WORLD_IMAGERY_TILE_SOURCE)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            // Default center: Lusaka, Zambia (-15.4167, 28.2833)
            controller.setCenter(GeoPoint(-15.4167, 28.2833))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = { map ->
            map.overlays.clear()

            // 1. Map Single Tap Receiver
            val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    onMapTap(LatLngPoint(p.latitude, p.longitude))
                    return true
                }

                override fun longPressHelper(p: GeoPoint): Boolean {
                    return false
                }
            })
            map.overlays.add(eventsOverlay)

            // 2. Render Saved Features from SQLite DB
            for ((layerType, records) in savedFeatures) {
                for (rec in records) {
                    val isSynced = rec.syncStatus == 0
                    val featureColor = if (isSynced) Color.GREEN else Color.YELLOW

                    try {
                        if (layerType.isPoint) {
                            val obj = JSONObject(rec.geometryJson)
                            val lat = obj.getDouble("lat")
                            val lng = obj.getDouble("lng")
                            val marker = Marker(map).apply {
                                position = GeoPoint(lat, lng)
                                title = "${layerType.displayName}: ${rec.notes}"
                                snippet = if (isSynced) "Status: Synced" else "Status: Local Edit"
                            }
                            map.overlays.add(marker)
                        } else if (layerType == FeatureLayerType.DETAIL_LINE) {
                            val arr = JSONArray(rec.geometryJson)
                            val geoPoints = mutableListOf<GeoPoint>()
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONObject(i)
                                geoPoints.add(GeoPoint(pt.getDouble("lat"), pt.getDouble("lng")))
                            }
                            val polyline = Polyline(map).apply {
                                setPoints(geoPoints)
                                outlinePaint.color = featureColor
                                outlinePaint.strokeWidth = 6f
                            }
                            map.overlays.add(polyline)
                        } else if (layerType == FeatureLayerType.DETAIL_POLYGON) {
                            val arr = JSONArray(rec.geometryJson)
                            val geoPoints = mutableListOf<GeoPoint>()
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONObject(i)
                                geoPoints.add(GeoPoint(pt.getDouble("lat"), pt.getDouble("lng")))
                            }
                            if (geoPoints.isNotEmpty()) {
                                geoPoints.add(geoPoints[0]) // close ring
                            }
                            val polygon = Polygon(map).apply {
                                points = geoPoints
                                fillColor = Color.argb(60, 0, 255, 0)
                                strokeColor = featureColor
                                strokeWidth = 4f
                            }
                            map.overlays.add(polygon)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 3. Render Active Draft Shape in Progress
            if (currentShapePoints.isNotEmpty()) {
                val activeGeoPoints = currentShapePoints.map { GeoPoint(it.latitude, it.longitude) }

                // Draft Markers
                for (pt in activeGeoPoints) {
                    val m = Marker(map).apply {
                        position = pt
                        title = "Draft Point"
                    }
                    map.overlays.add(m)
                }

                if (activeLayer == FeatureLayerType.DETAIL_LINE && activeGeoPoints.size >= 2) {
                    val draftPolyline = Polyline(map).apply {
                        setPoints(activeGeoPoints)
                        outlinePaint.color = Color.CYAN
                        outlinePaint.strokeWidth = 8f
                    }
                    map.overlays.add(draftPolyline)
                } else if (activeLayer == FeatureLayerType.DETAIL_POLYGON && activeGeoPoints.size >= 3) {
                    val closedPoints = activeGeoPoints.toMutableList().apply { add(activeGeoPoints[0]) }
                    val draftPolygon = Polygon(map).apply {
                        points = closedPoints
                        fillColor = Color.argb(80, 0, 200, 255)
                        strokeColor = Color.CYAN
                        strokeWidth = 6f
                    }
                    map.overlays.add(draftPolygon)
                }
            }

            // 4. Render Active External GNSS Receiver Cursor & Auto-Center / Follow Position
            if (gnssPosition != null && gnssPosition.fixQuality != GnssFixQuality.NO_FIX) {
                val gnssPoint = GeoPoint(gnssPosition.latitude, gnssPosition.longitude)
                val gnssMarker = Marker(map).apply {
                    position = gnssPoint
                    title = "Trimble / GNSS Position (${gnssPosition.fixQuality.displayName})"
                    snippet = "Sats: ${gnssPosition.satellitesCount}, HDOP: ${gnssPosition.hdop}"
                }
                map.overlays.add(gnssMarker)

                if (followGnssLocation) {
                    map.controller.animateTo(gnssPoint)
                }
            }

            map.invalidate()
        }
    )
}
