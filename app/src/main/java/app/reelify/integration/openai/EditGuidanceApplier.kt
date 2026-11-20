package app.reelify.integration.openai

import app.reelify.core.library.SoundLibrary
import app.reelify.core.model.CaptionAnimation
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.CaptionStyle
import app.reelify.core.model.ColorLook
import app.reelify.core.model.EditSettings
import app.reelify.core.model.MusicMood
import app.reelify.core.model.TransitionType

/**
 * Deterministically applies an AI-produced declarative edit plan to [EditSettings]. Unknown or
 * "unchanged" values are ignored, so a malformed suggestion can never corrupt the edit.
 */
object EditGuidanceApplier {
    private const val U = AiSchemas.UNCHANGED

    fun apply(edit: EditSettings, g: EditGuidanceDto, speechDensity: Float = 0.6f): EditSettings {
        var e = edit
        // Captions
        CaptionPresetId.entries.firstOrNull { it.name.lowercase() == g.captionPreset }?.let { id ->
            val base = CaptionStyle.preset(id)
            e = e.copy(captions = e.captions.copy(style = base.copy(timingOffsetMs = e.captions.style.timingOffsetMs)))
        }
        CaptionAnimation.entries.firstOrNull { it.name.lowercase() == g.captionAnimation }?.let { a ->
            e = e.copy(captions = e.captions.copy(style = e.captions.style.copy(animation = a)))
        }
        when (g.captionSize) {
            "smaller" -> e = e.copy(captions = e.captions.copy(style = e.captions.style.copy(sizeSp = (e.captions.style.sizeSp * 0.88f).coerceAtLeast(36f))))
            "larger" -> e = e.copy(captions = e.captions.copy(style = e.captions.style.copy(sizeSp = (e.captions.style.sizeSp * 1.12f).coerceAtMost(96f))))
        }
        if (g.highlightKeywords.isNotEmpty()) {
            e = e.copy(captions = e.captions.copy(highlightKeywords = (e.captions.highlightKeywords + g.highlightKeywords).distinct().take(12)))
        }
        // Transitions
        TransitionType.fromWire(g.transitionStyle)?.let { t -> e = e.copy(transitions = e.transitions.copy(defaultType = t)) }
        when (g.transitionAmount) {
            "fewer" -> e = e.copy(transitions = e.transitions.copy(withinSection = TransitionType.CUT, overrides = emptyMap(), durationMs = (e.transitions.durationMs * 0.8).toLong().coerceAtLeast(250)))
            "more" -> e = e.copy(transitions = e.transitions.copy(withinSection = TransitionType.CROSSFADE))
        }
        // Music
        MusicMood.fromWire(g.musicMood)?.let { mood ->
            val track = SoundLibrary.match(mood, speechDensity, preferCalm = false)
            e = e.copy(music = e.music.copy(trackId = track.id))
        }
        when (g.musicLevel) {
            "much_lower" -> e = e.copy(music = e.music.copy(volume = (e.music.volume * 0.55f).coerceAtLeast(0.05f)))
            "lower" -> e = e.copy(music = e.music.copy(volume = (e.music.volume * 0.75f).coerceAtLeast(0.05f)))
            "higher" -> e = e.copy(music = e.music.copy(volume = (e.music.volume * 1.25f).coerceAtMost(0.8f)))
        }
        // Color
        ColorLook.entries.firstOrNull { it.name.lowercase() == g.colorLook }?.let { look -> e = e.copy(color = e.color.copy(look = look)) }
        when (g.colorIntensity) {
            "subtle" -> e = e.copy(color = e.color.copy(intensity = 0.4f))
            "medium" -> e = e.copy(color = e.color.copy(intensity = 0.7f))
            "strong" -> e = e.copy(color = e.color.copy(intensity = 1f))
        }
        // Effects
        fun tri(v: String, cur: Boolean) = when (v) { "on" -> true; "off" -> false; else -> cur }
        val fx = e.effects
        e = e.copy(
            effects = fx.copy(
                pushIn = tri(g.effects.pushIn, fx.pushIn),
                vignette = when (g.effects.vignette) { "on" -> fx.vignette.coerceAtLeast(0.3f); "off" -> 0f; else -> fx.vignette },
                lightOverlay = tri(g.effects.lightOverlay, fx.lightOverlay),
                introTitle = tri(g.effects.introTitle, fx.introTitle),
            ),
            branding = e.branding.copy(
                lowerThird = tri(g.effects.lowerThird, e.branding.lowerThird),
                ctaOverlay = tri(g.effects.ctaOverlay, e.branding.ctaOverlay),
            ),
        )
        when (g.sfxLevel) {
            "none" -> e = e.copy(sfx = emptyList())
            "sparse" -> e = e.copy(sfx = e.sfx.take(2), audio = e.audio.copy(sfxVolume = minOf(e.audio.sfxVolume, 0.5f)))
        }
        return e
    }

    fun describe(e: EditSettings): String = buildString {
        append("captions ${if (e.captions.enabled) e.captions.style.preset.label + "/" + e.captions.style.animation.label else "off"}; ")
        append("transitions ${e.transitions.defaultType.label} ${e.transitions.durationMs}ms; ")
        append("music ${SoundLibrary.track(e.music.trackId)?.let { it.title + " (" + it.mood.label + ")" } ?: "none"} vol ${"%.2f".format(e.music.volume)}; ")
        append("color ${e.color.look.label} ${"%.1f".format(e.color.intensity)}; ")
        append("push-in ${e.effects.pushIn}, vignette ${e.effects.vignette}, light ${e.effects.lightOverlay}; ")
        append("lower third ${e.branding.lowerThird}, CTA ${e.branding.ctaOverlay}; sfx ${e.sfx.size}")
    }
}
