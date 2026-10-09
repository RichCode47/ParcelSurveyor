package com.example.parcelsurveyor.util

import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Custom osmdroid tile source for reading offline raster map tiles from an MBTiles SQLite database file.
 */
class MbTilesTileSource private constructor(
    name: String,
    minZoom: Int,
    maxZoom: Int,
    tileSize: Int,
    private val db: SQLiteDatabase
) : BitmapTileSourceBase(name, minZoom, maxZoom, tileSize, ".png") {

    companion object {
        /**
         * Creates an [MbTilesTileSource] instance from an MBTiles SQLite file.
         *
         * @param file The MBTiles database [File].
         * @return Initialized [MbTilesTileSource] instance, or null if file is invalid or missing.
         */
        fun create(file: File): MbTilesTileSource? {
            if (!file.exists()) return null
            return try {
                val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
                var minZoom = 0
                var maxZoom = 19

                val metaCursor = db.rawQuery("SELECT name, value FROM metadata", null)
                while (metaCursor.moveToNext()) {
                    val key = metaCursor.getString(0)
                    val value = metaCursor.getString(1)
                    if (key.equals("minzoom", ignoreCase = true)) minZoom = value.toIntOrNull() ?: 0
                    if (key.equals("maxzoom", ignoreCase = true)) maxZoom = value.toIntOrNull() ?: 19
                }
                metaCursor.close()

                MbTilesTileSource(file.name, minZoom, maxZoom, 256, db)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    /**
     * Retrieves the bitmap tile drawable for the specified zoom and tile coordinates.
     *
     * @param zoom Map zoom level.
     * @param x Tile X coordinate.
     * @param y Tile Y coordinate.
     * @return Tile image [Drawable], or null if tile is missing.
     */
    fun getTileDrawable(zoom: Int, x: Int, y: Int): Drawable? {
        val tmsY = (1 shl zoom) - 1 - y
        val cursor = db.rawQuery(
            "SELECT tile_data FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf(zoom.toString(), x.toString(), tmsY.toString())
        )
        var drawable: Drawable? = null
        if (cursor.moveToFirst()) {
            val blob = cursor.getBlob(0)
            if (blob != null) {
                val bmp = BitmapFactory.decodeStream(ByteArrayInputStream(blob))
                if (bmp != null) {
                    drawable = BitmapDrawable(null, bmp)
                }
            }
        }
        cursor.close()
        return drawable
    }

    /**
     * Closes the underlying MBTiles SQLite database connection.
     */
    fun close() {
        if (db.isOpen) {
            db.close()
        }
    }
}
