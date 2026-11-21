package app.reelify.core.content

import app.reelify.core.content.catalog.AerolaseTopicCatalog
import app.reelify.core.content.catalog.CfuElifeTopicCatalog
import app.reelify.core.content.catalog.CleopatraTopicCatalog
import app.reelify.core.content.catalog.DermablateTopicCatalog
import app.reelify.core.content.catalog.DiamondPeelingTopicCatalog
import app.reelify.core.content.catalog.LasemdTopicCatalog
import app.reelify.core.content.catalog.PicostarTopicCatalog
import app.reelify.core.model.Treatment
import app.reelify.core.template.LocalizedText

enum class TopicCategory(val label: LocalizedText) {
    PREPARATION(LocalizedText("Before your visit", "قبل الجلسة")),
    MISCONCEPTIONS(LocalizedText("Misconception", "تصحيح مفهوم")),
    SUITABILITY(LocalizedText("Who it fits", "لمن يناسب")),
    ADVICE(LocalizedText("Clinical advice", "نصيحة طبية")),
    DEVICE(LocalizedText("How it works", "شلون يشتغل")),
    PATIENT_QUESTIONS(LocalizedText("Patient Q&A", "سؤال مراجع")),
    EXPECTATIONS(LocalizedText("Sessions & timing", "الجلسات والنتائج")),
    COMPARISONS(LocalizedText("Comparison", "مقارنة")),
    AFTERCARE(LocalizedText("Aftercare", "بعد الجلسة")),
    ACNE(LocalizedText("Acne", "حب الشباب")),
    PIGMENTATION(LocalizedText("Pigmentation", "التصبغات")),
    SCARS(LocalizedText("Scars", "الندبات")),
    TEXTURE(LocalizedText("Texture & glow", "الملمس والنضارة")),
    MYTHS(LocalizedText("Myth buster", "تصحيح خرافة")),
    BEHIND_SCENES(LocalizedText("Behind the scenes", "داخل العيادة")),
    ;

    fun toUiCategory(): TopicUiCategory = when (this) {
        DEVICE, BEHIND_SCENES -> TopicUiCategory.HOW_IT_WORKS
        PATIENT_QUESTIONS -> TopicUiCategory.PATIENT_QUESTIONS
        MYTHS, MISCONCEPTIONS -> TopicUiCategory.MYTHS
        PREPARATION, AFTERCARE -> TopicUiCategory.BEFORE_AFTER_CARE
        COMPARISONS -> TopicUiCategory.COMPARISONS
        EXPECTATIONS, SUITABILITY, ADVICE, ACNE, PIGMENTATION, SCARS, TEXTURE ->
            TopicUiCategory.SESSIONS_EXPECTATIONS
    }
}

data class TopicIdea(
    val key: String,
    val category: TopicCategory,
    val title: LocalizedText,
    val brief: String,
    val templates: Set<String>,
    val needsProfileFacts: Boolean = false,
    val requiresIndication: List<String> = emptyList(),
    val hookAngle: LocalizedText = LocalizedText(""),
    val uiCategory: TopicUiCategory = category.toUiCategory(),
    val factIds: List<String> = emptyList(),
    val mostInteresting: Boolean = false,
) {
    fun titleFor(t: Treatment?, arabic: Boolean): String {
        val raw = if (arabic) title.ar.ifBlank { title.en } else title.en.ifBlank { title.ar }
        val name = t?.let {
            if (arabic) MedicalGlossary.enforceEnglishMedicalTerms(it.arabicName.ifBlank { it.name }).text
            else it.name
        } ?: "this treatment"
        val substituted = raw.replace("{t}", name)
        return MedicalGlossary.enforceEnglishMedicalTerms(substituted).text
    }

    fun hookFor(arabic: Boolean): String {
        val raw = if (arabic) hookAngle.ar.ifBlank { hookAngle.en } else hookAngle.en.ifBlank { hookAngle.ar }
        return MedicalGlossary.enforceEnglishMedicalTerms(raw).text
    }
}

