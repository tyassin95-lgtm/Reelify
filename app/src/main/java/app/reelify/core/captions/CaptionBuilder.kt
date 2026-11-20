package app.reelify.core.captions

import app.reelify.core.model.CaptionSegment
import app.reelify.core.model.CaptionWord
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.TerminologyEntry
import java.util.UUID

/** Builds readable caption segments from word timings and supports manual edits. */
object CaptionBuilder {
    private val sentenceEnd = Regex("[.!?؟…]$")
    private val clauseEnd = Regex("[,،;؛:]$")

    /**
     * Groups words into segments of at most [wordsPerCaption] words / [maxChars] characters,
     * breaking preferentially at punctuation and long pauses so lines read naturally in both
     * English and Arabic.
     */
    fun segment(
        words: List<CaptionWord>,
        wordsPerCaption: Int,
        maxChars: Int = 32,
        pauseBreakMs: Long = 450,
        maxSegmentMs: Long = 3200,
    ): List<CaptionSegment> {
        val out = mutableListOf<CaptionSegment>()
        var cur = mutableListOf<CaptionWord>()
        fun flush() {
            if (cur.isNotEmpty()) {
                out += CaptionSegment(UUID.randomUUID().toString(), cur.first().startMs, cur.last().endMs, cur.toList())
                cur = mutableListOf()
            }
        }
        for ((i, w) in words.withIndex()) {
            val prev = cur.lastOrNull()
            if (prev != null) {
                val gap = w.startMs - prev.endMs
                val chars = cur.sumOf { it.text.length + 1 } + w.text.length
                if (gap >= pauseBreakMs || cur.size >= wordsPerCaption || chars > maxChars || w.endMs - cur.first().startMs > maxSegmentMs) flush()
            }
            cur += w
            val next = words.getOrNull(i + 1)
            if (sentenceEnd.containsMatchIn(w.text)) flush()
            else if (clauseEnd.containsMatchIn(w.text) && cur.size >= (wordsPerCaption + 1) / 2 && next != null) flush()
        }
        flush()
        // Extend each segment slightly to bridge tiny gaps (reduces flicker) without overlapping.
        return out.mapIndexed { i, s ->
            val nextStart = out.getOrNull(i + 1)?.startMs ?: (s.endMs + 400)
            s.copy(endMs = minOf(s.endMs + 250, nextStart))
        }
    }

    /**
     * Applies an edited text to a segment. If the word count is unchanged the original word
     * timings are kept; otherwise timings are redistributed proportionally to word length within
     * the segment's time range.
     */
    fun editSegment(segment: CaptionSegment, newText: String): CaptionSegment {
        val tokens = newText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return segment.copy(words = emptyList(), manuallyEdited = true)
        if (tokens.size == segment.words.size) {
            return segment.copy(words = segment.words.zip(tokens) { w, t -> w.copy(text = t) }, manuallyEdited = true)
        }
        val span = (segment.endMs - segment.startMs).coerceAtLeast(tokens.size * 120L)
        val weights = tokens.map { it.length.coerceAtLeast(2).toDouble() }
        val total = weights.sum()
        var t = segment.startMs.toDouble()
        val words = tokens.mapIndexed { i, tok ->
            val d = span * weights[i] / total
            val w = CaptionWord(tok, t.toLong(), (t + d).toLong())
            t += d
            w
        }
        return segment.copy(words = words, manuallyEdited = true)
    }

    fun shiftSegment(segment: CaptionSegment, deltaMs: Long): CaptionSegment = segment.copy(
        startMs = (segment.startMs + deltaMs).coerceAtLeast(0),
        endMs = (segment.endMs + deltaMs).coerceAtLeast(1),
        words = segment.words.map { it.copy(startMs = (it.startMs + deltaMs).coerceAtLeast(0), endMs = (it.endMs + deltaMs).coerceAtLeast(1)) },
    )

