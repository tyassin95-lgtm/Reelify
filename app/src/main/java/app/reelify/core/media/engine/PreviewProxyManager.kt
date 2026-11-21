package app.reelify.core.media.engine

import android.content.Context
import android.os.Build
import android.util.Log
import app.reelify.core.storage.InsufficientStorageException
import app.reelify.core.storage.MediaStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Generates and caches a deterministic, preview-quality MP4 proxy for reliable playback via
 * standard ExoPlayer. Cache keys hash every visual, audio, caption, transition, and brand input
 * so stale edits are automatically detected while unchanged openings hit disk instantly.
 */
class PreviewProxyManager(
    private val context: Context,
    private val storage: MediaStorage,
    private val factory: CompositionFactory,
    private val runner: TransformerRunner,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(projectId: String): Mutex = locks.computeIfAbsent(projectId) { Mutex() }

    private val isEmulator: Boolean
        get() = Build.FINGERPRINT.contains("generic") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.MODEL.contains("sdk")

    fun fingerprint(inputs: RenderInputs): String {
        val md = MessageDigest.getInstance("SHA-256")
        fun put(v: Any?) {
            md.update((v?.toString() ?: "<null>").toByteArray(Charsets.UTF_8))
            md.update(0)
        }
        val p = inputs.bundle.project
        val e = p.edit
        put("v2.1-proxy")
        put(p.id)
        put(p.language.code)
        put(p.duration.totalMs)
        put(inputs.width); put(inputs.height); put(inputs.frameRate)
        inputs.timeline.video.forEach { seg ->
            put(seg.sectionIndex); put(seg.timelineStartMs); put(seg.durationMs); put(seg.sourceStartMs); put(seg.isCutaway)
            val c = seg.clip
            if (c != null) {
                put(c.id); put(c.uri); put(c.kind); put(c.trimStartMs); put(c.trimEndMs)
                put(c.transform); put(c.beauty); put(c.isClinical); put(c.faceTrack?.sampledAt)
            }
        }
        inputs.timeline.dialogue.forEach { d ->
            put(d.sectionIndex); put(d.timelineStartMs); put(d.durationMs); put(d.sourceStartMs)
            put(d.clip.id); put(d.clip.muted); put(d.clip.volume); put(inputs.clipGains[d.clip.id])
        }
        put(e.audio); put(e.music); put(inputs.musicUri); put(inputs.musicDurationMs)
        put(e.transitions); put(e.color); put(e.effects); put(e.branding); put(e.safeZone)
        put(e.sfx)
        put(e.captions)
        inputs.captions.forEach { seg ->
            put(seg.id); put(seg.startMs); put(seg.endMs); put(seg.text)
            seg.words.forEach { w -> put(w.startMs); put(w.endMs); put(w.emphasized) }
        }
        inputs.brand?.let { b ->
            put(b.logoPath); put(b.brandName); put(b.clinicName); put(b.presenterName); put(b.professionalTitle)
            put(b.primaryColor); put(b.secondaryColor); put(b.textColor); put(b.font)
            put(b.instagram); put(b.snapchat); put(b.booking); put(b.ctaText); put(b.ctaTextArabic)
            put(b.lowerThird); put(b.outro); put(b.logoPosition); put(b.watermarkOpacity)
            put(b.beforeAfterDisclosure); put(b.beforeAfterDisclosureArabic)
        }
        inputs.bundle.sections.sortedBy { it.index }.forEach { s ->
            put(s.index); put(s.shotType); put(s.onScreenText)
        }
        return md.digest().take(8).joinToString("") { "%02x".format(it) }
    }

    fun proxyFile(projectId: String, fingerprint: String): File =
        File(storage.cacheDir(projectId), "preview_proxy_${fingerprint}.mp4")

    fun cachedProxy(projectId: String, fingerprint: String): File? =
        proxyFile(projectId, fingerprint).takeIf { it.exists() && it.length() > 1024 }

    fun latestValidProxy(projectId: String): File? =
        storage.cacheDir(projectId).listFiles()
            ?.filter { it.isFile && it.name.startsWith("preview_proxy_") && it.name.endsWith(".mp4") && !it.name.contains(".tmp") && it.length() > 1024 }
            ?.maxByOrNull { it.lastModified() }

    suspend fun getOrRenderProxy(
        projectId: String,
        inputs: RenderInputs,
        onProgress: (Int) -> Unit = {},
    ): File = lockFor(projectId).withLock {
        val proxyInputs = if (isEmulator && inputs.width > 540) {
            inputs.copy(width = 540, height = 960, frameRate = minOf(inputs.frameRate, 30))
        } else {
            inputs.copy(width = minOf(inputs.width, 720), height = minOf(inputs.height, 1280), frameRate = minOf(inputs.frameRate, 30))
        }
        val fp = fingerprint(proxyInputs)
        cachedProxy(projectId, fp)?.let { hit ->
            onProgress(100)
            return@withLock hit
        }

        val minRequiredBytes = 32L * 1024 * 1024
        if (storage.freeBytes() < minRequiredBytes) {
            throw InsufficientStorageException(minRequiredBytes)
        }

        val target = proxyFile(projectId, fp)
        val tmp = File(storage.cacheDir(projectId), "preview_proxy_${fp}.tmp.mp4")
        runCatching { if (tmp.exists()) tmp.delete() }

        try {
            val built = factory.build(proxyInputs, RenderPurpose.PREVIEW)
            val bitrate = if (proxyInputs.width <= 540) 2_200_000 else 3_500_000
            val watchdogMs = if (isEmulator) 10 * 60_000L else 60_000L
            runner.export(
                composition = built.composition,
                output = tmp,
                videoBitrate = bitrate,
                audioOnly = false,
                maxDelayBetweenSamplesMs = watchdogMs,
                onProgress = onProgress,
            )
            if (!tmp.exists() || tmp.length() <= 1024) {
                throw RenderFailedException("Preview proxy file was empty or incomplete.")
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            cleanupObsoleteProxies(projectId, keepName = target.name)
            onProgress(100)
            target
        } catch (e: CancellationException) {
            runCatching { tmp.delete() }
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Preview proxy render failed for project=$projectId", t)
            runCatching { tmp.delete() }
            throw t
        }
    }

    private fun cleanupObsoleteProxies(projectId: String, keepName: String) {
        storage.cacheDir(projectId).listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith("preview_proxy_") && f.name != keepName) {
                runCatching { f.delete() }
            }
        }
    }

    companion object {
        private const val TAG = "ReelifyPreviewProxy"
    }
}
