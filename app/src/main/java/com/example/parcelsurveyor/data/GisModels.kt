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

/**
 * Enum defining the supported custom feature attribute field data types.
 */
enum class AttributeFieldType {
    /** Plain text input string. */
    TEXT,
    /** Decimal or integer numeric value. */
    NUMERIC,
    /** Single-selection dropdown list option. */
    PICKLIST,
    /** Boolean true/false checkbox flag. */
    CHECKBOX,
    /** Date/time stamp formatted string. */
    DATE
}

/**
 * Data class representing an attribute schema field definition for a GIS layer.
 *
 * @property key Unique field identifier key in JSON output.
 * @property label Human-readable UI field label.
 * @property fieldType The input field data type [AttributeFieldType].
 * @property options List of selectable option strings if [fieldType] is [AttributeFieldType.PICKLIST].
 * @property isRequired True if the field must be completed before saving.
 * @property defaultValue Default initial value string for the field.
 */
data class AttributeFieldSchema(
    val key: String,
    val label: String,
    val fieldType: AttributeFieldType,
    val options: List<String> = emptyList(),
    val isRequired: Boolean = false,
    val defaultValue: String = ""
)

/**
 * Repository providing predefined attribute schemas for each GIS feature layer type.
 */
object LayerSchemaRepository {

    /**
     * Retrieves the default attribute schema fields for a given [FeatureLayerType].
     *
     * @param layerType The target feature layer type.
     * @return List of [AttributeFieldSchema] definitions.
     */
    fun getSchemaForLayer(layerType: FeatureLayerType): List<AttributeFieldSchema> {
        val commonFields = listOf(
            AttributeFieldSchema("Description", "Description / Notes", AttributeFieldType.TEXT, defaultValue = "Field survey capture"),
            AttributeFieldSchema("Surveyor", "Surveyor Name", AttributeFieldType.TEXT, defaultValue = "Field Team"),
            AttributeFieldSchema("SurveyDate", "Survey Date", AttributeFieldType.DATE)
        )

        val layerSpecificFields = when (layerType) {
            FeatureLayerType.DETAIL_POINT -> listOf(
                AttributeFieldSchema(
                    key = "FeatureType",
                    label = "Point Type",
                    fieldType = AttributeFieldType.PICKLIST,
                    options = listOf("Control Point", "Utility Pole", "Tree", "Structure Corner", "Other")
                ),
                AttributeFieldSchema(
                    key = "Condition",
                    label = "Marker Condition",
                    fieldType = AttributeFieldType.PICKLIST,
                    options = listOf("Excellent", "Good", "Fair", "Poor", "Damaged")
                ),
                AttributeFieldSchema(
                    key = "Elevation_m",
                    label = "Elevation (m)",
                    fieldType = AttributeFieldType.NUMERIC,
                    defaultValue = "0.0"
                )
            )

            FeatureLayerType.PARCEL_POINT -> listOf(
                AttributeFieldSchema(
                    key = "MonumentType",
                    label = "Monument Marker Type",
                    fieldType = AttributeFieldType.PICKLIST,
                    options = listOf("Iron Pin", "Concrete Pillar", "Brass Cap", "Wooden Stake", "Stone", "Unmarked")
                ),
                AttributeFieldSchema(
                    key = "CornerName",
                    label = "Corner Identifier (e.g. Beacon #)",
                    fieldType = AttributeFieldType.TEXT,
                    defaultValue = "Beacon-1"
                ),
                AttributeFieldSchema(
                    key = "IsVerified",
                    label = "Survey Verified",
                    fieldType = AttributeFieldType.CHECKBOX,
                    defaultValue = "true"
                )
            )

            FeatureLayerType.DETAIL_LINE -> listOf(
                AttributeFieldSchema(
                    key = "FeatureType",
                    label = "Line Category",
                    fieldType = AttributeFieldType.PICKLIST,
                    options = listOf("Fence", "Road Edge", "Drainage Ditch", "Power Line", "Water Pipe", "Other")
                ),
                AttributeFieldSchema(
                    key = "Material",
                    label = "Fence / Line Material",
                    fieldType = AttributeFieldType.PICKLIST,
                    options = listOf("Barbed Wire", "Chain Link", "Wood", "Concrete", "Asphalt", "PVC", "Other")
                ),
                AttributeFieldSchema(
                    key = "Height_m",
                    label = "Height / Width (m)",
                    fieldType = AttributeFieldType.NUMERIC,
                    defaultValue = "1.5"
                )
            )

            FeatureLayerType.DETAIL_POLYGON -> listOf(
                AttributeFieldSchema(
                    key = "FeatureType",
                    label = "Land Use / Parcel Category",
                    fieldType = AttributeFieldType.PICKLIST,
                    options = listOf("Residential Parcel", "Commercial Land", "Agricultural", "Forest / Vegetation", "Water Body", "Other")
                ),
                AttributeFieldSchema(
                    key = "ZoningCode",
                    label = "Zoning Classification Code",
                    fieldType = AttributeFieldType.TEXT,
                    defaultValue = "R-1"
                ),
                AttributeFieldSchema(
                    key = "IsFenced",
                    label = "Fully Enclosed / Fenced",
                    fieldType = AttributeFieldType.CHECKBOX,
                    defaultValue = "false"
                )
            )
        }

        return commonFields + layerSpecificFields
    }
}

