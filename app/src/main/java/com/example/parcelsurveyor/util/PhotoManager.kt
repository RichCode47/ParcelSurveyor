package com.example.parcelsurveyor.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PhotoManager {

    fun createPhotoFile(context: Context): File {
        val storageDir = File(context.filesDir, "photos").apply {
            if (!exists()) mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(storageDir, "PHOTO_${timestamp}.jpg")
    }

    fun getPhotoUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
    }
}
