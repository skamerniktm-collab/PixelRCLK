package com.example

import androidx.compose.ui.geometry.Offset
import com.example.compress.engine.PngCompressor
import com.example.compress.model.CompressPreset
import com.example.compress.model.formatSavingsPercent
import com.example.editor.engine.PixelOperations
import com.example.editor.model.EditorTool
import com.example.editor.model.PixelLayer
import com.example.editor.model.RecolorShadingMode
import com.example.editor.ui.components.computeCanvasDrawMetrics
import com.example.editor.ui.components.screenOffsetToPixel
import com.example.editor.viewmodel.PixelEditorViewModel
import com.example.overlay.model.OverlayTransformMath
import com.example.resizer.engine.ImageResampler
import com.example.resizer.model.RESIZER_PRESET_SIZES
import com.example.resizer.model.ResampleMode
import com.example.resizer.model.ResizePreset
import com.example.resizer.model.formatExactFileSize
import com.example.resizer.viewmodel.ResizerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelEngineTest {

    @Test
    fun `stampBrush 1px draws single pixel and eraser sets alpha to zero`() {
        val pixels = IntArray(16)
        val lilac = 0xFFD0BCFF.toInt()

        val painted = PixelOperations.stampBrush(
            pixels = pixels,
            width = 4,
            height = 4,
            cx = 2,
            cy = 1,
            brushSize = 1,
            argbColor = lilac,
            isEraser = false
        )
        assertTrue(painted)
        assertEquals(lilac, pixels[1 * 4 + 2])
        assertEquals(0, pixels[0])

        val erased = PixelOperations.stampBrush(
            pixels = pixels,
            width = 4,
            height = 4,
            cx = 2,
            cy = 1,
            brushSize = 1,
            argbColor = lilac,
            isEraser = true
        )
        assertTrue(erased)
        assertEquals(0x00000000, pixels[1 * 4 + 2])
    }

    @Test
    fun `floodFill respects closed pixel boundary`() {
        // 5x5 grid with a 3x3 enclosed box at (1..3, 1..3) leaving center (2,2) empty
        val w = 5
        val h = 5
        val pixels = IntArray(w * h)
        val border = 0xFF381E72.toInt()
        val fill = 0xFFD0BCFF.toInt()

        for (x in 1..3) {
            pixels[1 * w + x] = border
            pixels[3 * w + x] = border
        }
        pixels[2 * w + 1] = border
        pixels[2 * w + 3] = border

        val changed = PixelOperations.floodFill(pixels, w, h, 2, 2, fill, tolerance = 0)
        assertTrue(changed)
        assertEquals(fill, pixels[2 * w + 2])
        assertEquals(0, pixels[0])
        assertEquals(border, pixels[1 * w + 1])
    }

    @Test
    fun `mergeDown composites upper semi-transparent layer over lower layer`() {
        val lower = PixelLayer(
            name = "Base",
            width = 2,
            height = 2,
            pixels = intArrayOf(0xFF0000FF.toInt(), 0, 0, 0),
            opacity = 1.0f
        )
        val upper = PixelLayer(
            name = "Overlay",
            width = 2,
            height = 2,
            pixels = intArrayOf(0xFFFF0000.toInt(), 0x80D0BCFF.toInt(), 0, 0),
            opacity = 0.5f
        )

        val merged = PixelOperations.mergeDown(upper = upper, lower = lower)
        assertEquals(1.0f, merged.opacity)
        // Pixel 0 should be ~50% red over 100% blue -> opaque purple
        val p0Alpha = (merged.pixels[0] ushr 24) and 0xFF
        val p0Red = (merged.pixels[0] ushr 16) and 0xFF
        val p0Blue = merged.pixels[0] and 0xFF
        assertEquals(255, p0Alpha)
        assertTrue(p0Red in 120..135)
        assertTrue(p0Blue in 120..135)
        // Pixel 1 had transparent lower + 50% opacity on 0x80 alpha -> ~64 alpha
        val p1Alpha = (merged.pixels[1] ushr 24) and 0xFF
        assertTrue(p1Alpha in 60..68)
    }

    @Test
    fun `viewModel undo redo and layer operations work consistently`() {
        val vm = PixelEditorViewModel()
        assertEquals(1, vm.uiState.value.layers.size)
        assertFalse(vm.uiState.value.canUndo)

        vm.beginCanvasInteraction(5, 5)
        vm.endCanvasInteraction()
        assertTrue(vm.uiState.value.canUndo)
        assertEquals(vm.uiState.value.selectedColorArgb, vm.uiState.value.activeLayer!!.pixels[5 * 32 + 5])

        vm.undo()
        assertFalse(vm.uiState.value.canUndo)
        assertTrue(vm.uiState.value.canRedo)
        assertEquals(0, vm.uiState.value.activeLayer!!.pixels[5 * 32 + 5])

        vm.redo()
        assertEquals(vm.uiState.value.selectedColorArgb, vm.uiState.value.activeLayer!!.pixels[5 * 32 + 5])

        vm.addLayer()
        assertEquals(2, vm.uiState.value.layers.size)
        vm.selectTool(EditorTool.ERASER)
        assertEquals(EditorTool.ERASER, vm.uiState.value.activeTool)
    }

    @Test
    fun `compositeLayersOpaque sets alpha to 255 and reveals RGB behind transparency`() {
        val layer = PixelLayer(
            name = "Texture",
            width = 3,
            height = 1,
            pixels = intArrayOf(
                0x00FF8040,           // hidden RGB with alpha = 0
                0x40D0BCFF,           // semi-transparent lilac
                0x00000000            // empty transparent -> opaque black
            )
        )
        val opaque = PixelOperations.compositeLayersOpaque(listOf(layer), 3, 1)
        assertEquals(0xFFFF8040.toInt(), opaque[0])
        assertEquals(0xFFD0BCFF.toInt(), opaque[1])
        assertEquals(0xFF000000.toInt(), opaque[2])
    }

    @Test
    fun `selection mask clips brush and translates pixels accurately`() {
        val vm = PixelEditorViewModel()
        val lilac = 0xFFD0BCFF.toInt()
        vm.selectColor(lilac)

        // Select 2x2 rectangle from (2,2) to (3,3)
        vm.selectTool(EditorTool.SELECT)
        vm.beginCanvasInteraction(2, 2)
        vm.continueCanvasInteraction(3, 3)
        vm.endCanvasInteraction()

        val selection = vm.uiState.value.selection!!
        assertEquals(4, selection.selectedCount)

        // Paint inside and outside selection: only inside (2..3, 2..3) should be painted
        vm.selectTool(EditorTool.BRUSH)
        vm.setBrushSize(5)
        vm.beginCanvasInteraction(2, 2)
        vm.endCanvasInteraction()

        val pixelsAfterPaint = vm.uiState.value.activeLayer!!.pixels
        assertEquals(lilac, pixelsAfterPaint[2 * 32 + 2])
        assertEquals(lilac, pixelsAfterPaint[3 * 32 + 3])
        assertEquals(0, pixelsAfterPaint[1 * 32 + 1])

        // Move selected pixels by (+4, +2)
        vm.selectTool(EditorTool.SELECT)
        vm.setSelectionMode(com.example.editor.model.SelectionMode.MOVE)
        vm.beginCanvasInteraction(2, 2)
        vm.continueCanvasInteraction(6, 4)
        vm.endCanvasInteraction()

        val pixelsAfterMove = vm.uiState.value.activeLayer!!.pixels
        assertEquals(0, pixelsAfterMove[2 * 32 + 2])
        assertEquals(lilac, pixelsAfterMove[4 * 32 + 6])
        assertEquals(lilac, pixelsAfterMove[5 * 32 + 7])

        // Copy, clear, and paste
        assertTrue(vm.copySelection())
        vm.deleteSelectedPixels()
        assertEquals(0, vm.uiState.value.activeLayer!!.pixels[4 * 32 + 6])
        assertTrue(vm.pasteClipboard())
        assertTrue(vm.uiState.value.selection != null)
    }

    @Test
    fun `lasso and magic wand selection masks select expected regions`() {
        val lassoMask = PixelOperations.createLassoSelectionMask(
            width = 6,
            height = 6,
            points = listOf(1 to 1, 4 to 1, 4 to 4, 1 to 4)
        )
        assertTrue(lassoMask[2 * 6 + 2])
        assertTrue(lassoMask[1 * 6 + 1])
        assertFalse(lassoMask[0])

        val pixels = IntArray(16)
        val accent = 0xFF9A82DB.toInt()
        pixels[1 * 4 + 1] = accent
        pixels[1 * 4 + 2] = accent
        pixels[2 * 4 + 2] = accent

        val wandMask = PixelOperations.createMagicWandMask(pixels, 4, 4, 1, 1, tolerance = 0)
        assertTrue(wandMask[1 * 4 + 1])
        assertTrue(wandMask[1 * 4 + 2])
        assertTrue(wandMask[2 * 4 + 2])
        assertFalse(wandMask[0])
    }

    @Test
    fun `hand tool interaction does not alter pixels or undo history`() {
        val vm = PixelEditorViewModel()
        vm.selectTool(EditorTool.HAND)
        vm.beginCanvasInteraction(4, 4)
        vm.continueCanvasInteraction(10, 12)
        vm.endCanvasInteraction()

        assertFalse(vm.uiState.value.canUndo)
        assertTrue(vm.uiState.value.activeLayer!!.pixels.all { it == 0 })
    }

    @Test
    fun `fill tool executes on release and rolls back cleanly when cancelled for pinch`() {
        val vm = PixelEditorViewModel()
        val lilac = 0xFFD0BCFF.toInt()
        vm.selectColor(lilac)
        vm.selectTool(EditorTool.FILL)

        // Cancelled by 2-finger pinch before release: no fill occurs
        vm.beginCanvasInteraction(1, 1)
        vm.cancelActiveStrokeForGesture()
        assertFalse(vm.uiState.value.canUndo)
        assertEquals(0, vm.uiState.value.activeLayer!!.pixels[1 * 32 + 1])

        // Clean single-finger tap & release: fills the canvas
        vm.beginCanvasInteraction(1, 1)
        vm.endCanvasInteraction()
        assertTrue(vm.uiState.value.canUndo)
        assertEquals(lilac, vm.uiState.value.activeLayer!!.pixels[1 * 32 + 1])
    }

    @Test
    fun `drawing and eyedropper record recent color history and saved palette slots`() {
        val vm = PixelEditorViewModel()
        val customMint = 0xFF4CE0B3.toInt()
        val customPeach = 0xFFFFAB91.toInt()

        // Preview + paint with Brush -> records customMint in recentColors
        vm.previewColor(customMint)
        assertFalse(customMint in vm.uiState.value.recentColors)
        vm.selectTool(EditorTool.BRUSH)
        vm.beginCanvasInteraction(0, 0)
        vm.endCanvasInteraction()
        assertEquals(customMint, vm.uiState.value.recentColors.first())

        // Sample with Eyedropper -> switches selected color & promotes in recentColors
        vm.previewColor(customPeach)
        vm.selectTool(EditorTool.BRUSH)
        vm.beginCanvasInteraction(2, 2)
        vm.endCanvasInteraction()
        assertEquals(customPeach, vm.uiState.value.recentColors.first())

        vm.selectTool(EditorTool.EYEDROPPER)
        vm.beginCanvasInteraction(0, 0)
        vm.endCanvasInteraction()
        assertEquals(customMint, vm.uiState.value.selectedColorArgb)
        assertEquals(customMint, vm.uiState.value.recentColors.first())

        // Save and remove from saved palette
        assertTrue(vm.toggleSaveColor(customMint))
        assertTrue(customMint in vm.uiState.value.savedColors)
        assertFalse(vm.toggleSaveColor(customMint))
        assertFalse(customMint in vm.uiState.value.savedColors)

        // Selecting from strip does not reorder history list
        val historyBefore = vm.uiState.value.recentColors
        vm.selectPaletteColor(customPeach)
        assertEquals(customPeach, vm.uiState.value.selectedColorArgb)
        assertEquals(historyBefore, vm.uiState.value.recentColors)
    }

    @Test
    fun `screenOffsetToPixel maps every drawn pixel center accurately across surface heights`() {
        val canvasW = 32
        val canvasH = 32
        val basePixelSize = 28.3f
        val heights = listOf(1200f, 1340f, 1580f)
        for (h in heights) {
            val metrics = computeCanvasDrawMetrics(
                surfaceWidth = 1080f,
                surfaceHeight = h,
                canvasWidth = canvasW,
                canvasHeight = canvasH,
                basePixelSize = basePixelSize,
                zoom = 1.75f,
                panOffset = Offset(14.3f, -27.8f)
            )
            for (py in 0 until canvasH) {
                for (px in 0 until canvasW) {
                    val center = Offset(
                        metrics.left + (px + 0.5f) * metrics.actualPixelW,
                        metrics.top + (py + 0.5f) * metrics.actualPixelH
                    )
                    assertEquals(px to py, screenOffsetToPixel(center, metrics, canvasW, canvasH))
                }
            }
        }
    }

    @Test
    fun `recolorSelectedPixels preserves light shadow hierarchy alpha and unselected pixels`() {
        val vm = PixelEditorViewModel()
        // Populate 3 shaded pixels + 1 transparent pixel + 1 unselected pixel on active layer
        val darkRed = 0xFF401010.toInt()
        val midRed = 0xAA902828.toInt() // semi-transparent!
        val brightRed = 0xFFFFB0B0.toInt()
        val blackOutline = 0xFF000000.toInt()
        val outsideBlue = 0xFF2050C0.toInt()

        val layer = vm.uiState.value.activeLayer!!
        layer.pixels[0] = darkRed
        layer.pixels[1] = midRed
        layer.pixels[2] = brightRed
        layer.pixels[3] = blackOutline
        layer.pixels[32] = outsideBlue // row 1, col 0 (outside selection)

        // Select row 0 cols 0..4 (includes transparent pixel at col 4)
        vm.selectTool(EditorTool.SELECT)
        vm.beginCanvasInteraction(0, 0)
        vm.continueCanvasInteraction(4, 0)
        vm.endCanvasInteraction()

        val greenTarget = 0xFF2ECC71.toInt()
        assertTrue(vm.recolorSelectedPixels(greenTarget, RecolorShadingMode.LUMINANCE, 1f))
        assertTrue(vm.uiState.value.canUndo)

        val result = vm.uiState.value.activeLayer!!.pixels
        fun luma(c: Int): Float {
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            return 0.299f * r + 0.587f * g + 0.114f * b
        }

        // Alpha preserved on semi-transparent and transparent pixels
        assertEquals(0xAA, (result[1] ushr 24) and 0xFF)
        assertEquals(0, result[4])
        // Black outline stays black
        assertEquals(blackOutline, result[3])
        // Unselected pixel untouched
        assertEquals(outsideBlue, result[32])
        // Light & shadow ordering preserved (dark < mid < bright), and green channel now dominates red
        assertTrue(luma(result[0]) < luma(result[1]))
        assertTrue(luma(result[1]) < luma(result[2]))
        assertTrue(((result[1] ushr 8) and 0xFF) > ((result[1] ushr 16) and 0xFF))

        // Undo restores original pixels cleanly
        vm.undo()
        assertEquals(midRed, vm.uiState.value.activeLayer!!.pixels[1])

        // Positive and negative brightnessShift adjust brightness while preserving shadow ordering
        val brighter = PixelOperations.recolorPixelWithShading(
            srcArgb = darkRed,
            targetArgb = greenTarget,
            mode = RecolorShadingMode.LUMINANCE,
            strength = 1f,
            brightnessShift = 0.4f
        )
        val neutral = PixelOperations.recolorPixelWithShading(
            srcArgb = darkRed,
            targetArgb = greenTarget,
            mode = RecolorShadingMode.LUMINANCE,
            strength = 1f,
            brightnessShift = 0f
        )
        val darker = PixelOperations.recolorPixelWithShading(
            srcArgb = brightRed,
            targetArgb = greenTarget,
            mode = RecolorShadingMode.LUMINANCE,
            strength = 1f,
            brightnessShift = -0.4f
        )
        val neutralBright = PixelOperations.recolorPixelWithShading(
            srcArgb = brightRed,
            targetArgb = greenTarget,
            mode = RecolorShadingMode.LUMINANCE,
            strength = 1f,
            brightnessShift = 0f
        )
        assertTrue(luma(brighter) > luma(neutral))
        assertTrue(luma(darker) < luma(neutralBright))

        // White pixel recolors cleanly when recolorWhite=true, and stays pure white when false
        val pureWhite = 0xFFFFFFFF.toInt()
        val lilacTarget = 0xFFD0BCFF.toInt()
        val recoloredWhite = PixelOperations.recolorPixelWithShading(
            srcArgb = pureWhite,
            targetArgb = lilacTarget,
            mode = RecolorShadingMode.LUMINANCE,
            strength = 1f,
            brightnessShift = 0f,
            recolorWhite = true
        )
        assertTrue(recoloredWhite != pureWhite)
        assertTrue((recoloredWhite and 0xFF) > ((recoloredWhite ushr 8) and 0xFF))

        val preservedWhite = PixelOperations.recolorPixelWithShading(
            srcArgb = pureWhite,
            targetArgb = lilacTarget,
            mode = RecolorShadingMode.LUMINANCE,
            strength = 1f,
            brightnessShift = 0f,
            recolorWhite = false
        )
        assertEquals(pureWhite, preservedWhite)
    }

    @Test
    fun `resizer preset catalog matches all 31 presets including 256x32, 128x16, 64x8 and 32x4`() {
        assertEquals(31, RESIZER_PRESET_SIZES.size)
        assertTrue(RESIZER_PRESET_SIZES.any { it.width == 512 && it.height == 256 })
        assertTrue(RESIZER_PRESET_SIZES.any { it.width == 1024 && it.height == 512 })
        assertTrue(RESIZER_PRESET_SIZES.any { it.width == 256 && it.height == 32 })
        assertTrue(RESIZER_PRESET_SIZES.any { it.width == 128 && it.height == 16 })
        assertTrue(RESIZER_PRESET_SIZES.any { it.width == 64 && it.height == 8 })
        assertTrue(RESIZER_PRESET_SIZES.any { it.width == 32 && it.height == 4 })
        assertTrue(RESIZER_PRESET_SIZES.any { it.width == 512 && it.height == 512 })
        assertTrue(RESIZER_PRESET_SIZES.any { it.width == 4 && it.height == 8 })
        assertEquals(1024 to 512, RESIZER_PRESET_SIZES.first().let { it.width to it.height })
        for (i in 0 until RESIZER_PRESET_SIZES.lastIndex) {
            assertTrue(RESIZER_PRESET_SIZES[i].area >= RESIZER_PRESET_SIZES[i + 1].area)
        }
    }

    @Test
    fun `image resampler supports all four modes and alpha handling`() {
        // 4x4 checkerboard of transparent (0x00000000) and semi-transparent lilac (0x80D0BCFF)
        val src = IntArray(16) { idx ->
            val x = idx % 4
            val y = idx / 4
            if ((x + y) % 2 == 0) 0x80D0BCFF.toInt() else 0x00000000
        }

        for (mode in ResampleMode.entries) {
            val outAlpha = ImageResampler.resizePixels(
                srcPixels = src,
                srcWidth = 4,
                srcHeight = 4,
                targetWidth = 2,
                targetHeight = 2,
                mode = mode,
                hasAlpha = true
            )
            assertEquals(4, outAlpha.size)

            val outOpaque = ImageResampler.resizePixels(
                srcPixels = src,
                srcWidth = 4,
                srcHeight = 4,
                targetWidth = 2,
                targetHeight = 2,
                mode = mode,
                hasAlpha = false
            )
            assertEquals(4, outOpaque.size)
            assertTrue(outOpaque.all { ((it ushr 24) and 0xFF) == 0xFF })
        }

        // NEAREST keeps crisp unblended sample values when scaling down/up
        val nearestOut = ImageResampler.resizePixels(
            srcPixels = src,
            srcWidth = 4,
            srcHeight = 4,
            targetWidth = 8,
            targetHeight = 8,
            mode = ResampleMode.NEAREST,
            hasAlpha = true
        )
        assertEquals(64, nearestOut.size)
        assertTrue(nearestOut.all { it == 0x80D0BCFF.toInt() || it == 0x00000000 })
    }

    @Test
    fun `resizer viewModel manages presets and editor roundtrip`() {
        val editorVm = PixelEditorViewModel()
        editorVm.selectColor(0xFF9A82DB.toInt())
        editorVm.beginCanvasInteraction(1, 1)
        editorVm.endCanvasInteraction()

        val (w, h, composited) = editorVm.exportCurrentCanvasForResizer()
        val resizerVm = ResizerViewModel()
        resizerVm.addFromEditorCanvas("sprite", w, h, composited)

        val stateAfterImport = resizerVm.uiState.value
        assertEquals(1, stateAfterImport.inputs.size)
        assertTrue(stateAfterImport.activeInput!!.hasAlpha)

        // Selecting a preset replaces previous selection (single-select) and updates previewPreset
        val targetPreset = RESIZER_PRESET_SIZES.first { it.width == 64 && it.height == 64 }
        resizerVm.togglePreset(targetPreset)
        assertEquals(listOf(targetPreset), resizerVm.uiState.value.effectivePresets)
        assertEquals(targetPreset, resizerVm.uiState.value.previewPreset)

        // Roundtrip resized pixels back into Editor
        val resized = ImageResampler.resizePixels(
            srcPixels = composited,
            srcWidth = w,
            srcHeight = h,
            targetWidth = 64,
            targetHeight = 64,
            mode = ResampleMode.NEAREST,
            hasAlpha = true
        )
        assertTrue(editorVm.loadImagePixelsIntoCanvas("sprite_64x64", 64, 64, resized))
        assertEquals(64, editorVm.uiState.value.canvasWidth)
        assertEquals(64, editorVm.uiState.value.canvasHeight)
    }

    @Test
    fun `formatExactFileSize formats bytes kilobytes and megabytes accurately`() {
        assertEquals("842 Б", formatExactFileSize(842L))
        assertEquals("4.18 КБ (4 281 Б)", formatExactFileSize(4281L))
        assertEquals("1.25 МБ (1 310 720 Б)", formatExactFileSize(1_310_720L))
    }

    @Test
    fun `overlay transform math handles rotation normalization fit scale and hit testing`() {
        assertEquals(0f, OverlayTransformMath.normalizeAngleDegrees(360f), 0.01f)
        assertEquals(-90f, OverlayTransformMath.normalizeAngleDegrees(270f), 0.01f)
        assertEquals(90f, OverlayTransformMath.normalizeAngleDegrees(-270f), 0.01f)

        val fit = OverlayTransformMath.computeInitialFitScale(
            layerWidth = 100,
            layerHeight = 50,
            bgWidth = 1000,
            bgHeight = 1000
        )
        // 38% of 1000 = 380 / 100 = 3.8f
        assertEquals(3.8f, fit, 0.01f)

        // Rectangular layer 100x20 rotated by 90 deg becomes 20x100 visually centered at (500, 500)
        assertTrue(
            OverlayTransformMath.hitTestLayer(
                canvasX = 500f,
                canvasY = 540f,
                offsetX = 500f,
                offsetY = 500f,
                layerWidth = 100,
                layerHeight = 20,
                effectiveScaleX = 1f,
                effectiveScaleY = 1f,
                rotationDegrees = 90f
            )
        )
        assertFalse(
            OverlayTransformMath.hitTestLayer(
                canvasX = 540f,
                canvasY = 500f,
                offsetX = 500f,
                offsetY = 500f,
                layerWidth = 100,
                layerHeight = 20,
                effectiveScaleX = 1f,
                effectiveScaleY = 1f,
                rotationDegrees = 90f
            )
        )

        // Non-uniform stretch (2x wider, 0.5x shorter) expands hit-test independently along X
        assertTrue(
            OverlayTransformMath.hitTestLayer(
                canvasX = 590f,
                canvasY = 500f,
                offsetX = 500f,
                offsetY = 500f,
                layerWidth = 100,
                layerHeight = 40,
                effectiveScaleX = 2f,
                effectiveScaleY = 0.5f,
                rotationDegrees = 0f
            )
        )
        assertFalse(
            OverlayTransformMath.hitTestLayer(
                canvasX = 500f,
                canvasY = 515f,
                offsetX = 500f,
                offsetY = 500f,
                layerWidth = 100,
                layerHeight = 40,
                effectiveScaleX = 2f,
                effectiveScaleY = 0.5f,
                rotationDegrees = 0f
            )
        )
    }

    @Test
    fun `pngCompressor produces valid PNG signature chunks and shrinks size with quantization and scaling`() {
        val w = 128
        val h = 128
        // Realistic photo-like textured pattern + transparent/semi-transparent border
        val rng = java.util.Random(42L)
        val pixels = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            val a = if (x < 8) 0 else if (x < 16) 128 else 255
            val r = (x * 2 + rng.nextInt(45)).coerceIn(0, 255)
            val g = (y * 2 + rng.nextInt(45)).coerceIn(0, 255)
            val b = (((x * 3) xor (y * 5)) + rng.nextInt(45)).coerceIn(0, 255)
            (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        val lossless = PngCompressor.compressToPng(
            srcPixels = pixels,
            srcWidth = w,
            srcHeight = h,
            scalePercent = 100,
            maxColors = null,
            preserveAlpha = true
        )
        val indexed64 = PngCompressor.compressToPng(
            srcPixels = pixels,
            srcWidth = w,
            srcHeight = h,
            scalePercent = 100,
            maxColors = 64,
            preserveAlpha = true
        )
        val packed16Scaled = PngCompressor.compressToPng(
            srcPixels = pixels,
            srcWidth = w,
            srcHeight = h,
            scalePercent = 50,
            maxColors = 16,
            preserveAlpha = true
        )

        fun assertValidPngBytes(bytes: ByteArray, expectPlte: Boolean, expectTrns: Boolean) {
            // 8-byte PNG signature
            val sig = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            assertTrue(bytes.size > 33)
            for (i in sig.indices) {
                assertEquals(sig[i], bytes[i])
            }
            val text = String(bytes, Charsets.ISO_8859_1)
            assertTrue(text.contains("IHDR"))
            assertTrue(text.contains("IDAT"))
            assertTrue(text.contains("IEND"))
            assertEquals(expectPlte, text.contains("PLTE"))
            assertEquals(expectTrns, text.contains("tRNS"))
        }

        assertValidPngBytes(lossless.pngBytes, expectPlte = false, expectTrns = false)
        assertValidPngBytes(indexed64.pngBytes, expectPlte = true, expectTrns = true)
        assertValidPngBytes(packed16Scaled.pngBytes, expectPlte = true, expectTrns = true)

        assertEquals(128, indexed64.width)
        assertEquals(128, indexed64.height)
        assertEquals(64, packed16Scaled.width)
        assertEquals(64, packed16Scaled.height)

        // Quantized PNG must be substantially smaller than full-color PNG, and 50%+16colors even smaller
        assertTrue(indexed64.pngBytes.size < lossless.pngBytes.size)
        assertTrue(packed16Scaled.pngBytes.size < indexed64.pngBytes.size)

        // Preserves transparent pixels when preserveAlpha = true
        assertEquals(0, (indexed64.pixels[0] ushr 24) and 0xFF)

        // Strips alpha cleanly when preserveAlpha = false
        val opaqueOut = PngCompressor.compressToPng(
            srcPixels = pixels,
            srcWidth = w,
            srcHeight = h,
            scalePercent = 100,
            maxColors = 32,
            preserveAlpha = false
        )
        assertValidPngBytes(opaqueOut.pngBytes, expectPlte = true, expectTrns = false)
        assertTrue(opaqueOut.pixels.all { ((it ushr 24) and 0xFF) == 0xFF })

        assertEquals("−50.0%", formatSavingsPercent(50f))
        assertEquals(100, CompressPreset.OPTIMAL.scalePercent)
    }

    @Test
    fun `overlay edge contact detects touching background boundaries for unrotated and rotated layers`() {
        // 512x128 background, 100x60 layer centered at (256, 64) -> touches no edge
        val centered = OverlayTransformMath.computeEdgeContact(
            offsetX = 256f,
            offsetY = 64f,
            layerWidth = 100,
            layerHeight = 60,
            effectiveScaleX = 1f,
            effectiveScaleY = 1f,
            rotationDegrees = 0f,
            bgWidth = 512,
            bgHeight = 128,
            toleranceCanvas = 3f
        )
        assertFalse(centered.any)

        // Layer stretched/moved so left edge sits at x=1 and bottom edge at y=128
        val touchedLeftBottom = OverlayTransformMath.computeEdgeContact(
            offsetX = 51f, // left = 51 - 50 = 1 (within tol 3)
            offsetY = 98f, // bottom = 98 + 30 = 128
            layerWidth = 100,
            layerHeight = 60,
            effectiveScaleX = 1f,
            effectiveScaleY = 1f,
            rotationDegrees = 0f,
            bgWidth = 512,
            bgHeight = 128,
            toleranceCanvas = 3f
        )
        assertTrue(touchedLeftBottom.left)
        assertFalse(touchedLeftBottom.right)
        assertFalse(touchedLeftBottom.top)
        assertTrue(touchedLeftBottom.bottom)

        // Rotated 90deg 100x40 layer has half-extent X=20, Y=50 -> top edge touches y=0 when offsetY=50
        val rotatedTop = OverlayTransformMath.computeEdgeContact(
            offsetX = 492f, // right = 492 + 20 = 512
            offsetY = 50f,  // top = 50 - 50 = 0
            layerWidth = 100,
            layerHeight = 40,
            effectiveScaleX = 1f,
            effectiveScaleY = 1f,
            rotationDegrees = 90f,
            bgWidth = 512,
            bgHeight = 128,
            toleranceCanvas = 2f
        )
        assertTrue(rotatedTop.right)
        assertTrue(rotatedTop.top)
        assertFalse(rotatedTop.left)
        assertFalse(rotatedTop.bottom)
    }
}
