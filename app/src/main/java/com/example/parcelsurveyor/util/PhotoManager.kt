package com.example.parcelsurveyor.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility object for managing site inspection photo files, creating timestamped image files on disk,
 * and generating secure content URIs via [FileProvider].
 */
object PhotoManager {

    /**
     * Creates a new timestamped JPEG image file in the app's internal photo storage directory.
     *
     * @param context Application context.
     * @return Newly created image [File].
     */
    fun createPhotoFile(context: Context): File {
        val storageDir = File(context.filesDir, "photos").apply {
            if (!exists()) mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(storageDir, "PHOTO_${timestamp}.jpg")
    }

    /**
     * Generates a secure content [Uri] for a photo file using [FileProvider].
     *
     * @param context Application context.
     * @param file The photo [File].
     * @return Secure content [Uri].
     */
    fun getPhotoUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }
}
