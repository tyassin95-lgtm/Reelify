package app.reelify.media

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.reelify.Fixtures
import app.reelify.core.library.SoundLibrary
import app.reelify.core.media.engine.CompositionFactory
import app.reelify.core.media.engine.PreviewProxyManager
import app.reelify.core.media.engine.ReelFonts
import app.reelify.core.media.engine.RenderInputs
import app.reelify.core.media.engine.RenderPurpose
import app.reelify.core.media.engine.TimelineResolver
import app.reelify.core.media.engine.TransformerRunner
import app.reelify.core.media.engine.singleClipPreview
import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.BrandProfile
import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.CaptionWord
import app.reelify.core.model.ClipRole
import app.reelify.core.model.ColorGrade
import app.reelify.core.model.EditSettings
import app.reelify.core.model.FillStrategy
import app.reelify.core.model.MediaKind
import app.reelify.core.model.MusicSettings
import app.reelify.core.model.SfxCue
import app.reelify.core.model.TransitionSettings
import app.reelify.core.model.TransitionType
import app.reelify.core.storage.MediaStorage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PreviewAndCompositionSafetyTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val storage = MediaStorage(ctx)
    private val fonts = ReelFonts(ctx.assets)
    private val factory = CompositionFactory(ctx, fonts)
    private val runner = TransformerRunner(ctx)
    private val proxyManager = PreviewProxyManager(ctx, storage, factory, runner)

    @Test fun `every singleClipPreview configuration sets explicit durationUs instead of TIME_UNSET`() {
        val configs = listOf(
            // Single recorded talking-head video with audio
            Fixtures.clip("rec_audio", "s1", hasAudio = true),
            // Muted video without audio
            Fixtures.clip("muted_vid", "s1", hasAudio = false).copy(muted = true),
            // Trimmed short or zero-duration metadata clip
            Fixtures.clip("short_zero", "s1", source = 0L, trimStart = 0L, trimEnd = 0L, hasAudio = true),
            // Still photo clip
            Fixtures.clip("photo", "s2", kind = MediaKind.IMAGE, source = 0L, trimStart = 0L, trimEnd = 0L, hasAudio = false),
            // Standalone voiceover audio clip
            Fixtures.clip("vo_audio", "s2", role = ClipRole.VOICEOVER, kind = MediaKind.AUDIO, source = 10_000L, hasAudio = true),
        )

        configs.forEach { clip ->
            val comp = factory.singleClipPreview(clip, ColorGrade(), width = 720, height = 1280)
            assertThat(comp.sequences).isNotEmpty()
            comp.sequences.forEach { seq ->
                assertThat(seq.editedMediaItems).isNotEmpty()
                seq.editedMediaItems.forEach { item ->
                    assertThat(item.durationUs).isNotEqualTo(C.TIME_UNSET)
                    assertThat(item.durationUs).isGreaterThan(0L)
                }
            }
        }
    }

    @Test fun `full multi-track compositions explicitly populate durationUs across video dialogue music and sfx`() {
        val pDir = storage.cacheDir("comp_test").apply { mkdirs() }
        val clips = listOf(
            Fixtures.clip("c1", "s1", hasAudio = true).copy(beauty = BeautySettings.forPreset(BeautyPreset.NATURAL)),
            Fixtures.clip("c2", "s2", hasAudio = false, kind = MediaKind.IMAGE),
            Fixtures.clip("broll2", "s2", role = ClipRole.BROLL, source = 4_000L, trimEnd = 4_000L, placement = 2_000L),
            Fixtures.clip("vo2", "s2", role = ClipRole.VOICEOVER, kind = MediaKind.AUDIO, hasAudio = true),
            Fixtures.clip("c3", "s3", hasAudio = true),
        )
        val project = Fixtures.project().copy(
            edit = EditSettings(
                music = MusicSettings(trackId = "calm_premium", volume = 0.25f),
                transitions = TransitionSettings(defaultType = TransitionType.DISSOLVE),
                sfx = listOf(SfxCue("cue1", 9_900L, SoundLibrary.sfxIds.first(), 0.5f)),
            ),
        )
        val bundle = Fixtures.bundle(clips, project = project)
        val timeline = TimelineResolver.resolve(bundle)
        assertThat(timeline.isRenderable).isTrue()

        val fakeMusic = File(pDir, "fake_music.m4a").apply { writeBytes(ByteArray(32)) }
        val inputs = RenderInputs(
            bundle = bundle,
            timeline = timeline,
            captions = listOf(
                CaptionSegment("cap1", 200L, 3_000L, listOf(CaptionWord("PicoStar", 200L, 1200L, emphasized = true))),
            ),
            brand = BrandProfile(brandName = "Dr. Sara Clinic"),
            width = 720,
            height = 1280,
            frameRate = 30,
            speechRanges = listOf(200L..9_500L),
            musicUri = Uri.fromFile(fakeMusic),
            musicDurationMs = 15_000L,
            cacheDir = pDir,
        )

        val built = factory.build(inputs, RenderPurpose.PREVIEW)
        assertThat(built.composition.sequences.size).isAtLeast(3)
        built.composition.sequences.forEach { seq ->
            seq.editedMediaItems.forEach { item ->
                assertThat(item.durationUs).isNotEqualTo(C.TIME_UNSET)
                assertThat(item.durationUs).isGreaterThan(0L)
            }
        }
    }

    @Test fun `preview proxy fingerprints are deterministic and invalidate on any project change`() {
        val pDir = storage.cacheDir("fp_test").apply { mkdirs() }
        val clips = listOf(
            Fixtures.clip("c1", "s1"),
            Fixtures.clip("c2", "s2"),
            Fixtures.clip("c3", "s3"),
        )
        val baseBundle = Fixtures.bundle(clips)
        val baseInputs = RenderInputs(
            bundle = baseBundle,
            timeline = TimelineResolver.resolve(baseBundle),
            captions = emptyList(),
            brand = BrandProfile(brandName = "Cleopatra Clinic"),
            width = 720,
            height = 1280,
            frameRate = 30,
            speechRanges = emptyList(),
            cacheDir = pDir,
        )

        val fp1 = proxyManager.fingerprint(baseInputs)
        val fp1Again = proxyManager.fingerprint(baseInputs)
        assertThat(fp1).isEqualTo(fp1Again)

        // Changing trim invalidates cache key
        val trimmedClips = listOf(clips[0].copy(trimStartMs = 500L), clips[1], clips[2])
        val trimmedBundle = Fixtures.bundle(trimmedClips)
        val fpTrim = proxyManager.fingerprint(
            baseInputs.copy(bundle = trimmedBundle, timeline = TimelineResolver.resolve(trimmedBundle)),
        )
        assertThat(fpTrim).isNotEqualTo(fp1)

        // Changing beauty/caption/music/Arabic text invalidates cache key
        val editedProject = baseBundle.project.copy(
            edit = baseBundle.project.edit.copy(transitions = TransitionSettings(defaultType = TransitionType.LIGHT_FLASH)),
        )
        val editedBundle = baseBundle.copy(project = editedProject)
        val fpTransition = proxyManager.fingerprint(baseInputs.copy(bundle = editedBundle))
        assertThat(fpTransition).isNotEqualTo(fp1)

        // Latest valid proxy selection ignores tiny/tmp files
        File(pDir, "preview_proxy_${fp1}.tmp.mp4").writeBytes(ByteArray(4096))
        assertThat(proxyManager.latestValidProxy("fp_test")).isNull()
        val realProxy = proxyManager.proxyFile("fp_test", fp1)
        realProxy.writeBytes(ByteArray(2048))
        assertThat(proxyManager.cachedProxy("fp_test", fp1)).isEqualTo(realProxy)
        assertThat(proxyManager.latestValidProxy("fp_test")).isEqualTo(realProxy)
    }

    @Test fun `card fill extension produces valid positive item duration`() {
        val pDir = storage.cacheDir("fill_test").apply { mkdirs() }
        val clips = listOf(
            Fixtures.clip("c1", "s1", source = 6_000L, trimEnd = 6_000L),
            Fixtures.clip("c2", "s2"),
            Fixtures.clip("c3", "s3"),
        )
        val project = Fixtures.project().copy(
            edit = EditSettings(fill = mapOf("s1" to FillStrategy.BRAND_CARD)),
        )
        val bundle = Fixtures.bundle(clips, project = project)
        val tl = TimelineResolver.resolve(bundle)
        assertThat(tl.isRenderable).isTrue()
        val built = factory.build(
            RenderInputs(bundle, tl, emptyList(), BrandProfile(brandName = "Dr. Sara"), 540, 960, 30, emptyList(), cacheDir = pDir),
            RenderPurpose.PREVIEW,
        )
        built.composition.sequences.forEach { seq ->
            seq.editedMediaItems.forEach { item ->
                assertThat(item.durationUs).isNotEqualTo(C.TIME_UNSET)
                assertThat(item.durationUs).isAtLeast(1_000L)
            }
        }
    }
}
