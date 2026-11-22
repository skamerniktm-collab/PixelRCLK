package com.example.overlay.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.overlay.model.OverlayBackground
import com.example.overlay.model.OverlayLayer
import com.example.overlay.model.OverlayProjectSummary
import com.example.overlay.model.OverlayUiState
import com.example.projects.ProjectRetentionPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class SerializedBackground(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean
)

data class SerializedLayer(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val offsetX: Float,
    val offsetY: Float,
    val baseFitScale: Float,
    val scaleX: Float,
    val scaleY: Float,
    val rotationDegrees: Float,
    val opacity: Float,
    val isVisible: Boolean,
    val pixelCrisp: Boolean
)

data class SerializedOverlayProject(
    val version: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long = updatedAtMillis,
    val preserveAlpha: Boolean,
    val showTransformFrame: Boolean,
    val selectedLayerId: String?,
    val background: SerializedBackground,
    val layers: List<SerializedLayer>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class LoadedOverlayProject(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long,
    val preserveAlpha: Boolean,
    val showTransformFrame: Boolean,
    val selectedLayerId: String?,
    val background: OverlayBackground,
    val layers: List<OverlayLayer>
)

object OverlayProjectJsonCodec {
    fun encode(project: SerializedOverlayProject): String {
        val root = JSONObject().apply {
            put("version", project.version)
            put("id", project.id)
            put("name", project.name)
            put("createdAtMillis", project.createdAtMillis)
            put("updatedAtMillis", project.updatedAtMillis)
            put("lastOpenedAtMillis", project.lastOpenedAtMillis)
            put("preserveAlpha", project.preserveAlpha)
            put("showTransformFrame", project.showTransformFrame)
            if (project.selectedLayerId != null) {
                put("selectedLayerId", project.selectedLayerId)
            } else {
                put("selectedLayerId", JSONObject.NULL)
            }
            put("background", JSONObject().apply {
                put("id", project.background.id)
                put("name", project.background.name)
                put("width", project.background.width)
                put("height", project.background.height)
                put("hasAlpha", project.background.hasAlpha)
            })
            val layersArr = JSONArray()
            for (layer in project.layers) {
                layersArr.put(JSONObject().apply {
                    put("id", layer.id)
                    put("name", layer.name)
                    put("width", layer.width)
                    put("height", layer.height)
                    put("hasAlpha", layer.hasAlpha)
                    put("offsetX", finiteOr(layer.offsetX, 0f).toDouble())
                    put("offsetY", finiteOr(layer.offsetY, 0f).toDouble())
                    put("baseFitScale", finiteOr(layer.baseFitScale, 1f).toDouble())
                    put("scaleX", finiteOr(layer.scaleX, 1f).toDouble())
                    put("scaleY", finiteOr(layer.scaleY, 1f).toDouble())
                    put("rotationDegrees", finiteOr(layer.rotationDegrees, 0f).toDouble())
                    put("opacity", finiteOr(layer.opacity, 1f).toDouble())
                    put("isVisible", layer.isVisible)
                    put("pixelCrisp", layer.pixelCrisp)
                })
            }
            put("layers", layersArr)
        }
        return root.toString()
    }

    fun decode(jsonText: String): SerializedOverlayProject? {
        return runCatching {
            val root = JSONObject(jsonText)
            val id = root.optString("id").takeIf { it.isNotBlank() } ?: return@runCatching null
            val name = root.optString("name").ifBlank { "Проект" }
            val version = root.optInt("version", SerializedOverlayProject.CURRENT_SCHEMA_VERSION)
            val createdAt = root.optLong("createdAtMillis", System.currentTimeMillis())
            val updatedAt = root.optLong("updatedAtMillis", createdAt)
            val lastOpenedAt = root.optLong("lastOpenedAtMillis", updatedAt)
            val preserveAlpha = root.optBoolean("preserveAlpha", true)
            val showTransformFrame = root.optBoolean("showTransformFrame", true)
            val selectedLayerId = if (root.isNull("selectedLayerId")) {
                null
            } else {
                root.optString("selectedLayerId").takeIf { it.isNotBlank() }
            }

            val bgObj = root.optJSONObject("background") ?: return@runCatching null
            val bgId = bgObj.optString("id").takeIf { it.isNotBlank() } ?: return@runCatching null
            val bg = SerializedBackground(
                id = bgId,
                name = bgObj.optString("name").ifBlank { "background" },
                width = bgObj.optInt("width", 1).coerceAtLeast(1),
                height = bgObj.optInt("height", 1).coerceAtLeast(1),
                hasAlpha = bgObj.optBoolean("hasAlpha", true)
            )

            val layersArr = root.optJSONArray("layers") ?: JSONArray()
            val layers = buildList(layersArr.length()) {
                for (i in 0 until layersArr.length()) {
                    val obj = layersArr.optJSONObject(i) ?: continue
                    val layerId = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    add(
                        SerializedLayer(
                            id = layerId,
                            name = obj.optString("name").ifBlank { "Слой ${i + 1}" },
                            width = obj.optInt("width", 1).coerceAtLeast(1),
                            height = obj.optInt("height", 1).coerceAtLeast(1),
                            hasAlpha = obj.optBoolean("hasAlpha", true),
                            offsetX = finiteOr(obj.optDouble("offsetX", 0.0).toFloat(), 0f),
                            offsetY = finiteOr(obj.optDouble("offsetY", 0.0).toFloat(), 0f),
                            baseFitScale = finiteOr(obj.optDouble("baseFitScale", 1.0).toFloat(), 1f)
                                .coerceAtLeast(0.005f),
                            scaleX = finiteOr(obj.optDouble("scaleX", 1.0).toFloat(), 1f)
                                .coerceAtLeast(0.01f),
                            scaleY = finiteOr(obj.optDouble("scaleY", 1.0).toFloat(), 1f)
                                .coerceAtLeast(0.01f),
                            rotationDegrees = finiteOr(obj.optDouble("rotationDegrees", 0.0).toFloat(), 0f),
                            opacity = finiteOr(obj.optDouble("opacity", 1.0).toFloat(), 1f)
                                .coerceIn(0.05f, 1f),
                            isVisible = obj.optBoolean("isVisible", true),
                            pixelCrisp = obj.optBoolean("pixelCrisp", false)
                        )
                    )
                }
            }

            SerializedOverlayProject(
                version = version,
                id = id,
                name = name,
                createdAtMillis = createdAt,
                updatedAtMillis = updatedAt,
                lastOpenedAtMillis = lastOpenedAt,
                preserveAlpha = preserveAlpha,
                showTransformFrame = showTransformFrame,
                selectedLayerId = selectedLayerId,
                background = bg,
                layers = layers
            )
        }.getOrNull()
    }

    private fun finiteOr(value: Float, fallback: Float): Float =
        if (value.isFinite()) value else fallback
}

object OverlayProjectRepository {

    private const val PROJECTS_DIR_NAME = "overlay_projects"
    private const val META_FILE_NAME = "project.json"
    private const val META_TMP_FILE_NAME = "project.json.tmp"
    private const val BG_FILE_NAME = "bg.png"
    private const val LAYERS_DIR_NAME = "layers"
    private const val PREVIEW_FILE_NAME = "preview.png"
    private const val PREVIEW_MAX_EDGE = 320

    fun projectsRoot(context: Context): File =
        File(context.filesDir, PROJECTS_DIR_NAME)

    private fun projectDir(context: Context, projectId: String): File =
        File(projectsRoot(context), sanitizeFileComponent(projectId))

    fun listProjects(context: Context): List<OverlayProjectSummary> {
        val root = projectsRoot(context)
        if (!root.exists() || !root.isDirectory) return emptyList()
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()

        return dirs.mapNotNull { dir ->
            val metaFile = File(dir, META_FILE_NAME)
            if (!metaFile.exists()) return@mapNotNull null
            val meta = runCatching { OverlayProjectJsonCodec.decode(metaFile.readText()) }
                .getOrNull() ?: return@mapNotNull null
            val bgFile = File(dir, BG_FILE_NAME)
            if (!bgFile.exists() || bgFile.length() == 0L) return@mapNotNull null
            val previewFile = File(dir, PREVIEW_FILE_NAME)
            OverlayProjectSummary(
                id = meta.id,
                name = meta.name,
                bgWidth = meta.background.width,
                bgHeight = meta.background.height,
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
        state: OverlayUiState,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val bg = state.background ?: error("Cannot save project without background")
        val dir = projectDir(context, projectId).apply { mkdirs() }
        val layersDir = File(dir, LAYERS_DIR_NAME).apply { mkdirs() }

        val existingMeta = File(dir, META_FILE_NAME).takeIf { it.exists() }?.let { file ->
            runCatching { OverlayProjectJsonCodec.decode(file.readText()) }.getOrNull()
        }
        val effectiveCreatedAt = existingMeta?.createdAtMillis ?: createdAtMillis

        val bgFile = File(dir, BG_FILE_NAME)
        val needWriteBg = !bgFile.exists() ||
            bgFile.length() == 0L ||
            existingMeta?.background?.id != bg.id
        if (needWriteBg) {
            writeBitmapPngAtomic(bg.bitmap, bgFile)
        }

        val activeLayerIds = state.layers.map { sanitizeFileComponent(it.id) }.toSet()
        layersDir.listFiles()?.forEach { existingFile ->
            val baseName = existingFile.nameWithoutExtension
            if (existingFile.isFile && baseName !in activeLayerIds) {
                existingFile.delete()
            }
        }

        for (layer in state.layers) {
            val layerFile = File(layersDir, "${sanitizeFileComponent(layer.id)}.png")
            if (!layerFile.exists() || layerFile.length() == 0L) {
                writeBitmapPngAtomic(layer.bitmap, layerFile)
            }
        }

        val previewBitmap = renderPreviewThumbnail(state)
        if (previewBitmap != null) {
            try {
                writeBitmapPngAtomic(previewBitmap, File(dir, PREVIEW_FILE_NAME))
            } finally {
                previewBitmap.recycle()
            }
        }

        val serialized = SerializedOverlayProject(
            version = SerializedOverlayProject.CURRENT_SCHEMA_VERSION,
            id = projectId,
            name = projectName.trim().ifBlank { "Проект" },
            createdAtMillis = effectiveCreatedAt,
            updatedAtMillis = nowMillis,
            lastOpenedAtMillis = nowMillis,
            preserveAlpha = state.preserveAlpha,
            showTransformFrame = state.showTransformFrame,
            selectedLayerId = state.selectedLayerId,
            background = SerializedBackground(
                id = bg.id,
                name = bg.name,
                width = bg.width,
                height = bg.height,
                hasAlpha = bg.hasAlpha
            ),
            layers = state.layers.map { layer ->
                SerializedLayer(
                    id = layer.id,
                    name = layer.name,
                    width = layer.width,
                    height = layer.height,
                    hasAlpha = layer.hasAlpha,
                    offsetX = layer.offsetX,
                    offsetY = layer.offsetY,
                    baseFitScale = layer.baseFitScale,
                    scaleX = layer.scaleX,
                    scaleY = layer.scaleY,
                    rotationDegrees = layer.rotationDegrees,
                    opacity = layer.opacity,
                    isVisible = layer.isVisible,
                    pixelCrisp = layer.pixelCrisp
                )
            }
        )

        val jsonText = OverlayProjectJsonCodec.encode(serialized)
        writeTextAtomic(File(dir, META_FILE_NAME), File(dir, META_TMP_FILE_NAME), jsonText)
        return nowMillis
    }

    fun loadProject(
        context: Context,
        projectId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): LoadedOverlayProject? {
        val dir = projectDir(context, projectId)
        val metaFile = File(dir, META_FILE_NAME)
        if (!metaFile.exists()) return null

        val meta = runCatching { OverlayProjectJsonCodec.decode(metaFile.readText()) }
            .getOrNull() ?: return null
        val bgBitmap = decodePngFile(File(dir, BG_FILE_NAME)) ?: return null

        val background = OverlayBackground(
            id = meta.background.id,
            name = meta.background.name,
            width = bgBitmap.width,
            height = bgBitmap.height,
            hasAlpha = meta.background.hasAlpha,
            bitmap = bgBitmap
        )

        val layersDir = File(dir, LAYERS_DIR_NAME)
        val loadedLayers = meta.layers.mapNotNull { layerMeta ->
            val layerFile = File(layersDir, "${sanitizeFileComponent(layerMeta.id)}.png")
            val bmp = decodePngFile(layerFile) ?: return@mapNotNull null
            OverlayLayer(
                id = layerMeta.id,
                name = layerMeta.name,
                bitmap = bmp,
                width = bmp.width,
                height = bmp.height,
                hasAlpha = layerMeta.hasAlpha,
                offsetX = layerMeta.offsetX,
                offsetY = layerMeta.offsetY,
                baseFitScale = layerMeta.baseFitScale,
                scaleX = layerMeta.scaleX,
                scaleY = layerMeta.scaleY,
                rotationDegrees = layerMeta.rotationDegrees,
                opacity = layerMeta.opacity,
                isVisible = layerMeta.isVisible,
                pixelCrisp = layerMeta.pixelCrisp
            )
        }

        val refreshedLastOpened = maxOf(meta.lastOpenedAtMillis, nowMillis)
        if (refreshedLastOpened != meta.lastOpenedAtMillis) {
            runCatching {
                writeTextAtomic(
                    metaFile,
                    File(dir, META_TMP_FILE_NAME),
                    OverlayProjectJsonCodec.encode(meta.copy(lastOpenedAtMillis = refreshedLastOpened))
                )
            }
        }

        val validSelectedId = meta.selectedLayerId?.takeIf { selId ->
            loadedLayers.any { it.id == selId }
        } ?: loadedLayers.lastOrNull()?.id

        return LoadedOverlayProject(
            id = meta.id,
            name = meta.name,
            createdAtMillis = meta.createdAtMillis,
            updatedAtMillis = meta.updatedAtMillis,
            lastOpenedAtMillis = refreshedLastOpened,
            preserveAlpha = meta.preserveAlpha,
            showTransformFrame = meta.showTransformFrame,
            selectedLayerId = validSelectedId,
            background = background,
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
        val meta = runCatching { OverlayProjectJsonCodec.decode(metaFile.readText()) }
            .getOrNull() ?: return false
        val updated = meta.copy(
            name = trimmed.take(48),
            updatedAtMillis = nowMillis,
            lastOpenedAtMillis = maxOf(meta.lastOpenedAtMillis, nowMillis)
        )
        writeTextAtomic(metaFile, File(dir, META_TMP_FILE_NAME), OverlayProjectJsonCodec.encode(updated))
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
                runCatching { OverlayProjectJsonCodec.decode(metaFile.readText()) }.getOrNull()
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

    private fun renderPreviewThumbnail(state: OverlayUiState): Bitmap? {
        val bg = state.background ?: return null
        val longest = max(bg.width, bg.height).coerceAtLeast(1)
        val thumbScale = if (longest > PREVIEW_MAX_EDGE) {
            PREVIEW_MAX_EDGE.toFloat() / longest
        } else {
            1f
        }
        val thumbW = (bg.width * thumbScale).roundToInt().coerceAtLeast(1)
        val thumbH = (bg.height * thumbScale).roundToInt().coerceAtLeast(1)

        val thumb = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
        thumb.setHasAlpha(state.preserveAlpha)
        val canvas = Canvas(thumb)
        if (!state.preserveAlpha) {
            canvas.drawColor(Color.BLACK)
        }
        canvas.scale(thumbScale, thumbScale)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bg.bitmap, 0f, 0f, bgPaint)

        for (layer in state.layers) {
            if (!layer.isVisible) continue
            canvas.save()
            canvas.translate(layer.offsetX, layer.offsetY)
            canvas.rotate(layer.rotationDegrees)
            val halfW = (layer.width * layer.effectiveScaleX) / 2f
            val halfH = (layer.height * layer.effectiveScaleY) / 2f
            val dstRect = RectF(-halfW, -halfH, halfW, halfH)
            val layerPaint = Paint().apply {
                isAntiAlias = !layer.pixelCrisp
                isFilterBitmap = !layer.pixelCrisp
                alpha = (layer.opacity * 255f).roundToInt().coerceIn(0, 255)
            }
            canvas.drawBitmap(layer.bitmap, null, dstRect, layerPaint)
            canvas.restore()
        }

        return thumb
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
