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

/**
 * Custom osmdroid tile source providing Esri World Imagery satellite basemap tiles.
 */
val ESRI_WORLD_IMAGERY_TILE_SOURCE = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    /**
     * Constructs the tile URL string for the given map tile index.
     *
     * @param pMapTileIndex Map tile index containing zoom, x, and y coordinates.
     * @return The HTTP URL string for the requested tile image.
     */
    override fun getTileURLString(pMapTileIndex: Long): String {
        val zoom = MapTileIndex.getZoom(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        return "$baseUrl$zoom/$y/$x"
    }
}

/**
 * Composable map view wrapping osmdroid [MapView] to display Esri satellite imagery,
 * saved feature geometries (points, lines, polygons), active draft shapes in progress,
 * and live external GNSS position cursors.
 *
 * @param activeLayer The currently active feature layer type being collected.
 * @param savedFeatures List of saved feature records grouped by layer type.
 * @param currentShapePoints List of coordinate points representing the active draft line or polygon.
 * @param gnssPosition Optional current [GnssPosition] update from GNSS receiver.
 * @param followGnssLocation Whether the map camera should automatically follow the GNSS position.
 * @param onMapTap Callback invoked when the user taps on the map.
 * @param onFeatureClick Callback invoked when the user clicks on a saved feature marker.
 * @param modifier Modifier for styling and sizing the map view.
 */
@Composable
fun GisMapView(
    activeLayer: FeatureLayerType?,
    savedFeatures: List<Pair<FeatureLayerType, List<FeatureRecord>>>,
    currentShapePoints: List<LatLngPoint>,
    gnssPosition: GnssPosition? = null,
    followGnssLocation: Boolean = true,
    targetCenterPoint: LatLngPoint? = null,
    breadcrumbPoints: List<LatLngPoint> = emptyList(),
    onMapTap: (LatLngPoint) -> Unit,
    onFeatureClick: (FeatureLayerType, FeatureRecord) -> Unit = { _, _ -> },
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
                                snippet = if (isSynced) "Status: Synced (Tap to Edit)" else "Status: Local Edit (Tap to Edit)"
                                setOnMarkerClickListener { _, _ ->
                                    onFeatureClick(layerType, rec)
                                    true
                                }
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

                            // Add a selectable start marker for line editing
                            if (geoPoints.isNotEmpty()) {
                                val startMarker = Marker(map).apply {
                                    position = geoPoints[0]
                                    title = "${layerType.displayName} (Line): ${rec.notes}"
                                    snippet = "Tap to Edit"
                                    setOnMarkerClickListener { _, _ ->
                                        onFeatureClick(layerType, rec)
                                        true
                                    }
                                }
                                map.overlays.add(startMarker)
                            }
                        } else if (layerType == FeatureLayerType.DETAIL_POLYGON) {
                            val arr = JSONArray(rec.geometryJson)
                            val geoPoints = mutableListOf<GeoPoint>()
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONObject(i)
                                geoPoints.add(GeoPoint(pt.getDouble("lat"), pt.getDouble("lng")))
                            }
                            val polygonPoints = geoPoints.toMutableList()
                            if (polygonPoints.isNotEmpty()) {
                                polygonPoints.add(polygonPoints[0]) // close ring
                            }
                            val polygon = Polygon(map).apply {
                                points = polygonPoints
                                fillColor = Color.argb(60, 0, 255, 0)
                                strokeColor = featureColor
                                strokeWidth = 4f
                            }
                            map.overlays.add(polygon)

                            // Add a selectable start marker for polygon editing
                            if (geoPoints.isNotEmpty()) {
                                val startMarker = Marker(map).apply {
                                    position = geoPoints[0]
                                    title = "${layerType.displayName} (Polygon): ${rec.notes}"
                                    snippet = "Tap to Edit"
                                    setOnMarkerClickListener { _, _ ->
                                        onFeatureClick(layerType, rec)
                                        true
                                    }
                                }
                                map.overlays.add(startMarker)
                            }
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

                // Render GNSS Accuracy Circle (Requirement 103)
                val accuracyMeters = maxOf(2.0, gnssPosition.hdop * 3.0)
                val earthRadius = 6371000.0
                val circlePoints = mutableListOf<GeoPoint>()
                for (i in 0..36) {
                    val angle = Math.toRadians((i * 10).toDouble())
                    val dLat = (accuracyMeters / earthRadius) * Math.cos(angle)
                    val dLng = (accuracyMeters / earthRadius) * Math.sin(angle) / Math.cos(Math.toRadians(gnssPoint.latitude))
                    circlePoints.add(GeoPoint(gnssPoint.latitude + Math.toDegrees(dLat), gnssPoint.longitude + Math.toDegrees(dLng)))
                }
                val accuracyPolygon = Polygon(map).apply {
                    points = circlePoints
                    fillColor = Color.argb(40, 33, 150, 243)
                    strokeColor = Color.argb(120, 33, 150, 243)
                    strokeWidth = 2f
                }
                map.overlays.add(accuracyPolygon)
            }

            // 5. Render Breadcrumb Trail Polyline (Requirement 104)
            if (breadcrumbPoints.size >= 2) {
                val breadcrumbGeoPoints = breadcrumbPoints.map { GeoPoint(it.latitude, it.longitude) }
                val breadcrumbPolyline = Polyline(map).apply {
                    setPoints(breadcrumbGeoPoints)
                    outlinePaint.color = Color.argb(150, 255, 152, 0)
                    outlinePaint.strokeWidth = 5f
                }
                map.overlays.add(breadcrumbPolyline)
            }

            if (targetCenterPoint != null) {
                map.controller.animateTo(GeoPoint(targetCenterPoint.latitude, targetCenterPoint.longitude))
                map.controller.setZoom(18.0)
            }

            map.invalidate()
        }
    )
}
