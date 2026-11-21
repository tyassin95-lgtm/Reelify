package app.reelify.core.content

import kotlinx.serialization.Serializable

/** Review status of a style example. Built-in examples start UNREVIEWED — they were written by the app author, not verified by a native Iraqi reviewer. */
@Serializable
enum class ExampleStatus(val label: String) {
    UNREVIEWED("Not yet reviewed"), APPROVED("Approved by you"), CORRECTED("Corrected by you"), REJECTED("Rejected — never used")
}

/**
 * A short reference line for the scriptwriter. [better] is the conversational target; [formal] is
 * the stiff/MSA/translated phrasing it replaces (optional). The owner can approve, correct or
 * reject each one; only non-rejected examples are sent to the model, approved/corrected first.
 */
@Serializable
data class StyleExample(
    val id: String,
    val category: String,
    val better: String,
    val formal: String = "",
    val note: String = "",
    val status: ExampleStatus = ExampleStatus.UNREVIEWED,
    val builtIn: Boolean = true,
)

/**
 * Iraqi Arabic editorial profile for a credible aesthetic specialist speaking on social media.
 * Everything here is a GUIDE, not a guarantee: heuristic checks cannot prove dialect authenticity,
 * and final quality is judged by the owner (see Studio → Iraqi style).
 */
object IraqiStyleGuide {
    const val VERSION = "iq-style-1"

    val toneRules = listOf(
        "Speak as a real Iraqi clinician talking to one patient: warm, confident, clear — never a news anchor or an advert.",
        "Use spoken Iraqi (Baghdadi-leaning, understood nationwide). Avoid Modern Standard Arabic sentence patterns and literal translation from English.",
        "Address viewers in plural second person (انتو / -كم) or singular (انت / -ك) consistently within a reel.",
        "Short sentences with natural rhythm for the camera; one idea per sentence.",
        "Keep device and treatment names exactly as spelled in the profile (e.g. Aerolase / إيروليز).",
        "Do not overload with slang or fillers; at most one light filler (يعني، الصراحة) per section.",
        "Do not imitate Gulf or Levantine dialect (e.g. avoid شلونكم→ كيفكم, ايش, هيك, وايد, زين as Gulf 'good').",
        "Vary expressions between reels; never open every reel with the same phrase.",
    )

    /** Formal / MSA markers that usually make spoken Iraqi sound stiff. Token → spoken alternative. */
    val formalMarkers: Map<String, String> = linkedMapOf(
        "سوف" to "راح / رح", "سنتحدث" to "راح نحجي", "سنتعرف" to "خلّي نعرف", "دعونا" to "خلّونا", "هل" to "(drop it: intonation makes the question)",
        "ماذا" to "شنو", "لماذا" to "ليش", "كيف" to "شلون", "الآن" to "هسه", "جداً" to "كلش / هواية", "جدا" to "كلش / هواية",
        "ليس" to "مو", "ليست" to "مو", "لا يوجد" to "ماكو", "يوجد" to "أكو", "هذه" to "هاي", "هؤلاء" to "هذوله", "الذي" to "اللي", "التي" to "اللي",
        "يجب" to "لازم", "يمكنكم" to "تكدرون", "يمكنك" to "تكدر", "نقدم لكم" to "خلّي أوريكم", "يرجى" to "(rephrase as a direct invitation)",
        "تعانون" to "عندكم / تحسون", "حيث" to "(split the sentence)", "وفقاً" to "حسب", "وفقا" to "حسب", "إن" to "(drop it)",
        "تجدر الإشارة" to "(drop it)", "من الجدير بالذكر" to "(drop it)", "عزيزي المتابع" to "(drop it)", "أعزائي" to "(drop it)",
    )

    /** Markers that typically signal spoken Iraqi. Presence is encouraging but never proof. */
    val iraqiMarkers = setOf(
        "شنو", "شلون", "ليش", "هسه", "هواية", "كلش", "مو", "ماكو", "أكو", "اكو", "هاي", "هذوله", "اللي", "هيج", "علمود", "وياي", "ويانا", "وياكم",
        "شكد", "شوكت", "بس", "خلّي", "خلي", "خلّونا", "خلونا", "راح", "تكدر", "تكدرون", "نكدر", "أكدر", "اكدر", "لازم", "أحجي", "احجي", "نحجي",
        "أريد", "اريد", "تريد", "تريدون", "يعني", "الصراحة", "سوا", "زين", "محد", "كلشي", "شغلة", "أوريكم", "اوريكم", "إلكم", "الكم", "إلك", "الك",
        "ترة", "ترى", "يمعود", "دا", "ديصير", "يصير", "ميصير", "بيها", "بيه", "إله", "اله",
    )

