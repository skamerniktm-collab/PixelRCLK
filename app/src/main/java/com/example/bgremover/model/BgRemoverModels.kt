package com.example.bgremover.model

import java.util.UUID

enum class BgRemovalMethod(
    val label: String,
    val description: String
) {
    EDGE_FLOOD(
        label = "Авто-фон",
        description = "Определяет цвет фона по краям и аккуратно удаляет его от границ"
    ),
    COLOR_KEY(
        label = "Цвет / Пипетка",
        description = "Удаляет выбранный цвет фона по всему изображению (нажмите пипеткой на фон)"
    ),
    SMART_SUBJECT(
        label = "Объект",
        description = "Локальное выделение центрального объекта и очистка сложного фона"
    )
}

enum class BgTouchTool(val label: String) {
    VIEW("Обзор"),
    PICK_COLOR("Пипетка"),
    ERASE("Ластик"),
    RESTORE("Вернуть")
}

data class BgRemoverSettings(
    val method: BgRemovalMethod = BgRemovalMethod.EDGE_FLOOD,
    val sensitivity: Int = 28,
    val edgeSoftness: Int = 20,
    val removeInnerHoles: Boolean = false,
    val targetColorArgb: Int? = null
) {
    fun normalized(): BgRemoverSettings = copy(
        sensitivity = sensitivity.coerceIn(1, 100),
        edgeSoftness = edgeSoftness.coerceIn(0, 100)
    )
}

data class BgRemoverItem(
    val id: String = UUID.randomUUID().toString(),
    val baseName: String,
    val width: Int,
    val height: Int,
    val originalPixels: IntArray,
    val processedPixels: IntArray,
    val detectedBgColorArgb: Int,
    val appliedSettings: BgRemoverSettings,
    val sourceByteSize: Long,
    val transparentPercent: Int,
    val version: Long = 1L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BgRemoverItem) return false
        return id == other.id &&
            baseName == other.baseName &&
            width == other.width &&
            height == other.height &&
            detectedBgColorArgb == other.detectedBgColorArgb &&
            appliedSettings == other.appliedSettings &&
            sourceByteSize == other.sourceByteSize &&
            transparentPercent == other.transparentPercent &&
            version == other.version &&
            originalPixels === other.originalPixels &&
            processedPixels === other.processedPixels
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + baseName.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + detectedBgColorArgb
        result = 31 * result + appliedSettings.hashCode()
        result = 31 * result + sourceByteSize.hashCode()
        result = 31 * result + transparentPercent
        result = 31 * result + version.hashCode()
        return result
    }
}

data class BgRemoverUiState(
    val items: List<BgRemoverItem> = emptyList(),
    val activeItemId: String? = null,
    val settings: BgRemoverSettings = BgRemoverSettings(),
    val touchTool: BgTouchTool = BgTouchTool.VIEW,
    val brushSizePx: Int = 24,
    val showOriginalComparison: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isApplyingMask: Boolean = false,
    val isProcessing: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val lastSavedSummary: String? = null
) {
    val activeItem: BgRemoverItem?
        get() = items.firstOrNull { it.id == activeItemId } ?: items.firstOrNull()
}