data class TopicSuggestion(
    val idea: TopicIdea,
    val title: String,
    val usedRecently: Boolean,
    val fromProfile: Boolean = false,
    val hookAngle: String = "",
    val uiCategory: TopicUiCategory = idea.uiCategory,
    val factIds: List<String> = idea.factIds,
    val mostInteresting: Boolean = idea.mostInteresting,
)

object TopicLibrary {

    val treatmentTopics: List<TreatmentTopic> =
        AerolaseTopicCatalog.topics +
            PicostarTopicCatalog.topics +
            CfuElifeTopicCatalog.topics +
            DermablateTopicCatalog.topics +
            LasemdTopicCatalog.topics +
            DiamondPeelingTopicCatalog.topics +
            CleopatraTopicCatalog.diamondGlowTopics +
            CleopatraTopicCatalog.cleopatraPendingTopics

    /** Legacy / custom-treatment fallback templates when no treatment-specific catalog matches. */
    val ideas: List<TopicIdea> = listOf(
        TopicIdea(
            "three_before", TopicCategory.PREPARATION,
            LocalizedText("Three things to know before {t}", "ثلاث شغلات لازم تعرفها قبل {t}"),
            "Three practical pre-visit checks and medications/sun guidance before starting.",
            setOf("three_things"), needsProfileFacts = true,
        ),
        TopicIdea(
            "misconception", TopicCategory.MISCONCEPTIONS,
            LocalizedText("A common misconception about {t}", "فكرة غلط منتشرة عن {t}"),
            "Contrast a popular myth against realistic clinical expectations and mechanisms.",
            setOf("myth_buster", "expert_explains"), needsProfileFacts = true,
        ),
        TopicIdea(
            "right_for_everyone", TopicCategory.SUITABILITY,
            LocalizedText("Is {t} right for everyone?", "هل {t} يناسب الكل؟"),
            "Explain candidate selection, contraindications, and when another modality fits better.",
            setOf("expert_explains", "patient_question"), needsProfileFacts = true,
        ),
        TopicIdea(
            "one_mistake", TopicCategory.ADVICE,
            LocalizedText("One mistake patients make around {t}", "أكثر غلطة تصير ويا {t}"),
            "Share a real clinical aftercare or expectation slip patients avoid after proper advice.",
            setOf("expert_explains", "myth_buster"),
        ),
        TopicIdea(
            "what_happens", TopicCategory.DEVICE,
            LocalizedText("What actually happens inside a {t} session", "شنو يصير خطوة بخطوة بجلسة {t}؟"),
            "Walk calmly through prep, sensation during passes, and immediate post-session appearance.",
            setOf("treatment_showcase", "expert_explains"), needsProfileFacts = true,
        ),
        TopicIdea(
            "top_question", TopicCategory.PATIENT_QUESTIONS,
            LocalizedText("The question patients ask most about {t}", "أكثر سؤال ينسأل بالعيادة عن {t}"),
            "Answer the top real clinical question transparently with session planning caveats.",
            setOf("patient_question"), needsProfileFacts = true,
        ),
        TopicIdea(
            "why_assessment", TopicCategory.ADVICE,
            LocalizedText("Why proper skin assessment comes before {t}", "ليش التقييم الطبي قبل {t} يغير النتيجة؟"),
            "Show why evaluating skin tone, trigger factors, and lesion depth prevents mismatched sessions.",
            setOf("expert_explains", "problem_approach"),
        ),
        TopicIdea(
            "what_affects_results", TopicCategory.EXPECTATIONS,
            LocalizedText("What affects your {t} results?", "شنو العوامل اللي تحدد نتيجة {t}؟"),
            "Break down biological timing, session spacing, and daily UV/aftercare compliance.",
            setOf("expert_explains", "three_things"), needsProfileFacts = true,
        ),
        TopicIdea(
            "vs_alternatives", TopicCategory.COMPARISONS,
            LocalizedText("{t} vs other clinic options", "شلون يفرق {t} عن الخيارات الثانية؟"),
            "Explain mechanism differences objectively so patients understand why one option fits better.",
            setOf("expert_explains", "three_things"), needsProfileFacts = true,
        ),
        TopicIdea(
            "aftercare_skip", TopicCategory.AFTERCARE,
            LocalizedText("The aftercare habit that protects your {t} result", "خطوة العناية اللي تحمي نتيجة {t}"),
            "Give clear, practical home protection and sun/barrier instructions after treatment.",
            setOf("three_things", "patient_question"),
        ),
        TopicIdea(
            "acne_back", TopicCategory.ACNE,
            LocalizedText("Why recurring breakouts need calm treatment planning", "ليش حب الشباب المتكرر يحتاج خطة هادئة مو تقشير عشوائي؟"),
            "Address root inflammation/sebum triggers alongside realistic maintenance.",
            setOf("problem_approach", "expert_explains"), requiresIndication = listOf("acne"),
        ),
        TopicIdea(
            "pigment_sun", TopicCategory.PIGMENTATION,
            LocalizedText("Pigmentation, sun exposure, and choosing {t}", "التصبغات والشمس ودور {t}"),
            "Explain depth-matching and daily UV prevention around pigment sessions.",
            setOf("problem_approach", "patient_question"), requiresIndication = listOf("pigment", "melasma", "spot"),
        ),
        TopicIdea(
            "scar_types", TopicCategory.SCARS,
            LocalizedText("Not all scars need the same depth of treatment", "مو كل الندب تتعالج بنفس العمق"),
            "Differentiate red flat marks from tethered or boxcar scars transparently.",
            setOf("problem_approach", "expert_explains"), requiresIndication = listOf("scar"),
        ),
        TopicIdea(
            "texture_pores", TopicCategory.TEXTURE,
            LocalizedText("Rough texture and pores: matching realistic steps", "الملمس الخشن والمسام: التوقعات الصح"),
            "Explain collagen/surface renewal timelines without exaggerated airbrushed promises.",
            setOf("problem_approach", "three_things"), requiresIndication = listOf("texture", "pore"),
        ),
    )

