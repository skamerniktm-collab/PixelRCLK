package com.example.editor.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.editor.engine.PixelOperations
import com.example.editor.model.EditorTool
import com.example.editor.model.EditorUiState
import com.example.editor.model.PixelLayer
import com.example.projects.ProjectRetentionPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class EditorProjectSummary(
    val id: String,
    val name: String,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val layerCount: Int,
    val updatedAtMillis: Long,
    val previewPath: String? = null
)

data class SerializedEditorLayer(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val isVisible: Boolean,
    val opacity: Float,
    val version: Long
)

data class SerializedEditorProject(
    val version: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long = updatedAtMillis,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val activeLayerId: String,
    val activeTool: String,
    val brushSize: Int,
    val fillTolerance: Int,
    val showPixelGrid: Boolean,
    val layers: List<SerializedEditorLayer>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class LoadedEditorProject(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val activeLayerId: String,
    val activeTool: EditorTool,
    val brushSize: Int,
    val fillTolerance: Int,
    val showPixelGrid: Boolean,
    val layers: List<PixelLayer>
)

object EditorProjectJsonCodec {
    fun encode(project: SerializedEditorProject): String {
        val root = JSONObject().apply {
            put("version", project.version)
            put("id", project.id)
            put("name", project.name)
            put("createdAtMillis", project.createdAtMillis)
            put("updatedAtMillis", project.updatedAtMillis)
            put("lastOpenedAtMillis", project.lastOpenedAtMillis)
            put("canvasWidth", project.canvasWidth)
            put("canvasHeight", project.canvasHeight)
            put("activeLayerId", project.activeLayerId)
            put("activeTool", project.activeTool)
            put("brushSize", project.brushSize)
            put("fillTolerance", project.fillTolerance)
            put("showPixelGrid", project.showPixelGrid)
            val layersArr = JSONArray()
            for (layer in project.layers) {
                layersArr.put(JSONObject().apply {
                    put("id", layer.id)
                    put("name", layer.name)
                    put("width", layer.width)
                    put("height", layer.height)
                    put("isVisible", layer.isVisible)
                    put("opacity", if (layer.opacity.isFinite()) layer.opacity.toDouble() else 1.0)
                    put("version", layer.version)
                })
            }
            put("layers", layersArr)
        }
        return root.toString()
    }

    fun decode(jsonText: String): SerializedEditorProject? {
        return runCatching {
            val root = JSONObject(jsonText)
            val id = root.optString("id").takeIf { it.isNotBlank() } ?: return@runCatching null
            val name = root.optString("name").ifBlank { "Пиксель-арт" }
            val version = root.optInt("version", SerializedEditorProject.CURRENT_SCHEMA_VERSION)
            val createdAt = root.optLong("createdAtMillis", System.currentTimeMillis())
            val updatedAt = root.optLong("updatedAtMillis", createdAt)
            val lastOpenedAt = root.optLong("lastOpenedAtMillis", updatedAt)
            val canvasWidth = root.optInt("canvasWidth", 32).coerceIn(1, 512)
            val canvasHeight = root.optInt("canvasHeight", 32).coerceIn(1, 512)
            val activeLayerId = root.optString("activeLayerId")
            val activeTool = root.optString("activeTool", EditorTool.BRUSH.name)
            val brushSize = root.optInt("brushSize", 1).coerceIn(1, 24)
            val fillTolerance = root.optInt("fillTolerance", 0).coerceIn(0, 64)
            val showPixelGrid = root.optBoolean("showPixelGrid", true)

            val layersArr = root.optJSONArray("layers") ?: JSONArray()
            val layers = buildList(layersArr.length()) {
                for (i in 0 until layersArr.length()) {
                    val obj = layersArr.optJSONObject(i) ?: continue
                    val layerId = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        SerializedEditorLayer(
                            id = layerId,
                            name = obj.optString("name").ifBlank { "Слой ${i + 1}" },
                            width = obj.optInt("width", canvasWidth).coerceIn(1, 512),
                            height = obj.optInt("height", canvasHeight).coerceIn(1, 512),
                            isVisible = obj.optBoolean("isVisible", true),
                            opacity = obj.optDouble("opacity", 1.0).toFloat()
                                .let { if (it.isFinite()) it.coerceIn(0f, 1f) else 1f },
                            version = obj.optLong("version", 1L).coerceAtLeast(1L)
                        )
                    )
                }
            }

            SerializedEditorProject(
                version = version,
                id = id,
                name = name,
                createdAtMillis = createdAt,
                updatedAtMillis = updatedAt,
                lastOpenedAtMillis = lastOpenedAt,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                activeLayerId = activeLayerId,
                activeTool = activeTool,
                brushSize = brushSize,
                fillTolerance = fillTolerance,
                showPixelGrid = showPixelGrid,
                layers = layers
            )
        }.getOrNull()
    }
}

