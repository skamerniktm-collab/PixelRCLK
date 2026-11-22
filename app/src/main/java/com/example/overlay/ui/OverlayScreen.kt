package com.example.overlay.ui

import android.content.Intent
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.editor.ui.components.CheckerboardBox
import com.example.overlay.model.BackgroundEdgeContact
import com.example.overlay.model.OverlayBackground
import com.example.overlay.model.OverlayLayer
import com.example.overlay.model.OverlayProjectSummary
import com.example.overlay.model.OverlayResizeHandle
import com.example.overlay.model.OverlayTransformMath
import com.example.overlay.model.OverlayUiState
import com.example.overlay.viewmodel.OverlayViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class SourcePickerTarget {
    BACKGROUND,
    OVERLAY_LAYER
}

@Composable
fun OverlayScreen(
    viewModel: OverlayViewModel,
    onGetEditorCanvas: () -> Triple<Int, Int, IntArray>,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var sourcePickerTarget by remember { mutableStateOf<SourcePickerTarget?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var projectToRename by remember { mutableStateOf<OverlayProjectSummary?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initializeProjects(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushAutoSaveNow(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val bgGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackgroundFromUri(context, uri) { ok ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "Фоновое фото загружено" else "Не удалось загрузить изображение"
                    )
                }
            }
        }
    }

    val bgFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackgroundFromUri(context, uri) { ok ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "Фоновое изображение открыто" else "Не удалось прочитать файл"
                    )
                }
            }
        }
    }

    val layerGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importOverlayLayersFromUris(context, uris) { count ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (count > 0) "Добавлено слоёв: $count" else "Не удалось загрузить слой"
                    )
                }
            }
        }
    }

    val layerFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importOverlayLayersFromUris(context, uris) { count ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (count > 0) "Добавлено слоёв: $count" else "Не удалось открыть файлы"
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
                showExportDialog = false
                scope.launch {
                    result.onSuccess { name ->
                        snackbarHostState.showSnackbar("Сохранено в папку: $name")
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar(
                            "Ошибка сохранения: ${err.localizedMessage ?: "неизвестная"}"
                        )
                    }
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
            val bg = uiState.background

            if (bg == null) {
                OverlayEmptyState(
                    onPickGallery = {
                        bgGalleryPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onPickFile = { bgFilePicker.launch(arrayOf("image/*")) },
                    onFromEditor = {
                        val (w, h, pixels) = onGetEditorCanvas()
                        viewModel.setBackgroundFromPixels("canvas_${w}x${h}", w, h, pixels)
                        scope.launch {
                            snackbarHostState.showSnackbar("Холст редактора установлен как фон")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlayTopActionBar(
                            background = bg,
                            layerCount = uiState.layers.size,
                            projectName = uiState.currentProjectName,
                            isAutoSaving = uiState.isAutoSaving,
                            lastAutoSavedAtMillis = uiState.lastAutoSavedAtMillis,
                            onRenameCurrentProject = {
                                uiState.currentProjectId?.let { curId ->
                                    projectToRename = uiState.savedProjects.firstOrNull { it.id == curId }
                                        ?: OverlayProjectSummary(
                                            id = curId,
                                            name = uiState.currentProjectName,
                                            bgWidth = bg.width,
                                            bgHeight = bg.height,
                                            layerCount = uiState.layers.size,
                                            updatedAtMillis = uiState.lastAutoSavedAtMillis ?: System.currentTimeMillis()
                                        )
                                }
                            },
                            onChangeBackground = { sourcePickerTarget = SourcePickerTarget.BACKGROUND },
                            onAddLayer = { sourcePickerTarget = SourcePickerTarget.OVERLAY_LAYER },
                            onOpenExport = { showExportDialog = true }
                        )

                        OverlayInteractiveCanvas(
                            background = bg,
                            layers = uiState.layers,
                            selectedLayerId = uiState.selectedLayerId,
                            showTransformFrame = uiState.showTransformFrame,
                            onToggleTransformFrame = viewModel::toggleShowTransformFrame,
                            onSelectLayer = viewModel::selectLayer,
                            onTransformSelected = viewModel::transformSelectedLayer,
                            onResizeByHandle = viewModel::resizeSelectedLayerByHandle,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(0.95f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OverlayLayersStripCard(
                            layers = uiState.layers,
                            selectedLayerId = uiState.selectedLayerId,
                            onSelectLayer = viewModel::selectLayer,
                            onToggleVisibility = viewModel::toggleLayerVisibility,
                            onAddLayer = { sourcePickerTarget = SourcePickerTarget.OVERLAY_LAYER }
                        )

                        OverlaySelectedLayerControls(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OverlayTopActionBar(
                        background = bg,
                        layerCount = uiState.layers.size,
                        projectName = uiState.currentProjectName,
                        isAutoSaving = uiState.isAutoSaving,
                        lastAutoSavedAtMillis = uiState.lastAutoSavedAtMillis,
                        onRenameCurrentProject = {
                            uiState.currentProjectId?.let { curId ->
                                projectToRename = uiState.savedProjects.firstOrNull { it.id == curId }
                                    ?: OverlayProjectSummary(
                                        id = curId,
                                        name = uiState.currentProjectName,
                                        bgWidth = bg.width,
                                        bgHeight = bg.height,
                                        layerCount = uiState.layers.size,
                                        updatedAtMillis = uiState.lastAutoSavedAtMillis ?: System.currentTimeMillis()
                                    )
                            }
                        },
                        onChangeBackground = { sourcePickerTarget = SourcePickerTarget.BACKGROUND },
                        onAddLayer = { sourcePickerTarget = SourcePickerTarget.OVERLAY_LAYER },
                        onOpenExport = { showExportDialog = true }
                    )

                    OverlayInteractiveCanvas(
                        background = bg,
                        layers = uiState.layers,
                        selectedLayerId = uiState.selectedLayerId,
                        showTransformFrame = uiState.showTransformFrame,
                        onToggleTransformFrame = viewModel::toggleShowTransformFrame,
                        onSelectLayer = viewModel::selectLayer,
                        onTransformSelected = viewModel::transformSelectedLayer,
                        onResizeByHandle = viewModel::resizeSelectedLayerByHandle,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 210.dp)
                            .fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 315.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OverlayLayersStripCard(
                            layers = uiState.layers,
                            selectedLayerId = uiState.selectedLayerId,
                            onSelectLayer = viewModel::selectLayer,
                            onToggleVisibility = viewModel::toggleLayerVisibility,
                            onAddLayer = { sourcePickerTarget = SourcePickerTarget.OVERLAY_LAYER }
                        )

                        OverlaySelectedLayerControls(
                            uiState = uiState,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    sourcePickerTarget?.let { target ->
        OverlaySourceChooserDialog(
            title = if (target == SourcePickerTarget.BACKGROUND) {
                "Выбрать фоновое изображение"
            } else {
                "Добавить слой поверх"
            },
            onPickGallery = {
                sourcePickerTarget = null
                if (target == SourcePickerTarget.BACKGROUND) {
                    bgGalleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                } else {
                    layerGalleryPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onPickFiles = {
                sourcePickerTarget = null
                if (target == SourcePickerTarget.BACKGROUND) {
                    bgFilePicker.launch(arrayOf("image/*"))
                } else {
                    layerFilePicker.launch(arrayOf("image/*"))
                }
            },
            onPickFromEditor = {
                sourcePickerTarget = null
                val (w, h, pixels) = onGetEditorCanvas()
                if (target == SourcePickerTarget.BACKGROUND) {
                    viewModel.setBackgroundFromPixels("canvas_${w}x${h}", w, h, pixels)
                } else {
                    viewModel.addOverlayLayerFromPixels("canvas_${w}x${h}", w, h, pixels)
                }
            },
            onDismiss = { sourcePickerTarget = null }
        )
    }

    val currentBg = uiState.background
    if (showExportDialog && currentBg != null) {
        OverlayExportDialog(
            background = currentBg,
            layerCount = uiState.layers.count { it.isVisible },
            preserveAlpha = uiState.preserveAlpha,
            isExporting = uiState.isExporting,
            onPreserveAlphaChange = viewModel::setPreserveAlpha,
            onSaveGallery = {
                viewModel.exportToGallery(context) { result ->
                    showExportDialog = false
                    scope.launch {
                        result.onSuccess { name ->
                            snackbarHostState.showSnackbar("Сохранено в галерее: $name")
                        }.onFailure { err ->
                            snackbarHostState.showSnackbar(
                                "Ошибка экспорта: ${err.localizedMessage ?: "неизвестная"}"
                            )
                        }
                    }
                }
            },
            onSaveToFolder = {
                folderPicker.launch(null)
            },
            onShare = {
                viewModel.exportForSharing(context) { result ->
                    showExportDialog = false
                    result.onSuccess { uri ->
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Поделиться PNG"))
                    }.onFailure { err ->
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Ошибка подготовки PNG: ${err.localizedMessage ?: "неизвестная"}"
                            )
                        }
                    }
                }
            },
            onDismiss = { if (!uiState.isExporting) showExportDialog = false }
        )
    }
    projectToRename?.let { target ->
        RenameProjectDialog(
            project = target,
            onConfirm = { newName ->
                viewModel.renameProject(context, target.id, newName)
                projectToRename = null
            },
            onDismiss = { projectToRename = null }
        )
    }
}

@Composable
private fun OverlayEmptyState(
    onPickGallery: () -> Unit,
    onPickFile: () -> Unit,
    onFromEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Text(
                    text = "Наложение слоёв поверх фото",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Загрузите основное фото, затем добавляйте логотипы, декали и текстуры — проект сохраняется автоматически во вкладке «Проекты».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onPickGallery,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("overlay_empty_pick_gallery")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Выбрать фото из галереи")
                }

                OutlinedButton(
                    onClick = onPickFile,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("overlay_empty_pick_file")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть файл с устройства")
                }

                OutlinedButton(
                    onClick = onFromEditor,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("overlay_empty_from_editor")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Brush,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Взять холст из редактора")
                }
            }
        }
    }
}

@Composable
private fun OverlayTopActionBar(
    background: OverlayBackground,
    layerCount: Int,
    projectName: String,
    isAutoSaving: Boolean,
    lastAutoSavedAtMillis: Long?,
    onRenameCurrentProject: () -> Unit,
    onChangeBackground: () -> Unit,
    onAddLayer: () -> Unit,
    onOpenExport: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onChangeBackground,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("overlay_change_bg_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Фон (${background.width}×${background.height})")
            }

            OutlinedButton(
                onClick = onAddLayer,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("overlay_add_layer_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (layerCount == 0) "+ Слой" else "+ Слой ($layerCount)")
            }

            Button(
                onClick = onOpenExport,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .testTag("overlay_export_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SaveAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Сохранить PNG")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onRenameCurrentProject)
                    .padding(vertical = 2.dp)
                    .testTag("overlay_current_project_name")
            ) {
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Переименовать текущий проект",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }

            val autoSaveStatus = when {
                isAutoSaving -> "Автосохранение…"
                lastAutoSavedAtMillis != null -> "Сохранено в ${formatShortTime(lastAutoSavedAtMillis)}"
                else -> "Автосохранение"
            }
            Text(
                text = autoSaveStatus,
                style = MaterialTheme.typography.labelSmall,
                color = if (isAutoSaving) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun OverlayInteractiveCanvas(
    background: OverlayBackground,
    layers: List<OverlayLayer>,
    selectedLayerId: String?,
    showTransformFrame: Boolean,
    onToggleTransformFrame: () -> Unit,
    onSelectLayer: (String?) -> Unit,
    onTransformSelected: (dxCanvas: Float, dyCanvas: Float, zoomFactor: Float, rotationDeltaDeg: Float) -> Unit,
    onResizeByHandle: (handle: OverlayResizeHandle, dxCanvas: Float, dyCanvas: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val bgImageBitmap = remember(background.id, background.bitmap) {
        background.bitmap.asImageBitmap()
    }
    val layerBitmaps = remember(layers.map { it.id to it.bitmap }) {
        layers.associate { it.id to it.bitmap.asImageBitmap() }
    }

    val currentBg by rememberUpdatedState(background)
    val currentLayers by rememberUpdatedState(layers)
    val currentSelectedId by rememberUpdatedState(selectedLayerId)
    val currentShowFrame by rememberUpdatedState(showTransformFrame)
    val density = LocalDensity.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val primaryColor = MaterialTheme.colorScheme.primary
    val edgeHighlightColor = MaterialTheme.colorScheme.tertiary
    val bgBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val handleFill = MaterialTheme.colorScheme.surface

    val selectedLayer = layers.firstOrNull { it.id == selectedLayerId }

    var viewZoom by remember(background.id) { mutableFloatStateOf(1f) }
    var viewPan by remember(background.id) { mutableStateOf(Offset.Zero) }
    var isCameraPanMode by remember(background.id) { mutableStateOf(false) }
    var canvasSizePx by remember(background.id) { mutableStateOf(IntSize.Zero) }

    val currentZoom by rememberUpdatedState(viewZoom)
    val currentPan by rememberUpdatedState(viewPan)
    val currentPanMode by rememberUpdatedState(isCameraPanMode)

    val edgeContact = remember(
        selectedLayer,
        background.width,
        background.height,
        canvasSizePx,
        viewZoom,
        density
    ) {
        if (selectedLayer == null || !selectedLayer.isVisible || canvasSizePx.width <= 0 || canvasSizePx.height <= 0) {
            BackgroundEdgeContact()
        } else {
            val viewW = canvasSizePx.width.toFloat()
            val viewH = canvasSizePx.height.toFloat()
            val bgW = background.width.coerceAtLeast(1).toFloat()
            val bgH = background.height.coerceAtLeast(1).toFloat()
            val fitScale = min(viewW / bgW, viewH / bgH) * viewZoom
            val tolCanvas = max(with(density) { 4.5.dp.toPx() } / fitScale.coerceAtLeast(0.0001f), 0.75f)
            OverlayTransformMath.computeEdgeContact(
                layer = selectedLayer,
                bgWidth = background.width,
                bgHeight = background.height,
                toleranceCanvas = tolCanvas
            )
        }
    }

    var lastHapticLayerId by remember(background.id) { mutableStateOf<String?>(null) }
    var prevEdgeContact by remember(background.id) { mutableStateOf(BackgroundEdgeContact()) }
    LaunchedEffect(selectedLayerId, edgeContact) {
        if (selectedLayerId != lastHapticLayerId) {
            lastHapticLayerId = selectedLayerId
            prevEdgeContact = edgeContact
            return@LaunchedEffect
        }
        val newlyTouched =
            (edgeContact.left && !prevEdgeContact.left) ||
                (edgeContact.right && !prevEdgeContact.right) ||
                (edgeContact.top && !prevEdgeContact.top) ||
                (edgeContact.bottom && !prevEdgeContact.bottom)
        if (newlyTouched) {
            val performed = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            if (!performed) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
        prevEdgeContact = edgeContact
    }

    fun clampViewPan(pan: Offset, zoomVal: Float, viewW: Float, viewH: Float, baseFit: Float): Offset {
        if (!pan.x.isFinite() || !pan.y.isFinite()) return Offset.Zero
        if (zoomVal <= 1f) {
            val maxX = (viewW * 0.25f)
            val maxY = (viewH * 0.25f)
            return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
        }
        val drawnW = currentBg.width * baseFit * zoomVal
        val drawnH = currentBg.height * baseFit * zoomVal
        val maxX = drawnW / 2f + viewW * 0.25f
        val maxY = drawnH / 2f + viewH * 0.25f
        return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("overlay_interactive_canvas")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(8.dp)
                .onSizeChanged { canvasSizePx = it }
                .pointerInput(background.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val viewW = size.width.toFloat().coerceAtLeast(1f)
                        val viewH = size.height.toFloat().coerceAtLeast(1f)
                        val bgW = currentBg.width.coerceAtLeast(1).toFloat()
                        val bgH = currentBg.height.coerceAtLeast(1).toFloat()
                        val baseFit = min(viewW / bgW, viewH / bgH).coerceAtLeast(0.0001f)
                        val effectiveFit = baseFit * currentZoom
                        val canvasLeft = (viewW - bgW * effectiveFit) / 2f + currentPan.x
                        val canvasTop = (viewH - bgH * effectiveFit) / 2f + currentPan.y
                        val hitPaddingCanvas = with(density) { 14.dp.toPx() } / effectiveFit
                        val handleRadiusCanvas = with(density) { 20.dp.toPx() } / effectiveFit

                        val downCx = (down.position.x - canvasLeft) / effectiveFit
                        val downCy = (down.position.y - canvasTop) / effectiveFit

                        val startedInPanMode = currentPanMode
                        val currentSelected = currentLayers.firstOrNull {
                            it.id == currentSelectedId && it.isVisible
                        }
                        var activeHandle = if (!startedInPanMode && currentShowFrame && currentSelected != null) {
                            OverlayTransformMath.hitTestResizeHandle(
                                canvasX = downCx,
                                canvasY = downCy,
                                layer = currentSelected,
                                handleRadiusCanvas = handleRadiusCanvas
                            )
                        } else {
                            null
                        }

                        val hitOnDown = if (startedInPanMode) {
                            null
                        } else if (activeHandle != null) {
                            currentSelected
                        } else {
                            OverlayTransformMath.findTopmostLayerAt(
                                canvasX = downCx,
                                canvasY = downCy,
                                layers = currentLayers,
                                hitPaddingCanvas = hitPaddingCanvas
                            )
                        }
                        if (!startedInPanMode && hitOnDown != null && hitOnDown.id != currentSelectedId) {
                            onSelectLayer(hitOnDown.id)
                        }

                        var totalPan = Offset.Zero
                        var hasTransformed = false
                        var isViewportPinch = false
                        var maxPointers = 1

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount == 0) {
                                break
                            }
                            maxPointers = max(maxPointers, pressedCount)

                            val rawZoom = if (pressedCount >= 2) event.calculateZoom() else 1f
                            val zoomChange = if (rawZoom.isFinite() && rawZoom > 0f) rawZoom else 1f
                            val rawPan = event.calculatePan()
                            val panChange = if (rawPan.x.isFinite() && rawPan.y.isFinite()) rawPan else Offset.Zero
                            val centroid = event.calculateCentroid(useCurrent = false)

                            if (pressedCount >= 2 || startedInPanMode) {
                                activeHandle = null
                                if (pressedCount >= 2) {
                                    isViewportPinch = true
                                }
                                hasTransformed = true

                                val oldZoom = viewZoom
                                val newZoom = (oldZoom * zoomChange).coerceIn(0.75f, 10f)
                                val factor = if (oldZoom > 0f) newZoom / oldZoom else 1f
                                val viewportCenter = Offset(viewW / 2f, viewH / 2f)
                                val nextPan = if (centroid.isSpecified && centroid.x.isFinite() && centroid.y.isFinite()) {
                                    val centroidFromCenter = centroid - viewportCenter - viewPan
                                    viewPan + panChange - centroidFromCenter * (factor - 1f)
                                } else {
                                    viewPan + panChange
                                }

                                viewZoom = newZoom
                                viewPan = clampViewPan(nextPan, newZoom, viewW, viewH, baseFit)

                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!isViewportPinch) {
                                if (!hasTransformed) {
                                    totalPan += panChange
                                    if (totalPan.getDistance() > touchSlop) {
                                        hasTransformed = true
                                    }
                                }

                                if (hasTransformed) {
                                    val curFit = baseFit * viewZoom
                                    val handleToDrag = activeHandle
                                    if (handleToDrag != null) {
                                        if (panChange != Offset.Zero) {
                                            onResizeByHandle(
                                                handleToDrag,
                                                panChange.x / curFit,
                                                panChange.y / curFit
                                            )
                                        }
                                    } else if (hitOnDown != null) {
                                        if (panChange != Offset.Zero) {
                                            onTransformSelected(
                                                panChange.x / curFit,
                                                panChange.y / curFit,
                                                1f,
                                                0f
                                            )
                                        }
                                    } else if (panChange != Offset.Zero) {
                                        // Dragging on empty background pans the zoomed viewport
                                        viewPan = clampViewPan(
                                            viewPan + panChange,
                                            viewZoom,
                                            viewW,
                                            viewH,
                                            baseFit
                                        )
                                    }
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }

                        if (!startedInPanMode && !hasTransformed && maxPointers == 1 && !isViewportPinch && activeHandle == null) {
                            onSelectLayer(hitOnDown?.id)
                        }
                    }
                }
        ) {
            val viewW = size.width.coerceAtLeast(1f)
            val viewH = size.height.coerceAtLeast(1f)
            val bgW = background.width.coerceAtLeast(1).toFloat()
            val bgH = background.height.coerceAtLeast(1).toFloat()
            val fitScale = min(viewW / bgW, viewH / bgH) * viewZoom
            val drawnW = bgW * fitScale
            val drawnH = bgH * fitScale
            val canvasLeft = (viewW - drawnW) / 2f + viewPan.x
            val canvasTop = (viewH - drawnH) / 2f + viewPan.y

            val canvasRight = canvasLeft + drawnW
            val canvasBottom = canvasTop + drawnH

            // Overhanging parts of overlay layers outside background bounds (faint preview)
            for (layer in layers) {
                if (!layer.isVisible) continue
                val bmp = layerBitmaps[layer.id] ?: continue
                val centerX = canvasLeft + layer.offsetX * fitScale
                val centerY = canvasTop + layer.offsetY * fitScale
                val dstW = (layer.width * layer.effectiveScaleX * fitScale).roundToInt().coerceAtLeast(1)
                val dstH = (layer.height * layer.effectiveScaleY * fitScale).roundToInt().coerceAtLeast(1)

                withTransform({
                    translate(left = centerX, top = centerY)
                    rotate(degrees = layer.rotationDegrees, pivot = Offset.Zero)
                }) {
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset(-dstW / 2, -dstH / 2),
                        dstSize = IntSize(dstW, dstH),
                        alpha = (layer.opacity * 0.38f).coerceIn(0.05f, 1f),
                        filterQuality = if (layer.pixelCrisp) FilterQuality.None else FilterQuality.High
                    )
                }
            }

            // Checkerboard + base background + full-opacity overlay layers inside background bounds
            clipRect(
                left = canvasLeft,
                top = canvasTop,
                right = canvasRight,
                bottom = canvasBottom
            ) {
                val cellPx = 10.dp.toPx().coerceAtLeast(4f)
                val lightCell = Color(0xFF282633)
                val darkCell = Color(0xFF1C1A24)
                val visLeft = max(canvasLeft, 0f)
                val visTop = max(canvasTop, 0f)
                val visRight = min(canvasRight, viewW)
                val visBottom = min(canvasBottom, viewH)
                if (visRight > visLeft && visBottom > visTop) {
                    drawRect(
                        color = darkCell,
                        topLeft = Offset(visLeft, visTop),
                        size = Size(visRight - visLeft, visBottom - visTop)
                    )
                    val startCol = ((visLeft - canvasLeft) / cellPx).toInt().coerceAtLeast(0)
                    val endCol = ((visRight - canvasLeft) / cellPx).toInt() + 1
                    val startRow = ((visTop - canvasTop) / cellPx).toInt().coerceAtLeast(0)
                    val endRow = ((visBottom - canvasTop) / cellPx).toInt() + 1
                    for (r in startRow..endRow) {
                        for (c in startCol..endCol) {
                            if ((r + c) % 2 == 0) {
                                drawRect(
                                    color = lightCell,
                                    topLeft = Offset(canvasLeft + c * cellPx, canvasTop + r * cellPx),
                                    size = Size(cellPx, cellPx)
                                )
                            }
                        }
                    }
                }

                // Base background image
                val bgQuality = if (max(background.width, background.height) <= 256 || fitScale >= 2.5f) {
                    FilterQuality.None
                } else {
                    FilterQuality.High
                }
                drawImage(
                    image = bgImageBitmap,
                    dstOffset = IntOffset(canvasLeft.roundToInt(), canvasTop.roundToInt()),
                    dstSize = IntSize(drawnW.roundToInt().coerceAtLeast(1), drawnH.roundToInt().coerceAtLeast(1)),
                    filterQuality = bgQuality
                )

                // Visible overlay layers in bottom-to-top Z-order
                for (layer in layers) {
                    if (!layer.isVisible) continue
                    val bmp = layerBitmaps[layer.id] ?: continue
                    val centerX = canvasLeft + layer.offsetX * fitScale
                    val centerY = canvasTop + layer.offsetY * fitScale
                    val dstW = (layer.width * layer.effectiveScaleX * fitScale).roundToInt().coerceAtLeast(1)
                    val dstH = (layer.height * layer.effectiveScaleY * fitScale).roundToInt().coerceAtLeast(1)

                    withTransform({
                        translate(left = centerX, top = centerY)
                        rotate(degrees = layer.rotationDegrees, pivot = Offset.Zero)
                    }) {
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset(-dstW / 2, -dstH / 2),
                            dstSize = IntSize(dstW, dstH),
                            alpha = layer.opacity,
                            filterQuality = if (layer.pixelCrisp) FilterQuality.None else FilterQuality.High
                        )
                    }
                }
            }

            // Crisp background boundary outline + glowing edge alignment highlights (hidden when Рамка is off)
            if (showTransformFrame) {
                drawRect(
                    color = bgBorderColor,
                    topLeft = Offset(canvasLeft, canvasTop),
                    size = Size(drawnW, drawnH),
                    style = Stroke(width = 1.dp.toPx())
                )
                if (edgeContact.any) {
                    val glowW = 6.dp.toPx()
                    val coreW = 2.5.dp.toPx()
                    val ext = 12.dp.toPx()
                    fun drawHighlightedEdge(start: Offset, end: Offset) {
                        drawLine(
                            color = edgeHighlightColor.copy(alpha = 0.35f),
                            start = start,
                            end = end,
                            strokeWidth = glowW,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = edgeHighlightColor,
                            start = start,
                            end = end,
                            strokeWidth = coreW,
                            cap = StrokeCap.Round
                        )
                    }
                    if (edgeContact.left) {
                        drawHighlightedEdge(
                            Offset(canvasLeft, canvasTop - ext),
                            Offset(canvasLeft, canvasBottom + ext)
                        )
                    }
                    if (edgeContact.right) {
                        drawHighlightedEdge(
                            Offset(canvasRight, canvasTop - ext),
                            Offset(canvasRight, canvasBottom + ext)
                        )
                    }
                    if (edgeContact.top) {
                        drawHighlightedEdge(
                            Offset(canvasLeft - ext, canvasTop),
                            Offset(canvasRight + ext, canvasTop)
                        )
                    }
                    if (edgeContact.bottom) {
                        drawHighlightedEdge(
                            Offset(canvasLeft - ext, canvasBottom),
                            Offset(canvasRight + ext, canvasBottom)
                        )
                    }
                }
            }

            // Active layer selection frame & handles (drawn unclipped so handles stay visible near edges)
            if (showTransformFrame && selectedLayer != null && selectedLayer.isVisible) {
                val centerX = canvasLeft + selectedLayer.offsetX * fitScale
                val centerY = canvasTop + selectedLayer.offsetY * fitScale
                val boxW = (selectedLayer.width * selectedLayer.effectiveScaleX * fitScale).coerceAtLeast(12f)
                val boxH = (selectedLayer.height * selectedLayer.effectiveScaleY * fitScale).coerceAtLeast(12f)
                val halfW = boxW / 2f
                val halfH = boxH / 2f
                val strokePx = 1.75.dp.toPx()
                val handleSize = 8.dp.toPx()
                val pillLong = 16.dp.toPx()
                val pillShort = 5.dp.toPx()
                val stemLen = 18.dp.toPx()

                withTransform({
                    translate(left = centerX, top = centerY)
                    rotate(degrees = selectedLayer.rotationDegrees, pivot = Offset.Zero)
                }) {
                    drawRect(
                        color = primaryColor,
                        topLeft = Offset(-halfW, -halfH),
                        size = Size(boxW, boxH),
                        style = Stroke(width = strokePx)
                    )

                    // Top rotation indicator stem + knob
                    drawLine(
                        color = primaryColor,
                        start = Offset(0f, -halfH),
                        end = Offset(0f, -halfH - stemLen),
                        strokeWidth = strokePx
                    )
                    drawCircle(
                        color = handleFill,
                        radius = handleSize * 0.65f,
                        center = Offset(0f, -halfH - stemLen)
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = handleSize * 0.65f,
                        center = Offset(0f, -halfH - stemLen),
                        style = Stroke(width = strokePx)
                    )

                    // 4 independent Width / Height edge pill handles
                    val hEdgeOffsets = listOf(Offset(0f, -halfH), Offset(0f, halfH))
                    hEdgeOffsets.forEach { pt ->
                        val tl = Offset(pt.x - pillLong / 2f, pt.y - pillShort / 2f)
                        val sz = Size(pillLong, pillShort)
                        val cr = CornerRadius(pillShort / 2f, pillShort / 2f)
                        drawRoundRect(color = handleFill, topLeft = tl, size = sz, cornerRadius = cr)
                        drawRoundRect(color = primaryColor, topLeft = tl, size = sz, cornerRadius = cr, style = Stroke(width = strokePx))
                    }
                    val vEdgeOffsets = listOf(Offset(-halfW, 0f), Offset(halfW, 0f))
                    vEdgeOffsets.forEach { pt ->
                        val tl = Offset(pt.x - pillShort / 2f, pt.y - pillLong / 2f)
                        val sz = Size(pillShort, pillLong)
                        val cr = CornerRadius(pillShort / 2f, pillShort / 2f)
                        drawRoundRect(color = handleFill, topLeft = tl, size = sz, cornerRadius = cr)
                        drawRoundRect(color = primaryColor, topLeft = tl, size = sz, cornerRadius = cr, style = Stroke(width = strokePx))
                    }

                    // 4 corner handles (free 2D stretch)
                    val corners = listOf(
                        Offset(-halfW, -halfH),
                        Offset(halfW, -halfH),
                        Offset(-halfW, halfH),
                        Offset(halfW, halfH)
                    )
                    corners.forEach { pt ->
                        drawRect(
                            color = handleFill,
                            topLeft = Offset(pt.x - handleSize / 2f, pt.y - handleSize / 2f),
                            size = Size(handleSize, handleSize)
                        )
                        drawRect(
                            color = primaryColor,
                            topLeft = Offset(pt.x - handleSize / 2f, pt.y - handleSize / 2f),
                            size = Size(handleSize, handleSize),
                            style = Stroke(width = strokePx)
                        )
                    }
                }
            }
        }

        // Top-left toggle to show/hide the selection/transform frame around the active layer
        if (selectedLayer != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (showTransformFrame) 1.5.dp else 1.dp,
                        color = if (showTransformFrame) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onToggleTransformFrame)
                    .testTag("overlay_frame_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CropFree,
                        contentDescription = if (showTransformFrame) {
                            "Скрыть рамку выделения"
                        } else {
                            "Показать рамку выделения"
                        },
                        tint = if (showTransformFrame) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Рамка",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showTransformFrame) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }

        // Floating Viewport Zoom & Camera Pan controls (top-right)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Camera / Pan mode lock toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = if (isCameraPanMode) 1.5.dp else 1.dp,
                        color = if (isCameraPanMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { isCameraPanMode = !isCameraPanMode }
                    .testTag("overlay_pan_mode_toggle")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PanTool,
                        contentDescription = "Режим обзора и перемещения холста",
                        tint = if (isCameraPanMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Обзор",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCameraPanMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // Zoom - / % (reset) / +
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                modifier = Modifier.border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                ) {
                    IconButton(
                        onClick = {
                            val next = (viewZoom / 1.35f).coerceIn(0.75f, 10f)
                            viewZoom = next
                            if (next <= 1.01f && abs(next - 1f) < 0.08f) {
                                viewZoom = 1f
                                viewPan = Offset.Zero
                            }
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("overlay_zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomOut,
                            contentDescription = "Отдалить",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Text(
                        text = "${(viewZoom * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (abs(viewZoom - 1f) > 0.02f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                viewZoom = 1f
                                viewPan = Offset.Zero
                            }
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .testTag("overlay_zoom_reset_button")
                    )

                    IconButton(
                        onClick = {
                            viewZoom = (viewZoom * 1.35f).coerceIn(0.75f, 10f)
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("overlay_zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ZoomIn,
                            contentDescription = "Приблизить",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // Subtle live transform HUD pill
        val hudText = if (selectedLayer != null) {
            val sx = (selectedLayer.scaleX * 100).roundToInt()
            val sy = (selectedLayer.scaleY * 100).roundToInt()
            val sizeSummary = if (sx == sy) "$sx%" else "Ш:$sx% × В:$sy%"
            "${selectedLayer.name} • $sizeSummary • ${selectedLayer.rotationDegrees.roundToInt()}°"
        } else if (layers.isEmpty()) {
            "Нажмите «+ Слой», чтобы добавить картинку поверх"
        } else {
            "Нажмите на слой и тяните за грани для растягивания"
        }

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp, start = 10.dp, end = 10.dp)
        ) {
            Text(
                text = hudText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun OverlayLayersStripCard(
    layers: List<OverlayLayer>,
    selectedLayerId: String?,
    onSelectLayer: (String?) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onAddLayer: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Слои поверх (${layers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(
                    onClick = onAddLayer,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("overlay_strip_add_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Добавить")
                }
            }

            if (layers.isEmpty()) {
                Text(
                    text = "Добавьте логотип, текстуру или стикер, чтобы двигать, крутить и масштабировать его поверх фото.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Show topmost layer first
                val displayList = remember(layers) { layers.asReversed() }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(displayList, key = { it.id }) { layer ->
                        val isSelected = layer.id == selectedLayerId
                        val thumb = remember(layer.id, layer.bitmap) {
                            layer.bitmap.asImageBitmap()
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { onSelectLayer(layer.id) }
                                .testTag("overlay_layer_chip_${layer.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 6.dp, top = 5.dp, bottom = 5.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                            RoundedCornerShape(8.dp)
                                        )
                                ) {
                                    CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 4f)
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val s = min(size.width / thumb.width, size.height / thumb.height)
                                        val dw = (thumb.width * s).roundToInt().coerceAtLeast(1)
                                        val dh = (thumb.height * s).roundToInt().coerceAtLeast(1)
                                        drawImage(
                                            image = thumb,
                                            dstOffset = IntOffset(
                                                ((size.width - dw) / 2f).roundToInt(),
                                                ((size.height - dh) / 2f).roundToInt()
                                            ),
                                            dstSize = IntSize(dw, dh),
                                            alpha = if (layer.isVisible) 1f else 0.3f,
                                            filterQuality = if (layer.pixelCrisp) FilterQuality.None else FilterQuality.Medium
                                        )
                                    }
                                }

                                Column(modifier = Modifier.width(74.dp)) {
                                    Text(
                                        text = layer.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val sx = (layer.scaleX * 100).roundToInt()
                                    val sy = (layer.scaleY * 100).roundToInt()
                                    val sizeLabel = if (sx == sy) "$sx%" else "$sx×$sy%"
                                    Text(
                                        text = "$sizeLabel • ${layer.rotationDegrees.roundToInt()}°",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }

                                IconButton(
                                    onClick = { onToggleVisibility(layer.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = if (layer.isVisible) {
                                            Icons.Rounded.Visibility
                                        } else {
                                            Icons.Rounded.VisibilityOff
                                        },
                                        contentDescription = if (layer.isVisible) "Скрыть слой" else "Показать слой",
                                        tint = if (layer.isVisible) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySelectedLayerControls(
    uiState: OverlayUiState,
    viewModel: OverlayViewModel
) {
    val selected = uiState.selectedLayer ?: return
    val idx = uiState.layers.indexOfFirst { it.id == selected.id }
    val canMoveUp = idx in 0 until uiState.layers.lastIndex
    val canMoveDown = idx > 0

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Order, duplicate, crisp pixel toggle, center & delete row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmallOutlinedActionButton(
                    icon = Icons.Rounded.ArrowUpward,
                    label = "Выше",
                    enabled = canMoveUp,
                    onClick = { viewModel.moveLayerUp(selected.id) },
                    testTag = "overlay_layer_up_button"
                )
                SmallOutlinedActionButton(
                    icon = Icons.Rounded.ArrowDownward,
                    label = "Ниже",
                    enabled = canMoveDown,
                    onClick = { viewModel.moveLayerDown(selected.id) },
                    testTag = "overlay_layer_down_button"
                )
                SmallOutlinedActionButton(
                    icon = Icons.Rounded.CenterFocusStrong,
                    label = "Центр",
                    enabled = true,
                    onClick = viewModel::centerSelectedLayer,
                    testTag = "overlay_layer_center_button"
                )
                SmallOutlinedActionButton(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Копия",
                    enabled = true,
                    onClick = { viewModel.duplicateLayer(selected.id) },
                    testTag = "overlay_layer_duplicate_button"
                )
                FilterChip(
                    selected = selected.pixelCrisp,
                    onClick = viewModel::toggleSelectedLayerPixelCrisp,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.GridOn,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = { Text("Пиксели", style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = Color.Transparent,
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected.pixelCrisp,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                        selectedBorderWidth = 1.5.dp
                    ),
                    modifier = Modifier.testTag("overlay_layer_pixel_crisp")
                )
                SmallOutlinedActionButton(
                    icon = Icons.Rounded.RestartAlt,
                    label = "Сброс",
                    enabled = true,
                    onClick = viewModel::resetSelectedLayerTransform,
                    testTag = "overlay_layer_reset_button"
                )
                IconButton(
                    onClick = { viewModel.removeLayer(selected.id) },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("overlay_layer_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Удалить слой",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Independent Width (scaleX) slider + -/+ buttons
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ширина",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(selected.scaleX * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.adjustSelectedLayerScaleX(-0.1f) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("overlay_scale_x_minus")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Уменьшить ширину",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = selected.scaleX.coerceIn(
                            OverlayTransformMath.MIN_LAYER_SCALE,
                            OverlayTransformMath.MAX_LAYER_SCALE
                        ),
                        onValueChange = viewModel::setSelectedLayerScaleX,
                        valueRange = OverlayTransformMath.MIN_LAYER_SCALE..OverlayTransformMath.MAX_LAYER_SCALE,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("overlay_scale_x_slider")
                    )
                    IconButton(
                        onClick = { viewModel.adjustSelectedLayerScaleX(0.1f) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("overlay_scale_x_plus")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Увеличить ширину",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Independent Height / Length (scaleY) slider + -/+ buttons
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Длина (высота)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(selected.scaleY * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.adjustSelectedLayerScaleY(-0.1f) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("overlay_scale_y_minus")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Уменьшить высоту",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = selected.scaleY.coerceIn(
                            OverlayTransformMath.MIN_LAYER_SCALE,
                            OverlayTransformMath.MAX_LAYER_SCALE
                        ),
                        onValueChange = viewModel::setSelectedLayerScaleY,
                        valueRange = OverlayTransformMath.MIN_LAYER_SCALE..OverlayTransformMath.MAX_LAYER_SCALE,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("overlay_scale_y_slider")
                    )
                    IconButton(
                        onClick = { viewModel.adjustSelectedLayerScaleY(0.1f) },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("overlay_scale_y_plus")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Увеличить высоту",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Rotation slider + quick angle step buttons
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Поворот",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${selected.rotationDegrees.roundToInt()}°",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = selected.rotationDegrees,
                    onValueChange = viewModel::setSelectedLayerRotation,
                    valueRange = -180f..180f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("overlay_rotation_slider")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AngleStepButton("-45°") { viewModel.rotateSelectedLayerBy(-45f) }
                    AngleStepButton("-15°") { viewModel.rotateSelectedLayerBy(-15f) }
                    AngleStepButton("0°") { viewModel.setSelectedLayerRotation(0f) }
                    AngleStepButton("+15°") { viewModel.rotateSelectedLayerBy(15f) }
                    AngleStepButton("+45°") { viewModel.rotateSelectedLayerBy(45f) }
                }
            }

            // Opacity slider
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Прозрачность",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(selected.opacity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = selected.opacity,
                    onValueChange = viewModel::setSelectedLayerOpacity,
                    valueRange = 0.05f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("overlay_opacity_slider")
                )
            }
        }
    }
}

@Composable
private fun SmallOutlinedActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier
            .heightIn(min = 36.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AngleStepButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.heightIn(min = 34.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun OverlaySourceChooserDialog(
    title: String,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
    onPickFromEditor: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPickGallery,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Из галереи")
                }
                OutlinedButton(
                    onClick = onPickFiles,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Файлы с устройства")
                }
                OutlinedButton(
                    onClick = onPickFromEditor,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.Brush, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Холст из редактора")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun OverlayExportDialog(
    background: OverlayBackground,
    layerCount: Int,
    preserveAlpha: Boolean,
    isExporting: Boolean,
    onPreserveAlphaChange: (Boolean) -> Unit,
    onSaveGallery: () -> Unit,
    onSaveToFolder: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(text = "Экспорт композиции PNG", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Разрешение: ${background.width} × ${background.height} px",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Видимых слоёв поверх: $layerCount • PNG без сжатия с потерями",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isExporting) { onPreserveAlphaChange(!preserveAlpha) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = "Сохранять альфа-канал",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (preserveAlpha) {
                                    "RGBA — прозрачность сохраняется"
                                } else {
                                    "RGB — непрозрачный формат"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preserveAlpha,
                            onCheckedChange = onPreserveAlphaChange,
                            enabled = !isExporting,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("overlay_export_alpha_switch")
                        )
                    }
                }

                if (isExporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSaveGallery,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("overlay_export_gallery_confirm")
                        ) {
                            Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сохранить в галерею")
                        }
                        OutlinedButton(
                            onClick = onSaveToFolder,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("overlay_export_folder_confirm")
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сохранить в папку")
                        }
                        OutlinedButton(
                            onClick = onShare,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("overlay_export_share_confirm")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Поделиться PNG")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isExporting) {
                Text("Закрыть")
            }
        }
    )
}
