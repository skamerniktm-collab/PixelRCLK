package com.example.editor.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.AutoFixNormal
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.editor.model.EditorTool
import com.example.editor.model.SelectionMode
import kotlin.math.roundToInt

@Composable
fun EditorTopBar(
    canvasWidth: Int,
    canvasHeight: Int,
    zoomScale: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    onNewProjectClick: () -> Unit,
    onResetZoomClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier,
    applyStatusBarPadding: Boolean = true
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: New Canvas / Import Photo + Canvas Dimensions & Zoom Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onNewProjectClick,
                    modifier = Modifier.testTag("new_project_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddBox,
                        contentDescription = "Новый холст или импорт фото",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onResetZoomClick)
                        .testTag("zoom_reset_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "${canvasWidth}×${canvasHeight}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "• ${(zoomScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (zoomScale !in 0.95f..1.05f) {
                            Icon(
                                imageVector = Icons.Rounded.ZoomOutMap,
                                contentDescription = "Сбросить масштаб",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Right: Undo / Redo + Export PNG
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onUndoClick,
                    enabled = canUndo,
                    modifier = Modifier.testTag("undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "Отменить",
                        tint = if (canUndo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                }

                IconButton(
                    onClick = onRedoClick,
                    enabled = canRedo,
                    modifier = Modifier.testTag("redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Redo,
                        contentDescription = "Повторить",
                        tint = if (canRedo) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                FilledTonalIconButton(
                    onClick = onExportClick,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("export_png_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SaveAlt,
                        contentDescription = "Экспорт PNG"
                    )
                }
            }
        }
    }
}

@Composable
fun EditorToolOptionsPill(
    activeTool: EditorTool,
    selectionMode: SelectionMode,
    hasSelection: Boolean,
    hasClipboard: Boolean,
    selectedColorArgb: Int,
    brushSize: Int,
    fillTolerance: Int,
    onBrushSizeChange: (Int) -> Unit,
    onFillToleranceChange: (Int) -> Unit,
    onSelectionModeChange: (SelectionMode) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onInvertSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCopySelection: () -> Unit,
    onCutSelection: () -> Unit,
    onPasteClipboard: () -> Unit,
    onExtractToNewLayer: () -> Unit,
    onRecolorSelected: () -> Unit,
    onOpenColorPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activeTool == EditorTool.HAND) return

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        if (activeTool == EditorTool.SELECT) {
            SelectionOptionsContent(
                selectionMode = selectionMode,
                hasSelection = hasSelection,
                hasClipboard = hasClipboard,
                fillTolerance = fillTolerance,
                onSelectionModeChange = onSelectionModeChange,
                onFillToleranceChange = onFillToleranceChange,
                onSelectAll = onSelectAll,
                onClearSelection = onClearSelection,
                onInvertSelection = onInvertSelection,
                onDeleteSelected = onDeleteSelected,
                onCopySelection = onCopySelection,
                onCutSelection = onCutSelection,
                onPasteClipboard = onPasteClipboard,
                onExtractToNewLayer = onExtractToNewLayer,
                onRecolorSelected = onRecolorSelected
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Color Swatch Button (48dp touch target)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenColorPicker)
                        .testTag("color_swatch_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 5f)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(selectedColorArgb))
                        )
                    }
                }

                AnimatedContent(
                    targetState = activeTool,
                    label = "tool_options",
                    modifier = Modifier.weight(1f)
                ) { tool ->
                    when (tool) {
                        EditorTool.BRUSH, EditorTool.ERASER -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.width(62.dp)) {
                                    Text(
                                        text = if (tool == EditorTool.ERASER) "Ластик" else "Размер",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$brushSize px",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Slider(
                                    value = brushSize.toFloat(),
                                    onValueChange = { onBrushSizeChange(it.roundToInt()) },
                                    valueRange = 1f..24f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("brush_size_slider")
                                )

                                // Live Brush Size Dot Indicator
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainer)
                                ) {
                                    val visualSize = (4f + (brushSize / 24f) * 18f).dp
                                    Box(
                                        modifier = Modifier
                                            .size(visualSize)
                                            .clip(CircleShape)
                                            .background(
                                                if (tool == EditorTool.ERASER) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            )
                                    )
                                }
                            }
                        }

                        EditorTool.FILL -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Допуск:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                listOf(0 to "Точно", 10 to "10", 20 to "20", 30 to "30", 48 to "48").forEach { (tol, label) ->
                                    val isSelected = fillTolerance == tol
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onFillToleranceChange(tol) },
                                        label = {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelMedium
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
                                        )
                                    )
                                }
                            }
                        }

                        EditorTool.EYEDROPPER -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Нажмите на пиксель для захвата",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format("#%08X", selectedColorArgb),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        EditorTool.SELECT, EditorTool.HAND -> Unit
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorPaletteStrip(
    selectedColorArgb: Int,
    recentColors: List<Int>,
    savedColors: List<Int>,
    onSelectColor: (Int) -> Unit,
    onToggleSaveCurrentColor: () -> Unit,
    onPinRecentColor: (Int) -> Unit,
    onRemoveSavedColor: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isCurrentSaved = selectedColorArgb in savedColors

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Save / Unsave current color button (48dp touch box)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleSaveCurrentColor)
                    .testTag("save_color_slot_button")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            width = if (isCurrentSaved) 1.5.dp else 1.dp,
                            color = if (isCurrentSaved) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isCurrentSaved) Icons.Rounded.Bookmark else Icons.Rounded.Add,
                        contentDescription = if (isCurrentSaved) {
                            "Убрать цвет из сохранённых"
                        } else {
                            "Сохранить цвет в палитру"
                        },
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            VerticalDivider(
                modifier = Modifier
                    .height(22.dp)
                    .padding(horizontal = 2.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            LazyRow(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (recentColors.isNotEmpty()) {
                    item(key = "history_icon") {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "История цветов",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(start = 4.dp, end = 2.dp)
                                .size(15.dp)
                        )
                    }
                    itemsIndexed(
                        items = recentColors,
                        key = { idx, argb -> "recent_${idx}_$argb" }
                    ) { idx, argb ->
                        StripColorSlot(
                            argb = argb,
                            selected = argb == selectedColorArgb,
                            shape = CircleShape,
                            contentDescription = "Недавний цвет ${String.format("#%08X", argb)}",
                            onClick = { onSelectColor(argb) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onPinRecentColor(argb)
                            },
                            modifier = Modifier.testTag("recent_strip_swatch_$idx")
                        )
                    }

                    if (savedColors.isNotEmpty()) {
                        item(key = "palette_divider") {
                            VerticalDivider(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            )
                        }
                    }
                }

                if (savedColors.isNotEmpty()) {
                    item(key = "saved_icon") {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkBorder,
                            contentDescription = "Сохранённая палитра",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp, end = 2.dp)
                                .size(15.dp)
                        )
                    }
                    itemsIndexed(
                        items = savedColors,
                        key = { idx, argb -> "saved_${idx}_$argb" }
                    ) { idx, argb ->
                        StripColorSlot(
                            argb = argb,
                            selected = argb == selectedColorArgb,
                            shape = RoundedCornerShape(9.dp),
                            contentDescription = "Сохранённый цвет ${String.format("#%08X", argb)}",
                            onClick = { onSelectColor(argb) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onRemoveSavedColor(argb)
                            },
                            modifier = Modifier.testTag("saved_strip_swatch_$idx")
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StripColorSlot(
    argb: Int,
    selected: Boolean,
    shape: Shape,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = contentDescription
            )
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 28.dp else 24.dp)
                .clip(shape)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                    },
                    shape = shape
                )
        ) {
            CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 4f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(argb))
            )
        }
    }
}

