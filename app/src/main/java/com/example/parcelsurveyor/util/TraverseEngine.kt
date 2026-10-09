package com.example.parcelsurveyor.util

import com.example.parcelsurveyor.data.LatLngPoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Data class representing an individual traverse survey leg defined by distance and bearing.
 *
 * @property label Identification label for the leg (e.g., "Leg 1-2" or "Beacon 1 -> Beacon 2").
 * @property distanceMeters Distance length in meters.
 * @property bearingDegrees Azimuth bearing in decimal degrees (0° to 360°).
 */
data class TraverseLeg(
    val label: String,
    val distanceMeters: Double,
    val bearingDegrees: Double
)

/**
 * Data class encapsulating full traverse calculation outputs including linear misclosures,
 * precision ratios, and Bowditch (Compass Rule) balanced coordinates.
 *
 * @property startPoint Initial geographic starting point.
 * @property startUtm Initial projected UTM starting coordinate.
 * @property unadjustedUtmPoints List of raw calculated UTM coordinates.
 * @property unadjustedLatLngPoints List of raw calculated geographic coordinates.
 * @property adjustedUtmPoints List of Bowditch-adjusted UTM coordinates.
 * @property adjustedLatLngPoints List of Bowditch-adjusted geographic coordinates.
 * @property totalPerimeterMeters Total perimeter length of the traverse loop in meters.
 * @property misclosureEastMeters Easting misclosure error ($C_E$) in meters.
 * @property misclosureNorthMeters Northing misclosure error ($C_N$) in meters.
 * @property linearMisclosureMeters Total linear error of closure ($E_c$) in meters.
 * @property misclosureAzimuthDegrees Direction of closure error in decimal degrees.
 * @property precisionDenominator Precision ratio denominator (e.g. 15400 for 1:15,400 precision).
 * @property precisionRatioString Formatted precision ratio text (e.g. "1:15,400 (PASS - Cadastral)").
 * @property unadjustedAreaSqMeters Polygon area calculated from unadjusted coordinates in $m^2$.
 * @property adjustedAreaSqMeters Polygon area calculated from Bowditch-adjusted coordinates in $m^2$.
 */
data class TraverseResult(
    val startPoint: LatLngPoint,
    val startUtm: UtmCoordinate,
    val unadjustedUtmPoints: List<UtmCoordinate>,
    val unadjustedLatLngPoints: List<LatLngPoint>,
    val adjustedUtmPoints: List<UtmCoordinate>,
    val adjustedLatLngPoints: List<LatLngPoint>,
    val totalPerimeterMeters: Double,
    val misclosureEastMeters: Double,
    val misclosureNorthMeters: Double,
    val linearMisclosureMeters: Double,
    val misclosureAzimuthDegrees: Double,
    val precisionDenominator: Double,
    val precisionRatioString: String,
    val unadjustedAreaSqMeters: Double,
    val adjustedAreaSqMeters: Double
)

/**
 * Calculation engine for land survey traverse closure, error misclosure analysis,
 * and Bowditch (Compass Rule) coordinate balancing.
 */
object TraverseEngine {

    /**
     * Executes forward traverse calculations and Bowditch balancing given a starting coordinate and a sequence of legs.
     *
     * @param startPoint Initial starting coordinate.
     * @param legs List of traverse legs (distance and bearing).
     * @return Complete [TraverseResult] report.
     */
    fun computeTraverse(startPoint: LatLngPoint, legs: List<TraverseLeg>): TraverseResult {
        val startUtm = UtmConverter.fromLatLngPoint(startPoint)

        val unadjustedUtmList = mutableListOf<UtmCoordinate>()
        unadjustedUtmList.add(startUtm)

        var currentEasting = startUtm.easting
        var currentNorthing = startUtm.northing
        var totalPerimeter = 0.0

        for (leg in legs) {
            val bearingRad = Math.toRadians(leg.bearingDegrees)
            val deltaE = leg.distanceMeters * sin(bearingRad)
            val deltaN = leg.distanceMeters * cos(bearingRad)

            currentEasting += deltaE
            currentNorthing += deltaN
            totalPerimeter += leg.distanceMeters

            unadjustedUtmList.add(
                UtmCoordinate(
                    zone = startUtm.zone,
                    hemisphere = startUtm.hemisphere,
                    easting = currentEasting,
                    northing = currentNorthing
                )
            )
        }

        // Misclosure calculations (difference between final calculated point and start point)
        val finalPoint = unadjustedUtmList.last()
        val cE = finalPoint.easting - startUtm.easting
        val cN = finalPoint.northing - startUtm.northing
        val linearMisclosure = sqrt(cE * cE + cN * cN)

        val misclosureAzimuth = (Math.toDegrees(atan2(cE, cN)) + 360.0) % 360.0

        val precisionDenom = if (linearMisclosure > 1e-6) totalPerimeter / linearMisclosure else 999999.0
        val precisionString = formatPrecisionRatio(precisionDenom)

        // Bowditch (Compass Rule) Adjustment
        val adjustedUtmList = mutableListOf<UtmCoordinate>()
        adjustedUtmList.add(startUtm)

        var accumulatedDist = 0.0
        var prevUnadjEasting = startUtm.easting
        var prevUnadjNorthing = startUtm.northing

        var curAdjEasting = startUtm.easting
        var curAdjNorthing = startUtm.northing

        for (i in legs.indices) {
            val leg = legs[i]
            accumulatedDist += leg.distanceMeters

            val nextUnadj = unadjustedUtmList[i + 1]
            val rawDeltaE = nextUnadj.easting - prevUnadjEasting
            val rawDeltaN = nextUnadj.northing - prevUnadjNorthing

            prevUnadjEasting = nextUnadj.easting
            prevUnadjNorthing = nextUnadj.northing

            // Bowditch corrections proportional to leg length
            val corrE = -cE * (leg.distanceMeters / totalPerimeter)
            val corrN = -cN * (leg.distanceMeters / totalPerimeter)

            val adjDeltaE = rawDeltaE + corrE
            val adjDeltaN = rawDeltaN + corrN

            curAdjEasting += adjDeltaE
            curAdjNorthing += adjDeltaN

            adjustedUtmList.add(
                UtmCoordinate(
                    zone = startUtm.zone,
                    hemisphere = startUtm.hemisphere,
                    easting = curAdjEasting,
                    northing = curAdjNorthing
                )
            )
        }

        // Convert UTM coordinates back to LatLng list
        val unadjustedLatLngList = unadjustedUtmList.map { utmToLatLngApprox(it, startPoint) }
        val adjustedLatLngList = adjustedUtmList.map { utmToLatLngApprox(it, startPoint) }

        val unadjArea = computeUtmPolygonArea(unadjustedUtmList)
        val adjArea = computeUtmPolygonArea(adjustedUtmList)

        return TraverseResult(
            startPoint = startPoint,
            startUtm = startUtm,
            unadjustedUtmPoints = unadjustedUtmList,
            unadjustedLatLngPoints = unadjustedLatLngList,
            adjustedUtmPoints = adjustedUtmList,
            adjustedLatLngPoints = adjustedLatLngList,
            totalPerimeterMeters = totalPerimeter,
            misclosureEastMeters = cE,
            misclosureNorthMeters = cN,
            linearMisclosureMeters = linearMisclosure,
            misclosureAzimuthDegrees = misclosureAzimuth,
            precisionDenominator = precisionDenom,
            precisionRatioString = precisionString,
            unadjustedAreaSqMeters = unadjArea,
            adjustedAreaSqMeters = adjArea
        )
    }

