package com.example.resizer.engine

import com.example.resizer.model.ResampleMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

object ImageResampler {

    fun resizePixels(
        srcPixels: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        mode: ResampleMode,
        hasAlpha: Boolean
    ): IntArray {
        val safeSrcW = srcWidth.coerceAtLeast(1)
        val safeSrcH = srcHeight.coerceAtLeast(1)
        val safeDstW = targetWidth.coerceAtLeast(1)
        val safeDstH = targetHeight.coerceAtLeast(1)

        if (safeSrcW == safeDstW && safeSrcH == safeDstH) {
            return if (hasAlpha) {
                srcPixels.copyOf()
            } else {
                IntArray(srcPixels.size) { i -> srcPixels[i] or 0xFF000000.toInt() }
            }
        }

        return when (mode) {
            ResampleMode.NEAREST -> resizeNearest(srcPixels, safeSrcW, safeSrcH, safeDstW, safeDstH, hasAlpha)
            ResampleMode.BILINEAR -> resizeBilinear(srcPixels, safeSrcW, safeSrcH, safeDstW, safeDstH, hasAlpha)
            ResampleMode.BICUBIC -> resizeBicubic(srcPixels, safeSrcW, safeSrcH, safeDstW, safeDstH, hasAlpha)
            ResampleMode.LANCZOS -> resizeLanczos(srcPixels, safeSrcW, safeSrcH, safeDstW, safeDstH, hasAlpha)
        }
    }

    private fun resizeNearest(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        hasAlpha: Boolean
    ): IntArray {
        val dstPixels = IntArray(dstW * dstH)
        val scaleX = srcW.toDouble() / dstW.toDouble()
        val scaleY = srcH.toDouble() / dstH.toDouble()

        for (dy in 0 until dstH) {
            val sy = floor((dy + 0.5) * scaleY).toInt().coerceIn(0, srcH - 1)
            val srcRow = sy * srcW
            val dstRow = dy * dstW
            for (dx in 0 until dstW) {
                val sx = floor((dx + 0.5) * scaleX).toInt().coerceIn(0, srcW - 1)
                val argb = srcPixels[srcRow + sx]
                dstPixels[dstRow + dx] = if (hasAlpha) argb else (argb or 0xFF000000.toInt())
            }
        }
        return dstPixels
    }

