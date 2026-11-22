package com.example.editor.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.editor.engine.PixelOperations
import com.example.editor.model.CanvasPreset
import com.example.editor.model.PixelLayer
import com.example.editor.model.PixelSelection
import com.example.editor.model.RecolorShadingMode
import kotlin.math.roundToInt

@Composable
fun NewCanvasDialog(
    onSelectPreset: (CanvasPreset) -> Unit,
    onImportPhotoClick: () -> Unit,
    onOpenFileClick: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRatio by remember { mutableStateOf("Все") }
    val ratioFilters = remember { listOf("Все", "1:1", "2:1", "4:1", "8:1") }
    val filteredPresets = remember(selectedRatio) {
        if (selectedRatio == "Все") {
            CanvasPreset.entries
        } else {
            CanvasPreset.entries.filter { it.ratio == selectedRatio }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "Новый проект",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Создайте прозрачный холст или откройте изображение из галереи и проводника.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onImportPhotoClick()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("import_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть из галереи")
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenFileClick()
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_file_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Открыть файл (Проводник)")
                }

                // Aspect Ratio Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ratioFilters.forEach { ratio ->
                        val isSelected = selectedRatio == ratio
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRatio = ratio },
                            label = {
                                Text(
                                    text = ratio,
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

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredPresets,
                        key = { "${it.width}x${it.height}" }
                    ) { preset ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    onSelectPreset(preset)
                                    onDismiss()
                                }
                                .testTag("preset_${preset.width}x${preset.height}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Grid4x4,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = preset.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Соотношение ${preset.ratio}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(
                                    text = "RGBA",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
fun ExportPngDialog(
    layers: List<PixelLayer>,
    canvasWidth: Int,
    canvasHeight: Int,
    isExporting: Boolean,
    onSaveToGallery: (Boolean) -> Unit,
    onSharePng: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var preserveAlpha by remember { mutableStateOf(true) }

    val previewBitmap = remember(layers, canvasWidth, canvasHeight, preserveAlpha) {
        val composited = if (preserveAlpha) {
            PixelOperations.compositeLayers(layers, canvasWidth, canvasHeight)
        } else {
            PixelOperations.compositeLayersOpaque(layers, canvasWidth, canvasHeight)
        }
        Bitmap.createBitmap(composited, canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                text = "Экспорт PNG",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 180.dp, height = 140.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                ) {
                    CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 8f)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scale = minOf(
                            size.width / canvasWidth.coerceAtLeast(1),
                            size.height / canvasHeight.coerceAtLeast(1)
                        )
                        val drawW = (canvasWidth * scale).roundToInt().coerceAtLeast(1)
                        val drawH = (canvasHeight * scale).roundToInt().coerceAtLeast(1)
                        val offX = ((size.width - drawW) / 2f).roundToInt()
                        val offY = ((size.height - drawH) / 2f).roundToInt()
                        drawImage(
                            image = previewBitmap,
                            dstOffset = IntOffset(offX, offY),
                            dstSize = IntSize(drawW, drawH),
                            filterQuality = FilterQuality.None
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = if (preserveAlpha) {
                            "$canvasWidth × $canvasHeight px • RGBA с альфа-каналом"
                        } else {
                            "$canvasWidth × $canvasHeight px • RGB без прозрачности"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                // Alpha Channel Toggle (RGBA / RGB)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !isExporting) { preserveAlpha = !preserveAlpha }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "Сохранять с альфа-каналом",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (preserveAlpha) {
                                    "RGBA — прозрачные участки сохраняются"
                                } else {
                                    "RGB — альфа = 255, видна вся текстура"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = preserveAlpha,
                            onCheckedChange = { preserveAlpha = it },
                            enabled = !isExporting,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            ),
                            modifier = Modifier.testTag("preserve_alpha_switch")
                        )
                    }
                }

                if (isExporting) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onSaveToGallery(preserveAlpha) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("confirm_save_png_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SaveAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Сохранить в галерею")
                        }

                        OutlinedButton(
                            onClick = { onSharePng(preserveAlpha) },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("share_png_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Поделиться PNG")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isExporting
            ) {
                Text("Отмена")
            }
        }
    )
}

private data class CroppedSelectionPreview(
    val width: Int,
    val height: Int,
    val pixels: IntArray
)

