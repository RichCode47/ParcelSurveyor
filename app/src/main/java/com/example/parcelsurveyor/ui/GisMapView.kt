package com.example.parcelsurveyor.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.FeatureRecord
import com.example.parcelsurveyor.data.LatLngPoint
import com.example.parcelsurveyor.gnss.GnssFixQuality
import com.example.parcelsurveyor.gnss.GnssPosition
import com.example.parcelsurveyor.util.GisGeometryUtils
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import java.util.Locale
import kotlin.math.cos

/**
 * Custom osmdroid tile source providing Esri World Imagery satellite basemap tiles.
 */
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

/**
 * Composable map view wrapping osmdroid [MapView] to display satellite imagery,
 * saved GIS feature layers, active shape drafts, real-time GPS precision overlays,
 * dynamic map scale bar, and an animated directional location arrow cursor.
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
    var mapOrientation by remember { mutableFloatStateOf(0f) }
    var mapZoom by remember { mutableDoubleStateOf(16.0) }
    var centerLat by remember { mutableDoubleStateOf(-15.4167) }

    // Custom directional arrow marker icon bitmap
    val arrowBitmap = remember { createDirectionalArrowBitmap() }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(ESRI_WORLD_IMAGERY_TILE_SOURCE)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(-15.4167, 28.2833))

            val rotationGestureOverlay = RotationGestureOverlay(this).apply {
                isEnabled = true
            }
            overlays.add(rotationGestureOverlay)

            addMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?): Boolean {
                    val newOrient = this@apply.mapOrientation
                    val newLat = mapCenter.latitude
                    if (mapOrientation != newOrient) mapOrientation = newOrient
                    if (centerLat != newLat) centerLat = newLat
                    return false
                }

                override fun onZoom(event: ZoomEvent?): Boolean {
                    val newZoom = zoomLevelDouble
                    val newOrient = this@apply.mapOrientation
                    if (mapZoom != newZoom) mapZoom = newZoom
                    if (mapOrientation != newOrient) mapOrientation = newOrient
                    return false
                }
            })
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.onDetach()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()

                val rotationOverlay = RotationGestureOverlay(map).apply { isEnabled = true }
                map.overlays.add(rotationOverlay)

                // 1. Single Tap Receiver
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

                // 2. Render Saved Features
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
                                    polygonPoints.add(polygonPoints[0])
                                }
                                val polygon = Polygon(map).apply {
                                    points = polygonPoints
                                    fillColor = Color.argb(60, 0, 255, 0)
                                    strokeColor = featureColor
                                    strokeWidth = 4f
                                }
                                map.overlays.add(polygon)

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

                // 3. Render Active Draft Shape
                if (currentShapePoints.isNotEmpty()) {
                    val activeGeoPoints = currentShapePoints.map { GeoPoint(it.latitude, it.longitude) }

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

                // 4. Directional Location Arrow Cursor & Live GPS Tracking
                if (gnssPosition != null && gnssPosition.fixQuality != GnssFixQuality.NO_FIX) {
                    val gnssPoint = GeoPoint(gnssPosition.latitude, gnssPosition.longitude)

                    val locationArrowMarker = Marker(map).apply {
                        position = gnssPoint
                        icon = BitmapDrawable(context.resources, arrowBitmap)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        rotation = gnssPosition.bearing
                        title = "Location (${gnssPosition.fixQuality.displayName})"
                        snippet = "HDOP: ${gnssPosition.hdop}, Sats: ${gnssPosition.satellitesCount}"
                    }
                    map.overlays.add(locationArrowMarker)

                    if (followGnssLocation) {
                        map.controller.setCenter(gnssPoint)
                    }

                    val accuracyMeters = maxOf(1.0, gnssPosition.hdop * 2.5)
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
                        fillColor = Color.argb(35, 33, 150, 243)
                        strokeColor = Color.argb(120, 33, 150, 243)
                        strokeWidth = 2f
                    }
                    map.overlays.add(accuracyPolygon)
                }

                // 5. Breadcrumb Trail
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
                    map.controller.setCenter(GeoPoint(targetCenterPoint.latitude, targetCenterPoint.longitude))
                    map.controller.setZoom(18.0)
                }
            }
        )

        // Real-time GPS Precision Indicator (Top-Left)
        GpsPrecisionOverlay(
            gnssPosition = gnssPosition,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 10.dp, start = 10.dp)
        )

        // Floating Compass Overlay (Top-Right)
        FloatingCompassOverlay(
            mapOrientation = mapOrientation,
            onResetNorth = {
                mapView.mapOrientation = 0f
                mapOrientation = 0f
                mapView.invalidate()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
        )

        // Persistent Dynamic Map Scale Bar (Bottom-Left)
        MapScaleBar(
            zoomLevel = mapZoom,
            centerLatitude = centerLat,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 20.dp)
        )
    }
}

/**
 * Real-time GPS precision indicator badge overlay displaying fix status, accuracy, HDOP/VDOP, and satellites count.
 */