    fun profileReady(t: Treatment?): Boolean {
        if (t == null) return false
        val pack = TreatmentKnowledgeCatalog.packFor(
            treatmentId = t.id,
            linkedReferenceId = t.linkedReferenceProfileId,
            needsConfiguration = t.needsConfiguration,
        )
        if (pack != null && pack.verificationStatus != VerificationStatus.CLINIC_PROTOCOL_REQUIRED) {
            return true
        }
        return !t.needsConfiguration && t.description.isNotBlank() && t.approvedIndications.isNotEmpty()
    }

    /** Returns treatment-specific topics filtered by template and optional category. */
    fun topicsFor(
        treatment: Treatment?,
        templateId: String? = null,
        uiCategory: TopicUiCategory = TopicUiCategory.ALL,
    ): List<TreatmentTopic> {
        val effectiveId = resolveCatalogId(treatment) ?: return emptyList()
        val byTreatment = treatmentTopics.filter { topic ->
            topic.treatmentId == effectiveId &&
                (!topic.requiresConfirmedIdentity || treatment?.identityConfirmed == true || effectiveId == "diamond_glow")
        }
        val byTemplate = if (templateId.isNullOrBlank()) {
            byTreatment
        } else {
            byTreatment.filter { it.templateId == templateId }
        }
        return when (uiCategory) {
            TopicUiCategory.ALL -> byTemplate
            TopicUiCategory.MOST_INTERESTING -> byTemplate.filter { it.mostInteresting }.ifEmpty { byTemplate }
            else -> byTemplate.filter { it.uiCategory == uiCategory }.ifEmpty { byTemplate }
        }
    }

    fun resolveCatalogId(treatment: Treatment?): String? {
        if (treatment == null) return null
        if (treatment.id == "cleopatra") {
            return if (treatment.identityConfirmed || treatment.linkedReferenceProfileId == "diamond_glow") {
                "diamond_glow"
            } else {
                "cleopatra"
            }
        }
        val linked = treatment.linkedReferenceProfileId
        if (!linked.isNullOrBlank() && treatmentTopics.any { it.treatmentId == linked }) {
            return linked
        }
        if (treatmentTopics.any { it.treatmentId == treatment.id }) {
            return treatment.id
        }
        return null
    }

