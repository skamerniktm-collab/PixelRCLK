package com.example.compress.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compress.engine.PngCompressor
import com.example.compress.model.CompressInputImage
import com.example.compress.model.CompressPreset
import com.example.compress.model.CompressPreviewResult
import com.example.compress.model.CompressSettings
import com.example.compress.model.CompressUiState
import com.example.compress.model.PngColorLimit
import com.example.compress.model.formatSavingsPercent
import com.example.resizer.model.formatExactFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class CompressViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(CompressUiState())
    val uiState: StateFlow<CompressUiState> = _uiState.asStateFlow()

    fun selectPreset(preset: CompressPreset) {
        _uiState.update { state ->
            val nextSettings = if (preset == CompressPreset.CUSTOM) {
                state.settings.copy(preset = CompressPreset.CUSTOM)
            } else {
                state.settings.copy(
                    preset = preset,
                    scalePercent = preset.scalePercent,
                    colorLimit = preset.colorLimit
                )
            }
            state.copy(settings = nextSettings, lastSavedSummary = null)
        }
    }

    fun setScalePercent(scalePercent: Int) {
        val clamped = scalePercent.coerceIn(10, 100)
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    preset = CompressPreset.CUSTOM,
                    scalePercent = clamped
                ),
                lastSavedSummary = null
            )
        }
    }

    fun setColorLimit(limit: PngColorLimit) {
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    preset = CompressPreset.CUSTOM,
                    colorLimit = limit
                ),
                lastSavedSummary = null
            )
        }
    }

    fun setPreserveAlpha(preserve: Boolean) {
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(preserveAlpha = preserve),
                lastSavedSummary = null
            )
        }
    }

    fun selectInput(id: String) {
        _uiState.update { it.copy(activeInputId = id) }
    }

    fun removeInput(id: String) {
        _uiState.update { state ->
            val updated = state.inputs.filterNot { it.id == id }
            val nextActive = if (state.activeInputId == id) {
                updated.firstOrNull()?.id
            } else {
                state.activeInputId
            }
            state.copy(
                inputs = updated,
                activeInputId = nextActive,
                lastSavedSummary = null
            )
        }
    }

    fun clearInputs() {
        _uiState.update {
            it.copy(
                inputs = emptyList(),
                activeInputId = null,
                lastSavedSummary = null
            )
        }
    }

    fun clearSavedSummary() {
        _uiState.update { it.copy(lastSavedSummary = null) }
    }

    fun addFromEditorCanvas(
        name: String,
        width: Int,
        height: Int,
        pixels: IntArray
    ) {
        viewModelScope.launch {
            val safeName = sanitizeBaseName(name, fallback = "canvas")
            val hasAlpha = pixels.any { ((it ushr 24) and 0xFF) < 255 }
            val baselineBytes = withContext(Dispatchers.Default) {
                measureBaselinePngBytes(pixels, width, height, hasAlpha)
            }
            val input = CompressInputImage(
                baseName = safeName,
                width = width,
                height = height,
                hasAlpha = hasAlpha,
                pixels = pixels.copyOf(),
                sourceByteSize = baselineBytes
            )
            _uiState.update { state ->
                state.copy(
                    inputs = state.inputs + input,
                    activeInputId = input.id,
                    lastSavedSummary = null
                )
            }
        }
    }

    fun importFromUris(
        context: Context,
        uris: List<Uri>,
        onComplete: (addedCount: Int) -> Unit
    ) {
        if (uris.isEmpty()) {
            onComplete(0)
            return
        }
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { index, uri ->
                    decodeUriToInput(context, uri, index)
                }
            }
            if (decoded.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        inputs = state.inputs + decoded,
                        activeInputId = decoded.first().id,
                        lastSavedSummary = null
                    )
                }
            }
            onComplete(decoded.size)
        }
    }

    suspend fun renderPreview(
        input: CompressInputImage,
        settings: CompressSettings
    ): CompressPreviewResult = withContext(Dispatchers.Default) {
        val normalized = settings.normalized()
        val output = PngCompressor.compressToPng(
            srcPixels = input.pixels,
            srcWidth = input.width,
            srcHeight = input.height,
            scalePercent = normalized.scalePercent,
            maxColors = normalized.colorLimit.maxColors,
            preserveAlpha = normalized.preserveAlpha
        )
        CompressPreviewResult(
            pixels = output.pixels,
            width = output.width,
            height = output.height,
            compressedBytes = output.pngBytes,
            originalByteSize = input.sourceByteSize,
            compressedByteSize = output.pngBytes.size.toLong()
        )
    }

    fun exportToGallery(
        context: Context,
        onComplete: (Result<String>) -> Unit
    ) {
        val state = _uiState.value
        if (state.isProcessing || state.inputs.isEmpty()) return

        val total = state.inputs.size
        val settings = state.settings.normalized()
        _uiState.update {
            it.copy(
                isProcessing = true,
                progressCurrent = 0,
                progressTotal = total,
                lastSavedSummary = null
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                var savedCount = 0
                var originalSum = 0L
                var compressedSum = 0L
                val usedNames = mutableSetOf<String>()

                for (input in state.inputs) {
                    val output = withContext(Dispatchers.Default) {
                        PngCompressor.compressToPng(
                            srcPixels = input.pixels,
                            srcWidth = input.width,
                            srcHeight = input.height,
                            scalePercent = settings.scalePercent,
                            maxColors = settings.colorLimit.maxColors,
                            preserveAlpha = settings.preserveAlpha
                        )
                    }
                    val fileName = buildUniquePngName(input.baseName, usedNames)
                    withContext(Dispatchers.IO) {
                        savePngBytesToGallery(context, fileName, output.pngBytes)
                    }
                    originalSum += input.sourceByteSize
                    compressedSum += output.pngBytes.size.toLong()
                    savedCount++
                    _uiState.update { it.copy(progressCurrent = savedCount) }
                }

                val savedPct = if (originalSum > 0L) {
                    ((originalSum - compressedSum).toDouble() / originalSum.toDouble() * 100.0).toFloat()
                } else 0f
                val summary = "Сохранено $savedCount PNG в Pictures/PixelResize/Compressed • " +
                    "${formatExactFileSize(compressedSum)} (${formatSavingsPercent(savedPct)})"
                summary
            }

            _uiState.update { stateAfter ->
                stateAfter.copy(
                    isProcessing = false,
                    progressCurrent = 0,
                    progressTotal = 0,
                    lastSavedSummary = result.getOrNull() ?: stateAfter.lastSavedSummary
                )
            }
            onComplete(result)
        }
    }

    fun exportToFolder(
        context: Context,
        treeUri: Uri,
        onComplete: (Result<String>) -> Unit
    ) {
        val state = _uiState.value
        if (state.isProcessing || state.inputs.isEmpty()) return

        val total = state.inputs.size
        val settings = state.settings.normalized()
        _uiState.update {
            it.copy(
                isProcessing = true,
                progressCurrent = 0,
                progressTotal = total,
                lastSavedSummary = null
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                var savedCount = 0
                var originalSum = 0L
                var compressedSum = 0L
                val usedNames = mutableSetOf<String>()

                for (input in state.inputs) {
                    val output = withContext(Dispatchers.Default) {
                        PngCompressor.compressToPng(
                            srcPixels = input.pixels,
                            srcWidth = input.width,
                            srcHeight = input.height,
                            scalePercent = settings.scalePercent,
                            maxColors = settings.colorLimit.maxColors,
                            preserveAlpha = settings.preserveAlpha
                        )
                    }
                    val fileName = buildUniquePngName(input.baseName, usedNames)
                    withContext(Dispatchers.IO) {
                        val docUri = DocumentsContract.createDocument(
                            context.contentResolver,
                            parentDocUri,
                            "image/png",
                            fileName
                        ) ?: error("Не удалось создать файл $fileName")
                        context.contentResolver.openOutputStream(docUri)?.use { stream ->
                            stream.write(output.pngBytes)
                        } ?: error("Не удалось записать файл $fileName")
                    }
                    originalSum += input.sourceByteSize
                    compressedSum += output.pngBytes.size.toLong()
                    savedCount++
                    _uiState.update { it.copy(progressCurrent = savedCount) }
                }

                val savedPct = if (originalSum > 0L) {
                    ((originalSum - compressedSum).toDouble() / originalSum.toDouble() * 100.0).toFloat()
                } else 0f
                "Сохранено в выбранную папку: $savedCount PNG • " +
                    "${formatExactFileSize(compressedSum)} (${formatSavingsPercent(savedPct)})"
            }

            _uiState.update { stateAfter ->
                stateAfter.copy(
                    isProcessing = false,
                    progressCurrent = 0,
                    progressTotal = 0,
                    lastSavedSummary = result.getOrNull() ?: stateAfter.lastSavedSummary
                )
            }
            onComplete(result)
        }
    }

    fun exportForSharing(
        context: Context,
        onComplete: (Result<List<Uri>>) -> Unit
    ) {
        val state = _uiState.value
        if (state.isProcessing || state.inputs.isEmpty()) return

        val total = state.inputs.size
        val settings = state.settings.normalized()
        _uiState.update {
            it.copy(
                isProcessing = true,
                progressCurrent = 0,
                progressTotal = total
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                val shareDir = File(context.cacheDir, "exports/compressed").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val uris = mutableListOf<Uri>()
                val usedNames = mutableSetOf<String>()
                var processed = 0

                for (input in state.inputs) {
                    val output = withContext(Dispatchers.Default) {
                        PngCompressor.compressToPng(
                            srcPixels = input.pixels,
                            srcWidth = input.width,
                            srcHeight = input.height,
                            scalePercent = settings.scalePercent,
                            maxColors = settings.colorLimit.maxColors,
                            preserveAlpha = settings.preserveAlpha
                        )
                    }
                    val fileName = buildUniquePngName(input.baseName, usedNames)
                    val uri = withContext(Dispatchers.IO) {
                        val outFile = File(shareDir, fileName)
                        FileOutputStream(outFile).use { stream ->
                            stream.write(output.pngBytes)
                        }
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            outFile
                        )
                    }
                    uris.add(uri)
                    processed++
                    _uiState.update { it.copy(progressCurrent = processed) }
                }
                uris.toList()
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    progressCurrent = 0,
                    progressTotal = 0
                )
            }
            onComplete(result)
        }
    }

    private fun buildUniquePngName(baseName: String, usedNames: MutableSet<String>): String {
        var candidate = "${baseName}_compressed.png"
        var suffix = 2
        while (!usedNames.add(candidate)) {
            candidate = "${baseName}_compressed_${suffix}.png"
            suffix++
        }
        return candidate
    }

    private fun decodeUriToInput(context: Context, uri: Uri, index: Int): CompressInputImage? {
        return runCatching {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    if (longest > MAX_INPUT_EDGE) {
                        val sample = ((longest + MAX_INPUT_EDGE - 1) / MAX_INPUT_EDGE).coerceAtLeast(2)
                        decoder.setTargetSampleSize(sample)
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                var sampleSize = 1
                val longest = max(bounds.outWidth, bounds.outHeight)
                if (longest > MAX_INPUT_EDGE) {
                    while (longest / sampleSize > MAX_INPUT_EDGE) {
                        sampleSize *= 2
                    }
                }
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inScaled = false
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)
                } ?: error("Не удалось открыть поток изображения")
            }

            val argbBitmap = if (rawBitmap.config != Bitmap.Config.ARGB_8888) {
                rawBitmap.copy(Bitmap.Config.ARGB_8888, false).also {
                    if (it !== rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            val w = argbBitmap.width.coerceAtLeast(1)
            val h = argbBitmap.height.coerceAtLeast(1)
            val pixels = IntArray(w * h)
            argbBitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            argbBitmap.recycle()

            val hasAlpha = pixels.any { ((it ushr 24) and 0xFF) < 255 }
            val (resolvedName, resolvedSize) = resolveFileMeta(context, uri)
            val finalSize = resolvedSize ?: measureStreamByteSize(context, uri)
                ?: measureBaselinePngBytes(pixels, w, h, hasAlpha)

            CompressInputImage(
                baseName = sanitizeBaseName(resolvedName, fallback = "photo_${index + 1}"),
                width = w,
                height = h,
                hasAlpha = hasAlpha,
                pixels = pixels,
                sourceByteSize = finalSize
            )
        }.getOrNull()
    }

    private fun resolveFileMeta(context: Context, uri: Uri): Pair<String?, Long?> {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx)?.substringBeforeLast('.') else null
                    val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        cursor.getLong(sizeIdx).takeIf { it > 0L }
                    } else null
                    name to size
                } else {
                    null to null
                }
            }
        }.getOrNull() ?: (null to null)
    }

    private fun measureStreamByteSize(context: Context, uri: Uri): Long? {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length > 0L) return@runCatching afd.length
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val buf = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buf)
                    if (read <= 0) break
                    total += read
                }
                total.takeIf { it > 0L }
            }
        }.getOrNull()
    }

    private fun measureBaselinePngBytes(
        pixels: IntArray,
        width: Int,
        height: Int,
        hasAlpha: Boolean
    ): Long {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        if (!hasAlpha) {
            bitmap.setHasAlpha(false)
        }
        return try {
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.size().toLong().coerceAtLeast(1L)
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun savePngBytesToGallery(
        context: Context,
        fileName: String,
        bytes: ByteArray
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/PixelResize/Compressed"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: error("Не удалось создать запись в MediaStore")

            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
            } ?: error("Не удалось открыть поток записи")

            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } else {
            val exportDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "PixelResize/Compressed"
            ).apply { mkdirs() }
            val outFile = File(exportDir, fileName)
            FileOutputStream(outFile).use { stream ->
                stream.write(bytes)
            }
        }
    }

    private fun sanitizeBaseName(raw: String?, fallback: String): String {
        val cleaned = raw
            ?.trim()
            ?.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ._-]"), "_")
            ?.trim('_')
            ?.take(32)
        return if (cleaned.isNullOrBlank()) fallback else cleaned
    }

    companion object {
        private const val MAX_INPUT_EDGE = 2048
    }
}
