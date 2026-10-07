package com.example.parcelsurveyor

import com.example.parcelsurveyor.gnss.GnssFixQuality
import com.example.parcelsurveyor.gnss.NmeaParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NmeaParserTest {

    @Test
    fun testParseRtkFixedGgaSentence() {
        // $GNGGA sentence with RTK Fix (code 4), 18 satellites, HDOP 0.8, Alt 1150.2m
        val sentence = "\$GNGGA,123519.00,1525.0020,S,02817.0000,E,4,18,0.8,1150.2,M,-30.0,M,,*52"

        val pos = NmeaParser.parseNmeaLine(sentence)
        assertNotNull(pos)
        pos!!

        assertEquals(-15.4167, pos.latitude, 0.0001)
        assertEquals(28.28333, pos.longitude, 0.0001)
        assertEquals(1150.2, pos.altitude, 0.1)
        assertEquals(GnssFixQuality.RTK_FIXED, pos.fixQuality)
        assertEquals(18, pos.satellitesCount)
        assertEquals(0.8, pos.hdop, 0.01)
    }

    @Test
    fun testParseRtkFloatGgaSentence() {
        // $GPGGA sentence with RTK Float (code 5), 12 satellites
        val sentence = "\$GPGGA,123519.00,0112.3456,N,10345.6789,W,5,12,1.2,50.0,M,0.0,M,,*47"

        val pos = NmeaParser.parseNmeaLine(sentence)
        assertNotNull(pos)
        pos!!

        assertEquals(1.20576, pos.latitude, 0.0001)
        assertEquals(-103.761315, pos.longitude, 0.0001)
        assertEquals(GnssFixQuality.RTK_FLOAT, pos.fixQuality)
        assertEquals(12, pos.satellitesCount)
    }

    @Test
    fun testInvalidNmeaLines() {
        assertNull(NmeaParser.parseNmeaLine(""))
        assertNull(NmeaParser.parseNmeaLine("NOT_A_NMEA_LINE"))
        assertNull(NmeaParser.parseNmeaLine("\$GPGSA,A,3,01,02,03,,,,,,,,,,2.5,1.2,2.2*03"))
    }
}
