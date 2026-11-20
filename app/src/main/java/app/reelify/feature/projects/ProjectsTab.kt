package app.reelify.feature.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.EmptyState
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.Rf
import app.reelify.core.model.Project
import app.reelify.core.model.ProjectStatus
import app.reelify.feature.common.ProjectPoster
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.strings
import app.reelify.navigation.Routes
import app.reelify.navigation.openProject
import kotlinx.coroutines.launch

private enum class Filter(val label: String) { ALL("All"), DRAFTS("Drafts"), PROGRESS("In progress"), DONE("Completed") }

@Composable
fun ProjectsTab(nav: NavHostController) {
    val c = appContainer()
    val s = strings()
    val scope = rememberCoroutineScope()
    val projects by c.projects.observeProjects().collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by remember { mutableStateOf(Filter.ALL) }
    var confirmDelete by remember { mutableStateOf<Project?>(null) }
    val shown = projects.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.DRAFTS -> it.status == ProjectStatus.DRAFT || it.status == ProjectStatus.SCRIPTED
            Filter.PROGRESS -> it.status == ProjectStatus.FILMING || it.status == ProjectStatus.EDITING
            Filter.DONE -> it.status == ProjectStatus.EXPORTED
        }
    }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding()) {
                Text(s.projects, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filter.entries.forEach { f -> Chip(f.label, f == filter, { filter = f }) }
                }
                GhostButton("Storage & cleanup", { nav.navigate(Routes.STORAGE) }, Modifier.padding(top = 4.dp))
            }
        }
        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState(Icons.Rounded.MovieCreation, "No reels here yet", "Create a reel and it will be saved automatically as you work.", action = s.createReel, onAction = { nav.navigate(Routes.setup()) })
        }
        items(shown, key = { it.id }) { p -> ProjectCell(p, onOpen = { nav.openProject(p) }, onDuplicate = { scope.launch { c.projects.duplicateAsTemplate(p.id) } }, onDelete = { confirmDelete = p }) }
    }
    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete “${p.title}”?") },
            text = { Text("This removes the project, its script, recordings made in Reelify and managed copies. Files in your gallery are not touched.") },
            confirmButton = { TextButton({ scope.launch { c.projects.delete(p.id) }; confirmDelete = null }) { Text(s.delete, color = Rf.Danger) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(s.cancel) } },
            containerColor = Rf.Surface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCell(p: Project, onOpen: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ProjectPoster(p, Modifier.fillMaxWidth().combinedClickable(onClick = onOpen, onLongClick = { menu = true }))
        DropdownMenu(menu, { menu = false }, containerColor = Rf.Surface2) {
            DropdownMenuItem({ Text("Open") }, { menu = false; onOpen() }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) })
            DropdownMenuItem({ Text("Duplicate as template") }, { menu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) })
            DropdownMenuItem({ Text("Delete", color = Rf.Danger) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Rf.Danger) })
        }
    }
}
