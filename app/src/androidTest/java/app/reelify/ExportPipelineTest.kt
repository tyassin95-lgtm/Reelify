package app.reelify

import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.reelify.core.captions.CaptionBuilder
import app.reelify.core.captions.CaptionRemapper
import app.reelify.core.media.ImportOptions
import app.reelify.core.media.engine.ExportValidator
import app.reelify.core.media.engine.RenderPurpose
import app.reelify.core.model.BrandProfile
import app.reelify.core.model.CaptionSource
import app.reelify.core.model.CaptionTrack
import app.reelify.core.model.ClipOrigin
import app.reelify.core.model.ClipRole
import app.reelify.core.model.ColorLook
import app.reelify.core.model.PlanMeta
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.SfxCue
import app.reelify.core.model.TransitionType
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.BeautyPreset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end on-device render: generated clips → project → (trim) → captions + branding + music +
 * SFX + transitions + beauty → Transformer export → validation with the Android media stack.
 * Emulators (software codecs/GL) render at a reduced size to keep runtime reasonable; physical
 * devices render the real 1080×1920 output.
 */
@RunWith(AndroidJUnit4::class)
class ExportPipelineTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = ctx.applicationContext as ReelifyApp
    private val isEmulator = Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu") || Build.MODEL.contains("sdk")

    private fun renderAndValidate(duration: ReelDuration, language: ReelLanguage) = runBlocking {
        val c = app.container
        val dir = File(ctx.cacheDir, "fixtures_${duration.seconds}").apply { mkdirs() }
        val sec = duration.sectionMs / 1000.0
        // Section 1 recorded longer than needed (spec: 14 s for a 10 s section), section 3 slightly long.
        val f1 = FixtureMedia.clip(ctx, dir, "s1", Color.rgb(80, 30, 50), sec * 1.4)
        val f2 = FixtureMedia.clip(ctx, dir, "s2", Color.rgb(20, 60, 90), sec * 1.5)
        val f3 = FixtureMedia.clip(ctx, dir, "s3", Color.rgb(60, 60, 20), sec * 1.2)

        val p = c.projects.create(language, "aerolase", duration, ReelStyle.EDUCATIONAL, "E2E ${duration.label}")
        val sections = c.projects.getBundle(p.id)!!.sections
        val scripts = if (language == ReelLanguage.IRAQI_ARABIC) listOf("شنو الفرق بين حب الشباب والتصبغات؟", "جهاز Aerolase ممكن يساعد بعض الحالات حسب حالة البشرة.", "احجز تقييم لبشرتك.")
        else listOf("Is laser right for every skin?", "Aerolase may help some acne concerns after assessment.", "Book a skin evaluation.")
        c.projects.applyPlan(p.id, PlanMeta(title = "E2E"), sections.mapIndexed { i, s -> s.copy(spokenScript = scripts[i], onScreenText = if (i == 0) "Aerolase?" else "") }, "test")

        val c1 = c.importer.import(p.id, sections[0].id, Uri.fromFile(f1), ImportOptions(ClipRole.PRIMARY, beauty = BeautySettings.forPreset(BeautyPreset.NATURAL)), ClipOrigin.CAMERA, "video/mp4")
        val c2 = c.importer.import(p.id, sections[1].id, Uri.fromFile(f2), ImportOptions(ClipRole.PRIMARY, clinical = true), ClipOrigin.IMPORT, "video/mp4")
        val c3 = c.importer.import(p.id, sections[2].id, Uri.fromFile(f3), ImportOptions(ClipRole.PRIMARY), ClipOrigin.CAMERA, "video/mp4")
        // Manual trims to exactly one section length (as a user would in the trim screen)
        c.projects.upsertClip(c1.copy(trimStartMs = 1_500, trimEndMs = 1_500 + duration.sectionMs))
        c.projects.upsertClip(c2.copy(trimStartMs = 2_000, trimEndMs = 2_000 + duration.sectionMs))
        c.projects.upsertClip(c3.copy(trimStartMs = 500, trimEndMs = 500 + duration.sectionMs))
        assertThat(c.projects.getClip(c2.id)!!.beauty.enabled).isFalse() // clinical footage: never beautified by default

        c.projects.updateEdit(p.id) { e -> e.copy(music = e.music.copy(trackId = "calm_clinical"), sfx = listOf(SfxCue("x", duration.sectionMs - 200, "soft_whoosh")),
            transitions = e.transitions.copy(defaultType = TransitionType.CROSSFADE), color = e.color.copy(look = ColorLook.CLEAN)) }
        c.settings.updateBrand { BrandProfile(brandName = "Reelify Clinic", clinicName = "Reelify Clinic", presenterName = "Dr. Test", professionalTitle = "Laser Specialist", ctaText = "Book your skin evaluation", ctaTextArabic = "احجز تقييم بشرتك", instagram = "@reelify") }
        c.projects.update(p.id) { it.copy(brandSnapshot = BrandProfile(brandName = "Reelify Clinic", presenterName = "Dr. Test", ctaText = "Book your skin evaluation", ctaTextArabic = "احجز تقييم بشرتك", instagram = "@reelify")) }

        var bundle = c.projects.getBundle(p.id)!!
        val tl = c.preparer.resolve(bundle)
        assertThat(tl.blocking).isEmpty()
        val words = CaptionRemapper.anchor(CaptionBuilder.fromScript(bundle.sections.map { s -> Triple(tl.sectionMs * (s.index - 1), tl.sectionMs, s.spokenScript) }), tl.dialogue)
        c.projects.updateCaptions(p.id, CaptionTrack(language, CaptionBuilder.segment(words, 4), CaptionSource.SCRIPT_ESTIMATE, 0))

        var inputs = c.preparer.inputs(p.id)
        if (isEmulator) inputs = inputs.copy(width = 360, height = 640)
        val built = c.compositionFactory.build(inputs, RenderPurpose.EXPORT)
        val out = File(dir, "reel.mp4")
        val watchdog = if (isEmulator) 30 * 60_000L else 60_000L
        c.transformerRunner.export(built.composition, out, 3_000_000, audioOnly = false, maxDelayBetweenSamplesMs = watchdog) { android.util.Log.w("ReelifyE2E", "render progress $it%") }
        val report = ExportValidator.validate(out, inputs.width, inputs.height, duration.totalMs, 30, expectAudio = true,
            captionsExpected = true, captionFramesDrawn = built.overlayRenderer!!.captionRenders,
            overlaysExpected = true, overlayFramesDrawn = built.overlayRenderer!!.overlayRenders)
        assertThat(report.summary.joinToString("\n")).doesNotContain("✗")
        assertThat(report.ok).isTrue()

        // Dialogue extraction for Deepgram produces a real AAC audio file
        val dialogue = c.compositionFactory.build(c.preparer.inputs(p.id, normalize = false), RenderPurpose.DIALOGUE)
        val m4a = File(dir, "dialogue.m4a")
        c.transformerRunner.export(dialogue.composition, m4a, 0, audioOnly = true, maxDelayBetweenSamplesMs = watchdog) { android.util.Log.w("ReelifyE2E", "dialogue progress $it%") }
        val audioReport = ExportValidator.validate(m4a, 0, 0, duration.totalMs - 0, 30, expectAudio = true, false, 0, false, 0, audioOnly = true)
        assertThat(audioReport.checks.first { it.first.startsWith("Audio track") }.second).isTrue()

        // Re-render after an edit (trim change) still yields an exact-length reel
        c.projects.upsertClip(c.projects.getClip(c1.id)!!.copy(trimStartMs = 2_500, trimEndMs = 2_500 + duration.sectionMs))
        bundle = c.projects.getBundle(p.id)!!
        assertThat(c.preparer.resolve(bundle).video.sumOf { it.durationMs }).isEqualTo(duration.totalMs)
    }

    @Test fun export30sIraqiArabic() = renderAndValidate(ReelDuration.S30, ReelLanguage.IRAQI_ARABIC)
    @Test fun export45sEnglish() = renderAndValidate(ReelDuration.S45, ReelLanguage.ENGLISH)
    @Test fun export60sEnglish() = renderAndValidate(ReelDuration.S60, ReelLanguage.ENGLISH)
}
