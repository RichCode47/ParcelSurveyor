package com.example.parcelsurveyor.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class GisDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "gis_mvp.db"
        private const val DATABASE_VERSION = 1
        val LAYER_TABLES = listOf("DetailPoint", "ParcelPoint", "DetailLine", "DetailPolygon")
    }

    override fun onCreate(db: SQLiteDatabase) {
        for (layer in LAYER_TABLES) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $layer (
                    globalId TEXT PRIMARY KEY,
                    geometry TEXT NOT NULL,
                    notes TEXT,
                    sync_status INTEGER DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        for (layer in LAYER_TABLES) {
            db.execSQL("DROP TABLE IF EXISTS $layer")
        }
        onCreate(db)
    }

    fun insertFeature(layerName: String, geometryJson: String, notes: String = "Field capture"): String {
        val globalId = "{" + UUID.randomUUID().toString().uppercase() + "}"
        val db = writableDatabase
        val values = ContentValues().apply {
            put("globalId", globalId)
            put("geometry", geometryJson)
            put("notes", notes)
            put("sync_status", 1)
        }
        db.insert(layerName, null, values)
        return globalId
    }

    fun getUnsyncedCount(): Int {
        val db = readableDatabase
        var total = 0
        for (layer in LAYER_TABLES) {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM $layer WHERE sync_status = 1", null)
            if (cursor.moveToFirst()) {
                total += cursor.getInt(0)
            }
            cursor.close()
        }
        return total
    }

    fun getUnsyncedFeatures(layerName: String): List<FeatureRecord> {
        val db = readableDatabase
        val list = mutableListOf<FeatureRecord>()
        val cursor = db.query(
            layerName,
            arrayOf("globalId", "geometry", "notes", "sync_status"),
            "sync_status = 1",
            null,
            null,
            null,
            null
        )
        while (cursor.moveToNext()) {
            list.add(
                FeatureRecord(
                    globalId = cursor.getString(0),
                    geometryJson = cursor.getString(1),
                    notes = cursor.getString(2) ?: "",
                    syncStatus = cursor.getInt(3)
                )
            )
        }
        cursor.close()
        return list
    }

    fun getAllFeatures(layerName: String): List<FeatureRecord> {
        val db = readableDatabase
        val list = mutableListOf<FeatureRecord>()
        val cursor = db.query(
            layerName,
            arrayOf("globalId", "geometry", "notes", "sync_status"),
            null,
            null,
            null,
            null,
            null
        )
        while (cursor.moveToNext()) {
            list.add(
                FeatureRecord(
                    globalId = cursor.getString(0),
                    geometryJson = cursor.getString(1),
                    notes = cursor.getString(2) ?: "",
                    syncStatus = cursor.getInt(3)
                )
            )
        }
        cursor.close()
        return list
    }

    fun markAsSynced(layerName: String, globalIds: List<String>) {
        if (globalIds.isEmpty()) return
        val db = writableDatabase
        val formattedIds = globalIds.joinToString(",") { "'$it'" }
        db.execSQL("UPDATE $layerName SET sync_status = 0 WHERE globalId IN ($formattedIds)")
    }
}
