package com.example.parcelsurveyor

import com.example.parcelsurveyor.data.FeatureLayerType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GisDataTest {

    @Test
    fun testFeatureLayerTypes() {
        assertEquals(4, FeatureLayerType.ALL_LAYERS.size)
        assertTrue(FeatureLayerType.DETAIL_POINT.isPoint)
        assertTrue(FeatureLayerType.PARCEL_POINT.isPoint)
        assertTrue(!FeatureLayerType.DETAIL_LINE.isPoint)
        assertTrue(!FeatureLayerType.DETAIL_POLYGON.isPoint)
    }

    @Test
    fun testEsriPointJsonFormat() {
        val geomObj = JSONObject().apply {
            put("lat", -15.4167)
            put("lng", 28.2833)
        }

        val esriGeom = JSONObject().apply {
            put("spatialReference", JSONObject().apply { put("wkid", 4326) })
            put("x", geomObj.getDouble("lng"))
            put("y", geomObj.getDouble("lat"))
        }

        assertEquals(28.2833, esriGeom.getDouble("x"), 0.0001)
        assertEquals(-15.4167, esriGeom.getDouble("y"), 0.0001)
        assertEquals(4326, esriGeom.getJSONObject("spatialReference").getInt("wkid"))
    }

    @Test
    fun testEsriPolygonRingClosure() {
        val pointsArr = JSONArray().apply {
            put(JSONObject().apply { put("lat", 10.0); put("lng", 20.0) })
            put(JSONObject().apply { put("lat", 10.0); put("lng", 30.0) })
            put(JSONObject().apply { put("lat", 20.0); put("lng", 30.0) })
        }

        val ringArr = JSONArray()
        for (i in 0 until pointsArr.length()) {
            val pt = pointsArr.getJSONObject(i)
            ringArr.put(JSONArray().apply {
                put(pt.getDouble("lng"))
                put(pt.getDouble("lat"))
            })
        }

        // Close ring
        val first = pointsArr.getJSONObject(0)
        ringArr.put(JSONArray().apply {
            put(first.getDouble("lng"))
            put(first.getDouble("lat"))
        })

        assertEquals(4, ringArr.length())
        assertEquals(20.0, ringArr.getJSONArray(0).getDouble(0), 0.0001)
        assertEquals(20.0, ringArr.getJSONArray(3).getDouble(0), 0.0001)
    }
}
