package com.example.editor.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.editor.engine.PixelOperations
import com.example.editor.engine.PngExporter
import com.example.editor.model.CanvasPreset
import com.example.editor.model.EditorSnapshot
import com.example.editor.model.EditorTool
import com.example.editor.model.EditorUiState
import com.example.editor.model.EyedropperPreview
import com.example.editor.model.PixelClipboard
import com.example.editor.model.PixelLayer
import com.example.editor.model.PixelSelection
import com.example.editor.model.RecolorShadingMode
import com.example.editor.model.SelectionMode
import com.example.editor.model.SelectionRect
import com.example.editor.storage.EditorProjectRepository
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
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class PixelEditorViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()
    private val maxHistorySize = 40

    private var clipboard: PixelClipboard? = null

    // Active stroke / selection transaction state
    private var strokeStartSnapshot: EditorSnapshot? = null
    private var strokeWorkingPixels: IntArray? = null
    private var strokeVisitedMask: BooleanArray? = null
    private var strokeDidModify = false
    private var lastStrokeX = -1
    private var lastStrokeY = -1

    // Selection creation / move state
    private var selectStartX = -1
    private var selectStartY = -1
    private var selectEndX = -1
    private var selectEndY = -1
    private val activeLassoPoints = mutableListOf<Pair<Int, Int>>()
    private var moveOriginPixels: IntArray? = null
    private var moveOriginMask: BooleanArray? = null
    private var moveLastDx = 0
    private var moveLastDy = 0

    private var prefsContext: Context? = null
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
                    EditorProjectRepository.pruneExpiredProjects(
                        context = ctx,
                        excludeProjectIds = listOfNotNull(activeId).toSet()
                    )
                    EditorProjectRepository.listProjects(ctx)
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
            if (shouldFlush && _uiState.value.currentProjectId != null && _uiState.value.layers.isNotEmpty()) {
                persistCurrentProjectInternal(ctx)
            }
            hasUnsavedChanges = false
            _uiState.update { it.copy(isLoadingProject = true) }
            val result = saveMutex.withLock {
                withContext(NonCancellable + Dispatchers.IO) {
                    val loaded = EditorProjectRepository.loadProject(ctx, projectId)
                    val list = EditorProjectRepository.listProjects(ctx)
                    loaded to list
                }
            }
            val (loaded, list) = result
            if (loaded == null) {
                _uiState.update { it.copy(isLoadingProject = false, savedProjects = list) }
                onComplete(false)
                return@launch
            }

            undoStack.clear()
            redoStack.clear()
            clearStrokeSession()
            projectCreatedAt[loaded.id] = loaded.createdAtMillis

            _uiState.update { state ->
                state.copy(
                    currentProjectId = loaded.id,
                    currentProjectName = loaded.name,
                    canvasWidth = loaded.canvasWidth,
                    canvasHeight = loaded.canvasHeight,
                    layers = loaded.layers,
                    activeLayerId = loaded.activeLayerId,
                    activeTool = loaded.activeTool,
                    brushSize = loaded.brushSize,
                    fillTolerance = loaded.fillTolerance,
                    showPixelGrid = loaded.showPixelGrid,
                    selection = null,
                    selectionPreviewRect = null,
                    lassoPreviewPoints = emptyList(),
                    canUndo = false,
                    canRedo = false,
                    zoomScale = 1f,
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
                    EditorProjectRepository.renameProject(ctx, projectId, trimmed)
                    EditorProjectRepository.listProjects(ctx)
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
                    EditorProjectRepository.deleteProject(ctx, projectId)
                    EditorProjectRepository.listProjects(ctx)
                }
            }
            projectCreatedAt.remove(projectId)
            _uiState.update { state ->
                if (state.currentProjectId == projectId) {
                    val fresh = createInitialState()
                    state.copy(
                        currentProjectId = null,
                        currentProjectName = "Новый рисунок",
                        canvasWidth = fresh.canvasWidth,
                        canvasHeight = fresh.canvasHeight,
                        layers = fresh.layers,
                        activeLayerId = fresh.activeLayerId,
                        selection = null,
                        selectionPreviewRect = null,
                        lassoPreviewPoints = emptyList(),
                        canUndo = false,
                        canRedo = false,
                        zoomScale = 1f,
                        lastAutoSavedAtMillis = null,
                        savedProjects = updatedList
                    )
                } else {
                    state.copy(savedProjects = updatedList)
                }
            }
            if (_uiState.value.currentProjectId == null) {
                undoStack.clear()
                redoStack.clear()
                clearStrokeSession()
            }
        }
    }

    fun flushAutoSaveNow(context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
        }
        val ctx = appContext ?: return
        val state = _uiState.value
        if (state.currentProjectId == null || state.layers.isEmpty()) return
        if (!hasUnsavedChanges && autoSaveJob?.isActive != true) return
        autoSaveJob?.cancel()
        autoSaveJob = null
        viewModelScope.launch {
            persistCurrentProjectInternal(ctx)
        }
    }

    private fun ensureProjectAndScheduleAutoSave(
        suggestedName: String? = null,
        delayMs: Long = AUTO_SAVE_DEBOUNCE_MS
    ) {
        if (_uiState.value.currentProjectId == null) {
            val newId = UUID.randomUUID().toString()
            projectCreatedAt[newId] = System.currentTimeMillis()
            _uiState.update { state ->
                if (state.currentProjectId == null) {
                    val name = (suggestedName ?: "Пиксель-арт (${state.canvasWidth}×${state.canvasHeight})").take(48)
                    state.copy(currentProjectId = newId, currentProjectName = name)
                } else {
                    state
                }
            }
        }
        scheduleAutoSave(delayMs)
    }

    private fun scheduleAutoSaveIfActive(delayMs: Long = AUTO_SAVE_DEBOUNCE_MS) {
        if (_uiState.value.currentProjectId != null) {
            scheduleAutoSave(delayMs)
        }
    }

    private fun scheduleAutoSave(delayMs: Long = AUTO_SAVE_DEBOUNCE_MS) {
        val state = _uiState.value
        if (state.currentProjectId == null || state.layers.isEmpty()) return
        hasUnsavedChanges = true
        val ctx = appContext ?: return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(delayMs)
            persistCurrentProjectInternal(ctx)
        }
    }

    private fun savePreviousProjectSnapshotAsync(prevState: EditorUiState) {
        val ctx = appContext ?: return
        val prevId = prevState.currentProjectId ?: return
        if (!hasUnsavedChanges || prevState.layers.isEmpty()) return
        autoSaveJob?.cancel()
        autoSaveJob = null
        hasUnsavedChanges = false
        val createdAt = projectCreatedAt.getOrPut(prevId) { System.currentTimeMillis() }
        val clonedLayers = prevState.layers.map { it.copy(pixels = it.pixels.copyOf()) }
        val snapshotCopy = prevState.copy(layers = clonedLayers)
        viewModelScope.launch {
            saveMutex.withLock {
                val list = withContext(NonCancellable + Dispatchers.IO) {
                    EditorProjectRepository.saveProject(
                        context = ctx,
                        projectId = prevId,
                        projectName = snapshotCopy.currentProjectName,
                        createdAtMillis = createdAt,
                        state = snapshotCopy
                    )
                    EditorProjectRepository.listProjects(ctx)
                }
                _uiState.update { it.copy(savedProjects = list) }
            }
        }
    }

    private suspend fun persistCurrentProjectInternal(ctx: Context) {
        saveMutex.withLock {
            val snapshot = _uiState.value
            val projectId = snapshot.currentProjectId ?: return@withLock
            if (snapshot.layers.isEmpty()) return@withLock
            val createdAt = projectCreatedAt.getOrPut(projectId) { System.currentTimeMillis() }
            hasUnsavedChanges = false

            _uiState.update { it.copy(isAutoSaving = true) }
            try {
                val (savedAt, updatedList) = withContext(NonCancellable + Dispatchers.IO) {
                    val ts = EditorProjectRepository.saveProject(
                        context = ctx,
                        projectId = projectId,
                        projectName = snapshot.currentProjectName,
                        createdAtMillis = createdAt,
                        state = snapshot
                    )
                    val list = EditorProjectRepository.listProjects(ctx)
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

    fun loadPersistedColors(context: Context) {
        val appCtx = context.applicationContext
        prefsContext = appCtx
        appContext = appCtx
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedRaw = prefs.getString(KEY_SAVED_COLORS, null)
        val recentRaw = prefs.getString(KEY_RECENT_COLORS, null)

        val loadedSaved = savedRaw?.let { parseColorList(it) }
        val loadedRecent = recentRaw?.let { parseColorList(it) }

        if (loadedSaved != null || loadedRecent != null) {
            _uiState.update { state ->
                state.copy(
                    savedColors = loadedSaved ?: state.savedColors,
                    recentColors = loadedRecent ?: state.recentColors
                )
            }
        }
    }

    private fun parseColorList(raw: String): List<Int> {
        if (raw.isBlank()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun persistColors(saved: List<Int>, recent: List<Int>) {
        val ctx = prefsContext ?: return
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SAVED_COLORS, saved.joinToString(","))
            .putString(KEY_RECENT_COLORS, recent.joinToString(","))
            .apply()
    }

    private fun createInitialState(): EditorUiState {
        val width = 32
        val height = 32
        val baseLayer = PixelLayer(
            name = "Слой 1",
            width = width,
            height = height,
            pixels = IntArray(width * height)
        )
        return EditorUiState(
            canvasWidth = width,
            canvasHeight = height,
            layers = listOf(baseLayer),
            activeLayerId = baseLayer.id
        )
    }

    fun selectTool(tool: EditorTool) {
        _uiState.update { state ->
            val nextMode = if (tool == EditorTool.SELECT && state.selection == null && state.selectionMode == SelectionMode.MOVE) {
                SelectionMode.RECTANGLE
            } else {
                state.selectionMode
            }
            state.copy(
                activeTool = tool,
                selectionMode = nextMode,
                eyedropperPreview = null
            )
        }
    }

    fun setSelectionMode(mode: SelectionMode) {
        _uiState.update { it.copy(selectionMode = mode) }
    }

    fun previewColor(argb: Int) {
        _uiState.update { it.copy(selectedColorArgb = argb) }
    }

    fun selectColor(argb: Int) {
        var nextSaved: List<Int> = emptyList()
        var nextRecent: List<Int> = emptyList()
        _uiState.update { state ->
            val updatedRecent = appendRecentColor(state.recentColors, argb)
            nextSaved = state.savedColors
            nextRecent = updatedRecent
            state.copy(
                selectedColorArgb = argb,
                recentColors = updatedRecent
            )
        }
        persistColors(nextSaved, nextRecent)
    }

    fun selectPaletteColor(argb: Int) {
        _uiState.update { it.copy(selectedColorArgb = argb) }
    }

    fun toggleSaveColor(argb: Int = _uiState.value.selectedColorArgb): Boolean {
        var added = false
        var nextSaved: List<Int> = emptyList()
        var nextRecent: List<Int> = emptyList()
        _uiState.update { state ->
            val updatedSaved = if (argb in state.savedColors) {
                added = false
                state.savedColors.filter { it != argb }
            } else {
                added = true
                buildList {
                    add(argb)
                    state.savedColors.take(MAX_SAVED_COLORS - 1).forEach { add(it) }
                }
            }
            nextSaved = updatedSaved
            nextRecent = state.recentColors
            state.copy(savedColors = updatedSaved)
        }
        persistColors(nextSaved, nextRecent)
        return added
    }

    fun saveColorToPalette(argb: Int = _uiState.value.selectedColorArgb): Boolean {
        var added = false
        var nextSaved: List<Int> = emptyList()
        var nextRecent: List<Int> = emptyList()
        _uiState.update { state ->
            val updatedSaved = if (argb in state.savedColors) {
                added = false
                state.savedColors
            } else {
                added = true
                buildList {
                    add(argb)
                    state.savedColors.take(MAX_SAVED_COLORS - 1).forEach { add(it) }
                }
            }
            nextSaved = updatedSaved
            nextRecent = state.recentColors
            state.copy(savedColors = updatedSaved)
        }
        if (added) {
            persistColors(nextSaved, nextRecent)
        }
        return added
    }

    fun removeSavedColor(argb: Int) {
        var nextSaved: List<Int> = emptyList()
        var nextRecent: List<Int> = emptyList()
        _uiState.update { state ->
            val updatedSaved = state.savedColors.filter { it != argb }
            nextSaved = updatedSaved
            nextRecent = state.recentColors
            state.copy(savedColors = updatedSaved)
        }
        persistColors(nextSaved, nextRecent)
    }

    fun clearRecentColors() {
        var nextSaved: List<Int> = emptyList()
        _uiState.update { state ->
            nextSaved = state.savedColors
            state.copy(recentColors = emptyList())
        }
        persistColors(nextSaved, emptyList())
    }

    private fun appendRecentColor(current: List<Int>, argb: Int): List<Int> {
        if (((argb ushr 24) and 0xFF) == 0) return current
        return buildList {
            add(argb)
            current.filter { it != argb }.take(MAX_RECENT_COLORS - 1).forEach { add(it) }
        }
    }

    private fun recordUsedColor(argb: Int) {
        var nextSaved: List<Int> = emptyList()
        var nextRecent: List<Int> = emptyList()
        _uiState.update { state ->
            val updated = appendRecentColor(state.recentColors, argb)
            nextSaved = state.savedColors
            nextRecent = updated
            if (updated == state.recentColors) {
                state
            } else {
                state.copy(recentColors = updated)
            }
        }
        persistColors(nextSaved, nextRecent)
    }

    fun setBrushSize(size: Int) {
        _uiState.update { it.copy(brushSize = size.coerceIn(1, 24)) }
        scheduleAutoSaveIfActive()
    }

    fun setFillTolerance(tolerance: Int) {
        _uiState.update { it.copy(fillTolerance = tolerance.coerceIn(0, 64)) }
        scheduleAutoSaveIfActive()
    }

    fun togglePixelGrid() {
        _uiState.update { it.copy(showPixelGrid = !it.showPixelGrid) }
        scheduleAutoSaveIfActive()
    }

    fun updateZoomScale(scale: Float) {
        _uiState.update { it.copy(zoomScale = scale) }
    }

    // --- Drawing & Pixel Manipulation ---

    fun beginCanvasInteraction(x: Int, y: Int) {
        val state = _uiState.value
        val activeMask = state.selection?.mask
        when (state.activeTool) {
            EditorTool.BRUSH, EditorTool.ERASER -> {
                val activeLayer = state.activeLayer ?: return
                if (!activeLayer.isVisible) return

                strokeStartSnapshot = captureSnapshot(state)
                val working = activeLayer.pixels.copyOf()
                val visited = BooleanArray(state.canvasWidth * state.canvasHeight)
                val isEraser = state.activeTool == EditorTool.ERASER
                val modified = PixelOperations.stampBrush(
                    pixels = working,
                    width = state.canvasWidth,
                    height = state.canvasHeight,
                    cx = x,
                    cy = y,
                    brushSize = state.brushSize,
                    argbColor = state.selectedColorArgb,
                    isEraser = isEraser,
                    strokeVisited = visited,
                    selectionMask = activeMask
                )

                strokeWorkingPixels = working
                strokeVisitedMask = visited
                strokeDidModify = modified
                lastStrokeX = x
                lastStrokeY = y

                _uiState.update { s ->
                    s.copy(
                        hoveredPixel = x to y,
                        layers = s.layers.map {
                            if (it.id == activeLayer.id) {
                                it.copy(pixels = working, version = it.version + 1L)
                            } else it
                        }
                    )
                }
            }

            EditorTool.FILL -> {
                val activeLayer = state.activeLayer ?: return
                if (!activeLayer.isVisible) return
                lastStrokeX = x
                lastStrokeY = y
                _uiState.update { it.copy(hoveredPixel = x to y) }
            }

            EditorTool.SELECT -> {
                beginSelectionInteraction(state, x, y)
            }

            EditorTool.EYEDROPPER -> {
                sampleColorAt(x, y)
            }

            EditorTool.HAND -> Unit
        }
    }

    fun continueCanvasInteraction(x: Int, y: Int) {
        val state = _uiState.value
        when (state.activeTool) {
            EditorTool.BRUSH, EditorTool.ERASER -> {
                val working = strokeWorkingPixels ?: return
                val activeLayer = state.activeLayer ?: return
                if (x == lastStrokeX && y == lastStrokeY) return

                val isEraser = state.activeTool == EditorTool.ERASER
                val modified = PixelOperations.drawLine(
                    pixels = working,
                    width = state.canvasWidth,
                    height = state.canvasHeight,
                    x0 = lastStrokeX,
                    y0 = lastStrokeY,
                    x1 = x,
                    y1 = y,
                    brushSize = state.brushSize,
                    argbColor = state.selectedColorArgb,
                    isEraser = isEraser,
                    strokeVisited = strokeVisitedMask,
                    selectionMask = state.selection?.mask
                )
                if (modified) strokeDidModify = true
                lastStrokeX = x
                lastStrokeY = y

                _uiState.update { s ->
                    s.copy(
                        hoveredPixel = x to y,
                        layers = s.layers.map {
                            if (it.id == activeLayer.id) {
                                it.copy(version = it.version + 1L)
                            } else it
                        }
                    )
                }
            }

            EditorTool.SELECT -> {
                continueSelectionInteraction(state, x, y)
            }

            EditorTool.EYEDROPPER -> {
                sampleColorAt(x, y)
            }

            EditorTool.FILL -> {
                lastStrokeX = x
                lastStrokeY = y
                _uiState.update { it.copy(hoveredPixel = x to y) }
            }

            EditorTool.HAND -> Unit
        }
    }

    fun endCanvasInteraction() {
        val state = _uiState.value
        var shouldSaveCanvas = false
        when (state.activeTool) {
            EditorTool.BRUSH, EditorTool.ERASER -> {
                val startSnap = strokeStartSnapshot
                if (startSnap != null && strokeDidModify) {
                    pushUndoSnapshot(startSnap)
                    shouldSaveCanvas = true
                    if (state.activeTool == EditorTool.BRUSH) {
                        recordUsedColor(state.selectedColorArgb)
                    }
                }
            }

            EditorTool.SELECT -> {
                endSelectionInteraction(state)
            }

            EditorTool.EYEDROPPER -> {
                val preview = state.eyedropperPreview
                if (preview != null && ((preview.argb ushr 24) and 0xFF) > 0) {
                    selectColor(preview.argb)
                }
            }

            EditorTool.FILL -> {
                val activeLayer = state.activeLayer
                if (activeLayer != null && activeLayer.isVisible &&
                    lastStrokeX in 0 until state.canvasWidth &&
                    lastStrokeY in 0 until state.canvasHeight
                ) {
                    val before = captureSnapshot(state)
                    val working = activeLayer.pixels.copyOf()
                    val modified = PixelOperations.floodFill(
                        pixels = working,
                        width = state.canvasWidth,
                        height = state.canvasHeight,
                        startX = lastStrokeX,
                        startY = lastStrokeY,
                        fillColor = state.selectedColorArgb,
                        tolerance = state.fillTolerance,
                        selectionMask = state.selection?.mask
                    )
                    if (modified) {
                        pushUndoSnapshot(before)
                        shouldSaveCanvas = true
                        _uiState.update { s ->
                            s.copy(
                                layers = s.layers.map {
                                    if (it.id == activeLayer.id) {
                                        it.copy(pixels = working, version = it.version + 1L)
                                    } else it
                                }
                            )
                        }
                        recordUsedColor(state.selectedColorArgb)
                    }
                }
            }

            EditorTool.HAND -> Unit
        }
        clearStrokeSession()
        _uiState.update {
            it.copy(
                hoveredPixel = null,
                eyedropperPreview = null,
                selectionPreviewRect = null,
                lassoPreviewPoints = emptyList(),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        if (shouldSaveCanvas) {
            ensureProjectAndScheduleAutoSave()
        }
    }

    /**
     * Called when a 1-finger touch immediately transitions into a 2-finger pinch/pan
     * so accidental single dots or partial selections are cleanly rolled back.
     */
    fun cancelActiveStrokeForGesture() {
        val startSnap = strokeStartSnapshot
        if (startSnap != null) {
            restoreSnapshot(startSnap)
        }
        clearStrokeSession()
        _uiState.update {
            it.copy(
                hoveredPixel = null,
                eyedropperPreview = null,
                selectionPreviewRect = null,
                lassoPreviewPoints = emptyList()
            )
        }
    }

    private fun beginSelectionInteraction(state: EditorUiState, x: Int, y: Int) {
        when (state.selectionMode) {
            SelectionMode.RECTANGLE -> {
                selectStartX = x
                selectStartY = y
                selectEndX = x
                selectEndY = y
                _uiState.update {
                    it.copy(
                        hoveredPixel = x to y,
                        selectionPreviewRect = SelectionRect(x, y, x, y)
                    )
                }
            }

            SelectionMode.LASSO -> {
                activeLassoPoints.clear()
                activeLassoPoints.add(x to y)
                _uiState.update {
                    it.copy(
                        hoveredPixel = x to y,
                        lassoPreviewPoints = activeLassoPoints.toList()
                    )
                }
            }

            SelectionMode.MAGIC_WAND -> {
                val activeLayer = state.activeLayer ?: return
                val mask = PixelOperations.createMagicWandMask(
                    pixels = activeLayer.pixels,
                    width = state.canvasWidth,
                    height = state.canvasHeight,
                    startX = x,
                    startY = y,
                    tolerance = state.fillTolerance
                )
                val before = captureSnapshot(state)
                pushUndoSnapshot(before)
                val nextVersion = (state.selection?.version ?: 0L) + 1L
                _uiState.update {
                    it.copy(
                        hoveredPixel = x to y,
                        selection = PixelSelection(state.canvasWidth, state.canvasHeight, mask, nextVersion),
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty()
                    )
                }
            }

            SelectionMode.MOVE -> {
                val currentSelection = state.selection ?: return
                val activeLayer = state.activeLayer ?: return
                if (!activeLayer.isVisible) return

                strokeStartSnapshot = captureSnapshot(state)
                moveOriginPixels = activeLayer.pixels.copyOf()
                moveOriginMask = currentSelection.mask.copyOf()
                selectStartX = x
                selectStartY = y
                moveLastDx = 0
                moveLastDy = 0
                _uiState.update { it.copy(hoveredPixel = x to y) }
            }
        }
    }

    private fun continueSelectionInteraction(state: EditorUiState, x: Int, y: Int) {
        when (state.selectionMode) {
            SelectionMode.RECTANGLE -> {
                if (selectStartX < 0) return
                selectEndX = x
                selectEndY = y
                _uiState.update {
                    it.copy(
                        hoveredPixel = x to y,
                        selectionPreviewRect = SelectionRect(selectStartX, selectStartY, x, y)
                    )
                }
            }

            SelectionMode.LASSO -> {
                if (activeLassoPoints.lastOrNull() != (x to y)) {
                    activeLassoPoints.add(x to y)
                    _uiState.update {
                        it.copy(
                            hoveredPixel = x to y,
                            lassoPreviewPoints = activeLassoPoints.toList()
                        )
                    }
                }
            }

            SelectionMode.MAGIC_WAND -> {
                _uiState.update { it.copy(hoveredPixel = x to y) }
            }

            SelectionMode.MOVE -> {
                val originPixels = moveOriginPixels ?: return
                val originMask = moveOriginMask ?: return
                val activeLayer = state.activeLayer ?: return
                val dx = x - selectStartX
                val dy = y - selectStartY
                if (dx == moveLastDx && dy == moveLastDy) return
                moveLastDx = dx
                moveLastDy = dy

                val (shiftedPixels, shiftedMask) = PixelOperations.translateSelectedPixels(
                    srcPixels = originPixels,
                    srcMask = originMask,
                    width = state.canvasWidth,
                    height = state.canvasHeight,
                    dx = dx,
                    dy = dy
                )
                val nextVersion = (state.selection?.version ?: 0L) + 1L
                _uiState.update { s ->
                    s.copy(
                        hoveredPixel = x to y,
                        selection = PixelSelection(s.canvasWidth, s.canvasHeight, shiftedMask, nextVersion),
                        layers = s.layers.map {
                            if (it.id == activeLayer.id) {
                                it.copy(pixels = shiftedPixels, version = it.version + 1L)
                            } else it
                        }
                    )
                }
            }
        }
    }

    private fun endSelectionInteraction(state: EditorUiState) {
        when (state.selectionMode) {
            SelectionMode.RECTANGLE -> {
                if (selectStartX >= 0 && selectEndX >= 0) {
                    val before = captureSnapshot(state)
                    val mask = PixelOperations.createRectSelectionMask(
                        width = state.canvasWidth,
                        height = state.canvasHeight,
                        x0 = selectStartX,
                        y0 = selectStartY,
                        x1 = selectEndX,
                        y1 = selectEndY
                    )
                    pushUndoSnapshot(before)
                    val nextVersion = (state.selection?.version ?: 0L) + 1L
                    _uiState.update {
                        it.copy(selection = PixelSelection(state.canvasWidth, state.canvasHeight, mask, nextVersion))
                    }
                }
            }

            SelectionMode.LASSO -> {
                if (activeLassoPoints.isNotEmpty()) {
                    val before = captureSnapshot(state)
                    val mask = PixelOperations.createLassoSelectionMask(
                        width = state.canvasWidth,
                        height = state.canvasHeight,
                        points = activeLassoPoints
                    )
                    pushUndoSnapshot(before)
                    val nextVersion = (state.selection?.version ?: 0L) + 1L
                    _uiState.update {
                        it.copy(selection = PixelSelection(state.canvasWidth, state.canvasHeight, mask, nextVersion))
                    }
                }
            }

            SelectionMode.MAGIC_WAND -> Unit

            SelectionMode.MOVE -> {
                val startSnap = strokeStartSnapshot
                if (startSnap != null && (moveLastDx != 0 || moveLastDy != 0)) {
                    pushUndoSnapshot(startSnap)
                }
            }
        }
    }

    private fun clearStrokeSession() {
        strokeStartSnapshot = null
        strokeWorkingPixels = null
        strokeVisitedMask = null
        strokeDidModify = false
        lastStrokeX = -1
        lastStrokeY = -1
        selectStartX = -1
        selectStartY = -1
        selectEndX = -1
        selectEndY = -1
        activeLassoPoints.clear()
        moveOriginPixels = null
        moveOriginMask = null
        moveLastDx = 0
        moveLastDy = 0
    }

    // --- Selection Actions ---

    fun selectAll() {
        val state = _uiState.value
        val before = captureSnapshot(state)
        pushUndoSnapshot(before)
        val mask = BooleanArray(state.canvasWidth * state.canvasHeight) { true }
        val nextVersion = (state.selection?.version ?: 0L) + 1L
        _uiState.update {
            it.copy(
                selection = PixelSelection(state.canvasWidth, state.canvasHeight, mask, nextVersion),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        val state = _uiState.value
        if (state.selection == null) return
        val before = captureSnapshot(state)
        pushUndoSnapshot(before)
        _uiState.update {
            val nextMode = if (it.selectionMode == SelectionMode.MOVE) SelectionMode.RECTANGLE else it.selectionMode
            it.copy(
                selection = null,
                selectionMode = nextMode,
                selectionPreviewRect = null,
                lassoPreviewPoints = emptyList(),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun invertSelection() {
        val state = _uiState.value
        val current = state.selection ?: return
        val before = captureSnapshot(state)
        pushUndoSnapshot(before)
        val inverted = PixelOperations.invertSelectionMask(current.mask)
        val hasAny = inverted.any { it }
        _uiState.update {
            it.copy(
                selection = if (hasAny) {
                    PixelSelection(state.canvasWidth, state.canvasHeight, inverted, current.version + 1L)
                } else {
                    null
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    fun deleteSelectedPixels() {
        val state = _uiState.value
        val current = state.selection ?: return
        val activeLayer = state.activeLayer ?: return
        if (!activeLayer.isVisible) return

        val working = activeLayer.pixels.copyOf()
        val modified = PixelOperations.clearSelectedPixels(working, current.mask)
        if (!modified) return

        pushUndoSnapshot(captureSnapshot(state))
        _uiState.update { s ->
            s.copy(
                layers = s.layers.map {
                    if (it.id == activeLayer.id) it.copy(pixels = working, version = it.version + 1L) else it
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun recolorSelectedPixels(
        targetColorArgb: Int,
        mode: RecolorShadingMode = RecolorShadingMode.LUMINANCE,
        strength: Float = 1f,
        brightnessShift: Float = 0f,
        recolorWhite: Boolean = true
    ): Boolean {
        val state = _uiState.value
        val current = state.selection ?: return false
        val activeLayer = state.activeLayer ?: return false
        if (!activeLayer.isVisible) return false

        val working = activeLayer.pixels.copyOf()
        val modified = PixelOperations.recolorSelectedPixels(
            pixels = working,
            mask = current.mask,
            targetArgb = targetColorArgb,
            mode = mode,
            strength = strength,
            brightnessShift = brightnessShift,
            recolorWhite = recolorWhite
        )
        if (!modified) return false

        pushUndoSnapshot(captureSnapshot(state))
        val updatedRecent = appendRecentColor(state.recentColors, targetColorArgb)
        _uiState.update { s ->
            s.copy(
                selectedColorArgb = targetColorArgb,
                recentColors = updatedRecent,
                layers = s.layers.map {
                    if (it.id == activeLayer.id) it.copy(pixels = working, version = it.version + 1L) else it
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        persistColors(state.savedColors, updatedRecent)
        ensureProjectAndScheduleAutoSave()
        return true
    }

    fun copySelection(): Boolean {
        val state = _uiState.value
        val current = state.selection ?: return false
        val activeLayer = state.activeLayer ?: return false
        val copied = PixelOperations.copySelectionToClipboard(
            pixels = activeLayer.pixels,
            mask = current.mask,
            width = state.canvasWidth,
            height = state.canvasHeight
        ) ?: return false
        clipboard = copied
        _uiState.update { it.copy(hasClipboard = true) }
        return true
    }

    fun cutSelection(): Boolean {
        if (!copySelection()) return false
        deleteSelectedPixels()
        return true
    }

    fun pasteClipboard(): Boolean {
        val clip = clipboard ?: return false
        val state = _uiState.value
        val activeLayer = state.activeLayer ?: return false
        if (!activeLayer.isVisible) return false

        pushUndoSnapshot(captureSnapshot(state))
        val working = activeLayer.pixels.copyOf()
        val pastedMask = PixelOperations.pasteClipboardOntoPixels(
            dstPixels = working,
            dstWidth = state.canvasWidth,
            dstHeight = state.canvasHeight,
            clipboard = clip
        )
        val nextVersion = (state.selection?.version ?: 0L) + 1L
        _uiState.update { s ->
            s.copy(
                layers = s.layers.map {
                    if (it.id == activeLayer.id) it.copy(pixels = working, version = it.version + 1L) else it
                },
                selection = PixelSelection(state.canvasWidth, state.canvasHeight, pastedMask, nextVersion),
                selectionMode = SelectionMode.MOVE,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
        return true
    }

    fun extractSelectionToNewLayer(): Boolean {
        val state = _uiState.value
        val current = state.selection ?: return false
        val activeLayer = state.activeLayer ?: return false

        val newPixels = IntArray(state.canvasWidth * state.canvasHeight)
        var anyNonZero = false
        for (i in newPixels.indices) {
            if (current.mask[i]) {
                newPixels[i] = activeLayer.pixels[i]
                if ((newPixels[i] ushr 24) != 0) anyNonZero = true
            }
        }
        if (!anyNonZero) return false

        pushUndoSnapshot(captureSnapshot(state))
        val newLayer = PixelLayer(
            name = "${activeLayer.name} (выдел.)",
            width = state.canvasWidth,
            height = state.canvasHeight,
            pixels = newPixels
        )
        val insertIdx = (state.layers.indexOfFirst { it.id == activeLayer.id } + 1)
            .coerceIn(0, state.layers.size)
        val updatedLayers = state.layers.toMutableList().apply { add(insertIdx, newLayer) }

        _uiState.update {
            it.copy(
                layers = updatedLayers,
                activeLayerId = newLayer.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
        return true
    }

    private fun sampleColorAt(x: Int, y: Int) {
        val state = _uiState.value
        if (x !in 0 until state.canvasWidth || y !in 0 until state.canvasHeight) return
        val idx = y * state.canvasWidth + x
        var sampled = PixelOperations.TRANSPARENT
        for (layer in state.layers) {
            if (!layer.isVisible || layer.opacity <= 0.001f) continue
            val p = layer.pixels[idx]
            if ((p ushr 24) != 0) {
                sampled = PixelOperations.blendSrcOver(sampled, p, layer.opacity)
            }
        }
        _uiState.update {
            it.copy(
                hoveredPixel = x to y,
                eyedropperPreview = EyedropperPreview(x = x, y = y, argb = sampled),
                selectedColorArgb = if ((sampled ushr 24) > 0) sampled else it.selectedColorArgb
            )
        }
    }

    // --- Undo / Redo ---

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = captureSnapshot(_uiState.value)
        redoStack.addLast(current)
        val previous = undoStack.removeLast()
        restoreSnapshot(previous)
        scheduleAutoSaveIfActive()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = captureSnapshot(_uiState.value)
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        restoreSnapshot(next)
        scheduleAutoSaveIfActive()
    }

    private fun pushUndoSnapshot(snapshot: EditorSnapshot) {
        undoStack.addLast(snapshot)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
    }

    private fun captureSnapshot(state: EditorUiState): EditorSnapshot {
        return EditorSnapshot(
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            layers = state.layers,
            activeLayerId = state.activeLayerId,
            selection = state.selection
        )
    }

    private fun restoreSnapshot(snapshot: EditorSnapshot) {
        _uiState.update {
            it.copy(
                canvasWidth = snapshot.canvasWidth,
                canvasHeight = snapshot.canvasHeight,
                layers = snapshot.layers,
                activeLayerId = snapshot.activeLayerId,
                selection = snapshot.selection,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    // --- Layer Management ---

    fun selectLayer(layerId: String) {
        _uiState.update { it.copy(activeLayerId = layerId) }
        scheduleAutoSaveIfActive()
    }

    fun addLayer() {
        val state = _uiState.value
        pushUndoSnapshot(captureSnapshot(state))
        val nextIndex = state.layers.size + 1
        val newLayer = PixelLayer(
            id = UUID.randomUUID().toString(),
            name = "Слой $nextIndex",
            width = state.canvasWidth,
            height = state.canvasHeight,
            pixels = IntArray(state.canvasWidth * state.canvasHeight)
        )
        _uiState.update {
            it.copy(
                layers = it.layers + newLayer,
                activeLayerId = newLayer.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun duplicateLayer(layerId: String) {
        val state = _uiState.value
        val index = state.layers.indexOfFirst { it.id == layerId }
        if (index == -1) return
        val source = state.layers[index]
        pushUndoSnapshot(captureSnapshot(state))

        val copyLayer = PixelLayer(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (копия)",
            width = source.width,
            height = source.height,
            pixels = source.pixels.copyOf(),
            isVisible = source.isVisible,
            opacity = source.opacity,
            version = 1L
        )
        val updated = state.layers.toMutableList().apply {
            add(index + 1, copyLayer)
        }
        _uiState.update {
            it.copy(
                layers = updated,
                activeLayerId = copyLayer.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun deleteLayer(layerId: String) {
        val state = _uiState.value
        if (state.layers.size <= 1) return
        val index = state.layers.indexOfFirst { it.id == layerId }
        if (index == -1) return

        pushUndoSnapshot(captureSnapshot(state))
        val updated = state.layers.filterNot { it.id == layerId }
        val nextActiveId = if (state.activeLayerId == layerId) {
            updated[index.coerceAtMost(updated.lastIndex)].id
        } else {
            state.activeLayerId
        }
        _uiState.update {
            it.copy(
                layers = updated,
                activeLayerId = nextActiveId,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun toggleLayerVisibility(layerId: String) {
        val state = _uiState.value
        pushUndoSnapshot(captureSnapshot(state))
        _uiState.update { s ->
            s.copy(
                layers = s.layers.map {
                    if (it.id == layerId) it.copy(isVisible = !it.isVisible) else it
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        _uiState.update { s ->
            s.copy(
                layers = s.layers.map {
                    if (it.id == layerId) it.copy(opacity = clamped) else it
                }
            )
        }
    }

    fun commitLayerOpacitySnapshot(beforeState: EditorSnapshot) {
        pushUndoSnapshot(beforeState)
        _uiState.update {
            it.copy(
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun snapshotCurrent(): EditorSnapshot = captureSnapshot(_uiState.value)

    fun renameLayer(layerId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        pushUndoSnapshot(captureSnapshot(state))
        _uiState.update { s ->
            s.copy(
                layers = s.layers.map {
                    if (it.id == layerId) it.copy(name = trimmed) else it
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun moveLayerUp(layerId: String) {
        val state = _uiState.value
        val index = state.layers.indexOfFirst { it.id == layerId }
        if (index == -1 || index >= state.layers.lastIndex) return
        pushUndoSnapshot(captureSnapshot(state))
        val updated = state.layers.toMutableList().apply {
            val item = removeAt(index)
            add(index + 1, item)
        }
        _uiState.update {
            it.copy(
                layers = updated,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun moveLayerDown(layerId: String) {
        val state = _uiState.value
        val index = state.layers.indexOfFirst { it.id == layerId }
        if (index <= 0) return
        pushUndoSnapshot(captureSnapshot(state))
        val updated = state.layers.toMutableList().apply {
            val item = removeAt(index)
            add(index - 1, item)
        }
        _uiState.update {
            it.copy(
                layers = updated,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    fun mergeLayerDown(layerId: String) {
        val state = _uiState.value
        val index = state.layers.indexOfFirst { it.id == layerId }
        if (index <= 0) return

        pushUndoSnapshot(captureSnapshot(state))
        val upper = state.layers[index]
        val lower = state.layers[index - 1]
        val merged = PixelOperations.mergeDown(upper = upper, lower = lower)

        val updated = state.layers.toMutableList().apply {
            set(index - 1, merged)
            removeAt(index)
        }
        _uiState.update {
            it.copy(
                layers = updated,
                activeLayerId = merged.id,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        ensureProjectAndScheduleAutoSave()
    }

    // --- Canvas Creation & Photo Import ---

    fun createNewCanvas(preset: CanvasPreset) {
        savePreviousProjectSnapshotAsync(_uiState.value)
        undoStack.clear()
        redoStack.clear()
        clearStrokeSession()
        val projectId = UUID.randomUUID().toString()
        projectCreatedAt[projectId] = System.currentTimeMillis()
        val baseLayer = PixelLayer(
            name = "Слой 1",
            width = preset.width,
            height = preset.height,
            pixels = IntArray(preset.width * preset.height)
        )
        _uiState.update {
            it.copy(
                currentProjectId = projectId,
                currentProjectName = "Холст ${preset.width}×${preset.height}",
                canvasWidth = preset.width,
                canvasHeight = preset.height,
                layers = listOf(baseLayer),
                activeLayerId = baseLayer.id,
                selection = null,
                selectionPreviewRect = null,
                lassoPreviewPoints = emptyList(),
                canUndo = false,
                canRedo = false,
                zoomScale = 1f,
                lastAutoSavedAtMillis = null
            )
        }
        scheduleAutoSave(delayMs = 150L)
    }

    fun importPhotoFromUri(context: Context, uri: Uri, onComplete: (Boolean) -> Unit) {
        appContext = context.applicationContext
        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                            decoder.isMutableRequired = true
                        }
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        } ?: error("Не удалось открыть поток изображения")
                    }

                    // Scale cleanly to at most 512px on the longest edge to match max canvas preset
                    val maxDim = 512
                    val longest = max(rawBitmap.width, rawBitmap.height).coerceAtLeast(1)
                    val targetW: Int
                    val targetH: Int
                    val scaled = if (longest > maxDim) {
                        val scale = maxDim.toFloat() / longest
                        targetW = (rawBitmap.width * scale).roundToInt().coerceAtLeast(1)
                        targetH = (rawBitmap.height * scale).roundToInt().coerceAtLeast(1)
                        Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
                    } else {
                        targetW = rawBitmap.width.coerceAtLeast(1)
                        targetH = rawBitmap.height.coerceAtLeast(1)
                        rawBitmap
                    }

                    val argbBitmap = if (scaled.config != Bitmap.Config.ARGB_8888) {
                        scaled.copy(Bitmap.Config.ARGB_8888, false)
                    } else {
                        scaled
                    }
                    val pixels = IntArray(targetW * targetH)
                    argbBitmap.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)
                    val layerName = resolveDisplayName(context, uri) ?: "Фото"
                    ImportResult(targetW, targetH, pixels, layerName)
                }.getOrNull()
            }

            if (imported == null) {
                onComplete(false)
                return@launch
            }

            savePreviousProjectSnapshotAsync(_uiState.value)
            undoStack.clear()
            redoStack.clear()
            clearStrokeSession()

            val projectId = UUID.randomUUID().toString()
            projectCreatedAt[projectId] = System.currentTimeMillis()

            val photoLayer = PixelLayer(
                name = imported.layerName,
                width = imported.width,
                height = imported.height,
                pixels = imported.pixels
            )
            _uiState.update {
                it.copy(
                    currentProjectId = projectId,
                    currentProjectName = "${imported.layerName} (${imported.width}×${imported.height})".take(48),
                    canvasWidth = imported.width,
                    canvasHeight = imported.height,
                    layers = listOf(photoLayer),
                    activeLayerId = photoLayer.id,
                    selection = null,
                    selectionPreviewRect = null,
                    lassoPreviewPoints = emptyList(),
                    canUndo = false,
                    canRedo = false,
                    zoomScale = 1f,
                    lastAutoSavedAtMillis = null
                )
            }
            scheduleAutoSave(delayMs = 150L)
            onComplete(true)
        }
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
                    if (idx >= 0) cursor.getString(idx)?.substringBeforeLast('.')?.take(24) else null
                } else {
                    null
                }
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private class ImportResult(
        val width: Int,
        val height: Int,
        val pixels: IntArray,
        val layerName: String
    )

    // --- PNG Export ---

    fun exportToGallery(
        context: Context,
        preserveAlpha: Boolean = true,
        onResult: (Result<Uri>) -> Unit
    ) {
        val state = _uiState.value
        if (state.isExporting) return
        _uiState.update { it.copy(isExporting = true) }

        viewModelScope.launch {
            val composited = withContext(Dispatchers.Default) {
                if (preserveAlpha) {
                    PixelOperations.compositeLayers(state.layers, state.canvasWidth, state.canvasHeight)
                } else {
                    PixelOperations.compositeLayersOpaque(state.layers, state.canvasWidth, state.canvasHeight)
                }
            }
            val result = PngExporter.savePngToGallery(
                context = context,
                pixels = composited,
                width = state.canvasWidth,
                height = state.canvasHeight,
                preserveAlpha = preserveAlpha
            )
            _uiState.update { it.copy(isExporting = false) }
            onResult(result)
        }
    }

    fun exportForSharing(
        context: Context,
        preserveAlpha: Boolean = true,
        onResult: (Result<Uri>) -> Unit
    ) {
        val state = _uiState.value
        if (state.isExporting) return
        _uiState.update { it.copy(isExporting = true) }

        viewModelScope.launch {
            val composited = withContext(Dispatchers.Default) {
                if (preserveAlpha) {
                    PixelOperations.compositeLayers(state.layers, state.canvasWidth, state.canvasHeight)
                } else {
                    PixelOperations.compositeLayersOpaque(state.layers, state.canvasWidth, state.canvasHeight)
                }
            }
            val result = PngExporter.createShareablePngUri(
                context = context,
                pixels = composited,
                width = state.canvasWidth,
                height = state.canvasHeight,
                preserveAlpha = preserveAlpha
            )
            _uiState.update { it.copy(isExporting = false) }
            onResult(result)
        }
    }

    fun exportCurrentCanvasForResizer(): Triple<Int, Int, IntArray> {
        val state = _uiState.value
        val pixels = PixelOperations.compositeLayers(
            layers = state.layers,
            width = state.canvasWidth,
            height = state.canvasHeight
        )
        return Triple(state.canvasWidth, state.canvasHeight, pixels)
    }

    fun loadImagePixelsIntoCanvas(
        name: String,
        width: Int,
        height: Int,
        pixels: IntArray
    ): Boolean {
        if (width !in 1..512 || height !in 1..512 || pixels.size != width * height) {
            return false
        }
        savePreviousProjectSnapshotAsync(_uiState.value)
        undoStack.clear()
        redoStack.clear()
        clearStrokeSession()

        val projectId = UUID.randomUUID().toString()
        projectCreatedAt[projectId] = System.currentTimeMillis()
        val layerName = name.ifBlank { "Ресайз" }.take(24)

        val importedLayer = PixelLayer(
            name = layerName,
            width = width,
            height = height,
            pixels = pixels.copyOf()
        )
        _uiState.update {
            it.copy(
                currentProjectId = projectId,
                currentProjectName = "$layerName (${width}×${height})".take(48),
                canvasWidth = width,
                canvasHeight = height,
                layers = listOf(importedLayer),
                activeLayerId = importedLayer.id,
                selection = null,
                selectionPreviewRect = null,
                lassoPreviewPoints = emptyList(),
                canUndo = false,
                canRedo = false,
                zoomScale = 1f,
                lastAutoSavedAtMillis = null
            )
        }
        scheduleAutoSave(delayMs = 150L)
        return true
    }

    companion object {
        private const val PREFS_NAME = "lilac_pixel_palette"
        private const val KEY_SAVED_COLORS = "saved_colors_v1"
        private const val KEY_RECENT_COLORS = "recent_colors_v1"
        private const val MAX_SAVED_COLORS = 32
        private const val MAX_RECENT_COLORS = 16
        private const val AUTO_SAVE_DEBOUNCE_MS = 600L
    }
}
