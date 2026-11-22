package com.example.compress.engine

import com.example.resizer.engine.ImageResampler
import com.example.resizer.model.ResampleMode
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class PngCompressionOutput(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val pngBytes: ByteArray
)

/**
 * Чистый Kotlin-компрессор, который всегда формирует валидный PNG (IHDR / [PLTE / tRNS] / IDAT / IEND)
 * со сжатием Zlib BEST_COMPRESSION (уровень 9), адаптивной фильтрацией строк и квантованием палитры
 * Median-Cut для ощутимого уменьшения веса файла.
 */
object PngCompressor {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47,
        0x0D, 0x0A, 0x1A, 0x0A
    )

    fun compressToPng(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        scalePercent: Int,
        maxColors: Int?,
        preserveAlpha: Boolean
    ): PngCompressionOutput {
        val safeW = srcWidth.coerceAtLeast(1)
        val safeH = srcHeight.coerceAtLeast(1)
        val clampedScale = scalePercent.coerceIn(10, 100)
        val targetW = ((safeW * clampedScale) / 100f).roundToInt().coerceAtLeast(1)
        val targetH = ((safeH * clampedScale) / 100f).roundToInt().coerceAtLeast(1)

        val srcHasAlpha = preserveAlpha && srcPixels.any { ((it ushr 24) and 0xFF) < 255 }

        val scaledPixels: IntArray = if (targetW == safeW && targetH == safeH) {
            if (srcHasAlpha) {
                srcPixels
            } else {
                IntArray(srcPixels.size) { idx -> srcPixels[idx] or 0xFF000000.toInt() }
            }
        } else if (targetW <= safeW / 2 || targetH <= safeH / 2) {
            downscaleAreaAverage(
                src = srcPixels,
                srcW = safeW,
                srcH = safeH,
                dstW = targetW,
                dstH = targetH,
                preserveAlpha = srcHasAlpha
            )
        } else {
            ImageResampler.resizePixels(
                srcPixels = srcPixels,
                srcWidth = safeW,
                srcHeight = safeH,
                targetWidth = targetW,
                targetHeight = targetH,
                mode = ResampleMode.BILINEAR,
                hasAlpha = srcHasAlpha
            )
        }

        val effectiveHasAlpha = srcHasAlpha && scaledPixels.any { ((it ushr 24) and 0xFF) < 255 }

        if (maxColors == null) {
            val bytes = encodeTruecolorPng(
                pixels = scaledPixels,
                width = targetW,
                height = targetH,
                hasAlpha = effectiveHasAlpha
            )
            return PngCompressionOutput(
                pixels = scaledPixels,
                width = targetW,
                height = targetH,
                pngBytes = bytes
            )
        }

        val clampedColors = maxColors.coerceIn(2, 256)
        val quantized = quantizePixels(
            pixels = scaledPixels,
            maxColors = clampedColors,
            preserveAlpha = effectiveHasAlpha
        )
        val bytes = encodeIndexedPng(
            indices = quantized.indices,
            palette = quantized.palette,
            width = targetW,
            height = targetH,
            hasAlpha = effectiveHasAlpha
        )
        return PngCompressionOutput(
            pixels = quantized.previewPixels,
            width = targetW,
            height = targetH,
            pngBytes = bytes
        )
    }

    private fun downscaleAreaAverage(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        preserveAlpha: Boolean
    ): IntArray {
        val out = IntArray(dstW * dstH)
        val scaleX = srcW.toFloat() / dstW.toFloat()
        val scaleY = srcH.toFloat() / dstH.toFloat()

        for (dy in 0 until dstH) {
            val yStart = (dy * scaleY).toInt().coerceIn(0, srcH - 1)
            val yEnd = (((dy + 1) * scaleY).toInt()).coerceIn(yStart + 1, srcH)
            for (dx in 0 until dstW) {
                val xStart = (dx * scaleX).toInt().coerceIn(0, srcW - 1)
                val xEnd = (((dx + 1) * scaleX).toInt()).coerceIn(xStart + 1, srcW)

                var sumR = 0L
                var sumG = 0L
                var sumB = 0L
                var sumA = 0L
                var count = 0

                for (sy in yStart until yEnd) {
                    val rowOffset = sy * srcW
                    for (sx in xStart until xEnd) {
                        val c = src[rowOffset + sx]
                        val a = if (preserveAlpha) (c ushr 24) and 0xFF else 255
                        val r = (c ushr 16) and 0xFF
                        val g = (c ushr 8) and 0xFF
                        val b = c and 0xFF
                        if (preserveAlpha) {
                            sumR += r * a
                            sumG += g * a
                            sumB += b * a
                            sumA += a
                        } else {
                            sumR += r
                            sumG += g
                            sumB += b
                        }
                        count++
                    }
                }

                out[dy * dstW + dx] = if (!preserveAlpha) {
                    val c = count.coerceAtLeast(1)
                    (0xFF shl 24) or
                        (((sumR / c).toInt().coerceIn(0, 255)) shl 16) or
                        (((sumG / c).toInt().coerceIn(0, 255)) shl 8) or
                        ((sumB / c).toInt().coerceIn(0, 255))
                } else if (sumA <= 0L) {
                    0
                } else {
                    val avgA = (sumA / count.coerceAtLeast(1)).toInt().coerceIn(0, 255)
                    val avgR = (sumR / sumA).toInt().coerceIn(0, 255)
                    val avgG = (sumG / sumA).toInt().coerceIn(0, 255)
                    val avgB = (sumB / sumA).toInt().coerceIn(0, 255)
                    (avgA shl 24) or (avgR shl 16) or (avgG shl 8) or avgB
                }
            }
        }
        return out
    }

    private data class QuantizeResult(
        val palette: IntArray,
        val indices: ByteArray,
        val previewPixels: IntArray
    )

    private fun quantizePixels(
        pixels: IntArray,
        maxColors: Int,
        preserveAlpha: Boolean
    ): QuantizeResult {
        // 1. Быстрый точный путь для пиксель-арта, иконок и скриншотов с малым числом цветов
        val exactMap = LinkedHashMap<Int, Int>(minOf(maxColors + 2, 260))
        var exceededExact = false
        for (raw in pixels) {
            val argb = if (preserveAlpha) {
                if (((raw ushr 24) and 0xFF) < 8) 0 else raw
            } else {
                raw or 0xFF000000.toInt()
            }
            if (!exactMap.containsKey(argb)) {
                if (exactMap.size >= maxColors) {
                    exceededExact = true
                    break
                }
                exactMap[argb] = exactMap.size
            }
        }
        if (!exceededExact && exactMap.isNotEmpty()) {
            val palette = exactMap.keys.toIntArray()
            val indices = ByteArray(pixels.size)
            val preview = IntArray(pixels.size)
            for (i in pixels.indices) {
                val raw = pixels[i]
                val argb = if (preserveAlpha) {
                    if (((raw ushr 24) and 0xFF) < 8) 0 else raw
                } else {
                    raw or 0xFF000000.toInt()
                }
                val idx = exactMap[argb] ?: 0
                indices[i] = idx.toByte()
                preview[i] = palette[idx]
            }
            return QuantizeResult(palette, indices, preview)
        }

        // 2. Быстрое гистограммное квантование Median-Cut (5-5-5-2 RGBA = 131 072 корзины)
        var hasFullyTransparent = false
        val bucketKeyMap = HashMap<Int, Int>(4096)
        val bucketCounts = ArrayList<Int>(4096)
        val bucketSumR = ArrayList<Long>(4096)
        val bucketSumG = ArrayList<Long>(4096)
        val bucketSumB = ArrayList<Long>(4096)
        val bucketSumA = ArrayList<Long>(4096)

        for (raw in pixels) {
            val a = if (preserveAlpha) (raw ushr 24) and 0xFF else 255
            if (preserveAlpha && a < 16) {
                hasFullyTransparent = true
                continue
            }
            val r = (raw ushr 16) and 0xFF
            val g = (raw ushr 8) and 0xFF
            val b = raw and 0xFF
            val key = ((r ushr 3) shl 12) or ((g ushr 3) shl 7) or ((b ushr 3) shl 2) or (a ushr 6)
            val existing = bucketKeyMap[key]
            if (existing != null) {
                bucketCounts[existing] = bucketCounts[existing] + 1
                bucketSumR[existing] = bucketSumR[existing] + r
                bucketSumG[existing] = bucketSumG[existing] + g
                bucketSumB[existing] = bucketSumB[existing] + b
                bucketSumA[existing] = bucketSumA[existing] + a
            } else {
                val id = bucketCounts.size
                bucketKeyMap[key] = id
                bucketCounts.add(1)
                bucketSumR.add(r.toLong())
                bucketSumG.add(g.toLong())
                bucketSumB.add(b.toLong())
                bucketSumA.add(a.toLong())
            }
        }

        val bucketCount = bucketCounts.size
        val targetColorBoxes = if (hasFullyTransparent) (maxColors - 1).coerceAtLeast(1) else maxColors

        val paletteList = ArrayList<Int>(maxColors)
        if (hasFullyTransparent) {
            paletteList.add(0x00000000)
        }

        if (bucketCount > 0) {
            val avgR = IntArray(bucketCount)
            val avgG = IntArray(bucketCount)
            val avgB = IntArray(bucketCount)
            val avgA = IntArray(bucketCount)
            val counts = IntArray(bucketCount)
            for (i in 0 until bucketCount) {
                val c = bucketCounts[i].coerceAtLeast(1)
                counts[i] = c
                avgR[i] = (bucketSumR[i] / c).toInt().coerceIn(0, 255)
                avgG[i] = (bucketSumG[i] / c).toInt().coerceIn(0, 255)
                avgB[i] = (bucketSumB[i] / c).toInt().coerceIn(0, 255)
                avgA[i] = if (preserveAlpha) (bucketSumA[i] / c).toInt().coerceIn(0, 255) else 255
            }

            if (bucketCount <= targetColorBoxes) {
                for (i in 0 until bucketCount) {
                    paletteList.add((avgA[i] shl 24) or (avgR[i] shl 16) or (avgG[i] shl 8) or avgB[i])
                }
            } else {
                val order = IntArray(bucketCount) { it }
                class Box(var start: Int, var end: Int) {
                    var minR = 255; var maxR = 0
                    var minG = 255; var maxG = 0
                    var minB = 255; var maxB = 0
                    var minA = 255; var maxA = 0
                    var totalPixels = 0L

                    fun recompute() {
                        minR = 255; maxR = 0
                        minG = 255; maxG = 0
                        minB = 255; maxB = 0
                        minA = 255; maxA = 0
                        totalPixels = 0L
                        for (idx in start until end) {
                            val bId = order[idx]
                            val r = avgR[bId]
                            val g = avgG[bId]
                            val b = avgB[bId]
                            val a = avgA[bId]
                            if (r < minR) minR = r
                            if (r > maxR) maxR = r
                            if (g < minG) minG = g
                            if (g > maxG) maxG = g
                            if (b < minB) minB = b
                            if (b > maxB) maxB = b
                            if (a < minA) minA = a
                            if (a > maxA) maxA = a
                            totalPixels += counts[bId]
                        }
                    }

                    fun volumeScore(): Long {
                        val range = max(
                            max(maxR - minR, maxG - minG),
                            max(maxB - minB, if (preserveAlpha) (maxA - minA) else 0)
                        ).toLong()
                        return if (end - start <= 1) -1L else range * 1_000_000L + totalPixels
                    }
                }

                val boxes = ArrayList<Box>(targetColorBoxes)
                boxes.add(Box(0, bucketCount).apply { recompute() })

                while (boxes.size < targetColorBoxes) {
                    var bestIdx = -1
                    var bestScore = -1L
                    for (i in boxes.indices) {
                        val s = boxes[i].volumeScore()
                        if (s > bestScore) {
                            bestScore = s
                            bestIdx = i
                        }
                    }
                    if (bestIdx < 0 || bestScore <= 0L) break

                    val box = boxes[bestIdx]
                    val rangeR = box.maxR - box.minR
                    val rangeG = box.maxG - box.minG
                    val rangeB = box.maxB - box.minB
                    val rangeA = if (preserveAlpha) (box.maxA - box.minA) else 0
                    val maxRange = max(max(rangeR, rangeG), max(rangeB, rangeA))

                    val subList = Array(box.end - box.start) { idx -> order[box.start + idx] }
                    subList.sortWith { i1, i2 ->
                        when (maxRange) {
                            rangeG -> avgG[i1].compareTo(avgG[i2])
                            rangeR -> avgR[i1].compareTo(avgR[i2])
                            rangeB -> avgB[i1].compareTo(avgB[i2])
                            else -> avgA[i1].compareTo(avgA[i2])
                        }
                    }
                    for (i in subList.indices) {
                        order[box.start + i] = subList[i]
                    }

                    val halfWeight = box.totalPixels / 2L
                    var running = 0L
                    var split = box.start + 1
                    for (i in box.start until box.end - 1) {
                        running += counts[order[i]]
                        if (running >= halfWeight) {
                            split = i + 1
                            break
                        }
                    }
                    split = split.coerceIn(box.start + 1, box.end - 1)

                    val rightBox = Box(split, box.end).apply { recompute() }
                    box.end = split
                    box.recompute()
                    boxes.add(rightBox)
                }

                for (box in boxes) {
                    var sR = 0L; var sG = 0L; var sB = 0L; var sA = 0L; var wSum = 0L
                    for (i in box.start until box.end) {
                        val bId = order[i]
                        val w = counts[bId].toLong()
                        sR += avgR[bId] * w
                        sG += avgG[bId] * w
                        sB += avgB[bId] * w
                        sA += avgA[bId] * w
                        wSum += w
                    }
                    val denom = wSum.coerceAtLeast(1L)
                    val pR = (sR / denom).toInt().coerceIn(0, 255)
                    val pG = (sG / denom).toInt().coerceIn(0, 255)
                    val pB = (sB / denom).toInt().coerceIn(0, 255)
                    val pA = if (preserveAlpha) (sA / denom).toInt().coerceIn(16, 255) else 255
                    paletteList.add((pA shl 24) or (pR shl 16) or (pG shl 8) or pB)
                }
            }
        }

        if (paletteList.isEmpty()) {
            paletteList.add(0xFF000000.toInt())
        }

        val palette = paletteList.toIntArray()
        val pR = IntArray(palette.size) { (palette[it] ushr 16) and 0xFF }
        val pG = IntArray(palette.size) { (palette[it] ushr 8) and 0xFF }
        val pB = IntArray(palette.size) { palette[it] and 0xFF }
        val pA = IntArray(palette.size) { (palette[it] ushr 24) and 0xFF }
        val startSearchIdx = if (hasFullyTransparent) 1 else 0

        val bucketToPalette = HashMap<Int, Int>(bucketCount.coerceAtLeast(16))
        val indices = ByteArray(pixels.size)
        val preview = IntArray(pixels.size)

        for (i in pixels.indices) {
            val raw = pixels[i]
            val a = if (preserveAlpha) (raw ushr 24) and 0xFF else 255
            if (hasFullyTransparent && a < 16) {
                indices[i] = 0
                preview[i] = 0
                continue
            }
            val r = (raw ushr 16) and 0xFF
            val g = (raw ushr 8) and 0xFF
            val b = raw and 0xFF
            val key = ((r ushr 3) shl 12) or ((g ushr 3) shl 7) or ((b ushr 3) shl 2) or (a ushr 6)
            val palIdx = bucketToPalette.getOrPut(key) {
                var bestIdx = startSearchIdx.coerceAtMost(palette.lastIndex)
                var bestDist = Long.MAX_VALUE
                for (pi in startSearchIdx until palette.size) {
                    val dr = (r - pR[pi]).toLong()
                    val dg = (g - pG[pi]).toLong()
                    val db = (b - pB[pi]).toLong()
                    val da = if (preserveAlpha) (a - pA[pi]).toLong() else 0L
                    val dist = dr * dr * 3L + dg * dg * 4L + db * db * 2L + da * da * 4L
                    if (dist < bestDist) {
                        bestDist = dist
                        bestIdx = pi
                    }
                }
                bestIdx
            }
            indices[i] = palIdx.toByte()
            preview[i] = palette[palIdx]
        }

        return QuantizeResult(palette, indices, preview)
    }

    private fun encodeIndexedPng(
        indices: ByteArray,
        palette: IntArray,
        width: Int,
        height: Int,
        hasAlpha: Boolean
    ): ByteArray {
        val bitDepth = when {
            palette.size <= 16 -> 4
            else -> 8
        }

        val baos = ByteArrayOutputStream(max(256, indices.size / 2 + 1024))
        baos.write(PNG_SIGNATURE)

        // IHDR
        val ihdr = ByteArray(13)
        writeIntBE(ihdr, 0, width)
        writeIntBE(ihdr, 4, height)
        ihdr[8] = bitDepth.toByte()
        ihdr[9] = 3 // Indexed-color
        ihdr[10] = 0
        ihdr[11] = 0
        ihdr[12] = 0
        writeChunk(baos, "IHDR", ihdr)

        // PLTE
        val plte = ByteArray(palette.size * 3)
        var lastAlphaIdx = -1
        for (i in palette.indices) {
            val c = palette[i]
            plte[i * 3] = ((c ushr 16) and 0xFF).toByte()
            plte[i * 3 + 1] = ((c ushr 8) and 0xFF).toByte()
            plte[i * 3 + 2] = (c and 0xFF).toByte()
            if (hasAlpha && ((c ushr 24) and 0xFF) < 255) {
                lastAlphaIdx = i
            }
        }
        writeChunk(baos, "PLTE", plte)

        // tRNS (только если есть прозрачные/полупрозрачные цвета в палитре)
        if (lastAlphaIdx >= 0) {
            val trns = ByteArray(lastAlphaIdx + 1) { idx ->
                ((palette[idx] ushr 24) and 0xFF).toByte()
            }
            writeChunk(baos, "tRNS", trns)
        }

        // Raw scanlines для indexed PNG
        val rowDataBytes = if (bitDepth == 4) (width + 1) / 2 else width
        val rawScanlines = ByteArray((rowDataBytes + 1) * height)
        var outPos = 0
        if (bitDepth == 4) {
            for (y in 0 until height) {
                rawScanlines[outPos++] = 0 // Filter: None
                val rowStart = y * width
                var x = 0
                while (x < width) {
                    val hi = indices[rowStart + x].toInt() and 0x0F
                    val lo = if (x + 1 < width) indices[rowStart + x + 1].toInt() and 0x0F else 0
                    rawScanlines[outPos++] = ((hi shl 4) or lo).toByte()
                    x += 2
                }
            }
        } else {
            for (y in 0 until height) {
                rawScanlines[outPos++] = 0 // Filter: None
                System.arraycopy(indices, y * width, rawScanlines, outPos, width)
                outPos += width
            }
        }

        val compressedIdat = deflateBest(rawScanlines)
        writeChunk(baos, "IDAT", compressedIdat)
        writeChunk(baos, "IEND", ByteArray(0))
        return baos.toByteArray()
    }

    private fun encodeTruecolorPng(
        pixels: IntArray,
        width: Int,
        height: Int,
        hasAlpha: Boolean
    ): ByteArray {
        val bpp = if (hasAlpha) 4 else 3
        val stride = width * bpp
        val rawScanlines = ByteArray((stride + 1) * height)

        var prevRow = ByteArray(stride)
        var curRow = ByteArray(stride)
        val filteredSub = ByteArray(stride)
        val filteredUp = ByteArray(stride)
        val filteredPaeth = ByteArray(stride)

        var outPos = 0
        for (y in 0 until height) {
            val rowOffset = y * width
            var curPos = 0
            for (x in 0 until width) {
                val c = pixels[rowOffset + x]
                curRow[curPos++] = ((c ushr 16) and 0xFF).toByte()
                curRow[curPos++] = ((c ushr 8) and 0xFF).toByte()
                curRow[curPos++] = (c and 0xFF).toByte()
                if (hasAlpha) {
                    curRow[curPos++] = ((c ushr 24) and 0xFF).toByte()
                }
            }

            var sadNone = 0L
            var sadSub = 0L
            var sadUp = 0L
            var sadPaeth = 0L

            for (i in 0 until stride) {
                val xVal = curRow[i].toInt() and 0xFF
                val aVal = if (i >= bpp) curRow[i - bpp].toInt() and 0xFF else 0
                val bVal = prevRow[i].toInt() and 0xFF
                val cVal = if (i >= bpp) prevRow[i - bpp].toInt() and 0xFF else 0

                val sub = (xVal - aVal) and 0xFF
                val up = (xVal - bVal) and 0xFF
                val paethPred = paethPredictor(aVal, bVal, cVal)
                val paeth = (xVal - paethPred) and 0xFF

                filteredSub[i] = sub.toByte()
                filteredUp[i] = up.toByte()
                filteredPaeth[i] = paeth.toByte()

                sadNone += abs(curRow[i].toInt())
                sadSub += abs(filteredSub[i].toInt())
                sadUp += abs(filteredUp[i].toInt())
                sadPaeth += abs(filteredPaeth[i].toInt())
            }

            val bestType: Byte
            val bestRow: ByteArray
            if (sadPaeth <= sadNone && sadPaeth <= sadSub && sadPaeth <= sadUp) {
                bestType = 4; bestRow = filteredPaeth
            } else if (sadSub <= sadNone && sadSub <= sadUp) {
                bestType = 1; bestRow = filteredSub
            } else if (sadUp <= sadNone) {
                bestType = 2; bestRow = filteredUp
            } else {
                bestType = 0; bestRow = curRow
            }

            rawScanlines[outPos++] = bestType
            System.arraycopy(bestRow, 0, rawScanlines, outPos, stride)
            outPos += stride

            val tmp = prevRow
            prevRow = curRow
            curRow = tmp
        }

        val baos = ByteArrayOutputStream(max(512, pixels.size))
        baos.write(PNG_SIGNATURE)

        val ihdr = ByteArray(13)
        writeIntBE(ihdr, 0, width)
        writeIntBE(ihdr, 4, height)
        ihdr[8] = 8
        ihdr[9] = if (hasAlpha) 6 else 2 // RGBA or RGB
        ihdr[10] = 0
        ihdr[11] = 0
        ihdr[12] = 0
        writeChunk(baos, "IHDR", ihdr)
        writeChunk(baos, "IDAT", deflateBest(rawScanlines))
        writeChunk(baos, "IEND", ByteArray(0))
        return baos.toByteArray()
    }

    private fun paethPredictor(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return when {
            pa <= pb && pa <= pc -> a
            pb <= pc -> b
            else -> c
        }
    }

    private fun deflateBest(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val out = ByteArrayOutputStream(raw.size / 2 + 128)
        val buf = ByteArray(16 * 1024)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            if (count > 0) {
                out.write(buf, 0, count)
            } else if (deflater.needsInput()) {
                break
            }
        }
        deflater.end()
        return out.toByteArray()
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val lenBytes = ByteArray(4)
        writeIntBE(lenBytes, 0, data.size)
        out.write(lenBytes)

        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        if (data.isNotEmpty()) {
            out.write(data)
        }

        val crc = CRC32()
        crc.update(typeBytes)
        if (data.isNotEmpty()) {
            crc.update(data)
        }
        val crcBytes = ByteArray(4)
        writeIntBE(crcBytes, 0, crc.value.toInt())
        out.write(crcBytes)
    }

    private fun writeIntBE(dst: ByteArray, offset: Int, value: Int) {
        dst[offset] = ((value ushr 24) and 0xFF).toByte()
        dst[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        dst[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        dst[offset + 3] = (value and 0xFF).toByte()
    }
}
