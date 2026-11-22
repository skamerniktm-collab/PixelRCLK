package com.example.resizer.viewmodel

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
import com.example.resizer.engine.ImageResampler
import com.example.resizer.model.RESIZER_PRESET_SIZES
import com.example.resizer.model.ResampleMode
import com.example.resizer.model.ResizePreset
import com.example.resizer.model.ResizerInputImage
import com.example.resizer.model.ResizerPreviewResult
import com.example.resizer.model.ResizerUiState
import com.example.resizer.storage.ResizerProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

class ResizerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ResizerUiState())
    val uiState: StateFlow<ResizerUiState> = _uiState.asStateFlow()

    private var appContext: Context? = null
    private var autoSaveJob: Job? = null
    private val saveMutex = Mutex()
    private val projectCreatedAt = mutableMapOf<String, Long>()
    @Volatile
    private var hasUnsavedChanges = false

    fun initializeProjects(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        viewModelScope.launch {
            val list = saveMutex.withLock {
                val activeId = _uiState.value.currentProjectId
                withContext(NonCancellable + Dispatchers.IO) {
                    ResizerProjectRepository.pruneExpiredProjects(
                        context = ctx,
                        excludeProjectIds = listOfNotNull(activeId).toSet()
                    )
                    ResizerProjectRepository.listProjects(ctx)
                }
            }
            _uiState.update { it.copy(savedProjects = list) }
        }
    }

    fun openProject(
        context: Context,
        projectId: String,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val ctx = context.applicationContext
        appContext = ctx
        autoSaveJob?.cancel()
        autoSaveJob = null
        val shouldFlush = hasUnsavedChanges
        viewModelScope.launch {
            if (shouldFlush && _uiState.value.inputs.isNotEmpty() && _uiState.value.currentProjectId != null) {
                persistCurrentProjectInternal(ctx)
            }
            hasUnsavedChanges = false
            _uiState.update { it.copy(isLoadingProject = true) }
            val result = saveMutex.withLock {
                withContext(NonCancellable + Dispatchers.IO) {
                    val loaded = ResizerProjectRepository.loadProject(ctx, projectId)
                    val list = ResizerProjectRepository.listProjects(ctx)
                    loaded to list
                }
            }
            val (loaded, list) = result
            if (loaded == null) {
                _uiState.update { it.copy(isLoadingProject = false, savedProjects = list) }
                onComplete(false)
                return@launch
            }
            projectCreatedAt[loaded.id] = loaded.createdAtMillis
            _uiState.update { state ->
                state.copy(
                    currentProjectId = loaded.id,
                    currentProjectName = loaded.name,
                    inputs = loaded.inputs,
                    activePreviewInputId = loaded.activePreviewInputId,
                    selectedPresets = loaded.selectedPresets,
                    previewPreset = loaded.previewPreset,
                    resampleMode = loaded.resampleMode,
                    preserveAlpha = loaded.preserveAlpha,
                    lastAutoSavedAtMillis = loaded.updatedAtMillis,
                    isLoadingProject = false,
                    savedProjects = list
                )
            }
            onComplete(true)
        }
    }

    fun renameProject(
        context: Context,
        projectId: String,
        newName: String
    ) {
        val trimmed = newName.trim().take(48)
        if (trimmed.isEmpty()) return
        val ctx = context.applicationContext
        appContext = ctx
        _uiState.update { state ->
            if (state.currentProjectId == projectId) {
                state.copy(currentProjectName = trimmed)
            } else {
                state
            }
        }
        viewModelScope.launch {
            val updatedList = saveMutex.withLock {
                withContext(NonCancellable + Dispatchers.IO) {
                    ResizerProjectRepository.renameProject(ctx, projectId, trimmed)
                    ResizerProjectRepository.listProjects(ctx)
                }
            }
            _uiState.update { it.copy(savedProjects = updatedList) }
        }
    }

    fun deleteProject(
        context: Context,
        projectId: String
    ) {
        val ctx = context.applicationContext
        appContext = ctx
        if (_uiState.value.currentProjectId == projectId) {
            autoSaveJob?.cancel()
            autoSaveJob = null
            hasUnsavedChanges = false
        }
        viewModelScope.launch {
            val updatedList = saveMutex.withLock {
                withContext(NonCancellable + Dispatchers.IO) {
                    ResizerProjectRepository.deleteProject(ctx, projectId)
                    ResizerProjectRepository.listProjects(ctx)
                }
            }
            projectCreatedAt.remove(projectId)
            _uiState.update { state ->
                if (state.currentProjectId == projectId) {
                    state.copy(
                        currentProjectId = null,
                        currentProjectName = "Новый ресайз",
                        inputs = emptyList(),
                        activePreviewInputId = null,
                        lastAutoSavedAtMillis = null,
                        savedProjects = updatedList
                    )
                } else {
                    state.copy(savedProjects = updatedList)
                }
            }
        }
    }

    fun flushAutoSaveNow(context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
        }
        val ctx = appContext ?: return
        val state = _uiState.value
        if (state.inputs.isEmpty() || state.currentProjectId == null) return
        if (!hasUnsavedChanges && autoSaveJob?.isActive != true) return
        autoSaveJob?.cancel()
        autoSaveJob = null
        viewModelScope.launch {
            persistCurrentProjectInternal(ctx)
        }
    }

    private fun scheduleAutoSave(delayMs: Long = AUTO_SAVE_DEBOUNCE_MS) {
        val state = _uiState.value
        if (state.inputs.isEmpty() || state.currentProjectId == null) return
        hasUnsavedChanges = true
        val ctx = appContext ?: return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(delayMs)
            persistCurrentProjectInternal(ctx)
        }
    }

    private suspend fun persistCurrentProjectInternal(ctx: Context) {
        saveMutex.withLock {
            val snapshot = _uiState.value
            val projectId = snapshot.currentProjectId ?: return@withLock
            if (snapshot.inputs.isEmpty()) return@withLock
            val createdAt = projectCreatedAt.getOrPut(projectId) { System.currentTimeMillis() }
            hasUnsavedChanges = false

            _uiState.update { it.copy(isAutoSaving = true) }
            try {
                val (savedAt, updatedList) = withContext(NonCancellable + Dispatchers.IO) {
                    val ts = ResizerProjectRepository.saveProject(
                        context = ctx,
                        projectId = projectId,
                        projectName = snapshot.currentProjectName,
                        createdAtMillis = createdAt,
                        state = snapshot
                    )
                    val list = ResizerProjectRepository.listProjects(ctx)
                    ts to list
                }
                _uiState.update { state ->
                    if (state.currentProjectId == projectId) {
                        state.copy(
                            isAutoSaving = false,
                            lastAutoSavedAtMillis = savedAt,
                            savedProjects = updatedList
                        )
                    } else {
                        state.copy(
                            isAutoSaving = false,
                            savedProjects = updatedList
                        )
                    }
                }
            } catch (_: Throwable) {
                _uiState.update { it.copy(isAutoSaving = false) }
            }
        }
    }

    fun setResampleMode(mode: ResampleMode) {
        _uiState.update { it.copy(resampleMode = mode) }
        scheduleAutoSave()
    }

    fun togglePreset(preset: ResizePreset) {
        _uiState.update { state ->
            state.copy(
                selectedPresets = setOf(preset),
                previewPreset = preset
            )
        }
        scheduleAutoSave()
    }

    fun setPreviewPreset(preset: ResizePreset) {
        _uiState.update { it.copy(previewPreset = preset) }
        scheduleAutoSave()
    }

    fun setPreserveAlpha(preserve: Boolean) {
        _uiState.update { it.copy(preserveAlpha = preserve) }
        scheduleAutoSave()
    }

    fun selectPreviewInput(id: String) {
        _uiState.update { it.copy(activePreviewInputId = id) }
        scheduleAutoSave()
    }

    fun removeInput(id: String) {
        var isEmptyAfter = false
        _uiState.update { state ->
            val updated = state.inputs.filterNot { it.id == id }
            isEmptyAfter = updated.isEmpty()
            val nextPreviewId = if (state.activePreviewInputId == id) {
                updated.firstOrNull()?.id
            } else {
                state.activePreviewInputId
            }
            if (isEmptyAfter) {
                state.copy(
                    currentProjectId = null,
                    currentProjectName = "Новый ресайз",
                    inputs = emptyList(),
                    activePreviewInputId = null,
                    lastAutoSavedAtMillis = null
                )
            } else {
                state.copy(
                    inputs = updated,
                    activePreviewInputId = nextPreviewId
                )
            }
        }
        if (isEmptyAfter) {
            autoSaveJob?.cancel()
            autoSaveJob = null
            hasUnsavedChanges = false
        } else {
            scheduleAutoSave()
        }
    }

    fun clearInputs() {
        autoSaveJob?.cancel()
        autoSaveJob = null
        hasUnsavedChanges = false
        _uiState.update {
            it.copy(
                currentProjectId = null,
                currentProjectName = "Новый ресайз",
                inputs = emptyList(),
                activePreviewInputId = null,
                lastAutoSavedAtMillis = null
            )
        }
    }

    fun addFromEditorCanvas(
        name: String,
        width: Int,
        height: Int,
        pixels: IntArray
    ) {
        val safeName = sanitizeBaseName(name, fallback = "canvas")
        val hasAlpha = pixels.any { ((it ushr 24) and 0xFF) < 255 }
        val input = ResizerInputImage(
            baseName = safeName,
            width = width,
            height = height,
            hasAlpha = hasAlpha,
            pixels = pixels.copyOf()
        )
        _uiState.update { state ->
            val nextInputs = state.inputs + input
            val projectId = state.currentProjectId ?: UUID.randomUUID().toString().also {
                projectCreatedAt[it] = System.currentTimeMillis()
            }
            val projectName = if (state.currentProjectId == null) {
                buildDefaultProjectName(nextInputs)
            } else {
                state.currentProjectName
            }
            state.copy(
                currentProjectId = projectId,
                currentProjectName = projectName,
                inputs = nextInputs,
                activePreviewInputId = input.id
            )
        }
        scheduleAutoSave(delayMs = 150L)
    }

    fun importFromUris(
        context: Context,
        uris: List<Uri>,
        onComplete: (addedCount: Int) -> Unit
    ) {
        appContext = context.applicationContext
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
                    val nextInputs = state.inputs + decoded
                    val projectId = state.currentProjectId ?: UUID.randomUUID().toString().also {
                        projectCreatedAt[it] = System.currentTimeMillis()
                    }
                    val projectName = if (state.currentProjectId == null) {
                        buildDefaultProjectName(nextInputs)
                    } else {
                        state.currentProjectName
                    }
                    state.copy(
                        currentProjectId = projectId,
                        currentProjectName = projectName,
                        inputs = nextInputs,
                        activePreviewInputId = decoded.first().id
                    )
                }
                scheduleAutoSave(delayMs = 150L)
            }
            onComplete(decoded.size)
        }
    }

    suspend fun renderPreviewResult(
        input: ResizerInputImage,
        preset: ResizePreset,
        mode: ResampleMode,
        preserveAlpha: Boolean
    ): ResizerPreviewResult = withContext(Dispatchers.Default) {
        val useAlpha = preserveAlpha && input.hasAlpha
        val resized = ImageResampler.resizePixels(
            srcPixels = input.pixels,
            srcWidth = input.width,
            srcHeight = input.height,
            targetWidth = preset.width,
            targetHeight = preset.height,
            mode = mode,
            hasAlpha = useAlpha
        )
        val outputBytes = encodePngBytes(resized, preset.width, preset.height, useAlpha).size.toLong()
        val originalBytes = input.sourceByteSize
            ?: encodePngBytes(input.pixels, input.width, input.height, input.hasAlpha).size.toLong()
        ResizerPreviewResult(
            pixels = resized,
            width = preset.width,
            height = preset.height,
            outputByteSize = outputBytes,
            originalByteSize = originalBytes
        )
    }

    fun exportToGallery(
        context: Context,
        onComplete: (Result<Int>) -> Unit
    ) {
        val state = _uiState.value
        val presets = state.effectivePresets
        if (state.isProcessing || state.inputs.isEmpty() || presets.isEmpty()) return

        val total = state.inputs.size * presets.size
        _uiState.update { it.copy(isProcessing = true, progressCurrent = 0, progressTotal = total) }

        viewModelScope.launch {
            val result = runCatching {
                var savedCount = 0
                for (input in state.inputs) {
                    val useAlpha = state.preserveAlpha && input.hasAlpha
                    for (preset in presets) {
                        val resized = withContext(Dispatchers.Default) {
                            ImageResampler.resizePixels(
                                srcPixels = input.pixels,
                                srcWidth = input.width,
                                srcHeight = input.height,
                                targetWidth = preset.width,
                                targetHeight = preset.height,
                                mode = state.resampleMode,
                                hasAlpha = useAlpha
                            )
                        }
                        val fileName = "${input.baseName}_${preset.width}x${preset.height}.png"
                        withContext(Dispatchers.IO) {
                            saveResizedPngToGallery(
                                context = context,
                                fileName = fileName,
                                pixels = resized,
                                width = preset.width,
                                height = preset.height,
                                hasAlpha = useAlpha
                            )
                        }
                        savedCount++
                        _uiState.update { it.copy(progressCurrent = savedCount) }
                    }
                }
                savedCount
            }
            _uiState.update { it.copy(isProcessing = false, progressCurrent = 0, progressTotal = 0) }
            onComplete(result)
        }
    }

    fun exportToFolder(
        context: Context,
        treeUri: Uri,
        onComplete: (Result<Int>) -> Unit
    ) {
        val state = _uiState.value
        val presets = state.effectivePresets
        if (state.isProcessing || state.inputs.isEmpty() || presets.isEmpty()) return

        val total = state.inputs.size * presets.size
        _uiState.update { it.copy(isProcessing = true, progressCurrent = 0, progressTotal = total) }

        viewModelScope.launch {
            val result = runCatching {
                val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
                var savedCount = 0

                for (input in state.inputs) {
                    val useAlpha = state.preserveAlpha && input.hasAlpha
                    for (preset in presets) {
                        val resized = withContext(Dispatchers.Default) {
                            ImageResampler.resizePixels(
                                srcPixels = input.pixels,
                                srcWidth = input.width,
                                srcHeight = input.height,
                                targetWidth = preset.width,
                                targetHeight = preset.height,
                                mode = state.resampleMode,
                                hasAlpha = useAlpha
                            )
                        }
                        val fileName = "${input.baseName}_${preset.width}x${preset.height}.png"
                        withContext(Dispatchers.IO) {
                            val bytes = encodePngBytes(resized, preset.width, preset.height, useAlpha)
                            val docUri = DocumentsContract.createDocument(
                                context.contentResolver,
                                parentDocUri,
                                "image/png",
                                fileName
                            ) ?: error("Не удалось создать файл $fileName")
                            context.contentResolver.openOutputStream(docUri)?.use { stream ->
                                stream.write(bytes)
                            } ?: error("Не удалось открыть поток записи для $fileName")
                        }
                        savedCount++
                        _uiState.update { it.copy(progressCurrent = savedCount) }
                    }
                }
                savedCount
            }
            _uiState.update { it.copy(isProcessing = false, progressCurrent = 0, progressTotal = 0) }
            onComplete(result)
        }
    }

    fun exportForSharing(
        context: Context,
        onComplete: (Result<List<Uri>>) -> Unit
    ) {
        val state = _uiState.value
        val presets = state.effectivePresets
        if (state.isProcessing || state.inputs.isEmpty() || presets.isEmpty()) return

        val total = state.inputs.size * presets.size
        _uiState.update { it.copy(isProcessing = true, progressCurrent = 0, progressTotal = total) }

        viewModelScope.launch {
            val result = runCatching {
                val shareDir = File(context.cacheDir, "exports/resized").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val uris = mutableListOf<Uri>()
                var processed = 0
                val usedFileNames = mutableSetOf<String>()

                for (input in state.inputs) {
                    val useAlpha = state.preserveAlpha && input.hasAlpha
                    for (preset in presets) {
                        val resized = withContext(Dispatchers.Default) {
                            ImageResampler.resizePixels(
                                srcPixels = input.pixels,
                                srcWidth = input.width,
                                srcHeight = input.height,
                                targetWidth = preset.width,
                                targetHeight = preset.height,
                                mode = state.resampleMode,
                                hasAlpha = useAlpha
                            )
                        }
                        var fileName = "${input.baseName}_${preset.width}x${preset.height}.png"
                        if (!usedFileNames.add(fileName)) {
                            fileName = "${input.baseName}_${processed + 1}_${preset.width}x${preset.height}.png"
                            usedFileNames.add(fileName)
                        }
                        val uri = withContext(Dispatchers.IO) {
                            val outFile = File(shareDir, fileName)
                            val bytes = encodePngBytes(resized, preset.width, preset.height, useAlpha)
                            FileOutputStream(outFile).use { stream ->
                                stream.write(bytes)
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
                }
                uris.toList()
            }
            _uiState.update { it.copy(isProcessing = false, progressCurrent = 0, progressTotal = 0) }
            onComplete(result)
        }
    }

    private fun decodeUriToInput(context: Context, uri: Uri, index: Int): ResizerInputImage? {
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
            val baseName = sanitizeBaseName(resolvedName, fallback = "image_${index + 1}")

            ResizerInputImage(
                baseName = baseName,
                width = w,
                height = h,
                hasAlpha = hasAlpha,
                pixels = pixels,
                sourceByteSize = resolvedSize
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

    private fun sanitizeBaseName(raw: String?, fallback: String): String {
        val cleaned = raw
            ?.trim()
            ?.replace(Regex("[^a-zA-Z0-9а-яА-ЯёЁ._-]"), "_")
            ?.trim('_')
            ?.take(32)
        return if (cleaned.isNullOrBlank()) fallback else cleaned
    }

    private fun encodePngBytes(
        pixels: IntArray,
        width: Int,
        height: Int,
        hasAlpha: Boolean
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        if (!hasAlpha) {
            bitmap.setHasAlpha(false)
        }
        return try {
            ByteArrayOutputStream().use { baos ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                    "Ошибка сжатия PNG"
                }
                baos.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun saveResizedPngToGallery(
        context: Context,
        fileName: String,
        pixels: IntArray,
        width: Int,
        height: Int,
        hasAlpha: Boolean
    ) {
        val bytes = encodePngBytes(pixels, width, height, hasAlpha)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/LilacPixel/Resized"
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
                "LilacPixel/Resized"
            ).apply { mkdirs() }
            val outFile = File(exportDir, fileName)
            FileOutputStream(outFile).use { stream ->
                stream.write(bytes)
            }
        }
    }

    private fun buildDefaultProjectName(inputs: List<ResizerInputImage>): String {
        val first = inputs.firstOrNull() ?: return "Ресайз"
        return if (inputs.size == 1) {
            "${first.baseName} (${first.width}×${first.height})".take(48)
        } else {
            "${first.baseName} (+${inputs.size - 1})".take(48)
        }
    }

    companion object {
        private const val MAX_INPUT_EDGE = 2048
        private const val AUTO_SAVE_DEBOUNCE_MS = 600L
        val ALL_PRESETS_COUNT: Int = RESIZER_PRESET_SIZES.size
    }
}
