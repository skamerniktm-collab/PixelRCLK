import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

data class SizePreset(val width: Int, val height: Int)

enum class ResampleMode(
    val title: String,
    val interpolationHint: Any?,
) {
    NEAREST("NEAREST", RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR),
    BILINEAR("BILINEAR", RenderingHints.VALUE_INTERPOLATION_BILINEAR),
    BICUBIC("BICUBIC", RenderingHints.VALUE_INTERPOLATION_BICUBIC),
    LANCZOS("LANCZOS", null),
}

val PRESET_SIZES = listOf(
    // Прямоугольные 2:1 (от 512x256 до 8x4)
    SizePreset(512, 256),
    SizePreset(256, 128),
    SizePreset(128, 64),
    SizePreset(64, 32),
    SizePreset(32, 16),
    SizePreset(16, 8),
    SizePreset(8, 4),
    // Широкие / панорамные и вытянутые варианты
    SizePreset(1024, 512),
    SizePreset(512, 128),
    SizePreset(256, 64),
    SizePreset(128, 32),
    SizePreset(64, 16),
    SizePreset(32, 8),
    // Квадратные степени двойки
    SizePreset(512, 512),
    SizePreset(256, 256),
    SizePreset(128, 128),
    SizePreset(64, 64),
    SizePreset(32, 32),
    SizePreset(16, 16),
    SizePreset(8, 8),
    // Вертикальные / обратные пропорции (1:2)
    SizePreset(256, 512),
    SizePreset(128, 256),
    SizePreset(64, 128),
    SizePreset(32, 64),
    SizePreset(16, 32),
    SizePreset(8, 16),
    SizePreset(4, 8),
)

fun askSizeChoice(): List<SizePreset> {
    println("\nВыбери размер:")
    PRESET_SIZES.forEachIndexed { index, preset ->
        println("  ${index + 1}. ${preset.width}x${preset.height}")
    }
    val allIndex = PRESET_SIZES.size + 1
    println("  $allIndex. Все варианты из списка сразу")

    while (true) {
        print("Номер размера: ")
        val num = readlnOrNull()?.trim()?.toIntOrNull()
        if (num != null) {
            if (num in 1..PRESET_SIZES.size) {
                return listOf(PRESET_SIZES[num - 1])
            }
            if (num == allIndex) {
                return PRESET_SIZES
            }
        }
        println("Неверный ввод, введи число от 1 до $allIndex.")
    }
}

