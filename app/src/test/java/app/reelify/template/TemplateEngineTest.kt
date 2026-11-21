package app.reelify.template

import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.CaptionStyle
import app.reelify.core.model.ColorLook
import app.reelify.core.model.EditGroup
import app.reelify.core.model.EditSettings
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelSection
import app.reelify.core.model.ShotType
import app.reelify.core.model.TransitionType
import app.reelify.core.template.BuiltInTemplates
import app.reelify.core.template.CameraFacing
import app.reelify.core.template.MediaRequirement
import app.reelify.core.template.ProductionPlanner
import app.reelify.core.template.SectionScript
import app.reelify.core.template.SfxStrategy
import app.reelify.core.template.ShotOverride
import app.reelify.core.template.TemplateOverrides
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TemplateEngineTest {
    private val all = BuiltInTemplates.all

    @Test fun `every built-in template has exactly three ordered shots and all presets resolve`() {
        assertThat(all).hasSize(6)
        assertThat(all.map { it.id }.toSet()).hasSize(6)
        all.forEach { t ->
            assertThat(t.shots.map { it.index }.sorted()).containsExactly(1, 2, 3).inOrder()
            assertThat(t.supportedDurations).containsExactlyElementsIn(ReelDuration.entries)
            assertThat(BuiltInTemplates.musicPresets).containsKey(t.musicPresetId)
            assertThat(BuiltInTemplates.transitionPresets).containsKey(t.transitionPresetId)
            assertThat(BuiltInTemplates.brandingPresets).containsKey(t.brandingPresetId)
            assertThat(SoundLibrary.track(ProductionPlanner.musicPreset(t).trackId)).isNotNull()
            t.shots.forEach { s ->
                assertThat(BuiltInTemplates.cameraPresets).containsKey(s.cameraPresetId)
                assertThat(BuiltInTemplates.teleprompterPresets).containsKey(s.teleprompterPresetId)
                assertThat(s.instruction.en).isNotEmpty(); assertThat(s.instruction.ar).isNotEmpty()
                assertThat(s.writingBrief).isNotEmpty()
                s.sfxIn?.let { assertThat(SoundLibrary.effect(it)).isNotNull() }
            }
        }
    }

    @Test fun `templates are genuinely different formats, not renamed copies`() {
        val signatures = all.map { t -> listOf(t.shots.map { it.role }, t.shots.map { it.requiredMedia }, t.captionPresetId, t.colorLook, t.transitionPresetId, t.musicPresetId) }
        assertThat(signatures.toSet()).hasSize(all.size)
        // Every template differs from every other one in at least 3 of: roles, media, captions, colour, transitions, music.
        for (i in all.indices) for (j in i + 1 until all.size) {
            val diff = signatures[i].zip(signatures[j]).count { (a, b) -> a != b }
            assertThat(diff).isAtLeast(3)
        }
    }

    @Test fun `section timing is exactly a third of the reel for 30, 45 and 60 seconds`() {
        assertThat(ReelDuration.S30.sectionMs).isEqualTo(10_000)
        assertThat(ReelDuration.S45.sectionMs).isEqualTo(15_000)
        assertThat(ReelDuration.S60.sectionMs).isEqualTo(20_000)
        ReelDuration.entries.forEach { d -> assertThat((0 until 3).map { d.sectionStartMs(it) }).containsExactly(0L, d.sectionMs, d.sectionMs * 2).inOrder() }
    }

    @Test fun `golden - shot structure and instructions are stable (bump the version when changing a template)`() {
        val golden = mapOf(
            "expert_explains" to Triple(1, listOf("hook", "explanation", "conclusion_cta"), listOf("talking_front", "device_rear", "talking_front_close")),
            "myth_buster" to Triple(1, listOf("myth", "correction", "takeaway"), listOf("talking_front", "talking_front", "talking_front_close")),
            "treatment_showcase" to Triple(1, listOf("introduction", "showcase", "suitability_cta"), listOf("talking_front", "broll_rear", "talking_front_close")),
            "three_things" to Triple(1, listOf("insight_1", "insight_2", "insight_3_cta"), listOf("talking_front", "talking_front", "talking_front_close")),
            "patient_question" to Triple(1, listOf("question", "answer", "qualification"), listOf("talking_front", "talking_front", "talking_front_close")),
            "problem_approach" to Triple(1, listOf("concern", "approach", "expectations_cta"), listOf("talking_front", "device_rear", "talking_front_close")),
        )
        all.forEach { t ->
            val (version, roles, cams) = golden.getValue(t.id)
            assertThat(t.version).isEqualTo(version)
            assertThat(t.shots.sortedBy { it.index }.map { it.role.wire }).isEqualTo(roles)
            assertThat(t.shots.sortedBy { it.index }.map { it.cameraPresetId }).isEqualTo(cams)
        }
        assertThat(BuiltInTemplates.EXPERT_EXPLAINS.shot(1).instruction.en).isEqualTo("Look into the lens. Phone at eye level, an arm's length away.")
    }

    @Test fun `sections are produced deterministically with stable ids`() {
        val t = BuiltInTemplates.EXPERT_EXPLAINS
        var n = 0
        val a = ProductionPlanner.sections(t, emptyList(), { "id-${++n}" }, 5)
        val b = ProductionPlanner.sections(t, a, { error("ids must be reused") }, 5)
        assertThat(b).isEqualTo(a)
        assertThat(a.map { it.shotType }).containsExactly(ShotType.TALKING_HEAD, ShotType.VOICEOVER_BROLL, ShotType.TALKING_HEAD).inOrder()
        assertThat(a[1].isVoiceover).isTrue()
    }

    private fun scripts() = listOf(
        SectionScript(1, "Hook line and intro", "Headline one", listOf("intro")),
        SectionScript(2, "Explanation over footage", "Headline two", listOf("footage", "not-in-text")),
        SectionScript(3, "Takeaway and call to action", "Headline three", listOf("Takeaway")),
    )

    @Test fun `AI script can never change template-owned fields`() {
        val t = BuiltInTemplates.MYTH_BUSTER
        var n = 0
        val base = ProductionPlanner.sections(t, emptyList(), { "s${++n}" }, 0)
        // Simulate a corrupted/malicious state: template-owned fields were tampered with.
        val tampered = base.map { it.copy(shotType = ShotType.BEFORE_AFTER, cameraDirection = "drone shot", transitionOut = TransitionType.SLIDE, sfxCue = "rise", framing = "wide") }
        val merged = ProductionPlanner.mergeScript(t, tampered, scripts(), now = 1)
        merged.forEachIndexed { i, s ->
            assertThat(s.shotType).isEqualTo(base[i].shotType)
            assertThat(s.cameraDirection).isEqualTo(base[i].cameraDirection)
            assertThat(s.transitionOut).isEqualTo(base[i].transitionOut)
            assertThat(s.sfxCue).isEqualTo(base[i].sfxCue)
            assertThat(s.framing).isEqualTo(base[i].framing)
            assertThat(s.id).isEqualTo(base[i].id)
        }
        assertThat(merged.map { it.spokenScript }).containsExactly("Hook line and intro", "Explanation over footage", "Takeaway and call to action").inOrder()
        // Emphasis words must appear in the dialogue.
        assertThat(merged[1].captionEmphasis).containsExactly("footage")
    }

    @Test fun `locked sections and onlyIndex are respected - regenerating one shot preserves the others`() {
        val t = BuiltInTemplates.THREE_THINGS
        var n = 0
        val base = ProductionPlanner.mergeScript(t, ProductionPlanner.sections(t, emptyList(), { "s${++n}" }, 0), scripts(), now = 1)
        val locked = base.map { if (it.index == 1) it.copy(locked = true) else it }
        val newer = listOf(SectionScript(1, "NEW 1", "", emptyList()), SectionScript(2, "NEW 2", "", emptyList()), SectionScript(3, "NEW 3", "", emptyList()))
        val m1 = ProductionPlanner.mergeScript(t, locked, newer, respectLocks = true, now = 2)
        assertThat(m1.map { it.spokenScript }).containsExactly("Hook line and intro", "NEW 2", "NEW 3").inOrder()
        val m2 = ProductionPlanner.mergeScript(t, locked, newer, respectLocks = true, onlyIndex = 2, now = 3)
        assertThat(m2.map { it.spokenScript }).containsExactly("Hook line and intro", "NEW 2", "Takeaway and call to action").inOrder()
    }

    @Test fun `template edit defaults follow the template exactly`() {
        val t = BuiltInTemplates.PATIENT_QUESTION
        val e = ProductionPlanner.templateEdit(t, ReelDuration.S45)
        assertThat(e.captions.style.preset).isEqualTo(CaptionPresetId.CLEAN_CLINICAL)
        assertThat(e.color.look).isEqualTo(ColorLook.COOL_CLINICAL)
        assertThat(e.music.trackId).isEqualTo("calm_clinical")
        assertThat(e.transitions.defaultType).isEqualTo(TransitionType.CROSSFADE)
        assertThat(e.effects.pushIn).isFalse()
        assertThat(e.branding.logoReveal).isFalse()
        // INTRO_ONLY → a single subtle cue at the start
        assertThat(t.sfxStrategy).isEqualTo(SfxStrategy.INTRO_ONLY)
        assertThat(e.sfx).hasSize(1); assertThat(e.sfx.single().timeMs).isEqualTo(200)
    }

    @Test fun `SFX accents land on section boundaries and are deterministic`() {
        val t = BuiltInTemplates.THREE_THINGS
        val a = ProductionPlanner.sfxCues(t, ReelDuration.S60, 3500)
        val b = ProductionPlanner.sfxCues(t, ReelDuration.S60, 3500)
        assertThat(a).isEqualTo(b)
        assertThat(a.map { it.timeMs }).containsExactly(200L, 20_000L - 120, 40_000L - 120).inOrder()
        assertThat(a.all { it.gain <= 0.5f }).isTrue()
    }

    @Test fun `user overrides win over template automation and re-application is idempotent`() {
        val t = BuiltInTemplates.EXPERT_EXPLAINS
        val start = ProductionPlanner.templateEdit(t, ReelDuration.S30)
        val user = start.copy(
            music = start.music.copy(trackId = "ambient_haze", volume = 0.1f),
            captions = start.captions.copy(style = CaptionStyle.preset(CaptionPresetId.BOLD_ENGAGEMENT)),
            userOverrides = setOf(EditGroup.MUSIC, EditGroup.CAPTIONS),
        )
        val once = ProductionPlanner.applyTemplateDefaults(user, t, ReelDuration.S30)
        val twice = ProductionPlanner.applyTemplateDefaults(once, t, ReelDuration.S30)
        assertThat(once.music.trackId).isEqualTo("ambient_haze")
        assertThat(once.captions.style.preset).isEqualTo(CaptionPresetId.BOLD_ENGAGEMENT)
        assertThat(once.color.look).isEqualTo(t.colorLook)
        assertThat(twice).isEqualTo(once)
        // Without overrides the template is restored.
        val reset = ProductionPlanner.applyTemplateDefaults(user.copy(userOverrides = emptySet()), t, ReelDuration.S30)
        assertThat(reset.music.trackId).isEqualTo("elegant_silk")
    }

    @Test fun `changed groups are detected for override bookkeeping`() {
        val a = EditSettings()
        val b = a.copy(color = a.color.copy(intensity = 0.2f), music = a.music.copy(volume = 0.5f))
        assertThat(a.changedGroups(b)).containsExactly(EditGroup.COLOR, EditGroup.MUSIC)
        assertThat(a.changedGroups(a.copy(editVersion = 9))).isEmpty()
    }

    @Test fun `camera presets - talking shots are front with teleprompter and beauty, B-roll is rear without either`() {
        val t = BuiltInTemplates.TREATMENT_SHOWCASE
        val s1 = ProductionPlanner.cameraSetup(t, TemplateOverrides(), 1)
        val s2 = ProductionPlanner.cameraSetup(t, TemplateOverrides(), 2)
        assertThat(s1.facing).isEqualTo(CameraFacing.FRONT); assertThat(s1.teleprompter).isTrue(); assertThat(s1.beauty).isTrue()
        assertThat(t.shot(2).requiredMedia).isEqualTo(MediaRequirement.VOICEOVER_BROLL)
        assertThat(s2.facing).isEqualTo(CameraFacing.BACK); assertThat(s2.teleprompter).isFalse(); assertThat(s2.beauty).isFalse(); assertThat(s2.focusAssist).isTrue()
        // user overrides
        val o = TemplateOverrides().withShot(1) { ShotOverride(facing = CameraFacing.BACK, teleprompter = false, beauty = false) }
        val s1o = ProductionPlanner.cameraSetup(t, o, 1)
        assertThat(s1o.facing).isEqualTo(CameraFacing.BACK); assertThat(s1o.teleprompter).isFalse(); assertThat(s1o.beauty).isFalse()
        assertThat(ProductionPlanner.beautyFor(t, TemplateOverrides(), 2, app.reelify.core.model.BeautySettings.forPreset(app.reelify.core.model.BeautyPreset.SOFT_GLOW)).enabled).isFalse()
    }

    @Test fun `legacy sections are untouched by template code paths`() {
        val legacy = ReelSection("x", 1, spokenScript = "old", cameraDirection = "custom legacy direction")
        // mergeScript only touches sections whose index exists in the template AND has incoming script.
        val out = ProductionPlanner.mergeScript(BuiltInTemplates.EXPERT_EXPLAINS, listOf(legacy), emptyList(), now = 1)
        assertThat(out.single()).isEqualTo(legacy)
    }
}
