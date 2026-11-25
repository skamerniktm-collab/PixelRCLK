package com.example.bgremover.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Compare
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bgremover.model.BgRemovalMethod
import com.example.bgremover.model.BgRemoverItem
import com.example.bgremover.model.BgRemoverSettings
import com.example.bgremover.model.BgRemoverUiState
import com.example.bgremover.model.BgTouchTool
import com.example.bgremover.viewmodel.BgRemoverViewModel
import com.example.editor.ui.components.CheckerboardBox
import com.example.resizer.model.formatExactFileSize
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun BgRemoverScreen(
    viewModel: BgRemoverViewModel,
    onImportFromEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeItem = uiState.activeItem

    val multiGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFromUris(context, uris) { added ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (added > 0) "Загружено изображений: $added"
                        else "Не удалось декодировать выбранные файлы"
                    )
                }
            }
        }
    }

    val multiDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFromUris(context, uris) { added ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (added > 0) "Загружено файлов: $added"
                        else "Не удалось прочитать выбранные файлы"
                    )
                }
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.exportToFolder(context, treeUri) { result ->
                scope.launch {
                    result.onSuccess { message ->
                        snackbarHostState.showSnackbar(message)
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar(
                            "Ошибка сохранения: ${err.localizedMessage ?: "неизвестная"}"
                        )
                    }
                }
            }
        }
    }

    val onSaveToGallery: () -> Unit = {
        viewModel.exportToGallery(context) { result ->
            scope.launch {
                result.onSuccess { message ->
                    snackbarHostState.showSnackbar(message)
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(
                        "Ошибка сохранения: ${err.localizedMessage ?: "неизвестная"}"
                    )
                }
            }
        }
    }

    val onSharePng: () -> Unit = {
        viewModel.exportForSharing(context) { result ->
            result.onSuccess { uris ->
                if (uris.isEmpty()) return@onSuccess
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uris.first())
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "image/png"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                context.startActivity(Intent.createChooser(intent, "Поделиться PNG (${uris.size})"))
            }.onFailure { err ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "Ошибка подготовки PNG: ${err.localizedMessage ?: "неизвестная"}"
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            val isExpanded = maxWidth >= 840.dp

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BgRemoverImportCard(
                            items = uiState.items,
                            activeItemId = activeItem?.id,
                            onImportFromEditor = onImportFromEditor,
                            onPickGallery = {
                                multiGalleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickFiles = { multiDocumentPicker.launch(arrayOf("image/*")) },
                            onSelectItem = viewModel::selectItem,
                            onRemoveItem = viewModel::removeItem,
                            onClearItems = viewModel::clearItems
                        )

                        BgRemoverPreviewCard(
                            activeItem = activeItem,
                            touchTool = uiState.touchTool,
                            brushSizePx = uiState.brushSizePx,
                            showOriginal = uiState.showOriginalComparison,
                            canUndo = uiState.canUndo,
                            canRedo = uiState.canRedo,
                            isApplyingMask = uiState.isApplyingMask,
                            onSelectTouchTool = viewModel::setTouchTool,
                            onBrushSizeChange = viewModel::setBrushSize,
                            onToggleComparison = viewModel::setShowOriginalComparison,
                            onUndo = viewModel::undo,
                            onRedo = viewModel::redo,
                            onPickColorAt = { x, y ->
                                val sampled = viewModel.pickBackgroundColorAt(x, y)
                                if (sampled != null) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Цвет фона выбран — фон обновлён")
                                    }
                                }
                            },
                            onBeginBrush = viewModel::beginBrushStroke,
                            onContinueBrush = viewModel::continueBrushStroke,
                            onEndBrush = viewModel::endBrushStroke,
                            onCancelBrush = viewModel::cancelBrushStroke
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BgRemoverSettingsCard(
                            settings = uiState.settings,
                            activeItem = activeItem,
                            itemCount = uiState.items.size,
                            onSelectMethod = viewModel::setMethod,
                            onSensitivityChange = viewModel::setSensitivity,
                            onEdgeSoftnessChange = viewModel::setEdgeSoftness,
                            onToggleInnerHoles = viewModel::setRemoveInnerHoles,
                            onResetAutoColor = viewModel::resetAutoBackgroundColor,
                            onReapplyActive = viewModel::reapplyActiveItem,
                            onResetToOriginal = viewModel::resetActiveItemToOriginal,
                            onApplyToAll = {
                                viewModel.applySettingsToAll { count ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Фон удалён для изображений: $count")
                                    }
                                }
                            }
                        )

                        BgRemoverExportCard(
                            uiState = uiState,
                            onSaveToGallery = onSaveToGallery,
                            onSaveToFolder = { folderPicker.launch(null) },
                            onShareBatch = onSharePng,
                            onDismissDone = viewModel::clearSavedSummary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        BgRemoverImportCard(
                            items = uiState.items,
                            activeItemId = activeItem?.id,
                            onImportFromEditor = onImportFromEditor,
                            onPickGallery = {
                                multiGalleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickFiles = { multiDocumentPicker.launch(arrayOf("image/*")) },
                            onSelectItem = viewModel::selectItem,
                            onRemoveItem = viewModel::removeItem,
                            onClearItems = viewModel::clearItems
                        )
                    }

                    if (activeItem != null) {
                        item {
                            BgRemoverPreviewCard(
                                activeItem = activeItem,
                                touchTool = uiState.touchTool,
                                brushSizePx = uiState.brushSizePx,
                                showOriginal = uiState.showOriginalComparison,
                                canUndo = uiState.canUndo,
                                canRedo = uiState.canRedo,
                                isApplyingMask = uiState.isApplyingMask,
                                onSelectTouchTool = viewModel::setTouchTool,
                                onBrushSizeChange = viewModel::setBrushSize,
                                onToggleComparison = viewModel::setShowOriginalComparison,
                                onUndo = viewModel::undo,
                                onRedo = viewModel::redo,
                                onPickColorAt = { x, y ->
                                    val sampled = viewModel.pickBackgroundColorAt(x, y)
                                    if (sampled != null) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Цвет фона выбран — фон обновлён")
                                        }
                                    }
                                },
                                onBeginBrush = viewModel::beginBrushStroke,
                                onContinueBrush = viewModel::continueBrushStroke,
                                onEndBrush = viewModel::endBrushStroke,
                                onCancelBrush = viewModel::cancelBrushStroke
                            )
                        }
                    }

                    item {
                        BgRemoverSettingsCard(
                            settings = uiState.settings,
                            activeItem = activeItem,
                            itemCount = uiState.items.size,
                            onSelectMethod = viewModel::setMethod,
                            onSensitivityChange = viewModel::setSensitivity,
                            onEdgeSoftnessChange = viewModel::setEdgeSoftness,
                            onToggleInnerHoles = viewModel::setRemoveInnerHoles,
                            onResetAutoColor = viewModel::resetAutoBackgroundColor,
                            onReapplyActive = viewModel::reapplyActiveItem,
                            onResetToOriginal = viewModel::resetActiveItemToOriginal,
                            onApplyToAll = {
                                viewModel.applySettingsToAll { count ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Фон удалён для изображений: $count")
                                    }
                                }
                            }
                        )
                    }

                    item {
                        BgRemoverExportCard(
                            uiState = uiState,
                            onSaveToGallery = onSaveToGallery,
                            onSaveToFolder = { folderPicker.launch(null) },
                            onShareBatch = onSharePng,
                            onDismissDone = viewModel::clearSavedSummary
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BgRemoverImportCard(
    items: List<BgRemoverItem>,
    activeItemId: String?,
    onImportFromEditor: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
    onSelectItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearItems: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (items.isEmpty()) "Удаление фона" else "Изображений: ${items.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Прозрачный результат сохраняется только в .png (альфа-канал)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (items.isNotEmpty()) {
                    TextButton(
                        onClick = onClearItems,
                        modifier = Modifier.testTag("bg_remover_clear_inputs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистить")
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPickGallery,
                    modifier = Modifier.testTag("bg_remover_pick_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Галерея")
                }

                OutlinedButton(
                    onClick = onPickFiles,
                    modifier = Modifier.testTag("bg_remover_pick_files_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Файлы")
                }

                OutlinedButton(
                    onClick = onImportFromEditor,
                    modifier = Modifier.testTag("bg_remover_import_editor_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Brush,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Из редактора")
                }
            }

            if (items.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val selected = item.id == activeItemId
                        BgRemoverInputChip(
                            item = item,
                            selected = selected,
                            onSelect = { onSelectItem(item.id) },
                            onRemove = { onRemoveItem(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BgRemoverInputChip(
    item: BgRemoverItem,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onSelect)
            .testTag("bg_remover_item_${item.baseName}")
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        RoundedCornerShape(10.dp)
                    )
            ) {
                CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 5f)
                BgRemoverBitmapPreview(
                    pixels = item.processedPixels,
                    width = item.width,
                    height = item.height,
                    version = item.version,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.width(104.dp)) {
                Text(
                    text = item.baseName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.width}×${item.height}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = "Вырезано: ${item.transparentPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("bg_remover_remove_item_${item.baseName}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Удалить ${item.baseName}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BgRemoverPreviewCard(
    activeItem: BgRemoverItem?,
    touchTool: BgTouchTool,
    brushSizePx: Int,
    showOriginal: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    isApplyingMask: Boolean,
    onSelectTouchTool: (BgTouchTool) -> Unit,
    onBrushSizeChange: (Int) -> Unit,
    onToggleComparison: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onPickColorAt: (Int, Int) -> Unit,
    onBeginBrush: (Float, Float) -> Unit,
    onContinueBrush: (Float, Float) -> Unit,
    onEndBrush: () -> Unit,
    onCancelBrush: () -> Unit
) {
    if (activeItem == null) return

    var viewZoom by remember(activeItem.id) { mutableFloatStateOf(1f) }
    var viewPan by remember(activeItem.id) { mutableStateOf(Offset.Zero) }
    var brushCursorCanvas by remember(activeItem.id) { mutableStateOf<Offset?>(null) }

    val currentTool by rememberUpdatedState(touchTool)
    val currentItem by rememberUpdatedState(activeItem)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    val displayPixels = if (showOriginal) activeItem.originalPixels else activeItem.processedPixels
    val displayVersion = if (showOriginal) -1L else activeItem.version
    val displayBitmap = remember(activeItem.id, displayVersion, activeItem.width, activeItem.height) {
        val safeW = activeItem.width.coerceAtLeast(1)
        val safeH = activeItem.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        bmp.setHasAlpha(true)
        bmp.setPixels(displayPixels, 0, safeW, 0, 0, safeW, safeH)
        bmp.asImageBitmap()
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showOriginal) "Предпросмотр: Оригинал" else "Предпросмотр на сетке PNG",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeItem.width}×${activeItem.height} • Прозрачность: ${activeItem.transparentPercent}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.testTag("bg_remover_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = "Отменить",
                            tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier.testTag("bg_remover_redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Redo,
                            contentDescription = "Повторить",
                            tint = if (canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            viewZoom = 1f
                            viewPan = Offset.Zero
                        },
                        modifier = Modifier.testTag("bg_remover_reset_zoom_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = "Сброс масштаба",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Checkerboard interactive viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp, max = 360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("bg_remover_checkerboard_preview")
            ) {
                CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 8f)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(activeItem.id) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val viewW = size.width.toFloat().coerceAtLeast(1f)
                                val viewH = size.height.toFloat().coerceAtLeast(1f)
                                val imgW = currentItem.width.coerceAtLeast(1).toFloat()
                                val imgH = currentItem.height.coerceAtLeast(1).toFloat()
                                val baseFit = min(viewW / imgW, viewH / imgH).coerceAtLeast(0.0001f)

                                fun toImageCoords(pos: Offset): Offset {
                                    val fit = baseFit * viewZoom
                                    val left = (viewW - imgW * fit) / 2f + viewPan.x
                                    val top = (viewH - imgH * fit) / 2f + viewPan.y
                                    return Offset((pos.x - left) / fit, (pos.y - top) / fit)
                                }

                                val tool = currentTool
                                val isBrush = tool == BgTouchTool.ERASE || tool == BgTouchTool.RESTORE
                                val isEyedropper = tool == BgTouchTool.PICK_COLOR
                                var brushStarted = false
                                var movedPastSlop = false
                                var totalDrag = Offset.Zero
                                var maxPointers = 1

                                if (isBrush) {
                                    down.consume()
                                    val imgPt = toImageCoords(down.position)
                                    brushCursorCanvas = imgPt
                                    onBeginBrush(imgPt.x, imgPt.y)
                                    brushStarted = true
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.count { it.pressed }
                                    if (pressed == 0) break
                                    maxPointers = max(maxPointers, pressed)

                                    val pan = event.calculatePan()
                                    val zoom = if (pressed >= 2) event.calculateZoom() else 1f
                                    val centroid = event.calculateCentroid(useCurrent = false)

                                    if (pressed >= 2) {
                                        if (brushStarted) {
                                            onCancelBrush()
                                            brushStarted = false
                                            brushCursorCanvas = null
                                        }
                                        val oldZoom = viewZoom
                                        val nextZoom = (oldZoom * zoom).coerceIn(0.8f, 12f)
                                        val factor = if (oldZoom > 0f) nextZoom / oldZoom else 1f
                                        val vc = Offset(viewW / 2f, viewH / 2f)
                                        val rawPan = if (centroid.isSpecified) {
                                            (viewPan + vc - centroid) * factor + centroid - vc + pan
                                        } else {
                                            viewPan * factor + pan
                                        }
                                        val maxPanX = max(viewW * 0.45f, imgW * baseFit * nextZoom * 0.6f)
                                        val maxPanY = max(viewH * 0.45f, imgH * baseFit * nextZoom * 0.6f)
                                        viewZoom = nextZoom
                                        viewPan = Offset(
                                            rawPan.x.coerceIn(-maxPanX, maxPanX),
                                            rawPan.y.coerceIn(-maxPanY, maxPanY)
                                        )
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else if (isBrush && maxPointers == 1) {
                                        val p = event.changes.firstOrNull { it.pressed }
                                        if (p != null) {
                                            val imgPt = toImageCoords(p.position)
                                            brushCursorCanvas = imgPt
                                            if (brushStarted) {
                                                onContinueBrush(imgPt.x, imgPt.y)
                                            }
                                            p.consume()
                                        }
                                    } else if (tool == BgTouchTool.VIEW && maxPointers == 1) {
                                        totalDrag += pan
                                        if (totalDrag.getDistance() > touchSlop) {
                                            movedPastSlop = true
                                        }
                                        val rawPan = viewPan + pan
                                        val maxPanX = max(viewW * 0.45f, imgW * baseFit * viewZoom * 0.6f)
                                        val maxPanY = max(viewH * 0.45f, imgH * baseFit * viewZoom * 0.6f)
                                        viewPan = Offset(
                                            rawPan.x.coerceIn(-maxPanX, maxPanX),
                                            rawPan.y.coerceIn(-maxPanY, maxPanY)
                                        )
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    } else if (isEyedropper && maxPointers == 1) {
                                        totalDrag += pan
                                        if (totalDrag.getDistance() > touchSlop) {
                                            movedPastSlop = true
                                        }
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                }

                                if (brushStarted) {
                                    onEndBrush()
                                    brushCursorCanvas = null
                                } else if (isEyedropper && maxPointers == 1 && !movedPastSlop) {
                                    val imgPt = toImageCoords(down.position)
                                    val px = imgPt.x.toInt()
                                    val py = imgPt.y.toInt()
                                    if (px in 0 until currentItem.width && py in 0 until currentItem.height) {
                                        onPickColorAt(px, py)
                                    }
                                }
                            }
                        }
                ) {
                    val viewW = size.width
                    val viewH = size.height
                    val imgW = activeItem.width.coerceAtLeast(1).toFloat()
                    val imgH = activeItem.height.coerceAtLeast(1).toFloat()
                    val effectiveFit = min(viewW / imgW, viewH / imgH) * viewZoom
                    val drawW = (imgW * effectiveFit).roundToInt().coerceAtLeast(1)
                    val drawH = (imgH * effectiveFit).roundToInt().coerceAtLeast(1)
                    val left = ((viewW - drawW) * 0.5f + viewPan.x).roundToInt()
                    val top = ((viewH - drawH) * 0.5f + viewPan.y).roundToInt()

                    drawImage(
                        image = displayBitmap,
                        dstOffset = IntOffset(left, top),
                        dstSize = IntSize(drawW, drawH),
                        filterQuality = if (activeItem.width <= 128 && activeItem.height <= 128) {
                            FilterQuality.None
                        } else {
                            FilterQuality.Low
                        }
                    )

                    val cursor = brushCursorCanvas
                    if (cursor != null && (touchTool == BgTouchTool.ERASE || touchTool == BgTouchTool.RESTORE)) {
                        val cx = (viewW - imgW * effectiveFit) * 0.5f + viewPan.x + cursor.x * effectiveFit
                        val cy = (viewH - imgH * effectiveFit) * 0.5f + viewPan.y + cursor.y * effectiveFit
                        val radiusScreen = (brushSizePx * 0.5f) * effectiveFit
                        drawCircle(
                            color = if (touchTool == BgTouchTool.RESTORE) tertiaryColor else primaryColor,
                            radius = radiusScreen.coerceAtLeast(4f),
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                if (isApplyingMask) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Обновление…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Tool selector + Before/After comparison toggle
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BgTouchTool.entries.forEach { tool ->
                    val selected = touchTool == tool
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectTouchTool(tool) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (tool) {
                                    BgTouchTool.VIEW -> Icons.Rounded.PanTool
                                    BgTouchTool.PICK_COLOR -> Icons.Rounded.Colorize
                                    BgTouchTool.ERASE -> Icons.Rounded.CleaningServices
                                    BgTouchTool.RESTORE -> Icons.Rounded.Brush
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tool.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = bgOutlinedChipColors(),
                        border = bgOutlinedChipBorder(selected),
                        modifier = Modifier.testTag("bg_remover_tool_${tool.name.lowercase()}")
                    )
                }

                FilterChip(
                    selected = showOriginal,
                    onClick = { onToggleComparison(!showOriginal) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Compare,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (showOriginal) "Оригинал" else "До / После",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = bgOutlinedChipColors(),
                    border = bgOutlinedChipBorder(showOriginal),
                    modifier = Modifier.testTag("bg_remover_compare_chip")
                )
            }

            if (touchTool == BgTouchTool.ERASE || touchTool == BgTouchTool.RESTORE) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (touchTool == BgTouchTool.ERASE) "Размер ластика" else "Размер кисти восстановления",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$brushSizePx px",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = brushSizePx.toFloat(),
                        onValueChange = { onBrushSizeChange(it.roundToInt()) },
                        valueRange = 4f..80f,
                        modifier = Modifier.testTag("bg_remover_brush_size_slider")
                    )
                }
            } else if (touchTool == BgTouchTool.PICK_COLOR) {
                Text(
                    text = "Нажмите на фон на изображении выше, чтобы выбрать точный цвет для удаления",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BgRemoverSettingsCard(
    settings: BgRemoverSettings,
    activeItem: BgRemoverItem?,
    itemCount: Int,
    onSelectMethod: (BgRemovalMethod) -> Unit,
    onSensitivityChange: (Int) -> Unit,
    onEdgeSoftnessChange: (Int) -> Unit,
    onToggleInnerHoles: (Boolean) -> Unit,
    onResetAutoColor: () -> Unit,
    onReapplyActive: () -> Unit,
    onResetToOriginal: () -> Unit,
    onApplyToAll: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Метод и настройки",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Target / detected background color swatch
                val swatchColor = settings.targetColorArgb ?: activeItem?.detectedBgColorArgb
                if (swatchColor != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (settings.targetColorArgb != null) "Цвет:" else "Авто:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(swatchColor))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                        )
                        if (settings.targetColorArgb != null) {
                            TextButton(
                                onClick = onResetAutoColor,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("bg_remover_reset_color_button")
                            ) {
                                Text("Авто", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BgRemovalMethod.entries.forEach { method ->
                    val selected = settings.method == method
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMethod(method) },
                        label = {
                            Text(
                                text = method.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = bgOutlinedChipColors(),
                        border = bgOutlinedChipBorder(selected),
                        modifier = Modifier.testTag("bg_remover_method_${method.name.lowercase()}")
                    )
                }
            }

            Text(
                text = settings.method.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Чувствительность",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${settings.sensitivity}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.sensitivity.toFloat(),
                    onValueChange = { onSensitivityChange(it.roundToInt()) },
                    valueRange = 1f..100f,
                    modifier = Modifier.testTag("bg_remover_sensitivity_slider")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Смягчение краёв",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${settings.edgeSoftness}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.edgeSoftness.toFloat(),
                    onValueChange = { onEdgeSoftnessChange(it.roundToInt()) },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("bg_remover_softness_slider")
                )
            }

            if (settings.method != BgRemovalMethod.COLOR_KEY) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Удалять внутренний фон",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Очищает замкнутые пустоты (например, внутри букв и деталей)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.removeInnerHoles,
                        onCheckedChange = onToggleInnerHoles,
                        modifier = Modifier.testTag("bg_remover_inner_holes_switch")
                    )
                }
            }

            if (activeItem != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReapplyActive,
                        modifier = Modifier.testTag("bg_remover_reapply_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoFixHigh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Авто-очистка")
                    }

                    OutlinedButton(
                        onClick = onResetToOriginal,
                        modifier = Modifier.testTag("bg_remover_reset_original_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Вернуть исходник")
                    }

                    if (itemCount > 1) {
                        OutlinedButton(
                            onClick = onApplyToAll,
                            modifier = Modifier.testTag("bg_remover_apply_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ко всем ($itemCount)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BgRemoverExportCard(
    uiState: BgRemoverUiState,
    onSaveToGallery: () -> Unit,
    onSaveToFolder: () -> Unit,
    onShareBatch: () -> Unit,
    onDismissDone: () -> Unit
) {
    val canExport = uiState.items.isNotEmpty() && !uiState.isProcessing

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "Экспорт строго в PNG",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "С альфа-каналом (прозрачность), без JPEG",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (uiState.items.isNotEmpty()) {
                    Text(
                        text = "${uiState.items.size} PNG",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (uiState.isProcessing && uiState.progressTotal > 0) {
                val fraction = uiState.progressCurrent.toFloat() / uiState.progressTotal.toFloat()
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .testTag("bg_remover_progress_bar")
                )
                Text(
                    text = "Обработка и сохранение PNG: ${uiState.progressCurrent} из ${uiState.progressTotal}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.lastSavedSummary != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .testTag("bg_remover_saved_status_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = uiState.lastSavedSummary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onDismissDone,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("bg_remover_done_button")
                        ) {
                            Text("Готово")
                        }
                    }
                }
            }

            Button(
                onClick = onSaveToGallery,
                enabled = canExport,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("bg_remover_save_gallery_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SaveAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.items.size > 1) {
                        "Сохранить все (${uiState.items.size} PNG)"
                    } else {
                        "Сохранить PNG"
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSaveToFolder,
                    enabled = canExport,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("bg_remover_save_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("В папку")
                }

                OutlinedButton(
                    onClick = onShareBatch,
                    enabled = canExport,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("bg_remover_share_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Поделиться")
                }
            }

            Text(
                text = "Галерея: Pictures/PixelRCLK/NoBg • Формат: только .png (ARGB_8888)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BgRemoverBitmapPreview(
    pixels: IntArray,
    width: Int,
    height: Int,
    version: Long,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(pixels, width, height, version) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        bmp.setHasAlpha(true)
        bmp.setPixels(pixels, 0, safeW, 0, 0, safeW, safeH)
        bmp.asImageBitmap()
    }

    Canvas(modifier = modifier) {
        val scale = min(
            size.width / width.coerceAtLeast(1),
            size.height / height.coerceAtLeast(1)
        )
        val drawW = (width * scale).roundToInt().coerceAtLeast(1)
        val drawH = (height * scale).roundToInt().coerceAtLeast(1)
        val left = ((size.width - drawW) * 0.5f).roundToInt()
        val top = ((size.height - drawH) * 0.5f).roundToInt()

        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(drawW, drawH),
            filterQuality = if (width <= 128 && height <= 128) FilterQuality.None else FilterQuality.Low
        )
    }
}

@Composable
private fun bgOutlinedChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun bgOutlinedChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.5.dp
)
