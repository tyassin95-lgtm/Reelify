package app.reelify.core.data

import app.reelify.core.model.TerminologyEntry
import app.reelify.core.model.Treatment

/**
 * Initial treatment library. Facts are deliberately conservative. Where the exact device or
 * protocol behind a name is ambiguous (CFU, Cleopatra) the profile is marked
 * [Treatment.needsConfiguration] and the AI is instructed not to invent capabilities.
 * The clinic owner should review and edit every profile in Studio → Treatments.
 */
object TreatmentSeed {
    private val universalForbidden = listOf(
        "guaranteed results", "permanent results", "100% effective", "no risk", "no side effects",
        "suitable for everyone", "instant results", "painless for everyone", "cure",
    )

    val treatments: List<Treatment> = listOf(
        Treatment(
            id = "aerolase", name = "Aerolase", arabicName = "إيروليز", sortOrder = 0, accentColor = 0xFFE11D48,
            alternativeNames = listOf("Aerolase Neo", "650-microsecond laser"),
            description = "A 1064 nm Nd:YAG laser platform using a 650-microsecond pulse. Treatment suitability depends on an in-person skin assessment.",
            approvedIndications = listOf(
                "May be considered for certain inflammatory acne concerns (assessment required)",
                "May be considered for some redness and pigmentation concerns (assessment required)",
            ),
            talkingPoints = listOf(
                "Short pulse duration designed to deliver energy efficiently",
                "Many patients describe the sensation as tolerable; experience varies",
                "Number of sessions depends on the individual case",
                "A consultation determines whether it is appropriate for your skin",
            ),
            precautions = listOf("Not every skin concern or skin type is suitable — assessment first", "Results and session count vary between individuals"),
            preferredTerminology = listOf("Aerolase", "إيروليز"),
            preferredHooks = listOf("Is acne laser treatment right for everyone?", "Why a skin consultation comes before any laser"),
            commonQuestions = listOf("How many sessions will I need?", "Does it hurt?", "Is there downtime?"),
            forbiddenClaims = universalForbidden,
            brollSuggestions = listOf("Device handpiece close-up", "Treatment on consenting patient (no identifiable face)", "Consultation conversation"),
            ctaStyle = "Invite viewers to book a skin evaluation",
        ),
        Treatment(
            id = "picostar", name = "Picostar", arabicName = "بيكوستار", sortOrder = 1, accentColor = 0xFF8B5CF6,
            alternativeNames = listOf("Pico laser", "Picosecond laser"),
            description = "Picosecond-class laser. Confirm the exact manufacturer/model and cleared indications in this profile before publishing specific claims.",
            approvedIndications = listOf("Configure: pigmentation-related indications confirmed for your device"),
            talkingPoints = listOf("Picosecond lasers deliver very short pulses", "Suitability depends on pigment type and skin assessment"),
            precautions = listOf("Configure device-specific precautions"),
            preferredTerminology = listOf("Picostar", "بيكوستار"),
            forbiddenClaims = universalForbidden,
            brollSuggestions = listOf("Device screen settings close-up", "Handpiece close-up"),
            ctaStyle = "Book a consultation to assess pigmentation",
            needsConfiguration = true,
        ),
        Treatment(
            id = "dermablate", name = "Dermablate", arabicName = "ديرمابليت", sortOrder = 2, accentColor = 0xFFF59E0B,
            alternativeNames = listOf("Er:YAG laser", "Erbium laser resurfacing"),
            description = "Erbium:YAG (2940 nm) ablative laser used for resurfacing procedures. Downtime and suitability depend on treatment depth and assessment.",
            approvedIndications = listOf("May be considered for selected texture, scar or resurfacing goals (assessment required)"),
            talkingPoints = listOf("Ablative resurfacing removes thin skin layers in a controlled way", "Downtime depends on the treatment depth", "Aftercare matters for healing"),
            precautions = listOf("Downtime varies", "Not suitable for every skin type or condition", "Strict aftercare and sun protection"),
            preferredTerminology = listOf("Dermablate", "ديرمابليت"),
            forbiddenClaims = universalForbidden,
            brollSuggestions = listOf("Device close-up", "Aftercare products flat-lay"),
            ctaStyle = "Encourage an assessment to discuss expectations and downtime",
        ),
        Treatment(
            id = "cfu", name = "CFU", arabicName = "سي إف يو", sortOrder = 3, accentColor = 0xFF06B6D4,
            description = "Needs configuration: specify exactly what CFU refers to in your clinic (device, manufacturer, mechanism, indications). The AI will not invent capabilities.",
            preferredTerminology = listOf("CFU"),
            forbiddenClaims = universalForbidden,
            needsConfiguration = true,
        ),
        Treatment(
            id = "lasemd", name = "LaseMD", arabicName = "ليز إم دي", sortOrder = 4, accentColor = 0xFF10B981,
            alternativeNames = listOf("LaseMD Ultra", "1927 nm thulium laser"),
            description = "Non-ablative fractional thulium laser (1927 nm) used for skin tone and texture protocols. Confirm the exact protocol you offer.",
            approvedIndications = listOf("May be considered for uneven tone, some pigmentation and texture concerns (assessment required)"),
            talkingPoints = listOf("Fractional, non-ablative approach", "Often planned as a series of sessions", "Recovery is typically short but varies"),
            precautions = listOf("Sun protection afterwards", "Suitability depends on assessment"),
            preferredTerminology = listOf("LaseMD", "ليز إم دي"),
            forbiddenClaims = universalForbidden,
            brollSuggestions = listOf("Rolling handpiece close-up", "Skin prep steps"),
            ctaStyle = "Book a consultation to plan a series",
        ),
        Treatment(
            id = "cleopatra", name = "Cleopatra", arabicName = "كليوباترا", sortOrder = 5, accentColor = 0xFFD4AF37,
            description = "Needs configuration: describe what the Cleopatra treatment involves in your clinic (protocol, products/devices, indications). The AI will not invent details.",
            preferredTerminology = listOf("Cleopatra", "كليوباترا"),
            forbiddenClaims = universalForbidden,
            needsConfiguration = true,
        ),
        Treatment(
            id = "diamond_peeling", name = "Diamond Peeling", arabicName = "التقشير الماسي", sortOrder = 6, accentColor = 0xFF60A5FA,
            alternativeNames = listOf("Diamond microdermabrasion"),
            description = "Mechanical exfoliation (microdermabrasion) using a diamond-tipped handpiece with suction.",
            approvedIndications = listOf("Superficial exfoliation as part of a skin-care routine"),
            talkingPoints = listOf("Gentle mechanical exfoliation of the outer layer", "Often combined with other treatments", "Comfort and results vary"),
            precautions = listOf("Not suitable over active inflamed lesions", "Sun protection afterwards"),
            preferredTerminology = listOf("Diamond Peeling", "التقشير الماسي"),
            forbiddenClaims = universalForbidden,
            brollSuggestions = listOf("Diamond tip close-up", "Gliding motion on skin (consenting patient)"),
            ctaStyle = "Invite viewers to ask about a skin routine plan",
        ),
        Treatment(
            id = Treatment.GENERAL_ID, name = "General / Custom", arabicName = "موضوع عام", sortOrder = 99, accentColor = 0xFF71717A,
            description = "General skin education or a custom topic not tied to a specific device.",
            forbiddenClaims = universalForbidden,
        ),
    )

