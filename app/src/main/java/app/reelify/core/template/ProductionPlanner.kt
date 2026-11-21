package app.reelify.core.template

import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.BrandingUse
import app.reelify.core.model.CaptionStyle
import app.reelify.core.model.ColorGrade
import app.reelify.core.model.EditGroup
import app.reelify.core.model.EditSettings
import app.reelify.core.model.MusicSettings
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelSection
import app.reelify.core.model.SfxCue
import app.reelify.core.model.TransitionSettings

/** Script content for one section — the ONLY part of a section the AI may change. */
data class SectionScript(
    val index: Int,
    val spokenScript: String,
    val onScreenHeadline: String,
    val emphasisWords: List<String>,
)

/** Resolved camera behaviour for one shot after applying user overrides. */
data class CameraSetup(
    val facing: CameraFacing,
    val teleprompter: Boolean,
    val teleprompterPreset: TeleprompterPreset,
    val beauty: Boolean,
    val faceGuide: Boolean,
    val focusAssist: Boolean,
    val framing: FramingPreset,
    val exposureHint: LocalizedText?,
)

/**
 * Builds the production plan deterministically:
 *   selected template + approved script + treatment + brand → sections & edit settings.
 * Nothing in here is random and nothing calls the network.
 */
object ProductionPlanner {

    /** Fields of [ReelSection] owned by the template. AI output can never write these. */
    fun applyTemplateFields(section: ReelSection, recipe: ShotRecipe, template: ReelTemplate): ReelSection = section.copy(
        purpose = recipe.role.purpose,
        role = recipe.role.wire,
        title = recipe.role.label.en,
        shotType = recipe.requiredMedia.shotType,
        isVoiceover = recipe.requiredMedia.needsVoice,
        mediaPlan = recipe.requiredMedia.mediaPlan,
        cameraDirection = recipe.instruction.en,
        framing = recipe.framing.label.en,
        lightingTip = recipe.lighting.en,
        deliveryDirection = recipe.delivery.en,
        bRollSuggestion = recipe.brollHint?.en.orEmpty(),
        transitionOut = recipe.transitionOut,
        sfxCue = if (template.sfxStrategy == SfxStrategy.NONE) "" else recipe.sfxIn.orEmpty(),
        editNotes = template.structureBrief,
    )

    /** Creates or re-applies the three template sections, keeping stable ids and existing script text. */
    fun sections(template: ReelTemplate, existing: List<ReelSection>, newId: (Int) -> String, now: Long): List<ReelSection> {
        require(template.shots.size == ReelDuration.SECTION_COUNT) { "Template ${template.id} must define exactly 3 shots" }
        val byIndex = existing.associateBy { it.index }
        return template.shots.sortedBy { it.index }.map { recipe ->
            val base = byIndex[recipe.index] ?: ReelSection(id = newId(recipe.index), index = recipe.index, updatedAt = now)
            applyTemplateFields(base, recipe, template).copy(updatedAt = now)
        }
    }

    /**
     * Merges AI-written script content into the sections. Only spoken script, headline and
     * emphasis words are taken from [scripts]; template-owned fields are re-asserted from the
     * template so a malformed or malicious response can't change how a shot is filmed or edited.
     * Locked (approved) sections are returned unchanged when [respectLocks] is true.
     */
    fun mergeScript(
        template: ReelTemplate,
        sections: List<ReelSection>,
        scripts: List<SectionScript>,
        respectLocks: Boolean = true,
        onlyIndex: Int? = null,
        now: Long,
    ): List<ReelSection> {
        val byIndex = scripts.associateBy { it.index }
        return sections.sortedBy { it.index }.map { s ->
            val recipe = template.shots.firstOrNull { it.index == s.index } ?: return@map s
            val incoming = byIndex[s.index]
            when {
                incoming == null -> s
                onlyIndex != null && s.index != onlyIndex -> s
                respectLocks && s.locked -> s
                else -> applyTemplateFields(
                    s.copy(
                        spokenScript = incoming.spokenScript,
                        onScreenText = incoming.onScreenHeadline,
                        captionEmphasis = incoming.emphasisWords.filter { w -> w.isNotBlank() && incoming.spokenScript.contains(w, ignoreCase = true) }.take(4),
                        updatedAt = now,
                    ),
                    recipe, template,
                )
            }
        }
    }

    fun musicPreset(template: ReelTemplate): MusicPreset = BuiltInTemplates.musicPresets[template.musicPresetId] ?: BuiltInTemplates.musicPresets.getValue("calm_premium")
    fun transitionPreset(template: ReelTemplate): TransitionPreset = BuiltInTemplates.transitionPresets[template.transitionPresetId] ?: BuiltInTemplates.transitionPresets.getValue("subtle_zoom")
    fun brandingPreset(template: ReelTemplate): BrandingPreset = BuiltInTemplates.brandingPresets[template.brandingPresetId] ?: BuiltInTemplates.brandingPresets.getValue("signature")

