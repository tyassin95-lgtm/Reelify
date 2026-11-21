package app.reelify.core.content

import app.reelify.core.model.TerminologyEntry
import kotlinx.serialization.Serializable

/**
 * Canonical reviewed medical & technical terminology entry.
 *
 * Policy (Reelify 2.2):
 * Regardless of whether a reel is in English or Iraqi Arabic (`ar-IQ`), medical and technical
 * terminology MUST be rendered using its approved English spelling in Latin characters.
 * Conversational Iraqi grammar and everyday Arabic words remain natural around those terms.
 */
@Serializable
data class CanonicalMedicalTerm(
    val id: String,
    val englishCanonical: String,
    val category: String,
    val isBrandProperNoun: Boolean = false,
    val prohibitedArabicTranslations: List<String> = emptyList(),
    val prohibitedTransliterations: List<String> = emptyList(),
    val misspellings: List<String> = emptyList(),
    val allowedAbbreviations: List<String> = emptyList(),
    val pronunciation: String = "",
    val treatmentIds: List<String> = emptyList(),
    val mandatoryEnglish: Boolean = true,
) {
    val allProhibitedArabic: List<String>
        get() = (prohibitedArabicTranslations + prohibitedTransliterations).filter { it.isNotBlank() }.distinct()
}

/**
 * Medically reviewed glossary enforcing canonical English Latin-script medical and device
 * terminology across AI ideas, scripts, headlines, rewrites, and transcription correction.
 */
object MedicalGlossary {

