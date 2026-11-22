package com.example

import android.content.Context
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.editor.model.EditorTool
import com.example.editor.model.EditorUiState
import com.example.editor.model.PixelLayer
import com.example.editor.storage.EditorProjectRepository
import com.example.resizer.model.RESIZER_PRESET_SIZES
import com.example.resizer.model.ResampleMode
import com.example.resizer.model.ResizerInputImage
import com.example.resizer.model.ResizerUiState
import com.example.resizer.storage.ResizerProjectRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EditorAndResizerProjectsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        EditorProjectRepository.projectsRoot(context).deleteRecursively()
        ResizerProjectRepository.projectsRoot(context).deleteRecursively()
    }

    @After
    fun tearDown() {
        EditorProjectRepository.projectsRoot(context).deleteRecursively()
        ResizerProjectRepository.projectsRoot(context).deleteRecursively()
    }

    @Test
    fun `editor save and load preserves canvas, layers, pixels, and tool state`() {
        val w = 32
        val h = 16
        val layer1Pixels = IntArray(w * h).apply {
            this[0] = Color.CYAN
            this[3 * w + 7] = Color.MAGENTA
        }
        val layer2Pixels = IntArray(w * h).apply {
            this[10 * w + 20] = Color.argb(180, 255, 100, 50)
        }

        val layer1 = PixelLayer(
            id = "ed-layer-1",
            name = "Фон",
            width = w,
            height = h,
            pixels = layer1Pixels,
            isVisible = true,
            opacity = 0.85f,
            version = 4L
        )
        val layer2 = PixelLayer(
            id = "ed-layer-2",
            name = "Детали",
            width = w,
            height = h,
            pixels = layer2Pixels,
            isVisible = false,
            opacity = 0.4f,
            version = 9L
        )

        val state = EditorUiState(
            currentProjectId = "ed-proj-1",
            currentProjectName = "Скин 32x16",
            canvasWidth = w,
            canvasHeight = h,
            layers = listOf(layer1, layer2),
            activeLayerId = "ed-layer-2",
            activeTool = EditorTool.FILL,
            brushSize = 3,
            fillTolerance = 12,
            showPixelGrid = false
        )

        EditorProjectRepository.saveProject(
            context = context,
            projectId = "ed-proj-1",
            projectName = "Скин 32x16",
            createdAtMillis = 1500L,
            state = state
        )

        val list = EditorProjectRepository.listProjects(context)
        assertEquals(1, list.size)
        val summary = list.first()
        assertEquals("ed-proj-1", summary.id)
        assertEquals("Скин 32x16", summary.name)
        assertEquals(w, summary.canvasWidth)
        assertEquals(h, summary.canvasHeight)
        assertEquals(2, summary.layerCount)
        assertNotNull(summary.previewPath)
        assertTrue(File(summary.previewPath!!).exists())

        val loaded = EditorProjectRepository.loadProject(context, "ed-proj-1")
        assertNotNull(loaded)
        loaded!!
        assertEquals("ed-proj-1", loaded.id)
        assertEquals("Скин 32x16", loaded.name)
        assertEquals(w, loaded.canvasWidth)
        assertEquals(h, loaded.canvasHeight)
        assertEquals("ed-layer-2", loaded.activeLayerId)
        assertEquals(EditorTool.FILL, loaded.activeTool)
        assertEquals(3, loaded.brushSize)
        assertEquals(12, loaded.fillTolerance)
        assertFalse(loaded.showPixelGrid)
        assertEquals(2, loaded.layers.size)

        val l1 = loaded.layers[0]
        assertEquals("ed-layer-1", l1.id)
        assertEquals("Фон", l1.name)
        assertTrue(l1.isVisible)
        assertEquals(0.85f, l1.opacity, 0.001f)
        assertEquals(4L, l1.version)
        assertEquals(Color.CYAN, l1.pixels[0])
        assertEquals(Color.MAGENTA, l1.pixels[3 * w + 7])

        val l2 = loaded.layers[1]
        assertEquals("ed-layer-2", l2.id)
        assertEquals("Детали", l2.name)
        assertFalse(l2.isVisible)
        assertEquals(0.4f, l2.opacity, 0.001f)
        assertEquals(9L, l2.version)
        val loadedSemi = l2.pixels[10 * w + 20]
        assertEquals(180, Color.alpha(loadedSemi))
        assertTrue(kotlin.math.abs(Color.red(loadedSemi) - 255) <= 1)
        assertTrue(kotlin.math.abs(Color.green(loadedSemi) - 100) <= 1)
        assertTrue(kotlin.math.abs(Color.blue(loadedSemi) - 50) <= 1)
    }

    @Test
    fun `editor resave cleans stale layers, rename and delete work`() {
        val w = 16
        val h = 16
        val layerA = PixelLayer(id = "la", name = "A", width = w, height = h, pixels = IntArray(w * h) { Color.RED }, version = 1L)
        val layerB = PixelLayer(id = "lb", name = "B", width = w, height = h, pixels = IntArray(w * h) { Color.BLUE }, version = 1L)

        EditorProjectRepository.saveProject(
            context = context,
            projectId = "ed-proj-2",
            projectName = "Черновик редактора",
            createdAtMillis = 2000L,
            state = EditorUiState(
                currentProjectId = "ed-proj-2",
                currentProjectName = "Черновик редактора",
                canvasWidth = w,
                canvasHeight = h,
                layers = listOf(layerA, layerB),
                activeLayerId = "lb"
            )
        )

        val dir = File(EditorProjectRepository.projectsRoot(context), "ed-proj-2")
        assertTrue(File(dir, "layers/la.png").exists())
        assertTrue(File(dir, "layers/lb.png").exists())

        val updatedPixelsB = IntArray(w * h) { Color.GREEN }
        EditorProjectRepository.saveProject(
            context = context,
            projectId = "ed-proj-2",
            projectName = "Черновик редактора",
            createdAtMillis = 2000L,
            state = EditorUiState(
                currentProjectId = "ed-proj-2",
                currentProjectName = "Черновик редактора",
                canvasWidth = w,
                canvasHeight = h,
                layers = listOf(layerB.copy(pixels = updatedPixelsB, version = 2L)),
                activeLayerId = "lb"
            )
        )

        assertFalse(File(dir, "layers/la.png").exists())
        assertTrue(File(dir, "layers/lb.png").exists())

        assertTrue(EditorProjectRepository.renameProject(context, "ed-proj-2", "Готовый спрайт"))
        val loaded = EditorProjectRepository.loadProject(context, "ed-proj-2")
        assertNotNull(loaded)
        assertEquals("Готовый спрайт", loaded!!.name)
        assertEquals(1, loaded.layers.size)
        assertEquals(Color.GREEN, loaded.layers.first().pixels[0])

        assertTrue(EditorProjectRepository.deleteProject(context, "ed-proj-2"))
        assertNull(EditorProjectRepository.loadProject(context, "ed-proj-2"))
        assertTrue(EditorProjectRepository.listProjects(context).isEmpty())
    }

    @Test
    fun `resizer save and load preserves inputs, presets, resample mode, and alpha setting`() {
        val presetA = RESIZER_PRESET_SIZES.first { it.width == 512 && it.height == 128 }
        val presetB = RESIZER_PRESET_SIZES.first { it.width == 256 && it.height == 64 }

        val input1Pixels = IntArray(64 * 32) { Color.YELLOW }.apply {
            this[2 * 64 + 4] = Color.RED
        }
        val input2Pixels = IntArray(32 * 32) { Color.argb(128, 30, 120, 240) }

        val input1 = ResizerInputImage(
            id = "res-in-1",
            baseName = "livery_wide",
            width = 64,
            height = 32,
            hasAlpha = false,
            pixels = input1Pixels,
            sourceByteSize = 4096L
        )
        val input2 = ResizerInputImage(
            id = "res-in-2",
            baseName = "emblem",
            width = 32,
            height = 32,
            hasAlpha = true,
            pixels = input2Pixels,
            sourceByteSize = 2048L
        )

        val state = ResizerUiState(
            currentProjectId = "res-proj-1",
            currentProjectName = "Ресайз ливреи",
            inputs = listOf(input1, input2),
            activePreviewInputId = "res-in-2",
            selectedPresets = setOf(presetA, presetB),
            previewPreset = presetB,
            resampleMode = ResampleMode.BICUBIC,
            preserveAlpha = false
        )

        ResizerProjectRepository.saveProject(
            context = context,
            projectId = "res-proj-1",
            projectName = "Ресайз ливреи",
            createdAtMillis = 3000L,
            state = state
        )

        val list = ResizerProjectRepository.listProjects(context)
        assertEquals(1, list.size)
        val summary = list.first()
        assertEquals("res-proj-1", summary.id)
        assertEquals("Ресайз ливреи", summary.name)
        assertEquals(256, summary.targetWidth)
        assertEquals(64, summary.targetHeight)
        assertEquals(2, summary.inputCount)
        assertNotNull(summary.previewPath)
        assertTrue(File(summary.previewPath!!).exists())

        val loaded = ResizerProjectRepository.loadProject(context, "res-proj-1")
        assertNotNull(loaded)
        loaded!!
        assertEquals("res-proj-1", loaded.id)
        assertEquals("Ресайз ливреи", loaded.name)
        assertEquals("res-in-2", loaded.activePreviewInputId)
        assertEquals(setOf(presetA, presetB), loaded.selectedPresets)
        assertEquals(presetB, loaded.previewPreset)
        assertEquals(ResampleMode.BICUBIC, loaded.resampleMode)
        assertFalse(loaded.preserveAlpha)
        assertEquals(2, loaded.inputs.size)

        val lIn1 = loaded.inputs[0]
        assertEquals("res-in-1", lIn1.id)
        assertEquals("livery_wide", lIn1.baseName)
        assertEquals(64, lIn1.width)
        assertEquals(32, lIn1.height)
        assertFalse(lIn1.hasAlpha)
        assertEquals(4096L, lIn1.sourceByteSize)
        assertEquals(Color.RED, lIn1.pixels[2 * 64 + 4])

        val lIn2 = loaded.inputs[1]
        assertEquals("res-in-2", lIn2.id)
        assertEquals("emblem", lIn2.baseName)
        assertTrue(lIn2.hasAlpha)
        assertEquals(2048L, lIn2.sourceByteSize)
        val loadedSemiIn2 = lIn2.pixels[0]
        assertEquals(128, Color.alpha(loadedSemiIn2))
        assertTrue(kotlin.math.abs(Color.red(loadedSemiIn2) - 30) <= 1)
        assertTrue(kotlin.math.abs(Color.green(loadedSemiIn2) - 120) <= 1)
        assertTrue(kotlin.math.abs(Color.blue(loadedSemiIn2) - 240) <= 1)
    }

    @Test
    fun `resizer resave removes stale inputs, rename and delete work`() {
        val in1 = ResizerInputImage(id = "in-a", baseName = "a", width = 16, height = 16, hasAlpha = false, pixels = IntArray(256) { Color.WHITE })
        val in2 = ResizerInputImage(id = "in-b", baseName = "b", width = 16, height = 16, hasAlpha = false, pixels = IntArray(256) { Color.BLACK })

        ResizerProjectRepository.saveProject(
            context = context,
            projectId = "res-proj-2",
            projectName = "Пакет 1",
            createdAtMillis = 4000L,
            state = ResizerUiState(
                currentProjectId = "res-proj-2",
                currentProjectName = "Пакет 1",
                inputs = listOf(in1, in2),
                activePreviewInputId = "in-a"
            )
        )

        val dir = File(ResizerProjectRepository.projectsRoot(context), "res-proj-2")
        assertTrue(File(dir, "inputs/in-a.png").exists())
        assertTrue(File(dir, "inputs/in-b.png").exists())

        ResizerProjectRepository.saveProject(
            context = context,
            projectId = "res-proj-2",
            projectName = "Пакет 1",
            createdAtMillis = 4000L,
            state = ResizerUiState(
                currentProjectId = "res-proj-2",
                currentProjectName = "Пакет 1",
                inputs = listOf(in2),
                activePreviewInputId = "in-b"
            )
        )

        assertFalse(File(dir, "inputs/in-a.png").exists())
        assertTrue(File(dir, "inputs/in-b.png").exists())

        assertTrue(ResizerProjectRepository.renameProject(context, "res-proj-2", "Финал ресайза"))
        val loaded = ResizerProjectRepository.loadProject(context, "res-proj-2")
        assertNotNull(loaded)
        assertEquals("Финал ресайза", loaded!!.name)
        assertEquals(1, loaded.inputs.size)
        assertEquals("in-b", loaded.activePreviewInputId)

        assertTrue(ResizerProjectRepository.deleteProject(context, "res-proj-2"))
        assertNull(ResizerProjectRepository.loadProject(context, "res-proj-2"))
        assertTrue(ResizerProjectRepository.listProjects(context).isEmpty())
    }
}
