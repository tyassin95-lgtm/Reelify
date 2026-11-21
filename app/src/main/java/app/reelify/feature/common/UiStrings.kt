package app.reelify.feature.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Interface-language string table for the primary navigation and actions (EN / AR). */
data class UiStrings(
    val home: String, val projects: String, val studio: String, val settings: String,
    val createReel: String, val createReelSub: String, val continueProject: String, val recent: String,
    val treatments: String, val templates: String, val next: String, val back: String, val analyze: String,
    val generateScript: String, val approvePlan: String, val record: String, val importMedia: String,
    val autoEdit: String, val export: String, val share: String, val retry: String, val cancel: String, val save: String,
    val delete: String, val aiConnections: String, val language: String,
) {
    companion object {
        val English = UiStrings(
            "Home", "Projects", "Studio", "Settings",
            "Create New Reel", "Pick a template, choose a topic, film three shots", "Continue", "Recent projects",
            "Treatments", "Templates", "Continue", "Back", "Analyze Idea",
            "Generate Script", "Approve Plan", "Record", "Import",
            "Edit Automatically", "Export", "Share", "Retry", "Cancel", "Save",
            "Delete", "AI Connections", "Language",
        )
        val Arabic = UiStrings(
            "الرئيسية", "المشاريع", "الاستوديو", "الإعدادات",
            "ريل جديد", "اختار قالب، اختار موضوع، وصوّر ثلاث لقطات", "متابعة", "المشاريع الأخيرة",
            "العلاجات", "القوالب", "التالي", "رجوع", "حلّل الفكرة",
            "اكتب السكربت", "اعتماد الخطة", "تسجيل", "استيراد",
            "مونتاج تلقائي", "تصدير", "مشاركة", "إعادة المحاولة", "إلغاء", "حفظ",
            "حذف", "اتصالات الذكاء الاصطناعي", "اللغة",
        )
    }
}

val LocalStrings = staticCompositionLocalOf { UiStrings.English }

/** True when the interface (not the reel) is in Arabic. */
val LocalArabicUi = staticCompositionLocalOf { false }

/** Inline bilingual UI copy for Reelify 2.0 screens. */
@Composable
fun tr(en: String, ar: String): String = if (LocalArabicUi.current) ar else en

@Composable
fun app.reelify.core.template.LocalizedText.text(): String = resolve(LocalArabicUi.current)

@Composable
fun strings(): UiStrings = LocalStrings.current