    fun setSegmentRange(segment: CaptionSegment, startMs: Long, endMs: Long): CaptionSegment {
        val oldSpan = (segment.endMs - segment.startMs).coerceAtLeast(1).toDouble()
        val newSpan = (endMs - startMs).coerceAtLeast(100).toDouble()
        return segment.copy(
            startMs = startMs, endMs = startMs + newSpan.toLong(),
            words = segment.words.map { w ->
                val s = startMs + ((w.startMs - segment.startMs) / oldSpan * newSpan).toLong()
                val e = startMs + ((w.endMs - segment.startMs) / oldSpan * newSpan).toLong()
                w.copy(startMs = s, endMs = e.coerceAtLeast(s + 1))
            },
            manuallyEdited = true,
        )
    }

    /** Marks words that match highlight keywords (case-insensitive, punctuation-insensitive). */
    fun applyEmphasis(segments: List<CaptionSegment>, keywords: List<String>): List<CaptionSegment> {
        if (keywords.isEmpty()) return segments
        val keyTokens = keywords.flatMap { k -> k.split(Regex("\\s+")) }.map { normalize(it) }.filter { it.length >= 2 }.toSet()
        return segments.map { s -> s.copy(words = s.words.map { w -> w.copy(emphasized = w.emphasized || normalize(w.text) in keyTokens) }) }
    }

    fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    /**
     * Rough captions from the approved script when no transcription is available. Timings are
     * spread evenly across each section and are clearly marked as estimates in the UI.
     */
    fun fromScript(sectionScripts: List<Triple<Long, Long, String>>): List<CaptionWord> =
        sectionScripts.flatMap { (start, dur, text) ->
            val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (tokens.isEmpty()) return@flatMap emptyList()
            val usable = (dur * 0.92).toLong()
            val each = usable / tokens.size
            tokens.mapIndexed { i, t -> CaptionWord(t, start + 150 + i * each, start + 150 + (i + 1) * each - 20) }
        }
}

/** Replaces common misrecognitions with the user's preferred terminology. */
object TerminologyCorrector {
    data class Correction(val from: String, val to: String)

    fun correct(words: List<CaptionWord>, terms: List<TerminologyEntry>, language: ReelLanguage): Pair<List<CaptionWord>, List<Correction>> {
        if (terms.isEmpty() || words.isEmpty()) return words to emptyList()
        val corrections = mutableListOf<Correction>()
        val result = words.toMutableList()
        // Build (variant tokens -> preferred spelling)
        val variants = terms.flatMap { t ->
            val preferred = if (language == ReelLanguage.IRAQI_ARABIC && t.arabic.isNotBlank()) t.arabic else t.english
            (t.misrecognitions + listOf(t.english, t.arabic)).filter { it.isNotBlank() }.map { v ->
                v.split(Regex("\\s+")).map { CaptionBuilder.normalize(it) }.filter { it.isNotEmpty() } to preferred
            }
        }.filter { it.first.isNotEmpty() }.sortedByDescending { it.first.size }

        var i = 0
        while (i < result.size) {
            var matched = false
            for ((tokens, preferred) in variants) {
                if (i + tokens.size > result.size) continue
                val window = result.subList(i, i + tokens.size).map { CaptionBuilder.normalize(it.text) }
                if (window == tokens) {
                    val original = result.subList(i, i + tokens.size).joinToString(" ") { it.text }
                    val trailing = result[i + tokens.size - 1].text.takeLastWhile { !it.isLetterOrDigit() }
                    val replacement = preferred + trailing
                    if (original != replacement) {
                        val merged = CaptionWord(replacement, result[i].startMs, result[i + tokens.size - 1].endMs, result[i].confidence, result[i].emphasized)
                        repeat(tokens.size) { result.removeAt(i) }
                        result.add(i, merged)
                        corrections += Correction(original, replacement)
                    }
                    matched = true
                    break
                }
            }
            i++
            if (!matched) continue
        }
        return result to corrections
    }
}

object SrtWriter {
    fun timestamp(ms: Long): String {
        val h = ms / 3_600_000; val m = (ms / 60_000) % 60; val s = (ms / 1000) % 60; val milli = ms % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    fun write(segments: List<CaptionSegment>, offsetMs: Long = 0): String = buildString {
        segments.filter { it.words.isNotEmpty() }.forEachIndexed { i, s ->
            appendLine(i + 1)
            appendLine("${timestamp((s.startMs + offsetMs).coerceAtLeast(0))} --> ${timestamp((s.endMs + offsetMs).coerceAtLeast(0))}")
            appendLine(s.text)
            appendLine()
        }
    }
}
