package com.example.parcelsurveyor.gnss

object NmeaParser {

    /**
     * Parses NMEA 0183 sentences (supports $GPGGA, $GNGGA, $GDRMC, etc.)
     * Returns a GnssPosition object if parsing succeeds, or null if invalid sentence.
     */
    fun parseNmeaLine(line: String, isBluetooth: Boolean = true): GnssPosition? {
        val trimmed = line.trim()
        if (!trimmed.startsWith("$")) return null

        // Strip checksum if present (e.g. *52)
        val cleanLine = if (trimmed.contains("*")) {
            trimmed.substring(0, trimmed.indexOf("*"))
        } else {
            trimmed
        }

        val tokens = cleanLine.split(",")
        if (tokens.isEmpty()) return null

        val sentenceType = tokens[0]
        if (sentenceType.endsWith("GGA")) {
            return parseGgaSentence(tokens, isBluetooth)
        }

        return null
    }

    private fun parseGgaSentence(tokens: List<String>, isBluetooth: Boolean): GnssPosition? {
        if (tokens.size < 10) return null

        try {
            val rawLat = tokens[2]
            val latDir = tokens[3]
            val rawLng = tokens[4]
            val lngDir = tokens[5]
            val fixCodeStr = tokens[6]
            val satsStr = tokens[7]
            val hdopStr = tokens[8]
            val altStr = tokens[9]

            if (rawLat.isEmpty() || rawLng.isEmpty()) return null

            val lat = parseNmeaLatitude(rawLat, latDir) ?: return null
            val lng = parseNmeaLongitude(rawLng, lngDir) ?: return null

            val fixCode = fixCodeStr.toIntOrNull() ?: 0
            val fixQuality = GnssFixQuality.fromCode(fixCode)
            val sats = satsStr.toIntOrNull() ?: 0
            val hdop = hdopStr.toDoubleOrNull() ?: 0.0
            val altitude = altStr.toDoubleOrNull() ?: 0.0

            return GnssPosition(
                latitude = lat,
                longitude = lng,
                altitude = altitude,
                fixQuality = fixQuality,
                satellitesCount = sats,
                hdop = hdop,
                timestamp = System.currentTimeMillis(),
                isBluetooth = isBluetooth
            )
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Converts NMEA Latitude format DDMM.MMMM to decimal degrees.
     * Example: "1525.0020", "S" -> -15.4167
     */
    fun parseNmeaLatitude(nmeaLat: String, direction: String): Double? {
        if (nmeaLat.length < 4) return null
        val deg = nmeaLat.substring(0, 2).toDoubleOrNull() ?: return null
        val min = nmeaLat.substring(2).toDoubleOrNull() ?: return null
        var decimal = deg + (min / 60.0)
        if (direction.equals("S", ignoreCase = true)) {
            decimal = -decimal
        }
        return decimal
    }

    /**
     * Converts NMEA Longitude format DDDMM.MMMM to decimal degrees.
     * Example: "02817.0000", "E" -> 28.28333
     */
    fun parseNmeaLongitude(nmeaLng: String, direction: String): Double? {
        if (nmeaLng.length < 5) return null
        val deg = nmeaLng.substring(0, 3).toDoubleOrNull() ?: return null
        val min = nmeaLng.substring(3).toDoubleOrNull() ?: return null
        var decimal = deg + (min / 60.0)
        if (direction.equals("W", ignoreCase = true)) {
            decimal = -decimal
        }
        return decimal
    }
}