object EditorProjectRepository {

    private const val PROJECTS_DIR_NAME = "editor_projects"
    private const val META_FILE_NAME = "project.json"
    private const val META_TMP_FILE_NAME = "project.json.tmp"
    private const val LAYERS_DIR_NAME = "layers"
    private const val PREVIEW_FILE_NAME = "preview.png"
    private const val PREVIEW_MIN_EDGE = 128
    private const val PREVIEW_MAX_EDGE = 320

    fun projectsRoot(context: Context): File =
        File(context.filesDir, PROJECTS_DIR_NAME)

    private fun projectDir(context: Context, projectId: String): File =
        File(projectsRoot(context), sanitizeFileComponent(projectId))

    fun listProjects(context: Context): List<EditorProjectSummary> {
        val root = projectsRoot(context)
        if (!root.exists() || !root.isDirectory) return emptyList()
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()

        return dirs.mapNotNull { dir ->
            val metaFile = File(dir, META_FILE_NAME)
            if (!metaFile.exists()) return@mapNotNull null
            val meta = runCatching { EditorProjectJsonCodec.decode(metaFile.readText()) }
                .getOrNull() ?: return@mapNotNull null
            if (meta.layers.isEmpty()) return@mapNotNull null
            val previewFile = File(dir, PREVIEW_FILE_NAME)
            EditorProjectSummary(
                id = meta.id,
                name = meta.name,
                canvasWidth = meta.canvasWidth,
                canvasHeight = meta.canvasHeight,
                layerCount = meta.layers.size,
                updatedAtMillis = meta.updatedAtMillis,
                previewPath = previewFile.takeIf { it.exists() && it.length() > 0L }?.absolutePath
            )
        }.sortedByDescending { it.updatedAtMillis }
    }

    fun saveProject(
        context: Context,
        projectId: String,
        projectName: String,
        createdAtMillis: Long,
        state: EditorUiState,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val dir = projectDir(context, projectId).apply { mkdirs() }
        val layersDir = File(dir, LAYERS_DIR_NAME).apply { mkdirs() }

        val existingMeta = File(dir, META_FILE_NAME).takeIf { it.exists() }?.let { file ->
            runCatching { EditorProjectJsonCodec.decode(file.readText()) }.getOrNull()
        }
        val effectiveCreatedAt = existingMeta?.createdAtMillis ?: createdAtMillis
        val existingVersionById = existingMeta?.layers?.associate { it.id to it.version }.orEmpty()

        val activeLayerIds = state.layers.map { sanitizeFileComponent(it.id) }.toSet()
        layersDir.listFiles()?.forEach { existingFile ->
            if (existingFile.isFile && existingFile.nameWithoutExtension !in activeLayerIds) {
                existingFile.delete()
            }
        }

        for (layer in state.layers) {
            val layerFile = File(layersDir, "${sanitizeFileComponent(layer.id)}.png")
            val versionMatches = existingVersionById[layer.id] == layer.version &&
                existingMeta?.canvasWidth == state.canvasWidth &&
                existingMeta.canvasHeight == state.canvasHeight
            if (!layerFile.exists() || layerFile.length() == 0L || !versionMatches) {
                val bmp = Bitmap.createBitmap(
                    layer.pixels,
                    layer.width,
                    layer.height,
                    Bitmap.Config.ARGB_8888
                )
                try {
                    writeBitmapPngAtomic(bmp, layerFile)
                } finally {
                    bmp.recycle()
                }
            }
        }

        val previewBitmap = renderPreviewBitmap(state)
        if (previewBitmap != null) {
            try {
                writeBitmapPngAtomic(previewBitmap, File(dir, PREVIEW_FILE_NAME))
            } finally {
                previewBitmap.recycle()
            }
        }

        val serialized = SerializedEditorProject(
            version = SerializedEditorProject.CURRENT_SCHEMA_VERSION,
            id = projectId,
            name = projectName.trim().ifBlank { "Пиксель-арт" },
            createdAtMillis = effectiveCreatedAt,
            updatedAtMillis = nowMillis,
            lastOpenedAtMillis = nowMillis,
            canvasWidth = state.canvasWidth,
            canvasHeight = state.canvasHeight,
            activeLayerId = state.activeLayerId,
            activeTool = state.activeTool.name,
            brushSize = state.brushSize,
            fillTolerance = state.fillTolerance,
            showPixelGrid = state.showPixelGrid,
            layers = state.layers.map { layer ->
                SerializedEditorLayer(
                    id = layer.id,
                    name = layer.name,
                    width = layer.width,
                    height = layer.height,
                    isVisible = layer.isVisible,
                    opacity = layer.opacity,
                    version = layer.version
                )
            }
        )

        writeTextAtomic(
            File(dir, META_FILE_NAME),
            File(dir, META_TMP_FILE_NAME),
            EditorProjectJsonCodec.encode(serialized)
        )
        return nowMillis
    }

