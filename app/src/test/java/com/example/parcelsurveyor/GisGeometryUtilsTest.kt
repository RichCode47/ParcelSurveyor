package com.example.parcelsurveyor

import com.example.parcelsurveyor.data.LatLngPoint
import com.example.parcelsurveyor.util.GisGeometryUtils
import org.junit.Assert.assertTrue
import org.junit.Test

class GisGeometryUtilsTest {

    @Test
    fun testHaversineDistance() {
        // Distance between Lusaka (-15.4167, 28.2833) and Ndola (-12.9583, 28.6361)
        val p1 = LatLngPoint(-15.4167, 28.2833)
        val p2 = LatLngPoint(-12.9583, 28.6361)

        val distMeters = GisGeometryUtils.calculateDistanceMeters(p1, p2)
        // Approx ~276 km
        assertTrue("Distance should be ~276km, got $distMeters", distMeters in 270000.0..285000.0)
    }

    @Test
    fun testLineLength() {
        val points = listOf(
            LatLngPoint(0.0, 0.0),
            LatLngPoint(0.0, 1.0), // ~111.3km along equator
            LatLngPoint(0.0, 2.0)  // ~111.3km along equator
        )

        val totalDist = GisGeometryUtils.calculateLineLengthMeters(points)
        assertTrue("Length should be ~222km, got $totalDist", totalDist in 220000.0..225000.0)
    }

    @Test
    fun testPolygonAreaCalculation() {
        // A ~100m x 100m square parcel at latitude 0 (Equator)
        // 1 degree lat ~ 111,320 m -> 100m is ~ 0.0008983 degrees
        val p1 = LatLngPoint(0.0, 0.0)
        val p2 = LatLngPoint(0.0, 0.0008983)
        val p3 = LatLngPoint(0.0008983, 0.0008983)
        val p4 = LatLngPoint(0.0008983, 0.0)

        val area = GisGeometryUtils.calculatePolygonAreaSqMeters(listOf(p1, p2, p3, p4))
        // 100m x 100m = 10,000 m² (1 Hectare)
        assertTrue("Area should be ~10,000 m², got $area", area in 9800.0..10200.0)

        val summary = GisGeometryUtils.formatAreaSummary(area)
        assertTrue(summary.contains("m²"))
        assertTrue(summary.contains("ha"))
        assertTrue(summary.contains("acres"))
    }
}