@Composable
private fun SelectionOptionsContent(
    selectionMode: SelectionMode,
    hasSelection: Boolean,
    hasClipboard: Boolean,
    fillTolerance: Int,
    onSelectionModeChange: (SelectionMode) -> Unit,
    onFillToleranceChange: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onInvertSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCopySelection: () -> Unit,
    onCutSelection: () -> Unit,
    onPasteClipboard: () -> Unit,
    onExtractToNewLayer: () -> Unit,
    onRecolorSelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Row 1: Selection modes (+ Magic Wand tolerance if selected)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionModeChip(
                selected = selectionMode == SelectionMode.RECTANGLE,
                enabled = true,
                icon = Icons.Rounded.CropFree,
                label = SelectionMode.RECTANGLE.label,
                tag = "select_mode_rect",
                onClick = { onSelectionModeChange(SelectionMode.RECTANGLE) }
            )
            SelectionModeChip(
                selected = selectionMode == SelectionMode.LASSO,
                enabled = true,
                icon = Icons.Rounded.Gesture,
                label = SelectionMode.LASSO.label,
                tag = "select_mode_lasso",
                onClick = { onSelectionModeChange(SelectionMode.LASSO) }
            )
            SelectionModeChip(
                selected = selectionMode == SelectionMode.MAGIC_WAND,
                enabled = true,
                icon = Icons.Rounded.AutoFixHigh,
                label = SelectionMode.MAGIC_WAND.label,
                tag = "select_mode_wand",
                onClick = { onSelectionModeChange(SelectionMode.MAGIC_WAND) }
            )
            SelectionModeChip(
                selected = selectionMode == SelectionMode.MOVE,
                enabled = hasSelection,
                icon = Icons.Rounded.OpenWith,
                label = SelectionMode.MOVE.label,
                tag = "select_mode_move",
                onClick = { onSelectionModeChange(SelectionMode.MOVE) }
            )

            if (selectionMode == SelectionMode.MAGIC_WAND) {
                val nextTol = when (fillTolerance) {
                    0 -> 10
                    10 -> 20
                    20 -> 30
                    30 -> 48
                    else -> 0
                }
                val tolLabel = when (fillTolerance) {
                    0 -> "Допуск: 0"
                    10 -> "Допуск: 10"
                    20 -> "Допуск: 20"
                    30 -> "Допуск: 30"
                    else -> "Допуск: 48"
                }
                AssistChip(
                    onClick = { onFillToleranceChange(nextTol) },
                    label = { Text(tolLabel, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // Row 2: Actions on selection / clipboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionActionChip(
                icon = Icons.Rounded.SelectAll,
                label = "Всё",
                enabled = true,
                tag = "select_all_button",
                onClick = onSelectAll
            )
            if (hasSelection) {
                SelectionActionChip(
                    icon = Icons.Rounded.Palette,
                    label = "Перекрасить",
                    enabled = true,
                    tag = "recolor_selection_button",
                    onClick = onRecolorSelected
                )
                SelectionActionChip(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Копир.",
                    enabled = true,
                    tag = "copy_selection_button",
                    onClick = onCopySelection
                )
                SelectionActionChip(
                    icon = Icons.Rounded.ContentCut,
                    label = "Вырезать",
                    enabled = true,
                    tag = "cut_selection_button",
                    onClick = onCutSelection
                )
                SelectionActionChip(
                    icon = Icons.Rounded.DeleteSweep,
                    label = "Очистить",
                    enabled = true,
                    tag = "delete_selection_button",
                    onClick = onDeleteSelected
                )
                SelectionActionChip(
                    icon = Icons.Rounded.Layers,
                    label = "В слой",
                    enabled = true,
                    tag = "extract_layer_button",
                    onClick = onExtractToNewLayer
                )
                SelectionActionChip(
                    icon = Icons.Rounded.Flip,
                    label = "Инверт.",
                    enabled = true,
                    tag = "invert_selection_button",
                    onClick = onInvertSelection
                )
                SelectionActionChip(
                    icon = Icons.Rounded.Close,
                    label = "Снять",
                    enabled = true,
                    tag = "clear_selection_button",
                    onClick = onClearSelection
                )
            }
            if (hasClipboard) {
                SelectionActionChip(
                    icon = Icons.Rounded.ContentPaste,
                    label = "Вставить",
                    enabled = true,
                    tag = "paste_selection_button",
                    onClick = onPasteClipboard
                )
            }
        }
    }
}

@Composable
private fun SelectionModeChip(
    selected: Boolean,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.Transparent,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = Color.Transparent,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            selectedBorderWidth = 1.5.dp
        ),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun SelectionActionChip(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
fun EditorBottomNavigationBar(
    activeTool: EditorTool,
    layerCount: Int,
    showLayersButton: Boolean,
    onSelectTool: (EditorTool) -> Unit,
    onOpenLayers: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(76.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val itemCount = 6
                val minItemWidth = 60.dp
                val needsScroll = maxWidth < minItemWidth * itemCount
                val computedWidth = if (needsScroll) minItemWidth else maxWidth / itemCount
                val scrollModifier = if (needsScroll) {
                    Modifier.horizontalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                }

                Row(
                    modifier = scrollModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (needsScroll) Arrangement.Start else Arrangement.SpaceEvenly
                ) {
                    LilacNavBarItem(
                        selected = activeTool == EditorTool.BRUSH,
                        label = "Кисть",
                        icon = if (activeTool == EditorTool.BRUSH) Icons.Rounded.Brush else Icons.Outlined.Brush,
                        contentDescription = "Кисть",
                        onClick = { onSelectTool(EditorTool.BRUSH) },
                        modifier = Modifier
                            .width(computedWidth)
                            .testTag("tool_brush_button")
                    )

                    LilacNavBarItem(
                        selected = activeTool == EditorTool.ERASER,
                        label = "Ластик",
                        icon = if (activeTool == EditorTool.ERASER) Icons.Rounded.AutoFixNormal else Icons.Outlined.AutoFixNormal,
                        contentDescription = "Ластик",
                        onClick = { onSelectTool(EditorTool.ERASER) },
                        modifier = Modifier
                            .width(computedWidth)
                            .testTag("tool_eraser_button")
                    )

                    LilacNavBarItem(
                        selected = activeTool == EditorTool.FILL,
                        label = "Заливка",
                        icon = if (activeTool == EditorTool.FILL) Icons.Rounded.FormatColorFill else Icons.Outlined.FormatColorFill,
                        contentDescription = "Заливка",
                        onClick = { onSelectTool(EditorTool.FILL) },
                        modifier = Modifier
                            .width(computedWidth)
                            .testTag("tool_fill_button")
                    )

                    LilacNavBarItem(
                        selected = activeTool == EditorTool.SELECT,
                        label = "Выбор",
                        icon = if (activeTool == EditorTool.SELECT) Icons.Rounded.SelectAll else Icons.Outlined.SelectAll,
                        contentDescription = "Выделение",
                        onClick = { onSelectTool(EditorTool.SELECT) },
                        modifier = Modifier
                            .width(computedWidth)
                            .testTag("tool_select_button")
                    )

                    LilacNavBarItem(
                        selected = activeTool == EditorTool.EYEDROPPER,
                        label = "Пипетка",
                        icon = if (activeTool == EditorTool.EYEDROPPER) Icons.Rounded.Colorize else Icons.Outlined.Colorize,
                        contentDescription = "Пипетка",
                        onClick = { onSelectTool(EditorTool.EYEDROPPER) },
                        modifier = Modifier
                            .width(computedWidth)
                            .testTag("tool_eyedropper_button")
                    )

                    LilacNavBarItem(
                        selected = activeTool == EditorTool.HAND,
                        label = "Рука",
                        icon = if (activeTool == EditorTool.HAND) Icons.Rounded.PanTool else Icons.Outlined.PanTool,
                        contentDescription = "Рука",
                        onClick = { onSelectTool(EditorTool.HAND) },
                        modifier = Modifier
                            .width(computedWidth)
                            .testTag("tool_hand_button")
                    )
                }
            }

            if (showLayersButton) {
                VerticalDivider(
                    modifier = Modifier
                        .height(40.dp)
                        .padding(horizontal = 2.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                LilacNavBarItem(
                    selected = false,
                    label = "Слои",
                    icon = Icons.Rounded.Layers,
                    contentDescription = "Слои",
                    badgeCount = layerCount,
                    onClick = onOpenLayers,
                    modifier = Modifier
                        .widthIn(min = 62.dp)
                        .testTag("open_layers_button")
                )
            }
        }
    }
}

@Composable
private fun LilacNavBarItem(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 52.dp, height = 32.dp)
                .clip(CircleShape)
        ) {
            val iconTint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            if (badgeCount != null) {
                BadgedBox(
                    badge = {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(badgeCount.toString())
                        }
                    }
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
