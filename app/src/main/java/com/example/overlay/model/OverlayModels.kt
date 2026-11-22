package com.example.overlay.model

import android.graphics.Bitmap
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class OverlayBackground(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val bitmap: Bitmap
)

enum class OverlayResizeHandle(
    val signX: Int,
    val signY: Int
) {
    LEFT(-1, 0),
    RIGHT(1, 0),
    TOP(0, -1),
    BOTTOM(0, 1),
    TOP_LEFT(-1, -1),
    TOP_RIGHT(1, -1),
    BOTTOM_LEFT(-1, 1),
    BOTTOM_RIGHT(1, 1)
}

data class OverlayLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val offsetX: Float,
    val offsetY: Float,
    val baseFitScale: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
    val isVisible: Boolean = true,
    val pixelCrisp: Boolean = false
) {
    val effectiveScaleX: Float
        get() = (baseFitScale * scaleX).coerceAtLeast(0.005f)

    val effectiveScaleY: Float
        get() = (baseFitScale * scaleY).coerceAtLeast(0.005f)
}

data class OverlayProjectSummary(
    val id: String,
    val name: String,
    val bgWidth: Int,
    val bgHeight: Int,
    val layerCount: Int,
    val updatedAtMillis: Long,
    val previewPath: String? = null
)

data class OverlayUiState(
    val currentProjectId: String? = null,
    val currentProjectName: String = "Новый проект",
    val background: OverlayBackground? = null,
    val layers: List<OverlayLayer> = emptyList(),
    val selectedLayerId: String? = null,
    val preserveAlpha: Boolean = true,
    val showTransformFrame: Boolean = true,
    val isExporting: Boolean = false,
    val savedProjects: List<OverlayProjectSummary> = emptyList(),
    val isAutoSaving: Boolean = false,
    val lastAutoSavedAtMillis: Long? = null,
    val isLoadingProject: Boolean = false
) {
    val selectedLayer: OverlayLayer?
        get() = layers.firstOrNull { it.id == selectedLayerId }
}

data class BackgroundEdgeContact(
    val left: Boolean = false,
    val right: Boolean = false,
    val top: Boolean = false,
    val bottom: Boolean = false
) {
    val any: Boolean get() = left || right || top || bottom
}

object OverlayTransformMath {
    const val MIN_LAYER_SCALE = 0.05f
    const val MAX_LAYER_SCALE = 50.0f
    const val DEFAULT_LAYER_COVERAGE = 0.38f

    fun computeInitialFitScale(
        layerWidth: Int,
        layerHeight: Int,
        bgWidth: Int,
        bgHeight: Int
    ): Float {
        val layerMax = max(layerWidth, layerHeight).coerceAtLeast(1).toFloat()
        val bgMin = min(bgWidth, bgHeight).coerceAtLeast(1).toFloat()
        return ((bgMin * DEFAULT_LAYER_COVERAGE) / layerMax).coerceIn(0.02f, 32f)
    }

    fun normalizeAngleDegrees(degrees: Float): Float {
        var angle = degrees % 360f
        if (angle > 180f) angle -= 360f
        if (angle <= -180f) angle += 360f
        return angle
    }

    fun computeEdgeContact(
        offsetX: Float,
        offsetY: Float,
        layerWidth: Int,
        layerHeight: Int,
        effectiveScaleX: Float,
        effectiveScaleY: Float,
        rotationDegrees: Float,
        bgWidth: Int,
        bgHeight: Int,
        toleranceCanvas: Float
    ): BackgroundEdgeContact {
        val halfW = (layerWidth * effectiveScaleX) / 2f
        val halfH = (layerHeight * effectiveScaleY) / 2f
        val rad = Math.toRadians(rotationDegrees.toDouble())
        val cosA = abs(cos(rad).toFloat())
        val sinA = abs(sin(rad).toFloat())
        val extentX = halfW * cosA + halfH * sinA
        val extentY = halfW * sinA + halfH * cosA

        val layerLeft = offsetX - extentX
        val layerRight = offsetX + extentX
        val layerTop = offsetY - extentY
        val layerBottom = offsetY + extentY

        val bgW = bgWidth.toFloat()
        val bgH = bgHeight.toFloat()
        val tol = toleranceCanvas.coerceAtLeast(0.5f)

        return BackgroundEdgeContact(
            left = abs(layerLeft) <= tol || abs(layerRight) <= tol,
            right = abs(layerRight - bgW) <= tol || abs(layerLeft - bgW) <= tol,
            top = abs(layerTop) <= tol || abs(layerBottom) <= tol,
            bottom = abs(layerBottom - bgH) <= tol || abs(layerTop - bgH) <= tol
        )
    }

    fun computeEdgeContact(
        layer: OverlayLayer,
        bgWidth: Int,
        bgHeight: Int,
        toleranceCanvas: Float
    ): BackgroundEdgeContact = computeEdgeContact(
        offsetX = layer.offsetX,
        offsetY = layer.offsetY,
        layerWidth = layer.width,
        layerHeight = layer.height,
        effectiveScaleX = layer.effectiveScaleX,
        effectiveScaleY = layer.effectiveScaleY,
        rotationDegrees = layer.rotationDegrees,
        bgWidth = bgWidth,
        bgHeight = bgHeight,
        toleranceCanvas = toleranceCanvas
    )

