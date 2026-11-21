package app.reelify.core.pipeline

import app.reelify.core.captions.CaptionBuilder
import app.reelify.core.captions.CaptionRemapper
import app.reelify.core.captions.TerminologyCorrector
import app.reelify.core.data.LibraryRepository
import app.reelify.core.data.ProjectRepository
import app.reelify.core.data.SettingsRepository
import app.reelify.core.library.SoundLibrary
import app.reelify.core.media.engine.CompositionFactory
import app.reelify.core.media.engine.FaceAnalyzer
import app.reelify.core.media.engine.RenderPreparer
import app.reelify.core.media.engine.RenderPurpose
import app.reelify.core.media.engine.TimelineIssue
import app.reelify.core.media.engine.TransformerRunner
import app.reelify.core.model.CaptionSource
import app.reelify.core.model.CaptionTrack
import app.reelify.core.model.CaptionWord
import app.reelify.core.model.MediaKind
import app.reelify.core.model.ProjectBundle
import app.reelify.core.model.ProjectStatus
import app.reelify.core.model.SfxCue
import app.reelify.core.security.AiProvider
import app.reelify.core.security.CredentialRepository
import app.reelify.core.storage.MediaStorage
import app.reelify.core.timing.TimingEstimator
import app.reelify.core.util.Ids
import app.reelify.integration.deepgram.DeepgramClient
import app.reelify.integration.openai.AiSchemas
import app.reelify.integration.openai.DirectorContext
import app.reelify.integration.openai.ReelDirector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

enum class AutoEditStage(val label: String) {
    VALIDATE("Checking footage"),
    ANALYZE("Analyzing faces for beauty"),
    EXTRACT("Extracting dialogue audio"),
    TRANSCRIBE("Transcribing dialogue"),
    CAPTIONS("Building synchronized captions"),
    SOUND("Applying music, sound & style"),
    ASSEMBLE("Assembling the reel"),
    DONE("Ready"),
}

data class AutoEditProgress(
    val stage: AutoEditStage,
    val fraction: Float? = null,
    val message: String = stage.label,
    val completed: Set<AutoEditStage> = emptySet(),
    val warnings: List<String> = emptyList(),
    val issues: List<TimelineIssue> = emptyList(),
    val error: String? = null,
    val done: Boolean = false,
)

data class AutoEditOptions(val transcribe: Boolean = true, val aiSoundDesign: Boolean = true, val aiEmphasis: Boolean = true, val forceRetranscribe: Boolean = false)

/**
 * Automatic editing pipeline. Every stage does real work; AI stages degrade gracefully (with a
 * visible warning) when credentials or connectivity are missing — local editing always works.
 */