fun askModeChoice(): ResampleMode {
    val modes = ResampleMode.entries
    println("\nВыбери режим:")
    modes.forEachIndexed { index, mode ->
        println("  ${index + 1}. ${mode.title}")
    }

    while (true) {
        print("Номер режима [по умолчанию 1 - NEAREST]: ")
        val input = readlnOrNull()?.trim().orEmpty()
        if (input.isEmpty()) return ResampleMode.NEAREST
        val num = input.toIntOrNull()
        if (num != null && num in 1..modes.size) {
            return modes[num - 1]
        }
        println("Неверный ввод, выбери 1..${modes.size}.")
    }
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

private fun resizeLanczos(source: BufferedImage, targetWidth: Int, targetHeight: Int, a: Int = 3): BufferedImage {
    val srcW = source.width
    val srcH = source.height
    val hasAlpha = source.colorModel.hasAlpha()
    val imageType = if (hasAlpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB

    val srcPixels = IntArray(srcW * srcH)
    source.getRGB(0, 0, srcW, srcH, srcPixels, 0, srcW)

    val scaleX = srcW.toDouble() / targetWidth.toDouble()
    val scaleY = srcH.toDouble() / targetHeight.toDouble()
    val supportX = a * maxOf(1.0, scaleX)
    val supportY = a * maxOf(1.0, scaleY)

    // Горизонтальный проход с накоплением в Double
    val tempA = DoubleArray(srcH * targetWidth)
    val tempR = DoubleArray(srcH * targetWidth)
    val tempG = DoubleArray(srcH * targetWidth)
    val tempB = DoubleArray(srcH * targetWidth)

    for (y in 0 until srcH) {
        val rowOffset = y * srcW
        val outRowOffset = y * targetWidth
        for (dx in 0 until targetWidth) {
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
                val dist = (sx - centerX) / maxOf(1.0, scaleX)
                val w = lanczosKernel(dist, a)
                if (w == 0.0) continue

                val argb = srcPixels[rowOffset + clampedX]
                val alpha = ((argb ushr 24) and 0xFF).toDouble()
                val red = ((argb ushr 16) and 0xFF).toDouble()
                val green = ((argb ushr 8) and 0xFF).toDouble()
                val blue = (argb and 0xFF).toDouble()

                weightSum += w
                sumA += alpha * w
                sumR += red * w
                sumG += green * w
                sumB += blue * w
            }

            val invWeight = if (weightSum != 0.0) 1.0 / weightSum else 1.0
            val idx = outRowOffset + dx
            tempA[idx] = sumA * invWeight
            tempR[idx] = sumR * invWeight
            tempG[idx] = sumG * invWeight
            tempB[idx] = sumB * invWeight
        }
    }

    // Вертикальный проход
    val dstPixels = IntArray(targetWidth * targetHeight)
    for (dy in 0 until targetHeight) {
        val centerY = (dy + 0.5) * scaleY - 0.5
        val top = floor(centerY - supportY).toInt()
        val bottom = floor(centerY + supportY).toInt()

        for (dx in 0 until targetWidth) {
            var weightSum = 0.0
            var sumA = 0.0
            var sumR = 0.0
            var sumG = 0.0
            var sumB = 0.0

            for (sy in top..bottom) {
                val clampedY = sy.coerceIn(0, srcH - 1)
                val dist = (sy - centerY) / maxOf(1.0, scaleY)
                val w = lanczosKernel(dist, a)
                if (w == 0.0) continue

                val idx = clampedY * targetWidth + dx
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

            dstPixels[dy * targetWidth + dx] =
                (outA shl 24) or (outR shl 16) or (outG shl 8) or outB
        }
    }

    val output = BufferedImage(targetWidth, targetHeight, imageType)
    output.setRGB(0, 0, targetWidth, targetHeight, dstPixels, 0, targetWidth)
    return output
}

fun resizePng(source: BufferedImage, targetWidth: Int, targetHeight: Int, mode: ResampleMode): BufferedImage {
    if (mode == ResampleMode.LANCZOS) {
        return resizeLanczos(source, targetWidth, targetHeight)
    }

    val imageType = if (source.colorModel.hasAlpha()) {
        BufferedImage.TYPE_INT_ARGB
    } else {
        BufferedImage.TYPE_INT_RGB
    }

    val output = BufferedImage(targetWidth, targetHeight, imageType)
    val g2d = output.createGraphics()
    try {
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, mode.interpolationHint)
        if (mode == ResampleMode.NEAREST) {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)
        }
        g2d.drawImage(source, 0, 0, targetWidth, targetHeight, null)
    } finally {
        g2d.dispose()
    }
    return output
}

fun main() {
    val sizes = askSizeChoice()
    val mode = askModeChoice()

    val inputDir = File("input_imgs")
    val outputDir = File("output_imgs")
    inputDir.mkdirs()
    outputDir.mkdirs()

    val pngFiles = inputDir.listFiles { file ->
        file.isFile && file.extension.equals("png", ignoreCase = true)
    }?.sortedBy { it.name }.orEmpty()

    if (pngFiles.isEmpty()) {
        println("\nВ папке '${inputDir.path}' нет PNG-файлов.")
        return
    }

    var savedCount = 0
    for (file in pngFiles) {
        val sourceImage = ImageIO.read(file) ?: continue
        val baseName = file.nameWithoutExtension

        for (preset in sizes) {
            val resized = resizePng(sourceImage, preset.width, preset.height, mode)
            val outFile = File(outputDir, "${baseName}_${preset.width}x${preset.height}.png")
            ImageIO.write(resized, "png", outFile)
            savedCount++
        }
    }

    println("\nГотово: сохранено PNG-файлов $savedCount | режим: ${mode.title} -> ${outputDir.path}")
}
