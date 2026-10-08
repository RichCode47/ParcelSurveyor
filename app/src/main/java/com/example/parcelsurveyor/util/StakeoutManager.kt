package com.example.parcelsurveyor.util

import com.example.parcelsurveyor.data.LatLngPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class StakeoutInfo(
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val deltaEastingMeters: Double,
    val deltaNorthingMeters: Double,
    val targetPoint: LatLngPoint,
    val targetName: String
)

object StakeoutManager {

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
