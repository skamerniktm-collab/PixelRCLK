package com.example.overlay.viewmodel

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.overlay.model.OverlayBackground
import com.example.overlay.model.OverlayLayer
import com.example.overlay.model.OverlayResizeHandle
import com.example.overlay.model.OverlayTransformMath
import com.example.overlay.model.OverlayUiState
import com.example.overlay.storage.OverlayProjectRepository
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
import kotlin.math.roundToInt

class OverlayViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

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
            val projects = saveMutex.withLock {
                val activeId = _uiState.value.currentProjectId
                withContext(NonCancellable + Dispatchers.IO) {
                    OverlayProjectRepository.pruneExpiredProjects(
                        context = ctx,
                        excludeProjectIds = listOfNotNull(activeId).toSet()
                    )
                    OverlayProjectRepository.listProjects(ctx)
                }
            }
            _uiState.update { it.copy(savedProjects = projects) }
        }
    }

    fun startNewProject(context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
        }
        autoSaveJob?.cancel()
        autoSaveJob = null
        val shouldFlush = hasUnsavedChanges
        hasUnsavedChanges = false
        viewModelScope.launch {
            appContext?.let { ctx ->
                if (shouldFlush && _uiState.value.background != null && _uiState.value.currentProjectId != null) {
                    persistCurrentProjectInternal(ctx)
                }
            }
            _uiState.update { state ->
                state.copy(
                    currentProjectId = null,
                    currentProjectName = "Новый проект",
                    background = null,
                    layers = emptyList(),
                    selectedLayerId = null,
                    preserveAlpha = true,
                    showTransformFrame = true,
                    lastAutoSavedAtMillis = null
                )
            }
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
            if (shouldFlush && _uiState.value.background != null && _uiState.value.currentProjectId != null) {
                persistCurrentProjectInternal(ctx)
            }
            hasUnsavedChanges = false
            _uiState.update { it.copy(isLoadingProject = true) }
            val result = saveMutex.withLock {
                withContext(NonCancellable + Dispatchers.IO) {
                    val loaded = OverlayProjectRepository.loadProject(ctx, projectId)
                    val list = OverlayProjectRepository.listProjects(ctx)
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
                    background = loaded.background,
                    layers = loaded.layers,
                    selectedLayerId = loaded.selectedLayerId,
                    preserveAlpha = loaded.preserveAlpha,
                    showTransformFrame = loaded.showTransformFrame,
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
                    OverlayProjectRepository.renameProject(ctx, projectId, trimmed)
                    OverlayProjectRepository.listProjects(ctx)
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
        }
        viewModelScope.launch {
            val updatedList = saveMutex.withLock {
                withContext(NonCancellable + Dispatchers.IO) {
                    OverlayProjectRepository.deleteProject(ctx, projectId)
                    OverlayProjectRepository.listProjects(ctx)
                }
            }
            projectCreatedAt.remove(projectId)
            _uiState.update { state ->
                if (state.currentProjectId == projectId) {
                    state.copy(
                        currentProjectId = null,
                        currentProjectName = "Новый проект",
                        background = null,
                        layers = emptyList(),
                        selectedLayerId = null,
                        preserveAlpha = true,
                        showTransformFrame = true,
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
        if (state.background == null || state.currentProjectId == null) return
        if (!hasUnsavedChanges && autoSaveJob?.isActive != true) return
        autoSaveJob?.cancel()
        autoSaveJob = null
        viewModelScope.launch {
            persistCurrentProjectInternal(ctx)
        }
    }

    private fun scheduleAutoSave(delayMs: Long = AUTO_SAVE_DEBOUNCE_MS) {
        hasUnsavedChanges = true
        val ctx = appContext ?: return
        val state = _uiState.value
        if (state.background == null || state.currentProjectId == null) return
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
            if (snapshot.background == null) return@withLock
            val createdAt = projectCreatedAt.getOrPut(projectId) { System.currentTimeMillis() }
            hasUnsavedChanges = false

            _uiState.update { it.copy(isAutoSaving = true) }
            try {
                val (savedAt, updatedList) = withContext(NonCancellable + Dispatchers.IO) {
                    val ts = OverlayProjectRepository.saveProject(
                        context = ctx,
                        projectId = projectId,
                        projectName = snapshot.currentProjectName,
                        createdAtMillis = createdAt,
                        state = snapshot
                    )
                    val list = OverlayProjectRepository.listProjects(ctx)
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

    fun importBackgroundFromUri(
        context: Context,
        uri: Uri,
        onComplete: (Boolean) -> Unit
    ) {
        appContext = context.applicationContext
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                decodeUriToBitmap(context, uri, MAX_BG_EDGE, "background")
            }
            if (decoded == null) {
                onComplete(false)
                return@launch
            }
            val (name, bitmap) = decoded
            val hasAlpha = bitmapHasTransparency(bitmap)
            applyNewBackground(
                OverlayBackground(
                    name = name,
                    width = bitmap.width,
                    height = bitmap.height,
                    hasAlpha = hasAlpha,
                    bitmap = bitmap
                )
            )
            onComplete(true)
        }
    }

    fun setBackgroundFromPixels(
        name: String,
        width: Int,
        height: Int,
        pixels: IntArray
    ) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(pixels, safeW, safeH, Bitmap.Config.ARGB_8888)
        val hasAlpha = pixels.any { ((it ushr 24) and 0xFF) < 255 }
        applyNewBackground(
            OverlayBackground(
                name = sanitizeBaseName(name, "canvas"),
                width = safeW,
                height = safeH,
                hasAlpha = hasAlpha,
                bitmap = bitmap
            )
        )
    }

    private fun applyNewBackground(newBg: OverlayBackground) {
        _uiState.update { state ->
            val prevBg = state.background
            val projectId = state.currentProjectId ?: UUID.randomUUID().toString().also {
                projectCreatedAt[it] = System.currentTimeMillis()
            }
            val projectName = if (state.currentProjectId == null) {
                buildDefaultProjectName(newBg)
            } else {
                state.currentProjectName
            }
            val updatedLayers = if (prevBg == null) {
                state.layers
            } else {
                val scaleX = newBg.width.toFloat() / prevBg.width.coerceAtLeast(1)
                val scaleY = newBg.height.toFloat() / prevBg.height.coerceAtLeast(1)
                state.layers.map { layer ->
                    layer.copy(
                        offsetX = (layer.offsetX * scaleX).coerceIn(0f, newBg.width.toFloat()),
                        offsetY = (layer.offsetY * scaleY).coerceIn(0f, newBg.height.toFloat()),
                        baseFitScale = OverlayTransformMath.computeInitialFitScale(
                            layerWidth = layer.width,
                            layerHeight = layer.height,
                            bgWidth = newBg.width,
                            bgHeight = newBg.height
                        )
                    )
                }
            }
            state.copy(
                currentProjectId = projectId,
                currentProjectName = projectName,
                background = newBg,
                layers = updatedLayers
            )
        }
        scheduleAutoSave(delayMs = 150L)
    }

    fun importOverlayLayersFromUris(
        context: Context,
        uris: List<Uri>,
        onComplete: (Int) -> Unit
    ) {
        val bg = _uiState.value.background
        if (bg == null || uris.isEmpty()) {
            onComplete(0)
            return
        }
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { idx, uri ->
                    decodeUriToBitmap(context, uri, MAX_LAYER_EDGE, "layer_${idx + 1}")
                }
            }
            if (decoded.isEmpty()) {
                onComplete(0)
                return@launch
            }
            _uiState.update { state ->
                val currentBg = state.background ?: return@update state
                val existingCount = state.layers.size
                val step = (minOf(currentBg.width, currentBg.height) * 0.03f).coerceIn(8f, 48f)
                val newLayers = decoded.mapIndexed { idx, (name, bitmap) ->
                    val cascade = ((existingCount + idx) % 5 - 2) * step
                    OverlayLayer(
                        name = name,
                        bitmap = bitmap,
                        width = bitmap.width,
                        height = bitmap.height,
                        hasAlpha = bitmapHasTransparency(bitmap),
                        offsetX = (currentBg.width / 2f + cascade).coerceIn(0f, currentBg.width.toFloat()),
                        offsetY = (currentBg.height / 2f + cascade).coerceIn(0f, currentBg.height.toFloat()),
                        baseFitScale = OverlayTransformMath.computeInitialFitScale(
                            layerWidth = bitmap.width,
                            layerHeight = bitmap.height,
                            bgWidth = currentBg.width,
                            bgHeight = currentBg.height
                        ),
                        scaleX = 1f,
                        scaleY = 1f,
                        rotationDegrees = 0f,
                        opacity = 1f,
                        isVisible = true,
                        pixelCrisp = false
                    )
                }
                state.copy(
                    layers = state.layers + newLayers,
                    selectedLayerId = newLayers.last().id
                )
            }
            appContext = context.applicationContext
            scheduleAutoSave()
            onComplete(decoded.size)
        }
    }

    fun addOverlayLayerFromPixels(
        name: String,
        width: Int,
        height: Int,
        pixels: IntArray
    ) {
        val bg = _uiState.value.background ?: return
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(pixels, safeW, safeH, Bitmap.Config.ARGB_8888)
        val hasAlpha = pixels.any { ((it ushr 24) and 0xFF) < 255 }
        val newLayer = OverlayLayer(
            name = sanitizeBaseName(name, "pixel_layer"),
            bitmap = bitmap,
            width = safeW,
            height = safeH,
            hasAlpha = hasAlpha,
            offsetX = bg.width / 2f,
            offsetY = bg.height / 2f,
            baseFitScale = OverlayTransformMath.computeInitialFitScale(
                layerWidth = safeW,
                layerHeight = safeH,
                bgWidth = bg.width,
                bgHeight = bg.height
            ),
            scaleX = 1f,
            scaleY = 1f,
            rotationDegrees = 0f,
            opacity = 1f,
            isVisible = true,
            pixelCrisp = max(safeW, safeH) <= 256
        )
        _uiState.update { state ->
            state.copy(
                layers = state.layers + newLayer,
                selectedLayerId = newLayer.id
            )
        }
        scheduleAutoSave()
    }

    fun selectLayer(layerId: String?) {
        _uiState.update { it.copy(selectedLayerId = layerId) }
        scheduleAutoSave()
    }

    fun transformSelectedLayer(
        dxCanvas: Float,
        dyCanvas: Float,
        zoomFactor: Float,
        rotationDeltaDeg: Float
    ) {
        _uiState.update { state ->
            val bg = state.background ?: return@update state
            val targetId = state.selectedLayerId ?: return@update state
            val updated = state.layers.map { layer ->
                if (layer.id != targetId) {
                    layer
                } else {
                    val halfW = (layer.width * layer.effectiveScaleX) / 2f
                    val halfH = (layer.height * layer.effectiveScaleY) / 2f
                    val freeMarginX = max(bg.width.toFloat(), halfW * 2f) * 3f + 4000f
                    val freeMarginY = max(bg.height.toFloat(), halfH * 2f) * 3f + 4000f
                    val nextX = (layer.offsetX + dxCanvas).coerceIn(-freeMarginX, bg.width + freeMarginX)
                    val nextY = (layer.offsetY + dyCanvas).coerceIn(-freeMarginY, bg.height + freeMarginY)
                    val nextScaleX = (layer.scaleX * zoomFactor).coerceIn(
                        OverlayTransformMath.MIN_LAYER_SCALE,
                        OverlayTransformMath.MAX_LAYER_SCALE
                    )
                    val nextScaleY = (layer.scaleY * zoomFactor).coerceIn(
                        OverlayTransformMath.MIN_LAYER_SCALE,
                        OverlayTransformMath.MAX_LAYER_SCALE
                    )
                    val nextRot = OverlayTransformMath.normalizeAngleDegrees(
                        layer.rotationDegrees + rotationDeltaDeg
                    )
                    layer.copy(
                        offsetX = nextX,
                        offsetY = nextY,
                        scaleX = nextScaleX,
                        scaleY = nextScaleY,
                        rotationDegrees = nextRot
                    )
                }
            }
            state.copy(layers = updated)
        }
        scheduleAutoSave()
    }

    fun resizeSelectedLayerByHandle(
        handle: OverlayResizeHandle,
        dxCanvas: Float,
        dyCanvas: Float
    ) {
        updateSelectedLayer { layer ->
            OverlayTransformMath.applyHandleDrag(
                layer = layer,
                handle = handle,
                dxCanvas = dxCanvas,
                dyCanvas = dyCanvas
            )
        }
    }

    fun setSelectedLayerScaleX(newScaleX: Float) {
        updateSelectedLayer { layer ->
            layer.copy(
                scaleX = newScaleX.coerceIn(
                    OverlayTransformMath.MIN_LAYER_SCALE,
                    OverlayTransformMath.MAX_LAYER_SCALE
                )
            )
        }
    }

    fun adjustSelectedLayerScaleX(delta: Float) {
        updateSelectedLayer { layer ->
            layer.copy(
                scaleX = (layer.scaleX + delta).coerceIn(
                    OverlayTransformMath.MIN_LAYER_SCALE,
                    OverlayTransformMath.MAX_LAYER_SCALE
                )
            )
        }
    }

    fun setSelectedLayerScaleY(newScaleY: Float) {
        updateSelectedLayer { layer ->
            layer.copy(
                scaleY = newScaleY.coerceIn(
                    OverlayTransformMath.MIN_LAYER_SCALE,
                    OverlayTransformMath.MAX_LAYER_SCALE
                )
            )
        }
    }

    fun adjustSelectedLayerScaleY(delta: Float) {
        updateSelectedLayer { layer ->
            layer.copy(
                scaleY = (layer.scaleY + delta).coerceIn(
                    OverlayTransformMath.MIN_LAYER_SCALE,
                    OverlayTransformMath.MAX_LAYER_SCALE
                )
            )
        }
    }

    fun setSelectedLayerRotation(degrees: Float) {
        updateSelectedLayer { layer ->
            layer.copy(rotationDegrees = OverlayTransformMath.normalizeAngleDegrees(degrees))
        }
    }

    fun rotateSelectedLayerBy(deltaDegrees: Float) {
        updateSelectedLayer { layer ->
            layer.copy(
                rotationDegrees = OverlayTransformMath.normalizeAngleDegrees(
                    layer.rotationDegrees + deltaDegrees
                )
            )
        }
    }

    fun setSelectedLayerOpacity(opacity: Float) {
        updateSelectedLayer { layer ->
            layer.copy(opacity = opacity.coerceIn(0.05f, 1f))
        }
    }

    fun toggleSelectedLayerPixelCrisp() {
        updateSelectedLayer { layer ->
            layer.copy(pixelCrisp = !layer.pixelCrisp)
        }
    }

    fun centerSelectedLayer() {
        val bg = _uiState.value.background ?: return
        updateSelectedLayer { layer ->
            layer.copy(
                offsetX = bg.width / 2f,
                offsetY = bg.height / 2f
            )
        }
    }

    fun resetSelectedLayerTransform() {
        val bg = _uiState.value.background ?: return
        updateSelectedLayer { layer ->
            layer.copy(
                offsetX = bg.width / 2f,
                offsetY = bg.height / 2f,
                scaleX = 1f,
                scaleY = 1f,
                rotationDegrees = 0f,
                opacity = 1f
            )
        }
    }

    fun toggleLayerVisibility(layerId: String) {
        _uiState.update { state ->
            state.copy(
                layers = state.layers.map {
                    if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it
                }
            )
        }
        scheduleAutoSave()
    }

    fun moveLayerUp(layerId: String) {
        _uiState.update { state ->
            val idx = state.layers.indexOfFirst { it.id == layerId }
            if (idx < 0 || idx >= state.layers.lastIndex) return@update state
            val mutable = state.layers.toMutableList()
            val item = mutable.removeAt(idx)
            mutable.add(idx + 1, item)
            state.copy(layers = mutable)
        }
        scheduleAutoSave()
    }

    fun moveLayerDown(layerId: String) {
        _uiState.update { state ->
            val idx = state.layers.indexOfFirst { it.id == layerId }
            if (idx <= 0) return@update state
            val mutable = state.layers.toMutableList()
            val item = mutable.removeAt(idx)
            mutable.add(idx - 1, item)
            state.copy(layers = mutable)
        }
        scheduleAutoSave()
    }

    fun duplicateLayer(layerId: String) {
        _uiState.update { state ->
            val bg = state.background ?: return@update state
            val idx = state.layers.indexOfFirst { it.id == layerId }
            if (idx < 0) return@update state
            val src = state.layers[idx]
            val offsetDelta = (minOf(bg.width, bg.height) * 0.04f).coerceIn(8f, 40f)
            val copy = src.copy(
                id = UUID.randomUUID().toString(),
                name = "${src.name} (копия)",
                offsetX = src.offsetX + offsetDelta,
                offsetY = src.offsetY + offsetDelta
            )
            val mutable = state.layers.toMutableList()
            mutable.add(idx + 1, copy)
            state.copy(layers = mutable, selectedLayerId = copy.id)
        }
        scheduleAutoSave()
    }

    fun removeLayer(layerId: String) {
        _uiState.update { state ->
            val idx = state.layers.indexOfFirst { it.id == layerId }
            if (idx < 0) return@update state
            val updated = state.layers.filterNot { it.id == layerId }
            val nextSelected = if (state.selectedLayerId == layerId) {
                updated.getOrNull((idx - 1).coerceAtLeast(0))?.id ?: updated.lastOrNull()?.id
            } else {
                state.selectedLayerId
            }
            state.copy(layers = updated, selectedLayerId = nextSelected)
        }
        scheduleAutoSave()
    }

    fun setPreserveAlpha(preserve: Boolean) {
        _uiState.update { it.copy(preserveAlpha = preserve) }
        scheduleAutoSave()
    }

    fun toggleShowTransformFrame() {
        _uiState.update { it.copy(showTransformFrame = !it.showTransformFrame) }
        scheduleAutoSave()
    }

    private inline fun updateSelectedLayer(transform: (OverlayLayer) -> OverlayLayer) {
        _uiState.update { state ->
            val targetId = state.selectedLayerId ?: return@update state
            state.copy(
                layers = state.layers.map { if (it.id == targetId) transform(it) else it }
            )
        }
        scheduleAutoSave()
    }

    fun renderCompositeBitmap(state: OverlayUiState = _uiState.value): Bitmap? {
        val bg = state.background ?: return null
        val outBitmap = Bitmap.createBitmap(bg.width, bg.height, Bitmap.Config.ARGB_8888)
        outBitmap.setHasAlpha(state.preserveAlpha)

        val canvas = Canvas(outBitmap)
        if (!state.preserveAlpha) {
            canvas.drawColor(Color.BLACK)
        }

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bg.bitmap, 0f, 0f, bgPaint)

        for (layer in state.layers) {
            if (!layer.isVisible) continue
            canvas.save()
            canvas.translate(layer.offsetX, layer.offsetY)
            canvas.rotate(layer.rotationDegrees)
            val halfW = (layer.width * layer.effectiveScaleX) / 2f
            val halfH = (layer.height * layer.effectiveScaleY) / 2f
            val dstRect = RectF(-halfW, -halfH, halfW, halfH)
            val layerPaint = Paint().apply {
                isAntiAlias = !layer.pixelCrisp
                isFilterBitmap = !layer.pixelCrisp
                alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(layer.bitmap, null, dstRect, layerPaint)
            canvas.restore()
        }

        return outBitmap
    }

    fun exportToGallery(
        context: Context,
        onComplete: (Result<String>) -> Unit
    ) {
        val snapshot = _uiState.value
        val bg = snapshot.background ?: return
        if (snapshot.isExporting) return

        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                val fileName = "${bg.name}_overlay_${System.currentTimeMillis() % 100000}.png"
                val bytes = withContext(Dispatchers.Default) {
                    val composite = renderCompositeBitmap(snapshot)
                        ?: error("Фоновое изображение не выбрано")
                    encodePngBytes(composite, snapshot.preserveAlpha)
                }
                withContext(Dispatchers.IO) {
                    savePngToGallery(context, fileName, bytes)
                }
                fileName
            }
            _uiState.update { it.copy(isExporting = false) }
            onComplete(result)
        }
    }

    fun exportToFolder(
        context: Context,
        treeUri: Uri,
        onComplete: (Result<String>) -> Unit
    ) {
        val snapshot = _uiState.value
        val bg = snapshot.background ?: return
        if (snapshot.isExporting) return

        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                val fileName = "${bg.name}_overlay_${System.currentTimeMillis() % 100000}.png"
                val bytes = withContext(Dispatchers.Default) {
                    val composite = renderCompositeBitmap(snapshot)
                        ?: error("Фоновое изображение не выбрано")
                    encodePngBytes(composite, snapshot.preserveAlpha)
                }
                withContext(Dispatchers.IO) {
                    val parentDocId = DocumentsContract.getTreeDocumentId(treeUri)
                    val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
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
                fileName
            }
            _uiState.update { it.copy(isExporting = false) }
            onComplete(result)
        }
    }

    fun exportForSharing(
        context: Context,
        onComplete: (Result<Uri>) -> Unit
    ) {
        val snapshot = _uiState.value
        val bg = snapshot.background ?: return
        if (snapshot.isExporting) return

        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            val result = runCatching {
                val fileName = "${bg.name}_overlay_${System.currentTimeMillis() % 100000}.png"
                val bytes = withContext(Dispatchers.Default) {
                    val composite = renderCompositeBitmap(snapshot)
                        ?: error("Фоновое изображение не выбрано")
                    encodePngBytes(composite, snapshot.preserveAlpha)
                }
                withContext(Dispatchers.IO) {
                    val shareDir = File(context.cacheDir, "exports/overlay").apply {
                        deleteRecursively()
                        mkdirs()
                    }
                    val outFile = File(shareDir, fileName)
                    FileOutputStream(outFile).use { stream ->
                        stream.write(bytes)
                    }
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outFile
                    )
                }
            }
            _uiState.update { it.copy(isExporting = false) }
            onComplete(result)
        }
    }

    private fun encodePngBytes(bitmap: Bitmap, preserveAlpha: Boolean): ByteArray {
        if (!preserveAlpha) {
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

    private fun savePngToGallery(
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
                    "${Environment.DIRECTORY_PICTURES}/PixelResize/Overlay"
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
                "PixelResize/Overlay"
            ).apply { mkdirs() }
            val outFile = File(exportDir, fileName)
            FileOutputStream(outFile).use { stream ->
                stream.write(bytes)
            }
        }
    }

    private fun decodeUriToBitmap(
        context: Context,
        uri: Uri,
        maxEdge: Int,
        fallbackName: String
    ): Pair<String, Bitmap>? {
        return runCatching {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val longest = max(info.size.width, info.size.height)
                    if (longest > maxEdge) {
                        val sample = ((longest + maxEdge - 1) / maxEdge).coerceAtLeast(2)
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
                if (longest > maxEdge) {
                    while (longest / sampleSize > maxEdge) {
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
                } ?: error("Не удалось открыть изображение")
            }

            val argbBitmap = if (rawBitmap.config != Bitmap.Config.ARGB_8888) {
                rawBitmap.copy(Bitmap.Config.ARGB_8888, false).also {
                    if (it !== rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            val resolvedName = resolveDisplayName(context, uri)
            sanitizeBaseName(resolvedName, fallbackName) to argbBitmap
        }.getOrNull()
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx)?.substringBeforeLast('.') else null
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun bitmapHasTransparency(bitmap: Bitmap): Boolean {
        if (!bitmap.hasAlpha()) return false
        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 32).coerceAtLeast(1)
        val stepY = (h / 32).coerceAtLeast(1)
        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                if (((bitmap.getPixel(x, y) ushr 24) and 0xFF) < 255) {
                    return true
                }
            }
        }
        return false
    }

    private fun buildDefaultProjectName(bg: OverlayBackground): String {
        val label = if (bg.name.startsWith("canvas_")) {
            "Холст"
        } else {
            bg.name
        }
        return "$label (${bg.width}×${bg.height})".take(48)
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
        private const val MAX_BG_EDGE = 4096
        private const val MAX_LAYER_EDGE = 2048
        private const val AUTO_SAVE_DEBOUNCE_MS = 600L
    }
}
