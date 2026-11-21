package app.reelify.core.content

import java.nio.charset.Charset
import java.text.Normalizer

/** What kind of field a string is; display-only fields get Markdown decoration removed. */
enum class TextField { SPOKEN, HEADLINE, HOOK, TITLE, NOTE }

data class NormalizedText(val text: String, val issues: List<String>) {
    val changed: Boolean get() = issues.isNotEmpty()
}

/**
 * Conservative clean-up for AI-generated (and pasted) text before it is stored, displayed,
 * spoken from the teleprompter or burned into captions.
 *
 * Rules (deliberately narrow — see docs/REELIFY_V2_DESIGN.md):
 *  - NFC normalization (not NFKC). NFKC is applied ONLY to Arabic presentation-form code points
 *    (U+FB50–U+FDFF, U+FE70–U+FEFC), which are pre-shaped glyphs that break text shaping;
 *    religious ligatures (U+FDF2, U+FDFA–U+FDFD) are left untouched.
 *  - Removes BOM/ZWNBSP, zero-width spaces, soft hyphens, C0/C1 control characters, U+FFFD
 *    replacement characters and directional OVERRIDES (LRO/RLO), which visually reverse text.
 *  - Keeps ZWJ/ZWNJ (meaningful for Arabic joining and emoji), LRM/RLM/ALM marks, and balanced
 *    bidi embeddings/isolates. Unbalanced embeddings/isolates are removed.
 *  - Never reverses Arabic, never removes Arabic letters, diacritics (harakat) or tatweel.
 *  - Decodes accidentally double-escaped sequences (\n, \", \uXXXX) and repairs UTF-8 text that
 *    was mis-decoded as Latin-1 ("mojibake") when the repair is unambiguous.
 *  - Strips Markdown decoration (bold/italic markers, headings, bullets, code ticks) and
 *    wrapping quotes from display-only fields; keeps intentional newlines.
 *  - Restores the exact preferred spelling of protected terms (device/brand names).
 */
object GeneratedContentNormalizer {

    private val removeAlways = setOf(
        '﻿', // BOM / ZWNBSP
        '​', // zero-width space
        '­', // soft hyphen
        '᠎', // Mongolian vowel separator (legacy zero-width)
        '￾', '￿',
        '‭', '‮', // LRO, RLO: overrides reverse visual order
    )
    private val embedOpen = setOf('‪', '‫') // LRE, RLE
    private const val PDF = '‬'
    private val isolateOpen = setOf('⁦', '⁧', '⁨') // LRI, RLI, FSI
    private const val PDI = '⁩'
    private val keepReligiousLigatures = setOf('ﷲ', 'ﷺ', 'ﷻ', '﷼', '﷽')

    private val escapedUnicode = Regex("""\\u([0-9a-fA-F]{4})""")
    private val mojibakeHint = Regex("[ØÙÚÛ][\u0080-¿ŒœŠšŸŽžƒˆ˜–-™]")

    fun normalize(raw: String, field: TextField = TextField.SPOKEN, protectedTerms: Collection<String> = emptyList()): NormalizedText {
        val issues = mutableListOf<String>()
        var s = raw

        // 1. Malformed escapes (the model sometimes double-escapes inside JSON strings).
        if (s.contains("\\n") || s.contains("\\\"") || s.contains("\\t") || escapedUnicode.containsMatchIn(s)) {
            val before = s
            s = escapedUnicode.replace(s) { m -> m.groupValues[1].toInt(16).toChar().toString() }
                .replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", " ").replace("\\\"", "\"")
            if (s != before) issues += "decoded escaped characters"
        }

        // 2. Mojibake (UTF-8 bytes shown as Latin-1/Windows-1252), e.g. "Ø§Ù„" for "ال".
        if (mojibakeHint.findAll(s).count() >= 2) {
            repairMojibake(s)?.let { fixed -> s = fixed; issues += "repaired mis-decoded text" }
        }