    /** Overused AI marketing lines in Arabic and English. Flagged so reels stay fresh. */
    val overusedPhrases = listOf(
        "في عالم", "بشرة نضرة ومشرقة", "سر الجمال", "لا تفوتوا الفرصة", "احصلوا على", "النتائج المذهلة", "ثورة في", "رحلتك نحو", "اكتشفوا سر",
        "تألقي", "إطلالة مثالية", "Say goodbye to", "game-changer", "game changer", "unlock", "glow-up journey", "In today's video", "Are you tired of",
        "Look no further", "transform your skin", "flawless skin",
    )

    /** Approved CTA styles (the model picks one; it must not force a consultation CTA every time). */
    val ctaStyles = listOf("book_assessment", "save_for_later", "comment_question", "follow_for_more", "share_with_friend", "dm_clinic")

    val ctaExamples: Map<String, String> = mapOf(
        "book_assessment" to "إذا محتارين، احجزوا تقييم ونشوف سوا شنو الأنسب لبشرتكم.",
        "save_for_later" to "احفظوا الفيديو حتى ترجعوله قبل جلستكم الجاية.",
        "comment_question" to "اكتبولي بالتعليقات شنو السؤال اللي يحيّركم، وأجاوبكم بفيديو ثاني.",
        "follow_for_more" to "تابعوني إذا تريدون تعرفون أكثر عن بشرتكم بدون مبالغة.",
        "share_with_friend" to "دزّوا الفيديو لصديقتكم اللي دائماً تسأل هالسؤال.",
        "dm_clinic" to "أي سؤال، راسلونا خاص ونجاوبكم.",
    )

    /** Curated reference examples (written for Reelify; status UNREVIEWED until the owner reviews them). */
    val builtInExamples: List<StyleExample> = listOf(
        StyleExample("iq01", "hook", "عندكم تصبغات بالوجه ومحتارين شنو الليزر المناسب؟ خلّي أوضحلكم شغلة مهمة قبل لا تختارون.", "هل تعانون من مشكلات التصبغات الجلدية؟ سوف نتحدث اليوم عن الخيارات العلاجية المتاحة."),
        StyleExample("iq02", "hook", "حبّ الشباب مو بس مشكلة مراهقين، هواية ناس يعانون منه حتى بالثلاثينات.", "إن حب الشباب ليس مقتصراً على فئة المراهقين."),
        StyleExample("iq03", "myth", "أكثر فكرة غلط أسمعها: الليزر يخفّف البشرة. لا، هاي مو صحيحة.", "من الخرافات الشائعة أن الليزر يسبب ترقق الجلد."),
        StyleExample("iq04", "explanation", "الإيروليز يشتغل بنبضة قصيرة كلش، وعدد الجلسات يختلف من شخص لشخص حسب حالة البشرة.", "يعمل جهاز الإيروليز بنبضات قصيرة جداً، ويختلف عدد الجلسات وفقاً لحالة البشرة."),
        StyleExample("iq05", "cta", "إذا محتارين، احجزوا تقييم ونشوف سوا شنو الأنسب إلكم.", "للحجز والاستفسار يرجى التواصل معنا."),
        StyleExample("iq06", "cta", "احفظوا الفيديو حتى ترجعوله قبل جلستكم الجاية."),
        StyleExample("iq07", "cta", "اكتبولي بالتعليقات شنو السؤال اللي يحيّركم، وأجاوبكم بفيديو ثاني."),
        StyleExample("iq08", "question", "هواية يسألوني: الجلسة تألّم؟ الصراحة الإحساس يختلف من شخص لشخص.", "يتساءل الكثيرون: هل الجلسة مؤلمة؟"),
        StyleExample("iq09", "qualification", "بس انتبهوا، مو كل بشرة يناسبها نفس العلاج، وعلمود هيج الفحص يجي قبل كلشي.", "يجب التنبيه إلى أن العلاج لا يناسب جميع أنواع البشرة."),
        StyleExample("iq10", "expectations", "النتيجة تحتاج وقت وصبر، ومحد يكدر يوعدك بنتيجة من أول جلسة.", "لا يمكن ضمان ظهور النتائج من الجلسة الأولى."),
        StyleExample("iq11", "three_things", "ثلاث شغلات لازم تعرفوهن قبل ما تسوون أي جلسة ليزر.", "إليكم ثلاثة أمور يجب معرفتها قبل الخضوع لجلسة الليزر."),
        StyleExample("iq12", "aftercare", "بعد الجلسة، واقي الشمس مو اختياري. هاي أهم خطوة.", "يجب استخدام واقي الشمس بعد الجلسة."),
        StyleExample("iq13", "preparation", "قبل الجلسة بكم يوم، خففوا الشمس، وكولولنا إذا تستعملون أي كريم.", "يُنصح بتجنب التعرض لأشعة الشمس قبل الجلسة."),
        StyleExample("iq14", "mistake", "أكثر غلطة أشوفها: الناس تبدي جلسات بدون ما تعرف سبب المشكلة أصلاً."),
        StyleExample("iq15", "device", "هذا جهاز البيكوستار، وخلّي أحجيلكم شلون نستخدمه بالعيادة.", "نقدم لكم اليوم جهاز بيكوستار المتطور."),
        StyleExample("iq16", "behind_scenes", "تعالوا وياي، أوريكم شلون نحضّر الغرفة قبل كل جلسة.", "دعونا نأخذكم في جولة خلف الكواليس."),
        StyleExample("iq17", "scars", "آثار الحبوب مو كلها نفس النوع، وكل نوع إله طريقة علاج.", "تختلف أنواع ندبات حب الشباب، ولكل نوع طريقة علاج خاصة به."),
        StyleExample("iq18", "texture", "إذا ملمس بشرتك مو ناعم والمسامات واضحة، هذا الفيديو إلك."),
        StyleExample("iq19", "comparison", "هواية يكولون هذا الليزر أحسن من ذاك. الجواب الصريح: يعتمد على بشرتك ومشكلتك.", "أيهما أفضل؟ يعتمد ذلك على نوع البشرة."),
        StyleExample("iq20", "reassurance", "ما أريد أخوّفكم، بس أريدكم تعرفون الصورة كاملة."),
        StyleExample("iq21", "suitability", "العلاج يناسب الكل؟ لا، وهذا شي طبيعي. التقييم هو اللي يحدد.", "هل يناسب هذا العلاج الجميع؟ لا، فالتقييم هو الذي يحدد ذلك."),
        StyleExample("iq22", "hook_curiosity", "تعرفون ليش بعض الناس ما يشوفون نتيجة من الليزر؟"),
        StyleExample("iq23", "hook_expert", "من خبرتي بالعيادة، السر مو بالجهاز بس، السر بالخطة."),
        StyleExample("iq24", "pigmentation", "الكلف يرجع إذا ما انتبهنا للشمس، فالعلاج وحده مو كافي.", "قد يعود الكلف في حال عدم الوقاية من الشمس."),
    )

