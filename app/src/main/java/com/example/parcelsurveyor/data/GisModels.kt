package com.example.parcelsurveyor.data

/**
 * Data class representing a geographic coordinate point with latitude and longitude.
 *
 * @property latitude Latitude in decimal degrees (WGS84).
 * @property longitude Longitude in decimal degrees (WGS84).
 */
data class LatLngPoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Enum defining the available GIS feature layer types, their database table names,
 * display names, and ArcGIS Online (AGOL) service layer indices.
 *
 * @property tableName The SQLite database table name for the layer.
 * @property displayName Human-readable display name for the layer.
 * @property agolLayerIndex Corresponding layer index in the AGOL FeatureServer REST endpoint.
 */
enum class FeatureLayerType(
    val tableName: String,
    val displayName: String,
    val agolLayerIndex: Int
) {
    /** Single detail point feature layer. */
    DETAIL_POINT("DetailPoint", "DetailPoint", 0),

    /** Parcel boundary point feature layer. */
    PARCEL_POINT("ParcelPoint", "ParcelPoint", 1),

    /** Linear feature layer (e.g., fences, roads, utilities). */
    DETAIL_LINE("DetailLine", "DetailLine", 2),

    /** Land parcel polygon area feature layer. */
    DETAIL_POLYGON("DetailPolygon", "DetailPolygon", 3);

    /**
     * Returns true if this feature layer represents a point geometry type.
     */
    val isPoint: Boolean
        get() = this == DETAIL_POINT || this == PARCEL_POINT

    companion object {
        /** List containing all available feature layer types. */
        val ALL_LAYERS = entries.toList()

        /**
         * Resolves a [FeatureLayerType] from its corresponding database table name.
         *
         * @param name The table name string.
         * @return The matching [FeatureLayerType], or [DETAIL_POINT] as a default fallback.
         */
        fun fromTableName(name: String): FeatureLayerType {
            return entries.find { it.tableName.equals(name, ignoreCase = true) } ?: DETAIL_POINT
        }
    }
}

/**
 * Data class representing an individual GIS feature record stored in the database.
 *
 * @property globalId Globally unique identifier (GUID) primary key.
 * @property geometryJson JSON-encoded string representation of the feature geometry.
 * @property notes Descriptive notes or metadata for the feature.
 * @property syncStatus Synchronization status flag (0 = synced, 1 = pending local edit).
 */
data class FeatureRecord(
    val globalId: String,
    val geometryJson: String,
    val notes: String,
    val syncStatus: Int
)
