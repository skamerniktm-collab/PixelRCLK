package com.example.compress.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.material3.Slider
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
import com.example.compress.model.CompressInputImage
import com.example.compress.model.CompressPreviewResult
import com.example.compress.model.CompressSettings
import com.example.compress.model.CompressUiState
import com.example.compress.model.PngColorLimit
import com.example.compress.model.formatSavingsPercent
import com.example.compress.viewmodel.CompressViewModel
import com.example.editor.ui.components.CheckerboardBox
import com.example.resizer.model.formatExactFileSize
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CompressScreen(
    viewModel: CompressViewModel,
    onImportFromEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeInput = uiState.activeInput
    val previewResult by produceState<CompressPreviewResult?>(
        initialValue = null,
        key1 = activeInput?.id,
        key2 = uiState.settings,
        key3 = activeInput?.pixels
    ) {
        value = if (activeInput == null) {
            null
        } else {
            viewModel.renderPreview(activeInput, uiState.settings)
        }
    }

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
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CompressImportCard(
                            inputs = uiState.inputs,
                            activeInputId = activeInput?.id,
                            onImportFromEditor = onImportFromEditor,
                            onPickGallery = {
                                multiGalleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickFiles = { multiDocumentPicker.launch(arrayOf("image/*")) },
                            onSelectInput = viewModel::selectInput,
                            onRemoveInput = viewModel::removeInput,
                            onClearInputs = viewModel::clearInputs
                        )

                        CompressPreviewCard(
                            activeInput = activeInput,
                            previewResult = previewResult,
                            settings = uiState.settings,
                            inputCount = uiState.inputs.size
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CompressControlsCard(
                            settings = uiState.settings,
                            activeInput = activeInput,
                            onScaleChange = viewModel::setScalePercent,
                            onSelectColorLimit = viewModel::setColorLimit,
                            onToggleAlpha = viewModel::setPreserveAlpha
                        )

                        CompressExportCard(
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
                        CompressImportCard(
                            inputs = uiState.inputs,
                            activeInputId = activeInput?.id,
                            onImportFromEditor = onImportFromEditor,
                            onPickGallery = {
                                multiGalleryPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onPickFiles = { multiDocumentPicker.launch(arrayOf("image/*")) },
                            onSelectInput = viewModel::selectInput,
                            onRemoveInput = viewModel::removeInput,
                            onClearInputs = viewModel::clearInputs
                        )
                    }

                    if (activeInput != null) {
                        item {
                            CompressPreviewCard(
                                activeInput = activeInput,
                                previewResult = previewResult,
                                settings = uiState.settings,
                                inputCount = uiState.inputs.size
                            )
                        }
                    }

                    item {
                        CompressControlsCard(
                            settings = uiState.settings,
                            activeInput = activeInput,
                            onScaleChange = viewModel::setScalePercent,
                            onSelectColorLimit = viewModel::setColorLimit,
                            onToggleAlpha = viewModel::setPreserveAlpha
                        )
                    }

                    item {
                        CompressExportCard(
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
private fun CompressImportCard(
    inputs: List<CompressInputImage>,
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (inputs.isEmpty()) "Сжатие в PNG" else "Загружено файлов: ${inputs.size}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Формат на выходе всегда .png",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (inputs.isNotEmpty()) {
                    TextButton(
                        onClick = onClearInputs,
                        modifier = Modifier.testTag("compress_clear_inputs_button")
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
                    modifier = Modifier.testTag("compress_pick_gallery_button")
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
                    modifier = Modifier.testTag("compress_pick_files_button")
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
                    modifier = Modifier.testTag("compress_import_editor_button")
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

            if (inputs.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(inputs, key = { it.id }) { item ->
                        val selected = item.id == activeInputId
                        CompressInputChip(
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
private fun CompressInputChip(
    item: CompressInputImage,
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
            .testTag("compress_input_item_${item.baseName}")
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
                CompressPixelPreview(
                    pixels = item.pixels,
                    width = item.width,
                    height = item.height,
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
                    text = formatExactFileSize(item.sourceByteSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("compress_remove_input_${item.baseName}")
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
private fun CompressPreviewCard(
    activeInput: CompressInputImage?,
    previewResult: CompressPreviewResult?,
    settings: CompressSettings,
    inputCount: Int
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
                    text = if (inputCount > 1) "Предпросмотр (текущее)" else "Предпросмотр PNG",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val savingsText = previewResult?.let { formatSavingsPercent(it.savedPercent) }
                if (savingsText != null) {
                    val isReduced = (previewResult?.savedBytes ?: 0L) >= 0L
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent,
                        modifier = Modifier
                            .border(
                                width = 1.2.dp,
                                color = if (isReduced) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                shape = RoundedCornerShape(10.dp)
                            )
                            .testTag("compress_savings_badge")
                    ) {
                        Text(
                            text = savingsText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isReduced) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompressPreviewTile(
                    title = "Оригинал • ${activeInput.width}×${activeInput.height}",
                    subtitle = formatExactFileSize(activeInput.sourceByteSize),
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

                val outW = previewResult?.width
                    ?: ((activeInput.width * settings.scalePercent) / 100f).roundToInt().coerceAtLeast(1)
                val outH = previewResult?.height
                    ?: ((activeInput.height * settings.scalePercent) / 100f).roundToInt().coerceAtLeast(1)

                CompressPreviewTile(
                    title = "PNG • ${outW}×${outH}",
                    subtitle = previewResult?.let { formatExactFileSize(it.compressedByteSize) } ?: "Расчёт...",
                    highlightSubtitle = previewResult != null,
                    pixels = previewResult?.pixels,
                    width = outW,
                    height = outH,
                    modifier = Modifier.weight(1f)
                )
            }

            if (previewResult != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compress_size_comparison_box")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Было (${activeInput.width}×${activeInput.height})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatExactFileSize(previewResult.originalByteSize),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Станет PNG (${previewResult.width}×${previewResult.height})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = formatExactFileSize(previewResult.compressedByteSize),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (previewResult.savedBytes < 0L) {
                            Text(
                                text = "Исходник был сжат с потерями (например, JPEG). Для уменьшения веса PNG уменьшите масштаб или палитру цветов.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompressPreviewTile(
    title: String,
    subtitle: String,
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
                CompressPixelPreview(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompressControlsCard(
    settings: CompressSettings,
    activeInput: CompressInputImage?,
    onScaleChange: (Int) -> Unit,
    onSelectColorLimit: (PngColorLimit) -> Unit,
    onToggleAlpha: (Boolean) -> Unit
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
                    text = "Параметры сжатия PNG",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Масштаб разрешения
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val targetDimensionsText = activeInput?.let {
                    val w = ((it.width * settings.scalePercent) / 100f).roundToInt().coerceAtLeast(1)
                    val h = ((it.height * settings.scalePercent) / 100f).roundToInt().coerceAtLeast(1)
                    "${w}×${h} px"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Масштаб разрешения",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = buildString {
                            append("${settings.scalePercent}%")
                            if (targetDimensionsText != null) {
                                append(" • ")
                                append(targetDimensionsText)
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = settings.scalePercent.toFloat(),
                    onValueChange = { onScaleChange(it.roundToInt()) },
                    valueRange = 10f..100f,
                    modifier = Modifier.testTag("compress_scale_slider")
                )
            }

            // Палитра цветов PNG
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Палитра PNG: ${settings.colorLimit.description}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PngColorLimit.entries.forEach { limit ->
                        val selected = settings.colorLimit == limit
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectColorLimit(limit) },
                            label = {
                                Text(
                                    text = limit.shortLabel,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = darkOutlinedFilterChipColors(),
                            border = darkOutlinedFilterChipBorder(selected),
                            modifier = Modifier.testTag("compress_palette_${limit.name.lowercase()}")
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Сохранять альфа-канал",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Выключите для непрозрачных фото, чтобы дополнительно уменьшить вес PNG",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = settings.preserveAlpha,
                    onCheckedChange = onToggleAlpha,
                    modifier = Modifier.testTag("compress_alpha_switch")
                )
            }
        }
    }
}

@Composable
private fun CompressExportCard(
    uiState: CompressUiState,
    onSaveToGallery: () -> Unit,
    onSaveToFolder: () -> Unit,
    onShareBatch: () -> Unit,
    onDismissDone: () -> Unit
) {
    val canExport = uiState.inputs.isNotEmpty() && !uiState.isProcessing

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
                        imageVector = Icons.Rounded.Compress,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Сохранение сжатого PNG",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (uiState.inputs.isNotEmpty()) {
                    Text(
                        text = "${uiState.inputs.size} файл.",
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
                )
                Text(
                    text = "Сжатие и сохранение PNG: ${uiState.progressCurrent} из ${uiState.progressTotal}...",
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
                        .testTag("compress_saved_status_card")
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
                            modifier = Modifier.testTag("compress_done_button")
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
                    .testTag("compress_save_gallery_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SaveAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (uiState.inputs.size > 1) {
                        "Сохранить все (${uiState.inputs.size} PNG)"
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
                        .testTag("compress_save_folder_button")
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
                        .testTag("compress_share_button")
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
                text = "Галерея: Pictures/PixelResize/Compressed • Всегда формат .png",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun darkOutlinedFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun darkOutlinedFilterChipBorder(selected: Boolean) = FilterChipDefaults.filterChipBorder(
    enabled = true,
    selected = selected,
    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.5.dp
)

@Composable
private fun CompressPixelPreview(
    pixels: IntArray,
    width: Int,
    height: Int,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(pixels, width, height) {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
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