    private fun matchesIndication(idea: TopicIdea, t: Treatment?): Boolean {
        if (idea.requiresIndication.isEmpty()) return true
        if (t == null) return false
        val bag = (t.approvedIndications + t.description + t.name + t.arabicName).joinToString(" ").lowercase()
        return idea.requiresIndication.any { it in bag }
    }

    fun suggestions(
        treatment: Treatment?,
        templateId: String?,
        recentKeys: List<String>,
        arabic: Boolean,
        limit: Int = 60,
        uiCategory: TopicUiCategory = TopicUiCategory.ALL,
    ): List<TopicSuggestion> {
        val recent = recentKeys.toSet()
        val specific = topicsFor(treatment, templateId, uiCategory)

        if (specific.isNotEmpty()) {
            val mapped = specific.map { topic ->
                val idea = TopicIdea(
                    key = topic.key,
                    category = topic.category,
                    title = topic.title,
                    brief = topic.brief,
                    templates = setOf(topic.templateId),
                    needsProfileFacts = topic.factIds.isNotEmpty(),
                    hookAngle = topic.hookAngle,
                    uiCategory = topic.uiCategory,
                    factIds = topic.factIds,
                    mostInteresting = topic.mostInteresting,
                )
                TopicSuggestion(
                    idea = idea,
                    title = idea.titleFor(treatment, arabic),
                    usedRecently = topic.key in recent,
                    fromProfile = true,
                    hookAngle = idea.hookFor(arabic),
                    uiCategory = topic.uiCategory,
                    factIds = topic.factIds,
                    mostInteresting = topic.mostInteresting,
                )
            }
            val customQ = customProfileQuestions(treatment, templateId, recent, uiCategory)
            return (mapped + customQ)
                .sortedWith(
                    compareBy<TopicSuggestion>(
                        { it.usedRecently },
                        { uiCategory == TopicUiCategory.ALL && !it.mostInteresting },
                    ),
                )
                .take(limit)
        }

        // Custom or general treatment fallback
        val base = ideas
            .filter { matchesIndication(it, treatment) }
            .filter { uiCategory == TopicUiCategory.ALL || uiCategory == TopicUiCategory.MOST_INTERESTING || it.uiCategory == uiCategory }
            .map { idea ->
                TopicSuggestion(
                    idea = idea,
                    title = idea.titleFor(treatment, arabic),
                    usedRecently = idea.key in recent,
                    hookAngle = idea.hookFor(arabic),
                    uiCategory = idea.uiCategory,
                )
            }
        val customQ = customProfileQuestions(treatment, templateId, recent, uiCategory)
        return (customQ + base)
            .sortedWith(
                compareBy<TopicSuggestion>(
                    { it.usedRecently },
                    { templateId != null && templateId !in it.idea.templates },
                    { !it.fromProfile },
                ),
            )
            .take(limit)
    }

    private fun customProfileQuestions(
        treatment: Treatment?,
        templateId: String?,
        recent: Set<String>,
        uiCategory: TopicUiCategory,
    ): List<TopicSuggestion> {
        if (treatment == null || uiCategory !in setOf(TopicUiCategory.ALL, TopicUiCategory.PATIENT_QUESTIONS)) {
            return emptyList()
        }
        val prefix = treatment.id
        return treatment.commonQuestions
            .filter { it.isNotBlank() }
            .mapIndexedNotNull { idx, q ->
                val key = "profile_q_${prefix}_$idx"
                if (treatmentTopics.any { it.treatmentId == resolveCatalogId(treatment) && it.title.en.equals(q, ignoreCase = true) }) {
                    return@mapIndexedNotNull null
                }
                val idea = TopicIdea(
                    key = key,
                    category = TopicCategory.PATIENT_QUESTIONS,
                    title = LocalizedText(q),
                    brief = "Answer this real clinic patient question from ${treatment.name}: $q",
                    templates = setOf(templateId ?: "patient_question"),
                    needsProfileFacts = true,
                    uiCategory = TopicUiCategory.PATIENT_QUESTIONS,
                )
                TopicSuggestion(
                    idea = idea,
                    title = q,
                    usedRecently = key in recent,
                    fromProfile = true,
                    uiCategory = TopicUiCategory.PATIENT_QUESTIONS,
                )
            }
    }
}
