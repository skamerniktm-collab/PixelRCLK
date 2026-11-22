package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.editor.model.EditorUiState
import com.example.editor.model.PixelLayer
import com.example.editor.storage.EditorProjectRepository
import com.example.overlay.model.OverlayBackground
import com.example.overlay.model.OverlayUiState
import com.example.overlay.storage.OverlayProjectRepository
import com.example.projects.ProjectRetentionPolicy
import com.example.resizer.model.ResizerInputImage
import com.example.resizer.model.ResizerUiState
import com.example.resizer.storage.ResizerProjectRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProjectAutoCleanupTest {

    private lateinit var context: Context
    private val dayMs = 24L * 60L * 60L * 1000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        EditorProjectRepository.projectsRoot(context).deleteRecursively()
        ResizerProjectRepository.projectsRoot(context).deleteRecursively()
        OverlayProjectRepository.projectsRoot(context).deleteRecursively()
    }

    @After
    fun tearDown() {
        EditorProjectRepository.projectsRoot(context).deleteRecursively()
        ResizerProjectRepository.projectsRoot(context).deleteRecursively()
        OverlayProjectRepository.projectsRoot(context).deleteRecursively()
    }

    @Test
    fun `editor project expires after 7 days unless opened or modified`() {
        val t0 = 1_700_000_000_000L
        val stateStale = EditorUiState(
            currentProjectId = "ed-stale",
            currentProjectName = "Старый рисунок",
            canvasWidth = 8,
            canvasHeight = 8,
            layers = listOf(PixelLayer(id = "l1", name = "L1", width = 8, height = 8, pixels = IntArray(64) { Color.RED })),
            activeLayerId = "l1"
        )
        val stateReopened = EditorUiState(
            currentProjectId = "ed-opened",
            currentProjectName = "Открывался на 5 день",
            canvasWidth = 8,
            canvasHeight = 8,
            layers = listOf(PixelLayer(id = "l2", name = "L2", width = 8, height = 8, pixels = IntArray(64) { Color.BLUE })),
            activeLayerId = "l2"
        )

        EditorProjectRepository.saveProject(context, "ed-stale", "Старый рисунок", t0, stateStale, nowMillis = t0)
        EditorProjectRepository.saveProject(context, "ed-opened", "Открывался на 5 день", t0, stateReopened, nowMillis = t0)

        // Open second project on day 5 — updatedAtMillis stays t0, lastOpenedAtMillis becomes t0 + 5d
        val loaded = EditorProjectRepository.loadProject(context, "ed-opened", nowMillis = t0 + 5 * dayMs)
        assertNotNull(loaded)
        assertEquals(t0, loaded!!.updatedAtMillis)
        assertEquals(t0 + 5 * dayMs, loaded.lastOpenedAtMillis)

        // Before 7 days: nothing removed
        assertEquals(0, EditorProjectRepository.pruneExpiredProjects(context, nowMillis = t0 + 7 * dayMs - 1L))
        assertEquals(2, EditorProjectRepository.listProjects(context).size)

        // At 8 days: ed-stale expired (>7d), ed-opened kept (opened 3 days ago)
        val removedDay8 = EditorProjectRepository.pruneExpiredProjects(context, nowMillis = t0 + 8 * dayMs)
        assertEquals(1, removedDay8)
        assertFalse(File(EditorProjectRepository.projectsRoot(context), "ed-stale").exists())
        val remaining = EditorProjectRepository.listProjects(context)
        assertEquals(1, remaining.size)
        assertEquals("ed-opened", remaining.first().id)

        // At 12 days (7 days since last open): ed-opened also expires
        val removedDay12 = EditorProjectRepository.pruneExpiredProjects(context, nowMillis = t0 + 12 * dayMs)
        assertEquals(1, removedDay12)
        assertTrue(EditorProjectRepository.listProjects(context).isEmpty())
    }

    @Test
    fun `resizer project expires after 7 days and respects active exclusion and open refresh`() {
        val t0 = 1_700_000_000_000L
        val img = ResizerInputImage(id = "in-1", baseName = "pic", width = 8, height = 8, hasAlpha = false, pixels = IntArray(64) { Color.GREEN })
        val stateA = ResizerUiState(currentProjectId = "res-old", currentProjectName = "Старый ресайз", inputs = listOf(img), activePreviewInputId = "in-1")
        val stateB = ResizerUiState(currentProjectId = "res-active", currentProjectName = "Открытый сейчас", inputs = listOf(img), activePreviewInputId = "in-1")

        ResizerProjectRepository.saveProject(context, "res-old", "Старый ресайз", t0, stateA, nowMillis = t0)
        ResizerProjectRepository.saveProject(context, "res-active", "Открытый сейчас", t0, stateB, nowMillis = t0)

        // Excluding res-active protects it while open in memory
        val removedWithExclude = ResizerProjectRepository.pruneExpiredProjects(
            context = context,
            nowMillis = t0 + ProjectRetentionPolicy.RETENTION_MILLIS,
            excludeProjectIds = setOf("res-active")
        )
        assertEquals(1, removedWithExclude)
        assertFalse(File(ResizerProjectRepository.projectsRoot(context), "res-old").exists())
        assertTrue(File(ResizerProjectRepository.projectsRoot(context), "res-active").exists())

        // Opening refreshes timer
        assertNotNull(ResizerProjectRepository.loadProject(context, "res-active", nowMillis = t0 + 7 * dayMs))
        assertEquals(0, ResizerProjectRepository.pruneExpiredProjects(context, nowMillis = t0 + 10 * dayMs))
        assertEquals(1, ResizerProjectRepository.pruneExpiredProjects(context, nowMillis = t0 + 14 * dayMs))
    }

    @Test
    fun `overlay project expires after 7 days and legacy json without lastOpenedAtMillis falls back to updatedAtMillis`() {
        val t0 = 1_700_000_000_000L
        val bg = OverlayBackground(
            id = "bg-1",
            name = "car",
            width = 16,
            height = 16,
            hasAlpha = false,
            bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        )
        OverlayProjectRepository.saveProject(
            context = context,
            projectId = "ov-legacy",
            projectName = "Ливрея",
            createdAtMillis = t0,
            state = OverlayUiState(currentProjectId = "ov-legacy", currentProjectName = "Ливрея", background = bg),
            nowMillis = t0
        )

        // Simulate legacy project.json without "lastOpenedAtMillis"
        val metaFile = File(OverlayProjectRepository.projectsRoot(context), "ov-legacy/project.json")
        val legacyJson = metaFile.readText().replace(Regex(",\"lastOpenedAtMillis\":\\d+"), "")
        metaFile.writeText(legacyJson)

        assertEquals(0, OverlayProjectRepository.pruneExpiredProjects(context, nowMillis = t0 + 6 * dayMs))
        assertEquals(1, OverlayProjectRepository.pruneExpiredProjects(context, nowMillis = t0 + 7 * dayMs))
        assertTrue(OverlayProjectRepository.listProjects(context).isEmpty())
    }
}
