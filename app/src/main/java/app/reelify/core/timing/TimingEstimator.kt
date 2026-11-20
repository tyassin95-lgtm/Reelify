package app.reelify.core.timing

import app.reelify.core.model.ReelLanguage

enum class TimingFit(val label: String) {
    EMPTY("No dialogue"),
    TOO_SHORT("Likely too short"),
    FITS("Fits comfortably"),
    TOO_LONG("Likely too long"),
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
