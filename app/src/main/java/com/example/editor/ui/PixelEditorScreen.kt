package com.example.editor.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.IncomingImageRequest
import com.example.R
import com.example.editor.model.EditorTool
import com.example.editor.ui.components.ColorPaletteStrip
import com.example.editor.ui.components.ColorPickerSheet
import com.example.editor.ui.components.EditorBottomNavigationBar
import com.example.editor.ui.components.EditorToolOptionsPill
import com.example.editor.ui.components.EditorTopBar
import com.example.editor.ui.components.ExportPngDialog
import com.example.editor.ui.components.LayersPanelContent
import com.example.editor.ui.components.LayersSheet
import com.example.editor.ui.components.NewCanvasDialog
import com.example.editor.ui.components.PixelCanvasView
import com.example.editor.ui.components.RecolorSelectionDialog
import com.example.compress.ui.CompressScreen
import com.example.compress.viewmodel.CompressViewModel
import com.example.editor.viewmodel.PixelEditorViewModel
import com.example.overlay.ui.OverlayScreen
import com.example.overlay.ui.ProjectsTabScreen
import com.example.overlay.viewmodel.OverlayViewModel
import com.example.resizer.model.AppTab
import com.example.resizer.ui.ResizerScreen
import com.example.resizer.viewmodel.ResizerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LilacStudioScreen(
    incomingImageRequest: IncomingImageRequest? = null,
    onIncomingImageHandled: () -> Unit = {},
    editorViewModel: PixelEditorViewModel = viewModel(),
    resizerViewModel: ResizerViewModel = viewModel(),
    overlayViewModel: OverlayViewModel = viewModel(),
    compressViewModel: CompressViewModel = viewModel()
) {
    val appContext = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var activeTab by rememberSaveable { mutableStateOf(AppTab.EDITOR) }
    var previousTab by rememberSaveable { mutableStateOf(activeTab) }
    var editorZoomResetSignal by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        editorViewModel.initializeProjects(appContext)
        resizerViewModel.initializeProjects(appContext)
        overlayViewModel.initializeProjects(appContext)
    }

    LaunchedEffect(activeTab) {
        if (previousTab != activeTab) {
            when (previousTab) {
                AppTab.EDITOR -> editorViewModel.flushAutoSaveNow(appContext)
                AppTab.RESIZER -> resizerViewModel.flushAutoSaveNow(appContext)
                AppTab.OVERLAY -> overlayViewModel.flushAutoSaveNow(appContext)
                else -> {}
            }
            previousTab = activeTab
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                editorViewModel.flushAutoSaveNow(appContext)
                resizerViewModel.flushAutoSaveNow(appContext)
                overlayViewModel.flushAutoSaveNow(appContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(incomingImageRequest?.requestId) {
        if (incomingImageRequest != null) {
            activeTab = AppTab.EDITOR
        }
    }

    BackHandler(enabled = activeTab != AppTab.EDITOR) {
        activeTab = AppTab.EDITOR
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            PrimaryScrollableTabRow(
                selectedTabIndex = activeTab.ordinal,
                edgePadding = 8.dp,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(activeTab.ordinal, matchContentSize = true),
                        width = 64.dp,
                        height = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                AppTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    Tab(
                        selected = selected,
                        onClick = { activeTab = tab },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("tab_${tab.name.lowercase()}"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (tab) {
                                        AppTab.EDITOR -> Icons.Rounded.Brush
                                        AppTab.RESIZER -> Icons.Rounded.PhotoSizeSelectLarge
                                        AppTab.OVERLAY -> Icons.Rounded.Layers
                                        AppTab.COMPRESS -> Icons.Rounded.Compress
                                        AppTab.PROJECTS -> Icons.Rounded.CollectionsBookmark
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab) {
                AppTab.EDITOR -> {
                    PixelEditorScreen(
                        incomingImageRequest = incomingImageRequest,
                        onIncomingImageHandled = onIncomingImageHandled,
                        viewModel = editorViewModel,
                        applyStatusBarPadding = false,
                        externalZoomResetSignal = editorZoomResetSignal
                    )
                }

                AppTab.RESIZER -> {
                    ResizerScreen(
                        viewModel = resizerViewModel,
                        onImportFromEditor = {
                            val (w, h, pixels) = editorViewModel.exportCurrentCanvasForResizer()
                            resizerViewModel.addFromEditorCanvas(
                                name = "canvas_${w}x${h}",
                                width = w,
                                height = h,
                                pixels = pixels
                            )
                        },
                        onOpenResultInEditor = { name, width, height, pixels ->
                            if (editorViewModel.loadImagePixelsIntoCanvas(name, width, height, pixels)) {
                                editorZoomResetSignal++
                                activeTab = AppTab.EDITOR
                            }
                        }
                    )
                }

                AppTab.OVERLAY -> {
                    OverlayScreen(
                        viewModel = overlayViewModel,
                        onGetEditorCanvas = { editorViewModel.exportCurrentCanvasForResizer() }
                    )
                }

                AppTab.COMPRESS -> {
                    CompressScreen(
                        viewModel = compressViewModel,
                        onImportFromEditor = {
                            val (w, h, pixels) = editorViewModel.exportCurrentCanvasForResizer()
                            compressViewModel.addFromEditorCanvas(
                                name = "canvas_${w}x${h}",
                                width = w,
                                height = h,
                                pixels = pixels
                            )
                        }
                    )
                }

                AppTab.PROJECTS -> {
                    ProjectsTabScreen(
                        editorViewModel = editorViewModel,
                        resizerViewModel = resizerViewModel,
                        overlayViewModel = overlayViewModel,
                        onNavigateToTab = { targetTab ->
                            if (targetTab == AppTab.EDITOR) {
                                editorZoomResetSignal++
                            }
                            activeTab = targetTab
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PixelEditorScreen(
    incomingImageRequest: IncomingImageRequest? = null,
    onIncomingImageHandled: () -> Unit = {},
    viewModel: PixelEditorViewModel = viewModel(),
    applyStatusBarPadding: Boolean = true,
    externalZoomResetSignal: Int = 0
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLayersSheet by remember { mutableStateOf(false) }
    var showColorPickerSheet by remember { mutableStateOf(false) }
    var showNewCanvasDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showRecolorDialog by remember { mutableStateOf(false) }
    var zoomResetToken by remember { mutableIntStateOf(0) }

    fun openUriIntoEditor(uri: android.net.Uri) {
        viewModel.importPhotoFromUri(context, uri) { success ->
            if (success) {
                zoomResetToken++
            }
            scope.launch {
                snackbarHostState.showSnackbar(
                    if (success) "Изображение открыто в редакторе" else "Не удалось открыть изображение"
                )
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            openUriIntoEditor(uri)
        }
    }

    val fileManagerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            openUriIntoEditor(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadPersistedColors(context)
    }

    LaunchedEffect(externalZoomResetSignal) {
        if (externalZoomResetSignal > 0) {
            zoomResetToken++
        }
    }

    LaunchedEffect(incomingImageRequest?.requestId) {
        val request = incomingImageRequest ?: return@LaunchedEffect
        openUriIntoEditor(request.uri)
        onIncomingImageHandled()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedLayout = maxWidth >= 840.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                EditorTopBar(
                    canvasWidth = uiState.canvasWidth,
                    canvasHeight = uiState.canvasHeight,
                    zoomScale = uiState.zoomScale,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onNewProjectClick = { showNewCanvasDialog = true },
                    onResetZoomClick = { zoomResetToken++ },
                    onUndoClick = viewModel::undo,
                    onRedoClick = viewModel::redo,
                    onExportClick = { showExportDialog = true },
                    applyStatusBarPadding = applyStatusBarPadding
                )
            },
            bottomBar = {
                Column {
                    EditorToolOptionsPill(
                        activeTool = uiState.activeTool,
                        selectionMode = uiState.selectionMode,
                        hasSelection = uiState.selection != null,
                        hasClipboard = uiState.hasClipboard,
                        selectedColorArgb = uiState.selectedColorArgb,
                        brushSize = uiState.brushSize,
                        fillTolerance = uiState.fillTolerance,
                        onBrushSizeChange = viewModel::setBrushSize,
                        onFillToleranceChange = viewModel::setFillTolerance,
                        onSelectionModeChange = viewModel::setSelectionMode,
                        onSelectAll = viewModel::selectAll,
                        onClearSelection = viewModel::clearSelection,
                        onInvertSelection = viewModel::invertSelection,
                        onDeleteSelected = viewModel::deleteSelectedPixels,
                        onCopySelection = {
                            if (viewModel.copySelection()) {
                                scope.launch { snackbarHostState.showSnackbar("Выделение скопировано") }
                            }
                        },
                        onCutSelection = {
                            if (viewModel.cutSelection()) {
                                scope.launch { snackbarHostState.showSnackbar("Выделение вырезано") }
                            }
                        },
                        onPasteClipboard = {
                            if (viewModel.pasteClipboard()) {
                                scope.launch { snackbarHostState.showSnackbar("Вставлено — перетащите для сдвига") }
                            }
                        },
                        onExtractToNewLayer = {
                            val ok = viewModel.extractSelectionToNewLayer()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (ok) "Скопировано в новый слой" else "В выделении нет непрозрачных пикселей"
                                )
                            }
                        },
                        onRecolorSelected = { showRecolorDialog = true },
                        onOpenColorPicker = { showColorPickerSheet = true }
                    )
                    if (uiState.activeTool != EditorTool.HAND && uiState.activeTool != EditorTool.SELECT) {
                        ColorPaletteStrip(
                            selectedColorArgb = uiState.selectedColorArgb,
                            recentColors = uiState.recentColors,
                            savedColors = uiState.savedColors,
                            onSelectColor = viewModel::selectPaletteColor,
                            onToggleSaveCurrentColor = {
                                val added = viewModel.toggleSaveColor()
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (added) "Цвет сохранён в палитру" else "Цвет убран из сохранённых"
                                    )
                                }
                            },
                            onPinRecentColor = { argb ->
                                val added = viewModel.saveColorToPalette(argb)
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (added) "Цвет закреплён в палитре" else "Цвет уже в палитре"
                                    )
                                }
                            },
                            onRemoveSavedColor = { argb ->
                                viewModel.removeSavedColor(argb)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Цвет удалён из палитры")
                                }
                            }
                        )
                    }
                    EditorBottomNavigationBar(
                        activeTool = uiState.activeTool,
                        layerCount = uiState.layers.size,
                        showLayersButton = !isExpandedLayout,
                        onSelectTool = viewModel::selectTool,
                        onOpenLayers = { showLayersSheet = true }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                PixelCanvasView(
                    canvasWidth = uiState.canvasWidth,
                    canvasHeight = uiState.canvasHeight,
                    layers = uiState.layers,
                    activeLayerName = uiState.activeLayer?.name ?: "Слой",
                    activeTool = uiState.activeTool,
                    showPixelGrid = uiState.showPixelGrid,
                    hoveredPixel = uiState.hoveredPixel,
                    eyedropperPreview = uiState.eyedropperPreview,
                    selection = uiState.selection,
                    selectionPreviewRect = uiState.selectionPreviewRect,
                    lassoPreviewPoints = uiState.lassoPreviewPoints,
                    zoomResetToken = zoomResetToken,
                    onToggleGrid = viewModel::togglePixelGrid,
                    onClearSelection = viewModel::clearSelection,
                    onZoomChanged = viewModel::updateZoomScale,
                    onBeginInteraction = viewModel::beginCanvasInteraction,
                    onContinueInteraction = viewModel::continueCanvasInteraction,
                    onEndInteraction = viewModel::endCanvasInteraction,
                    onCancelStrokeForGesture = viewModel::cancelActiveStrokeForGesture,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                // Expanded tablet/foldable persistent Layers side pane
                if (isExpandedLayout) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(topStart = 24.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .width(340.dp)
                            .fillMaxHeight()
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            LayersPanelContent(
                                layers = uiState.layers,
                                activeLayerId = uiState.activeLayerId,
                                onSelectLayer = viewModel::selectLayer,
                                onAddLayer = viewModel::addLayer,
                                onDuplicateLayer = viewModel::duplicateLayer,
                                onDeleteLayer = viewModel::deleteLayer,
                                onToggleVisibility = viewModel::toggleLayerVisibility,
                                onOpacityChange = viewModel::setLayerOpacity,
                                onCaptureOpacitySnapshot = viewModel::snapshotCurrent,
                                onCommitOpacitySnapshot = viewModel::commitLayerOpacitySnapshot,
                                onMoveUp = viewModel::moveLayerUp,
                                onMoveDown = viewModel::moveLayerDown,
                                onMergeDown = viewModel::mergeLayerDown,
                                onRenameLayer = viewModel::renameLayer,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showLayersSheet) {
        LayersSheet(
            layers = uiState.layers,
            activeLayerId = uiState.activeLayerId,
            onSelectLayer = viewModel::selectLayer,
            onAddLayer = viewModel::addLayer,
            onDuplicateLayer = viewModel::duplicateLayer,
            onDeleteLayer = viewModel::deleteLayer,
            onToggleVisibility = viewModel::toggleLayerVisibility,
            onOpacityChange = viewModel::setLayerOpacity,
            onCaptureOpacitySnapshot = viewModel::snapshotCurrent,
            onCommitOpacitySnapshot = viewModel::commitLayerOpacitySnapshot,
            onMoveUp = viewModel::moveLayerUp,
            onMoveDown = viewModel::moveLayerDown,
            onMergeDown = viewModel::mergeLayerDown,
            onRenameLayer = viewModel::renameLayer,
            onDismiss = { showLayersSheet = false }
        )
    }

    if (showColorPickerSheet) {
        ColorPickerSheet(
            currentColorArgb = uiState.selectedColorArgb,
            savedColors = uiState.savedColors,
            recentColors = uiState.recentColors,
            onColorPreview = viewModel::previewColor,
            onColorCommit = viewModel::selectColor,
            onSelectPaletteColor = viewModel::selectPaletteColor,
            onToggleSaveColor = { argb -> viewModel.toggleSaveColor(argb) },
            onRemoveSavedColor = viewModel::removeSavedColor,
            onClearRecentColors = viewModel::clearRecentColors,
            onDismiss = { showColorPickerSheet = false }
        )
    }

    if (showRecolorDialog && uiState.selection != null && uiState.activeLayer != null) {
        RecolorSelectionDialog(
            activeLayer = uiState.activeLayer!!,
            selection = uiState.selection!!,
            canvasWidth = uiState.canvasWidth,
            canvasHeight = uiState.canvasHeight,
            initialColorArgb = uiState.selectedColorArgb,
            savedColors = uiState.savedColors,
            recentColors = uiState.recentColors,
            onApply = { targetColor, mode, strength, brightnessShift, recolorWhite ->
                val ok = viewModel.recolorSelectedPixels(targetColor, mode, strength, brightnessShift, recolorWhite)
                showRecolorDialog = false
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (ok) "Выделение перекрашено с сохранением светотени"
                        else "В выделении нет пикселей для перекраски"
                    )
                }
            },
            onDismiss = { showRecolorDialog = false }
        )
    }

    if (showNewCanvasDialog) {
        NewCanvasDialog(
            onSelectPreset = { preset ->
                viewModel.createNewCanvas(preset)
                zoomResetToken++
            },
            onImportPhotoClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onOpenFileClick = {
                fileManagerLauncher.launch(arrayOf("image/*"))
            },
            onDismiss = { showNewCanvasDialog = false }
        )
    }

    if (showExportDialog) {
        ExportPngDialog(
            layers = uiState.layers,
            canvasWidth = uiState.canvasWidth,
            canvasHeight = uiState.canvasHeight,
            isExporting = uiState.isExporting,
            onSaveToGallery = { preserveAlpha ->
                viewModel.exportToGallery(context, preserveAlpha) { result ->
                    showExportDialog = false
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (result.isSuccess) {
                                if (preserveAlpha) {
                                    context.getString(R.string.export_success)
                                } else {
                                    "PNG (RGB без альфы) сохранён в галерею"
                                }
                            } else {
                                "Ошибка экспорта: ${result.exceptionOrNull()?.localizedMessage ?: "неизвестная"}"
                            }
                        )
                    }
                }
            },
            onSharePng = { preserveAlpha ->
                viewModel.exportForSharing(context, preserveAlpha) { result ->
                    showExportDialog = false
                    result.onSuccess { uri ->
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Поделиться PNG"))
                    }.onFailure { err ->
                        scope.launch {
                            snackbarHostState.showSnackbar("Ошибка экспорта: ${err.localizedMessage ?: "неизвестная"}")
                        }
                    }
                }
            },
            onDismiss = { showExportDialog = false }
        )
    }
}
