package com.example.editor.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.editor.storage.EditorProjectSummary
import java.util.UUID

enum class EditorTool {
    BRUSH,
    ERASER,
    FILL,
    SELECT,
    EYEDROPPER,
    HAND
}

enum class SelectionMode(val label: String) {
    RECTANGLE("Рамка"),
    LASSO("Лассо"),
    MAGIC_WAND("Палочка"),
    MOVE("Сдвиг")
}

enum class RecolorShadingMode(val label: String) {
    LUMINANCE("Светотень"),
    TINT_MULTIPLY("Тонирование")
}

enum class CanvasPreset(
    val label: String,
    val ratio: String,
    val width: Int,
    val height: Int
) {
    W512_H256("512 × 256", "2:1", 512, 256),
    W512_H128("512 × 128", "4:1", 512, 128),
    W256_H256("256 × 256", "1:1", 256, 256),
    W256_H128("256 × 128", "2:1", 256, 128),
    W256_H64("256 × 64", "4:1", 256, 64),
    W128_H128("128 × 128", "1:1", 128, 128),
    W128_H64("128 × 64", "2:1", 128, 64),
    W128_H32("128 × 32", "4:1", 128, 32),
    W64_H64("64 × 64", "1:1", 64, 64),
    W64_H32("64 × 32", "2:1", 64, 32),
    W64_H16("64 × 16", "4:1", 64, 16),
    W64_H8("64 × 8", "8:1", 64, 8),
    W32_H32("32 × 32", "1:1", 32, 32),
    W32_H16("32 × 16", "2:1", 32, 16),
    W32_H8("32 × 8", "4:1", 32, 8),
    W16_H16("16 × 16", "1:1", 16, 16),
    W16_H8("16 × 8", "2:1", 16, 8),
    W16_H4("16 × 4", "4:1", 16, 4),
    W8_H8("8 × 8", "1:1", 8, 8),
    W8_H4("8 × 4", "2:1", 8, 4)
}

data class PixelSelection(
    val width: Int,
    val height: Int,
    val mask: BooleanArray,
    val version: Long = 1L
) {
    val selectedCount: Int by lazy(LazyThreadSafetyMode.NONE) {
        var count = 0
        for (b in mask) {
            if (b) count++
        }
        count
    }

    fun isSelected(x: Int, y: Int): Boolean {
        if (x !in 0 until width || y !in 0 until height) return false
        return mask[y * width + x]
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelSelection) return false
        return width == other.width &&
            height == other.height &&
            version == other.version &&
            mask === other.mask
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + version.hashCode()
        return result
    }
}

data class SelectionRect(
    val x0: Int,
    val y0: Int,
    val x1: Int,
    val y1: Int
)

data class PixelClipboard(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val mask: BooleanArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelClipboard) return false
        return width == other.width &&
            height == other.height &&
            pixels.contentEquals(other.pixels) &&
            mask.contentEquals(other.mask)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + pixels.contentHashCode()
        result = 31 * result + mask.contentHashCode()
        return result
    }
}

data class PixelLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val isVisible: Boolean = true,
    val opacity: Float = 1.0f,
    val version: Long = 1L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PixelLayer) return false
        return id == other.id &&
            name == other.name &&
            width == other.width &&
            height == other.height &&
            isVisible == other.isVisible &&
            opacity == other.opacity &&
            version == other.version &&
            pixels === other.pixels
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + isVisible.hashCode()
        result = 31 * result + opacity.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }
}

data class EditorSnapshot(
    val canvasWidth: Int,
    val canvasHeight: Int,
    val layers: List<PixelLayer>,
    val activeLayerId: String,
    val selection: PixelSelection? = null
)

data class EyedropperPreview(
    val x: Int,
    val y: Int,
    val argb: Int
)

data class EditorUiState(
    val currentProjectId: String? = null,
    val currentProjectName: String = "Новый рисунок",
    val canvasWidth: Int = 32,
    val canvasHeight: Int = 32,
    val layers: List<PixelLayer> = emptyList(),
    val activeLayerId: String = "",
    val activeTool: EditorTool = EditorTool.BRUSH,
    val selectionMode: SelectionMode = SelectionMode.RECTANGLE,
    val selection: PixelSelection? = null,
    val selectionPreviewRect: SelectionRect? = null,
    val lassoPreviewPoints: List<Pair<Int, Int>> = emptyList(),
    val hasClipboard: Boolean = false,
    val selectedColorArgb: Int = Color(0xFFD0BCFF).toArgb(),
    val brushSize: Int = 1,
    val fillTolerance: Int = 0,
    val showPixelGrid: Boolean = true,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val zoomScale: Float = 1f,
    val hoveredPixel: Pair<Int, Int>? = null,
    val eyedropperPreview: EyedropperPreview? = null,
    val savedColors: List<Int> = DefaultLilacPalette,
    val recentColors: List<Int> = emptyList(),
    val isExporting: Boolean = false,
    val savedProjects: List<EditorProjectSummary> = emptyList(),
    val isAutoSaving: Boolean = false,
    val lastAutoSavedAtMillis: Long? = null,
    val isLoadingProject: Boolean = false
) {
    val activeLayer: PixelLayer?
        get() = layers.firstOrNull { it.id == activeLayerId } ?: layers.lastOrNull()
}

val DefaultLilacPalette: List<Int> = listOf(
    Color(0xFFD0BCFF).toArgb(), // Soft Lilac
    Color(0xFFEADDFF).toArgb(), // Pale Lavender
    Color(0xFF9A82DB).toArgb(), // Muted Violet
    Color(0xFF6750A4).toArgb(), // Deep Iris
    Color(0xFFEFB8C8).toArgb(), // Dusty Rose
    Color(0xFFF28B82).toArgb(), // Coral Pastel
    Color(0xFFFDD663).toArgb(), // Warm Cream
    Color(0xFF81C995).toArgb(), // Mint Sage
    Color(0xFF8AB4F8).toArgb(), // Periwinkle Blue
    Color(0xFFFFFFFF).toArgb(), // Pure White
    Color(0xFF938F99).toArgb(), // Neutral Slate
    Color(0xFF1C1B1F).toArgb()  // Charcoal Ink
)
