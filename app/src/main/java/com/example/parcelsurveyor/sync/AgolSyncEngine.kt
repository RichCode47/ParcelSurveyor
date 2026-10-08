package com.example.parcelsurveyor.sync

import com.example.parcelsurveyor.data.FeatureLayerType
import com.example.parcelsurveyor.data.GisDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SyncResult(
    val success: Boolean,
    val syncedCount: Int,
    val errorMessage: String? = null
)

class AgolSyncEngine(private val dbHelper: GisDatabaseHelper) {

    suspend fun syncAllLayers(agolServiceUrl: String): SyncResult = withContext(Dispatchers.IO) {
        var totalSynced = 0

        try {
            for (layer in FeatureLayerType.ALL_LAYERS) {
                val unsynced = dbHelper.getUnsyncedFeatures(layer.tableName)
                if (unsynced.isEmpty()) continue

                val addsArray = JSONArray()
                val syncedGlobalIds = mutableListOf<String>()

                for (record in unsynced) {
                    val esriGeom = JSONObject()
                    val spatialRef = JSONObject().apply { put("wkid", 4326) }
                    esriGeom.put("spatialReference", spatialRef)

                    if (layer.isPoint) {
                        val geomObj = JSONObject(record.geometryJson)
                        esriGeom.put("x", geomObj.getDouble("lng"))
                        esriGeom.put("y", geomObj.getDouble("lat"))
                    } else if (layer == FeatureLayerType.DETAIL_LINE) {
                        val pointsArr = JSONArray(record.geometryJson)
                        val pathArr = JSONArray()
                        for (i in 0 until pointsArr.length()) {
                            val pt = pointsArr.getJSONObject(i)
                            val coord = JSONArray().apply {
                                put(pt.getDouble("lng"))
                                put(pt.getDouble("lat"))
                            }
                            pathArr.put(coord)
                        }
                        val pathsArr = JSONArray().apply { put(pathArr) }
                        esriGeom.put("paths", pathsArr)
                    } else if (layer == FeatureLayerType.DETAIL_POLYGON) {
                        val pointsArr = JSONArray(record.geometryJson)
                        val ringArr = JSONArray()
                        for (i in 0 until pointsArr.length()) {
                            val pt = pointsArr.getJSONObject(i)
                            val coord = JSONArray().apply {
                                put(pt.getDouble("lng"))
                                put(pt.getDouble("lat"))
                            }
                            ringArr.put(coord)
                        }
                        // Close ring if not closed
                        if (pointsArr.length() > 0) {
                            val first = pointsArr.getJSONObject(0)
                            val last = pointsArr.getJSONObject(pointsArr.length() - 1)
                            if (first.getDouble("lng") != last.getDouble("lng") ||
                                first.getDouble("lat") != last.getDouble("lat")
                            ) {
                                val closedCoord = JSONArray().apply {
                                    put(first.getDouble("lng"))
                                    put(first.getDouble("lat"))
                                }
                                ringArr.put(closedCoord)
                            }
                        }
                        val ringsArr = JSONArray().apply { put(ringArr) }
                        esriGeom.put("rings", ringsArr)
                    }

                    val attributes = JSONObject().apply {
                        put("GlobalID", record.globalId)
                        put("Notes", record.notes)
                    }

                    val addFeature = JSONObject().apply {
                        put("geometry", esriGeom)
                        put("attributes", attributes)
                    }

                    addsArray.put(addFeature)
                    syncedGlobalIds.add(record.globalId)
                }

                val endpoint = "${agolServiceUrl.trimEnd('/')}/${layer.agolLayerIndex}/applyEdits"
                val postData = "adds=" + URLEncoder.encode(addsArray.toString(), "UTF-8") + "&f=json"

                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                OutputStreamWriter(conn.outputStream).use { writer ->
                    writer.write(postData)
                    writer.flush()
                }

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val response = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        response.append(line)
                    }
                    reader.close()

                    dbHelper.markAsSynced(layer.tableName, syncedGlobalIds)
                    totalSynced += syncedGlobalIds.size
                } else {
                    return@withContext SyncResult(
                        success = false,
                        syncedCount = totalSynced,
                        errorMessage = "HTTP Error $responseCode syncing ${layer.tableName}"
                    )
                }
            }

            // Sync Unsynced Attachments
            syncPhotoAttachments(agolServiceUrl)

            SyncResult(success = true, syncedCount = totalSynced)
        } catch (e: Exception) {
            SyncResult(success = false, syncedCount = totalSynced, errorMessage = e.localizedMessage)
        }
    }

    private fun syncPhotoAttachments(agolServiceUrl: String) {
        val unsyncedAttachments = dbHelper.getUnsyncedAttachments()
        for (att in unsyncedAttachments) {
            val file = File(att.photoPath)
            if (!file.exists()) continue

            try {
                // Layer index default 0 for detail points
                val endpoint = "${agolServiceUrl.trimEnd('/')}/0/${att.globalId}/addAttachment?f=json"
                val boundary = "===Boundary" + System.currentTimeMillis()
                val url = URL(endpoint)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                conn.outputStream.use { os ->
                    PrintWriter(OutputStreamWriter(os, "UTF-8"), true).use { writer ->
                        writer.append("--$boundary\r\n")
                        writer.append("Content-Disposition: form-data; name=\"attachment\"; filename=\"${file.name}\"\r\n")
                        writer.append("Content-Type: image/jpeg\r\n\r\n")
                        writer.flush()

                        FileInputStream(file).use { fis ->
                            val buffer = ByteArray(4096)
                            var bytesRead: Int
                            while (fis.read(buffer).also { bytesRead = it } != -1) {
                                os.write(buffer, 0, bytesRead)
                            }
                            os.flush()
                        }

                        writer.append("\r\n--$boundary--\r\n")
                        writer.flush()
                    }
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    dbHelper.markAttachmentSynced(att.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