    /** Built-in lines merged with the owner's corrections/approvals (by id). Rejected lines are excluded. */
    fun effectiveExamples(ownerEdits: List<StyleExample>): List<StyleExample> {
        val byId = ownerEdits.associateBy { it.id }
        val merged = builtInExamples.map { byId[it.id] ?: it } + ownerEdits.filter { e -> builtInExamples.none { it.id == e.id } }
        return merged.filter { it.status != ExampleStatus.REJECTED }
            .sortedBy { when (it.status) { ExampleStatus.APPROVED, ExampleStatus.CORRECTED -> 0; else -> 1 } }
    }
}

data class DialectReport(
    val formalHits: List<String>,
    val iraqiHits: List<String>,
    val overusedHits: List<String>,
) {
    /** Strongly MSA: several formal markers and no spoken-Iraqi markers at all. */
    val likelyFormal: Boolean get() = formalHits.size >= 2 && iraqiHits.isEmpty()
    fun notes(): List<String> = buildList {
        if (formalHits.isNotEmpty()) add("Formal wording: " + formalHits.distinct().take(5).joinToString("، ") { "$it → ${IraqiStyleGuide.formalMarkers[it] ?: "?"}" })
        if (overusedHits.isNotEmpty()) add("Overused phrasing: " + overusedHits.distinct().joinToString(", "))
    }
}

/**
 * Heuristic dialect/tone check. It looks for formal (MSA) markers, spoken-Iraqi markers and
 * overused marketing phrases. It CANNOT certify that text is authentic Iraqi Arabic — it only
 * catches obvious problems so they can be repaired or shown to the owner.
 */
object DialectChecker {
    private val arabicPrefixes = listOf("و", "ف", "ب", "ل")

    fun tokens(text: String): List<String> =
        text.split(Regex("[^\\p{L}\\p{M}]+")).map { it.trim() }.filter { it.isNotEmpty() }
            .map { it.replace(Regex("[\\u064B-\\u0652\\u0640]"), "") } // strip harakat + tatweel for matching only

    fun analyze(text: String): DialectReport {
        val toks = tokens(text)
        val norm = " " + toks.joinToString(" ") + " "
        val formal = mutableListOf<String>()
        for (m in IraqiStyleGuide.formalMarkers.keys) {
            val key = m.replace(Regex("[\\u064B-\\u0652]"), "")
            if (key.contains(' ')) { if (norm.contains(" $key ")) formal += m; continue }
            if (toks.any { t -> t == key || (key.length >= 3 && arabicPrefixes.any { p -> t == p + key }) }) formal += m
        }
        val iraqi = toks.filter { t -> t in IraqiStyleGuide.iraqiMarkers || arabicPrefixes.any { p -> t.startsWith(p) && t.drop(1) in IraqiStyleGuide.iraqiMarkers } }
        val overused = IraqiStyleGuide.overusedPhrases.filter { text.contains(it, ignoreCase = true) }
        return DialectReport(formal.distinct(), iraqi.distinct(), overused)
    }

    /** English overused-phrase check (dialect markers don't apply). */
    fun overused(text: String): List<String> = IraqiStyleGuide.overusedPhrases.filter { text.contains(it, ignoreCase = true) }
}