    val builtInTerms: List<CanonicalMedicalTerm> = listOf(
        // -------------------- Devices & Clinic Systems (Proper Nouns) --------------------
        CanonicalMedicalTerm(
            id = "term_picostar",
            englishCanonical = "PicoStar",
            category = "device",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("بيكوستار", "بيكو ستار"),
            misspellings = listOf("Picostar", "PikoStar", "pico star", "piko star", "PicoStar®"),
            pronunciation = "PEE-ko-star",
            treatmentIds = listOf("picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_aerolase",
            englishCanonical = "Aerolase",
            category = "device",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("إيروليز", "ايروليز", "أيروليز", "إيرو ليز", "ايرو ليز"),
            misspellings = listOf("Airolase", "Aero lase", "Aero laze", "Aerolaze"),
            pronunciation = "AIR-oh-laze",
            treatmentIds = listOf("aerolase"),
        ),
        CanonicalMedicalTerm(
            id = "term_neo_elite",
            englishCanonical = "Neo Elite",
            category = "device",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("نيو إيليت", "نيو ايليت"),
            misspellings = listOf("Neo-Elite", "Neo elite"),
            treatmentIds = listOf("aerolase"),
        ),
        CanonicalMedicalTerm(
            id = "term_cfu_elife",
            englishCanonical = "CFU Èlife",
            category = "device",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("سي إف يو إيلايف", "سي اف يو ايلايف", "سي إف يو", "سي اف يو"),
            misspellings = listOf("CFU Elife", "CFU-Elife", "Cfu elife", "C F U", "see if you"),
            allowedAbbreviations = listOf("CFU"),
            pronunciation = "see-ef-you ee-life",
            treatmentIds = listOf("cfu"),
        ),
        CanonicalMedicalTerm(
            id = "term_dermablate",
            englishCanonical = "Dermablate",
            category = "device",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("ديرمابليت", "ديرما بليت"),
            misspellings = listOf("Derma blate", "Derma blade", "Dermablade", "Dermablate®"),
            pronunciation = "DER-ma-blate",
            treatmentIds = listOf("dermablate"),
        ),
        CanonicalMedicalTerm(
            id = "term_lasemd_ultra",
            englishCanonical = "LaseMD Ultra",
            category = "device",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("ليز إم دي ألترا", "ليز ام دي الترا", "ليزر إم دي ألترا"),
            misspellings = listOf("LaseMD-Ultra", "Laser MD Ultra", "Lays MD Ultra"),
            treatmentIds = listOf("lasemd"),
        ),
        CanonicalMedicalTerm(
            id = "term_lasemd",
            englishCanonical = "LaseMD",
            category = "device",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("ليز إم دي", "ليز ام دي", "ليزر إم دي", "ليزر ام دي"),
            misspellings = listOf("Lase MD", "Laser MD", "Lays MD", "Lasemd"),
            pronunciation = "laze em dee",
            treatmentIds = listOf("lasemd"),
        ),
        CanonicalMedicalTerm(
            id = "term_diamond_glow",
            englishCanonical = "DiamondGlow",
            category = "device",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("دايموند گلو", "دايموند جلو", "دايموند غلو", "دايمند گلو", "دايمند جلو"),
            misspellings = listOf("Diamond Glow", "Diamond glow", "DimondGlow", "DiamondGlow®"),
            pronunciation = "DIE-mund glow",
            treatmentIds = listOf("diamond_glow", "cleopatra"),
        ),
        CanonicalMedicalTerm(
            id = "term_diamond_peeling",
            englishCanonical = "Diamond Peeling",
            category = "procedure",
            isBrandProperNoun = true,
            prohibitedArabicTranslations = listOf("التقشير الماسي", "تقشير ماسي"),
            prohibitedTransliterations = listOf("دايموند بيلنك", "دايموند بيلينغ"),
            misspellings = listOf("Diamond peel", "Dimond peeling", "Diamond Pealing"),
            treatmentIds = listOf("diamond_peeling"),
        ),
        CanonicalMedicalTerm(
            id = "term_microspot",
            englishCanonical = "MicroSpot",
            category = "technology",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("مايكروسبوت", "مايكرو سبوت"),
            misspellings = listOf("Micro Spot", "Micro-Spot", "microspot"),
            treatmentIds = listOf("picostar", "dermablate"),
        ),
        CanonicalMedicalTerm(
            id = "term_pro_infusion",
            englishCanonical = "Pro-Infusion",
            category = "technology",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("برو إنفيوجن", "برو انفيوجن"),
            misspellings = listOf("Pro Infusion", "ProInfusion"),
            treatmentIds = listOf("diamond_glow"),
        ),
        CanonicalMedicalTerm(
            id = "term_intellitrak",
            englishCanonical = "IntelliTrak",
            category = "technology",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("إنتلي تراك", "انتلي تراك"),
            misspellings = listOf("Intellitrak", "IntelliTrack", "Intelli-Trak"),
            treatmentIds = listOf("lasemd"),
        ),

        // -------------------- Modalities & Physics --------------------
        CanonicalMedicalTerm(
            id = "term_hifu",
            englishCanonical = "HIFU",
            category = "modality",
            isBrandProperNoun = true,
            prohibitedArabicTranslations = listOf("الموجات فوق الصوتية عالية الكثافة", "الموجات فوق الصوتية المركزة", "الموجات فوق الصوتية المركّزة"),
            prohibitedTransliterations = listOf("هايفو", "الهايفو"),
            misspellings = listOf("Hifu", "hifu", "HYFU"),
            treatmentIds = listOf("cfu"),
        ),
        CanonicalMedicalTerm(
            id = "term_nd_yag",
            englishCanonical = "Nd:YAG",
            category = "modality",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("ند ياغ", "إن دي ياغ", "ان دي ياغ", "اندياغ", "نيوديميوم ياغ"),
            misspellings = listOf("Nd YAG", "Nd-YAG", "ND Yag", "Nd yag", "Nd:yag"),
            treatmentIds = listOf("aerolase", "picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_er_yag",
            englishCanonical = "Er:YAG",
            category = "modality",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("إربيوم ياغ", "اربيوم ياغ", "إربيوم:ياغ", "اربيوم:ياغ", "ايربيوم ياغ"),
            misspellings = listOf("Er YAG", "Er-YAG", "ER Yag", "Er:yag", "Erbium YAG"),
            treatmentIds = listOf("dermablate"),
        ),
        CanonicalMedicalTerm(
            id = "term_picosecond_laser",
            englishCanonical = "Picosecond laser",
            category = "modality",
            prohibitedArabicTranslations = listOf("ليزر البيكو ثانية", "ليزر البيكوثانية", "ليزر بيكو ثانية"),
            prohibitedTransliterations = listOf("بيكوسيكوند ليزر", "بيكو ليزر", "ليزر البيكو"),
            misspellings = listOf("Pico second laser", "picosecond-laser"),
            treatmentIds = listOf("picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_nanosecond_laser",
            englishCanonical = "Nanosecond laser",
            category = "modality",
            prohibitedArabicTranslations = listOf("ليزر النانو ثانية", "ليزر النانوثانية"),
            prohibitedTransliterations = listOf("نانوسيكوند ليزر", "نانو ليزر"),
            treatmentIds = listOf("picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_fractional_laser",
            englishCanonical = "Fractional laser",
            category = "modality",
            prohibitedArabicTranslations = listOf("الليزر الجزئي", "الليزر التجزيئي", "ليزر مجزأ", "الليزر المجزأ"),
            prohibitedTransliterations = listOf("فراكشنال ليزر", "ليزر فراكشنال", "الفراكشنال ليزر"),
            misspellings = listOf("fractional-laser"),
            treatmentIds = listOf("dermablate", "lasemd", "picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_thulium_laser",
            englishCanonical = "Thulium laser",
            category = "modality",
            prohibitedTransliterations = listOf("ثوليوم ليزر", "ليزر الثوليوم"),
            treatmentIds = listOf("lasemd"),
        ),
        CanonicalMedicalTerm(
            id = "term_photoacoustic",
            englishCanonical = "Photoacoustic",
            category = "physics",
            prohibitedTransliterations = listOf("فوتوأكوستيك", "فوتو اكوستيك"),
            treatmentIds = listOf("picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_photothermal",
            englishCanonical = "Photothermal",
            category = "physics",
            prohibitedTransliterations = listOf("فوتوثيرمال", "فوتو ثيرمال"),
            treatmentIds = listOf("aerolase", "picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_wavelength",
            englishCanonical = "Wavelength",
            category = "physics",
            prohibitedTransliterations = listOf("ويف لينث", "ويفلينث"),
        ),

        // -------------------- Clinical Conditions & Procedures --------------------
        CanonicalMedicalTerm(
            id = "term_pih",
            englishCanonical = "Post-inflammatory hyperpigmentation",
            category = "condition",
            prohibitedArabicTranslations = listOf("فرط التصبغ ما بعد الالتهاب", "فرط التصبغ بعد الالتهاب", "تصبغات ما بعد الالتهاب"),
            allowedAbbreviations = listOf("PIH"),
            misspellings = listOf("post inflammatory hyperpigmentation"),
            treatmentIds = listOf("aerolase", "picostar", "dermablate", "lasemd"),
        ),
        CanonicalMedicalTerm(
            id = "term_pie",
            englishCanonical = "Post-inflammatory erythema",
            category = "condition",
            prohibitedArabicTranslations = listOf("احمرار ما بعد الالتهاب", "حمامي ما بعد الالتهاب"),
            allowedAbbreviations = listOf("PIE"),
            misspellings = listOf("post inflammatory erythema"),
            treatmentIds = listOf("aerolase"),
        ),
        CanonicalMedicalTerm(
            id = "term_hyperpigmentation",
            englishCanonical = "Hyperpigmentation",
            category = "condition",
            prohibitedArabicTranslations = listOf("فرط التصبغ", "فرط التصبغات"),
            prohibitedTransliterations = listOf("هايبر بيغمنتيشن", "هايبر بيجمنتيشن", "هايبر بيغمنتايشن"),
            treatmentIds = listOf("aerolase", "picostar", "lasemd", "dermablate"),
        ),
        CanonicalMedicalTerm(
            id = "term_melasma",
            englishCanonical = "Melasma",
            category = "condition",
            prohibitedArabicTranslations = listOf("الكلف"),
            prohibitedTransliterations = listOf("ميلازما"),
            treatmentIds = listOf("aerolase", "picostar", "lasemd"),
        ),
        CanonicalMedicalTerm(
            id = "term_hyperhidrosis",
            englishCanonical = "Hyperhidrosis",
            category = "condition",
            prohibitedArabicTranslations = listOf("فرط التعرق", "التعرق الزائد", "التعرق الإبطي المفرط"),
            prohibitedTransliterations = listOf("هايبيرهيدروسيس"),
            treatmentIds = listOf("cfu"),
        ),
        CanonicalMedicalTerm(
            id = "term_skin_resurfacing",
            englishCanonical = "Skin resurfacing",
            category = "procedure",
            prohibitedArabicTranslations = listOf("إعادة تسطيح البشرة", "إعادة تسطيح الجلد", "تجديد سطح الجلد الكشطي"),
            prohibitedTransliterations = listOf("سكن ريسيرفسنغ", "ريسيرفسنغ"),
            treatmentIds = listOf("dermablate", "lasemd"),
        ),
        CanonicalMedicalTerm(
            id = "term_microdermabrasion",
            englishCanonical = "Microdermabrasion",
            category = "procedure",
            prohibitedArabicTranslations = listOf("التقشير الكريستالي المجهري", "التقشير المجهري"),
            prohibitedTransliterations = listOf("مايكروديرمابريجن", "ميكروديرمابريجن"),
            misspellings = listOf("micro-dermabrasion", "microderm abrasion"),
            treatmentIds = listOf("diamond_peeling", "diamond_glow"),
        ),

        // -------------------- Skin Anatomy & Biology --------------------
        CanonicalMedicalTerm(
            id = "term_collagen",
            englishCanonical = "Collagen",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("الكولاجين", "كولاجين"),
            prohibitedTransliterations = listOf("كولاچين"),
            treatmentIds = listOf("cfu", "dermablate", "lasemd", "aerolase", "picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_neocollagenesis",
            englishCanonical = "Neocollagenesis",
            category = "anatomy",
            prohibitedTransliterations = listOf("نيوكولاجينيسيس"),
            treatmentIds = listOf("cfu", "dermablate", "lasemd"),
        ),
        CanonicalMedicalTerm(
            id = "term_epidermis",
            englishCanonical = "Epidermis",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("طبقة البشرة السطحية"),
            prohibitedTransliterations = listOf("الإبيديرمس", "إبيديرمس", "ابيديرمس"),
        ),
        CanonicalMedicalTerm(
            id = "term_dermis",
            englishCanonical = "Dermis",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("طبقة الأدمة", "الأدمة"),
            prohibitedTransliterations = listOf("الديرمس", "ديرمس"),
        ),
        CanonicalMedicalTerm(
            id = "term_stratum_corneum",
            englishCanonical = "Stratum corneum",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("الطبقة القرنية"),
            prohibitedTransliterations = listOf("ستراتوم كورنيوم"),
            treatmentIds = listOf("diamond_peeling", "lasemd", "diamond_glow"),
        ),
        CanonicalMedicalTerm(
            id = "term_smas",
            englishCanonical = "SMAS",
            category = "anatomy",
            isBrandProperNoun = true,
            prohibitedArabicTranslations = listOf("طبقة السماس"),
            prohibitedTransliterations = listOf("السماس", "سماس"),
            misspellings = listOf("smas", "Smas"),
            treatmentIds = listOf("cfu"),
        ),
        CanonicalMedicalTerm(
            id = "term_melanin",
            englishCanonical = "Melanin",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("صبغة الميلانين", "الميلانين", "ميلانين"),
        ),
        CanonicalMedicalTerm(
            id = "term_hemoglobin",
            englishCanonical = "Hemoglobin",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("الهيموغلوبين", "هيموغلوبين", "الهيموجلوبين"),
            treatmentIds = listOf("aerolase"),
        ),
        CanonicalMedicalTerm(
            id = "term_sebaceous_glands",
            englishCanonical = "Sebaceous glands",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("الغدد الدهنية"),
            treatmentIds = listOf("aerolase", "diamond_peeling"),
        ),
        CanonicalMedicalTerm(
            id = "term_lymphatic_drainage",
            englishCanonical = "Lymphatic drainage",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("التصريف اللمفاوي", "تصريف لمفاوي"),
            treatmentIds = listOf("diamond_glow", "picostar"),
        ),
        CanonicalMedicalTerm(
            id = "term_micro_channels",
            englishCanonical = "Micro-channels",
            category = "anatomy",
            prohibitedArabicTranslations = listOf("القنوات المجهرية", "قنوات مجهرية"),
            treatmentIds = listOf("lasemd"),
        ),
        CanonicalMedicalTerm(
            id = "term_fitzpatrick",
            englishCanonical = "Fitzpatrick",
            category = "clinical",
            isBrandProperNoun = true,
            prohibitedTransliterations = listOf("فيتزباتريك", "فتزباتريك"),
        ),
    )

    private val diacriticsRegex = Regex("[\\u064B-\\u0652\\u0640]")
    private val nmRegex = Regex("(\\d+)\\s*(?:نانومتر|نانو متر|ن\\.م)")
    private val usRegex = Regex("(\\d+)\\s*(?:مايكروثانية|ميكروثانية|ميكرو ثانية)")

    /**
     * All canonical English medical terms that should retain English spelling in prompts and validation.
     */
    fun canonicalEnglishList(treatmentId: String? = null, extraUserTerms: List<TerminologyEntry> = emptyList()): List<String> {
        val core = builtInTerms.filter {
            it.mandatoryEnglish && (treatmentId == null || it.treatmentIds.isEmpty() || treatmentId in it.treatmentIds)
        }.map { it.englishCanonical }
        val generalClinical = listOf(
            "PicoStar", "Aerolase", "CFU Èlife", "HIFU", "Nd:YAG", "Er:YAG",
            "Picosecond laser", "Fractional laser", "Melasma", "Hyperpigmentation",
            "Post-inflammatory hyperpigmentation", "Hyperhidrosis", "Collagen",
            "Epidermis", "Dermis", "SMAS", "Skin resurfacing", "Microdermabrasion",
        )
        val user = extraUserTerms.map { it.english.trim() }.filter { it.isNotBlank() }
        return (core + generalClinical + user).distinctBy { it.lowercase() }
    }

    fun promptBlock(treatmentId: String?, extraUserTerms: List<TerminologyEntry> = emptyList()): String {
        val terms = canonicalEnglishList(treatmentId, extraUserTerms)
        return buildString {
            appendLine("MANDATORY ENGLISH MEDICAL TERMINOLOGY (strict rule for BOTH English and Iraqi Arabic reels):")
            appendLine("- ALWAYS write medical, anatomical, clinical, and device terms in English Latin characters—NEVER translate or transliterate them into Arabic script (e.g., write 'PicoStar' not 'بيكوستار', 'Collagen' not 'كولاجين', 'Melasma' not 'الكلف', 'HIFU' not 'هايفو', 'SMAS' not 'سماس', 'Hyperpigmentation' not 'فرط التصبغ').")
            appendLine("- Preserve natural spoken Iraqi Arabic sentence grammar around English terms, e.g.:")
            appendLine("  \"بـ PicoStar نستخدم Picosecond laser، بس اختيار الـ Wavelength يعتمد على نوع الـ Pigmentation، مو كل التصبغات نتعامل وياها بنفس الطريقة.\"")
            appendLine("- Required canonical English terms for this topic/glossary: ${terms.joinToString(", ")}")
        }
    }

    /**
     * Enforces canonical English medical terminology across text by:
     * 1. Converting Arabic units attached to numbers (`1064 نانومتر` -> `1064 nm`, `650 مايكروثانية` -> `650 microseconds`).
     * 2. Replacing prohibited Arabic medical translations/transliterations with canonical English
     *    while keeping Arabic grammatical prefixes (`و`, `ب`, `ل`, `الـ`) natural.
     * 3. Standardizing Latin misspellings & casing to canonical clinical forms.
     */
    fun enforceEnglishMedicalTerms(
        raw: String,
        userTerms: List<TerminologyEntry> = emptyList(),
    ): NormalizedText {
        if (raw.isBlank()) return NormalizedText(raw, emptyList())
        var s = raw
        val issues = mutableListOf<String>()

        // Normalize metric units after digits
        val afterNm = nmRegex.replace(s) { "${it.groupValues[1]} nm" }
        if (afterNm != s) {
            s = afterNm
            issues += "standardized wavelength units to nm"
        }
        val afterUs = usRegex.replace(s) { "${it.groupValues[1]} microseconds" }
        if (afterUs != s) {
            s = afterUs
            issues += "standardized pulse-width units to microseconds"
        }

        // Build lookup table sorted longest-first so compound terms match before shorter components
        val arabicRules = mutableListOf<Pair<String, CanonicalMedicalTerm>>()
        for (term in builtInTerms) {
            if (!term.mandatoryEnglish) continue
            for (ar in term.allProhibitedArabic) {
                arabicRules += ar to term
            }
        }
        for (u in userTerms) {
            val eng = u.english.trim()
            if (eng.isBlank()) continue
            val synthetic = CanonicalMedicalTerm(
                id = "user_${u.id}",
                englishCanonical = eng,
                category = "user",
                isBrandProperNoun = eng.firstOrNull()?.isUpperCase() == true,
                prohibitedArabicTranslations = listOfNotNull(u.arabic.trim().takeIf { it.isNotBlank() }),
                misspellings = u.misrecognitions.filter { it.isNotBlank() },
            )
            synthetic.prohibitedArabicTranslations.forEach { arabicRules += it to synthetic }
            u.misrecognitions.filter { it.any { c -> c in '\u0600'..'\u06FF' } }.forEach {
                arabicRules += it to synthetic
            }
        }

        for ((arTerm, meta) in arabicRules.sortedByDescending { it.first.length }) {
            val cleanVariant = diacriticsRegex.replace(arTerm.trim(), "")
            if (cleanVariant.isBlank()) continue
            val bareVariant = if (cleanVariant.startsWith("ال") && cleanVariant.length > 3) cleanVariant.drop(2) else cleanVariant
            val escapedBare = Regex.escape(bareVariant)
            // Match optional conjunction (و/ب/ف) + optional article/preposition (لل/ال/ل) + stem
            val pattern = Regex(
                "(?<![\\p{L}\\p{N}])(?:([وبف])?(?:(لل)|(ال)|(ل))|([وبف]))?(?:${Regex.escape(cleanVariant)}|$escapedBare)(?![\\p{L}\\p{N}])"
            )
            val next = pattern.replace(s) { m ->
                val conj = m.groups[1]?.value ?: m.groups[5]?.value.orEmpty()
                val isLil = !m.groups[2]?.value.isNullOrEmpty()
                val isAlGroup = !m.groups[3]?.value.isNullOrEmpty()
                val isLi = !m.groups[4]?.value.isNullOrEmpty()
                val isPlainClean = conj.isEmpty() && !isLil && !isAlGroup && !isLi &&
                    cleanVariant.startsWith("ال") && m.value.startsWith("ال")
                val hasDefinite = isLil || isAlGroup || isPlainClean
                when {
                    meta.isBrandProperNoun -> {
                        when {
                            isLil || isLi -> "${conj}لـ ${meta.englishCanonical}"
                            conj == "و" || conj == "ف" -> "$conj ${meta.englishCanonical}"
                            conj == "ب" -> "بـ ${meta.englishCanonical}"
                            else -> meta.englishCanonical
                        }
                    }
                    hasDefinite -> {
                        when {
                            isLil -> "${conj}للـ ${meta.englishCanonical}"
                            conj == "ب" -> "بالـ ${meta.englishCanonical}"
                            conj == "و" || conj == "ف" -> "${conj}الـ ${meta.englishCanonical}"
                            else -> "الـ ${meta.englishCanonical}"
                        }
                    }
                    else -> {
                        when {
                            isLi -> "${conj}لـ ${meta.englishCanonical}"
                            conj == "و" || conj == "ف" -> "$conj ${meta.englishCanonical}"
                            conj == "ب" -> "بـ ${meta.englishCanonical}"
                            else -> meta.englishCanonical
                        }
                    }
                }
            }
            if (next != s) {
                s = next
                issues += "enforced English medical term '${meta.englishCanonical}'"
            }
        }

        // Standardize Latin misspellings & casing for mandatory canonical terms
        val latinRules = (builtInTerms.flatMap { term ->
            if (!term.mandatoryEnglish) emptyList()
            else (listOf(term.englishCanonical) + term.misspellings).map { it to term.englishCanonical }
        } + userTerms.filter { it.english.isNotBlank() }.flatMap { u ->
            (listOf(u.english.trim()) + u.misrecognitions.filter { it.any { c -> c in 'A'..'Z' || c in 'a'..'z' } })
                .map { it.trim() to u.english.trim() }
        }).distinctBy { it.first.lowercase() }.sortedByDescending { it.first.length }

        for ((variant, canonical) in latinRules) {
            if (variant.isBlank()) continue
            val re = Regex("(?<![A-Za-z0-9])" + Regex.escape(variant) + "(?![A-Za-z0-9])", RegexOption.IGNORE_CASE)
            val next = re.replace(s) { m ->
                // Preserve exact case if already identical
                if (m.value == canonical) m.value else canonical
            }
            if (next != s) {
                s = next
                issues += "canonicalized '$canonical'"
            }
        }

        s = s.replace(Regex(" {2,}"), " ").trim()
        return NormalizedText(s, issues.distinct())
    }

    /**
     * Validates text for remaining prohibited Arabic translations/transliterations or Latin
     * misspellings of mandatory English medical terminology.
     */
    fun validate(text: String, userTerms: List<TerminologyEntry> = emptyList()): List<String> {
        if (text.isBlank()) return emptyList()
        val errors = mutableListOf<String>()
        val allTerms = builtInTerms.filter { it.mandatoryEnglish }
        for (term in allTerms) {
            for (ar in term.allProhibitedArabic) {
                val bare = if (ar.startsWith("ال") && ar.length > 3) ar.drop(2) else ar
                val re = Regex("(?<![\\p{L}\\p{N}])(?:[وبف]?(?:لل|ال|ل)|[وبف])?(?:${Regex.escape(ar)}|${Regex.escape(bare)})(?![\\p{L}\\p{N}])")
                if (re.containsMatchIn(text)) {
                    errors += "Medical term must be in English (${term.englishCanonical}), not Arabic '$ar'"
                    break
                }
            }
            for (miss in term.misspellings) {
                if (miss.equals(term.englishCanonical, ignoreCase = false)) continue
                val re = Regex("(?<![A-Za-z0-9])" + Regex.escape(miss) + "(?![A-Za-z0-9])")
                if (re.containsMatchIn(text)) {
                    errors += "Use canonical English spelling '${term.englishCanonical}' instead of '$miss'"
                    break
                }
            }
        }
        for (u in userTerms) {
            val eng = u.english.trim()
            if (eng.isBlank()) continue
            val ar = u.arabic.trim()
            if (ar.isNotBlank() && Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(ar) + "(?![\\p{L}\\p{N}])").containsMatchIn(text)) {
                errors += "User glossary term must be in English ($eng), not '$ar'"
            }
        }
        return errors.distinct()
    }
}
