package com.example.editor.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CheckerDark
import com.example.ui.theme.CheckerLight
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    currentColorArgb: Int,
    savedColors: List<Int>,
    recentColors: List<Int>,
    onColorPreview: (Int) -> Unit,
    onColorCommit: (Int) -> Unit,
    onSelectPaletteColor: (Int) -> Unit,
    onToggleSaveColor: (Int) -> Unit,
    onRemoveSavedColor: (Int) -> Unit,
    onClearRecentColors: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        ColorPickerContent(
            currentColorArgb = currentColorArgb,
            savedColors = savedColors,
            recentColors = recentColors,
            onColorPreview = onColorPreview,
            onColorCommit = onColorCommit,
            onSelectPaletteColor = onSelectPaletteColor,
            onToggleSaveColor = onToggleSaveColor,
            onRemoveSavedColor = onRemoveSavedColor,
            onClearRecentColors = onClearRecentColors,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ColorPickerContent(
    currentColorArgb: Int,
    savedColors: List<Int>,
    recentColors: List<Int>,
    onColorPreview: (Int) -> Unit,
    onColorCommit: (Int) -> Unit,
    onSelectPaletteColor: (Int) -> Unit,
    onToggleSaveColor: (Int) -> Unit,
    onRemoveSavedColor: (Int) -> Unit,
    onClearRecentColors: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initialHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(currentColorArgb, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember {
        mutableFloatStateOf(((currentColorArgb ushr 24) and 0xFF) / 255f)
    }

    var isDraggingPad by remember { mutableStateOf(false) }
    var isDraggingHue by remember { mutableStateOf(false) }
    var lastEmittedArgb by remember { mutableStateOf<Int?>(currentColorArgb) }

    // Sync HSV only when color changed externally (e.g. tapping a palette swatch)
    LaunchedEffect(currentColorArgb) {
        if (currentColorArgb != lastEmittedArgb) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(currentColorArgb, hsv)
            // Preserve current hue when picking pure white/black/grey so spectrum marker doesn't jump to 0°
            if (hsv[1] > 0.001f && hsv[2] > 0.001f) {
                hue = hsv[0]
            }
            saturation = hsv[1]
            value = hsv[2]
            alpha = ((currentColorArgb ushr 24) and 0xFF) / 255f
            lastEmittedArgb = currentColorArgb
        }
    }

    fun currentArgb(h: Float = hue, s: Float = saturation, v: Float = value, a: Float = alpha): Int {
        return android.graphics.Color.HSVToColor(
            (a * 255f).roundToInt().coerceIn(0, 255),
            floatArrayOf(h, s, v)
        )
    }

    fun emitPreview() {
        val rgb = currentArgb()
        lastEmittedArgb = rgb
        onColorPreview(rgb)
    }

    fun emitCommit() {
        val rgb = currentArgb()
        lastEmittedArgb = rgb
        onColorCommit(rgb)
    }

    val animatedSat by animateFloatAsState(
        targetValue = saturation,
        animationSpec = if (isDraggingPad) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sat_thumb"
    )
    val animatedVal by animateFloatAsState(
        targetValue = value,
        animationSpec = if (isDraggingPad) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "val_thumb"
    )
    val animatedHue by animateFloatAsState(
        targetValue = hue,
        animationSpec = if (isDraggingHue) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "hue_thumb"
    )

    val previewColor = Color(currentArgb())

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + live swatch preview
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Цвет и альфа-канал",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format("#%08X", previewColor.toArgb()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Checkerboard + live RGBA circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
            ) {
                CheckerboardBox(modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(previewColor)
                )
            }
        }

        // 2D Saturation & Value Pad (single uninterrupted pointer stream, zero quantization jump)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        saturation = (down.position.x / size.width).coerceIn(0f, 1f)
                        value = (1f - down.position.y / size.height).coerceIn(0f, 1f)
                        emitPreview()

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                                ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed) {
                                isDraggingPad = false
                                emitCommit()
                                break
                            }
                            change.consume()
                            isDraggingPad = true
                            saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                            value = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            emitPreview()
                        }
                    }
                }
                .testTag("sv_color_pad")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                drawRect(
                    brush = Brush.horizontalGradient(listOf(Color.White, hueColor))
                )
                drawRect(
                    brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
                )

                val thumbX = animatedSat * size.width
                val thumbY = (1f - animatedVal) * size.height
                val outerRadius = if (isDraggingPad) 13.dp.toPx() else 11.dp.toPx()
                val innerRadius = if (isDraggingPad) 11.dp.toPx() else 9.5.dp.toPx()

                drawCircle(
                    color = Color.Black.copy(alpha = 0.45f),
                    radius = outerRadius,
                    center = Offset(thumbX, thumbY),
                    style = Stroke(width = 3.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = innerRadius,
                    center = Offset(thumbX, thumbY),
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }
        }

        // Hue Spectrum Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Оттенок (${hue.roundToInt()}°)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            hue = ((down.position.x / size.width).coerceIn(0f, 1f)) * 360f
                            emitPreview()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change == null || !change.pressed) {
                                    isDraggingHue = false
                                    emitCommit()
                                    break
                                }
                                change.consume()
                                isDraggingHue = true
                                hue = ((change.position.x / size.width).coerceIn(0f, 1f)) * 360f
                                emitPreview()
                            }
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rainbow = listOf(
                        Color(0xFFFF0000),
                        Color(0xFFFFFF00),
                        Color(0xFF00FF00),
                        Color(0xFF00FFFF),
                        Color(0xFF0000FF),
                        Color(0xFFFF00FF),
                        Color(0xFFFF0000)
                    )
                    drawRect(brush = Brush.horizontalGradient(rainbow))
                    val tx = (animatedHue / 360f) * size.width
                    drawCircle(
                        color = Color.White,
                        radius = size.height / 2.2f,
                        center = Offset(tx.coerceIn(10f, size.width - 10f), size.height / 2f),
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

        // Alpha (Transparency) Slider
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Альфа-канал (непрозрачность)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(alpha * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = alpha,
                onValueChange = {
                    alpha = it
                    emitPreview()
                },
                onValueChangeFinished = {
                    emitCommit()
                },
                valueRange = 0.05f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                ),
                modifier = Modifier.testTag("alpha_slider")
            )
        }

        val haptic = LocalHapticFeedback.current
        val isCurrentSaved = currentColorArgb in savedColors

        // Recent History Swatches
        if (recentColors.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "История оттенков",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = onClearRecentColors) {
                        Text(
                            text = "Очистить",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    recentColors.forEachIndexed { idx, argb ->
                        val isSelected = argb == currentColorArgb
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .combinedClickable(
                                    onClick = { onSelectPaletteColor(argb) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleSaveColor(argb)
                                    }
                                )
                                .testTag("recent_sheet_swatch_$idx")
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                CheckerboardBox(modifier = Modifier.fillMaxSize())
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(argb))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Saved Palette Swatches
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Сохранённая палитра",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Удерживайте слот для удаления",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                AssistChip(
                    onClick = { onToggleSaveColor(currentColorArgb) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (isCurrentSaved) Icons.Rounded.Bookmark else Icons.Rounded.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (isCurrentSaved) "В палитре" else "Сохранить",
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (isCurrentSaved) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    ),
                    modifier = Modifier.testTag("sheet_save_color_chip")
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                savedColors.forEachIndexed { idx, argb ->
                    val isSelected = argb == currentColorArgb
                    Surface(
                        shape = RoundedCornerShape(11.dp),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(11.dp)
                            )
                            .combinedClickable(
                                onClick = { onSelectPaletteColor(argb) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRemoveSavedColor(argb)
                                }
                            )
                            .testTag("swatch_$idx")
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CheckerboardBox(modifier = Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(argb))
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun CheckerboardBox(
    modifier: Modifier = Modifier,
    cellDp: Float = 6f
) {
    Canvas(modifier = modifier) {
        val cellPx = cellDp.dp.toPx().coerceAtLeast(4f)
        val cols = (size.width / cellPx).toInt() + 1
        val rows = (size.height / cellPx).toInt() + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val color = if ((r + c) % 2 == 0) CheckerLight else CheckerDark
                drawRect(
                    color = color,
                    topLeft = Offset(c * cellPx, r * cellPx),
                    size = Size(cellPx, cellPx)
                )
            }
        }
    }
}
