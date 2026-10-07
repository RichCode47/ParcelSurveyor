package com.example.parcelsurveyor.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.FeatureRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

object GisDataExporter {

    private fun getExportDirectory(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Exports features to a CSV file.
     */
    fun exportToCsv(
        context: Context,
        data: List<Pair<FeatureLayerType, List<FeatureRecord>>>
    ): File {
        val file = File(getExportDirectory(context), "ParcelSurvey_Export.csv")
        FileWriter(file).use { writer ->
            writer.append("GlobalID,Layer,Latitude,Longitude,Notes,Sync_Status\n")

            for ((layerType, records) in data) {
                for (rec in records) {
                    try {
                        val isSynced = if (rec.syncStatus == 0) "Synced" else "Local Edit"
                        if (layerType.isPoint) {
                            val obj = JSONObject(rec.geometryJson)
                            val lat = obj.getDouble("lat")
                            val lng = obj.getDouble("lng")
                            writer.append("\"${rec.globalId}\",\"${layerType.displayName}\",$lat,$lng,\"${rec.notes}\",\"$isSynced\"\n")
                        } else {
                            val arr = JSONArray(rec.geometryJson)
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONObject(i)
                                val lat = pt.getDouble("lat")
                                val lng = pt.getDouble("lng")
                                writer.append("\"${rec.globalId}\",\"${layerType.displayName}_pt${i + 1}\",$lat,$lng,\"${rec.notes}\",\"$isSynced\"\n")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
        return file
    }

    /**
     * Exports features to a Google Earth KML file.
     */
    fun exportToKml(
        context: Context,
        data: List<Pair<FeatureLayerType, List<FeatureRecord>>>
    ): File {
        val file = File(getExportDirectory(context), "ParcelSurvey_Export.kml")
        FileWriter(file).use { writer ->
            writer.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            writer.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
            writer.append("  <Document>\n")
            writer.append("    <name>Parcel Surveyor Field Collection</name>\n")

            for ((layerType, records) in data) {
                for (rec in records) {
                    try {
                        writer.append("    <Placemark>\n")
                        writer.append("      <name>${layerType.displayName}: ${rec.notes}</name>\n")
                        writer.append("      <description>GlobalID: ${rec.globalId}</description>\n")

                        if (layerType.isPoint) {
                            val obj = JSONObject(rec.geometryJson)
                            val lat = obj.getDouble("lat")
                            val lng = obj.getDouble("lng")
                            writer.append("      <Point><coordinates>$lng,$lat,0</coordinates></Point>\n")
                        } else if (layerType == FeatureLayerType.DETAIL_LINE) {
                            val arr = JSONArray(rec.geometryJson)
                            val coords = mutableListOf<String>()
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONObject(i)
                                coords.add("${pt.getDouble("lng")},${pt.getDouble("lat")},0")
                            }
                            writer.append("      <LineString><coordinates>${coords.joinToString(" ")}</coordinates></LineString>\n")
                        } else if (layerType == FeatureLayerType.DETAIL_POLYGON) {
                            val arr = JSONArray(rec.geometryJson)
                            val coords = mutableListOf<String>()
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONObject(i)
                                coords.add("${pt.getDouble("lng")},${pt.getDouble("lat")},0")
                            }
                            if (coords.isNotEmpty()) {
                                coords.add(coords[0]) // Close ring
                            }
                            writer.append("      <Polygon><outerBoundaryIs><LinearRing><coordinates>${coords.joinToString(" ")}</coordinates></LinearRing></outerBoundaryIs></Polygon>\n")
                        }
                        writer.append("    </Placemark>\n")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            writer.append("  </Document>\n")
            writer.append("</kml>\n")
        }
        return file
    }

    /**
     * Exports features to a GeoJSON file.
     */
    fun exportToGeoJson(
        context: Context,
        data: List<Pair<FeatureLayerType, List<FeatureRecord>>>
    ): File {
        val root = JSONObject().apply {
            put("type", "FeatureCollection")
        }
        val featuresArr = JSONArray()

        for ((layerType, records) in data) {
            for (rec in records) {
                try {
                    val featureObj = JSONObject().apply {
                        put("type", "Feature")
                    }

                    val props = JSONObject().apply {
                        put("GlobalID", rec.globalId)
                        put("Layer", layerType.displayName)
                        put("Notes", rec.notes)
                        put("SyncStatus", rec.syncStatus)
                    }
                    featureObj.put("properties", props)

                    val geomObj = JSONObject()
                    if (layerType.isPoint) {
                        val pt = JSONObject(rec.geometryJson)
                        geomObj.put("type", "Point")
                        geomObj.put("coordinates", JSONArray().apply {
                            put(pt.getDouble("lng"))
                            put(pt.getDouble("lat"))
                        })
                    } else if (layerType == FeatureLayerType.DETAIL_LINE) {
                        val arr = JSONArray(rec.geometryJson)
                        val lineCoords = JSONArray()
                        for (i in 0 until arr.length()) {
                            val pt = arr.getJSONObject(i)
                            lineCoords.put(JSONArray().apply {
                                put(pt.getDouble("lng"))
                                put(pt.getDouble("lat"))
                            })
                        }
                        geomObj.put("type", "LineString")
                        geomObj.put("coordinates", lineCoords)
                    } else if (layerType == FeatureLayerType.DETAIL_POLYGON) {
                        val arr = JSONArray(rec.geometryJson)
                        val ringCoords = JSONArray()
                        for (i in 0 until arr.length()) {
                            val pt = arr.getJSONObject(i)
                            ringCoords.put(JSONArray().apply {
                                put(pt.getDouble("lng"))
                                put(pt.getDouble("lat"))
                            })
                        }
                        if (arr.length() > 0) {
                            val first = arr.getJSONObject(0)
                            ringCoords.put(JSONArray().apply {
                                put(first.getDouble("lng"))
                                put(first.getDouble("lat"))
                            })
                        }
                        geomObj.put("type", "Polygon")
                        geomObj.put("coordinates", JSONArray().apply { put(ringCoords) })
                    }
                    featureObj.put("geometry", geomObj)
                    featuresArr.put(featureObj)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        root.put("features", featuresArr)

        val file = File(getExportDirectory(context), "ParcelSurvey_Export.geojson")
        file.writeText(root.toString(2))
        return file
    }

    /**
     * Shares an exported file via Android Share Intent.
     */
    fun shareExportedFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Survey Export File"))
    }
}
