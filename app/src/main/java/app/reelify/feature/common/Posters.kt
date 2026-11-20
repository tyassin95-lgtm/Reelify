package app.reelify.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.reelify.core.designsystem.Rf
import app.reelify.core.designsystem.StatusPill
import app.reelify.core.model.MediaClip
import app.reelify.core.model.MediaKind
import app.reelify.core.model.Project
import app.reelify.core.model.ProjectStatus
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.map

fun mediaModel(uri: String): Any = if (uri.startsWith("content://") || uri.startsWith("file://")) uri else java.io.File(uri)

fun statusColor(s: ProjectStatus) = when (s) {
    ProjectStatus.DRAFT -> Rf.TextSecondary
    ProjectStatus.SCRIPTED -> Rf.Info
    ProjectStatus.FILMING -> Rf.Warning
    ProjectStatus.EDITING -> Rf.RoseSoft
    ProjectStatus.EXPORTED -> Rf.Success
}

/** Cinematic 9:16 poster for a project. Uses its first visual clip as artwork when available. */
@Composable
fun ProjectPoster(project: Project, modifier: Modifier = Modifier, accent: Color = Rf.Burgundy) {
    val container = appContainer()
    val firstClip by remember(project.id) {
        container.projects.observeBundle(project.id).map { b -> b?.clips?.filter { it.kind != MediaKind.AUDIO }?.minByOrNull { it.createdAt } }
    }.collectAsState(initial = null)
    Box(modifier.aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(accent, Rf.Surface))).border(1.dp, Rf.Border, RoundedCornerShape(20.dp))) {
        val art: MediaClip? = firstClip
        if (art != null) AsyncImage(model = mediaModel(art.thumbnailPath ?: art.uri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(Icons.Rounded.Movie, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(44.dp).align(Alignment.Center))
        Box(Modifier.fillMaxSize().background(Rf.HeroScrim))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
            StatusPill(project.status.label, statusColor(project.status))
            Text(project.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text("${project.duration.label} · ${project.language.displayName}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
fun ClipThumb(clip: MediaClip?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Rf.Surface3)) {
        if (clip != null && clip.kind != MediaKind.AUDIO) {
            AsyncImage(model = mediaModel(clip.thumbnailPath ?: clip.uri), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else Icon(Icons.Rounded.Movie, null, tint = Rf.TextTertiary, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
fun PosterPlaceholder(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(20.dp)).background(Rf.Surface2))
