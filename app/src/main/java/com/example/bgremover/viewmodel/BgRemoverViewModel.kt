package com.example.bgremover.viewmodel

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
import com.example.bgremover.engine.BgRemoverEngine
import com.example.bgremover.model.BgRemovalMethod
import com.example.bgremover.model.BgRemoverItem
import com.example.bgremover.model.BgRemoverSettings
import com.example.bgremover.model.BgRemoverUiState
import com.example.bgremover.model.BgTouchTool
import com.example.resizer.model.formatExactFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

class BgRemoverViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(BgRemoverUiState())
    val uiState: StateFlow<BgRemoverUiState> = _uiState.asStateFlow()

    private val undoStacks = mutableMapOf<String, ArrayDeque<IntArray>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<IntArray>>()
    private var reprocessJob: Job? = null
    private var lastAutoUndoItemId: String? = null
    private var lastAutoUndoTimestamp: Long = 0L

    // Active manual brush stroke session
    private var strokeItemId: String? = null
    private var strokeStartSnapshot: IntArray? = null
    private var strokeWorkingPixels: IntArray? = null
    private var lastBrushX: Float = -1f
    private var lastBrushY: Float = -1f
    private var strokeDidModify: Boolean = false

    fun setMethod(method: BgRemovalMethod) {
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(method = method),
                showOriginalComparison = false,
                lastSavedSummary = null
            )
        }
        scheduleReprocessActiveItem(immediate = true, recordUndo = true)
    }

    fun setSensitivity(sensitivity: Int) {
        val clamped = sensitivity.coerceIn(1, 100)
        if (_uiState.value.settings.sensitivity == clamped) return
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(sensitivity = clamped),
                showOriginalComparison = false,
                lastSavedSummary = null
            )
        }
        scheduleReprocessActiveItem(immediate = false, recordUndo = true)
    }

    fun setEdgeSoftness(softness: Int) {
        val clamped = softness.coerceIn(0, 100)
        if (_uiState.value.settings.edgeSoftness == clamped) return
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(edgeSoftness = clamped),
                showOriginalComparison = false,
                lastSavedSummary = null
            )
        }
        scheduleReprocessActiveItem(immediate = false, recordUndo = true)
    }

    fun setRemoveInnerHoles(remove: Boolean) {
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(removeInnerHoles = remove),
                showOriginalComparison = false,
                lastSavedSummary = null
            )
        }
        scheduleReprocessActiveItem(immediate = true, recordUndo = true)
    }

    fun resetAutoBackgroundColor() {
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(targetColorArgb = null),
                showOriginalComparison = false,
                lastSavedSummary = null
            )
        }
        scheduleReprocessActiveItem(immediate = true, recordUndo = true)
    }

    fun pickBackgroundColorAt(x: Int, y: Int): Int? {
        val item = _uiState.value.activeItem ?: return null
        if (x !in 0 until item.width || y !in 0 until item.height) return null
        val sampled = item.originalPixels[y * item.width + x]
        val alpha = (sampled ushr 24) and 0xFF
        if (alpha < 16) return null
        val opaqueColor = (0xFF shl 24) or (sampled and 0x00FFFFFF)
        val nextMethod = if (_uiState.value.settings.method == BgRemovalMethod.SMART_SUBJECT) {
            BgRemovalMethod.COLOR_KEY
        } else {
            _uiState.value.settings.method
        }
        _uiState.update { state ->
            state.copy(
                settings = state.settings.copy(
                    method = nextMethod,
                    targetColorArgb = opaqueColor
                ),
                showOriginalComparison = false,
                lastSavedSummary = null
            )
        }
        scheduleReprocessActiveItem(immediate = true, recordUndo = true)
        return opaqueColor
    }

    fun setTouchTool(tool: BgTouchTool) {
        _uiState.update { state ->
            state.copy(
                touchTool = tool,
                showOriginalComparison = if (tool == BgTouchTool.ERASE || tool == BgTouchTool.RESTORE || tool == BgTouchTool.PICK_COLOR) {
                    false
                } else {
                    state.showOriginalComparison
                }
            )
        }
    }

    fun setBrushSize(sizePx: Int) {
        _uiState.update { it.copy(brushSizePx = sizePx.coerceIn(4, 80)) }
    }

    fun setShowOriginalComparison(showOriginal: Boolean) {
        _uiState.update { it.copy(showOriginalComparison = showOriginal) }
    }

    fun reapplyActiveItem() {
        _uiState.update { it.copy(showOriginalComparison = false, lastSavedSummary = null) }
        scheduleReprocessActiveItem(immediate = true, recordUndo = true)
    }

    fun resetActiveItemToOriginal() {
        val active = _uiState.value.activeItem ?: return
        pushUndoSnapshot(active.id, active.processedPixels.copyOf())
        val restored = active.originalPixels.copyOf()
        val pct = BgRemoverEngine.computeTransparentPercent(restored)
        updateItemPixels(active.id, restored, pct)
    }

    fun applySettingsToAll(onComplete: (Int) -> Unit = {}) {
        val snapshot = _uiState.value
        if (snapshot.isProcessing || snapshot.items.isEmpty()) return
        val settings = snapshot.settings.normalized()
        val total = snapshot.items.size
        _uiState.update {
            it.copy(
                isProcessing = true,
                progressCurrent = 0,
                progressTotal = total,
                showOriginalComparison = false,
                lastSavedSummary = null
            )
        }
        viewModelScope.launch {
            val updatedItems = mutableListOf<BgRemoverItem>()
            var done = 0
            for (item in snapshot.items) {
                pushUndoSnapshot(item.id, item.processedPixels.copyOf())
                val res = withContext(Dispatchers.Default) {
                    BgRemoverEngine.removeBackground(
                        originalPixels = item.originalPixels,
                        width = item.width,
                        height = item.height,
                        settings = settings
                    )
                }
                updatedItems.add(
                    item.copy(
                        processedPixels = res.pixels,
                        detectedBgColorArgb = res.detectedBgColorArgb,
                        appliedSettings = settings,
                        transparentPercent = res.transparentPercent,
                        version = item.version + 1L
                    )
                )
                done++
                _uiState.update { it.copy(progressCurrent = done) }
            }
            _uiState.update { state ->
                val activeId = state.activeItemId
                state.copy(
                    items = updatedItems,
                    isProcessing = false,
                    progressCurrent = 0,
                    progressTotal = 0,
                    canUndo = activeId?.let { (undoStacks[it]?.isNotEmpty() == true) } ?: false,
                    canRedo = activeId?.let { (redoStacks[it]?.isNotEmpty() == true) } ?: false
                )
            }
            onComplete(done)
        }
    }

    fun selectItem(id: String) {
        val item = _uiState.value.items.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(
                activeItemId = id,
                settings = item.appliedSettings,
                showOriginalComparison = false,
                canUndo = undoStacks[id]?.isNotEmpty() == true,
                canRedo = redoStacks[id]?.isNotEmpty() == true,
                lastSavedSummary = null
            )
        }
    }

    fun removeItem(id: String) {
        undoStacks.remove(id)
        redoStacks.remove(id)
        _uiState.update { state ->
            val updated = state.items.filterNot { it.id == id }
            val nextActive = if (state.activeItemId == id) {
                updated.firstOrNull()?.id
            } else {
                state.activeItemId
            }
            val nextItem = updated.firstOrNull { it.id == nextActive }
            state.copy(
                items = updated,
                activeItemId = nextActive,
                settings = nextItem?.appliedSettings ?: state.settings,
                canUndo = nextActive?.let { undoStacks[it]?.isNotEmpty() == true } ?: false,
                canRedo = nextActive?.let { redoStacks[it]?.isNotEmpty() == true } ?: false,
                lastSavedSummary = null
            )
        }
    }

    fun clearItems() {
        reprocessJob?.cancel()
        undoStacks.clear()
        redoStacks.clear()
        clearStrokeSession()
        _uiState.update {
            it.copy(
                items = emptyList(),
                activeItemId = null,
                showOriginalComparison = false,
                canUndo = false,
                canRedo = false,
                lastSavedSummary = null
            )
        }
    }

    fun clearSavedSummary() {
        _uiState.update { it.copy(lastSavedSummary = null) }
    }

    fun undo() {
        val active = _uiState.value.activeItem ?: return
        val stack = undoStacks[active.id] ?: return
        if (stack.isEmpty()) return
        val prev = stack.removeLast()
        val redo = redoStacks.getOrPut(active.id) { ArrayDeque() }
        redo.addLast(active.processedPixels.copyOf())
        if (redo.size > MAX_UNDO_STEPS) redo.removeFirst()

        val pct = BgRemoverEngine.computeTransparentPercent(prev)
        updateItemPixels(active.id, prev, pct)
    }

    fun redo() {
        val active = _uiState.value.activeItem ?: return
        val stack = redoStacks[active.id] ?: return
        if (stack.isEmpty()) return
        val next = stack.removeLast()
        val undo = undoStacks.getOrPut(active.id) { ArrayDeque() }
        undo.addLast(active.processedPixels.copyOf())
        if (undo.size > MAX_UNDO_STEPS) undo.removeFirst()

        val pct = BgRemoverEngine.computeTransparentPercent(next)
        updateItemPixels(active.id, next, pct)
    }

    fun beginBrushStroke(x: Float, y: Float) {
        val tool = _uiState.value.touchTool
        if (tool != BgTouchTool.ERASE && tool != BgTouchTool.RESTORE) return
        val item = _uiState.value.activeItem ?: return

        strokeItemId = item.id
        strokeStartSnapshot = item.processedPixels.copyOf()
        val working = item.processedPixels.copyOf()
        strokeWorkingPixels = working
        lastBrushX = x
        lastBrushY = y
        strokeDidModify = false

        val radius = (_uiState.value.brushSizePx / 2f).coerceAtLeast(1f)
        BgRemoverEngine.applyBrushStroke(
            targetPixels = working,
            originalPixels = item.originalPixels,
            width = item.width,
            height = item.height,
            x0 = x,
            y0 = y,
            x1 = x,
            y1 = y,
            radiusPx = radius,
            restore = tool == BgTouchTool.RESTORE
        )
        strokeDidModify = true
        publishStrokeFrame(item.id, working)
    }

    fun continueBrushStroke(x: Float, y: Float) {
        val itemId = strokeItemId ?: return
        val working = strokeWorkingPixels ?: return
        val item = _uiState.value.items.firstOrNull { it.id == itemId } ?: return
        val tool = _uiState.value.touchTool
        if (tool != BgTouchTool.ERASE && tool != BgTouchTool.RESTORE) return

        val radius = (_uiState.value.brushSizePx / 2f).coerceAtLeast(1f)
        BgRemoverEngine.applyBrushStroke(
            targetPixels = working,
            originalPixels = item.originalPixels,
            width = item.width,
            height = item.height,
            x0 = lastBrushX,
            y0 = lastBrushY,
            x1 = x,
            y1 = y,
            radiusPx = radius,
            restore = tool == BgTouchTool.RESTORE
        )
        lastBrushX = x
        lastBrushY = y
        strokeDidModify = true
        publishStrokeFrame(itemId, working)
    }

    fun endBrushStroke() {
        val itemId = strokeItemId ?: return
        val startSnapshot = strokeStartSnapshot
        val working = strokeWorkingPixels
        if (strokeDidModify && startSnapshot != null && working != null) {
            pushUndoSnapshot(itemId, startSnapshot)
            val pct = BgRemoverEngine.computeTransparentPercent(working)
            _uiState.update { state ->
                val updatedItems = state.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            processedPixels = working,
                            transparentPercent = pct,
                            version = item.version + 1L
                        )
                    } else item
                }
                state.copy(
                    items = updatedItems,
                    canUndo = undoStacks[itemId]?.isNotEmpty() == true,
                    canRedo = redoStacks[itemId]?.isNotEmpty() == true,
                    lastSavedSummary = null
                )
            }
        }
        clearStrokeSession()
    }

    fun cancelBrushStroke() {
        val itemId = strokeItemId
        val startSnapshot = strokeStartSnapshot
        if (itemId != null && startSnapshot != null) {
            val pct = BgRemoverEngine.computeTransparentPercent(startSnapshot)
            updateItemPixels(itemId, startSnapshot, pct, clearRedo = false)
        }
        clearStrokeSession()
    }

    fun addFromEditorCanvas(
        name: String,
        width: Int,
        height: Int,
        pixels: IntArray
    ) {
        val settings = _uiState.value.settings.normalized()
        viewModelScope.launch {
            val safeName = sanitizeBaseName(name, fallback = "canvas")
            val orig = pixels.copyOf()
            val (baselineBytes, removed) = withContext(Dispatchers.Default) {
                val bytes = measureBaselinePngBytes(orig, width, height)
                val res = BgRemoverEngine.removeBackground(orig, width, height, settings)
                bytes to res
            }
            val item = BgRemoverItem(
                baseName = safeName,
                width = width,
                height = height,
                originalPixels = orig,
                processedPixels = removed.pixels,
                detectedBgColorArgb = removed.detectedBgColorArgb,
                appliedSettings = settings,
                sourceByteSize = baselineBytes,
                transparentPercent = removed.transparentPercent
            )
            _uiState.update { state ->
                state.copy(
                    items = state.items + item,
                    activeItemId = item.id,
                    showOriginalComparison = false,
                    canUndo = false,
                    canRedo = false,
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
        val settings = _uiState.value.settings.normalized()
        _uiState.update { it.copy(isApplyingMask = true, lastSavedSummary = null) }
        viewModelScope.launch {
            val decoded = withContext(Dispatchers.Default) {
                uris.mapIndexedNotNull { index, uri ->
                    decodeUriToItem(context, uri, index, settings)
                }
            }
            _uiState.update { state ->
                if (decoded.isEmpty()) {
                    state.copy(isApplyingMask = false)
                } else {
                    val firstId = decoded.first().id
                    state.copy(
                        items = state.items + decoded,
                        activeItemId = firstId,
                        settings = decoded.first().appliedSettings,
                        showOriginalComparison = false,
                        isApplyingMask = false,
                        canUndo = false,
                        canRedo = false,
                        lastSavedSummary = null
                    )
                }
            }
            onComplete(decoded.size)
        }
    }

    fun exportToGallery(
        context: Context,
        onComplete: (Result<String>) -> Unit
    ) {
        val state = _uiState.value
        if (state.isProcessing || state.items.isEmpty()) return

        val total = state.items.size
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
                var totalBytes = 0L
                val usedNames = mutableSetOf<String>()

                for (item in state.items) {
                    val pngBytes = withContext(Dispatchers.Default) {
                        BgRemoverEngine.encodeTransparentPngBytes(
                            pixels = item.processedPixels,
                            width = item.width,
                            height = item.height
                        )
                    }
                    val fileName = buildUniquePngName(item.baseName, usedNames)
                    withContext(Dispatchers.IO) {
                        savePngBytesToGallery(context, fileName, pngBytes)
                    }
                    totalBytes += pngBytes.size.toLong()
                    savedCount++
                    _uiState.update { it.copy(progressCurrent = savedCount) }
                }

                "Сохранено $savedCount PNG с прозрачностью в Pictures/PixelRCLK/NoBg • ${formatExactFileSize(totalBytes)}"
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
        if (state.isProcessing || state.items.isEmpty()) return

        val total = state.items.size
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
                var totalBytes = 0L
                val usedNames = mutableSetOf<String>()

                for (item in state.items) {
                    val pngBytes = withContext(Dispatchers.Default) {
                        BgRemoverEngine.encodeTransparentPngBytes(
                            pixels = item.processedPixels,
                            width = item.width,
                            height = item.height
                        )
                    }
                    val fileName = buildUniquePngName(item.baseName, usedNames)
                    withContext(Dispatchers.IO) {
                        val docUri = DocumentsContract.createDocument(
                            context.contentResolver,
                            parentDocUri,
                            "image/png",
                            fileName
                        ) ?: error("Не удалось создать файл $fileName")
                        context.contentResolver.openOutputStream(docUri)?.use { stream ->
                            stream.write(pngBytes)
                        } ?: error("Не удалось записать файл $fileName")
                    }
                    totalBytes += pngBytes.size.toLong()
                    savedCount++
                    _uiState.update { it.copy(progressCurrent = savedCount) }
                }

                "Сохранено в выбранную папку: $savedCount PNG с прозрачностью • ${formatExactFileSize(totalBytes)}"
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
        if (state.isProcessing || state.items.isEmpty()) return

        val total = state.items.size
        _uiState.update {
            it.copy(
                isProcessing = true,
                progressCurrent = 0,
                progressTotal = total
            )
        }

        viewModelScope.launch {
            val result = runCatching {
                val shareDir = File(context.cacheDir, "exports/nobg").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val uris = mutableListOf<Uri>()
                val usedNames = mutableSetOf<String>()
                var processed = 0

                for (item in state.items) {
                    val pngBytes = withContext(Dispatchers.Default) {
                        BgRemoverEngine.encodeTransparentPngBytes(
                            pixels = item.processedPixels,
                            width = item.width,
                            height = item.height
                        )
                    }
                    val fileName = buildUniquePngName(item.baseName, usedNames)
                    val uri = withContext(Dispatchers.IO) {
                        val outFile = File(shareDir, fileName)
                        FileOutputStream(outFile).use { stream ->
                            stream.write(pngBytes)
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

    private fun scheduleReprocessActiveItem(immediate: Boolean, recordUndo: Boolean) {
        val active = _uiState.value.activeItem ?: return
        val targetId = active.id
        val settingsSnapshot = _uiState.value.settings.normalized()

        if (recordUndo) {
            val now = System.currentTimeMillis()
            if (lastAutoUndoItemId != targetId || now - lastAutoUndoTimestamp > 900L) {
                pushUndoSnapshot(targetId, active.processedPixels.copyOf())
                lastAutoUndoItemId = targetId
                lastAutoUndoTimestamp = now
            }
        }

        reprocessJob?.cancel()
        reprocessJob = viewModelScope.launch {
            if (!immediate) {
                delay(REPROCESS_DEBOUNCE_MS)
            }
            _uiState.update { it.copy(isApplyingMask = true) }
            val currentItem = _uiState.value.items.firstOrNull { it.id == targetId } ?: run {
                _uiState.update { it.copy(isApplyingMask = false) }
                return@launch
            }
            val result = withContext(Dispatchers.Default) {
                BgRemoverEngine.removeBackground(
                    originalPixels = currentItem.originalPixels,
                    width = currentItem.width,
                    height = currentItem.height,
                    settings = settingsSnapshot
                )
            }
            _uiState.update { state ->
                val updatedItems = state.items.map { item ->
                    if (item.id == targetId) {
                        item.copy(
                            processedPixels = result.pixels,
                            detectedBgColorArgb = result.detectedBgColorArgb,
                            appliedSettings = settingsSnapshot,
                            transparentPercent = result.transparentPercent,
                            version = item.version + 1L
                        )
                    } else item
                }
                state.copy(
                    items = updatedItems,
                    isApplyingMask = false,
                    canUndo = state.activeItemId?.let { undoStacks[it]?.isNotEmpty() == true } ?: false,
                    canRedo = state.activeItemId?.let { redoStacks[it]?.isNotEmpty() == true } ?: false
                )
            }
        }
    }

    private fun publishStrokeFrame(itemId: String, working: IntArray) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            processedPixels = working,
                            version = item.version + 1L
                        )
                    } else item
                }
            )
        }
    }

    private fun updateItemPixels(
        itemId: String,
        pixels: IntArray,
        transparentPercent: Int,
        clearRedo: Boolean = false
    ) {
        if (clearRedo) {
            redoStacks[itemId]?.clear()
        }
        _uiState.update { state ->
            val updatedItems = state.items.map { item ->
                if (item.id == itemId) {
                    item.copy(
                        processedPixels = pixels,
                        transparentPercent = transparentPercent,
                        version = item.version + 1L
                    )
                } else item
            }
            state.copy(
                items = updatedItems,
                canUndo = state.activeItemId?.let { undoStacks[it]?.isNotEmpty() == true } ?: false,
                canRedo = state.activeItemId?.let { redoStacks[it]?.isNotEmpty() == true } ?: false,
                lastSavedSummary = null
            )
        }
    }

    private fun pushUndoSnapshot(itemId: String, snapshot: IntArray) {
        val stack = undoStacks.getOrPut(itemId) { ArrayDeque() }
        stack.addLast(snapshot)
        while (stack.size > MAX_UNDO_STEPS) {
            stack.removeFirst()
        }
        redoStacks[itemId]?.clear()
    }

    private fun clearStrokeSession() {
        strokeItemId = null
        strokeStartSnapshot = null
        strokeWorkingPixels = null
        lastBrushX = -1f
        lastBrushY = -1f
        strokeDidModify = false
    }

    private fun buildUniquePngName(baseName: String, usedNames: MutableSet<String>): String {
        var candidate = "${baseName}_nobg.png"
        var suffix = 2
        while (!usedNames.add(candidate)) {
            candidate = "${baseName}_nobg_${suffix}.png"
            suffix++
        }
        return candidate
    }

    private fun decodeUriToItem(
        context: Context,
        uri: Uri,
        index: Int,
        settings: BgRemoverSettings
    ): BgRemoverItem? {
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
                } ?: error("Не удалось открыть изображение")
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

            val (resolvedName, resolvedSize) = resolveFileMeta(context, uri)
            val finalSize = resolvedSize ?: measureStreamByteSize(context, uri)
                ?: measureBaselinePngBytes(pixels, w, h)
            val removed = BgRemoverEngine.removeBackground(pixels, w, h, settings)

            BgRemoverItem(
                baseName = sanitizeBaseName(resolvedName, fallback = "image_${index + 1}"),
                width = w,
                height = h,
                originalPixels = pixels,
                processedPixels = removed.pixels,
                detectedBgColorArgb = removed.detectedBgColorArgb,
                appliedSettings = settings,
                sourceByteSize = finalSize,
                transparentPercent = removed.transparentPercent
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

    private fun measureBaselinePngBytes(pixels: IntArray, width: Int, height: Int): Long {
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        bitmap.setHasAlpha(true)
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
                    "${Environment.DIRECTORY_PICTURES}/PixelRCLK/NoBg"
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
                "PixelRCLK/NoBg"
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
        private const val MAX_INPUT_EDGE = 1600
        private const val MAX_UNDO_STEPS = 16
        private const val REPROCESS_DEBOUNCE_MS = 120L
    }
}
