package app.reelify.integration.openai

import app.reelify.core.content.DialectChecker
import app.reelify.core.content.FactualContentBrief
import app.reelify.core.content.GeneratedContentNormalizer
import app.reelify.core.content.IraqiStyleGuide
import app.reelify.core.content.MedicalGlossary
import app.reelify.core.content.TextField
import app.reelify.core.model.ClaimSeverity
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ShotType
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.policy.ClaimChecker
import app.reelify.core.template.MediaRequirement
import app.reelify.core.template.ReelTemplate
import app.reelify.core.template.ShotRole
import app.reelify.core.timing.TimingEstimator

/** Errors trigger validation/targeted revision; notes are shown to the creator. */
data class ScriptCheck(
    val errors: List<String>,
    val notes: List<String>,
    val targetSectionsForRevision: Set<Int> = emptySet(),
) {
    val ok: Boolean get() = errors.isEmpty()
}

/**
 * Multi-layered validation for Reelify 2.2 scripts:
 *  1. Structural integrity & narrative outline
 *  2. Language & canonical English medical terminology policy
 *  3. Iraqi dialect authenticity heuristics (ar-IQ)
 *  4. Shot-aware word budget & hook duration parity with [TimingEstimator]
 *  5. Cross-section anti-repetition (conceptual overlap, repeated caveats, repeated device intros)
 *  6. Meaningful educational payoff & clinical grounding (preventing unrelated indication bleed)
 *  7. Formatting artifacts & local medical-claim safety rules
 */
object ScriptValidator {

    private val stopwords = setOf(
        // English common stopwords
        "the", "and", "that", "this", "for", "with", "you", "your", "from", "about", "what",
        "why", "how", "when", "into", "over", "more", "some", "each", "most", "very", "just",
        "only", "than", "here", "there", "are", "can", "may", "will", "not", "all",
        // Arabic / Iraqi common grammatical words (so conceptual similarity focuses on actual content)
        "على", "من", "في", "الى", "إلى", "عن", "مع", "هذا", "هذه", "هاي", "اللي", "التي",
        "الذي", "شنو", "شلون", "ليش", "بس", "كل", "مو", "أكو", "اكو", "ماكو", "يعني", "حتى",
        "قبل", "بعد", "بين", "عبر", "ويا", "وياكم", "إلكم", "الكم", "انتو", "نحن", "هسه", "كلش",
    )

    private val variabilityOrConsultClauses = listOf(
        Regex("individual assessment|book a consultation|book an assessment|consult (?:a |your )?(?:doctor|specialist|clinician)|in-person evaluation|depends on (?:the )?individual|results vary", RegexOption.IGNORE_CASE),
        Regex("تقييم الطبيب|احجزوا? تقييم|احجزوا? استشارة|استشارة الطبيب|فحص الطبيب|تختلف من شخص|يختلف من شخص|تختلف حسب (?:كل )?حالة|حسب حالة كل بشرة"),
    )

    private val unrelatedTopicSignals: Map<String, Regex> = mapOf(
        "hyperhidrosis" to Regex("\\bhyperhidrosis\\b|sweat glands?|axillary|تعرق|التعرق|الإبط", RegexOption.IGNORE_CASE),
        "body_contouring" to Regex("\\bbody contouring\\b|\\badipose\\b|\\bsubcutaneous fat\\b|\\b6 mm\\b|\\b9 mm\\b|\\b13 mm\\b|نحت الجسم|دهون البطن", RegexOption.IGNORE_CASE),
        "tattoos_pmu" to Regex("\\btattoo\\b|\\bpmu\\b|تاتو|الوشم|حبر الوشم", RegexOption.IGNORE_CASE),
        "scars_resurfacing" to Regex("\\bacne scars?\\b|\\batrophic scars?\\b|ندبات الحبوب|الندب", RegexOption.IGNORE_CASE),
        "benign_lesions" to Regex("\\bblesions?\\b|\\bseborrheic\\b|\\bxanthelasma\\b|شامة|زوائد جلدية", RegexOption.IGNORE_CASE),
    )

    fun wordBudget(timing: TimingEstimator, language: ReelLanguage, sectionMs: Long): Int =
        timing.estimate("", language, sectionMs).recommendedMaxWords.coerceAtLeast(8)

