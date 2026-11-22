package com.example.resizer.model

import com.example.resizer.storage.ResizerProjectSummary
import java.util.UUID

enum class AppTab(val label: String) {
    EDITOR("Редактор"),
    RESIZER("Ресайзер"),
    OVERLAY("Наложение"),
    COMPRESS("Сжатие"),
    PROJECTS("Проекты")
}

enum class ResampleMode(
    val title: String,
    val description: String
) {
    NEAREST("NEAREST", "Ближайший сосед — чёткие пиксели без размытия"),
    BILINEAR("BILINEAR", "Билинейная — мягкое линейное сглаживание"),
    BICUBIC("BICUBIC", "Бикубическая — плавная детализация 4×4"),
    LANCZOS("LANCZOS", "Lanczos-3 — высокое качество уменьшения")
}

enum class ResizeCategory(val label: String) {
    ALL("Все"),
    RATIO_2_1("2:1"),
    WIDE("Широкие"),
    SQUARE("1:1"),
    PORTRAIT_1_2("1:2")
}

data class ResizePreset(
    val width: Int,
    val height: Int,
    val category: ResizeCategory
) {
    val label: String get() = "${width}×${height}"
    val area: Int get() = width * height

    val aspectLabel: String
        get() {
            fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
            val d = gcd(width, height).coerceAtLeast(1)
            return "${width / d}:${height / d}"
        }
}

val RESIZER_PRESET_SIZES: List<ResizePreset> = listOf(
    // Прямоугольные 2:1 (от 512x256 до 8x4)
    ResizePreset(512, 256, ResizeCategory.RATIO_2_1),
    ResizePreset(256, 128, ResizeCategory.RATIO_2_1),
    ResizePreset(128, 64, ResizeCategory.RATIO_2_1),
    ResizePreset(64, 32, ResizeCategory.RATIO_2_1),
    ResizePreset(32, 16, ResizeCategory.RATIO_2_1),
    ResizePreset(16, 8, ResizeCategory.RATIO_2_1),
    ResizePreset(8, 4, ResizeCategory.RATIO_2_1),
    // Широкие / панорамные и вытянутые варианты
    ResizePreset(1024, 512, ResizeCategory.WIDE),
    ResizePreset(512, 128, ResizeCategory.WIDE),
    ResizePreset(256, 64, ResizeCategory.WIDE),
    ResizePreset(256, 32, ResizeCategory.WIDE),
    ResizePreset(128, 32, ResizeCategory.WIDE),
    ResizePreset(128, 16, ResizeCategory.WIDE),
    ResizePreset(64, 16, ResizeCategory.WIDE),
    ResizePreset(64, 8, ResizeCategory.WIDE),
    ResizePreset(32, 8, ResizeCategory.WIDE),
    ResizePreset(32, 4, ResizeCategory.WIDE),
    // Квадратные степени двойки
    ResizePreset(512, 512, ResizeCategory.SQUARE),
    ResizePreset(256, 256, ResizeCategory.SQUARE),
    ResizePreset(128, 128, ResizeCategory.SQUARE),
    ResizePreset(64, 64, ResizeCategory.SQUARE),
    ResizePreset(32, 32, ResizeCategory.SQUARE),
    ResizePreset(16, 16, ResizeCategory.SQUARE),
    ResizePreset(8, 8, ResizeCategory.SQUARE),
    // Вертикальные / обратные пропорции (1:2)
    ResizePreset(256, 512, ResizeCategory.PORTRAIT_1_2),
    ResizePreset(128, 256, ResizeCategory.PORTRAIT_1_2),
    ResizePreset(64, 128, ResizeCategory.PORTRAIT_1_2),
    ResizePreset(32, 64, ResizeCategory.PORTRAIT_1_2),
    ResizePreset(16, 32, ResizeCategory.PORTRAIT_1_2),
    ResizePreset(8, 16, ResizeCategory.PORTRAIT_1_2),
    ResizePreset(4, 8, ResizeCategory.PORTRAIT_1_2)
).sortedWith(
    compareByDescending<ResizePreset> { it.area }
        .thenByDescending { maxOf(it.width, it.height) }
        .thenByDescending { it.width }
)

data class ResizerInputImage(
    val id: String = UUID.randomUUID().toString(),
    val baseName: String,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val pixels: IntArray,
    val sourceByteSize: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResizerInputImage) return false
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
        result = 31 * result + (sourceByteSize?.hashCode() ?: 0)
        return result
    }
}

data class ResizerPreviewResult(
    val pixels: IntArray,
    val width: Int,
    val height: Int,
    val outputByteSize: Long,
    val originalByteSize: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResizerPreviewResult) return false
        return width == other.width &&
            height == other.height &&
            outputByteSize == other.outputByteSize &&
            originalByteSize == other.originalByteSize &&
            pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int {
        var result = pixels.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + outputByteSize.hashCode()
        result = 31 * result + originalByteSize.hashCode()
        return result
    }
}

fun formatExactFileSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    val exactBytesText = safe.toString().reversed().chunked(3).joinToString(" ").reversed()
    return when {
        safe < 1024L -> "$exactBytesText Б"
        safe < 1024L * 1024L -> {
            val kb = String.format(java.util.Locale.US, "%.2f", safe / 1024.0)
            "$kb КБ ($exactBytesText Б)"
        }
        else -> {
            val mb = String.format(java.util.Locale.US, "%.2f", safe / (1024.0 * 1024.0))
            "$mb МБ ($exactBytesText Б)"
        }
    }
}

data class ResizerUiState(
    val currentProjectId: String? = null,
    val currentProjectName: String = "Новый ресайз",
    val inputs: List<ResizerInputImage> = emptyList(),
    val activePreviewInputId: String? = null,
    val selectedPresets: Set<ResizePreset> = setOf(RESIZER_PRESET_SIZES.first()),
    val previewPreset: ResizePreset = RESIZER_PRESET_SIZES.first(),
    val resampleMode: ResampleMode = ResampleMode.NEAREST,
    val preserveAlpha: Boolean = true,
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val savedProjects: List<ResizerProjectSummary> = emptyList(),
    val isAutoSaving: Boolean = false,
    val lastAutoSavedAtMillis: Long? = null,
    val isLoadingProject: Boolean = false
) {
    val activeInput: ResizerInputImage?
        get() = inputs.firstOrNull { it.id == activePreviewInputId } ?: inputs.firstOrNull()

    val effectivePresets: List<ResizePreset>
        get() = RESIZER_PRESET_SIZES.filter { it in selectedPresets }
}