    private fun resizeBilinear(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        hasAlpha: Boolean
    ): IntArray {
        val dstPixels = IntArray(dstW * dstH)
        val scaleX = srcW.toDouble() / dstW.toDouble()
        val scaleY = srcH.toDouble() / dstH.toDouble()

        for (dy in 0 until dstH) {
            val srcY = (dy + 0.5) * scaleY - 0.5
            val y0 = floor(srcY).toInt().coerceIn(0, srcH - 1)
            val y1 = (y0 + 1).coerceIn(0, srcH - 1)
            val wy = (srcY - floor(srcY)).coerceIn(0.0, 1.0)
            val invWy = 1.0 - wy

            val row0 = y0 * srcW
            val row1 = y1 * srcW
            val dstRow = dy * dstW

            for (dx in 0 until dstW) {
                val srcX = (dx + 0.5) * scaleX - 0.5
                val x0 = floor(srcX).toInt().coerceIn(0, srcW - 1)
                val x1 = (x0 + 1).coerceIn(0, srcW - 1)
                val wx = (srcX - floor(srcX)).coerceIn(0.0, 1.0)
                val invWx = 1.0 - wx

                val c00 = srcPixels[row0 + x0]
                val c10 = srcPixels[row0 + x1]
                val c01 = srcPixels[row1 + x0]
                val c11 = srcPixels[row1 + x1]

                val w00 = invWx * invWy
                val w10 = wx * invWy
                val w01 = invWx * wy
                val w11 = wx * wy

                val outA = if (hasAlpha) {
                    clampByte(
                        ((c00 ushr 24) and 0xFF) * w00 +
                            ((c10 ushr 24) and 0xFF) * w10 +
                            ((c01 ushr 24) and 0xFF) * w01 +
                            ((c11 ushr 24) and 0xFF) * w11
                    )
                } else {
                    0xFF
                }
                val outR = clampByte(
                    ((c00 ushr 16) and 0xFF) * w00 +
                        ((c10 ushr 16) and 0xFF) * w10 +
                        ((c01 ushr 16) and 0xFF) * w01 +
                        ((c11 ushr 16) and 0xFF) * w11
                )
                val outG = clampByte(
                    ((c00 ushr 8) and 0xFF) * w00 +
                        ((c10 ushr 8) and 0xFF) * w10 +
                        ((c01 ushr 8) and 0xFF) * w01 +
                        ((c11 ushr 8) and 0xFF) * w11
                )
                val outB = clampByte(
                    (c00 and 0xFF) * w00 +
                        (c10 and 0xFF) * w10 +
                        (c01 and 0xFF) * w01 +
                        (c11 and 0xFF) * w11
                )

                dstPixels[dstRow + dx] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
        return dstPixels
    }

    private fun bicubicKernel(x: Double): Double {
        // Catmull-Rom spline (a = -0.5)
        val ax = abs(x)
        return when {
            ax <= 1.0 -> (1.5 * ax - 2.5) * ax * ax + 1.0
            ax < 2.0 -> ((-0.5 * ax + 2.5) * ax - 4.0) * ax + 2.0
            else -> 0.0
        }
    }

    private fun resizeBicubic(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        hasAlpha: Boolean
    ): IntArray {
        val scaleX = srcW.toDouble() / dstW.toDouble()
        val scaleY = srcH.toDouble() / dstH.toDouble()
        val supportX = 2.0 * maxOf(1.0, scaleX)
        val supportY = 2.0 * maxOf(1.0, scaleY)
        val invFilterScaleX = 1.0 / maxOf(1.0, scaleX)
        val invFilterScaleY = 1.0 / maxOf(1.0, scaleY)

        val tempA = DoubleArray(srcH * dstW)
        val tempR = DoubleArray(srcH * dstW)
        val tempG = DoubleArray(srcH * dstW)
        val tempB = DoubleArray(srcH * dstW)

        for (y in 0 until srcH) {
            val rowOffset = y * srcW
            val outRowOffset = y * dstW
            for (dx in 0 until dstW) {
                val centerX = (dx + 0.5) * scaleX - 0.5
                val left = floor(centerX - supportX).toInt()
                val right = floor(centerX + supportX).toInt()

                var weightSum = 0.0
                var sumA = 0.0
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0

                for (sx in left..right) {
                    val clampedX = sx.coerceIn(0, srcW - 1)
                    val w = bicubicKernel((sx - centerX) * invFilterScaleX)
                    if (w == 0.0) continue

                    val argb = srcPixels[rowOffset + clampedX]
                    weightSum += w
                    sumA += ((argb ushr 24) and 0xFF) * w
                    sumR += ((argb ushr 16) and 0xFF) * w
                    sumG += ((argb ushr 8) and 0xFF) * w
                    sumB += (argb and 0xFF) * w
                }

                val invWeight = if (weightSum != 0.0) 1.0 / weightSum else 1.0
                val idx = outRowOffset + dx
                tempA[idx] = sumA * invWeight
                tempR[idx] = sumR * invWeight
                tempG[idx] = sumG * invWeight
                tempB[idx] = sumB * invWeight
            }
        }

        val dstPixels = IntArray(dstW * dstH)
        for (dy in 0 until dstH) {
            val centerY = (dy + 0.5) * scaleY - 0.5
            val top = floor(centerY - supportY).toInt()
            val bottom = floor(centerY + supportY).toInt()
            val dstRow = dy * dstW

            for (dx in 0 until dstW) {
                var weightSum = 0.0
                var sumA = 0.0
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0

                for (sy in top..bottom) {
                    val clampedY = sy.coerceIn(0, srcH - 1)
                    val w = bicubicKernel((sy - centerY) * invFilterScaleY)
                    if (w == 0.0) continue

                    val idx = clampedY * dstW + dx
                    weightSum += w
                    sumA += tempA[idx] * w
                    sumR += tempR[idx] * w
                    sumG += tempG[idx] * w
                    sumB += tempB[idx] * w
                }

                val invWeight = if (weightSum != 0.0) 1.0 / weightSum else 1.0
                val outA = if (hasAlpha) clampByte(sumA * invWeight) else 0xFF
                val outR = clampByte(sumR * invWeight)
                val outG = clampByte(sumG * invWeight)
                val outB = clampByte(sumB * invWeight)
                dstPixels[dstRow + dx] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
        return dstPixels
    }

    private fun lanczosKernel(x: Double, a: Int = 3): Double {
        val ax = abs(x)
        if (ax < 1e-9) return 1.0
        if (ax >= a) return 0.0
        val px = PI * x
        val pxa = px / a
        return (sin(px) / px) * (sin(pxa) / pxa)
    }

    private fun clampByte(value: Double): Int =
        value.roundToInt().coerceIn(0, 255)

    private fun resizeLanczos(
        srcPixels: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        hasAlpha: Boolean,
        a: Int = 3
    ): IntArray {
        val scaleX = srcW.toDouble() / dstW.toDouble()
        val scaleY = srcH.toDouble() / dstH.toDouble()
        val supportX = a * maxOf(1.0, scaleX)
        val supportY = a * maxOf(1.0, scaleY)
        val invFilterScaleX = 1.0 / maxOf(1.0, scaleX)
        val invFilterScaleY = 1.0 / maxOf(1.0, scaleY)

        // Horizontal pass in Double
        val tempA = DoubleArray(srcH * dstW)
        val tempR = DoubleArray(srcH * dstW)
        val tempG = DoubleArray(srcH * dstW)
        val tempB = DoubleArray(srcH * dstW)

        for (y in 0 until srcH) {
            val rowOffset = y * srcW
            val outRowOffset = y * dstW
            for (dx in 0 until dstW) {
                val centerX = (dx + 0.5) * scaleX - 0.5
                val left = floor(centerX - supportX).toInt()
                val right = floor(centerX + supportX).toInt()

                var weightSum = 0.0
                var sumA = 0.0
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0

                for (sx in left..right) {
                    val clampedX = sx.coerceIn(0, srcW - 1)
                    val w = lanczosKernel((sx - centerX) * invFilterScaleX, a)
                    if (w == 0.0) continue

                    val argb = srcPixels[rowOffset + clampedX]
                    weightSum += w
                    sumA += ((argb ushr 24) and 0xFF) * w
                    sumR += ((argb ushr 16) and 0xFF) * w
                    sumG += ((argb ushr 8) and 0xFF) * w
                    sumB += (argb and 0xFF) * w
                }

                val invWeight = if (weightSum != 0.0) 1.0 / weightSum else 1.0
                val idx = outRowOffset + dx
                tempA[idx] = sumA * invWeight
                tempR[idx] = sumR * invWeight
                tempG[idx] = sumG * invWeight
                tempB[idx] = sumB * invWeight
            }
        }

        // Vertical pass
        val dstPixels = IntArray(dstW * dstH)
        for (dy in 0 until dstH) {
            val centerY = (dy + 0.5) * scaleY - 0.5
            val top = floor(centerY - supportY).toInt()
            val bottom = floor(centerY + supportY).toInt()
            val dstRow = dy * dstW

            for (dx in 0 until dstW) {
                var weightSum = 0.0
                var sumA = 0.0
                var sumR = 0.0
                var sumG = 0.0
                var sumB = 0.0

                for (sy in top..bottom) {
                    val clampedY = sy.coerceIn(0, srcH - 1)
                    val w = lanczosKernel((sy - centerY) * invFilterScaleY, a)
                    if (w == 0.0) continue

                    val idx = clampedY * dstW + dx
                    weightSum += w
                    sumA += tempA[idx] * w
                    sumR += tempR[idx] * w
                    sumG += tempG[idx] * w
                    sumB += tempB[idx] * w
                }

                val invWeight = if (weightSum != 0.0) 1.0 / weightSum else 1.0
                val outA = if (hasAlpha) clampByte(sumA * invWeight) else 0xFF
                val outR = clampByte(sumR * invWeight)
                val outG = clampByte(sumG * invWeight)
                val outB = clampByte(sumB * invWeight)

                dstPixels[dstRow + dx] = (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
        return dstPixels
    }
}
