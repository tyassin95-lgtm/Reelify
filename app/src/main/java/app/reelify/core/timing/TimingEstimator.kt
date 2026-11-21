package app.reelify.core.timing

import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.SectionPurpose
import app.reelify.core.model.ShotType
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class TimingFit(val label: String) {
    EMPTY("No dialogue"),
    TOO_SHORT("Likely too short"),
    FITS("Fits comfortably"),
    TOO_LONG("Likely too long"),
}

data class ShotWordBudget(
    val targetMinWords: Int,
    val targetMaxWords: Int,
    val hardMinWords: Int,
    val hardMaxWords: Int,
    val nominalWords: Int,
    val wordsPerSecond: Float,
    val sectionMs: Long,
) {
    fun promptLabel(): String = "$targetMinWords-$targetMaxWords words"
}

data class TimingEstimate(
    val words: Int,
    val speechMs: Long,
    val pauseMs: Long,
    val totalMs: Long,
    val targetMs: Long,
    val fit: TimingFit,
    /** Approximate number of words that would fit comfortably. */
    val recommendedMaxWords: Int,
) {
    val ratio: Float get() = if (targetMs == 0L) 0f else totalMs.toFloat() / targetMs
}

/**
 * Estimates spoken duration from word count, configurable speaking pace and punctuation pauses.
 * These are estimates only; once footage exists the actual clip duration is used instead.
 */
class TimingEstimator(
    private val englishWps: Float = 2.5f,
    private val arabicWps: Float = 2.1f,
) {
    fun wordsPerSecond(language: ReelLanguage) = if (language == ReelLanguage.IRAQI_ARABIC) arabicWps else englishWps

    fun wordBudgetForShot(
        language: ReelLanguage,
        sectionMs: Long,
        purpose: SectionPurpose = SectionPurpose.EXPLANATION,
        shotType: ShotType = ShotType.TALKING_HEAD,
    ): ShotWordBudget {
        val wps = wordsPerSecond(language)
        val usableSec = ((sectionMs * 0.90f - 450f) / 1000f).coerceAtLeast(2.5f)
        val nominal = (usableSec * wps).roundToInt().coerceAtLeast(10)
        val (minRatio, maxRatio, hardMinRatio) = when {
            shotType == ShotType.VOICEOVER_BROLL -> Triple(0.76f, 1.00f, 0.62f)
            purpose == SectionPurpose.CALL_TO_ACTION -> Triple(0.78f, 1.02f, 0.65f)
            else -> Triple(0.82f, 1.06f, 0.68f)
        }
        val tMin = (nominal * minRatio).roundToInt().coerceAtLeast(10)
        val tMax = (nominal * maxRatio).roundToInt().coerceAtLeast(tMin + 4)
        val hMin = (nominal * hardMinRatio).roundToInt().coerceAtLeast(8)
        val hMax = (nominal * 1.18f).roundToInt().coerceAtLeast(tMax + 3)
        return ShotWordBudget(tMin, tMax, hMin, hMax, nominal, wps, sectionMs)
    }

    fun estimatedDurationMs(text: String, language: ReelLanguage): Long {
        val words = countWords(text)
        if (words == 0) return 0L
        val wps = wordsPerSecond(language).coerceAtLeast(1.0f)
        val sentenceEnds = Regex("[.!?؟…]+").findAll(text).count()
        val commas = Regex("[,،;؛:]").findAll(text).count()
        return (words / wps * 1000f).roundToLong() + sentenceEnds * 350L + commas * 150L
    }

    companion object {
        fun calibratedPaceFromSample(wordsSpoken: Int, elapsedMs: Long): Float {
            if (wordsSpoken <= 0 || elapsedMs <= 500L) return 2.1f
            val seconds = elapsedMs / 1000f
            return (wordsSpoken / seconds).coerceIn(1.3f, 3.8f)
        }
    }

    fun countWords(text: String): Int =
        text.split(Regex("[\\s\\u200f\\u200e]+")).count { token -> token.any { it.isLetterOrDigit() } }

    fun estimate(text: String, language: ReelLanguage, targetMs: Long, brollCoverage: Float = 0f): TimingEstimate {
        val words = countWords(text)
        val wps = wordsPerSecond(language)
        val speechMs = (words / wps * 1000).toLong()
        val sentenceEnds = Regex("[.!?؟…]+").findAll(text).count()
        val commas = Regex("[,،;؛:]").findAll(text).count()
        val pauseMs = sentenceEnds * 350L + commas * 150L
        val total = speechMs + pauseMs
        // B-roll covering silence makes shorter dialogue acceptable.
        val lowerBound = targetMs * (0.55f - 0.25f * brollCoverage.coerceIn(0f, 1f))
        val fit = when {
            words == 0 -> TimingFit.EMPTY
            total > targetMs * 1.08f -> TimingFit.TOO_LONG
            total < lowerBound -> TimingFit.TOO_SHORT
            else -> TimingFit.FITS
        }
        val usable = (targetMs * 0.92f - sentenceEnds.coerceAtLeast(1) * 350L).coerceAtLeast(0f)
        return TimingEstimate(words, speechMs, pauseMs, total, targetMs, fit, (usable / 1000f * wps).toInt())
    }
}
