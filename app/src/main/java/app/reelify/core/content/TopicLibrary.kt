package app.reelify.core.content

import app.reelify.core.model.Treatment
import app.reelify.core.template.LocalizedText

@kotlinx.serialization.Serializable
enum class TopicCategory(val label: LocalizedText) {
    ACNE(LocalizedText("Acne", "حب الشباب")), PIGMENTATION(LocalizedText("Pigmentation", "التصبغات")), TEXTURE(LocalizedText("Skin texture", "ملمس البشرة")),
    SCARS(LocalizedText("Scars", "الآثار والندبات")), SUITABILITY(LocalizedText("Suitability", "لمن يناسب")), PREPARATION(LocalizedText("Preparation", "التحضير")),
    AFTERCARE(LocalizedText("Aftercare", "العناية بعد الجلسة")), PATIENT_QUESTIONS(LocalizedText("Patient questions", "أسئلة المراجعين")), MYTHS(LocalizedText("Myths", "أفكار غلط")),
    MISCONCEPTIONS(LocalizedText("Misconceptions", "مفاهيم خاطئة")), EXPECTATIONS(LocalizedText("Expectations", "التوقعات")), COMPARISONS(LocalizedText("Comparisons", "مقارنات")),
    ADVICE(LocalizedText("Professional advice", "نصيحة مختص")), BEHIND_SCENES(LocalizedText("Behind the scenes", "ورا الكواليس")), DEVICE(LocalizedText("Device introduction", "تعريف بالجهاز")),
}

/**
 * An offline topic structure. [title] may contain `{t}` (treatment name). [brief] tells the
 * scriptwriter what the reel is about — it never contains clinical facts itself; facts come
 * only from the approved treatment profile. Topics with [needsProfileFacts] are hidden for
 * profiles that still need configuration.
 */
data class TopicIdea(
    val key: String,
    val category: TopicCategory,
    val title: LocalizedText,
    val brief: String,
    val templates: Set<String>,
    val needsProfileFacts: Boolean = false,
    /** Lower-case keywords; when set, the topic is shown only if the profile's indications/talking points mention one. */
    val requiresIndication: List<String> = emptyList(),
) {
    fun titleFor(t: Treatment?, arabic: Boolean): String {
        val name = if (t == null || t.isGeneral) (if (arabic) "العلاج" else "treatment") else if (arabic) t.arabicName.ifBlank { t.name } else t.name
        return title.resolve(arabic).replace("{t}", name)
    }
}

data class TopicSuggestion(val idea: TopicIdea, val title: String, val usedRecently: Boolean, val fromProfile: Boolean = false)

object TopicLibrary {
    private const val EE = "expert_explains"; private const val MB = "myth_buster"; private const val TS = "treatment_showcase"
    private const val TT = "three_things"; private const val PQ = "patient_question"; private const val PA = "problem_approach"

