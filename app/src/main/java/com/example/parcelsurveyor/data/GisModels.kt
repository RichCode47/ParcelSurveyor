package com.example.parcelsurveyor.data

data class LatLngPoint(
    val latitude: Double,
    val longitude: Double
)

enum class FeatureLayerType(
    val tableName: String,
    val displayName: String,
    val agolLayerIndex: Int
) {
    DETAIL_POINT("DetailPoint", "DetailPoint", 0),
    PARCEL_POINT("ParcelPoint", "ParcelPoint", 1),
    DETAIL_LINE("DetailLine", "DetailLine", 2),
    DETAIL_POLYGON("DetailPolygon", "DetailPolygon", 3);

    val isPoint: Boolean
        get() = this == DETAIL_POINT || this == PARCEL_POINT

    companion object {
        val ALL_LAYERS = entries.toList()

        fun fromTableName(name: String): FeatureLayerType {
            return entries.find { it.tableName.equals(name, ignoreCase = true) } ?: DETAIL_POINT
        }
    }
}

data class FeatureRecord(
    val globalId: String,
    val geometryJson: String,
    val notes: String,
    val syncStatus: Int
)
