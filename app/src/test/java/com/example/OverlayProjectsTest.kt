package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.overlay.model.OverlayBackground
import com.example.overlay.model.OverlayLayer
import com.example.overlay.model.OverlayUiState
import com.example.overlay.storage.OverlayProjectRepository
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
class OverlayProjectsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OverlayProjectRepository.projectsRoot(context).deleteRecursively()
    }

    @After
    fun tearDown() {
        OverlayProjectRepository.projectsRoot(context).deleteRecursively()
    }

    @Test
    fun `save and load project preserves background, layers, positions, sizes, and settings`() {
        val bgBitmap = Bitmap.createBitmap(512, 128, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.DKGRAY)
            setPixel(10, 20, Color.MAGENTA)
        }
        val layer1Bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
            setPixel(5, 5, Color.RED)
        }
        val layer2Bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.YELLOW)
        }

        val bg = OverlayBackground(
            id = "bg-1",
            name = "car_skin",
            width = 512,
            height = 128,
            hasAlpha = false,
            bitmap = bgBitmap
        )
        val layer1 = OverlayLayer(
            id = "layer-1",
            name = "decal_left",
            bitmap = layer1Bitmap,
            width = 64,
            height = 32,
            hasAlpha = true,
            offsetX = -18.5f,
            offsetY = 74.25f,
            baseFitScale = 0.76f,
            scaleX = 2.4f,
            scaleY = 1.35f,
            rotationDegrees = -32.5f,
            opacity = 0.72f,
            isVisible = true,
            pixelCrisp = true
        )
        val layer2 = OverlayLayer(
            id = "layer-2",
            name = "logo_top",
            bitmap = layer2Bitmap,
            width = 40,
            height = 40,
            hasAlpha = true,
            offsetX = 520.0f,
            offsetY = 140.0f,
            baseFitScale = 1.2f,
            scaleX = 0.85f,
            scaleY = 3.1f,
            rotationDegrees = 145f,
            opacity = 0.45f,
            isVisible = false,
            pixelCrisp = false
        )

        val state = OverlayUiState(
            currentProjectId = "proj-abc",
            currentProjectName = "Ливрея 512x128",
            background = bg,
            layers = listOf(layer1, layer2),
            selectedLayerId = "layer-1",
            preserveAlpha = false,
            showTransformFrame = false
        )

        OverlayProjectRepository.saveProject(
            context = context,
            projectId = "proj-abc",
            projectName = "Ливрея 512x128",
            createdAtMillis = 1000L,
            state = state
        )

        val list = OverlayProjectRepository.listProjects(context)
        assertEquals(1, list.size)
        val summary = list.first()
        assertEquals("proj-abc", summary.id)
        assertEquals("Ливрея 512x128", summary.name)
        assertEquals(512, summary.bgWidth)
        assertEquals(128, summary.bgHeight)
        assertEquals(2, summary.layerCount)
        assertNotNull(summary.previewPath)
        assertTrue(File(summary.previewPath!!).exists())

        val loaded = OverlayProjectRepository.loadProject(context, "proj-abc")
        assertNotNull(loaded)
        loaded!!
        assertEquals("proj-abc", loaded.id)
        assertEquals("Ливрея 512x128", loaded.name)
        assertFalse(loaded.preserveAlpha)
        assertFalse(loaded.showTransformFrame)
        assertEquals("layer-1", loaded.selectedLayerId)
        assertEquals(512, loaded.background.width)
        assertEquals(128, loaded.background.height)
        assertEquals(Color.MAGENTA, loaded.background.bitmap.getPixel(10, 20))

        assertEquals(2, loaded.layers.size)
        val loadedL1 = loaded.layers[0]
        assertEquals("layer-1", loadedL1.id)
        assertEquals("decal_left", loadedL1.name)
        assertEquals(-18.5f, loadedL1.offsetX, 0.001f)
        assertEquals(74.25f, loadedL1.offsetY, 0.001f)
        assertEquals(0.76f, loadedL1.baseFitScale, 0.001f)
        assertEquals(2.4f, loadedL1.scaleX, 0.001f)
        assertEquals(1.35f, loadedL1.scaleY, 0.001f)
        assertEquals(-32.5f, loadedL1.rotationDegrees, 0.001f)
        assertEquals(0.72f, loadedL1.opacity, 0.001f)
        assertTrue(loadedL1.isVisible)
        assertTrue(loadedL1.pixelCrisp)
        assertEquals(Color.RED, loadedL1.bitmap.getPixel(5, 5))

        val loadedL2 = loaded.layers[1]
        assertEquals("layer-2", loadedL2.id)
        assertEquals(520.0f, loadedL2.offsetX, 0.001f)
        assertEquals(140.0f, loadedL2.offsetY, 0.001f)
        assertEquals(0.85f, loadedL2.scaleX, 0.001f)
        assertEquals(3.1f, loadedL2.scaleY, 0.001f)
        assertEquals(145f, loadedL2.rotationDegrees, 0.001f)
        assertEquals(0.45f, loadedL2.opacity, 0.001f)
        assertFalse(loadedL2.isVisible)
        assertFalse(loadedL2.pixelCrisp)
    }

    @Test
    fun `resaving removes stale layers, rename updates metadata, delete removes files`() {
        val bg = OverlayBackground(
            id = "bg-2",
            name = "base",
            width = 128,
            height = 128,
            hasAlpha = true,
            bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        )
        val layerA = OverlayLayer(
            id = "layer-a",
            name = "A",
            bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888),
            width = 16,
            height = 16,
            hasAlpha = true,
            offsetX = 64f,
            offsetY = 64f,
            baseFitScale = 2f
        )
        val layerB = OverlayLayer(
            id = "layer-b",
            name = "B",
            bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888),
            width = 16,
            height = 16,
            hasAlpha = true,
            offsetX = 32f,
            offsetY = 32f,
            baseFitScale = 1f
        )

        OverlayProjectRepository.saveProject(
            context = context,
            projectId = "proj-2",
            projectName = "Черновик",
            createdAtMillis = 2000L,
            state = OverlayUiState(
                currentProjectId = "proj-2",
                currentProjectName = "Черновик",
                background = bg,
                layers = listOf(layerA, layerB),
                selectedLayerId = "layer-b"
            )
        )

        val projectFolder = File(OverlayProjectRepository.projectsRoot(context), "proj-2")
        assertTrue(File(projectFolder, "layers/layer-a.png").exists())
        assertTrue(File(projectFolder, "layers/layer-b.png").exists())

        // Remove layerA and resize layerB
        OverlayProjectRepository.saveProject(
            context = context,
            projectId = "proj-2",
            projectName = "Черновик",
            createdAtMillis = 2000L,
            state = OverlayUiState(
                currentProjectId = "proj-2",
                currentProjectName = "Черновик",
                background = bg,
                layers = listOf(layerB.copy(scaleX = 4.5f, offsetX = 99f)),
                selectedLayerId = "layer-b"
            )
        )

        assertFalse(File(projectFolder, "layers/layer-a.png").exists())
        assertTrue(File(projectFolder, "layers/layer-b.png").exists())

        assertTrue(OverlayProjectRepository.renameProject(context, "proj-2", "Финальный проект"))
        val renamedLoaded = OverlayProjectRepository.loadProject(context, "proj-2")
        assertNotNull(renamedLoaded)
        assertEquals("Финальный проект", renamedLoaded!!.name)
        assertEquals(1, renamedLoaded.layers.size)
        assertEquals(4.5f, renamedLoaded.layers.first().scaleX, 0.001f)
        assertEquals(99f, renamedLoaded.layers.first().offsetX, 0.001f)

        assertTrue(OverlayProjectRepository.deleteProject(context, "proj-2"))
        assertNull(OverlayProjectRepository.loadProject(context, "proj-2"))
        assertTrue(OverlayProjectRepository.listProjects(context).isEmpty())
    }
}
