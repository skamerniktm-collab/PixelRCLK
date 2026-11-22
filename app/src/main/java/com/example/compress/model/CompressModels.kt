package com.example.compress.model

import java.util.UUID
import kotlin.math.roundToInt

enum class PngColorLimit(
    val maxColors: Int?,
    val shortLabel: String,
    val description: String
) {
    COLORS_256(256, "256 цв.", "PNG-8 до 256 цветов"),
    COLORS_16(16, "16 цв.", "Палитра 16 цветов (4-bit)")
}

enum class CompressPreset(
    val title: String,
    val subtitle: String,
    val scalePercent: Int,
    val colorLimit: PngColorLimit
) {
    OPTIMAL("Оптимально", "100% • 256 цв.", 100, PngColorLimit.COLORS_256),
    BALANCED("Баланс", "75% • 256 цв.", 75, PngColorLimit.COLORS_256),
    STRONG("Сильное", "50% • 16 цв.", 50, PngColorLimit.COLORS_16),
    MAXIMUM("Минимум", "35% • 16 цв.", 35, PngColorLimit.COLORS_16),
    CUSTOM("Вручную", "Свои параметры", 100, PngColorLimit.COLORS_256)
}

data class CompressSettings(
    val preset: CompressPreset = CompressPreset.OPTIMAL,
    val scalePercent: Int = CompressPreset.OPTIMAL.scalePercent,
    val colorLimit: PngColorLimit = CompressPreset.OPTIMAL.colorLimit,
    val preserveAlpha: Boolean = true
) {
    fun normalized(): CompressSettings = copy(
        scalePercent = scalePercent.coerceIn(10, 100)
    )
}

data class CompressInputImage(
    val id: String = UUID.randomUUID().toString(),
    val baseName: String,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val pixels: IntArray,
    val sourceByteSize: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompressInputImage) return false
        return id == other.id &&
            baseName == other.baseName &&
            width == other.width &&
            height == other.height &&
            hasAlpha == other.hasAlpha &&
            sourceByteSize == other.sourceByteSize &&
            pixels === other.pixels
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + baseName.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + hasAlpha.hashCode()
        result = 31 * result + sourceByteSize.hashCode()
        return result
    }
}

data class CompressPreviewResult(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val compressedBytes: ByteArray,
    val originalByteSize: Long,
    val compressedByteSize: Long
) {
    val savedBytes: Long
        get() = originalByteSize - compressedByteSize

    val savedPercent: Float
        get() = if (originalByteSize > 0L) {
            ((originalByteSize - compressedByteSize).toDouble() / originalByteSize.toDouble() * 100.0).toFloat()
        } else {
            0f
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CompressPreviewResult) return false
        return width == other.width &&
            height == other.height &&
            originalByteSize == other.originalByteSize &&
            compressedByteSize == other.compressedByteSize &&
            pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = pixels.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + originalByteSize.hashCode()
        result = 31 * result + compressedByteSize.hashCode()
        return result
    }
}

data class CompressUiState(
    val inputs: List<CompressInputImage> = emptyList(),
    val activeInputId: String? = null,
    val settings: CompressSettings = CompressSettings(),
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val lastSavedSummary: String? = null
) {
    val activeInput: CompressInputImage?
        get() = inputs.firstOrNull { it.id == activeInputId } ?: inputs.firstOrNull()

    val totalOriginalBytes: Long
        get() = inputs.sumOf { it.sourceByteSize }
}

fun formatSavingsPercent(savedPercent: Float): String {
    val rounded = (savedPercent * 10f).roundToInt() / 10f
    return when {
        rounded > 0f -> "−${String.format(java.util.Locale.US, "%.1f", rounded)}%"
        rounded < 0f -> "+${String.format(java.util.Locale.US, "%.1f", -rounded)}%"
        else -> "0.0%"
    }
}
