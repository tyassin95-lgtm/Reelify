package app.reelify.core.library

import app.reelify.core.model.MusicMood

data class MusicTrack(
    val id: String,
    val title: String,
    val mood: MusicMood,
    val bpm: Int,
    val energy: Float,      // 0 calm .. 1 energetic
    val density: Float,     // how busy the arrangement is (lower suits dense speech)
    val assetPath: String,  // under assets/
    val durationMs: Long,
)

data class SoundEffect(val id: String, val title: String, val assetPath: String, val durationMs: Long)

/**
 * Bundled audio catalog. All bundled music and SFX are ORIGINAL works procedurally synthesized for
 * Reelify (see tools/generate_audio.py and assets/licenses/AUDIO_LICENSE.txt) and are dedicated to
 * the public domain (CC0). No third-party or commercial recordings are included.
 */
object SoundLibrary {
    val music: List<MusicTrack> = listOf(
        MusicTrack("elegant_silk", "Silk Atelier", MusicMood.ELEGANT, 84, 0.35f, 0.35f, "audio/music/elegant_silk.m4a", 64_000),
        MusicTrack("cinematic_rise", "Quiet Horizon", MusicMood.CINEMATIC, 72, 0.45f, 0.45f, "audio/music/cinematic_rise.m4a", 64_000),
        MusicTrack("modern_pulse", "Glass Pulse", MusicMood.MODERN, 104, 0.6f, 0.55f, "audio/music/modern_pulse.m4a", 64_000),
        MusicTrack("luxury_velvet", "Velvet Hour", MusicMood.LUXURY, 90, 0.4f, 0.4f, "audio/music/luxury_velvet.m4a", 64_000),
        MusicTrack("soft_electronic_glow", "Soft Glow", MusicMood.SOFT_ELECTRONIC, 100, 0.5f, 0.5f, "audio/music/soft_electronic_glow.m4a", 64_000),
        MusicTrack("minimal_air", "White Room", MusicMood.MINIMAL, 80, 0.2f, 0.2f, "audio/music/minimal_air.m4a", 64_000),
        MusicTrack("motivational_lift", "Lift", MusicMood.MOTIVATIONAL, 112, 0.75f, 0.6f, "audio/music/motivational_lift.m4a", 64_000),
        MusicTrack("calm_clinical", "Clear Skin", MusicMood.CALM_CLINICAL, 76, 0.25f, 0.25f, "audio/music/calm_clinical.m4a", 64_000),
        MusicTrack("social_bounce", "Feed Bounce", MusicMood.SOCIAL, 118, 0.8f, 0.65f, "audio/music/social_bounce.m4a", 64_000),
        MusicTrack("ambient_haze", "Morning Haze", MusicMood.AMBIENT, 66, 0.15f, 0.2f, "audio/music/ambient_haze.m4a", 64_000),
    )

    val sfx: List<SoundEffect> = listOf(
        SoundEffect("soft_whoosh", "Soft whoosh", "audio/sfx/soft_whoosh.m4a", 700),
        SoundEffect("transition_sweep", "Transition sweep", "audio/sfx/transition_sweep.m4a", 900),
        SoundEffect("subtle_impact", "Subtle impact", "audio/sfx/subtle_impact.m4a", 900),
        SoundEffect("soft_click", "Soft click", "audio/sfx/soft_click.m4a", 150),
        SoundEffect("rise", "Rise", "audio/sfx/rise.m4a", 1500),
        SoundEffect("gentle_shimmer", "Gentle shimmer", "audio/sfx/gentle_shimmer.m4a", 1400),
        SoundEffect("clean_pop", "Clean pop", "audio/sfx/clean_pop.m4a", 200),
        SoundEffect("text_reveal", "Text reveal", "audio/sfx/text_reveal.m4a", 500),
        SoundEffect("light_accent", "Light accent", "audio/sfx/light_accent.m4a", 800),
    )

    val sfxIds: List<String> get() = sfx.map { it.id }
    val musicIds: List<String> get() = music.map { it.id }

    fun track(id: String?): MusicTrack? = music.firstOrNull { it.id == id }
    fun effect(id: String?): SoundEffect? = sfx.firstOrNull { it.id == id }

    /**
     * Deterministic music matching from plan mood + speaking density: prefers the mood match,
     * then lower arrangement density when speech is dense.
     */
    fun match(mood: MusicMood?, speechDensity: Float, preferCalm: Boolean): MusicTrack {
        return music.maxBy { t ->
            var score = 0f
            if (t.mood == mood) score += 3f
            score -= kotlin.math.abs(t.density - (1f - speechDensity) * 0.7f)
            if (preferCalm) score -= t.energy * 0.8f
            score
        }
    }
}
