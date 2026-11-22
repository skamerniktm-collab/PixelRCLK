package com.example.editor.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.editor.model.EditorSnapshot
import com.example.editor.model.PixelLayer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    layers: List<PixelLayer>,
    activeLayerId: String,
    onSelectLayer: (String) -> Unit,
    onAddLayer: () -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onOpacityChange: (String, Float) -> Unit,
    onCaptureOpacitySnapshot: () -> EditorSnapshot,
    onCommitOpacitySnapshot: (EditorSnapshot) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onMergeDown: (String) -> Unit,
    onRenameLayer: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        LayersPanelContent(
            layers = layers,
            activeLayerId = activeLayerId,
            onSelectLayer = onSelectLayer,
            onAddLayer = onAddLayer,
            onDuplicateLayer = onDuplicateLayer,
            onDeleteLayer = onDeleteLayer,
            onToggleVisibility = onToggleVisibility,
            onOpacityChange = onOpacityChange,
            onCaptureOpacitySnapshot = onCaptureOpacitySnapshot,
            onCommitOpacitySnapshot = onCommitOpacitySnapshot,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onMergeDown = onMergeDown,
            onRenameLayer = onRenameLayer,
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
fun LayersPanelContent(
    layers: List<PixelLayer>,
    activeLayerId: String,
    onSelectLayer: (String) -> Unit,
    onAddLayer: () -> Unit,
    onDuplicateLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onOpacityChange: (String, Float) -> Unit,
    onCaptureOpacitySnapshot: () -> EditorSnapshot,
    onCommitOpacitySnapshot: (EditorSnapshot) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onMergeDown: (String) -> Unit,
    onRenameLayer: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var renamingLayer by remember { mutableStateOf<PixelLayer?>(null) }
    var opacityBeforeDrag by remember { mutableStateOf<EditorSnapshot?>(null) }

    // Display top layer first (reverse order of rendering stack)
    val displayLayers = remember(layers) { layers.asReversed() }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Слои (${layers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedButton(
                onClick = onAddLayer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("add_layer_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Добавить")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(
                items = displayLayers,
                key = { _, layer -> layer.id }
            ) { displayIndex, layer ->
                val isSelected = layer.id == activeLayerId
                val stackIndex = layers.lastIndex - displayIndex
                val canMoveUp = stackIndex < layers.lastIndex
                val canMoveDown = stackIndex > 0
                val canDelete = layers.size > 1

                LayerCardItem(
                    layer = layer,
                    isSelected = isSelected,
                    canMoveUp = canMoveUp,
                    canMoveDown = canMoveDown,
                    canMergeDown = canMoveDown,
                    canDelete = canDelete,
                    onSelect = { onSelectLayer(layer.id) },
                    onToggleVisibility = { onToggleVisibility(layer.id) },
                    onOpacityChange = { newOpacity ->
                        if (opacityBeforeDrag == null) {
                            opacityBeforeDrag = onCaptureOpacitySnapshot()
                        }
                        onOpacityChange(layer.id, newOpacity)
                    },
                    onOpacityChangeFinished = {
                        opacityBeforeDrag?.let { snap ->
                            onCommitOpacitySnapshot(snap)
                            opacityBeforeDrag = null
                        }
                    },
                    onMoveUp = { onMoveUp(layer.id) },
                    onMoveDown = { onMoveDown(layer.id) },
                    onDuplicate = { onDuplicateLayer(layer.id) },
                    onMergeDown = { onMergeDown(layer.id) },
                    onDelete = { onDeleteLayer(layer.id) },
                    onRenameClick = { renamingLayer = layer }
                )
            }
        }
    }

    renamingLayer?.let { target ->
        var draftName by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renamingLayer = null },
            title = { Text("Переименовать слой") },
            text = {
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    singleLine = true,
                    label = { Text("Название слоя") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameLayer(target.id, draftName)
                        renamingLayer = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingLayer = null }) {
                    Text("Отмена")
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun LayerCardItem(
    layer: PixelLayer,
    isSelected: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canMergeDown: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onToggleVisibility: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onOpacityChangeFinished: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onMergeDown: () -> Unit,
    onDelete: () -> Unit,
    onRenameClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = if (isSelected) 4.dp else 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp)
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onSelect)
            .testTag("layer_card_${layer.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Layer Thumbnail over Checkerboard
                LayerThumbnail(
                    layer = layer,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = layer.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = onRenameClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Переименовать",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Text(
                        text = if (layer.isVisible) {
                            "Непрозрачность: ${(layer.opacity * 100).roundToInt()}%"
                        } else {
                            "Скрыт • ${(layer.opacity * 100).roundToInt()}%"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onToggleVisibility,
                    modifier = Modifier.testTag("toggle_visibility_${layer.id}")
                ) {
                    Icon(
                        imageVector = if (layer.isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = if (layer.isVisible) "Скрыть слой" else "Показать слой",
                        tint = if (layer.isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isSelected) {
                Spacer(modifier = Modifier.height(6.dp))

                // Opacity Slider
                Slider(
                    value = layer.opacity,
                    onValueChange = onOpacityChange,
                    onValueChangeFinished = onOpacityChangeFinished,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("layer_opacity_slider")
                )

                // Layer Action Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row {
                        IconButton(
                            onClick = onMoveUp,
                            enabled = canMoveUp,
                            modifier = Modifier.testTag("layer_move_up")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowUp,
                                contentDescription = "Переместить выше",
                                tint = if (canMoveUp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        IconButton(
                            onClick = onMoveDown,
                            enabled = canMoveDown,
                            modifier = Modifier.testTag("layer_move_down")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Переместить ниже",
                                tint = if (canMoveDown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }

                    Row {
                        IconButton(
                            onClick = onDuplicate,
                            modifier = Modifier.testTag("layer_duplicate")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Дублировать слой",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = onMergeDown,
                            enabled = canMergeDown,
                            modifier = Modifier.testTag("layer_merge_down")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerticalAlignBottom,
                                contentDescription = "Объединить вниз",
                                tint = if (canMergeDown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            enabled = canDelete,
                            modifier = Modifier.testTag("layer_delete")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Удалить слой",
                                tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerThumbnail(
    layer: PixelLayer,
    modifier: Modifier = Modifier
) {
    val thumbBitmap = remember(layer.id, layer.version, layer.width, layer.height) {
        Bitmap.createBitmap(layer.pixels, layer.width, layer.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    Box(modifier = modifier) {
        CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 5f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawImage(
                image = thumbBitmap,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                alpha = if (layer.isVisible) layer.opacity else 0.25f,
                filterQuality = FilterQuality.None
            )
        }
    }
}
