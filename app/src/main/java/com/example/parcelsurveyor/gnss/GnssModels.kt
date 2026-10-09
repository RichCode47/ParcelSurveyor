package com.example.parcelsurveyor.gnss

/**
 * Enum representing GNSS fix quality grades (such as GPS Single, RTK Fixed, RTK Float)
 * extracted from NMEA GPGGA / GNGGA sentence fix indicator codes.
 *
 * @property code NMEA fix quality integer code.
 * @property displayName Human-readable fix quality name.
 * @property colorHex Hex color code string associated with the fix quality for UI badges.
 */
enum class GnssFixQuality(
    val code: Int,
    val displayName: String,
    val colorHex: String
) {
    /** No valid position fix available. */
    NO_FIX(0, "No Fix", "#888888"),

    /** Standard GPS autonomous single point fix. */
    GPS_SPS(1, "GPS Single", "#2196F3"),

    /** Differential GPS (DGPS) corrected fix. */
    DGPS(2, "DGPS", "#03A9F4"),

    /** Precision Positioning Service (PPS) fix. */
    PPS(3, "PPS Fix", "#00BCD4"),

    /** Real-Time Kinematic (RTK) Fixed high-precision centimeter-level fix. */
    RTK_FIXED(4, "RTK FIX", "#4CAF50"),

    /** Real-Time Kinematic (RTK) Float decimeter-level fix. */
    RTK_FLOAT(5, "RTK FLOAT", "#FFC107"),

    /** Estimated (dead reckoning) position fix. */
    ESTIMATED(6, "Estimated", "#FF9800");

    /**
     * Returns true if this fix quality represents high-precision RTK (Fixed or Float).
     */
    val isHighPrecision: Boolean
        get() = this == RTK_FIXED || this == RTK_FLOAT

    companion object {
        /**
         * Resolves a [GnssFixQuality] from an NMEA fix quality integer code.
         *
         * @param code The integer fix quality code.
         * @return The corresponding [GnssFixQuality], or [NO_FIX] as a default fallback.
         */
        fun fromCode(code: Int): GnssFixQuality {
            return entries.find { it.code == code } ?: NO_FIX
        }
    }
}

/**
 * Data class representing a parsed GNSS position report from internal GPS or an external RTK receiver.
 *
 * @property latitude Latitude in decimal degrees (WGS84).
 * @property longitude Longitude in decimal degrees (WGS84).
 * @property altitude Altitude above mean sea level in meters.
 * @property fixQuality Quality grade of the GNSS position fix.
 * @property satellitesCount Number of satellites used in computing the fix.
 * @property hdop Horizontal Dilution of Precision indicator.
 * @property timestamp Epoch timestamp in milliseconds when the position was recorded.
 * @property isBluetooth True if position originated from an external Bluetooth GNSS receiver.
 */
data class GnssPosition(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val fixQuality: GnssFixQuality = GnssFixQuality.NO_FIX,
    val satellitesCount: Int = 0,
    val hdop: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val isBluetooth: Boolean = false
)

/**
 * Data class representing a paired Bluetooth device (such as an RTK GNSS receiver).
 *
 * @property name Human-readable name of the Bluetooth device.
 * @property address MAC address of the Bluetooth device.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val address: String
)
