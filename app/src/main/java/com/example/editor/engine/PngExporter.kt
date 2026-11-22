package com.example.editor.engine

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PngExporter {

    private fun createExportBitmap(
        pixels: IntArray,
        width: Int,
        height: Int,
        preserveAlpha: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        if (!preserveAlpha) {
            bitmap.setHasAlpha(false)
        }
        return bitmap
    }

    suspend fun savePngToGallery(
        context: Context,
        pixels: IntArray,
        width: Int,
        height: Int,
        preserveAlpha: Boolean
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = createExportBitmap(pixels, width, height, preserveAlpha)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val suffix = if (preserveAlpha) "RGBA" else "RGB"
            val fileName = "LilacPixel_${timestamp}_$suffix.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/LilacPixel"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: error("Не удалось создать запись в MediaStore")

                resolver.openOutputStream(uri)?.use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "Ошибка сжатия PNG"
                    }
                } ?: error("Не удалось открыть поток для записи")

                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                uri
            } else {
                val exportDir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "LilacPixel"
                ).apply { mkdirs() }
                val outFile = File(exportDir, fileName)
                FileOutputStream(outFile).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                        "Ошибка сжатия PNG"
                    }
                }
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outFile
                )
            }
        }
    }

    suspend fun createShareablePngUri(
        context: Context,
        pixels: IntArray,
        width: Int,
        height: Int,
        preserveAlpha: Boolean
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = createExportBitmap(pixels, width, height, preserveAlpha)
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val suffix = if (preserveAlpha) "RGBA" else "RGB"
            val cacheExportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val outFile = File(cacheExportsDir, "LilacPixel_${timestamp}_$suffix.png")
            FileOutputStream(outFile).use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    "Ошибка сжатия PNG"
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outFile
            )
        }
    }
}
