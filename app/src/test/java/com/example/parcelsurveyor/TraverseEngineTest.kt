package com.example.parcelsurveyor

import com.example.parcelsurveyor.data.LatLngPoint
import com.example.parcelsurveyor.util.TraverseEngine
import com.example.parcelsurveyor.util.TraverseLeg
import com.example.parcelsurveyor.util.UtmConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraverseEngineTest {

    @Test
    fun testPerfectClosedTraverse() {
        val startPoint = LatLngPoint(-15.4167, 28.2833) // Lusaka, Zambia

        val legs = listOf(
            TraverseLeg("Leg 1 (East)", 100.0, 90.0),
            TraverseLeg("Leg 2 (North)", 100.0, 0.0),
            TraverseLeg("Leg 3 (West)", 100.0, 270.0),
            TraverseLeg("Leg 4 (South)", 100.0, 180.0)
        )

        val result = TraverseEngine.computeTraverse(startPoint, legs)

        assertEquals("Total perimeter should be 400m", 400.0, result.totalPerimeterMeters, 0.001)
        assertTrue("Linear misclosure should be near 0", result.linearMisclosureMeters < 0.001)
        assertTrue("Adjusted area should be ~10,000 m²", result.adjustedAreaSqMeters in 9900.0..10100.0)
        assertTrue(result.precisionRatioString.contains("EXCELLENT"))
    }

    @Test
    fun testTraverseMisclosureAndBowditchAdjustment() {
        val startPoint = LatLngPoint(-15.4167, 28.2833)

        // Square loop with 0.10m intentional error in leg 4 distance (99.90m instead of 100.0m)
        val legs = listOf(
            TraverseLeg("Leg 1", 100.0, 90.0),
            TraverseLeg("Leg 2", 100.0, 0.0),
            TraverseLeg("Leg 3", 100.0, 270.0),
            TraverseLeg("Leg 4", 99.90, 180.0)
        )

        val result = TraverseEngine.computeTraverse(startPoint, legs)

        assertTrue("Linear misclosure should be ~0.10m", result.linearMisclosureMeters in 0.09..0.11)
        assertTrue("Precision ratio should be ~1:3990", result.precisionDenominator in 3800.0..4200.0)

        // Verify Bowditch adjustment forces the final adjusted coordinate to exactly match start point
        val startUtm = UtmConverter.fromLatLngPoint(startPoint)
        val finalAdjUtm = result.adjustedUtmPoints.last()

        assertEquals("Final adjusted Easting must equal start Easting", startUtm.easting, finalAdjUtm.easting, 0.0001)
        assertEquals("Final adjusted Northing must equal start Northing", startUtm.northing, finalAdjUtm.northing, 0.0001)
    }

    @Test
    fun testComputeTraverseFromPoints() {
        val p1 = LatLngPoint(-15.4167, 28.2833)
        val p2 = LatLngPoint(-15.4167, 28.2842)
        val p3 = LatLngPoint(-15.4158, 28.2842)
        val p4 = LatLngPoint(-15.4158, 28.2833)

        val result = TraverseEngine.computeTraverseFromPoints(listOf(p1, p2, p3, p4))

        assertTrue("Perimeter should be > 0", result.totalPerimeterMeters > 0)
        assertTrue("Area should be > 0", result.adjustedAreaSqMeters > 0)
        assertTrue(result.precisionRatioString.isNotEmpty())
    }
}
