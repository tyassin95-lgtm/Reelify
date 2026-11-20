package app.reelify.core.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class StorageUsage(val projectsBytes: Long, val cacheBytes: Long, val freeBytes: Long, val perProject: Map<String, Long>)

/**
 * Owns the on-device layout of project media:
 * filesDir/projects/<projectId>/{media,cache,exports,thumbs}
 */
class MediaStorage(private val context: Context) {
    private val root: File get() = File(context.filesDir, "projects").apply { mkdirs() }

    fun projectDir(projectId: String): File = File(root, projectId).apply { mkdirs() }
    fun mediaDir(projectId: String): File = File(projectDir(projectId), "media").apply { mkdirs() }
    fun cacheDir(projectId: String): File = File(projectDir(projectId), "cache").apply { mkdirs() }
    fun exportDir(projectId: String): File = File(projectDir(projectId), "exports").apply { mkdirs() }
    fun thumbDir(projectId: String): File = File(projectDir(projectId), "thumbs").apply { mkdirs() }
    fun brandDir(): File = File(context.filesDir, "brand").apply { mkdirs() }
    fun musicDir(): File = File(context.filesDir, "music").apply { mkdirs() }

    fun newRecordingFile(projectId: String, sectionIndex: Int): File =
        File(mediaDir(projectId), "rec_s${sectionIndex}_${System.currentTimeMillis()}.mp4")

    fun newVoiceoverFile(projectId: String, sectionIndex: Int): File =
        File(mediaDir(projectId), "vo_s${sectionIndex}_${System.currentTimeMillis()}.m4a")

    fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "media"

    fun sizeOf(uri: Uri): Long = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
        }
    }.getOrNull() ?: -1L

    /** Try to keep a durable grant on a picked URI. Returns true when the grant persisted. */
    fun tryPersist(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    /**
     * Returns a project-safe reference for an imported URI. Prefers a persisted grant (no copy);
     * falls back to a managed copy when the provider doesn't allow persistence.
     */
    suspend fun secureImport(projectId: String, uri: Uri, forceCopy: Boolean = false): String = withContext(Dispatchers.IO) {
        if (uri.scheme == ContentResolver.SCHEME_FILE || uri.scheme == null) return@withContext uri.path ?: uri.toString()
        if (!forceCopy && tryPersist(uri)) return@withContext uri.toString()
        val size = sizeOf(uri)
        if (size > 0 && size > freeBytes() - 200L * 1024 * 1024) throw InsufficientStorageException(size)
        val ext = displayName(uri).substringAfterLast('.', "bin").take(5)
        val out = File(mediaDir(projectId), "imp_${UUID.randomUUID().toString().take(8)}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it, 1 shl 16) } }
            ?: error("Cannot read the selected file")
        out.absolutePath
    }

    suspend fun copyInto(target: File, uri: Uri): File = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
            ?: error("Cannot read the selected file")
        target
    }

    /** True when the reference can currently be opened. */
    fun isAccessible(ref: String): Boolean = runCatching {
        if (ref.startsWith("content://")) {
            context.contentResolver.openFileDescriptor(Uri.parse(ref), "r")?.use { true } ?: false
        } else {
            File(ref).let { it.exists() && it.length() > 0 }
        }
    }.getOrDefault(false)

    fun toUri(ref: String): Uri = if (ref.startsWith("content://") || ref.startsWith("file://")) Uri.parse(ref) else Uri.fromFile(File(ref))

    suspend fun deleteProjectFiles(projectId: String) = withContext(Dispatchers.IO) {
        File(root, projectId).deleteRecursively()
    }

    suspend fun clearRenderCaches() = withContext(Dispatchers.IO) {
        root.listFiles()?.forEach { File(it, "cache").deleteRecursively() }
    }

    /** Deletes managed media files in project dirs that no clip references anymore. */
    suspend fun cleanupOrphans(referenced: Set<String>, liveProjectIds: Set<String>): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        root.listFiles()?.forEach { dir ->
            if (dir.name !in liveProjectIds) {
                freed += dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                dir.deleteRecursively()
                return@forEach
            }
            File(dir, "media").listFiles()?.forEach { f ->
                if (f.absolutePath !in referenced) { freed += f.length(); f.delete() }
            }
        }
        freed
    }

    suspend fun usage(): StorageUsage = withContext(Dispatchers.IO) {
        val per = root.listFiles()?.associate { d -> d.name to d.walkBottomUp().filter { it.isFile }.sumOf { it.length() } } ?: emptyMap()
        val cache = root.listFiles()?.sumOf { d -> File(d, "cache").walkBottomUp().filter { it.isFile }.sumOf { it.length() } } ?: 0L
        StorageUsage(per.values.sum(), cache, freeBytes(), per)
    }
}

class InsufficientStorageException(val requiredBytes: Long) :
    Exception("Not enough free storage (needs about ${requiredBytes / (1024 * 1024)} MB).")
