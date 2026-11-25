package com.example.bgremover.engine

import android.graphics.Bitmap
import com.example.bgremover.model.BgRemovalMethod
import com.example.bgremover.model.BgRemoverSettings
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class BgRemovalResult(
    val pixels: IntArray,
    val detectedBgColorArgb: Int,
    val transparentPercent: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BgRemovalResult) return false
        return detectedBgColorArgb == other.detectedBgColorArgb &&
            transparentPercent == other.transparentPercent &&
            pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = detectedBgColorArgb
        result = 31 * result + transparentPercent
        result = 31 * result + pixels.contentHashCode()
        return result
    }
}

object BgRemoverEngine {

    private const val COLOR_WHITE_ARGB: Int = 0xFFFFFFFF.toInt()

    private fun packOpaqueRgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    fun detectDominantBorderColor(pixels: IntArray, width: Int, height: Int): Int {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return COLOR_WHITE_ARGB

        val bucketCounts = HashMap<Int, Int>()
        val bucketRedSum = HashMap<Int, Long>()
        val bucketGreenSum = HashMap<Int, Long>()
        val bucketBlueSum = HashMap<Int, Long>()

        fun sample(x: Int, y: Int, weight: Int) {
            val c = pixels[y * width + x]
            val a = (c ushr 24) and 0xFF
            if (a < 24) return
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            bucketCounts[key] = (bucketCounts[key] ?: 0) + weight
            bucketRedSum[key] = (bucketRedSum[key] ?: 0L) + r.toLong() * weight
            bucketGreenSum[key] = (bucketGreenSum[key] ?: 0L) + g.toLong() * weight
            bucketBlueSum[key] = (bucketBlueSum[key] ?: 0L) + b.toLong() * weight
        }

        val ring = max(1, min(width, height) / 48).coerceAtMost(4)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val onBorder = x < ring || x >= width - ring || y < ring || y >= height - ring
                if (onBorder) {
                    val isCorner = (x < ring * 2 || x >= width - ring * 2) &&
                        (y < ring * 2 || y >= height - ring * 2)
                    sample(x, y, if (isCorner) 2 else 1)
                }
            }
        }

        val bestKey = bucketCounts.maxByOrNull { it.value }?.key
        if (bestKey != null) {
            val count = bucketCounts[bestKey] ?: 1
            val r = ((bucketRedSum[bestKey] ?: 0L) / count).toInt().coerceIn(0, 255)
            val g = ((bucketGreenSum[bestKey] ?: 0L) / count).toInt().coerceIn(0, 255)
            val b = ((bucketBlueSum[bestKey] ?: 0L) / count).toInt().coerceIn(0, 255)
            return packOpaqueRgb(r, g, b)
        }

        return COLOR_WHITE_ARGB
    }

    fun removeBackground(
        originalPixels: IntArray,
        width: Int,
        height: Int,
        settings: BgRemoverSettings
    ): BgRemovalResult {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val total = safeW * safeH
        val normalized = settings.normalized()
        val detectedBg = detectDominantBorderColor(originalPixels, safeW, safeH)
        val targetBg = normalized.targetColorArgb ?: detectedBg

        if (originalPixels.size < total) {
            return BgRemovalResult(originalPixels.copyOf(), detectedBg, 0)
        }

        val result = when (normalized.method) {
            BgRemovalMethod.EDGE_FLOOD -> removeEdgeFlood(
                originalPixels = originalPixels,
                width = safeW,
                height = safeH,
                bgColor = targetBg,
                sensitivity = normalized.sensitivity,
                edgeSoftness = normalized.edgeSoftness,
                removeInnerHoles = normalized.removeInnerHoles
            )
            BgRemovalMethod.COLOR_KEY -> removeColorKey(
                originalPixels = originalPixels,
                width = safeW,
                height = safeH,
                bgColor = targetBg,
                sensitivity = normalized.sensitivity,
                edgeSoftness = normalized.edgeSoftness
            )
            BgRemovalMethod.SMART_SUBJECT -> removeSmartSubject(
                originalPixels = originalPixels,
                width = safeW,
                height = safeH,
                explicitBgColor = normalized.targetColorArgb,
                sensitivity = normalized.sensitivity,
                edgeSoftness = normalized.edgeSoftness,
                removeInnerHoles = normalized.removeInnerHoles
            )
        }

        val pct = computeTransparentPercent(result)
        return BgRemovalResult(
            pixels = result,
            detectedBgColorArgb = targetBg,
            transparentPercent = pct
        )
    }

    private fun removeEdgeFlood(
        originalPixels: IntArray,
        width: Int,
        height: Int,
        bgColor: Int,
        sensitivity: Int,
        edgeSoftness: Int,
        removeInnerHoles: Boolean
    ): IntArray {
        val total = width * height
        val threshold = sensitivityToDistance(sensitivity)
        val featherBand = softnessToFeather(threshold, edgeSoftness)
        val outerThreshold = threshold + featherBand

        val bgR = (bgColor ushr 16) and 0xFF
        val bgG = (bgColor ushr 8) and 0xFF
        val bgB = bgColor and 0xFF

        val distances = FloatArray(total)
        val bgMask = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0

        for (i in 0 until total) {
            val c = originalPixels[i]
            val a = (c ushr 24) and 0xFF
            distances[i] = if (a < 8) 0f else perceptualDistance(c, bgR, bgG, bgB)
        }

        fun enqueueIfBg(idx: Int, maxDist: Float) {
            if (!bgMask[idx] && distances[idx] <= maxDist) {
                bgMask[idx] = true
                queue[tail++] = idx
            }
        }

        for (x in 0 until width) {
            enqueueIfBg(x, threshold)
            enqueueIfBg((height - 1) * width + x, threshold)
        }
        for (y in 0 until height) {
            enqueueIfBg(y * width, threshold)
            enqueueIfBg(y * width + (width - 1), threshold)
        }

        while (head < tail) {
            val idx = queue[head++]
            val x = idx % width
            val y = idx / width

            if (x > 0) enqueueIfBg(idx - 1, threshold)
            if (x + 1 < width) enqueueIfBg(idx + 1, threshold)
            if (y > 0) enqueueIfBg(idx - width, threshold)
            if (y + 1 < height) enqueueIfBg(idx + width, threshold)
        }

        if (removeInnerHoles) {
            val innerLimit = threshold * 0.85f
            for (i in 0 until total) {
                if (!bgMask[i] && distances[i] <= innerLimit) {
                    bgMask[i] = true
                }
            }
        }

        val out = originalPixels.copyOf()
        for (i in 0 until total) {
            val c = originalPixels[i]
            val origA = (c ushr 24) and 0xFF
            if (origA == 0) {
                out[i] = 0
                continue
            }
            if (bgMask[i]) {
                out[i] = 0
                continue
            }

            // Feather boundary pixels adjacent to removed background
            if (featherBand > 0.5f && distances[i] < outerThreshold && hasBgNeighbor(bgMask, i, width, height)) {
                val factor = ((distances[i] - threshold) / featherBand).coerceIn(0.15f, 1f)
                val newA = (origA * factor).roundToInt().coerceIn(0, 255)
                out[i] = decontaminateEdgePixel(c, bgR, bgG, bgB, factor, newA)
            }
        }

        if (edgeSoftness > 0) {
            applyEdgeAlphaFeather(out, bgMask, width, height, edgeSoftness, bgR, bgG, bgB)
        }

        return out
    }

    private fun removeColorKey(
        originalPixels: IntArray,
        width: Int,
        height: Int,
        bgColor: Int,
        sensitivity: Int,
        edgeSoftness: Int
    ): IntArray {
        val total = width * height
        val threshold = sensitivityToDistance(sensitivity)
        val featherBand = softnessToFeather(threshold, edgeSoftness).coerceAtLeast(1f)
        val bgR = (bgColor ushr 16) and 0xFF
        val bgG = (bgColor ushr 8) and 0xFF
        val bgB = bgColor and 0xFF

        val out = IntArray(total)
        for (i in 0 until total) {
            val c = originalPixels[i]
            val origA = (c ushr 24) and 0xFF
            if (origA == 0) {
                out[i] = 0
                continue
            }
            val dist = perceptualDistance(c, bgR, bgG, bgB)
            when {
                dist <= threshold -> out[i] = 0
                edgeSoftness > 0 && dist < threshold + featherBand -> {
                    val t = ((dist - threshold) / featherBand).coerceIn(0f, 1f)
                    val newA = (origA * t).roundToInt().coerceIn(0, 255)
                    out[i] = if (newA == 0) 0 else decontaminateEdgePixel(c, bgR, bgG, bgB, t, newA)
                }
                else -> out[i] = c
            }
        }
        return out
    }

    private fun removeSmartSubject(
        originalPixels: IntArray,
        width: Int,
        height: Int,
        explicitBgColor: Int?,
        sensitivity: Int,
        edgeSoftness: Int,
        removeInnerHoles: Boolean
    ): IntArray {
        val total = width * height
        val borderPalette = collectBorderPalette(originalPixels, width, height, explicitBgColor)
        val baseThreshold = sensitivityToDistance(sensitivity) * 1.15f
        val featherBand = softnessToFeather(baseThreshold, edgeSoftness)

        val minDistToBorder = FloatArray(total)
        val rawBg = BooleanArray(total)
        val invHalfW = 1f / max(1f, width * 0.5f)
        val invHalfH = 1f / max(1f, height * 0.5f)

        for (y in 0 until height) {
            val dy = (y - height * 0.5f) * invHalfH
            for (x in 0 until width) {
                val idx = y * width + x
                val c = originalPixels[idx]
                val a = (c ushr 24) and 0xFF
                if (a < 8) {
                    minDistToBorder[idx] = 0f
                    rawBg[idx] = true
                    continue
                }
                var bestDist = Float.MAX_VALUE
                for (bg in borderPalette) {
                    val br = (bg ushr 16) and 0xFF
                    val bgG = (bg ushr 8) and 0xFF
                    val bb = bg and 0xFF
                    val d = perceptualDistance(c, br, bgG, bb)
                    if (d < bestDist) bestDist = d
                }
                minDistToBorder[idx] = bestDist

                // Spatial prior: outer ring is more permissive to remove, center object is more protected
                val dx = (x - width * 0.5f) * invHalfW
                val radial = sqrt(dx * dx + dy * dy).coerceIn(0f, 1.42f)
                val spatialMultiplier = 0.78f + radial * 0.36f
                if (bestDist <= baseThreshold * spatialMultiplier) {
                    rawBg[idx] = true
                }
            }
        }

        // Flood from borders through rawBg pixels so interior details of the subject stay solid unless removeInnerHoles is enabled
        val bgMask = BooleanArray(total)
        if (removeInnerHoles) {
            System.arraycopy(rawBg, 0, bgMask, 0, total)
        } else {
            val queue = IntArray(total)
            var head = 0
            var tail = 0
            fun seed(idx: Int) {
                if (rawBg[idx] && !bgMask[idx]) {
                    bgMask[idx] = true
                    queue[tail++] = idx
                }
            }
            for (x in 0 until width) {
                seed(x)
                seed((height - 1) * width + x)
            }
            for (y in 0 until height) {
                seed(y * width)
                seed(y * width + (width - 1))
            }
            while (head < tail) {
                val idx = queue[head++]
                val x = idx % width
                val y = idx / width
                if (x > 0) seed(idx - 1)
                if (x + 1 < width) seed(idx + 1)
                if (y > 0) seed(idx - width)
                if (y + 1 < height) seed(idx + width)
            }
        }

        // Morphological despeckle: remove isolated 1px foreground noise inside background
        val cleanedBg = bgMask.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                if (!bgMask[idx]) {
                    var bgNeighbors = 0
                    if (bgMask[idx - 1]) bgNeighbors++
                    if (bgMask[idx + 1]) bgNeighbors++
                    if (bgMask[idx - width]) bgNeighbors++
                    if (bgMask[idx + width]) bgNeighbors++
                    if (bgNeighbors >= 4 && minDistToBorder[idx] <= baseThreshold * 1.35f) {
                        cleanedBg[idx] = true
                    }
                }
            }
        }

        val primaryBg = borderPalette.firstOrNull() ?: COLOR_WHITE_ARGB
        val pR = (primaryBg ushr 16) and 0xFF
        val pG = (primaryBg ushr 8) and 0xFF
        val pB = primaryBg and 0xFF

        val out = originalPixels.copyOf()
        for (i in 0 until total) {
            val c = originalPixels[i]
            val origA = (c ushr 24) and 0xFF
            if (origA == 0 || cleanedBg[i]) {
                out[i] = 0
            } else if (featherBand > 0.5f && minDistToBorder[i] < baseThreshold + featherBand &&
                hasBgNeighbor(cleanedBg, i, width, height)
            ) {
                val t = ((minDistToBorder[i] - baseThreshold) / featherBand).coerceIn(0.2f, 1f)
                val newA = (origA * t).roundToInt().coerceIn(0, 255)
                out[i] = decontaminateEdgePixel(c, pR, pG, pB, t, newA)
            }
        }

        if (edgeSoftness > 0) {
            applyEdgeAlphaFeather(out, cleanedBg, width, height, edgeSoftness, pR, pG, pB)
        }

        return out
    }

    private fun collectBorderPalette(
        pixels: IntArray,
        width: Int,
        height: Int,
        explicitColor: Int?
    ): List<Int> {
        val buckets = HashMap<Int, LongArray>() // [count, rSum, gSum, bSum]
        val ring = max(2, min(width, height) / 24).coerceAtMost(8)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val inRing = x < ring || x >= width - ring || y < ring || y >= height - ring
                if (!inRing) continue
                val c = pixels[y * width + x]
                val a = (c ushr 24) and 0xFF
                if (a < 24) continue
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val key = ((r shr 5) shl 6) or ((g shr 5) shl 3) or (b shr 5)
                val acc = buckets.getOrPut(key) { LongArray(4) }
                acc[0]++
                acc[1] += r
                acc[2] += g
                acc[3] += b
            }
        }

        val sorted = buckets.entries
            .sortedByDescending { it.value[0] }
            .take(5)
            .map { entry ->
                val acc = entry.value
                val cnt = acc[0].coerceAtLeast(1L)
                packOpaqueRgb(
                    (acc[1] / cnt).toInt().coerceIn(0, 255),
                    (acc[2] / cnt).toInt().coerceIn(0, 255),
                    (acc[3] / cnt).toInt().coerceIn(0, 255)
                )
            }

        return buildList {
            if (explicitColor != null) add(explicitColor)
            for (col in sorted) {
                if (none {
                        perceptualDistance(
                            it,
                            (col ushr 16) and 0xFF,
                            (col ushr 8) and 0xFF,
                            col and 0xFF
                        ) < 18f
                    }
                ) {
                    add(col)
                }
            }
            if (isEmpty()) add(COLOR_WHITE_ARGB)
        }
    }

    private fun applyEdgeAlphaFeather(
        out: IntArray,
        bgMask: BooleanArray,
        width: Int,
        height: Int,
        edgeSoftness: Int,
        bgR: Int,
        bgG: Int,
        bgB: Int
    ) {
        val alphaStrength = (edgeSoftness / 100f).coerceIn(0f, 1f) * 0.45f
        if (alphaStrength <= 0.02f) return
        val copy = out.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                if (bgMask[idx]) continue
                val c = copy[idx]
                val a = (c ushr 24) and 0xFF
                if (a == 0) continue

                var bgNeighbors = 0
                if (bgMask[idx - 1]) bgNeighbors++
                if (bgMask[idx + 1]) bgNeighbors++
                if (bgMask[idx - width]) bgNeighbors++
                if (bgMask[idx + width]) bgNeighbors++
                if (bgNeighbors > 0) {
                    val ratio = (1f - (bgNeighbors / 4f) * alphaStrength).coerceIn(0.35f, 1f)
                    val nextA = (a * ratio).roundToInt().coerceIn(0, 255)
                    out[idx] = decontaminateEdgePixel(c, bgR, bgG, bgB, ratio, nextA)
                }
            }
        }
    }

    private fun hasBgNeighbor(bgMask: BooleanArray, idx: Int, width: Int, height: Int): Boolean {
        val x = idx % width
        val y = idx / width
        if (x > 0 && bgMask[idx - 1]) return true
        if (x + 1 < width && bgMask[idx + 1]) return true
        if (y > 0 && bgMask[idx - width]) return true
        if (y + 1 < height && bgMask[idx + width]) return true
        return false
    }

    private fun decontaminateEdgePixel(
        pixel: Int,
        bgR: Int,
        bgG: Int,
        bgB: Int,
        alphaFactor: Float,
        newAlpha: Int
    ): Int {
        if (newAlpha <= 0) return 0
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val pull = ((1f - alphaFactor) * 0.45f).coerceIn(0f, 0.45f)
        val outR = (r + (r - bgR) * pull).roundToInt().coerceIn(0, 255)
        val outG = (g + (g - bgG) * pull).roundToInt().coerceIn(0, 255)
        val outB = (b + (b - bgB) * pull).roundToInt().coerceIn(0, 255)
        return (newAlpha shl 24) or (outR shl 16) or (outG shl 8) or outB
    }

    fun applyBrushStroke(
        targetPixels: IntArray,
        originalPixels: IntArray,
        width: Int,
        height: Int,
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        radiusPx: Float,
        restore: Boolean
    ) {
        if (width <= 0 || height <= 0) return
        val r = radiusPx.coerceAtLeast(1f)
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = hypot(dx, dy)
        val step = (r * 0.35f).coerceIn(0.5f, 4f)
        val steps = max(1, ceil(dist / step).toInt())

        val minX = max(0, floor(min(x0, x1) - r - 1f).toInt())
        val maxX = min(width - 1, ceil(max(x0, x1) + r + 1f).toInt())
        val minY = max(0, floor(min(y0, y1) - r - 1f).toInt())
        val maxY = min(height - 1, ceil(max(y0, y1) + r + 1f).toInt())

        val innerR = r * 0.65f
        val feather = (r - innerR).coerceAtLeast(0.5f)

        for (y in minY..maxY) {
            for (x in minX..maxX) {
                var minD = Float.MAX_VALUE
                for (s in 0..steps) {
                    val t = if (steps == 0) 0f else s.toFloat() / steps
                    val cx = x0 + dx * t
                    val cy = y0 + dy * t
                    val d = hypot(x + 0.5f - cx, y + 0.5f - cy)
                    if (d < minD) minD = d
                }
                if (minD > r) continue
                val strength = if (minD <= innerR) {
                    1f
                } else {
                    (1f - (minD - innerR) / feather).coerceIn(0f, 1f)
                }
                val idx = y * width + x
                val cur = targetPixels[idx]
                val curA = (cur ushr 24) and 0xFF
                if (restore) {
                    val orig = originalPixels[idx]
                    val origA = (orig ushr 24) and 0xFF
                    if (origA == 0) continue
                    val nextA = max(curA, (curA + (origA - curA) * strength).roundToInt()).coerceIn(0, 255)
                    val origR = (orig ushr 16) and 0xFF
                    val origG = (orig ushr 8) and 0xFF
                    val origB = orig and 0xFF
                    targetPixels[idx] = (nextA shl 24) or (origR shl 16) or (origG shl 8) or origB
                } else {
                    if (curA == 0) continue
                    val nextA = (curA * (1f - strength)).roundToInt().coerceIn(0, 255)
                    if (nextA == 0) {
                        targetPixels[idx] = 0
                    } else {
                        targetPixels[idx] = (nextA shl 24) or (cur and 0x00FFFFFF)
                    }
                }
            }
        }
    }

    fun computeTransparentPercent(pixels: IntArray): Int {
        if (pixels.isEmpty()) return 0
        var transparentCount = 0
        for (p in pixels) {
            if (((p ushr 24) and 0xFF) < 16) {
                transparentCount++
            }
        }
        return ((transparentCount * 100.0) / pixels.size).roundToInt().coerceIn(0, 100)
    }

    fun encodeTransparentPngBytes(
        pixels: IntArray,
        width: Int,
        height: Int
    ): ByteArray {
        val safeW = width.coerceAtLeast(1)
        val safeH = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(pixels, safeW, safeH, Bitmap.Config.ARGB_8888)
        bitmap.setHasAlpha(true)
        return try {
            ByteArrayOutputStream().use { baos ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)) {
                    "Не удалось закодировать прозрачный PNG"
                }
                baos.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Weighted Euclidean color distance normalized roughly to 0..255 range.
     */
    private fun perceptualDistance(pixel: Int, refR: Int, refG: Int, refB: Int): Float {
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val rMean = (r + refR) * 0.5f
        val dr = (r - refR).toFloat()
        val dg = (g - refG).toFloat()
        val db = (b - refB).toFloat()
        val wr = 2f + rMean / 256f
        val wg = 4f
        val wb = 2f + (255f - rMean) / 256f
        return sqrt((wr * dr * dr + wg * dg * dg + wb * db * db) / 9f)
    }

    private fun sensitivityToDistance(sensitivity: Int): Float {
        // Map 1..100 -> perceptual distance ~4..145
        val s = sensitivity.coerceIn(1, 100) / 100f
        return 4f + s * s * 115f + s * 28f
    }

    private fun softnessToFeather(threshold: Float, edgeSoftness: Int): Float {
        if (edgeSoftness <= 0) return 0f
        val s = edgeSoftness.coerceIn(0, 100) / 100f
        return (threshold * 0.45f + 22f) * s
    }
}
