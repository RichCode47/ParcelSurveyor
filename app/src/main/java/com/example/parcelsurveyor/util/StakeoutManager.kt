package com.example.parcelsurveyor.util

import com.example.parcelsurveyor.data.LatLngPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Data class representing real-time stakeout navigation metrics between a current position
 * and a target survey beacon.
 *
 * @property distanceMeters Distance to target in meters.
 * @property bearingDegrees Compass bearing / azimuth angle to target in degrees (0 - 360).
 * @property deltaEastingMeters Easting coordinate offset in meters (+ = East, - = West).
 * @property deltaNorthingMeters Northing coordinate offset in meters (+ = North, - = South).
 * @property targetPoint Target geographic coordinates ([LatLngPoint]).
 * @property targetName Human-readable name or label of the target beacon.
 */
data class StakeoutInfo(
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val deltaEastingMeters: Double,
    val deltaNorthingMeters: Double,
    val targetPoint: LatLngPoint,
    val targetName: String
)

/**
 * Utility object for calculating stakeout navigation metrics (distance, bearing,
 * and UTM Easting/Northing offsets) between the current GNSS position and a selected target point.
 */
object StakeoutManager {

    /**
     * Calculates stakeout navigation information from the current position to a target point.
     *
     * @param currentPos Current geographic coordinates ([LatLngPoint]) of the surveyor.
     * @param targetPos Target geographic coordinates ([LatLngPoint]) of the stakeout beacon.
     * @param targetName Optional name or label of the target beacon.
     * @return Calculated [StakeoutInfo] metrics.
     */
    fun calculateStakeout(
        currentPos: LatLngPoint,
        targetPos: LatLngPoint,
        targetName: String = "Target Beacon"
    ): StakeoutInfo {
        val distance = GisGeometryUtils.calculateDistanceMeters(currentPos, targetPos)

        val lat1 = Math.toRadians(currentPos.latitude)
        val lat2 = Math.toRadians(targetPos.latitude)
        val dLng = Math.toRadians(targetPos.longitude - currentPos.longitude)

        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        var azimuth = Math.toDegrees(atan2(y, x))
        azimuth = (azimuth + 360.0) % 360.0

        val currentUtm = UtmConverter.fromLatLngPoint(currentPos)
        val targetUtm = UtmConverter.fromLatLngPoint(targetPos)

        val dE = targetUtm.easting - currentUtm.easting
        val dN = targetUtm.northing - currentUtm.northing

        return StakeoutInfo(
            distanceMeters = distance,
            bearingDegrees = azimuth,
            deltaEastingMeters = dE,
            deltaNorthingMeters = dN,
            targetPoint = targetPos,
            targetName = targetName
        )
    }
}
