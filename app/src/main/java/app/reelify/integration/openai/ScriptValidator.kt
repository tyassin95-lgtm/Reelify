package app.reelify.integration.openai

import app.reelify.core.content.DialectChecker
import app.reelify.core.content.GeneratedContentNormalizer
import app.reelify.core.content.IraqiStyleGuide
import app.reelify.core.content.TextField
import app.reelify.core.model.ClaimSeverity
import app.reelify.core.model.ReelLanguage
import app.reelify.core.policy.ClaimChecker
import app.reelify.core.template.ReelTemplate
import app.reelify.core.template.ShotRole
import app.reelify.core.timing.TimingEstimator

/** Errors are sent back to the model for one repair round; notes are shown to the creator. */
data class ScriptCheck(val errors: List<String>, val notes: List<String>) {
    val ok: Boolean get() = errors.isEmpty()
}

/**
 * Layered validation for AI scripts:
 *  1. structure  2. language/script  3. dialect heuristics (ar-IQ)  4. word budget / speaking time
 *  5. hook diversity  6. formatting artifacts  7. local medical-claim rules (+ profile's forbidden claims)
 * None of these proves dialect authenticity; they only catch clear problems.
 */
object ScriptValidator {

    fun wordBudget(timing: TimingEstimator, language: ReelLanguage, sectionMs: Long): Int =
        timing.estimate("", language, sectionMs).recommendedMaxWords.coerceAtLeast(8)

    fun words(text: String): Set<String> = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.toSet()

    /** Jaccard similarity of word sets — used to reject near-duplicate hooks. */
    fun similarity(a: String, b: String): Float {
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0f
        return (x intersect y).size.toFloat() / (x union y).size
    }

    fun hookErrors(hooks: List<HookDto>, language: ReelLanguage, expected: Int = 3): List<String> = buildList {
        if (hooks.size != expected) add("hook_options must contain exactly $expected hooks (got ${hooks.size})")
        if (hooks.map { it.style }.distinct().size < hooks.size.coerceAtMost(3)) add("each hook must use a different style")
        hooks.forEachIndexed { i, h ->
            if (h.text.isBlank()) add("hook ${i + 1} is empty")
            if (!PlanValidator.languageMatches(h.text, language)) add("hook ${i + 1} must be written in ${language.displayName}")
            if (TimingEstimator().countWords(h.text) > 18) add("hook ${i + 1} is too long to say in ~3 seconds")
        }
        for (i in hooks.indices) for (j in i + 1 until hooks.size) {
            if (similarity(hooks[i].text, hooks[j].text) > 0.6f) add("hooks ${i + 1} and ${j + 1} are too similar — use genuinely different angles")
        }
    }

    fun sectionErrors(
        s: ScriptSectionDto,
        hookText: String?,
        template: ReelTemplate,
        language: ReelLanguage,
        budget: Int,
        forbidden: List<String>,
        notes: MutableList<String>,
    ): List<String> = buildList {
        val where = "section ${s.index}"
        val shot = template.shots.firstOrNull { it.index == s.index }
        val spoken = if (hookText != null) "$hookText ${s.spokenScript}" else s.spokenScript
        if (s.spokenScript.isBlank()) add("$where: spoken_script is empty")
        if (!PlanValidator.languageMatches(s.spokenScript, language)) add("$where: spoken_script must be written in ${language.displayName}")
        if (s.onScreenHeadline.isNotBlank() && !PlanValidator.languageMatches(s.onScreenHeadline, language)) add("$where: on_screen_headline must be in ${language.displayName}")
        if (TimingEstimator().countWords(s.onScreenHeadline) > 7) add("$where: on_screen_headline must be at most 5 words")
        val n = TimingEstimator().countWords(spoken)
        if (n > (budget * 1.15f).toInt()) add("$where: too long to speak in time ($n words; budget about $budget words${if (hookText != null) " including the hook" else ""})")
        if (n < maxOf(4, (budget * 0.35f).toInt())) add("$where: too short ($n words; aim for about ${(budget * 0.7f).toInt()} words)")
        if (Regex("\\[[^\\]]{2,}\\]|\\((camera|cut|b-roll|pause|music)[^)]*\\)", RegexOption.IGNORE_CASE).containsMatchIn(s.spokenScript)) add("$where: remove stage directions from spoken_script")
        if (language == ReelLanguage.IRAQI_ARABIC) {
            val report = DialectChecker.analyze(s.spokenScript)
            if (report.likelyFormal) add("$where: sounds like formal MSA (${report.formalHits.take(4).joinToString("، ")}). Rewrite in natural spoken Iraqi.")
            notes += report.notes().map { "Shot ${s.index}: $it" }
        } else {
            DialectChecker.overused(s.spokenScript).takeIf { it.isNotEmpty() }?.let { notes += "Shot ${s.index}: overused phrasing — ${it.joinToString()}" }
        }
        val flags = ClaimChecker.check(spoken + "\n" + s.onScreenHeadline, null, forbidden)
        val mythShot = shot?.role == ShotRole.MYTH
        flags.forEach { f ->
            if (f.severity == ClaimSeverity.BLOCKER && !mythShot) add("$where: unsafe claim “${f.claim}” — ${f.issue} ${f.suggestion}")
            else notes += "Shot ${s.index}: check “${f.claim}” — ${f.issue}"
        }
        if (GeneratedContentNormalizer.hasArtifacts(s.spokenScript)) notes += "Shot ${s.index}: formatting artifacts were cleaned"
    }