        // 3. Line breaks and control characters.
        s = s.replace("\r\n", "\n").replace('\r', '\n').replace(' ', '\n').replace(' ', '\n')
        val sb = StringBuilder(s.length)
        var removedControl = false
        var removedReplacement = false
        for (ch in s) {
            when {
                ch == '\n' -> sb.append(ch)
                ch == '\t' -> sb.append(' ')
                ch == '�' -> removedReplacement = true
                ch in removeAlways -> removedControl = true
                ch.code < 0x20 || ch.code in 0x7F..0x9F -> removedControl = true
                ch == ' ' || ch == ' ' || ch == ' ' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        s = sb.toString()
        if (removedReplacement) issues += "removed replacement characters (�)"
        if (removedControl) issues += "removed invisible control characters"

        // 4. Bidi: drop unbalanced embeddings / isolates, keep balanced ones and marks.
        val balanced = balanceBidi(s)
        if (balanced != s) { s = balanced; issues += "removed unbalanced direction controls" }

        // 5. Arabic presentation forms → standard letters (targeted compatibility mapping).
        if (s.any { isPresentationForm(it) }) {
            s = buildString { for (ch in s) append(if (isPresentationForm(ch)) Normalizer.normalize(ch.toString(), Normalizer.Form.NFKC) else ch.toString()) }
            issues += "converted Arabic presentation forms"
        }

        // 6. Canonical composition.
        val nfc = Normalizer.normalize(s, Normalizer.Form.NFC)
        if (nfc != s) s = nfc

        // 7. Markdown decoration.
        val md = stripMarkdown(s, field)
        if (md != s) { s = md; issues += "removed formatting symbols" }

        // 8. Whitespace & punctuation spacing.
        s = s.lines().joinToString("\n") { line -> line.replace(Regex("[ ]{2,}"), " ").trim() }
            .replace(Regex("\n{3,}"), "\n\n")
            .replace(Regex(" +([،؛؟!?.,:;])"), "$1")
            .trim()
        if (field != TextField.SPOKEN && field != TextField.NOTE) s = s.replace('\n', ' ').replace(Regex(" {2,}"), " ")

        // 9. Wrapping quotes on display-only fields.
        if (field == TextField.HEADLINE || field == TextField.HOOK || field == TextField.TITLE) {
            val unq = unwrapQuotes(s)
            if (unq != s) s = unq
        }

        // 10. Protected spellings (Latin device/brand names only; whole-word, case-insensitive).
        for (term in protectedTerms.filter { it.isNotBlank() && it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } }.sortedByDescending { it.length }) {
            // Latin boundaries only, so Arabic prefixes attached to the name ("وlasemd") still match.
            val re = Regex("(?<![A-Za-z0-9])" + Regex.escape(term) + "(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
            s = re.replace(s) { term }
        }
        return NormalizedText(s, issues.distinct())
    }

    /**
     * Minimal, non-destructive display filter for text already stored (user-edited or legacy):
     * drops only characters that can never render meaningfully (U+FFFD, BOM, zero-width space,
     * C0/C1 controls, directional overrides). Use [normalize] for new AI output.
     */
    fun displaySafe(text: String): String {
        if (text.none { it == '\uFFFD' || it in removeAlways || (it.code < 0x20 && it != '\n') || it.code in 0x7F..0x9F }) return text
        return buildString(text.length) {
            for (ch in text) if (!(ch == '\uFFFD' || ch in removeAlways || (ch.code < 0x20 && ch != '\n') || ch.code in 0x7F..0x9F)) append(ch)
        }
    }

    /** Arabic-script letters vs Latin letters. Paragraph direction must follow the DOMINANT script, not the first letter. */
    fun isArabicDominant(text: String): Boolean {
        var ar = 0; var la = 0
        for (ch in text) {
            if (ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F' || ch in '\uFB50'..'\uFDFF' || ch in '\uFE70'..'\uFEFC') ar++
            else if (ch in 'a'..'z' || ch in 'A'..'Z') la++
        }
        return ar > 0 && ar >= la
    }

    fun containsArabic(text: String): Boolean = text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }

    fun clean(raw: String, field: TextField = TextField.SPOKEN, protectedTerms: Collection<String> = emptyList()): String =
        normalize(raw, field, protectedTerms).text

    /** True when the text still contains characters we never want to show or render. */
    fun hasArtifacts(text: String): Boolean = text.any { it == '�' || it in removeAlways || (it.code < 0x20 && it != '\n') || isPresentationForm(it) } ||
        text.contains("**") || text.contains("\\n") || mojibakeHint.findAll(text).count() >= 2

    private fun isPresentationForm(ch: Char): Boolean =
        (ch in 'ﭐ'..'﷿' || ch in 'ﹰ'..'ﻼ') && ch !in keepReligiousLigatures

    /** Windows-1252 code points for bytes 0x80..0x9F (Latin-1 elsewhere). */
    private val cp1252Reverse: Map<Char, Int> by lazy {
        val cs = Charset.forName("windows-1252")
        (0x80..0x9F).mapNotNull { b -> String(byteArrayOf(b.toByte()), cs).singleOrNull()?.takeIf { it != '\uFFFD' }?.let { it to b } }.toMap()
    }

    private fun repairMojibake(s: String): String? = runCatching {
        val out = java.io.ByteArrayOutputStream(s.length)
        for (ch in s) {
            val b = when {
                ch.code <= 0xFF -> ch.code
                else -> cp1252Reverse[ch] ?: return null // not mojibake-only text: leave untouched
            }
            out.write(b)
        }
        val decoded = String(out.toByteArray(), Charsets.UTF_8)
        if (decoded.contains('�')) return null
        val arabic = decoded.count { it in '؀'..'ۿ' }
        if (arabic == 0) null else decoded
    }.getOrNull()

    private fun balanceBidi(s: String): String {
        if (s.none { it in embedOpen || it == PDF || it in isolateOpen || it == PDI }) return s
        // Mark which control characters have a matching partner; drop the rest.
        val keep = BooleanArray(s.length) { true }
        val embedStack = ArrayDeque<Int>()
        val isoStack = ArrayDeque<Int>()
        s.forEachIndexed { i, ch ->
            when {
                ch in embedOpen -> embedStack.addLast(i)
                ch == PDF -> if (embedStack.isNotEmpty()) embedStack.removeLast() else keep[i] = false
                ch in isolateOpen -> isoStack.addLast(i)
                ch == PDI -> if (isoStack.isNotEmpty()) isoStack.removeLast() else keep[i] = false
            }
        }
        embedStack.forEach { keep[it] = false }
        isoStack.forEach { keep[it] = false }
        return buildString { s.forEachIndexed { i, ch -> if (keep[i]) append(ch) } }
    }

    private fun stripMarkdown(s: String, field: TextField): String {
        var t = s
        t = t.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1").replace(Regex("__(.+?)__"), "$1")
        t = t.replace(Regex("(?<![\\p{L}\\p{N}*])\\*(?!\\s)([^*\n]+?)(?<!\\s)\\*(?![\\p{L}\\p{N}*])"), "$1")
        t = t.replace(Regex("`([^`\n]+)`"), "$1")
        t = t.lines().joinToString("\n") { line ->
            line.replace(Regex("^\\s{0,3}#{1,6}\\s+"), "")
                .replace(Regex("^\\s{0,3}>\\s?"), "")
                .replace(Regex("^\\s{0,3}[-*•]\\s+"), "")
        }
        // Stray leftover bold markers (lone "*" is left alone: it can be meaningful, e.g. "2 * 3").
        t = t.replace("**", "")
        if (field != TextField.SPOKEN && field != TextField.NOTE) t = t.replace(Regex("^\\s*\\d+[.)]\\s+(?=\\S)"), { m -> if (field == TextField.HEADLINE) m.value else "" })
        return t
    }

    private val quotePairs = listOf('"' to '"', '“' to '”', '«' to '»', '\'' to '\'', '‘' to '’', '„' to '“')
    private fun unwrapQuotes(s: String): String {
        var t = s
        repeat(2) {
            val pair = quotePairs.firstOrNull { (o, c) -> t.length >= 2 && t.first() == o && t.last() == c }
            if (pair != null) {
                val inner = t.substring(1, t.length - 1)
                // Only unwrap when the quotes wrap the whole string (no other quote of that kind inside).
                if (!inner.contains(pair.second)) t = inner.trim()
            }
        }
        return t
    }
}
