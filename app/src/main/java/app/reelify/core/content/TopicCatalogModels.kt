package app.reelify.core.content

import app.reelify.core.template.LocalizedText

enum class TopicUiCategory(val label: LocalizedText) {
    ALL(LocalizedText("All", "الكل")),
    MOST_INTERESTING(LocalizedText("Most interesting", "الأكثر جذباً")),
    HOW_IT_WORKS(LocalizedText("How it works", "شلون يشتغل")),
    PATIENT_QUESTIONS(LocalizedText("Patient Q&A", "أسئلة المراجعين")),
    SESSIONS_EXPECTATIONS(LocalizedText("Sessions & expectations", "الجلسات والنتائج")),
    MYTHS(LocalizedText("Myths & clarity", "توضيح ومفاهيم")),
    BEFORE_AFTER_CARE(LocalizedText("Before & aftercare", "قبل وبعد الجلسة")),
    COMPARISONS(LocalizedText("Comparisons", "مقارنات")),
}

data class TreatmentTopic(
    val key: String,
    val treatmentId: String,
    val templateId: String,
    val category: TopicCategory,
    val uiCategory: TopicUiCategory,
    val title: LocalizedText,
    val hookAngle: LocalizedText,
    val brief: String,
    val factIds: List<String>,
    val mostInteresting: Boolean = false,
    val requiresConfirmedIdentity: Boolean = false,
)
