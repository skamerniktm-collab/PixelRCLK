package com.example.resizer.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.editor.ui.components.CheckerboardBox
import com.example.resizer.model.RESIZER_PRESET_SIZES
import com.example.resizer.model.ResampleMode
import com.example.resizer.model.ResizePreset
import com.example.resizer.model.ResizerInputImage
import com.example.resizer.model.ResizerPreviewResult
import com.example.resizer.model.ResizerUiState
import com.example.resizer.model.formatExactFileSize
import com.example.resizer.viewmodel.ResizerViewModel
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ResizerScreen(
    viewModel: ResizerViewModel,
    onImportFromEditor: () -> Unit,
    onOpenResultInEditor: (name: String, width: Int, height: Int, pixels: IntArray) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeInput = uiState.activeInput
    val previewResult by produceState<ResizerPreviewResult?>(
        initialValue = null,
        key1 = activeInput?.id,
        key2 = uiState.previewPreset,
        key3 = Triple(uiState.resampleMode, uiState.preserveAlpha, activeInput?.pixels)
    ) {
        value = if (activeInput == null) {
            null
        } else {
            viewModel.renderPreviewResult(
                input = activeInput,
                preset = uiState.previewPreset,
                mode = uiState.resampleMode,
                preserveAlpha = uiState.preserveAlpha
            )
        }
    }

    val multiGalleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFromUris(context, uris) { added ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (added > 0) "Добавлено изображений: $added"
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
                        if (added > 0) "Загружено PNG/изображений: $added"
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
                    result.onSuccess { count ->
                        snackbarHostState.showSnackbar("Сохранено в выбранную папку: $count PNG")
                    }.onFailure { err ->
                        snackbarHostState.showSnackbar(
                            "Ошибка сохранения: ${err.localizedMessage ?: "неизвестная"}"
                        )
                    }
                }
            }
        }
    }

    val onSaveGallery: () -> Unit = {
        viewModel.exportToGallery(context) { result ->
            scope.launch {
                result.onSuccess { count ->
                    snackbarHostState.showSnackbar("Сохранено в галерею (Pictures/LilacPixel/Resized): $count PNG")
                }.onFailure { err ->
                    snackbarHostState.showSnackbar(
                        "Ошибка экспорта: ${err.localizedMessage ?: "неизвестная"}"
                    )
                }
            }
        }
    }

    val onShareBatch: () -> Unit = {
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
                        "Ошибка подготовки файлов: ${err.localizedMessage ?: "неизвестная"}"
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
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SourcesSection(
                            inputs = uiState.inputs,
                            activeInputId = activeInput?.id,
                            onImportFromEditor = onImportFromEditor,
                            onPickGallery = {
                                multiGalleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickFiles = { multiDocumentPicker.launch(arrayOf("image/*")) },
                            onSelectInput = viewModel::selectPreviewInput,
                            onRemoveInput = viewModel::removeInput,
                            onClearInputs = viewModel::clearInputs
                        )

                        PreviewSection(
                            activeInput = activeInput,
                            previewPreset = uiState.previewPreset,
                            previewResult = previewResult,
                            resampleMode = uiState.resampleMode,
                            onOpenResultInEditor = {
                                val result = previewResult ?: return@PreviewSection
                                val input = activeInput ?: return@PreviewSection
                                onOpenResultInEditor(
                                    "${input.baseName}_${uiState.previewPreset.width}x${uiState.previewPreset.height}",
                                    uiState.previewPreset.width,
                                    uiState.previewPreset.height,
                                    result.pixels
                                )
                            }
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        ResampleModeSection(
                            selectedMode = uiState.resampleMode,
                            onSelectMode = viewModel::setResampleMode
                        )

                        PresetsSection(
                            uiState = uiState,
                            onTogglePreset = viewModel::togglePreset
                        )

                        ExportSection(
                            uiState = uiState,
                            onPreserveAlphaChange = viewModel::setPreserveAlpha,
                            onSaveGallery = onSaveGallery,
                            onSaveToFolder = { folderPicker.launch(null) },
                            onShareBatch = onShareBatch
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SourcesSection(
                        inputs = uiState.inputs,
                        activeInputId = activeInput?.id,
                        onImportFromEditor = onImportFromEditor,
                        onPickGallery = {
                            multiGalleryPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onPickFiles = { multiDocumentPicker.launch(arrayOf("image/*")) },
                        onSelectInput = viewModel::selectPreviewInput,
                        onRemoveInput = viewModel::removeInput,
                        onClearInputs = viewModel::clearInputs
                    )

                    PreviewSection(
                        activeInput = activeInput,
                        previewPreset = uiState.previewPreset,
                        previewResult = previewResult,
                        resampleMode = uiState.resampleMode,
                        onOpenResultInEditor = {
                            val result = previewResult ?: return@PreviewSection
                            val input = activeInput ?: return@PreviewSection
                            onOpenResultInEditor(
                                "${input.baseName}_${uiState.previewPreset.width}x${uiState.previewPreset.height}",
                                uiState.previewPreset.width,
                                uiState.previewPreset.height,
                                result.pixels
                            )
                        }
                    )

                    ResampleModeSection(
                        selectedMode = uiState.resampleMode,
                        onSelectMode = viewModel::setResampleMode
                    )

                    PresetsSection(
                        uiState = uiState,
                        onTogglePreset = viewModel::togglePreset
                    )

                    ExportSection(
                        uiState = uiState,
                        onPreserveAlphaChange = viewModel::setPreserveAlpha,
                        onSaveGallery = onSaveGallery,
                        onSaveToFolder = { folderPicker.launch(null) },
                        onShareBatch = onShareBatch
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun SourcesSection(
    inputs: List<ResizerInputImage>,
    activeInputId: String?,
    onImportFromEditor: () -> Unit,
    onPickGallery: () -> Unit,
    onPickFiles: () -> Unit,
    onSelectInput: (String) -> Unit,
    onRemoveInput: (String) -> Unit,
    onClearInputs: () -> Unit
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoSizeSelectLarge,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (inputs.isEmpty()) "Исходные PNG" else "Исходные PNG (${inputs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (inputs.isNotEmpty()) {
                    TextButton(
                        onClick = onClearInputs,
                        modifier = Modifier.testTag("resizer_clear_inputs_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Очистить список",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Очистить")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onImportFromEditor,
                    modifier = Modifier.testTag("resizer_import_editor_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Brush,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Из редактора")
                }

                OutlinedButton(
                    onClick = onPickGallery,
                    modifier = Modifier.testTag("resizer_pick_gallery_button")
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
                    modifier = Modifier.testTag("resizer_pick_files_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Файлы PNG")
                }
            }

            if (inputs.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(inputs, key = { it.id }) { item ->
                        val selected = item.id == activeInputId
                        InputThumbnailChip(
                            item = item,
                            selected = selected,
                            onSelect = { onSelectInput(item.id) },
                            onRemove = { onRemoveInput(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputThumbnailChip(
    item: ResizerInputImage,
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
            .testTag("resizer_input_item_${item.baseName}")
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
                PixelPreviewCanvas(
                    pixels = item.pixels,
                    width = item.width,
                    height = item.height,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.width(96.dp)) {
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
                    text = "${item.width}×${item.height} • ${if (item.hasAlpha) "RGBA" else "RGB"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("resizer_remove_input_${item.baseName}")
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

@Composable
private fun PreviewSection(
    activeInput: ResizerInputImage?,
    previewPreset: ResizePreset,
    previewResult: ResizerPreviewResult?,
    resampleMode: ResampleMode,
    onOpenResultInEditor: () -> Unit
) {
    if (activeInput == null) return

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
                Text(
                    text = "Предпросмотр (${resampleMode.title})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${activeInput.width}×${activeInput.height} → ${previewPreset.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val origSizeText = (previewResult?.originalByteSize ?: activeInput.sourceByteSize)
                    ?.let { formatExactFileSize(it) }
                PreviewTile(
                    title = "Оригинал • ${activeInput.width}×${activeInput.height}",
                    subtitle = origSizeText,
                    pixels = activeInput.pixels,
                    width = activeInput.width,
                    height = activeInput.height,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                PreviewTile(
                    title = "Итог • ${previewPreset.label}",
                    subtitle = previewResult?.let { formatExactFileSize(it.outputByteSize) },
                    highlightSubtitle = true,
                    pixels = previewResult?.pixels,
                    width = previewPreset.width,
                    height = previewPreset.height,
                    modifier = Modifier.weight(1f)
                )
            }

            if (previewResult != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resizer_preview_exact_size")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Точный вес PNG: ${formatExactFileSize(previewResult.outputByteSize)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val canSendToEditor = previewResult != null &&
                previewPreset.width <= 512 &&
                previewPreset.height <= 512

            if (canSendToEditor) {
                OutlinedButton(
                    onClick = onOpenResultInEditor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resizer_send_to_editor_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Brush,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть результат (${previewPreset.label}) в редакторе")
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(
    title: String,
    subtitle: String?,
    pixels: IntArray?,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier,
    highlightSubtitle: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 7f)
            if (pixels != null) {
                PixelPreviewCanvas(
                    pixels = pixels,
                    width = width,
                    height = height,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (highlightSubtitle) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlightSubtitle) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PixelPreviewCanvas(
    pixels: IntArray,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(pixels, width, height) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val safePixels = if (pixels.size == safeW * safeH) {
            pixels
        } else {
            IntArray(safeW * safeH)
        }
        Bitmap.createBitmap(safePixels, safeW, safeH, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    Canvas(modifier = modifier) {
        val scale = min(size.width / imageBitmap.width, size.height / imageBitmap.height)
        val dstW = (imageBitmap.width * scale).roundToInt().coerceAtLeast(1)
        val dstH = (imageBitmap.height * scale).roundToInt().coerceAtLeast(1)
        val offsetX = ((size.width - dstW) / 2f).roundToInt()
        val offsetY = ((size.height - dstH) / 2f).roundToInt()
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(offsetX, offsetY),
            dstSize = IntSize(dstW, dstH),
            filterQuality = FilterQuality.None
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResampleModeSection(
    selectedMode: ResampleMode,
    onSelectMode: (ResampleMode) -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Алгоритм масштабирования",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResampleMode.entries.forEach { mode ->
                    val isSelected = mode == selectedMode
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMode(mode) },
                        label = {
                            Text(
                                text = mode.title,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = Color.Transparent,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                            selectedBorderWidth = 1.5.dp
                        ),
                        modifier = Modifier.testTag("resample_mode_${mode.name.lowercase()}")
                    )
                }
            }

            Text(
                text = selectedMode.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PresetsSection(
    uiState: ResizerUiState,
    onTogglePreset: (ResizePreset) -> Unit
) {
    val listState = rememberLazyListState()

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
            Column {
                Text(
                    text = "Целевые размеры",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "От большего к меньшему",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Vertical scrollable list sorted from largest to smallest
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(256.dp)
                        .testTag("resizer_presets_list")
                ) {
                    items(
                        items = RESIZER_PRESET_SIZES,
                        key = { "${it.width}x${it.height}" }
                    ) { preset ->
                        val isSelected = preset == uiState.previewPreset
                        PresetRowItem(
                            preset = preset,
                            isSelected = isSelected,
                            onClick = { onTogglePreset(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetRowItem(
    preset: ResizePreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("resizer_preset_${preset.width}x${preset.height}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mini aspect-ratio box preview
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(30.dp)
            ) {
                val maxEdge = maxOf(preset.width, preset.height).toFloat().coerceAtLeast(1f)
                val boxW = (26f * (preset.width / maxEdge)).coerceAtLeast(8f).dp
                val boxH = (26f * (preset.height / maxEdge)).coerceAtLeast(8f).dp
                Box(
                    modifier = Modifier
                        .size(width = boxW, height = boxH)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(3.dp)
                        )
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = "${preset.category.label} • пропорция ${preset.aspectLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = if (isSelected) {
                    Icons.Rounded.RadioButtonChecked
                } else {
                    Icons.Rounded.RadioButtonUnchecked
                },
                contentDescription = if (isSelected) "Выбрано ${preset.label}" else "Выбрать ${preset.label}",
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun ExportSection(
    uiState: ResizerUiState,
    onPreserveAlphaChange: (Boolean) -> Unit,
    onSaveGallery: () -> Unit,
    onSaveToFolder: () -> Unit,
    onShareBatch: () -> Unit
) {
    val totalFiles = uiState.inputs.size * uiState.effectivePresets.size
    val canExport = totalFiles > 0 && !uiState.isProcessing

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сохранять альфа-канал",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (uiState.preserveAlpha) {
                            "Прозрачность исходника сохраняется (ARGB)"
                        } else {
                            "Принудительно непрозрачный RGB (alpha = 255)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.preserveAlpha,
                    onCheckedChange = onPreserveAlphaChange,
                    modifier = Modifier.testTag("resizer_alpha_switch")
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Text(
                text = if (totalFiles > 0) {
                    "К созданию: $totalFiles PNG (<имя>_<W>x<H>.png)"
                } else {
                    "Добавьте хотя бы одно изображение для экспорта"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (totalFiles > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (uiState.isProcessing && uiState.progressTotal > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = {
                            (uiState.progressCurrent.toFloat() / uiState.progressTotal.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Text(
                        text = "Обработка: ${uiState.progressCurrent} из ${uiState.progressTotal}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSaveGallery,
                    enabled = canExport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("resizer_export_gallery_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SaveAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("В галерею", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onSaveToFolder,
                    enabled = canExport,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("resizer_export_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("В папку", maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = onShareBatch,
                enabled = canExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resizer_share_batch_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (totalFiles > 1) "Поделиться пакетом ($totalFiles PNG)" else "Поделиться PNG"
                )
            }
        }
    }
}