    val terminology: List<TerminologyEntry> = listOf(
        TerminologyEntry("t-aerolase", "Aerolase", "إيروليز", "AIR-oh-laze", listOf("aero lase", "airolase", "aero laze", "ايرو ليز", "ايروليز", "إيرو ليز")),
        TerminologyEntry("t-picostar", "Picostar", "بيكوستار", "PEE-ko-star", listOf("pico star", "piko star", "بيكو ستار")),
        TerminologyEntry("t-dermablate", "Dermablate", "ديرمابليت", "DER-ma-blate", listOf("derma blate", "derma blade", "ديرما بليت")),
        TerminologyEntry("t-lasemd", "LaseMD", "ليز إم دي", "laze em dee", listOf("laser md", "lase md", "lays md", "ليزر ام دي", "ليز ام دي")),
        TerminologyEntry("t-cfu", "CFU", "سي إف يو", "see-ef-you", listOf("c f u", "see if you", "سي اف يو")),
        TerminologyEntry("t-cleopatra", "Cleopatra", "كليوباترا", "klee-oh-PAT-ra", listOf("cleo patra", "كليو باترا")),
        TerminologyEntry("t-diamond", "Diamond Peeling", "التقشير الماسي", "", listOf("diamond peel", "dimond peeling", "دايموند بيلنك")),
    )
}