    fun toLayerLocalCoords(
        canvasX: Float,
        canvasY: Float,
        offsetX: Float,
        offsetY: Float,
        rotationDegrees: Float
    ): Pair<Float, Float> {
        val dx = canvasX - offsetX
        val dy = canvasY - offsetY
        val rad = -Math.toRadians(rotationDegrees.toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val localX = dx * cosA - dy * sinA
        val localY = dx * sinA + dy * cosA
        return localX to localY
    }

    fun hitTestLayer(
        canvasX: Float,
        canvasY: Float,
        offsetX: Float,
        offsetY: Float,
        layerWidth: Int,
        layerHeight: Int,
        effectiveScaleX: Float,
        effectiveScaleY: Float,
        rotationDegrees: Float,
        hitPaddingCanvas: Float = 0f
    ): Boolean {
        val (localX, localY) = toLayerLocalCoords(
            canvasX = canvasX,
            canvasY = canvasY,
            offsetX = offsetX,
            offsetY = offsetY,
            rotationDegrees = rotationDegrees
        )
        val halfW = (layerWidth * effectiveScaleX) / 2f + hitPaddingCanvas
        val halfH = (layerHeight * effectiveScaleY) / 2f + hitPaddingCanvas
        return abs(localX) <= halfW && abs(localY) <= halfH
    }

    fun hitTestLayer(
        canvasX: Float,
        canvasY: Float,
        layer: OverlayLayer,
        hitPaddingCanvas: Float = 0f
    ): Boolean = hitTestLayer(
        canvasX = canvasX,
        canvasY = canvasY,
        offsetX = layer.offsetX,
        offsetY = layer.offsetY,
        layerWidth = layer.width,
        layerHeight = layer.height,
        effectiveScaleX = layer.effectiveScaleX,
        effectiveScaleY = layer.effectiveScaleY,
        rotationDegrees = layer.rotationDegrees,
        hitPaddingCanvas = hitPaddingCanvas
    )

    fun findTopmostLayerAt(
        canvasX: Float,
        canvasY: Float,
        layers: List<OverlayLayer>,
        hitPaddingCanvas: Float = 0f
    ): OverlayLayer? {
        for (i in layers.lastIndex downTo 0) {
            val layer = layers[i]
            if (layer.isVisible && hitTestLayer(canvasX, canvasY, layer, hitPaddingCanvas)) {
                return layer
            }
        }
        return null
    }

    fun hitTestResizeHandle(
        canvasX: Float,
        canvasY: Float,
        layer: OverlayLayer,
        handleRadiusCanvas: Float
    ): OverlayResizeHandle? {
        val (localX, localY) = toLayerLocalCoords(
            canvasX = canvasX,
            canvasY = canvasY,
            offsetX = layer.offsetX,
            offsetY = layer.offsetY,
            rotationDegrees = layer.rotationDegrees
        )
        val halfW = (layer.width * layer.effectiveScaleX) / 2f
        val halfH = (layer.height * layer.effectiveScaleY) / 2f

        var bestHandle: OverlayResizeHandle? = null
        var bestDistSq = handleRadiusCanvas * handleRadiusCanvas

        for (handle in OverlayResizeHandle.entries) {
            val hx = handle.signX * halfW
            val hy = handle.signY * halfH
            val dx = localX - hx
            val dy = localY - hy
            val distSq = dx * dx + dy * dy
            if (distSq <= bestDistSq) {
                bestDistSq = distSq
                bestHandle = handle
            }
        }
        return bestHandle
    }

    fun applyHandleDrag(
        layer: OverlayLayer,
        handle: OverlayResizeHandle,
        dxCanvas: Float,
        dyCanvas: Float
    ): OverlayLayer {
        val rad = Math.toRadians(layer.rotationDegrees.toDouble())
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()
        val vx = -uy
        val vy = ux

        val projX = dxCanvas * ux + dyCanvas * uy
        val projY = dxCanvas * vx + dyCanvas * vy

        val baseW = (layer.width * layer.baseFitScale).coerceAtLeast(1f)
        val baseH = (layer.height * layer.baseFitScale).coerceAtLeast(1f)
        val oldHalfW = (baseW * layer.scaleX) / 2f
        val oldHalfH = (baseH * layer.scaleY) / 2f

        var newScaleX = layer.scaleX
        var newHalfW = oldHalfW
        if (handle.signX != 0) {
            val desiredWidth = baseW * layer.scaleX + handle.signX * projX
            newScaleX = (desiredWidth / baseW).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
            newHalfW = (baseW * newScaleX) / 2f
        }

        var newScaleY = layer.scaleY
        var newHalfH = oldHalfH
        if (handle.signY != 0) {
            val desiredHeight = baseH * layer.scaleY + handle.signY * projY
            newScaleY = (desiredHeight / baseH).coerceIn(MIN_LAYER_SCALE, MAX_LAYER_SCALE)
            newHalfH = (baseH * newScaleY) / 2f
        }

        val shiftLocalX = handle.signX * (newHalfW - oldHalfW)
        val shiftLocalY = handle.signY * (newHalfH - oldHalfH)
        val newOffsetX = layer.offsetX + shiftLocalX * ux + shiftLocalY * vx
        val newOffsetY = layer.offsetY + shiftLocalX * uy + shiftLocalY * vy

        return layer.copy(
            offsetX = newOffsetX,
            offsetY = newOffsetY,
            scaleX = newScaleX,
            scaleY = newScaleY
        )
    }
}
