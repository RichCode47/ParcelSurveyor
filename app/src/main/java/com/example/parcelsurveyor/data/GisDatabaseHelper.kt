package com.example.parcelsurveyor.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

/**
 * Data class representing a photo or media attachment record linked to a GIS feature.
 *
 * @property id Unique auto-incrementing database primary key ID.
 * @property globalId Globally unique identifier (GUID) of the parent GIS feature.
 * @property photoPath Absolute file path to the stored attachment image on disk.
 * @property syncStatus Synchronization status flag (1 = unsynced, 0 = synced to server).
 */
data class AttachmentRecord(
    val id: Long,
    val globalId: String,
    val photoPath: String,
    val syncStatus: Int
)

/**
 * SQLite database helper managing local storage of GIS feature layers (points, lines, polygons)
 * and photo attachments for offline field data collection and synchronization.
 *
 * @property context Application context used to initialize the database.
 */
class GisDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        /** Name of the underlying SQLite database file. */
        private const val DATABASE_NAME = "gis_mvp.db"

        /** Current database schema version. */
        private const val DATABASE_VERSION = 2

        /** List of spatial feature layer table names managed by the database. */
        val LAYER_TABLES = listOf("DetailPoint", "ParcelPoint", "DetailLine", "DetailPolygon")
    }

    /**
     * Called when the database is created for the first time. Creates spatial tables for each
     * feature layer and the FeatureAttachments table.
     *
     * @param db The SQLite database instance.
     */
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

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS FeatureAttachments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                globalId TEXT NOT NULL,
                photoPath TEXT NOT NULL,
                sync_status INTEGER DEFAULT 1
            )
            """.trimIndent()
        )
    }

    /**
     * Called when the database needs to be upgraded to a new version.
     *
     * @param db The SQLite database instance.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS FeatureAttachments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    globalId TEXT NOT NULL,
                    photoPath TEXT NOT NULL,
                    sync_status INTEGER DEFAULT 1
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Inserts a new spatial feature record into the specified layer table.
     *
     * @param layerName The name of the feature layer table (e.g., "DetailPoint").
     * @param geometryJson JSON string representing the feature geometry.
     * @param notes Optional descriptive notes for the feature.
     * @return The generated GUID string serving as the feature's primary key.
     */
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

    /**
     * Updates an existing spatial feature record and resets its sync status to pending (`sync_status = 1`).
     *
     * @param layerName The name of the feature layer table.
     * @param globalId The GUID primary key of the feature to update.
     * @param geometryJson Updated JSON string representing the feature geometry.
     * @param notes Updated descriptive notes for the feature.
     */
    fun updateFeature(layerName: String, globalId: String, geometryJson: String, notes: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("geometry", geometryJson)
            put("notes", notes)
            put("sync_status", 1)
        }
        db.update(layerName, values, "globalId = ?", arrayOf(globalId))
    }

    /**
     * Deletes a spatial feature record and its associated photo attachments from the database.
     *
     * @param layerName The name of the feature layer table.
     * @param globalId The GUID primary key of the feature to delete.
     */
    fun deleteFeature(layerName: String, globalId: String) {
        val db = writableDatabase
        db.delete(layerName, "globalId = ?", arrayOf(globalId))
        db.delete("FeatureAttachments", "globalId = ?", arrayOf(globalId))
    }

    /**
     * Adds a photo attachment record linked to a specific GIS feature.
     *
     * @param globalId The GUID of the parent GIS feature.
     * @param photoPath Absolute file path to the photo.
     * @return The row ID of the newly inserted attachment record.
     */
    fun addAttachment(globalId: String, photoPath: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("globalId", globalId)
            put("photoPath", photoPath)
            put("sync_status", 1)
        }
        return db.insert("FeatureAttachments", null, values)
    }

    /**
     * Retrieves all photo attachment file paths associated with a given GIS feature.
     *
     * @param globalId The GUID of the GIS feature.
     * @return A list of photo file path strings.
     */
    fun getAttachmentsForFeature(globalId: String): List<String> {
        val db = readableDatabase
        val list = mutableListOf<String>()
        val cursor = db.query(
            "FeatureAttachments",
            arrayOf("photoPath"),
            "globalId = ?",
            arrayOf(globalId),
            null, null, null
        )
        while (cursor.moveToNext()) {
            list.add(cursor.getString(0))
        }
        cursor.close()
        return list
    }

    /**
     * Retrieves all photo attachments that have not yet been synchronized with the server.
     *
     * @return A list of [AttachmentRecord] objects with `sync_status = 1`.
     */
    fun getUnsyncedAttachments(): List<AttachmentRecord> {
        val db = readableDatabase
        val list = mutableListOf<AttachmentRecord>()
        val cursor = db.query(
            "FeatureAttachments",
            arrayOf("id", "globalId", "photoPath", "sync_status"),
            "sync_status = 1",
            null, null, null, null
        )
        while (cursor.moveToNext()) {
            list.add(
                AttachmentRecord(
                    id = cursor.getLong(0),
                    globalId = cursor.getString(1),
                    photoPath = cursor.getString(2),
                    syncStatus = cursor.getInt(3)
                )
            )
        }
        cursor.close()
        return list
    }

    /**
     * Marks a specific photo attachment record as synchronized (`sync_status = 0`).
     *
     * @param id The database primary key ID of the attachment record.
     */
    fun markAttachmentSynced(id: Long) {
        val db = writableDatabase
        val values = ContentValues().apply { put("sync_status", 0) }
        db.update("FeatureAttachments", values, "id = ?", arrayOf(id.toString()))
    }

    /**
     * Calculates the total number of unsynced feature records across all layer tables.
     *
     * @return Total count of unsynced features.
     */
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

    /**
     * Retrieves all unsynced feature records for a given layer table.
     *
     * @param layerName The name of the feature layer table.
     * @return A list of [FeatureRecord] objects with pending edits.
     */
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

    /**
     * Retrieves all feature records (both synced and unsynced) for a given layer table.
     *
     * @param layerName The name of the feature layer table.
     * @return A list of all [FeatureRecord] objects in the layer.
     */
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

    /**
     * Marks a list of feature records in a specified layer as successfully synchronized (`sync_status = 0`).
     *
     * @param layerName The name of the feature layer table.
     * @param globalIds A list of feature GUIDs to mark as synced.
     */
    fun markAsSynced(layerName: String, globalIds: List<String>) {
        if (globalIds.isEmpty()) return
        val db = writableDatabase
        val formattedIds = globalIds.joinToString(",") { "'$it'" }
        db.execSQL("UPDATE $layerName SET sync_status = 0 WHERE globalId IN ($formattedIds)")
    }
}
