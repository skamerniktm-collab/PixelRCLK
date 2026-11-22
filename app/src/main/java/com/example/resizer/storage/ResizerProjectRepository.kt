package com.example.resizer.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.projects.ProjectRetentionPolicy
import com.example.resizer.model.RESIZER_PRESET_SIZES
import com.example.resizer.model.ResampleMode
import com.example.resizer.model.ResizePreset
import com.example.resizer.model.ResizerInputImage
import com.example.resizer.model.ResizerUiState
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class ResizerProjectSummary(
    val id: String,
    val name: String,
    val targetWidth: Int,
    val targetHeight: Int,
    val inputCount: Int,
    val updatedAtMillis: Long,
    val previewPath: String? = null
)

data class SerializedResizerPreset(
    val width: Int,
    val height: Int
)

data class SerializedResizerInput(
    val id: String,
    val baseName: String,
    val width: Int,
    val height: Int,
    val hasAlpha: Boolean,
    val sourceByteSize: Long?
)

data class SerializedResizerProject(
    val version: Int = CURRENT_SCHEMA_VERSION,
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long = updatedAtMillis,
    val activePreviewInputId: String?,
    val selectedPresets: List<SerializedResizerPreset>,
    val previewPreset: SerializedResizerPreset,
    val resampleMode: String,
    val preserveAlpha: Boolean,
    val inputs: List<SerializedResizerInput>
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

data class LoadedResizerProject(
    val id: String,
    val name: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val lastOpenedAtMillis: Long,
    val activePreviewInputId: String?,
    val selectedPresets: Set<ResizePreset>,
    val previewPreset: ResizePreset,
    val resampleMode: ResampleMode,
    val preserveAlpha: Boolean,
    val inputs: List<ResizerInputImage>
)

object ResizerProjectJsonCodec {
    fun encode(project: SerializedResizerProject): String {
        val root = JSONObject().apply {
            put("version", project.version)
            put("id", project.id)
            put("name", project.name)
            put("createdAtMillis", project.createdAtMillis)
            put("updatedAtMillis", project.updatedAtMillis)
            put("lastOpenedAtMillis", project.lastOpenedAtMillis)
            if (project.activePreviewInputId != null) {
                put("activePreviewInputId", project.activePreviewInputId)
            } else {
                put("activePreviewInputId", JSONObject.NULL)
            }
            put("previewPreset", JSONObject().apply {
                put("width", project.previewPreset.width)
                put("height", project.previewPreset.height)
            })
            val presetsArr = JSONArray()
            for (preset in project.selectedPresets) {
                presetsArr.put(JSONObject().apply {
                    put("width", preset.width)
                    put("height", preset.height)
                })
            }
            put("selectedPresets", presetsArr)
            put("resampleMode", project.resampleMode)
            put("preserveAlpha", project.preserveAlpha)

            val inputsArr = JSONArray()
            for (input in project.inputs) {
                inputsArr.put(JSONObject().apply {
                    put("id", input.id)
                    put("baseName", input.baseName)
                    put("width", input.width)
                    put("height", input.height)
                    put("hasAlpha", input.hasAlpha)
                    if (input.sourceByteSize != null) {
                        put("sourceByteSize", input.sourceByteSize)
                    } else {
                        put("sourceByteSize", JSONObject.NULL)
                    }
                })
            }
            put("inputs", inputsArr)
        }
        return root.toString()
    }

    fun decode(jsonText: String): SerializedResizerProject? {
        return runCatching {
            val root = JSONObject(jsonText)
            val id = root.optString("id").takeIf { it.isNotBlank() } ?: return@runCatching null
            val name = root.optString("name").ifBlank { "Ресайз" }
            val version = root.optInt("version", SerializedResizerProject.CURRENT_SCHEMA_VERSION)
            val createdAt = root.optLong("createdAtMillis", System.currentTimeMillis())
            val updatedAt = root.optLong("updatedAtMillis", createdAt)
            val lastOpenedAt = root.optLong("lastOpenedAtMillis", updatedAt)
            val activePreviewInputId = if (root.isNull("activePreviewInputId")) {
                null
            } else {
                root.optString("activePreviewInputId").takeIf { it.isNotBlank() }
            }

            val previewObj = root.optJSONObject("previewPreset")
            val defaultPreset = RESIZER_PRESET_SIZES.first()
            val previewPreset = SerializedResizerPreset(
                width = previewObj?.optInt("width", defaultPreset.width)?.coerceAtLeast(1) ?: defaultPreset.width,
                height = previewObj?.optInt("height", defaultPreset.height)?.coerceAtLeast(1) ?: defaultPreset.height
            )

            val presetsArr = root.optJSONArray("selectedPresets") ?: JSONArray()
            val selectedPresets = buildList(presetsArr.length()) {
                for (i in 0 until presetsArr.length()) {
                    val obj = presetsArr.optJSONObject(i) ?: continue
                    val w = obj.optInt("width", 0)
                    val h = obj.optInt("height", 0)
                    if (w > 0 && h > 0) {
                        add(SerializedResizerPreset(w, h))
                    }
                }
            }

            val resampleMode = root.optString("resampleMode", ResampleMode.NEAREST.name)
            val preserveAlpha = root.optBoolean("preserveAlpha", true)

            val inputsArr = root.optJSONArray("inputs") ?: JSONArray()
            val inputs = buildList(inputsArr.length()) {
                for (i in 0 until inputsArr.length()) {
                    val obj = inputsArr.optJSONObject(i) ?: continue
                    val inputId = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val sourceByteSize = if (obj.isNull("sourceByteSize")) {
                        null
                    } else {
                        obj.optLong("sourceByteSize", -1L).takeIf { it > 0L }
                    }
                    add(
                        SerializedResizerInput(
                            id = inputId,
                            baseName = obj.optString("baseName").ifBlank { "image_${i + 1}" },
                            width = obj.optInt("width", 1).coerceAtLeast(1),
                            height = obj.optInt("height", 1).coerceAtLeast(1),
                            hasAlpha = obj.optBoolean("hasAlpha", true),
                            sourceByteSize = sourceByteSize
                        )
                    )
                }
            }

            SerializedResizerProject(
                version = version,
                id = id,
                name = name,
                createdAtMillis = createdAt,
                updatedAtMillis = updatedAt,
                lastOpenedAtMillis = lastOpenedAt,
                activePreviewInputId = activePreviewInputId,
                selectedPresets = selectedPresets,
                previewPreset = previewPreset,
                resampleMode = resampleMode,
                preserveAlpha = preserveAlpha,
                inputs = inputs
            )
        }.getOrNull()
    }
}

object ResizerProjectRepository {

    private const val PROJECTS_DIR_NAME = "resizer_projects"
    private const val META_FILE_NAME = "project.json"
    private const val META_TMP_FILE_NAME = "project.json.tmp"
    private const val INPUTS_DIR_NAME = "inputs"
    private const val PREVIEW_FILE_NAME = "preview.png"
    private const val PREVIEW_MIN_EDGE = 128
    private const val PREVIEW_MAX_EDGE = 320

    fun projectsRoot(context: Context): File =
        File(context.filesDir, PROJECTS_DIR_NAME)

    private fun projectDir(context: Context, projectId: String): File =
        File(projectsRoot(context), sanitizeFileComponent(projectId))

    fun listProjects(context: Context): List<ResizerProjectSummary> {
        val root = projectsRoot(context)
        if (!root.exists() || !root.isDirectory) return emptyList()
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()

        return dirs.mapNotNull { dir ->
            val metaFile = File(dir, META_FILE_NAME)
            if (!metaFile.exists()) return@mapNotNull null
            val meta = runCatching { ResizerProjectJsonCodec.decode(metaFile.readText()) }
                .getOrNull() ?: return@mapNotNull null
            if (meta.inputs.isEmpty()) return@mapNotNull null
            val previewFile = File(dir, PREVIEW_FILE_NAME)
            ResizerProjectSummary(
                id = meta.id,
                name = meta.name,
                targetWidth = meta.previewPreset.width,
                targetHeight = meta.previewPreset.height,
                inputCount = meta.inputs.size,
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
        state: ResizerUiState,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        check(state.inputs.isNotEmpty()) { "Cannot save resizer project without inputs" }
        val dir = projectDir(context, projectId).apply { mkdirs() }
        val inputsDir = File(dir, INPUTS_DIR_NAME).apply { mkdirs() }

        val existingMeta = File(dir, META_FILE_NAME).takeIf { it.exists() }?.let { file ->
            runCatching { ResizerProjectJsonCodec.decode(file.readText()) }.getOrNull()
        }
        val effectiveCreatedAt = existingMeta?.createdAtMillis ?: createdAtMillis

        val activeInputIds = state.inputs.map { sanitizeFileComponent(it.id) }.toSet()
        inputsDir.listFiles()?.forEach { existingFile ->
            if (existingFile.isFile && existingFile.nameWithoutExtension !in activeInputIds) {
                existingFile.delete()
            }
        }

        for (input in state.inputs) {
            val inputFile = File(inputsDir, "${sanitizeFileComponent(input.id)}.png")
            if (!inputFile.exists() || inputFile.length() == 0L) {
                val bmp = Bitmap.createBitmap(
                    input.pixels,
                    input.width,
                    input.height,
                    Bitmap.Config.ARGB_8888
                )
                try {
                    writeBitmapPngAtomic(bmp, inputFile)
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

        val serialized = SerializedResizerProject(
            version = SerializedResizerProject.CURRENT_SCHEMA_VERSION,
            id = projectId,
            name = projectName.trim().ifBlank { "Ресайз" },
            createdAtMillis = effectiveCreatedAt,
            updatedAtMillis = nowMillis,
            lastOpenedAtMillis = nowMillis,
            activePreviewInputId = state.activePreviewInputId,
            selectedPresets = state.selectedPresets.map { SerializedResizerPreset(it.width, it.height) },
            previewPreset = SerializedResizerPreset(state.previewPreset.width, state.previewPreset.height),
            resampleMode = state.resampleMode.name,
            preserveAlpha = state.preserveAlpha,
            inputs = state.inputs.map { input ->
                SerializedResizerInput(
                    id = input.id,
                    baseName = input.baseName,
                    width = input.width,
                    height = input.height,
                    hasAlpha = input.hasAlpha,
                    sourceByteSize = input.sourceByteSize
                )
            }
        )

        writeTextAtomic(
            File(dir, META_FILE_NAME),
            File(dir, META_TMP_FILE_NAME),
            ResizerProjectJsonCodec.encode(serialized)
        )
        return nowMillis
    }

    fun loadProject(
        context: Context,
        projectId: String,
        nowMillis: Long = System.currentTimeMillis()
    ): LoadedResizerProject? {
        val dir = projectDir(context, projectId)
        val metaFile = File(dir, META_FILE_NAME)
        if (!metaFile.exists()) return null

        val meta = runCatching { ResizerProjectJsonCodec.decode(metaFile.readText()) }
            .getOrNull() ?: return null
        val inputsDir = File(dir, INPUTS_DIR_NAME)

        val loadedInputs = meta.inputs.mapNotNull { inputMeta ->
            val inputFile = File(inputsDir, "${sanitizeFileComponent(inputMeta.id)}.png")
            val bmp = decodePngFile(inputFile) ?: return@mapNotNull null
            try {
                val pixels = IntArray(bmp.width * bmp.height)
                bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                ResizerInputImage(
                    id = inputMeta.id,
                    baseName = inputMeta.baseName,
                    width = bmp.width,
                    height = bmp.height,
                    hasAlpha = inputMeta.hasAlpha,
                    pixels = pixels,
                    sourceByteSize = inputMeta.sourceByteSize
                )
            } finally {
                bmp.recycle()
            }
        }

        if (loadedInputs.isEmpty()) return null

        val refreshedLastOpened = maxOf(meta.lastOpenedAtMillis, nowMillis)
        if (refreshedLastOpened != meta.lastOpenedAtMillis) {
            runCatching {
                writeTextAtomic(
                    metaFile,
                    File(dir, META_TMP_FILE_NAME),
                    ResizerProjectJsonCodec.encode(meta.copy(lastOpenedAtMillis = refreshedLastOpened))
                )
            }
        }

        val validActiveId = meta.activePreviewInputId?.takeIf { id ->
            loadedInputs.any { it.id == id }
        } ?: loadedInputs.first().id

        val matchedSelectedPresets = meta.selectedPresets.mapNotNull { sp ->
            RESIZER_PRESET_SIZES.firstOrNull { it.width == sp.width && it.height == sp.height }
        }.toSet().ifEmpty { setOf(RESIZER_PRESET_SIZES.first()) }

        val matchedPreviewPreset = RESIZER_PRESET_SIZES.firstOrNull {
            it.width == meta.previewPreset.width && it.height == meta.previewPreset.height
        } ?: matchedSelectedPresets.first()

        val mode = runCatching { ResampleMode.valueOf(meta.resampleMode) }
            .getOrDefault(ResampleMode.NEAREST)

        return LoadedResizerProject(
            id = meta.id,
            name = meta.name,
            createdAtMillis = meta.createdAtMillis,
            updatedAtMillis = meta.updatedAtMillis,
            lastOpenedAtMillis = refreshedLastOpened,
            activePreviewInputId = validActiveId,
            selectedPresets = matchedSelectedPresets,
            previewPreset = matchedPreviewPreset,
            resampleMode = mode,
            preserveAlpha = meta.preserveAlpha,
            inputs = loadedInputs
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
        val meta = runCatching { ResizerProjectJsonCodec.decode(metaFile.readText()) }
            .getOrNull() ?: return false
        val updated = meta.copy(
            name = trimmed.take(48),
            updatedAtMillis = nowMillis,
            lastOpenedAtMillis = maxOf(meta.lastOpenedAtMillis, nowMillis)
        )
        writeTextAtomic(metaFile, File(dir, META_TMP_FILE_NAME), ResizerProjectJsonCodec.encode(updated))
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
                runCatching { ResizerProjectJsonCodec.decode(metaFile.readText()) }.getOrNull()
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

    private fun renderPreviewBitmap(state: ResizerUiState): Bitmap? {
        val previewInput = state.activeInput ?: return null
        val w = previewInput.width.coerceAtLeast(1)
        val h = previewInput.height.coerceAtLeast(1)
        val raw = Bitmap.createBitmap(previewInput.pixels, w, h, Bitmap.Config.ARGB_8888)
        val longest = max(w, h)
        if (longest in PREVIEW_MIN_EDGE..PREVIEW_MAX_EDGE) {
            return raw
        }
        val targetLongest = if (longest < PREVIEW_MIN_EDGE) PREVIEW_MIN_EDGE else PREVIEW_MAX_EDGE
        val scale = targetLongest.toFloat() / longest
        val dstW = (w * scale).roundToInt().coerceAtLeast(1)
        val dstH = (h * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(raw, dstW, dstH, longest > PREVIEW_MAX_EDGE)
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