    val ideas: List<TopicIdea> = listOf(
        TopicIdea("three_before", TopicCategory.PREPARATION, LocalizedText("Three things to know before {t}", "ثلاث شغلات لازم تعرفها قبل {t}"),
            "Three practical, non-clinical-claim things to know before booking (assessment, preparation, realistic expectations).", setOf(TT, EE)),
        TopicIdea("misconception", TopicCategory.MISCONCEPTIONS, LocalizedText("A common misconception about {t}", "فكرة غلط منتشرة عن {t}"),
            "Correct one common misconception using an approved fact from the profile.", setOf(MB), needsProfileFacts = true),
        TopicIdea("right_for_everyone", TopicCategory.SUITABILITY, LocalizedText("Is {t} right for everyone?", "{t} يناسب الكل؟"),
            "Explain honestly that suitability depends on skin type, concern and assessment.", setOf(PQ, EE, MB)),
        TopicIdea("one_mistake", TopicCategory.ADVICE, LocalizedText("One mistake people make", "أكثر غلطة يسويها الناس"),
            "One common, relatable mistake people make before or after treatment (e.g. skipping assessment or sun protection).", setOf(EE, MB, PA)),
        TopicIdea("what_happens", TopicCategory.DEVICE, LocalizedText("What happens during a {t} session?", "شنو يصير بجلسة {t}؟"),
            "Walk through a session simply and accurately (only steps in the profile; sensation varies).", setOf(TS, EE), needsProfileFacts = true),
        TopicIdea("top_question", TopicCategory.PATIENT_QUESTIONS, LocalizedText("The question patients ask most", "أكثر سؤال يسألوه المراجعين"),
            "Answer the most common patient question from the profile honestly, with the important qualification.", setOf(PQ)),
        TopicIdea("why_assessment", TopicCategory.ADVICE, LocalizedText("Why proper assessment matters", "ليش التقييم قبل العلاج مهم"),
            "Why a skin assessment comes before choosing any device or protocol.", setOf(EE, PQ, PA)),
        TopicIdea("what_affects_results", TopicCategory.EXPECTATIONS, LocalizedText("What affects your results?", "شنو يأثر على النتيجة؟"),
            "General factors that influence results (skin type, consistency, aftercare, sun) without promising outcomes.", setOf(TT, EE)),
        TopicIdea("vs_alternatives", TopicCategory.COMPARISONS, LocalizedText("{t} vs other options", "{t} مقارنة بخيارات ثانية"),
            "A fair, non-superlative comparison: which concerns the profile says it may suit vs when another approach may be considered.", setOf(EE, PQ), needsProfileFacts = true),
        TopicIdea("before_session", TopicCategory.PREPARATION, LocalizedText("Before your session", "قبل جلستك"),
            "Simple preparation advice (tell the clinic about products/medication, sun exposure) — general, not prescriptive.", setOf(TT, PQ)),
        TopicIdea("aftercare_skip", TopicCategory.AFTERCARE, LocalizedText("The aftercare step people skip", "خطوة العناية اللي الناس تنساها"),
            "The aftercare step most often skipped (typically sun protection) and why it matters.", setOf(EE, MB, TT)),
        TopicIdea("how_many_sessions", TopicCategory.EXPECTATIONS, LocalizedText("How many sessions will I need?", "كم جلسة أحتاج؟"),
            "Honest answer: it varies per person and concern; explain what influences the number.", setOf(PQ, PA)),
        TopicIdea("acne_back", TopicCategory.ACNE, LocalizedText("Why acne keeps coming back", "ليش حب الشباب يرجع؟"),
            "Relatable acne concern and how an assessment-based plan with this treatment may fit.", setOf(PA, EE), requiresIndication = listOf("acne", "حب الشباب")),
        TopicIdea("pigment_sun", TopicCategory.PIGMENTATION, LocalizedText("Pigmentation and the sun", "التصبغات والشمس"),
            "Why pigmentation can return without sun protection, and where treatment may fit.", setOf(PA, EE, MB), requiresIndication = listOf("pigment", "تصبغ", "melasma")),
        TopicIdea("scar_types", TopicCategory.SCARS, LocalizedText("Not all scars are the same", "مو كل الآثار نفس الشي"),
            "Different scar types need different approaches; assessment decides.", setOf(EE, TT), requiresIndication = listOf("scar", "آثار", "ندب")),
        TopicIdea("texture_pores", TopicCategory.TEXTURE, LocalizedText("Rough texture and visible pores", "ملمس البشرة والمسامات"),
            "A relatable texture concern and how the treatment may be part of a plan.", setOf(PA, EE), requiresIndication = listOf("texture", "pore", "ملمس", "مسام", "resurfac")),
        TopicIdea("myth_thins_skin", TopicCategory.MYTHS, LocalizedText("Myth: lasers thin your skin", "فكرة غلط: الليزر يخفّف البشرة"),
            "Address the myth carefully using only what the profile supports; if unsupported, say it depends and assessment matters.", setOf(MB), needsProfileFacts = true),
        TopicIdea("behind_scenes", TopicCategory.BEHIND_SCENES, LocalizedText("Behind the scenes: preparing a session", "ورا الكواليس: شلون نحضّر الجلسة"),
            "Show how the room and device are prepared (hygiene, settings check) — no outcome claims.", setOf(TS)),
        TopicIdea("meet_device", TopicCategory.DEVICE, LocalizedText("Meet the {t}", "تعرّفوا على {t}"),
            "Introduce the device/treatment by name and what the profile says it may be considered for.", setOf(TS), needsProfileFacts = true),
        TopicIdea("honest_advice", TopicCategory.ADVICE, LocalizedText("My honest advice before any laser", "نصيحتي الصريحة قبل أي ليزر"),
            "Honest expert advice: assessment first, realistic expectations, aftercare.", setOf(EE, TT, PA)),
    )

    private fun matchesIndication(idea: TopicIdea, t: Treatment?): Boolean {
        if (idea.requiresIndication.isEmpty()) return true
        if (t == null || t.isGeneral) return false
        val hay = (t.approvedIndications + t.talkingPoints + t.description).joinToString(" ").lowercase()
        return idea.requiresIndication.any { hay.contains(it.lowercase()) }
    }

    /** Whether a treatment profile has enough approved facts for fact-dependent topics. */
    fun profileReady(t: Treatment?): Boolean = t != null && !t.isGeneral && !t.needsConfiguration && (t.approvedIndications.isNotEmpty() || t.talkingPoints.isNotEmpty())

    /**
     * Topic suggestions for a treatment + template, safest first. Topics used recently for this
     * treatment drop to the end (variety), template matches rise. Deterministic for equal input.
     */
    fun suggestions(treatment: Treatment?, templateId: String?, recentKeys: List<String>, arabic: Boolean, limit: Int = 10): List<TopicSuggestion> {
        val ready = profileReady(treatment)
        val recent = recentKeys.take(8).toSet()
        val fromLib = ideas.filter { (!it.needsProfileFacts || ready) && matchesIndication(it, treatment) }
            .map { TopicSuggestion(it, it.titleFor(treatment, arabic), it.key in recent) }
        val fromProfile = if (ready) treatment!!.commonQuestions.take(3).mapIndexed { i, q ->
            val idea = TopicIdea("profile_q_$i", TopicCategory.PATIENT_QUESTIONS, LocalizedText(q), "Answer this patient question from the profile: $q", setOf("patient_question"), needsProfileFacts = true)
            TopicSuggestion(idea, q, idea.key in recent, fromProfile = true)
        } else emptyList()
        return (fromLib + fromProfile)
            .sortedWith(compareBy<TopicSuggestion>({ it.usedRecently }, { templateId != null && templateId !in it.idea.templates }, { !it.fromProfile }))
            .take(limit)
    }
}
