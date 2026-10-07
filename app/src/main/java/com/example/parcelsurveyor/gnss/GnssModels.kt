package com.example.parcelsurveyor.gnss

enum class GnssFixQuality(
    val code: Int,
    val displayName: String,
    val colorHex: String
) {
    NO_FIX(0, "No Fix", "#888888"),
    GPS_SPS(1, "GPS Single", "#2196F3"),
    DGPS(2, "DGPS", "#03A9F4"),
    PPS(3, "PPS Fix", "#00BCD4"),
    RTK_FIXED(4, "RTK FIX", "#4CAF50"),
    RTK_FLOAT(5, "RTK FLOAT", "#FFC107"),
    ESTIMATED(6, "Estimated", "#FF9800");

    val isHighPrecision: Boolean
        get() = this == RTK_FIXED || this == RTK_FLOAT

    companion object {
        fun fromCode(code: Int): GnssFixQuality {
            return entries.find { it.code == code } ?: NO_FIX
        }
    }
}

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

data class BluetoothDeviceInfo(
    val name: String,
    val address: String
)