class AutoEditor(
    private val projects: ProjectRepository,
    private val library: LibraryRepository,
    private val settings: SettingsRepository,
    private val storage: MediaStorage,
    private val preparer: RenderPreparer,
    private val factory: CompositionFactory,
    private val runner: TransformerRunner,
    private val faces: FaceAnalyzer,
    private val deepgram: DeepgramClient,
    private val director: ReelDirector,
    private val credentials: CredentialRepository,
) {
    fun run(projectId: String, options: AutoEditOptions = AutoEditOptions()): Flow<AutoEditProgress> = channelFlow {
        val warnings = mutableListOf<String>()
        val done = mutableSetOf<AutoEditStage>()
        suspend fun emit(stage: AutoEditStage, fraction: Float? = null, msg: String = stage.label) =
            send(AutoEditProgress(stage, fraction, msg, done.toSet(), warnings.toList()))

        try {
            // 1. Validate
            emit(AutoEditStage.VALIDATE)
            var bundle = projects.getBundle(projectId) ?: error("Project not found")
            var timeline = preparer.resolve(bundle)
            if (timeline.blocking.isNotEmpty()) {
                send(AutoEditProgress(AutoEditStage.VALIDATE, null, "Some sections need attention", done, warnings, timeline.issues, error = timeline.blocking.joinToString("\n") { it.message }))
                return@channelFlow
            }
            timeline.issues.forEach { warnings += it.message }
            done += AutoEditStage.VALIDATE

            // 2. Face analysis for clips with beauty enabled
            val needFaces = bundle.clips.filter { c ->
                c.isVisual && c.beauty.enabled && (c.faceTrack == null || c.faceTrack.samples.none { it.tMs in c.trimStartMs..c.trimEndMs } && c.kind == MediaKind.VIDEO)
            }
            needFaces.forEachIndexed { i, c ->
                emit(AutoEditStage.ANALYZE, i.toFloat() / needFaces.size, "Analyzing faces (${i + 1}/${needFaces.size})")
                val track = faces.analyze(c, storage.toUri(c.uri))
                projects.upsertClip(c.copy(faceTrack = track))
            }
            done += AutoEditStage.ANALYZE
            bundle = projects.getBundle(projectId)!!
            timeline = preparer.resolve(bundle)

            // 3-5. Dialogue → transcription → captions
            val signature = preparer.dialogueSignature(timeline)
            val existing = bundle.project.captions
            val keepExisting = existing != null && !options.forceRetranscribe &&
                (existing.dialogueSignature == signature || existing.source == CaptionSource.MANUAL || existing.segments.any { it.manuallyEdited }) &&
                existing.source != CaptionSource.SCRIPT_ESTIMATE
            val deepgramReady = credentials.status(AiProvider.DEEPGRAM).configured
            var captions: CaptionTrack? = existing
            if (!keepExisting && preparer.hasDialogue(timeline)) {
                var words: List<CaptionWord>? = null
                if (options.transcribe && deepgramReady) {
                    emit(AutoEditStage.EXTRACT, 0f)
                    val inputs = preparer.inputs(projectId, normalize = false)
                    val built = factory.build(inputs, RenderPurpose.DIALOGUE)
                    val audio = preparer.tempFile(projectId, "dialogue_${System.currentTimeMillis()}.m4a")
                    runner.export(built.composition, audio, 0, audioOnly = true) { p -> trySend(AutoEditProgress(AutoEditStage.EXTRACT, p / 100f, "Extracting dialogue audio", done.toSet(), warnings.toList())) }
                    done += AutoEditStage.EXTRACT
                    emit(AutoEditStage.TRANSCRIBE, null, "Transcribing with Deepgram (${bundle.project.language.displayName})")
                    try {
                        val terms = library.terminology()
                        val treatment = library.getTreatment(bundle.project.treatmentId)
                        val keyterms = if (settings.current().sendKeytermsToDeepgram) {
                            (terms.flatMap { listOf(it.english, it.arabic) } + listOfNotNull(treatment?.name, treatment?.arabicName)).filter { it.isNotBlank() }
                        } else emptyList()
                        val transcript = deepgram.transcribe(audio, "audio/mp4", bundle.project.language, keyterms)
                        val raw = transcript.words.map { CaptionWord(it.text, it.startMs, it.endMs, it.confidence) }
                        val (corrected, fixes) = TerminologyCorrector.correct(raw, terms, bundle.project.language)
                        if (fixes.isNotEmpty()) warnings += "Applied ${fixes.size} terminology correction(s): " + fixes.take(3).joinToString { "${it.from} → ${it.to}" }
                        words = CaptionRemapper.anchor(corrected, timeline.dialogue)
                        done += AutoEditStage.TRANSCRIBE
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        warnings += "Transcription unavailable: ${e.message}. Captions were estimated from the script instead."
                    } finally {
                        audio.delete()
                    }
                } else if (options.transcribe && !deepgramReady) {
                    warnings += "Deepgram isn't configured, so captions are estimated from the approved script. Add a key in Settings for accurate, synchronized captions."
                }
                emit(AutoEditStage.CAPTIONS)
                val style = bundle.project.edit.captions.style
                val source = if (words != null) CaptionSource.DEEPGRAM else CaptionSource.SCRIPT_ESTIMATE
                val finalWords = words ?: CaptionRemapper.anchor(
                    CaptionBuilder.fromScript(bundle.sections.map { s -> Triple(timeline.sectionMs * (s.index - 1), timeline.sectionMs, s.spokenScript) }),
                    timeline.dialogue,
                )
                captions = CaptionTrack(bundle.project.language, CaptionBuilder.segment(finalWords, style.wordsPerCaption), source, System.currentTimeMillis(), bundle.project.edit.editVersion, signature)
                projects.updateCaptions(projectId, captions)
            } else if (!preparer.hasDialogue(timeline)) {
                warnings += "No dialogue audio found — captions skipped."
            }
            done += AutoEditStage.CAPTIONS

            // Caption emphasis. Template projects use the emphasis words written with the approved
            // script (deterministic, no extra API call); legacy projects keep the AI keyword pass.
            val openAiReady = credentials.status(AiProvider.OPENAI).configured
            val ctx = directorContext(bundle)
            val template = bundle.project.template.takeIf { bundle.project.isTemplate }
            if (template != null) {
                val words = bundle.sections.flatMap { it.captionEmphasis }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)
                if (words.isNotEmpty() && bundle.project.edit.captions.highlightKeywords.isEmpty() && app.reelify.core.model.EditGroup.CAPTIONS !in bundle.project.edit.userOverrides) {
                    projects.updateEdit(projectId, byUser = false) { e -> e.copy(captions = e.captions.copy(highlightKeywords = words)) }
                }
            } else if (options.aiEmphasis && openAiReady && captions != null && captions.segments.isNotEmpty() && bundle.project.edit.captions.highlightKeywords.isEmpty()) {
                runCatching { director.captionEmphasis(ctx, captions.segments.joinToString(" ") { it.text }) }
                    .onSuccess { kw -> if (kw.isNotEmpty()) projects.updateEdit(projectId, byUser = false) { e -> e.copy(captions = e.captions.copy(highlightKeywords = kw)) } }
                    .onFailure { if (it is CancellationException) throw it; warnings += "Keyword highlighting skipped: ${it.message}" }
            }

            // 6. Music & SFX
            emit(AutoEditStage.SOUND)
            bundle = projects.getBundle(projectId)!!
            if (template != null) {
                // Template rules: same music, SFX positions, transitions, colour, captions and
                // branding every time. Groups the user changed are left alone.
                projects.updateEdit(projectId, byUser = false) { e -> app.reelify.core.template.ProductionPlanner.applyTemplateDefaults(e, template, bundle.project.duration) }
            } else if (bundle.project.edit.music.trackId == null) {
                val density = speechDensity(bundle)
                var trackId: String? = null
                var cues: List<SfxCue> = emptyList()
                if (options.aiSoundDesign && openAiReady) {
                    runCatching { director.soundDesign(ctx, bundle.project.plan, bundle.sections) }
                        .onSuccess { d -> trackId = d.musicTrackId; cues = d.sfx.mapNotNull { cueFor(it.cueAt, it.sfxId, bundle) } }
                        .onFailure { if (it is CancellationException) throw it; warnings += "AI sound design skipped (${it.message}); used local matching." }
                }
                if (trackId == null) {
                    trackId = SoundLibrary.match(bundle.project.plan?.musicMood, density, preferCalm = true).id
                    cues = bundle.sections.mapNotNull { s -> s.sfxCue.takeIf { it.isNotBlank() }?.let { id -> cueFor(if (s.index == 1) "start" else "boundary_${s.index - 1}", id, bundle) } }
                }
                projects.updateEdit(projectId, byUser = false) { e -> e.copy(music = e.music.copy(trackId = trackId), sfx = cues) }
            }
            done += AutoEditStage.SOUND

            // 7. Assemble: snapshot branding, mark status
            emit(AutoEditStage.ASSEMBLE)
            val brand = settings.currentBrand()
            projects.update(projectId) { it.copy(brandSnapshot = brand, status = if (it.status == ProjectStatus.EXPORTED) it.status else ProjectStatus.EDITING) }
            // Build once to verify the composition is constructible before opening the preview.
            factory.build(preparer.inputs(projectId), RenderPurpose.PREVIEW)
            done += AutoEditStage.ASSEMBLE
            send(AutoEditProgress(AutoEditStage.DONE, 1f, "Your reel is assembled", done + AutoEditStage.DONE, warnings.toList(), timeline.issues, done = true))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            send(AutoEditProgress(AutoEditStage.ASSEMBLE, null, "Automatic editing failed", done.toSet(), warnings.toList(), error = e.message ?: e.javaClass.simpleName))
        }
    }

    private fun speechDensity(b: ProjectBundle): Float {
        val words = b.sections.sumOf { TimingEstimator().countWords(it.spokenScript) }
        return (words / (b.project.duration.totalMs / 1000f) / 2.5f).coerceIn(0f, 1f)
    }

    private fun cueFor(cueAt: String, sfxId: String, b: ProjectBundle): SfxCue? {
        if (sfxId !in AiSchemas.sfxCueValues || sfxId == "none") return null
        val total = b.project.duration.totalMs; val sec = b.project.duration.sectionMs
        val t = when (cueAt) {
            "start" -> 250L
            "boundary_1" -> sec - 200
            "boundary_2" -> sec * 2 - 200
            "cta" -> total - b.project.edit.branding.ctaDurationMs
            "end" -> total - 1500
            else -> return null
        }
        return SfxCue(Ids.new(), t.coerceIn(0, total - 300), sfxId, 0.6f)
    }

    private suspend fun directorContext(b: ProjectBundle) = DirectorContext(
        b.project.language, b.project.duration, b.project.style, library.getTreatment(b.project.treatmentId),
        library.terminology(), settings.currentBrand(), b.project.concept, b.project.feedback,
    )
}
