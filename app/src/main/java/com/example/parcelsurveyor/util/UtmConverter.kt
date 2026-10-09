package com.example.parcelsurveyor.util

import com.example.parcelsurveyor.data.LatLngPoint
import java.util.Locale
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/**
 * Data class representing a projected Universal Transverse Mercator (UTM) coordinate.
 *
 * @property zone UTM longitudinal zone number (1 - 60).
 * @property hemisphere Hemisphere indicator character ('N' or 'S').
 * @property easting Easting coordinate in meters.
 * @property northing Northing coordinate in meters.
 */
data class UtmCoordinate(
    val zone: Int,
    val hemisphere: Char,
    val easting: Double,
    val northing: Double
) {
    /** Formatted string representation of the UTM coordinate. */
    val formattedString: String
        get() = String.format(
            Locale.US,
            "Zone %d%c | E: %,.1fm | N: %,.1fm",
            zone, hemisphere, easting, northing
        )
}

/**
 * Utility object for converting WGS84 geographic coordinates (Latitude / Longitude)
 * into projected Universal Transverse Mercator (UTM) coordinates.
 */
object UtmConverter {

    /** Central meridian scale factor for UTM projection. */
    private const val K0 = 0.9996

    /** WGS84 ellipsoid semi-major axis in meters. */
    private const val A = 6378137.0

    /** WGS84 ellipsoid flattening. */
    private const val F = 1.0 / 298.257223563

    /** WGS84 ellipsoid semi-minor axis in meters. */
    private const val B = A * (1.0 - F)

    /** First eccentricity squared. */
    private const val E2 = (A * A - B * B) / (A * A)

    /** Second eccentricity squared. */
    private const val EP2 = (A * A - B * B) / (B * B)

    /**
     * Converts WGS84 latitude and longitude into a projected [UtmCoordinate].
     *
     * @param lat Latitude in decimal degrees.
     * @param lng Longitude in decimal degrees.
     * @return Projected [UtmCoordinate].
     */
    fun fromLatLng(lat: Double, lng: Double): UtmCoordinate {
        val latRad = Math.toRadians(lat)
        val lngRad = Math.toRadians(lng)

        var zone = ((lng + 180.0) / 6.0).toInt() + 1
        if (lat >= 56.0 && lat < 64.0 && lng >= 3.0 && lng < 12.0) {
            zone = 32
        }

        // Special zones for Svalbard
        if (lat >= 72.0 && lat < 84.0) {
            if (lng >= 0.0 && lng < 9.0) zone = 31
            else if (lng >= 9.0 && lng < 21.0) zone = 33
            else if (lng >= 21.0 && lng < 33.0) zone = 35
            else if (lng >= 33.0 && lng < 42.0) zone = 37
        }

        val lng0 = Math.toRadians(((zone - 1) * 6 - 180 + 3).toDouble())

        val N = A / Math.sqrt(1.0 - E2 * sin(latRad).pow(2.0))
        val T = tan(latRad).pow(2.0)
        val C = EP2 * cos(latRad).pow(2.0)
        val A_coeff = cos(latRad) * (lngRad - lng0)

        val M = A * (
                (1.0 - E2 / 4.0 - 3.0 * E2 * E2 / 64.0 - 5.0 * E2 * E2 * E2 / 256.0) * latRad
                        - (3.0 * E2 / 8.0 + 3.0 * E2 * E2 / 32.0 + 45.0 * E2 * E2 * E2 / 1024.0) * sin(2.0 * latRad)
                        + (15.0 * E2 * E2 / 256.0 + 45.0 * E2 * E2 * E2 / 1024.0) * sin(4.0 * latRad)
                        - (35.0 * E2 * E2 * E2 / 3072.0) * sin(6.0 * latRad)
                )

        val easting = K0 * N * (
                A_coeff + (1.0 - T + C) * A_coeff.pow(3.0) / 6.0
                        + (5.0 - 18.0 * T + T * T + 72.0 * C - 58.0 * EP2) * A_coeff.pow(5.0) / 120.0
                ) + 500000.0

        var northing = K0 * (
                M + N * tan(latRad) * (
                        A_coeff.pow(2.0) / 2.0
                                + (5.0 - T + 9.0 * C + 4.0 * C * C) * A_coeff.pow(4.0) / 24.0
                                + (61.0 - 58.0 * T + T * T + 600.0 * C - 330.0 * EP2) * A_coeff.pow(6.0) / 720.0
                        )
                )

        val hemisphere = if (lat >= 0) 'N' else 'S'
        if (lat < 0) {
            northing += 10000000.0
        }

        return UtmCoordinate(
            zone = zone,
            hemisphere = hemisphere,
            easting = easting,
            northing = northing
        )
    }

    /**
     * Converts a [LatLngPoint] into a projected [UtmCoordinate].
     *
     * @param point Geographic coordinate [LatLngPoint].
     * @return Projected [UtmCoordinate].
     */
    fun fromLatLngPoint(point: LatLngPoint): UtmCoordinate {
        return fromLatLng(point.latitude, point.longitude)
    }
}
