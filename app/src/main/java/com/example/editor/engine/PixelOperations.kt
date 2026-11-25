package com.example.editor.engine

import com.example.editor.model.PixelClipboard
import com.example.editor.model.PixelLayer
import com.example.editor.model.RecolorShadingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PixelOperations {

    const val TRANSPARENT: Int = 0x00000000

    /**
     * Paints or erases a pixel-aligned circular stamp centered at (cx, cy).
     * When [isEraser] is true, writes absolute transparency (alpha = 0).
     * If [selectionMask] is non-null, only pixels inside the selection are modified.
     */
    fun stampBrush(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Int,
        cy: Int,
        brushSize: Int,
        argbColor: Int,
        isEraser: Boolean,
        strokeVisited: BooleanArray? = null,
        selectionMask: BooleanArray? = null
    ): Boolean {
        val clampedSize = brushSize.coerceIn(1, 32)
        var modified = false

        if (clampedSize == 1) {
            if (cx in 0 until width && cy in 0 until height) {
                val idx = cy * width + cx
                if (selectionMask != null && !selectionMask[idx]) return false
                if (strokeVisited == null || !strokeVisited[idx]) {
                    strokeVisited?.set(idx, true)
                    val nextColor = if (isEraser) {
                        TRANSPARENT
                    } else {
                        applyPaintColor(pixels[idx], argbColor)
                    }
                    if (pixels[idx] != nextColor) {
                        pixels[idx] = nextColor
                        modified = true
                    }
                }
            }
            return modified
        }

        val radius = (clampedSize - 1) / 2.0f
        val radiusSq = radius * radius + 0.35f
        val minOffset = -((clampedSize) / 2)
        val maxOffset = (clampedSize - 1) / 2

        for (dy in minOffset..maxOffset) {
            val py = cy + dy
            if (py !in 0 until height) continue
            for (dx in minOffset..maxOffset) {
                val px = cx + dx
                if (px !in 0 until width) continue

                val distSq = dx * dx + dy * dy
                if (distSq <= radiusSq) {
                    val idx = py * width + px
                    if (selectionMask != null && !selectionMask[idx]) continue
                    if (strokeVisited != null && strokeVisited[idx]) continue
                    strokeVisited?.set(idx, true)

                    val nextColor = if (isEraser) {
                        TRANSPARENT
                    } else {
                        applyPaintColor(pixels[idx], argbColor)
                    }
                    if (pixels[idx] != nextColor) {
                        pixels[idx] = nextColor
                        modified = true
                    }
                }
            }
        }
        return modified
    }

    /**
     * Gap-free Bresenham line interpolation between (x0, y0) and (x1, y1).
     */
    fun drawLine(
        pixels: IntArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        brushSize: Int,
        argbColor: Int,
        isEraser: Boolean,
        strokeVisited: BooleanArray? = null,
        selectionMask: BooleanArray? = null
    ): Boolean {
        var currX = x0
        var currY = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        var modified = false

        while (true) {
            if (stampBrush(
                    pixels, width, height, currX, currY,
                    brushSize, argbColor, isEraser, strokeVisited, selectionMask
                )
            ) {
                modified = true
            }
            if (currX == x1 && currY == y1) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                currX += sx
            }
            if (e2 <= dx) {
                err += dx
                currY += sy
            }
        }
        return modified
    }

    /**
     * Iterative BFS flood fill respecting pixel boundaries, optional color tolerance,
     * and optional active [selectionMask].
     */
    fun floodFill(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        fillColor: Int,
        tolerance: Int = 0,
        selectionMask: BooleanArray? = null
    ): Boolean {
        if (startX !in 0 until width || startY !in 0 until height) return false

        val startIdx = startY * width + startX
        if (selectionMask != null && !selectionMask[startIdx]) return false

        val targetColor = pixels[startIdx]
        if (tolerance == 0 && targetColor == fillColor) return false
        if (colorsMatch(targetColor, fillColor, 0) && tolerance == 0) return false

        val total = width * height
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        queue[tail++] = startIdx
        visited[startIdx] = true
        var modified = false

        while (head < tail) {
            val idx = queue[head++]
            if (selectionMask != null && !selectionMask[idx]) continue
            val current = pixels[idx]
            if (!colorsMatch(current, targetColor, tolerance)) continue

            if (pixels[idx] != fillColor) {
                pixels[idx] = fillColor
                modified = true
            }

            val x = idx % width
            val y = idx / width

            if (x > 0) {
                val left = idx - 1
                if (!visited[left] && (selectionMask == null || selectionMask[left]) &&
                    colorsMatch(pixels[left], targetColor, tolerance)
                ) {
                    visited[left] = true
                    queue[tail++] = left
                }
            }
            if (x + 1 < width) {
                val right = idx + 1
                if (!visited[right] && (selectionMask == null || selectionMask[right]) &&
                    colorsMatch(pixels[right], targetColor, tolerance)
                ) {
                    visited[right] = true
                    queue[tail++] = right
                }
            }
            if (y > 0) {
                val up = idx - width
                if (!visited[up] && (selectionMask == null || selectionMask[up]) &&
                    colorsMatch(pixels[up], targetColor, tolerance)
                ) {
                    visited[up] = true
                    queue[tail++] = up
                }
            }
            if (y + 1 < height) {
                val down = idx + width
                if (!visited[down] && (selectionMask == null || selectionMask[down]) &&
                    colorsMatch(pixels[down], targetColor, tolerance)
                ) {
                    visited[down] = true
                    queue[tail++] = down
                }
            }
        }
        return modified
    }

    // --- Selection Mask & Pixel Operations ---

    fun createRectSelectionMask(
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ): BooleanArray {
        val mask = BooleanArray(width * height)
        val minX = min(x0, x1).coerceIn(0, width - 1)
        val maxX = max(x0, x1).coerceIn(0, width - 1)
        val minY = min(y0, y1).coerceIn(0, height - 1)
        val maxY = max(y0, y1).coerceIn(0, height - 1)
        for (y in minY..maxY) {
            val rowOffset = y * width
            for (x in minX..maxX) {
                mask[rowOffset + x] = true
            }
        }
        return mask
    }

    /**
     * Builds a closed pixel mask from freehand lasso vertices using Bresenham boundary
     * segments plus point-in-polygon scanline filling.
     */
    fun createLassoSelectionMask(
        width: Int,
        height: Int,
        points: List<Pair<Int, Int>>
    ): BooleanArray {
        val mask = BooleanArray(width * height)
        if (points.isEmpty()) return mask
        if (points.size == 1) {
            val (px, py) = points[0]
            if (px in 0 until width && py in 0 until height) {
                mask[py * width + px] = true
            }
            return mask
        }
        if (points.size == 2) {
            markMaskLine(mask, width, height, points[0].first, points[0].second, points[1].first, points[1].second)
            return mask
        }

        // 1. Mark closed perimeter pixels so thin edges are never lost
        for (i in points.indices) {
            val p0 = points[i]
            val p1 = points[(i + 1) % points.size]
            markMaskLine(mask, width, height, p0.first, p0.second, p1.first, p1.second)
        }

        // 2. Scanline polygon interior fill using pixel centers (+0.5f)
        val xs = FloatArray(points.size)
        for (y in 0 until height) {
            val cy = y + 0.5f
            var nodeCount = 0
            var j = points.lastIndex
            for (i in points.indices) {
                val yi = points[i].second + 0.5f
                val yj = points[j].second + 0.5f
                val xi = points[i].first + 0.5f
                val xj = points[j].first + 0.5f
                if ((yi < cy && yj >= cy) || (yj < cy && yi >= cy)) {
                    xs[nodeCount++] = xi + (cy - yi) / (yj - yi) * (xj - xi)
                }
                j = i
            }
            if (nodeCount >= 2) {
                xs.sort(0, nodeCount)
                var k = 0
                while (k + 1 < nodeCount) {
                    val startX = kotlin.math.floor(xs[k]).toInt().coerceIn(0, width - 1)
                    val endX = kotlin.math.floor(xs[k + 1]).toInt().coerceIn(0, width - 1)
                    val rowOffset = y * width
                    for (x in startX..endX) {
                        mask[rowOffset + x] = true
                    }
                    k += 2
                }
            }
        }
        return mask
    }

    private fun markMaskLine(
        mask: BooleanArray,
        width: Int,
        height: Int,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int
    ) {
        var cx = x0.coerceIn(0, width - 1)
        var cy = y0.coerceIn(0, height - 1)
        val tx = x1.coerceIn(0, width - 1)
        val ty = y1.coerceIn(0, height - 1)
        val dx = abs(tx - cx)
        val dy = -abs(ty - cy)
        val sx = if (cx < tx) 1 else -1
        val sy = if (cy < ty) 1 else -1
        var err = dx + dy
        while (true) {
            mask[cy * width + cx] = true
            if (cx == tx && cy == ty) break
            val e2 = 2 * err
            if (e2 >= dy) {
                err += dy
                cx += sx
            }
            if (e2 <= dx) {
                err += dx
                cy += sy
            }
        }
    }

    fun createMagicWandMask(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        tolerance: Int = 0
    ): BooleanArray {
        if (width <= 0 || height <= 0) return BooleanArray(0)
        val total = width * height
        val mask = BooleanArray(total)
        if (pixels.size < total) return mask
        if (startX !in 0 until width || startY !in 0 until height) return mask

        val startIdx = startY * width + startX
        val targetColor = pixels[startIdx]
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        mask[startIdx] = true
        queue[tail++] = startIdx

        while (head < tail) {
            val idx = queue[head++]
            val x = idx % width
            val y = idx / width

            val minNx = max(0, x - 1)
            val maxNx = min(width - 1, x + 1)
            val minNy = max(0, y - 1)
            val maxNy = min(height - 1, y + 1)

            for (ny in minNy..maxNy) {
                val rowOffset = ny * width
                for (nx in minNx..maxNx) {
                    if (nx == x && ny == y) continue
                    val nIdx = rowOffset + nx
                    if (!mask[nIdx] && wandColorsMatch(pixels[nIdx], targetColor, tolerance)) {
                        mask[nIdx] = true
                        queue[tail++] = nIdx
                    }
                }
            }
        }
        return mask
    }

    fun invertSelectionMask(mask: BooleanArray): BooleanArray {
        return BooleanArray(mask.size) { i -> !mask[i] }
    }

    fun clearSelectedPixels(pixels: IntArray, mask: BooleanArray): Boolean {
        var modified = false
        val count = minOf(pixels.size, mask.size)
        for (i in 0 until count) {
            if (mask[i] && pixels[i] != TRANSPARENT) {
                pixels[i] = TRANSPARENT
                modified = true
            }
        }
        return modified
    }

    /**
     * Recolors non-transparent pixels inside [mask] toward [targetArgb] while preserving
     * light & shadow shading and each pixel's original alpha channel.
     */
    fun recolorSelectedPixels(
        pixels: IntArray,
        mask: BooleanArray,
        targetArgb: Int,
        mode: RecolorShadingMode = RecolorShadingMode.LUMINANCE,
        strength: Float = 1f,
        brightnessShift: Float = 0f,
        recolorWhite: Boolean = true
    ): Boolean {
        var modified = false
        val count = minOf(pixels.size, mask.size)
        for (i in 0 until count) {
            if (!mask[i]) continue
            val src = pixels[i]
            if ((src ushr 24) == 0) continue
            val next = recolorPixelWithShading(src, targetArgb, mode, strength, brightnessShift, recolorWhite)
            if (next != src) {
                pixels[i] = next
                modified = true
            }
        }
        return modified
    }

    /**
     * Applies [targetArgb] chromaticity/tint to [srcArgb] while preserving original alpha
     * and shading according to [mode], with optional monotonic [brightnessShift] in [-1f..1f].
     * When [recolorWhite] is true, pure white pixels also receive the target tint instead of
     * clipping back to pure white in luminance-preserving mode.
     */
    fun recolorPixelWithShading(
        srcArgb: Int,
        targetArgb: Int,
        mode: RecolorShadingMode = RecolorShadingMode.LUMINANCE,
        strength: Float = 1f,
        brightnessShift: Float = 0f,
        recolorWhite: Boolean = true
    ): Int {
        val srcAlpha = (srcArgb ushr 24) and 0xFF
        if (srcAlpha == 0) return srcArgb

        val targetAlpha = (targetArgb ushr 24) and 0xFF
        val effStrength = strength.coerceIn(0f, 1f) * (targetAlpha / 255f)
        if (effStrength <= 0.0001f) return srcArgb

        val clampedShift = brightnessShift.coerceIn(-1f, 1f)

        val sr = ((srcArgb ushr 16) and 0xFF) / 255f
        val sg = ((srcArgb ushr 8) and 0xFF) / 255f
        val sb = (srcArgb and 0xFF) / 255f

        val tr = ((targetArgb ushr 16) and 0xFF) / 255f
        val tg = ((targetArgb ushr 8) and 0xFF) / 255f
        val tb = (targetArgb and 0xFF) / 255f

        val srcLum = 0.299f * sr + 0.587f * sg + 0.114f * sb
        if (!recolorWhite && srcLum >= 0.985f && clampedShift == 0f) {
            return srcArgb
        }

        val recoloredR: Float
        val recoloredG: Float
        val recoloredB: Float

        when (mode) {
            RecolorShadingMode.LUMINANCE -> {
                // Rec. 601 perceived luminance preservation (SetLum + ClipColor)
                val targetLum = 0.299f * tr + 0.587f * tg + 0.114f * tb
                val baseLum = if (recolorWhite && targetLum < 0.999f) {
                    // Scale [0..1] slightly below 1.0 when target is chromatic so pure white
                    // receives visible color instead of clipping back to (1, 1, 1) white.
                    val maxLum = (targetLum + (1f - targetLum) * 0.22f).coerceIn(targetLum, 1f)
                    srcLum * maxLum
                } else {
                    srcLum
                }
                val desiredLum = when {
                    clampedShift > 0f -> baseLum + clampedShift * (1f - baseLum)
                    clampedShift < 0f -> baseLum * (1f + clampedShift)
                    else -> baseLum
                }.coerceIn(0f, 1f)

                val d = desiredLum - targetLum

                var r = tr + d
                var g = tg + d
                var b = tb + d

                val minC = minOf(r, g, b)
                val maxC = maxOf(r, g, b)
                if (minC < 0f) {
                    val denom = (desiredLum - minC).coerceAtLeast(1e-5f)
                    r = desiredLum + ((r - desiredLum) * desiredLum) / denom
                    g = desiredLum + ((g - desiredLum) * desiredLum) / denom
                    b = desiredLum + ((b - desiredLum) * desiredLum) / denom
                }
                if (maxC > 1f) {
                    val denom = (maxC - desiredLum).coerceAtLeast(1e-5f)
                    r = desiredLum + ((r - desiredLum) * (1f - desiredLum)) / denom
                    g = desiredLum + ((g - desiredLum) * (1f - desiredLum)) / denom
                    b = desiredLum + ((b - desiredLum) * (1f - desiredLum)) / denom
                }

                recoloredR = r.coerceIn(0f, 1f)
                recoloredG = g.coerceIn(0f, 1f)
                recoloredB = b.coerceIn(0f, 1f)
            }

            RecolorShadingMode.TINT_MULTIPLY -> {
                var r = (sr * tr).coerceIn(0f, 1f)
                var g = (sg * tg).coerceIn(0f, 1f)
                var b = (sb * tb).coerceIn(0f, 1f)
                if (clampedShift > 0f) {
                    r += clampedShift * (1f - r)
                    g += clampedShift * (1f - g)
                    b += clampedShift * (1f - b)
                } else if (clampedShift < 0f) {
                    val scale = 1f + clampedShift
                    r *= scale
                    g *= scale
                    b *= scale
                }
                recoloredR = r.coerceIn(0f, 1f)
                recoloredG = g.coerceIn(0f, 1f)
                recoloredB = b.coerceIn(0f, 1f)
            }
        }

        val outR = ((sr + (recoloredR - sr) * effStrength) * 255f).roundToInt().coerceIn(0, 255)
        val outG = ((sg + (recoloredG - sg) * effStrength) * 255f).roundToInt().coerceIn(0, 255)
        val outB = ((sb + (recoloredB - sb) * effStrength) * 255f).roundToInt().coerceIn(0, 255)

        return (srcAlpha shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    /**
     * Lifts pixels selected by [srcMask] out of [srcPixels] and places them shifted by (dx, dy),
     * returning the updated pixel buffer and shifted selection mask.
     */
    fun translateSelectedPixels(
        srcPixels: IntArray,
        srcMask: BooleanArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int
    ): Pair<IntArray, BooleanArray> {
        val resultPixels = srcPixels.copyOf()
        val resultMask = BooleanArray(width * height)

        // Erase lifted region from base
        for (i in resultPixels.indices) {
            if (srcMask[i]) {
                resultPixels[i] = TRANSPARENT
            }
        }

        // Place lifted pixels at shifted coordinates
        for (y in 0 until height) {
            val ny = y + dy
            if (ny !in 0 until height) continue
            for (x in 0 until width) {
                val nx = x + dx
                if (nx !in 0 until width) continue
                val srcIdx = y * width + x
                if (!srcMask[srcIdx]) continue

                val dstIdx = ny * width + nx
                resultMask[dstIdx] = true
                val liftedColor = srcPixels[srcIdx]
                if ((liftedColor ushr 24) != 0) {
                    resultPixels[dstIdx] = blendSrcOver(resultPixels[dstIdx], liftedColor, 1f)
                }
            }
        }
        return resultPixels to resultMask
    }

    fun copySelectionToClipboard(
        pixels: IntArray,
        mask: BooleanArray,
        width: Int,
        height: Int
    ): PixelClipboard? {
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (mask[y * width + x]) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < minX || maxY < minY) return null

        val cw = maxX - minX + 1
        val ch = maxY - minY + 1
        val clipPixels = IntArray(cw * ch)
        val clipMask = BooleanArray(cw * ch)
        for (y in 0 until ch) {
            for (x in 0 until cw) {
                val srcIdx = (minY + y) * width + (minX + x)
                val dstIdx = y * cw + x
                if (mask[srcIdx]) {
                    clipMask[dstIdx] = true
                    clipPixels[dstIdx] = pixels[srcIdx]
                }
            }
        }
        return PixelClipboard(cw, ch, clipPixels, clipMask)
    }

    fun pasteClipboardOntoPixels(
        dstPixels: IntArray,
        dstWidth: Int,
        dstHeight: Int,
        clipboard: PixelClipboard
    ): BooleanArray {
        val newMask = BooleanArray(dstWidth * dstHeight)
        val offsetX = ((dstWidth - clipboard.width) / 2).coerceAtLeast(0)
        val offsetY = ((dstHeight - clipboard.height) / 2).coerceAtLeast(0)

        for (y in 0 until clipboard.height) {
            val ty = offsetY + y
            if (ty !in 0 until dstHeight) continue
            for (x in 0 until clipboard.width) {
                val tx = offsetX + x
                if (tx !in 0 until dstWidth) continue
                val srcIdx = y * clipboard.width + x
                if (!clipboard.mask[srcIdx]) continue

                val dstIdx = ty * dstWidth + tx
                newMask[dstIdx] = true
                val color = clipboard.pixels[srcIdx]
                if ((color ushr 24) != 0) {
                    dstPixels[dstIdx] = blendSrcOver(dstPixels[dstIdx], color, 1f)
                }
            }
        }
        return newMask
    }

    /**
     * Composites visible layers bottom-to-top onto a transparent ARGB_8888 IntArray.
     */
    fun compositeLayers(
        layers: List<PixelLayer>,
        width: Int,
        height: Int
    ): IntArray {
        val output = IntArray(width * height) { TRANSPARENT }
        for (layer in layers) {
            if (!layer.isVisible || layer.opacity <= 0.001f) continue
            val srcPixels = layer.pixels
            val count = minOf(output.size, srcPixels.size)
            val layerAlpha = layer.opacity.coerceIn(0f, 1f)

            for (i in 0 until count) {
                val src = srcPixels[i]
                if ((src ushr 24) == 0) continue
                output[i] = blendSrcOver(output[i], src, layerAlpha)
            }
        }
        return output
    }

    /**
     * Composites visible layers into an opaque RGB buffer (alpha = 255 everywhere).
     * Reveals RGB texture data even in semi-transparent or alpha-masked pixels.
     */
    fun compositeLayersOpaque(
        layers: List<PixelLayer>,
        width: Int,
        height: Int
    ): IntArray {
        val opaqueBlack = 0xFF000000.toInt()
        val output = IntArray(width * height) { opaqueBlack }
        var isFirstVisibleLayer = true

        for (layer in layers) {
            if (!layer.isVisible || layer.opacity <= 0.001f) continue
            val srcPixels = layer.pixels
            val count = minOf(output.size, srcPixels.size)
            val layerAlpha = layer.opacity.coerceIn(0f, 1f)

            for (i in 0 until count) {
                val src = srcPixels[i]
                val srcAlpha = (src ushr 24) and 0xFF
                val srcRgb = src and 0x00FFFFFF

                if (isFirstVisibleLayer) {
                    if (srcAlpha > 0 || srcRgb != 0) {
                        output[i] = opaqueBlack or srcRgb
                    }
                } else if (srcAlpha > 0) {
                    output[i] = blendSrcOver(output[i], src, layerAlpha) or opaqueBlack
                }
            }
            isFirstVisibleLayer = false
        }
        return output
    }

    /**
     * Merges [upper] down into [lower], baking both layer opacities accurately into
     * a single pixel buffer with resulting opacity = 1.0f.
     */
    fun mergeDown(upper: PixelLayer, lower: PixelLayer): PixelLayer {
        val size = lower.width * lower.height
        val merged = IntArray(size) { TRANSPARENT }
        val lowerOpacity = if (lower.isVisible) lower.opacity.coerceIn(0f, 1f) else 0f
        val upperOpacity = if (upper.isVisible) upper.opacity.coerceIn(0f, 1f) else 0f

        for (i in 0 until size) {
            var acc = TRANSPARENT
            val lp = lower.pixels[i]
            if (lowerOpacity > 0.001f && (lp ushr 24) != 0) {
                acc = scaleAlpha(lp, lowerOpacity)
            }
            val up = upper.pixels[i]
            if (upperOpacity > 0.001f && (up ushr 24) != 0) {
                acc = blendSrcOver(acc, up, upperOpacity)
            }
            merged[i] = acc
        }

        return lower.copy(
            pixels = merged,
            opacity = 1.0f,
            isVisible = lower.isVisible || upper.isVisible,
            version = lower.version + 1L
        )
    }

    fun blendSrcOver(dstArgb: Int, srcArgb: Int, srcOpacity: Float = 1f): Int {
        val srcRawA = (srcArgb ushr 24) and 0xFF
        val srcA = (srcRawA / 255f) * srcOpacity.coerceIn(0f, 1f)
        if (srcA <= 0f) return dstArgb

        val dstA = ((dstArgb ushr 24) and 0xFF) / 255f
        if (srcA >= 0.999f || dstA <= 0f) {
            val outA = (srcA * 255f).roundToInt().coerceIn(0, 255)
            return (outA shl 24) or (srcArgb and 0x00FFFFFF)
        }

        val sr = (srcArgb ushr 16) and 0xFF
        val sg = (srcArgb ushr 8) and 0xFF
        val sb = srcArgb and 0xFF

        val dr = (dstArgb ushr 16) and 0xFF
        val dg = (dstArgb ushr 8) and 0xFF
        val db = dstArgb and 0xFF

        val invSrcA = 1f - srcA
        val outA = srcA + dstA * invSrcA
        if (outA <= 0.0001f) return TRANSPARENT

        val outR = ((sr * srcA + dr * dstA * invSrcA) / outA).roundToInt().coerceIn(0, 255)
        val outG = ((sg * srcA + dg * dstA * invSrcA) / outA).roundToInt().coerceIn(0, 255)
        val outB = ((sb * srcA + db * dstA * invSrcA) / outA).roundToInt().coerceIn(0, 255)
        val outAInt = (outA * 255f).roundToInt().coerceIn(0, 255)

        return (outAInt shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    private fun applyPaintColor(dstArgb: Int, srcArgb: Int): Int {
        val alpha = (srcArgb ushr 24) and 0xFF
        return when {
            alpha >= 255 -> srcArgb
            alpha == 0 -> TRANSPARENT
            else -> blendSrcOver(dstArgb, srcArgb, 1f)
        }
    }

    private fun scaleAlpha(argb: Int, opacity: Float): Int {
        val a = (((argb ushr 24) and 0xFF) * opacity).roundToInt().coerceIn(0, 255)
        return if (a == 0) TRANSPARENT else (a shl 24) or (argb and 0x00FFFFFF)
    }

    private fun colorsMatch(c1: Int, c2: Int, tolerance: Int): Boolean {
        if (c1 == c2) return true
        val a1 = (c1 ushr 24) and 0xFF
        val a2 = (c2 ushr 24) and 0xFF
        // Treat two completely transparent pixels as identical regardless of stale RGB bits
        if (a1 == 0 && a2 == 0) return true
        if (tolerance == 0) return false

        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF

        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF

        val maxDiff = max(max(abs(a1 - a2), abs(r1 - r2)), max(abs(g1 - g2), abs(b1 - b2)))
        return maxDiff <= tolerance
    }

    /**
     * Perceptual redmean color comparison for Magic Wand selection, comparing candidate pixel
     * strictly against the tapped seed pixel while stopping cleanly at transparency boundaries.
     */
    private fun wandColorsMatch(candidate: Int, target: Int, tolerance: Int): Boolean {
        if (candidate == target) return true
        val a1 = (candidate ushr 24) and 0xFF
        val a2 = (target ushr 24) and 0xFF
        if (a1 == 0 && a2 == 0) return true
        if (a1 == 0 || a2 == 0) return false
        if (tolerance <= 0) return false
        if (abs(a1 - a2) > tolerance) return false

        val r1 = (candidate ushr 16) and 0xFF
        val g1 = (candidate ushr 8) and 0xFF
        val b1 = candidate and 0xFF

        val r2 = (target ushr 16) and 0xFF
        val g2 = (target ushr 8) and 0xFF
        val b2 = target and 0xFF

        val rMean = (r1 + r2) * 0.5f
        val dr = (r1 - r2).toFloat()
        val dg = (g1 - g2).toFloat()
        val db = (b1 - b2).toFloat()
        val wr = 2f + rMean / 256f
        val wg = 4f
        val wb = 2f + (255f - rMean) / 256f
        val distance = sqrt((wr * dr * dr + wg * dg * dg + wb * db * db) / 9f)
        return distance <= tolerance.toFloat()
    }
}