    fun words(text: String): Set<String> = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 }.toSet()

    fun contentTokens(text: String, excludeTerms: Collection<String> = emptyList()): Set<String> {
        val excluded = excludeTerms.flatMap { it.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) }.toSet()
        return text.lowercase()
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .map { tok ->
                // Strip single-char Arabic clitic prefixes for overlap comparison
                if (tok.length >= 4 && tok.first() in listOf('و', 'ب', 'ل', 'ف') && tok.any { it in '\u0600'..'\u06FF' }) tok.drop(1) else tok
            }
            .filter { it.length >= 3 && it !in stopwords && it !in excluded }
            .toSet()
    }

    fun contentSimilarity(a: String, b: String, excludeTerms: Collection<String> = emptyList()): Float {
        val x = contentTokens(a, excludeTerms)
        val y = contentTokens(b, excludeTerms)
        if (x.isEmpty() || y.isEmpty()) return 0f
        return (x intersect y).size.toFloat() / (x union y).size
    }

    fun trigrams(text: String): Set<String> {
        val toks = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 2 }
        if (toks.size < 3) return emptySet()
        return (0..toks.size - 3).map { "${toks[it]} ${toks[it + 1]} ${toks[it + 2]}" }.toSet()
    }

    /** Jaccard similarity of word sets — used to reject near-duplicate hooks. */
    fun similarity(a: String, b: String): Float {
        val x = words(a); val y = words(b)
        if (x.isEmpty() || y.isEmpty()) return 0f
        return (x intersect y).size.toFloat() / (x union y).size
    }

    fun hookErrors(
        hooks: List<HookDto>,
        language: ReelLanguage,
        expected: Int = 3,
        timing: TimingEstimator = TimingEstimator(),
        terminology: List<TerminologyEntry> = emptyList(),
    ): List<String> = buildList {
        if (hooks.size != expected) add("hook_options must contain exactly $expected hooks (got ${hooks.size})")
        if (hooks.map { it.style }.distinct().size < hooks.size.coerceAtMost(3)) add("each hook must use a different style")
        hooks.forEachIndexed { i, h ->
            if (h.text.isBlank()) add("hook ${i + 1} is empty")
            if (!PlanValidator.languageMatches(h.text, language)) add("hook ${i + 1} must be written in ${language.displayName}")
            val medErrs = MedicalGlossary.validate(h.text, terminology)
            if (medErrs.isNotEmpty()) add("hook ${i + 1}: ${medErrs.first()}")
            val wc = timing.countWords(h.text)
            val estMs = timing.estimatedDurationMs(h.text, language)
            if (wc > 16 || estMs > 8200L) add("hook ${i + 1} is too long to speak in ~3 seconds ($wc words)")
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
        timing: TimingEstimator = TimingEstimator(),
        terminology: List<TerminologyEntry> = emptyList(),
        sectionMs: Long = 10_000L,
    ): List<String> = buildList {
        val where = "section ${s.index}"
        val shot = template.shots.firstOrNull { it.index == s.index }
        val shotType = if (shot?.requiredMedia == MediaRequirement.VOICEOVER_BROLL) ShotType.VOICEOVER_BROLL else ShotType.TALKING_HEAD
        val purpose = shot?.role?.purpose ?: app.reelify.core.model.SectionPurpose.EXPLANATION
        val shotBudget = timing.wordBudgetForShot(language, sectionMs, purpose, shotType)

        val spoken = if (hookText != null) "$hookText ${s.spokenScript}" else s.spokenScript
        if (s.spokenScript.isBlank()) add("$where: spoken_script is empty")
        if (!PlanValidator.languageMatches(s.spokenScript, language)) add("$where: spoken_script must be written in ${language.displayName}")
        if (s.onScreenHeadline.isNotBlank() && !PlanValidator.languageMatches(s.onScreenHeadline, language)) add("$where: on_screen_headline must be in ${language.displayName}")
        if (timing.countWords(s.onScreenHeadline) > 7) add("$where: on_screen_headline must be at most 5 words")

        // Medical terminology check (must remain English Latin characters across spoken + headline)
        val medSpokenErrs = MedicalGlossary.validate(s.spokenScript, terminology)
        if (medSpokenErrs.isNotEmpty()) add("$where: ${medSpokenErrs.first()}")
        val medHeadlineErrs = MedicalGlossary.validate(s.onScreenHeadline, terminology)
        if (medHeadlineErrs.isNotEmpty()) add("$where: headline ${medHeadlineErrs.first()}")

        val n = timing.countWords(spoken)
        val effectiveMax = maxOf((budget * 1.16f).toInt(), shotBudget.hardMaxWords)
        val effectiveMin = minOf(maxOf(6, (budget * 0.55f).toInt()), shotBudget.hardMinWords)
        if (n > effectiveMax) add("$where: too long to speak in time ($n words; budget about ${shotBudget.promptLabel()}${if (hookText != null) " including the hook" else ""})")
        if (n < effectiveMin) add("$where: too short for the shot duration ($n words; aim for ${shotBudget.promptLabel()})")
        else if (n < shotBudget.targetMinWords) notes += "Shot ${s.index}: concise ($n words vs ${shotBudget.promptLabel()}) — purposeful B-roll or a clarifying example fits nicely."

        if (Regex("\\[[^\\]]{2,}\\]|\\((camera|cut|b-roll|pause|music)[^)]*\\)", RegexOption.IGNORE_CASE).containsMatchIn(s.spokenScript)) {
            add("$where: remove stage directions from spoken_script")
        }
        val fillers = DialectChecker.genericFillerHits(s.spokenScript)
        if (fillers.isNotEmpty()) add("$where: remove generic promotional filler (${fillers.first()}) and state specific clinical facts.")

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

    /**
     * Evaluates narrative differentiation across Shots 1..3, educational payoff, treatment physics,
     * and clinical focus boundaries. Populates [targetedRevisions] with specific section indexes that
     * should be rewritten when targeted repair is needed.
     */
    fun validateCrossSectionCoherence(
        dto: ScriptDto,
        template: ReelTemplate,
        language: ReelLanguage,
        factualBrief: FactualContentBrief? = null,
        treatmentName: String? = null,
        targetedRevisions: MutableSet<Int> = mutableSetOf(),
    ): List<String> = buildList {
        val sorted = dto.sections.sortedBy { it.index }
        if (sorted.size < 3) return@buildList
        val s1 = sorted[0].spokenScript
        val s2 = sorted[1].spokenScript
        val s3 = sorted[2].spokenScript
        val exclude = listOfNotNull(treatmentName, factualBrief?.treatmentName)

        // 1. Cross-section conceptual similarity & repeated phrases
        val pairs = listOf(Triple(1, 2, s1 to s2), Triple(2, 3, s2 to s3), Triple(1, 3, s1 to s3))
        for ((i, j, txt) in pairs) {
            val sim = contentSimilarity(txt.first, txt.second, exclude)
            val sharedTrigrams = trigrams(txt.first) intersect trigrams(txt.second)
            if (sim > 0.45f || sharedTrigrams.size >= 2) {
                add("section $j: repeats content from section $i (overlap=${"%.0f".format(sim * 100)}%); each shot must advance a distinct point")
                targetedRevisions += j
            }
        }

        // 2. Repeated consultation / assessment / variability disclaimers across multiple shots
        val consultSections = sorted.filter { sec ->
            variabilityOrConsultClauses.any { re -> re.containsMatchIn(sec.spokenScript) }
        }.map { it.index }
        if (consultSections.size >= 2) {
            val offend = consultSections.last()
            add("section $offend: consultation/variability caveat is repeated across shots ${consultSections.joinToString(", ")}; keep it in at most one shot")
            targetedRevisions += offend
        }

        // 3. Repeated generic device intro in Shot 2 or Shot 3
        val introPattern = Regex("^(?:today we(?:'re| are) (?:looking at|talking about)|meet |نقدملكم|تعرفوا على|هذا جهاز )", RegexOption.IGNORE_CASE)
        if (introPattern.containsMatchIn(s2.trim())) {
            add("section 2: re-introduces the device instead of teaching the mechanism/evidence")
            targetedRevisions += 2
        }
        if (introPattern.containsMatchIn(s3.trim())) {
            add("section 3: re-introduces the device instead of delivering the practical payoff")
            targetedRevisions += 3
        }

        // 4. Meaningful educational payoff in Shot 3 (reject pure empty consultation closure)
        val s3Tokens = contentTokens(s3, exclude)
        val s3ConsultMatches = variabilityOrConsultClauses.sumOf { re -> re.findAll(s3).count() }
        val clinicalPayoffSignals = Regex(
            "(?:because|since|means|interval|weeks|months|sessions|layers?|epiderm|derm|smas|melanin|wavelength|nm|collagen|sunscreen|spf|pigment|scars?|ink|macrophage|lymph|ultrasound|" +
                "لأن|يعني|أسبوع|أسابيع|شهر|أشهر|جلسات|عمق|طبقة|واقي الشمس|تصبغ|حبر|صبغة|تراكمي|تدريجي|موجة)",
            RegexOption.IGNORE_CASE,
        )
        if (s3ConsultMatches > 0 && !clinicalPayoffSignals.containsMatchIn(s3) && s3Tokens.size < 14) {
            add("section 3: lacks a concrete educational payoff — do not end with only a generic consultation/variability statement")
            targetedRevisions += 3
        }

        // 5. Treatment identity / CFU HIFU laser guard
        val allText = listOf(dto.title, s1, s2, s3).plus(dto.hookOptions.map { it.text }).joinToString(" ")
        if (treatmentName?.contains("CFU", ignoreCase = true) == true) {
            val claimsLaser = Regex("\\bCFU\\b[^.?!]{0,40}\\blaser\\b|جهاز\\s+CFU[^.?!]{0,30}ليزر", RegexOption.IGNORE_CASE).containsMatchIn(allText) &&
                !allText.contains("not a laser", ignoreCase = true) && !allText.contains("مو ليزر") && !allText.contains("مو بالليزر")
            if (claimsLaser) {
                add("section 2: CFU Èlife is HIFU (focused ultrasound), never refer to it as a laser")
                targetedRevisions += 2
            }
        }

        // 6. Topic focus isolation (prevent pulling unrelated indications when brief excluded them)
        factualBrief?.excludedTopicAreas.orEmpty().forEach { excludedArea ->
            val signal = unrelatedTopicSignals[excludedArea]
            if (signal != null) {
                sorted.forEach { sec ->
                    if (signal.containsMatchIn(sec.spokenScript)) {
                        add("section ${sec.index}: introduces unrelated '$excludedArea' content outside the selected topic")
                        targetedRevisions += sec.index
                    }
                }
            }
        }

        // 7. Narrative outline consistency when present
        if (dto.narrativeOutline.openingQuestion.isNotBlank() && dto.narrativeOutline.viewerLearnedPayoff.isNotBlank()) {
            val outlinePayoffTokens = contentTokens(dto.narrativeOutline.viewerLearnedPayoff, exclude)
            if (outlinePayoffTokens.size >= 3 && variabilityOrConsultClauses.any { it.containsMatchIn(dto.narrativeOutline.viewerLearnedPayoff) } &&
                !clinicalPayoffSignals.containsMatchIn(dto.narrativeOutline.viewerLearnedPayoff)
            ) {
                add("section 3: narrative outline payoff is too generic; state the specific clinical rule the viewer learned")
                targetedRevisions += 3
            }
        }

        // Check title terminology
        MedicalGlossary.validate(dto.title).firstOrNull()?.let { add("title: $it") }
    }

    fun validateScript(
        dto: ScriptDto,
        template: ReelTemplate,
        language: ReelLanguage,
        budget: Int,
        forbidden: List<String>,
        timing: TimingEstimator = TimingEstimator(),
        factualBrief: FactualContentBrief? = null,
        terminology: List<TerminologyEntry> = emptyList(),
        treatmentName: String? = null,
        sectionMs: Long = 10_000L,
    ): ScriptCheck {
        val errors = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val targetRev = mutableSetOf<Int>()
        if (dto.language != language.code) errors += "language must be \"${language.code}\""
        if (dto.sections.size != 3) errors += "sections must contain exactly 3 items (got ${dto.sections.size})"
        if (dto.sections.map { it.index }.sorted() != listOf(1, 2, 3)) errors += "section indexes must be exactly 1, 2, 3"
        errors += hookErrors(dto.hookOptions, language, timing = timing, terminology = terminology)
        if (dto.ctaStyle !in IraqiStyleGuide.ctaStyles) errors += "invalid cta_style"
        val hook = dto.hookOptions.firstOrNull()?.text
        dto.sections.forEach { s ->
            val secErrs = sectionErrors(
                s = s,
                hookText = if (s.index == 1) hook else null,
                template = template,
                language = language,
                budget = budget,
                forbidden = forbidden,
                notes = notes,
                timing = timing,
                terminology = terminology,
                sectionMs = sectionMs,
            )
            if (secErrs.isNotEmpty()) {
                errors += secErrs
                targetRev += s.index
            }
        }

        errors += validateCrossSectionCoherence(
            dto = dto,
            template = template,
            language = language,
            factualBrief = factualBrief,
            treatmentName = treatmentName,
            targetedRevisions = targetRev,
        )

        val combinedSpoken = dto.sections.joinToString(" ") { it.spokenScript }
        val totalFillers = DialectChecker.genericFillerHits(combinedSpoken)
        if (totalFillers.isNotEmpty()) {
            errors += "script uses promotional clichés (${totalFillers.first()}); teach concrete treatment specifics instead."
        }
        val consultCount = DialectChecker.excessiveConsultationHits(combinedSpoken)
        if (consultCount >= 2) {
            errors += "consultation/assessment reminder appears $consultCount times; mention individual assessment naturally at most once."
        }
        return ScriptCheck(errors.distinct(), notes.distinct(), targetRev.toSet())
    }

    fun validateRewrite(
        dto: RewriteDto,
        targets: Set<Int>,
        hook: String?,
        template: ReelTemplate,
        language: ReelLanguage,
        budget: Int,
        forbidden: List<String>,
        timing: TimingEstimator = TimingEstimator(),
        terminology: List<TerminologyEntry> = emptyList(),
        sectionMs: Long = 10_000L,
        existingOtherSections: List<ScriptSectionDto> = emptyList(),
        treatmentName: String? = null,
    ): ScriptCheck {
        val errors = mutableListOf<String>()
        val notes = mutableListOf<String>()
        val got = dto.sections.map { it.index }.toSet()
        if (got != targets) errors += "return exactly the target sections ${targets.sorted()} (got ${got.sorted()})"
        dto.sections.filter { it.index in targets }.forEach { s ->
            errors += sectionErrors(
                s = s,
                hookText = if (s.index == 1) hook else null,
                template = template,
                language = language,
                budget = budget,
                forbidden = forbidden,
                notes = notes,
                timing = timing,
                terminology = terminology,
                sectionMs = sectionMs,
            )
        }
        // Check rewrite against untouched sections so it does not reintroduce repetition
        if (existingOtherSections.isNotEmpty()) {
            val exclude = listOfNotNull(treatmentName)
            dto.sections.filter { it.index in targets }.forEach { rewritten ->
                existingOtherSections.filter { it.index != rewritten.index }.forEach { other ->
                    val sim = contentSimilarity(rewritten.spokenScript, other.spokenScript, exclude)
                    if (sim > 0.45f) {
                        errors += "section ${rewritten.index}: repeats section ${other.index}; use a distinct point"
                    }
                }
            }
        }
        return ScriptCheck(errors.distinct(), notes.distinct())
    }

    /** Normalizes every text field of a script DTO in place (before validation and storage). */
    fun normalize(dto: ScriptDto, protectedTerms: Collection<String>): ScriptDto = dto.copy(
        title = GeneratedContentNormalizer.clean(dto.title, TextField.TITLE, protectedTerms),
        creativeAngle = GeneratedContentNormalizer.clean(dto.creativeAngle, TextField.NOTE, protectedTerms),
        narrativeOutline = dto.narrativeOutline.copy(
            openingQuestion = GeneratedContentNormalizer.clean(dto.narrativeOutline.openingQuestion, TextField.NOTE, protectedTerms),
            centralInsight = GeneratedContentNormalizer.clean(dto.narrativeOutline.centralInsight, TextField.NOTE, protectedTerms),
            explanatoryAnalogy = GeneratedContentNormalizer.clean(dto.narrativeOutline.explanatoryAnalogy, TextField.NOTE, protectedTerms),
            viewerLearnedPayoff = GeneratedContentNormalizer.clean(dto.narrativeOutline.viewerLearnedPayoff, TextField.NOTE, protectedTerms),
        ),
        hookOptions = dto.hookOptions.map { it.copy(text = GeneratedContentNormalizer.clean(it.text, TextField.HOOK, protectedTerms)) },
        sections = dto.sections.map { normalize(it, protectedTerms) },
        medicalReview = dto.medicalReview.map { it.copy(claim = GeneratedContentNormalizer.clean(it.claim, TextField.NOTE, protectedTerms), suggestion = GeneratedContentNormalizer.clean(it.suggestion, TextField.NOTE, protectedTerms)) },
    ).let { d ->
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
