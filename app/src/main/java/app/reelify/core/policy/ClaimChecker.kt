package app.reelify.core.policy

import app.reelify.core.model.ClaimFlag
import app.reelify.core.model.ClaimSeverity

/**
 * Small, deterministic content-policy layer. Flags (never auto-edits) wording that commonly
 * produces misleading medical-aesthetic claims, in English and Arabic. The treatment profile's
 * own "claims not to make" list is checked as well.
 */
object ClaimChecker {
    private data class Rule(val pattern: Regex, val issue: String, val suggestion: String, val severity: ClaimSeverity)

    private fun r(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE))

    private val rules = listOf(
        Rule(r("\\bguarantee(d|s)?\\b|\\bguaranteed results?\\b"), "Guaranteed outcomes can't be promised.", "Say results vary and depend on assessment.", ClaimSeverity.BLOCKER),
        Rule(r("100\\s?%|١٠٠\\s?٪|100\\s?٪"), "Absolute effectiveness claims are unsupported.", "Use 'many patients' or 'may help'.", ClaimSeverity.BLOCKER),
        Rule(r("\\bpermanent(ly)?\\b|\\bforever\\b"), "Permanence claims are rarely supportable.", "Describe typical duration and maintenance.", ClaimSeverity.BLOCKER),
        Rule(r("\\bno risks?\\b|\\brisk[- ]free\\b|\\bzero risk\\b|\\bcompletely safe\\b|\\b100% safe\\b"), "Every procedure has risks.", "Mention that suitability is assessed individually.", ClaimSeverity.BLOCKER),
        Rule(r("\\bno side[- ]effects?\\b"), "Side effects can't be ruled out.", "Say side effects are discussed in consultation.", ClaimSeverity.BLOCKER),
        Rule(r("\\bcures?\\b|\\bcured\\b|\\beliminates? (acne|pigmentation|melasma|scars?)\\b"), "Cure/elimination claims overstate outcomes.", "Use 'can help improve' with assessment.", ClaimSeverity.WARNING),
        Rule(r("\\binstant(ly)? results?\\b|\\bovernight results?\\b"), "Timing promises are misleading.", "Explain that improvement is gradual and varies.", ClaimSeverity.WARNING),
        Rule(r("\\b(painless|no pain)\\b"), "Comfort varies between people.", "Say 'many patients find it tolerable'.", ClaimSeverity.WARNING),
        Rule(r("\\b(all|every) skin types?\\b|\\bsuitable for everyone\\b|\\bworks for everyone\\b"), "Universal suitability claims need evidence for your device.", "Say suitability depends on skin assessment.", ClaimSeverity.WARNING),
        Rule(r("\\bbest (laser|treatment|clinic|results?) in\\b|\\bnumber ?1\\b|#1\\b"), "Superlative comparisons are hard to substantiate.", "Focus on what you offer instead.", ClaimSeverity.INFO),
        Rule(r("\\bfda[- ]approved\\b"), "Regulatory status must be verified for this exact device and indication.", "Confirm wording against the device's clearance.", ClaimSeverity.WARNING),
        // Arabic
        Rule(r("مضمون|مضمونة|نضمن|ضمان"), "لا يمكن ضمان النتائج.", "قل إن النتائج تختلف حسب حالة البشرة.", ClaimSeverity.BLOCKER),
        Rule(r("دائمي|دائمة|نهائي|نهائية|للأبد|طول العمر"), "ادعاء الدوام غير مدعوم عادة.", "وضّح أن النتائج تحتاج متابعة وتختلف.", ClaimSeverity.BLOCKER),
        Rule(r("بدون (أي )?(أعراض|مخاطر|اضرار|أضرار|ألم)|ما بيه (أي )?(ضرر|خطورة|اعراض|أعراض)|آمن تماما|آمن ١٠٠|امن تماما"), "كل إجراء له احتمالية أعراض أو مخاطر.", "اذكر أن الملاءمة تُحدد بالاستشارة.", ClaimSeverity.BLOCKER),
        Rule(r("يشفي|شفاء تام|يقضي (تماما|نهائيا)|يزيل نهائيا"), "ادعاء الشفاء أو الإزالة التامة مبالغ فيه.", "استخدم 'يساعد على تحسين'.", ClaimSeverity.WARNING),
        Rule(r("نتائج (فورية|من أول جلسة)|من أول جلسة"), "وعود التوقيت قد تكون مضللة.", "وضّح أن التحسن تدريجي ويختلف.", ClaimSeverity.WARNING),
        Rule(r("لكل (أنواع|انواع) البشرة|يناسب الكل|يناسب الجميع|لكل الناس"), "ادعاء الملاءمة للجميع يحتاج دليل.", "قل حسب حالة البشرة.", ClaimSeverity.WARNING),
    )

    fun check(text: String, sectionId: String? = null, profileForbidden: List<String> = emptyList()): List<ClaimFlag> {
        if (text.isBlank()) return emptyList()
        val flags = mutableListOf<ClaimFlag>()
        for (rule in rules) {
            rule.pattern.find(text)?.let { m ->
                flags += ClaimFlag(m.value.trim(), rule.issue, rule.suggestion, rule.severity, sectionId, source = "local")
            }
        }
        for (phrase in profileForbidden) {
            val p = phrase.trim()
            if (p.length >= 4 && text.contains(p, ignoreCase = true) && flags.none { it.claim.equals(p, true) }) {
                flags += ClaimFlag(p, "Listed under 'claims not to make' in the treatment profile.", "Remove or rephrase.", ClaimSeverity.BLOCKER, sectionId, "profile")
            }
        }
        return flags.distinctBy { it.claim.lowercase() + it.issue }
    }
}
