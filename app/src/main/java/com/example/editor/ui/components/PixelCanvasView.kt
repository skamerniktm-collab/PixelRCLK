package com.example.editor.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridOff
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.editor.model.EditorTool
import com.example.editor.model.EyedropperPreview
import com.example.editor.model.PixelLayer
import com.example.editor.model.PixelSelection
import com.example.editor.model.SelectionRect
import com.example.ui.theme.CheckerDark
import com.example.ui.theme.CheckerLight
import com.example.ui.theme.GraphiteSurfaceDim
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun PixelCanvasView(
    canvasWidth: Int,
    canvasHeight: Int,
    layers: List<PixelLayer>,
    activeLayerName: String,
    activeTool: EditorTool,
    showPixelGrid: Boolean,
    hoveredPixel: Pair<Int, Int>?,
    eyedropperPreview: EyedropperPreview?,
    selection: PixelSelection?,
    selectionPreviewRect: SelectionRect?,
    lassoPreviewPoints: List<Pair<Int, Int>>,
    zoomResetToken: Int,
    onToggleGrid: () -> Unit,
    onClearSelection: () -> Unit,
    onZoomChanged: (Float) -> Unit,
    onBeginInteraction: (Int, Int) -> Unit,
    onContinueInteraction: (Int, Int) -> Unit,
    onEndInteraction: () -> Unit,
    onCancelStrokeForGesture: () -> Unit,
    modifier: Modifier = Modifier
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(zoomResetToken, canvasWidth, canvasHeight) {
        zoom = 1f
        panOffset = Offset.Zero
        onZoomChanged(1f)
    }

    // Build crisp checkerboard ImageBitmap once per canvas size
    val checkerboardBitmap = remember(canvasWidth, canvasHeight) {
        createCheckerboardBitmap(canvasWidth, canvasHeight)
    }

    // Cache Android Bitmap + ImageBitmap per layer keyed by version
    val layerBitmapCache = remember {
        mutableMapOf<String, LayerBitmapEntry>()
    }

    val activeIds = layers.map { it.id }.toSet()
    layerBitmapCache.keys.retainAll(activeIds)
    val layerImages: List<Pair<PixelLayer, ImageBitmap>> = layers.map { layer ->
        val cached = layerBitmapCache[layer.id]
        val entry = if (cached != null &&
            cached.width == layer.width &&
            cached.height == layer.height &&
            cached.version == layer.version
        ) {
            cached
        } else {
            val bmp = if (cached != null && cached.width == layer.width && cached.height == layer.height) {
                cached.bitmap
            } else {
                Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
            }
            bmp.setPixels(layer.pixels, 0, layer.width, 0, 0, layer.width, layer.height)
            val newEntry = LayerBitmapEntry(
                width = layer.width,
                height = layer.height,
                version = layer.version,
                bitmap = bmp,
                imageBitmap = bmp.asImageBitmap()
            )
            layerBitmapCache[layer.id] = newEntry
            newEntry
        }
        layer to entry.imageBitmap
    }

    // Precompute selection veil bitmap & merged boundary segments only when selection changes
    val selectionVisual = remember(selection?.version, selection?.width, selection?.height) {
        selection?.let { buildSelectionVisual(it) }
    }

    // Animate marching ants only when a selection or selection preview is visible
    val hasActiveOverlay = selection != null || selectionPreviewRect != null || lassoPreviewPoints.isNotEmpty()
    val marchingAntsPhase = if (hasActiveOverlay) {
        val transition = rememberInfiniteTransition(label = "marching_ants")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 16f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ants_phase"
        )
        phase
    } else {
        0f
    }

    val currentTool by rememberUpdatedState(activeTool)
    val currentOnBegin by rememberUpdatedState(onBeginInteraction)
    val currentOnContinue by rememberUpdatedState(onContinueInteraction)
    val currentOnEnd by rememberUpdatedState(onEndInteraction)
    val currentOnCancel by rememberUpdatedState(onCancelStrokeForGesture)
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(GraphiteSurfaceDim)
            .clipToBounds()
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val viewportHeightPx = with(density) { maxHeight.toPx() }

        val paddingPx = with(density) { 48.dp.toPx() }
        val availW = (viewportWidthPx - paddingPx).coerceAtLeast(64f)
        val availH = (viewportHeightPx - paddingPx).coerceAtLeast(64f)
        val basePixelSize = min(availW / canvasWidth.coerceAtLeast(1), availH / canvasHeight.coerceAtLeast(1))
        val currentBasePixelSize by rememberUpdatedState(basePixelSize)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pixel_canvas_surface")
                .pointerInput(canvasWidth, canvasHeight) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val startedInHandMode = currentTool == EditorTool.HAND
                        val clampForSelection = currentTool == EditorTool.SELECT

                        fun hitTestPixel(pos: Offset): Pair<Int, Int>? {
                            val metrics = computeCanvasDrawMetrics(
                                surfaceWidth = size.width.toFloat(),
                                surfaceHeight = size.height.toFloat(),
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                basePixelSize = currentBasePixelSize,
                                zoom = zoom,
                                panOffset = panOffset
                            )
                            return screenOffsetToPixel(
                                pos = pos,
                                metrics = metrics,
                                canvasWidth = canvasWidth,
                                canvasHeight = canvasHeight,
                                clampToBounds = clampForSelection
                            )
                        }

                        val initialPixel = if (startedInHandMode) null else hitTestPixel(firstDown.position)
                        var isMultiTouch = false
                        var movedPixelsCount = 0
                        var lastPixel = initialPixel

                        if (initialPixel != null) {
                            currentOnBegin(initialPixel.first, initialPixel.second)
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedChanges = event.changes.filter { it.pressed }

                            if (pressedChanges.isEmpty()) {
                                if (!isMultiTouch && !startedInHandMode) {
                                    currentOnEnd()
                                }
                                break
                            }

                            if (startedInHandMode) {
                                // Hand tool: 1 finger pans freely, 2+ fingers zoom & pan simultaneously
                                val zoomChange = if (pressedChanges.size >= 2) event.calculateZoom() else 1f
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)

                                val oldZoom = zoom
                                val newZoom = (oldZoom * zoomChange).coerceIn(0.5f, 36f)
                                val zoomFactor = if (oldZoom > 0f) newZoom / oldZoom else 1f

                                val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                                val centroidFromCenter = centroid - viewportCenter - panOffset
                                val adjustedPan = panOffset + panChange - centroidFromCenter * (zoomFactor - 1f)

                                zoom = newZoom
                                panOffset = adjustedPan
                                if (zoomChange != 1f) {
                                    currentOnZoomChanged(newZoom)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (pressedChanges.size >= 2) {
                                if (!isMultiTouch) {
                                    isMultiTouch = true
                                    val elapsed = System.currentTimeMillis() - downTime
                                    if (elapsed < 280L || movedPixelsCount <= 2 ||
                                        currentTool == EditorTool.FILL ||
                                        currentTool == EditorTool.EYEDROPPER
                                    ) {
                                        currentOnCancel()
                                    } else {
                                        currentOnEnd()
                                    }
                                }
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)

                                val oldZoom = zoom
                                val newZoom = (oldZoom * zoomChange).coerceIn(0.5f, 36f)
                                val zoomFactor = if (oldZoom > 0f) newZoom / oldZoom else 1f

                                // Anchor zoom at pinch centroid relative to viewport center
                                val viewportCenter = Offset(size.width / 2f, size.height / 2f)
                                val centroidFromCenter = centroid - viewportCenter - panOffset
                                val adjustedPan = panOffset + panChange - centroidFromCenter * (zoomFactor - 1f)

                                zoom = newZoom
                                panOffset = adjustedPan
                                currentOnZoomChanged(newZoom)

                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else if (!isMultiTouch) {
                                val change = pressedChanges.first()
                                change.consume()
                                val nextPixel = hitTestPixel(change.position)
                                if (nextPixel != null) {
                                    if (lastPixel == null) {
                                        currentOnBegin(nextPixel.first, nextPixel.second)
                                        lastPixel = nextPixel
                                    } else if (nextPixel != lastPixel) {
                                        movedPixelsCount += abs(nextPixel.first - lastPixel.first) +
                                            abs(nextPixel.second - lastPixel.second)
                                        currentOnContinue(nextPixel.first, nextPixel.second)
                                        lastPixel = nextPixel
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val metrics = computeCanvasDrawMetrics(
                    surfaceWidth = size.width,
                    surfaceHeight = size.height,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    basePixelSize = basePixelSize,
                    zoom = zoom,
                    panOffset = panOffset
                )
                val left = metrics.left
                val top = metrics.top
                val totalW = metrics.totalWidth
                val totalH = metrics.totalHeight

                val dstOffset = IntOffset(left, top)
                val dstSize = IntSize(totalW, totalH)

                // 1. Subtle outer glow / shadow frame around canvas
                drawRect(
                    color = Color(0xFF3E384E),
                    topLeft = Offset(left - 2f, top - 2f),
                    size = Size(totalW + 4f, totalH + 4f)
                )

                // 2. Crisp Alpha Checkerboard
                drawImage(
                    image = checkerboardBitmap,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    filterQuality = FilterQuality.None
                )

                // 3. Visible Layers Bottom-to-Top
                for ((layer, image) in layerImages) {
                    if (!layer.isVisible || layer.opacity <= 0.001f) continue
                    drawImage(
                        image = image,
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        alpha = layer.opacity.coerceIn(0f, 1f),
                        filterQuality = FilterQuality.None
                    )
                }

                // 4. Optional Pixel Grid when zoomed enough to be readable (clipped to viewport)
                val actualPixelW = metrics.actualPixelW
                val actualPixelH = metrics.actualPixelH
                if (showPixelGrid && actualPixelW >= 7f && actualPixelH >= 7f) {
                    val gridColor = Color(0x1FD0BCFF)
                    val startX = max(1, floor((0f - left) / actualPixelW).toInt())
                    val endX = min(canvasWidth - 1, floor((size.width - left) / actualPixelW).toInt() + 1)
                    val lineTop = max(top.toFloat(), 0f)
                    val lineBottom = min((top + totalH).toFloat(), size.height)
                    if (lineBottom > lineTop) {
                        for (x in startX..endX) {
                            val lineX = left + x * actualPixelW
                            drawLine(
                                color = gridColor,
                                start = Offset(lineX, lineTop),
                                end = Offset(lineX, lineBottom),
                                strokeWidth = 1f
                            )
                        }
                    }

                    val startY = max(1, floor((0f - top) / actualPixelH).toInt())
                    val endY = min(canvasHeight - 1, floor((size.height - top) / actualPixelH).toInt() + 1)
                    val lineLeft = max(left.toFloat(), 0f)
                    val lineRight = min((left + totalW).toFloat(), size.width)
                    if (lineRight > lineLeft) {
                        for (y in startY..endY) {
                            val lineY = top + y * actualPixelH
                            drawLine(
                                color = gridColor,
                                start = Offset(lineLeft, lineY),
                                end = Offset(lineRight, lineY),
                                strokeWidth = 1f
                            )
                        }
                    }
                }

                // 5. Active Selection Veil + Animated Marching Ants Outline
                if (selectionVisual != null) {
                    drawImage(
                        image = selectionVisual.veilBitmap,
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        filterQuality = FilterQuality.None
                    )

                    val darkAnts = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), marchingAntsPhase)
                    val lilacAnts = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), marchingAntsPhase + 8f)

                    for (seg in selectionVisual.boundarySegments) {
                        val p0 = Offset(left + seg.x0 * actualPixelW, top + seg.y0 * actualPixelH)
                        val p1 = Offset(left + seg.x1 * actualPixelW, top + seg.y1 * actualPixelH)
                        drawLine(
                            color = Color(0xFF1C1B1F),
                            start = p0,
                            end = p1,
                            strokeWidth = 3f,
                            pathEffect = darkAnts
                        )
                        drawLine(
                            color = Color(0xFFD0BCFF),
                            start = p0,
                            end = p1,
                            strokeWidth = 3f,
                            pathEffect = lilacAnts
                        )
                    }
                }

                // 6. Live Rectangular Selection Preview
                if (selectionPreviewRect != null) {
                    val minX = min(selectionPreviewRect.x0, selectionPreviewRect.x1).coerceIn(0, canvasWidth - 1)
                    val maxX = max(selectionPreviewRect.x0, selectionPreviewRect.x1).coerceIn(0, canvasWidth - 1)
                    val minY = min(selectionPreviewRect.y0, selectionPreviewRect.y1).coerceIn(0, canvasHeight - 1)
                    val maxY = max(selectionPreviewRect.y0, selectionPreviewRect.y1).coerceIn(0, canvasHeight - 1)

                    val rectLeft = left + minX * actualPixelW
                    val rectTop = top + minY * actualPixelH
                    val rectW = (maxX - minX + 1) * actualPixelW
                    val rectH = (maxY - minY + 1) * actualPixelH

                    drawRect(
                        color = Color(0x33D0BCFF),
                        topLeft = Offset(rectLeft, rectTop),
                        size = Size(rectW, rectH)
                    )
                    drawRect(
                        color = Color(0xFFD0BCFF),
                        topLeft = Offset(rectLeft, rectTop),
                        size = Size(rectW, rectH),
                        style = Stroke(
                            width = 2.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), marchingAntsPhase)
                        )
                    )
                }

                // 7. Live Lasso Path Preview
                if (lassoPreviewPoints.isNotEmpty()) {
                    val path = Path().apply {
                        val first = lassoPreviewPoints.first()
                        moveTo(
                            left + (first.first + 0.5f) * actualPixelW,
                            top + (first.second + 0.5f) * actualPixelH
                        )
                        for (i in 1 until lassoPreviewPoints.size) {
                            val pt = lassoPreviewPoints[i]
                            lineTo(
                                left + (pt.first + 0.5f) * actualPixelW,
                                top + (pt.second + 0.5f) * actualPixelH
                            )
                        }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF1C1B1F),
                        style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        color = Color(0xFFD0BCFF),
                        style = Stroke(
                            width = 2.5f,
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), marchingAntsPhase)
                        )
                    )
                }

                // 8. Hovered/Active pixel highlight reticle
                if (hoveredPixel != null) {
                    val (hx, hy) = hoveredPixel
                    if (hx in 0 until canvasWidth && hy in 0 until canvasHeight) {
                        drawRect(
                            color = Color(0xFFD0BCFF),
                            topLeft = Offset(left + hx * actualPixelW, top + hy * actualPixelH),
                            size = Size(actualPixelW, actualPixelH),
                            style = Stroke(width = 2.5f)
                        )
                    }
                }
            }

            // Floating HUD: Layer / Coordinates Pill (top start) & Selection / Grid Toggles (top end)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val hudText = if (hoveredPixel != null) {
                                "X:${hoveredPixel.first}  Y:${hoveredPixel.second}"
                            } else {
                                activeLayerName
                            }
                            Text(
                                text = hudText,
                                style = if (hoveredPixel != null) {
                                    MaterialTheme.typography.labelSmall
                                } else {
                                    MaterialTheme.typography.labelMedium
                                },
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (selection != null) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onClearSelection)
                                .testTag("hud_clear_selection")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SelectAll,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "${selection.selectedCount} px",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Снять выделение",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Grid Toggle Pill
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (showPixelGrid) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onToggleGrid)
                        .testTag("toggle_grid_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (showPixelGrid) Icons.Rounded.GridOn else Icons.Rounded.GridOff,
                            contentDescription = "Сетка пикселей",
                            tint = if (showPixelGrid) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Сетка",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (showPixelGrid) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            // Floating Eyedropper Loupe Badge when sampling
            if (activeTool == EditorTool.EYEDROPPER && eyedropperPreview != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(CheckerDark)
                                .background(Color(eyedropperPreview.argb))
                                .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        )
                        val hex = String.format("#%08X", eyedropperPreview.argb)
                        Text(
                            text = "$hex (${eyedropperPreview.x}, ${eyedropperPreview.y})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

internal data class CanvasDrawMetrics(
    val left: Int,
    val top: Int,
    val totalWidth: Int,
    val totalHeight: Int,
    val actualPixelW: Float,
    val actualPixelH: Float
)

internal fun computeCanvasDrawMetrics(
    surfaceWidth: Float,
    surfaceHeight: Float,
    canvasWidth: Int,
    canvasHeight: Int,
    basePixelSize: Float,
    zoom: Float,
    panOffset: Offset
): CanvasDrawMetrics {
    val safeCanvasW = canvasWidth.coerceAtLeast(1)
    val safeCanvasH = canvasHeight.coerceAtLeast(1)
    val pixelSize = basePixelSize * zoom
    val totalWidth = (safeCanvasW * pixelSize).roundToInt().coerceAtLeast(1)
    val totalHeight = (safeCanvasH * pixelSize).roundToInt().coerceAtLeast(1)
    val left = ((surfaceWidth - totalWidth) / 2f + panOffset.x).roundToInt()
    val top = ((surfaceHeight - totalHeight) / 2f + panOffset.y).roundToInt()
    return CanvasDrawMetrics(
        left = left,
        top = top,
        totalWidth = totalWidth,
        totalHeight = totalHeight,
        actualPixelW = totalWidth.toFloat() / safeCanvasW,
        actualPixelH = totalHeight.toFloat() / safeCanvasH
    )
}

internal fun screenOffsetToPixel(
    pos: Offset,
    metrics: CanvasDrawMetrics,
    canvasWidth: Int,
    canvasHeight: Int,
    clampToBounds: Boolean = false
): Pair<Int, Int>? {
    if (metrics.actualPixelW <= 0.0001f || metrics.actualPixelH <= 0.0001f) return null
    val px = floor((pos.x - metrics.left) / metrics.actualPixelW).toInt()
    val py = floor((pos.y - metrics.top) / metrics.actualPixelH).toInt()
    if (clampToBounds) {
        return px.coerceIn(0, canvasWidth - 1) to py.coerceIn(0, canvasHeight - 1)
    }
    return if (px in 0 until canvasWidth && py in 0 until canvasHeight) {
        px to py
    } else {
        null
    }
}

private data class LayerBitmapEntry(
    val width: Int,
    val height: Int,
    val version: Long,
    val bitmap: Bitmap,
    val imageBitmap: ImageBitmap
)

private data class SelectionEdgeSegment(
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int
)

private class SelectionVisualCache(
    val veilBitmap: ImageBitmap,
    val boundarySegments: List<SelectionEdgeSegment>
)

private fun buildSelectionVisual(selection: PixelSelection): SelectionVisualCache {
    val w = selection.width
    val h = selection.height
    val mask = selection.mask

    val dimColor = 0x550E0D12
    val veilPixels = IntArray(w * h) { idx ->
        if (mask[idx]) 0x00000000 else dimColor
    }
    val veilBitmap = Bitmap.createBitmap(veilPixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()

    // Merge contiguous horizontal and vertical boundary segments for fast marching-ants rendering
    val segments = ArrayList<SelectionEdgeSegment>()

    // Horizontal edges (top of row y, and bottom of last row)
    for (y in 0..h) {
        var runStartX = -1
        for (x in 0 until w) {
            val above = if (y > 0) mask[(y - 1) * w + x] else false
            val below = if (y < h) mask[y * w + x] else false
            val isBoundary = above != below
            if (isBoundary) {
                if (runStartX == -1) runStartX = x
            } else if (runStartX != -1) {
                segments.add(SelectionEdgeSegment(runStartX, y, x, y))
                runStartX = -1
            }
        }
        if (runStartX != -1) {
            segments.add(SelectionEdgeSegment(runStartX, y, w, y))
        }
    }

    // Vertical edges (left of col x, and right of last col)
    for (x in 0..w) {
        var runStartY = -1
        for (y in 0 until h) {
            val left = if (x > 0) mask[y * w + (x - 1)] else false
            val right = if (x < w) mask[y * w + x] else false
            val isBoundary = left != right
            if (isBoundary) {
                if (runStartY == -1) runStartY = y
            } else if (runStartY != -1) {
                segments.add(SelectionEdgeSegment(x, runStartY, x, y))
                runStartY = -1
            }
        }
        if (runStartY != -1) {
            segments.add(SelectionEdgeSegment(x, runStartY, x, h))
        }
    }

    return SelectionVisualCache(veilBitmap = veilBitmap, boundarySegments = segments)
}

private fun createCheckerboardBitmap(width: Int, height: Int): ImageBitmap {
    val w = width.coerceAtLeast(1)
    val h = height.coerceAtLeast(1)
    val dark = CheckerDark.toArgb()
    val light = CheckerLight.toArgb()
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            pixels[y * w + x] = if ((x + y) % 2 == 0) light else dark
        }
    }
    return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
}