@Composable
fun GpsPrecisionOverlay(
    gnssPosition: GnssPosition?,
    modifier: Modifier = Modifier
) {
    val pos = gnssPosition ?: GnssPosition(0.0, 0.0)
    val fixQuality = pos.fixQuality
    val isHighAccuracy = fixQuality.isHighPrecision || pos.hdop in 0.01..1.5

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val badgeBgColor = when (fixQuality) {
                    GnssFixQuality.RTK_FIXED -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    GnssFixQuality.RTK_FLOAT, GnssFixQuality.DGPS -> androidx.compose.ui.graphics.Color(0xFFF57F17)
                    GnssFixQuality.GPS_SPS -> androidx.compose.ui.graphics.Color(0xFF0288D1)
                    else -> androidx.compose.ui.graphics.Color(0xFF757575)
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeBgColor
                ) {
                    Text(
                        text = fixQuality.displayName,
                        color = androidx.compose.ui.graphics.Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Text(
                    text = "🛰️ ${pos.satellitesCount} Sats",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val accuracyEst = maxOf(0.02, pos.hdop * 0.25)
            val accuracyText = if (pos.hdop > 0) String.format(Locale.US, "±%.2fm", accuracyEst) else "±1.5m"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Acc: $accuracyText",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isHighAccuracy) androidx.compose.ui.graphics.Color(0xFF2E7D32) else androidx.compose.ui.graphics.Color(0xFFD84315)
                )

                Text(
                    text = String.format(Locale.US, "HDOP: %.1f | VDOP: %.1f", pos.hdop, if (pos.vdop > 0) pos.vdop else pos.hdop * 1.3),
                    fontSize = 9.sp,
                    color = androidx.compose.ui.graphics.Color.Gray
                )
            }
        }
    }
}

/**
 * Persistent dynamic map scale bar updating dynamically based on current zoom level and latitude.
 */
@Composable
fun MapScaleBar(
    zoomLevel: Double,
    centerLatitude: Double,
    modifier: Modifier = Modifier
) {
    val metersPerPixel = (156543.03392 * cos(Math.toRadians(centerLatitude))) / Math.pow(2.0, zoomLevel)
    val targetPixelWidthDp = 100.0
    val targetMeters = metersPerPixel * targetPixelWidthDp

    val scaleSteps = doubleArrayOf(
        1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0,
        1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0
    )

    var chosenScaleMeters = scaleSteps[0]
    for (step in scaleSteps) {
        if (step <= targetMeters * 1.3) {
            chosenScaleMeters = step
        } else break
    }

    val actualWidthDp = (chosenScaleMeters / metersPerPixel).dp
    val labelText = GisGeometryUtils.formatDistanceString(chosenScaleMeters)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = labelText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            Canvas(modifier = Modifier.size(actualWidthDp, 8.dp)) {
                val lineY = size.height / 2f
                val tickHeight = 6.dp.toPx()

                drawLine(
                    color = androidx.compose.ui.graphics.Color.Black,
                    start = Offset(0f, lineY),
                    end = Offset(size.width, lineY),
                    strokeWidth = 2.dp.toPx()
                )

                drawLine(
                    color = androidx.compose.ui.graphics.Color.Black,
                    start = Offset(0f, lineY - tickHeight / 2f),
                    end = Offset(0f, lineY + tickHeight / 2f),
                    strokeWidth = 2.dp.toPx()
                )

                drawLine(
                    color = androidx.compose.ui.graphics.Color.Black,
                    start = Offset(size.width, lineY - tickHeight / 2f),
                    end = Offset(size.width, lineY + tickHeight / 2f),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

/**
 * Creates a high-visibility directional location arrow bitmap for GNSS position cursor.
 */
private fun createDirectionalArrowBitmap(): Bitmap {
    val size = 64
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    val haloPath = android.graphics.Path().apply {
        moveTo(size / 2f, 4f)
        lineTo(size - 8f, size - 8f)
        lineTo(size / 2f, size - 18f)
        lineTo(8f, size - 8f)
        close()
    }
    paint.color = Color.WHITE
    canvas.drawPath(haloPath, paint)

    val arrowPath = android.graphics.Path().apply {
        moveTo(size / 2f, 8f)
        lineTo(size - 12f, size - 12f)
        lineTo(size / 2f, size - 20f)
        lineTo(12f, size - 12f)
        close()
    }
    paint.color = Color.rgb(0, 180, 255)
    canvas.drawPath(arrowPath, paint)

    return bitmap
}

/**
 * Floating compass widget indicating True North relative to map orientation.
 */
@Composable
fun FloatingCompassOverlay(
    mapOrientation: Float,
    onResetNorth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedOrientation by animateFloatAsState(
        targetValue = mapOrientation,
        animationSpec = tween(durationMillis = 300),
        label = "compassRotation"
    )

    Surface(
        onClick = onResetNorth,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier.size(48.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Canvas(
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer {
                        rotationZ = -animatedOrientation
                    }
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val radius = size.width / 2f

                drawCircle(
                    color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.4f),
                    radius = radius,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )

                val northPath = Path().apply {
                    moveTo(cx, cy - radius + 4.dp.toPx())
                    lineTo(cx - 5.dp.toPx(), cy)
                    lineTo(cx + 5.dp.toPx(), cy)
                    close()
                }
                drawPath(
                    path = northPath,
                    color = androidx.compose.ui.graphics.Color(0xFFE53935)
                )

                val southPath = Path().apply {
                    moveTo(cx, cy + radius - 4.dp.toPx())
                    lineTo(cx - 5.dp.toPx(), cy)
                    lineTo(cx + 5.dp.toPx(), cy)
                    close()
                }
                drawPath(
                    path = southPath,
                    color = androidx.compose.ui.graphics.Color(0xFFB0BEC5)
                )

                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }
    }
}