    fun loadProject(
        context: Context,
        projectId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): LoadedEditorProject? {
        val dir = projectDir(context, projectId)
        val metaFile = File(dir, META_FILE_NAME)
        if (!metaFile.exists()) return null

        val meta = runCatching { EditorProjectJsonCodec.decode(metaFile.readText()) }
            .getOrNull() ?: return null
        val layersDir = File(dir, LAYERS_DIR_NAME)

        val loadedLayers = meta.layers.mapNotNull { layerMeta ->
            val layerFile = File(layersDir, "${sanitizeFileComponent(layerMeta.id)}.png")
            val bmp = decodePngFile(layerFile) ?: return@mapNotNull null
            try {
                if (bmp.width != meta.canvasWidth || bmp.height != meta.canvasHeight) {
                    return@mapNotNull null
                }
                val pixels = IntArray(bmp.width * bmp.height)
                bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                PixelLayer(
                    id = layerMeta.id,
                    name = layerMeta.name,
                    width = bmp.width,
                    height = bmp.height,
                    pixels = pixels,
                    isVisible = layerMeta.isVisible,
                    opacity = layerMeta.opacity,
                    version = layerMeta.version
                )
            } finally {
                bmp.recycle()
            }
        }

        if (loadedLayers.isEmpty()) return null

        val refreshedLastOpened = maxOf(meta.lastOpenedAtMillis, nowMillis)
        if (refreshedLastOpened != meta.lastOpenedAtMillis) {
            runCatching {
                writeTextAtomic(
                    metaFile,
                    File(dir, META_TMP_FILE_NAME),
                    EditorProjectJsonCodec.encode(meta.copy(lastOpenedAtMillis = refreshedLastOpened))
                )
            }
        }

        val validActiveId = meta.activeLayerId.takeIf { id ->
            loadedLayers.any { it.id == id }
        } ?: loadedLayers.last().id

        val tool = runCatching { EditorTool.valueOf(meta.activeTool) }
            .getOrDefault(EditorTool.BRUSH)

        return LoadedEditorProject(
            id = meta.id,
            name = meta.name,
            createdAtMillis = meta.createdAtMillis,
            updatedAtMillis = meta.updatedAtMillis,
            lastOpenedAtMillis = refreshedLastOpened,
            canvasWidth = meta.canvasWidth,
            canvasHeight = meta.canvasHeight,
            activeLayerId = validActiveId,
            activeTool = tool,
            brushSize = meta.brushSize,
            fillTolerance = meta.fillTolerance,
            showPixelGrid = meta.showPixelGrid,
            layers = loadedLayers
        )
    }