    /** Template default for each edit group. */
    fun templateEdit(template: ReelTemplate, duration: ReelDuration, base: EditSettings = EditSettings()): EditSettings {
        val music = musicPreset(template)
        val trans = transitionPreset(template)
        val brand = brandingPreset(template)
        val captionStyle = CaptionStyle.preset(template.captionPresetId)
        val boundary = template.shots.sortedBy { it.index }.take(2).mapIndexed { i, shot -> i.toString() to shot.transitionOut }.toMap()
        return base.copy(
            captions = base.captions.copy(style = captionStyle.copy(timingOffsetMs = base.captions.style.timingOffsetMs)),
            music = MusicSettings(
                trackId = SoundLibrary.track(music.trackId)?.id, volume = music.volume, ducking = music.ducking,
                duckDepth = music.duckDepth, fadeInMs = music.fadeInMs, fadeOutMs = music.fadeOutMs,
            ),
            transitions = TransitionSettings(
                defaultType = trans.betweenShots, durationMs = trans.durationMs,
                overrides = boundary.filterValues { it != trans.betweenShots },
                withinSection = trans.withinShot,
            ),
            color = ColorGrade(look = template.colorLook, intensity = template.colorIntensity),
            effects = base.effects.copy(
                pushIn = template.pushIn, vignette = template.vignette, lightOverlay = template.lightOverlay,
                introTitle = template.introTitle, sectionTitles = true, textEmphasis = true,
            ),
            branding = BrandingUse(
                enabled = base.branding.enabled, watermark = brand.watermark, lowerThird = brand.lowerThird,
                ctaOverlay = brand.ctaOverlay, logoReveal = brand.logoReveal, ctaDurationMs = brand.ctaDurationMs,
                logoPosition = base.branding.logoPosition, logoOffsetX = base.branding.logoOffsetX, logoOffsetY = base.branding.logoOffsetY,
            ),
            sfx = sfxCues(template, duration, brand.ctaDurationMs),
        )
    }

    /**
     * Applies template defaults to every edit group the user has NOT overridden. User changes always
     * win; running this twice yields the same result (idempotent / deterministic).
     */
    fun applyTemplateDefaults(edit: EditSettings, template: ReelTemplate, duration: ReelDuration): EditSettings {
        val t = templateEdit(template, duration, edit)
        val o = edit.userOverrides
        return edit.copy(
            captions = if (EditGroup.CAPTIONS in o) edit.captions else edit.captions.copy(style = t.captions.style),
            music = if (EditGroup.MUSIC in o) edit.music else t.music,
            transitions = if (EditGroup.TRANSITIONS in o) edit.transitions else t.transitions,
            color = if (EditGroup.COLOR in o) edit.color else t.color,
            effects = if (EditGroup.EFFECTS in o) edit.effects else t.effects,
            branding = if (EditGroup.BRANDING in o) edit.branding else t.branding,
            sfx = if (EditGroup.SFX in o) edit.sfx else t.sfx,
        )
    }

    /** Subtle SFX at fixed, meaningful positions defined by the template. Ids are stable for determinism. */
    fun sfxCues(template: ReelTemplate, duration: ReelDuration, ctaDurationMs: Long): List<SfxCue> {
        if (template.sfxStrategy == SfxStrategy.NONE) return emptyList()
        val sec = duration.sectionMs
        return template.shots.sortedBy { it.index }.mapNotNull { shot ->
            val id = shot.sfxIn ?: return@mapNotNull null
            if (SoundLibrary.effect(id) == null) return@mapNotNull null
            if (template.sfxStrategy == SfxStrategy.INTRO_ONLY && shot.index != 1) return@mapNotNull null
            val t = if (shot.index == 1) 200L else sec * (shot.index - 1) - 120
            SfxCue("tpl-${template.id}-${shot.index}", t, id, if (shot.index == 1) 0.45f else 0.38f)
        }
    }

    fun cameraSetup(template: ReelTemplate, overrides: TemplateOverrides, shotIndex: Int): CameraSetup {
        val shot = template.shot(shotIndex)
        val cam = BuiltInTemplates.cameraPresets[shot.cameraPresetId] ?: BuiltInTemplates.cameraPresets.getValue("talking_front")
        val tp = BuiltInTemplates.teleprompterPresets[shot.teleprompterPresetId] ?: BuiltInTemplates.teleprompterPresets.getValue("off")
        val o = overrides.forShot(shotIndex)
        val talking = shot.requiredMedia == MediaRequirement.TALKING_TO_CAMERA
        return CameraSetup(
            facing = o.facing ?: cam.facing,
            teleprompter = o.teleprompter ?: (tp.enabled && cam.teleprompter && talking),
            teleprompterPreset = tp,
            // Beauty is never on by default for B-roll / treatment footage (clinical integrity).
            beauty = o.beauty ?: (cam.beautyByDefault && talking),
            faceGuide = cam.faceGuide && shot.framing.faceGuide != null,
            focusAssist = cam.focusAssist,
            framing = shot.framing,
            exposureHint = cam.exposureHint,
        )
    }

    /** Beauty settings a new talking clip receives under this template (OFF for clinical footage). */
    fun beautyFor(template: ReelTemplate, overrides: TemplateOverrides, shotIndex: Int, userDefault: BeautySettings): BeautySettings {
        val setup = cameraSetup(template, overrides, shotIndex)
        if (!setup.beauty) return BeautySettings(enabled = false)
        return if (userDefault.enabled) userDefault else BeautySettings.forPreset(template.talkingBeauty)
    }
}