@Composable
fun RecolorSelectionDialog(
    activeLayer: PixelLayer,
    selection: PixelSelection,
    canvasWidth: Int,
    canvasHeight: Int,
    initialColorArgb: Int,
    savedColors: List<Int>,
    recentColors: List<Int>,
    onApply: (targetColorArgb: Int, mode: RecolorShadingMode, strength: Float, brightnessShift: Float, recolorWhite: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember(initialColorArgb) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColorArgb, it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var brightnessShift by remember { mutableFloatStateOf(0f) }
    var strength by remember { mutableFloatStateOf(1f) }
    var recolorWhite by remember { mutableStateOf(true) }
    var shadingMode by remember { mutableStateOf(RecolorShadingMode.LUMINANCE) }

    val targetColorArgb = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(255, floatArrayOf(hue, saturation, value))
    }

    val quickSwatches = remember(recentColors, savedColors) {
        val white = 0xFFFFFFFF.toInt()
        buildList {
            add(white)
            recentColors.forEach { if (it !in this) add(it) }
            savedColors.forEach { if (it !in this) add(it) }
        }.take(16)
    }

    // Extract minimal bounding box of selected pixels once for fast live preview
    val cropped = remember(activeLayer.id, activeLayer.version, selection.version) {
        var minX = canvasWidth
        var minY = canvasHeight
        var maxX = -1
        var maxY = -1
        val mask = selection.mask
        val src = activeLayer.pixels
        for (y in 0 until canvasHeight) {
            for (x in 0 until canvasWidth) {
                val idx = y * canvasWidth + x
                if (mask[idx] && (src[idx] ushr 24) != 0) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            null
        } else {
            val cw = maxX - minX + 1
            val ch = maxY - minY + 1
            val buf = IntArray(cw * ch)
            for (y in 0 until ch) {
                for (x in 0 until cw) {
                    val idx = (minY + y) * canvasWidth + (minX + x)
                    if (mask[idx]) {
                        buf[y * cw + x] = src[idx]
                    }
                }
            }
            CroppedSelectionPreview(cw, ch, buf)
        }
    }

    val recoloredPreviewBitmap = remember(cropped, targetColorArgb, shadingMode, strength, brightnessShift, recolorWhite) {
        cropped?.let { c ->
            val out = IntArray(c.pixels.size) { i ->
                val p = c.pixels[i]
                if ((p ushr 24) == 0) 0 else PixelOperations.recolorPixelWithShading(
                    srcArgb = p,
                    targetArgb = targetColorArgb,
                    mode = shadingMode,
                    strength = strength,
                    brightnessShift = brightnessShift,
                    recolorWhite = recolorWhite
                )
            }
            Bitmap.createBitmap(out, c.width, c.height, Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Перекраска выделения",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "С сохранением светотени и прозрачности",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Live preview box
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                ) {
                    CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 8f)
                    if (recoloredPreviewBitmap != null && cropped != null) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            val scale = minOf(
                                size.width / cropped.width.coerceAtLeast(1),
                                size.height / cropped.height.coerceAtLeast(1)
                            )
                            val drawW = (cropped.width * scale).roundToInt().coerceAtLeast(1)
                            val drawH = (cropped.height * scale).roundToInt().coerceAtLeast(1)
                            val offX = ((size.width - drawW) / 2f).roundToInt()
                            val offY = ((size.height - drawH) / 2f).roundToInt()
                            drawImage(
                                image = recoloredPreviewBitmap,
                                dstOffset = IntOffset(offX, offY),
                                dstSize = IntSize(drawW, drawH),
                                filterQuality = FilterQuality.None
                            )
                        }
                    } else {
                        Text(
                            text = "В выделении нет непрозрачных пикселей",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Shading mode selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Режим",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        RecolorShadingMode.entries.forEach { mode ->
                            val isSelected = shadingMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { shadingMode = mode },
                                label = {
                                    Text(
                                        text = mode.label,
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
                                ),
                                modifier = Modifier.testTag("recolor_mode_${mode.name.lowercase()}")
                            )
                        }
                        FilterChip(
                            selected = recolorWhite,
                            onClick = { recolorWhite = !recolorWhite },
                            label = {
                                Text(
                                    text = "Белый цвет",
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
                                selected = recolorWhite,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                selectedBorderWidth = 1.5.dp
                            ),
                            modifier = Modifier.testTag("recolor_white_toggle")
                        )
                    }
                    Text(
                        text = if (shadingMode == RecolorShadingMode.LUMINANCE) {
                            "Сохраняет исходные тени, блики и чёрные контуры"
                        } else {
                            "Окрашивает светлые участки (подходит для белых/серых текстур)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Quick Swatches + Current Target Color
                if (quickSwatches.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(quickSwatches) { idx, argb ->
                            val selected = (argb and 0x00FFFFFF) == (targetColorArgb and 0x00FFFFFF)
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb or 0xFF000000.toInt()))
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        val hsv = FloatArray(3)
                                        android.graphics.Color.colorToHSV(argb, hsv)
                                        if (hsv[1] > 0.001f && hsv[2] > 0.001f) {
                                            hue = hsv[0]
                                        }
                                        saturation = hsv[1]
                                        value = hsv[2]
                                    }
                                    .testTag("recolor_swatch_$idx")
                            )
                        }
                    }
                }

                // Target Hue, Saturation, Value (for Tint), Strength sliders
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(targetColorArgb))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            )
                            Text(
                                text = "Оттенок (${hue.roundToInt()}°)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = String.format("#%06X", targetColorArgb and 0x00FFFFFF),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("recolor_hue_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Насыщенность",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(saturation * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("recolor_sat_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Яркость",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val pct = (brightnessShift * 100).roundToInt()
                        Text(
                            text = if (pct > 0) "+$pct%" else "$pct%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = brightnessShift,
                        onValueChange = { brightnessShift = it },
                        valueRange = -1f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("recolor_brightness_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Интенсивность",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(strength * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = strength,
                        onValueChange = { strength = it },
                        valueRange = 0.05f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("recolor_strength_slider")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = cropped != null,
                onClick = { onApply(targetColorArgb, shadingMode, strength, brightnessShift, recolorWhite) },
                modifier = Modifier.testTag("apply_recolor_button")
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