    fun renameProject(
        context: Context,
        projectId: String,
        newName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val dir = projectDir(context, projectId)
        val metaFile = File(dir, META_FILE_NAME)
        if (!metaFile.exists()) return false
        val meta = runCatching { EditorProjectJsonCodec.decode(metaFile.readText()) }
            .getOrNull() ?: return false
        val updated = meta.copy(
            name = trimmed.take(48),
            updatedAtMillis = nowMillis,
            lastOpenedAtMillis = maxOf(meta.lastOpenedAtMillis, nowMillis)
        )
        writeTextAtomic(metaFile, File(dir, META_TMP_FILE_NAME), EditorProjectJsonCodec.encode(updated))
        return true
    }

    fun deleteProject(context: Context, projectId: String): Boolean {
        val dir = projectDir(context, projectId)
        if (!dir.exists()) return false
        return dir.deleteRecursively()
    }

    fun pruneExpiredProjects(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        excludeProjectIds: Set<String> = emptySet(),
        retentionMillis: Long = ProjectRetentionPolicy.RETENTION_MILLIS
    ): Int {
        val root = projectsRoot(context)
        if (!root.exists() || !root.isDirectory) return 0
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return 0
        val sanitizedExcluded = excludeProjectIds.map { sanitizeFileComponent(it) }.toSet()
        var removedCount = 0

        for (dir in dirs) {
            if (dir.name in excludeProjectIds || dir.name in sanitizedExcluded) continue
            val metaFile = File(dir, META_FILE_NAME)
            val meta = if (metaFile.exists()) {
                runCatching { EditorProjectJsonCodec.decode(metaFile.readText()) }.getOrNull()
            } else {
                null
            }
            if (meta != null && meta.id in excludeProjectIds) continue

            val expired = if (meta != null) {
                ProjectRetentionPolicy.isExpired(
                    updatedAtMillis = meta.updatedAtMillis,
                    lastOpenedAtMillis = meta.lastOpenedAtMillis,
                    nowMillis = nowMillis,
                    retentionMillis = retentionMillis
                )
            } else {
                (nowMillis - dir.lastModified()) >= retentionMillis
            }

            if (expired && dir.deleteRecursively()) {
                removedCount++
            }
        }
        return removedCount
    }

    private fun renderPreviewBitmap(state: EditorUiState): Bitmap? {
        if (state.layers.isEmpty()) return null
        val w = state.canvasWidth.coerceAtLeast(1)
        val h = state.canvasHeight.coerceAtLeast(1)
        val composited = PixelOperations.compositeLayers(state.layers, w, h)
        val raw = Bitmap.createBitmap(composited, w, h, Bitmap.Config.ARGB_8888)
        val longest = max(w, h)
        if (longest in PREVIEW_MIN_EDGE..PREVIEW_MAX_EDGE) {
            return raw
        }
        val targetLongest = if (longest < PREVIEW_MIN_EDGE) PREVIEW_MIN_EDGE else PREVIEW_MAX_EDGE
        val scale = targetLongest.toFloat() / longest
        val dstW = (w * scale).roundToInt().coerceAtLeast(1)
        val dstH = (h * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(raw, dstW, dstH, false)
        if (scaled !== raw) raw.recycle()
        return scaled
    }

    private fun decodePngFile(file: File): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@runCatching null
            if (raw.config != Bitmap.Config.ARGB_8888) {
                raw.copy(Bitmap.Config.ARGB_8888, false).also {
                    if (it !== raw) raw.recycle()
                }
            } else {
                raw
            }
        }.getOrNull()
    }

    private fun writeBitmapPngAtomic(bitmap: Bitmap, dest: File) {
        val tmp = File(dest.parentFile, "${dest.name}.tmp")
        FileOutputStream(tmp).use { out ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                "Failed to write PNG ${dest.name}"
            }
            out.fd.sync()
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun writeTextAtomic(dest: File, tmp: File, content: String) {
        FileOutputStream(tmp).use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (dest.exists()) dest.delete()
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    private fun sanitizeFileComponent(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "item" }
}