    /**
     * Extracts traverse legs from a list of map coordinates and computes closure & Bowditch adjustment.
     *
     * @param points List of vertex [LatLngPoint] coordinates.
     * @return Complete [TraverseResult].
     */
    fun computeTraverseFromPoints(points: List<LatLngPoint>): TraverseResult {
        if (points.size < 3) {
            val start = points.firstOrNull() ?: LatLngPoint(0.0, 0.0)
            return computeTraverse(start, emptyList())
        }

        val start = points.first()
        val legs = mutableListOf<TraverseLeg>()

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val dist = GisGeometryUtils.calculateDistanceMeters(p1, p2)
            val bearing = GisGeometryUtils.calculateBearingDegrees(p1, p2)
            legs.add(TraverseLeg("Leg ${i + 1}", dist, bearing))
        }

        // Close back to initial point if last point != first point
        val lastPt = points.last()
        if (GisGeometryUtils.calculateDistanceMeters(lastPt, start) > 0.01) {
            val dist = GisGeometryUtils.calculateDistanceMeters(lastPt, start)
            val bearing = GisGeometryUtils.calculateBearingDegrees(lastPt, start)
            legs.add(TraverseLeg("Leg Close", dist, bearing))
        }

        return computeTraverse(start, legs)
    }

    /**
     * Calculates polygon area from UTM planar coordinates using the Shoelace formula.
     *
     * @param utmPoints List of [UtmCoordinate] polygon vertices.
     * @return Area in square meters.
     */
    fun computeUtmPolygonArea(utmPoints: List<UtmCoordinate>): Double {
        if (utmPoints.size < 3) return 0.0
        var areaSum = 0.0
        val n = utmPoints.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            areaSum += utmPoints[i].easting * utmPoints[j].northing
            areaSum -= utmPoints[j].easting * utmPoints[i].northing
        }
        return abs(areaSum) / 2.0
    }

    /**
     * Converts a UTM projected coordinate back to approximate WGS84 LatLng around a reference point.
     */
    private fun utmToLatLngApprox(utm: UtmCoordinate, refLatLng: LatLngPoint): LatLngPoint {
        val refUtm = UtmConverter.fromLatLngPoint(refLatLng)
        val dE = utm.easting - refUtm.easting
        val dN = utm.northing - refUtm.northing

        val meanLatRad = Math.toRadians(refLatLng.latitude)
        val rEarth = 6371000.0

        val dLatDeg = Math.toDegrees(dN / rEarth)
        val dLngDeg = Math.toDegrees(dE / (rEarth * cos(meanLatRad)))

        return LatLngPoint(refLatLng.latitude + dLatDeg, refLatLng.longitude + dLngDeg)
    }

    /**
     * Formats precision ratio integer into cadastral quality status text.
     */
    private fun formatPrecisionRatio(denom: Double): String {
        val roundedDenom = denom.toInt()
        val grade = when {
            roundedDenom >= 10000 -> "EXCELLENT (Cadastral Class A)"
            roundedDenom >= 5000 -> "PASS (Cadastral Standard)"
            roundedDenom >= 2500 -> "FAIR (Detail Survey)"
            else -> "WARNING (Exceeds Tolerance)"
        }
        return String.format(Locale.US, "1:%,d - %s", roundedDenom, grade)
    }
}
