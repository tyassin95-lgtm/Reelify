package app.reelify.feature.projects

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.reelify.core.designsystem.Chip
import app.reelify.core.designsystem.CinematicCard
import app.reelify.core.designsystem.EmptyState
import app.reelify.core.designsystem.GhostButton
import app.reelify.core.designsystem.InfoBanner
import app.reelify.core.designsystem.PillAction
import app.reelify.core.designsystem.Rf
import app.reelify.core.model.Project
import app.reelify.core.model.ProjectStatus
import app.reelify.feature.common.ProjectPoster
import app.reelify.feature.common.appContainer
import app.reelify.feature.common.strings
import app.reelify.feature.common.tr
import app.reelify.navigation.Routes
import app.reelify.navigation.openProject
import kotlinx.coroutines.launch

private enum class Filter(val label: String, val labelAr: String) {
    ALL("All", "الكل"),
    DRAFTS("Drafts", "المسودات"),
    PROGRESS("In progress", "قيد التنفيذ"),
    DONE("Completed", "المكتملة"),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectsTab(nav: NavHostController) {
    val c = appContainer()
    val s = strings()
    val scope = rememberCoroutineScope()
    val projects by c.projects.observeProjects().collectAsStateWithLifecycle(initialValue = emptyList())
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }
    var confirmDelete by remember { mutableStateOf<Project?>(null) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIdsList by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedIds = remember(selectedIdsList) { selectedIdsList.toSet() }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var emptyLegacyDrafts by remember { mutableStateOf<List<Project>>(emptyList()) }
    var statusNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projects) {
        emptyLegacyDrafts = c.projects.findEmptyLegacyDrafts()
        // Drop any selected IDs that no longer exist
        val liveIds = projects.map { it.id }.toSet()
        val pruned = selectedIdsList.filter { it in liveIds }
        if (pruned.size != selectedIdsList.size) {
            selectedIdsList = pruned
            if (pruned.isEmpty()) selectionMode = false
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectedIdsList = emptyList()
    }

    fun toggleSelection(id: String) {
        val next = if (id in selectedIds) selectedIds - id else selectedIds + id
        selectedIdsList = next.toList()
        if (next.isEmpty()) selectionMode = false
    }

    BackHandler(enabled = selectionMode) { exitSelection() }

    val shown = projects.filter {
        when (filter) {
            Filter.ALL -> true
            Filter.DRAFTS -> it.status == ProjectStatus.DRAFT || it.status == ProjectStatus.SCRIPTED
            Filter.PROGRESS -> it.status == ProjectStatus.FILMING || it.status == ProjectStatus.EDITING
            Filter.DONE -> it.status == ProjectStatus.EXPORTED
        }
    }

    LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectionMode) {
                    CinematicCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    tr("${selectedIds.size} selected", "تم تحديد ${selectedIds.size}"),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(::exitSelection) {
                                    Icon(Icons.Rounded.Close, tr("Cancel selection", "إلغاء التحديد"), tint = Rf.TextSecondary)
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillAction(
                                    text = tr("Select all (${shown.size})", "تحديد الكل (${shown.size})"),
                                    icon = Icons.Rounded.SelectAll,
                                    onClick = { selectedIdsList = shown.map { it.id } },
                                    enabled = shown.isNotEmpty(),
                                )
                                PillAction(
                                    text = tr("Deselect all", "إلغاء الكل"),
                                    icon = null,
                                    onClick = { selectedIdsList = emptyList(); selectionMode = false },
                                    enabled = selectedIds.isNotEmpty(),
                                )
                                PillAction(
                                    text = tr("Delete (${selectedIds.size})", "حذف (${selectedIds.size})"),
                                    icon = Icons.Rounded.Delete,
                                    onClick = { confirmBulkDelete = true },
                                    enabled = selectedIds.isNotEmpty(),
                                    primary = true,
                                )
                            }
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.projects, style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                        if (shown.isNotEmpty()) {
                            GhostButton(
                                tr("Select", "تحديد"),
                                { selectionMode = true; selectedIdsList = emptyList() },
                                icon = Icons.Rounded.SelectAll,
                            )
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Filter.entries.forEach { f ->
                        Chip(tr(f.label, f.labelAr), f == filter, { filter = f })
                    }
                }

                if (!selectionMode) {
                    GhostButton(tr("Storage & cleanup", "التخزين والتنظيف"), { nav.navigate(Routes.STORAGE) })
                    if (emptyLegacyDrafts.isNotEmpty()) {
                        CinematicCard {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    tr(
                                        "${emptyLegacyDrafts.size} older empty draft(s) have no script or footage.",
                                        "توجد ${emptyLegacyDrafts.size} مسودة قديمة فارغة بدون سكربت أو تصوير.",
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    PillAction(
                                        tr("Select empty drafts", "تحديد الفارغة"),
                                        Icons.Rounded.SelectAll,
                                        onClick = {
                                            filter = Filter.ALL
                                            selectedIdsList = emptyLegacyDrafts.map { it.id }
                                            selectionMode = true
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                statusNotice?.let { InfoBanner(it, color = Rf.Success) }
            }
        }
        if (shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            EmptyState(Icons.Rounded.MovieCreation, tr("No reels here yet", "ماكو مشاريع بعد"), tr("Projects appear here once you write a script.", "المشاريع تنحفظ هنا بعد ما ينكتب السكربت."), action = s.createReel, onAction = { nav.navigate(Routes.choose()) })
        }
        items(shown, key = { it.id }) { p ->
            val isSelected = p.id in selectedIds
            ProjectCell(
                p = p,
                selectionMode = selectionMode,
                selected = isSelected,
                onClick = {
                    if (selectionMode) toggleSelection(p.id) else nav.openProject(p)
                },
                onLongClick = {
                    if (!selectionMode) {
                        selectionMode = true
                        selectedIdsList = listOf(p.id)
                    } else {
                        toggleSelection(p.id)
                    }
                },
                onDuplicate = { scope.launch { c.projects.duplicateAsTemplate(p.id) } },
                onDelete = { confirmDelete = p },
            )
        }
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(tr("Delete “${p.title}”?", "حذف “${p.title}”؟")) },
            text = { Text(tr("This removes the project, its script, recordings made in Reelify and managed copies. Files in your gallery are not touched.", "هذا يحذف المشروع والسكربت والتسجيلات المحفوظة داخل Reelify. ملفات الأستوديو ما تتأثر.")) },
            confirmButton = {
                TextButton({
                    val targetId = p.id
                    confirmDelete = null
                    scope.launch {
                        c.projects.delete(targetId)
                        statusNotice = "Deleted 1 project"
                    }
                }) { Text(s.delete, color = Rf.Danger) }
            },
            dismissButton = { TextButton({ confirmDelete = null }) { Text(s.cancel) } },
            containerColor = Rf.Surface,
        )
    }

    if (confirmBulkDelete && selectedIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(tr("Delete ${selectedIds.size} project(s)?", "حذف ${selectedIds.size} مشاريع؟")) },
            text = {
                Text(
                    tr(
                        "This permanently removes the selected projects, their scripts, sections, local renders, and Reelify-managed clips. External gallery files stay untouched.",
                        "سيتم حذف المشاريع المحددة نهائياً مع السكربتات والتصاميم والتسجيلات الخاصة بها، بدون المساس بملفات الاستوديو الخارجية.",
                    ),
                )
            },
            confirmButton = {
                TextButton({
                    val ids = selectedIds.toList()
                    confirmBulkDelete = false
                    exitSelection()
                    scope.launch {
                        val deletedCount = c.projects.deleteMany(ids)
                        statusNotice = "Deleted $deletedCount project(s)"
                    }
                }) { Text(s.delete, color = Rf.Danger) }
            },
            dismissButton = { TextButton({ confirmBulkDelete = false }) { Text(s.cancel) } },
            containerColor = Rf.Surface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectCell(
    p: Project,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        ProjectPoster(
            p,
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .then(if (selected) Modifier.border(2.5.dp, Rf.Rose, RoundedCornerShape(20.dp)) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        )
        if (selectionMode) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape).background(Color(0xBB000000)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (selected) "Selected" else "Not selected",
                    tint = if (selected) Rf.RoseSoft else Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            IconButton(
                onClick = { menu = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).clip(CircleShape).background(Color(0x66000000)),
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Options", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        DropdownMenu(menu, { menu = false }, containerColor = Rf.Surface2) {
            DropdownMenuItem({ Text("Open") }, { menu = false; onClick() }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) })
            DropdownMenuItem({ Text("Duplicate as template") }, { menu = false; onDuplicate() }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) })
            DropdownMenuItem({ Text("Select multiple") }, { menu = false; onLongClick() }, leadingIcon = { Icon(Icons.Rounded.CheckCircle, null) })
            DropdownMenuItem({ Text("Delete", color = Rf.Danger) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = Rf.Danger) })
        }
    }
}
