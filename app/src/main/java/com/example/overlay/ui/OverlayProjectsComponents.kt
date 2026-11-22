package com.example.overlay.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.editor.ui.components.CheckerboardBox
import com.example.editor.viewmodel.PixelEditorViewModel
import com.example.overlay.model.OverlayProjectSummary
import com.example.overlay.viewmodel.OverlayViewModel
import com.example.projects.AppProjectSummary
import com.example.projects.ProjectKind
import com.example.resizer.model.AppTab
import com.example.resizer.viewmodel.ResizerViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun ProjectsTabScreen(
    editorViewModel: PixelEditorViewModel,
    resizerViewModel: ResizerViewModel,
    overlayViewModel: OverlayViewModel,
    onNavigateToTab: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val editorState by editorViewModel.uiState.collectAsStateWithLifecycle()
    val resizerState by resizerViewModel.uiState.collectAsStateWithLifecycle()
    val overlayState by overlayViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var projectToRename by remember { mutableStateOf<AppProjectSummary?>(null) }
    var projectToDelete by remember { mutableStateOf<AppProjectSummary?>(null) }

    LaunchedEffect(Unit) {
        editorViewModel.flushAutoSaveNow(context)
        resizerViewModel.flushAutoSaveNow(context)
        overlayViewModel.flushAutoSaveNow(context)
        editorViewModel.initializeProjects(context)
        resizerViewModel.initializeProjects(context)
        overlayViewModel.initializeProjects(context)
    }

    val combinedProjects = remember(
        editorState.savedProjects,
        resizerState.savedProjects,
        overlayState.savedProjects
    ) {
        buildList {
            editorState.savedProjects.forEach { item ->
                add(
                    AppProjectSummary(
                        id = item.id,
                        kind = ProjectKind.EDITOR,
                        name = item.name,
                        width = item.canvasWidth,
                        height = item.canvasHeight,
                        itemCount = item.layerCount,
                        detailLabel = "${item.canvasWidth}×${item.canvasHeight} px • Слоёв: ${item.layerCount}",
                        updatedAtMillis = item.updatedAtMillis,
                        previewPath = item.previewPath
                    )
                )
            }
            resizerState.savedProjects.forEach { item ->
                add(
                    AppProjectSummary(
                        id = item.id,
                        kind = ProjectKind.RESIZER,
                        name = item.name,
                        width = item.targetWidth,
                        height = item.targetHeight,
                        itemCount = item.inputCount,
                        detailLabel = "${item.targetWidth}×${item.targetHeight} px • Файлов: ${item.inputCount}",
                        updatedAtMillis = item.updatedAtMillis,
                        previewPath = item.previewPath
                    )
                )
            }
            overlayState.savedProjects.forEach { item ->
                add(
                    AppProjectSummary(
                        id = item.id,
                        kind = ProjectKind.OVERLAY,
                        name = item.name,
                        width = item.bgWidth,
                        height = item.bgHeight,
                        itemCount = item.layerCount,
                        detailLabel = "${item.bgWidth}×${item.bgHeight} px • Слоёв: ${item.layerCount}",
                        updatedAtMillis = item.updatedAtMillis,
                        previewPath = item.previewPath
                    )
                )
            }
        }.sortedByDescending { it.updatedAtMillis }
    }

    val activeEditorId = editorState.currentProjectId
    val activeResizerId = resizerState.currentProjectId?.takeIf { resizerState.inputs.isNotEmpty() }
    val activeOverlayId = overlayState.currentProjectId?.takeIf { overlayState.background != null }

    val isAutoSaving = editorState.isAutoSaving || resizerState.isAutoSaving || overlayState.isAutoSaving
    val lastAutoSavedAtMillis = listOfNotNull(
        editorState.lastAutoSavedAtMillis,
        resizerState.lastAutoSavedAtMillis,
        overlayState.lastAutoSavedAtMillis
    ).maxOrNull()
    val isLoadingProject = editorState.isLoadingProject || resizerState.isLoadingProject || overlayState.isLoadingProject

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        ProjectsScreenContent(
            savedProjects = combinedProjects,
            isProjectActive = { project ->
                when (project.kind) {
                    ProjectKind.EDITOR -> project.id == activeEditorId
                    ProjectKind.RESIZER -> project.id == activeResizerId
                    ProjectKind.OVERLAY -> project.id == activeOverlayId
                }
            },
            isAutoSaving = isAutoSaving,
            lastAutoSavedAtMillis = lastAutoSavedAtMillis,
            isLoadingProject = isLoadingProject,
            onOpenProject = { project ->
                when (project.kind) {
                    ProjectKind.EDITOR -> {
                        if (project.id == activeEditorId) {
                            onNavigateToTab(AppTab.EDITOR)
                        } else {
                            editorViewModel.openProject(context, project.id) { ok ->
                                if (ok) {
                                    onNavigateToTab(AppTab.EDITOR)
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Не удалось открыть проект")
                                    }
                                }
                            }
                        }
                    }
                    ProjectKind.RESIZER -> {
                        if (project.id == activeResizerId) {
                            onNavigateToTab(AppTab.RESIZER)
                        } else {
                            resizerViewModel.openProject(context, project.id) { ok ->
                                if (ok) {
                                    onNavigateToTab(AppTab.RESIZER)
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Не удалось открыть проект")
                                    }
                                }
                            }
                        }
                    }
                    ProjectKind.OVERLAY -> {
                        if (project.id == activeOverlayId) {
                            onNavigateToTab(AppTab.OVERLAY)
                        } else {
                            overlayViewModel.openProject(context, project.id) { ok ->
                                if (ok) {
                                    onNavigateToTab(AppTab.OVERLAY)
                                } else {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Не удалось открыть проект")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            onRenameProject = { projectToRename = it },
            onDeleteProject = { projectToDelete = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars)
        )
    }

    projectToRename?.let { target ->
        RenameProjectDialog(
            project = target,
            onConfirm = { newName ->
                when (target.kind) {
                    ProjectKind.EDITOR -> editorViewModel.renameProject(context, target.id, newName)
                    ProjectKind.RESIZER -> resizerViewModel.renameProject(context, target.id, newName)
                    ProjectKind.OVERLAY -> overlayViewModel.renameProject(context, target.id, newName)
                }
                projectToRename = null
            },
            onDismiss = { projectToRename = null }
        )
    }

    projectToDelete?.let { target ->
        DeleteProjectConfirmDialog(
            project = target,
            onConfirm = {
                when (target.kind) {
                    ProjectKind.EDITOR -> editorViewModel.deleteProject(context, target.id)
                    ProjectKind.RESIZER -> resizerViewModel.deleteProject(context, target.id)
                    ProjectKind.OVERLAY -> overlayViewModel.deleteProject(context, target.id)
                }
                projectToDelete = null
            },
            onDismiss = { projectToDelete = null }
        )
    }
}

@Composable
fun ProjectsScreenContent(
    savedProjects: List<AppProjectSummary>,
    isProjectActive: (AppProjectSummary) -> Boolean,
    isAutoSaving: Boolean,
    lastAutoSavedAtMillis: Long?,
    isLoadingProject: Boolean,
    onOpenProject: (AppProjectSummary) -> Unit,
    onRenameProject: (AppProjectSummary) -> Unit,
    onDeleteProject: (AppProjectSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("overlay_projects_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .size(40.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CollectionsBookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Мои проекты",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusText = when {
                    isAutoSaving -> "Автосохранение…"
                    lastAutoSavedAtMillis != null -> "Автосохранение • ${formatShortTime(lastAutoSavedAtMillis)} • автоочистка 7 дн."
                    else -> "Проектов: ${savedProjects.size} • автоочистка 7 дн."
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isLoadingProject) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Восстановление проекта…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else if (savedProjects.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Нет сохранённых проектов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("projects_empty_label")
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 270.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(savedProjects, key = { "${it.kind.name}:${it.id}" }) { project ->
                    ProjectCard(
                        project = project,
                        isActive = isProjectActive(project),
                        onOpen = { onOpenProject(project) },
                        onRename = { onRenameProject(project) },
                        onDelete = { onDeleteProject(project) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProjectCard(
    project: AppProjectSummary,
    isActive: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val previewBitmap = remember(project.previewPath, project.updatedAtMillis) {
        loadPreviewImageBitmap(project.previewPath)
    }
    val dateLabel = remember(project.updatedAtMillis) {
        formatProjectDateTime(project.updatedAtMillis)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isActive) 1.5.dp else 1.dp,
                color = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                },
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onOpen)
            .testTag("project_card_${project.kind.name.lowercase()}_${project.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 66.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                CheckerboardBox(modifier = Modifier.fillMaxSize(), cellDp = 5f)
                if (previewBitmap != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = min(size.width / previewBitmap.width, size.height / previewBitmap.height)
                        val dw = (previewBitmap.width * s).roundToInt().coerceAtLeast(1)
                        val dh = (previewBitmap.height * s).roundToInt().coerceAtLeast(1)
                        drawImage(
                            image = previewBitmap,
                            dstOffset = IntOffset(
                                ((size.width - dw) / 2f).roundToInt(),
                                ((size.height - dh) / 2f).roundToInt()
                            ),
                            dstSize = IntSize(dw, dh),
                            filterQuality = if (project.kind == ProjectKind.EDITOR) {
                                FilterQuality.None
                            } else {
                                FilterQuality.Medium
                            }
                        )
                    }
                } else {
                    Icon(
                        imageVector = when (project.kind) {
                            ProjectKind.EDITOR -> Icons.Rounded.Brush
                            ProjectKind.RESIZER -> Icons.Rounded.PhotoSizeSelectLarge
                            ProjectKind.OVERLAY -> Icons.Rounded.Layers
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        Text(
                            text = project.kind.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                text = "Открыт",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = project.detailLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.testTag("project_rename_${project.kind.name.lowercase()}_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Переименовать проект",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("project_delete_${project.kind.name.lowercase()}_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Удалить проект",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RenameProjectDialog(
    project: OverlayProjectSummary,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    RenameProjectDialog(
        projectId = project.id,
        initialName = project.name,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun RenameProjectDialog(
    project: AppProjectSummary,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    RenameProjectDialog(
        projectId = "${project.kind.name}:${project.id}",
        initialName = project.name,
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
private fun RenameProjectDialog(
    projectId: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember(projectId) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("Переименовать проект", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { if (it.length <= 48) nameInput = it },
                label = { Text("Название проекта") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_project_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameInput) },
                enabled = nameInput.trim().isNotEmpty(),
                modifier = Modifier.testTag("rename_project_confirm")
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun DeleteProjectConfirmDialog(
    project: AppProjectSummary,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text("Удалить проект?", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                text = "Проект «${project.name}» (${project.kind.label}, ${project.detailLabel}) будет удалён из памяти приложения.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("delete_project_confirm")
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

private fun loadPreviewImageBitmap(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        BitmapFactory.decodeFile(path)?.asImageBitmap()
    }.getOrNull()
}

private fun formatProjectDateTime(millis: Long): String {
    val formatter = SimpleDateFormat("dd.MM.yyyy • HH:mm", Locale.forLanguageTag("ru"))
    return formatter.format(Date(millis))
}

fun formatShortTime(millis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru"))
    return formatter.format(Date(millis))
}
