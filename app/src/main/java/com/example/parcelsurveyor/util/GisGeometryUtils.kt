package com.example.parcelsurveyor.util

import com.example.parcelsurveyor.data.LatLngPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GisGeometryUtils {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates distance between two LatLngPoints using the Haversine formula.
     */
    fun calculateDistanceMeters(p1: LatLngPoint, p2: LatLngPoint): Double {
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLng = Math.toRadians(p2.longitude - p1.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Calculates total length of a polyline in meters.
     */
    fun calculateLineLengthMeters(points: List<LatLngPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += calculateDistanceMeters(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Calculates geodesic area of a polygon ring in square meters using planar projection.
     */
    fun calculatePolygonAreaSqMeters(points: List<LatLngPoint>): Double {
        if (points.size < 3) return 0.0

        val meanLatRad = Math.toRadians(points.map { it.latitude }.average())

        val xCoords = DoubleArray(points.size)
        val yCoords = DoubleArray(points.size)

        for (i in points.indices) {
            val pt = points[i]
            xCoords[i] = EARTH_RADIUS_METERS * Math.toRadians(pt.longitude) * cos(meanLatRad)
            yCoords[i] = EARTH_RADIUS_METERS * Math.toRadians(pt.latitude)
        }

        var areaSum = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            areaSum += xCoords[i] * yCoords[j]
            areaSum -= xCoords[j] * yCoords[i]
        }

        return abs(areaSum) / 2.0
    }

    /**
     * Formats distance in meters or kilometers.
     */
    fun formatDistanceString(meters: Double): String {
        return if (meters < 1000.0) {
            String.format(Locale.US, "%.1f m", meters)
        } else {
            String.format(Locale.US, "%.2f km", meters / 1000.0)
        }
    }

    /**
     * Formats area into Square Meters, Hectares, and Acres.
     */
    fun formatAreaSummary(sqMeters: Double): String {
        val hectares = sqMeters / 10000.0
        val acres = sqMeters * 0.000247105
        return String.format(Locale.US, "%,.1f m² (%,.3f ha / %,.2f acres)", sqMeters, hectares, acres)
    }
}