    fun validateScript(
        dto: ScriptDto,
        template: ReelTemplate,
        language: ReelLanguage,
        budget: Int,
        forbidden: List<String>,
    ): ScriptCheck {
        val errors = mutableListOf<String>()
        val notes = mutableListOf<String>()
        if (dto.language != language.code) errors += "language must be \"${language.code}\""
        if (dto.sections.size != 3) errors += "sections must contain exactly 3 items (got ${dto.sections.size})"
        if (dto.sections.map { it.index }.sorted() != listOf(1, 2, 3)) errors += "section indexes must be exactly 1, 2, 3"
        errors += hookErrors(dto.hookOptions, language)
        if (dto.ctaStyle !in IraqiStyleGuide.ctaStyles) errors += "invalid cta_style"
        val hook = dto.hookOptions.firstOrNull()?.text
        dto.sections.forEach { s -> errors += sectionErrors(s, if (s.index == 1) hook else null, template, language, budget, forbidden, notes) }
        return ScriptCheck(errors.distinct(), notes.distinct())
    }

    fun validateRewrite(dto: RewriteDto, targets: Set<Int>, hook: String?, template: ReelTemplate, language: ReelLanguage, budget: Int, forbidden: List<String>): ScriptCheck {
        val errors = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val got = dto.sections.map { it.index }.toSet()
        if (got != targets) errors += "return exactly the target sections ${targets.sorted()} (got ${got.sorted()})"
        dto.sections.filter { it.index in targets }.forEach { s -> errors += sectionErrors(s, if (s.index == 1) hook else null, template, language, budget, forbidden, notes) }
        return ScriptCheck(errors.distinct(), notes.distinct())
    }

    /** Normalizes every text field of a script DTO in place (before validation and storage). */
    fun normalize(dto: ScriptDto, protectedTerms: Collection<String>): ScriptDto = dto.copy(
        title = GeneratedContentNormalizer.clean(dto.title, TextField.TITLE, protectedTerms),
        creativeAngle = GeneratedContentNormalizer.clean(dto.creativeAngle, TextField.NOTE, protectedTerms),
        hookOptions = dto.hookOptions.map { it.copy(text = GeneratedContentNormalizer.clean(it.text, TextField.HOOK, protectedTerms)) },
        sections = dto.sections.map { normalize(it, protectedTerms) },
        medicalReview = dto.medicalReview.map { it.copy(claim = GeneratedContentNormalizer.clean(it.claim, TextField.NOTE), suggestion = GeneratedContentNormalizer.clean(it.suggestion, TextField.NOTE)) },
    ).let { d ->
        // The app places the hook in front of section 1; drop it if the model repeated it anyway.
        val hook = d.hookOptions.firstOrNull()?.text.orEmpty()
        d.copy(sections = d.sections.map { s -> if (s.index == 1 && hook.isNotBlank()) s.copy(spokenScript = stripLeadingHook(s.spokenScript, hook)) else s })
    }

    fun normalize(s: ScriptSectionDto, protectedTerms: Collection<String>): ScriptSectionDto = s.copy(
        spokenScript = GeneratedContentNormalizer.clean(s.spokenScript, TextField.SPOKEN, protectedTerms),
        onScreenHeadline = GeneratedContentNormalizer.clean(s.onScreenHeadline, TextField.HEADLINE, protectedTerms),
        emphasisWords = s.emphasisWords.map { GeneratedContentNormalizer.clean(it, TextField.HEADLINE, protectedTerms) }.filter { it.isNotBlank() },
    )

    fun stripLeadingHook(body: String, hook: String): String {
        val b = body.trim(); val h = hook.trim()
        if (h.isEmpty()) return b
        return if (b.startsWith(h)) b.removePrefix(h).trimStart(' ', '\n', '،', ',', '.', '-', '—').trim() else b
    }

    /** Places a hook in front of section 1's body (the stored spoken script is hook + body). */
    fun compose(hook: String, body: String): String = listOf(hook.trim(), body.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    /** Replaces the current hook at the start of section 1 with [newHook]; prepends if the text was edited. */
    fun swapHook(spoken: String, oldHook: String?, newHook: String): String {
        val body = if (!oldHook.isNullOrBlank() && spoken.trim().startsWith(oldHook.trim())) stripLeadingHook(spoken, oldHook) else spoken
        return compose(newHook, body)
    }
}
