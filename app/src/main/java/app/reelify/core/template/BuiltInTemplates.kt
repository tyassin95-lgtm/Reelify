package app.reelify.core.template

import app.reelify.core.model.BeautyPreset
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.ColorLook
import app.reelify.core.model.TransitionType

/**
 * Reelify's built-in production formats. These are deliberately fixed: the same template always
 * produces the same shot plan, camera behaviour and edit, so daily reels look consistently branded.
 *
 * VERSIONING: bump a template's [ReelTemplate.version] whenever anything in it changes. Projects
 * keep the snapshot they were created with, so existing reels never change unexpectedly.
 */
object BuiltInTemplates {

    // ------------------------------------------------------------------ shared presets

    val cameraPresets: Map<String, CameraPreset> = listOf(
        CameraPreset("talking_front", CameraFacing.FRONT, teleprompter = true, beautyByDefault = true, faceGuide = true, focusAssist = false,
            exposureHint = LocalizedText("Face a window or soft light; avoid a bright window behind you.", "خلّي الضوء قدّام وجهك، مو الشباك وراك.")),
        CameraPreset("talking_front_close", CameraFacing.FRONT, teleprompter = true, beautyByDefault = true, faceGuide = true, focusAssist = false,
            exposureHint = LocalizedText("Same light as shot 1 so the reel matches.", "نفس إضاءة اللقطة الأولى حتى يطلع الفيديو متناسق.")),
        CameraPreset("broll_rear", CameraFacing.BACK, teleprompter = false, beautyByDefault = false, faceGuide = false, focusAssist = true,
            exposureHint = LocalizedText("Tap the treatment area to focus and lock exposure.", "اضغط على منطقة العلاج حتى يثبت الفوكس والإضاءة.")),
        CameraPreset("device_rear", CameraFacing.BACK, teleprompter = false, beautyByDefault = false, faceGuide = false, focusAssist = true,
            exposureHint = LocalizedText("Move slowly; tap the device to focus.", "حرّك التلفون بهدوء واضغط على الجهاز للفوكس.")),
    ).associateBy { it.id }

    val teleprompterPresets: Map<String, TeleprompterPreset> = listOf(
        TeleprompterPreset("near_lens", enabled = true, position = TeleprompterPosition.NEAR_LENS, visibleLines = 3, sizeSp = 24f),
        TeleprompterPreset("near_lens_large", enabled = true, position = TeleprompterPosition.NEAR_LENS, visibleLines = 3, sizeSp = 27f),
        TeleprompterPreset("off", enabled = false),
    ).associateBy { it.id }

    val musicPresets: Map<String, MusicPreset> = listOf(
        MusicPreset("calm_premium", "elegant_silk", 0.24f),
        MusicPreset("clinical_soft", "calm_clinical", 0.22f),
        MusicPreset("modern_light", "modern_pulse", 0.20f, duckDepth = 0.65f),
        MusicPreset("cinematic_warm", "cinematic_rise", 0.24f),
        MusicPreset("luxury", "luxury_velvet", 0.23f),
        MusicPreset("minimal", "minimal_air", 0.22f),
    ).associateBy { it.id }

    val transitionPresets: Map<String, TransitionPreset> = listOf(
        TransitionPreset("subtle_zoom", TransitionType.SUBTLE_ZOOM, TransitionType.CROSSFADE, 380),
        TransitionPreset("clean_cut", TransitionType.CUT, TransitionType.CUT, 300),
        TransitionPreset("soft_dissolve", TransitionType.CROSSFADE, TransitionType.CROSSFADE, 450),
        TransitionPreset("light_flash", TransitionType.LIGHT_FLASH, TransitionType.CUT, 360),
        TransitionPreset("soft_blur", TransitionType.SOFT_BLUR, TransitionType.CROSSFADE, 420),
    ).associateBy { it.id }

    val brandingPresets: Map<String, BrandingPreset> = listOf(
        BrandingPreset("signature", watermark = true, lowerThird = true, ctaOverlay = true, logoReveal = true, ctaDurationMs = 3500),
        BrandingPreset("minimal", watermark = true, lowerThird = false, ctaOverlay = true, logoReveal = false, ctaDurationMs = 3000),
        BrandingPreset("clinical", watermark = true, lowerThird = true, ctaOverlay = true, logoReveal = false, ctaDurationMs = 3500),
    ).associateBy { it.id }

    // ------------------------------------------------------------------ reusable shot texts

    private val eyeLevel = LocalizedText("Look into the lens. Phone at eye level, an arm's length away.", "باوع على العدسة. التلفون بمستوى عينك وعلى بُعد ذراع.")
    private val softFront = LocalizedText("Soft light in front of you, plain background behind.", "ضوء ناعم قدّامك وخلفية بسيطة وراك.")
    private val warmClose = LocalizedText("Come a little closer. Calm, confident, warm close.", "تقرّب شوية. ختام هادي وواثق ودافي.")
    private val tighter = LocalizedText("Slightly tighter framing — chest up, eyes on the lens.", "لقطة أقرب شوية — من الصدر وفوك، وعيونك على العدسة.")
    private val conversational = LocalizedText("Speak like you're answering one patient, not reading.", "احجي كأنك تجاوب مراجع واحد، مو تقرا.")
    private val brollSteady = LocalizedText("Rear camera. Slow, steady movement over the device or treatment.", "الكاميرا الخلفية. حركة هادية وثابتة على الجهاز أو العلاج.")
    private val brollLight = LocalizedText("Bright, even clinic light; no patient faces unless consent covers it.", "إضاءة العيادة واضحة ومتساوية؛ بدون وجوه مراجعين إلا بموافقة.")
    private val voiceDelivery = LocalizedText("Record the voice in a quiet room, phone near your mouth.", "سجّل الصوت بغرفة هادية والتلفون قريب من حلكك.")

    private fun talking(index: Int, role: ShotRole, framing: FramingPreset, cam: String, instruction: LocalizedText, brief: String, out: TransitionType, delivery: LocalizedText = conversational, sfx: String? = null, broll: Boolean = false, prompter: String = "near_lens") =
        ShotRecipe(index, role, MediaRequirement.TALKING_TO_CAMERA, framing, cam, prompter, instruction, softFront, delivery, brief, broll, out, sfx,
            brollHint = if (broll) LocalizedText("Optional: add a short device or clinic cutaway.", "اختياري: ضيف لقطة قصيرة للجهاز أو العيادة.") else null)

    private fun voiceover(index: Int, role: ShotRole, framing: FramingPreset, cam: String, instruction: LocalizedText, brief: String, out: TransitionType, sfx: String? = null) =
        ShotRecipe(index, role, MediaRequirement.VOICEOVER_BROLL, framing, cam, "off", instruction, brollLight, voiceDelivery, brief, false, out, sfx,
            brollHint = LocalizedText("Device close-up, treatment B-roll or clinic ambience.", "لقطة قريبة للجهاز، أو لقطات من العلاج، أو أجواء العيادة."))

    // ------------------------------------------------------------------ templates

    val EXPERT_EXPLAINS = ReelTemplate(
        id = "expert_explains", version = 1,
        name = LocalizedText("Expert Explains", "الخبير يوضّح"),
        description = LocalizedText("Hook to camera, a clean B-roll explanation, then a warm close.", "افتتاحية للكاميرا، شرح فوق لقطات للجهاز، وختام دافي."),
        previewAsset = TemplateArt.EXPERT,
        shots = listOf(
            talking(1, ShotRole.HOOK, FramingPreset.MEDIUM_CLOSE_UP, "talking_front", eyeLevel,
                "Open with the chosen hook, then name the single idea this reel explains.", TransitionType.SUBTLE_ZOOM, sfx = "gentle_shimmer"),
            voiceover(2, ShotRole.EXPLANATION, FramingPreset.DEVICE_MACRO, "device_rear", brollSteady,
                "Explain the idea clearly in plain words over device/treatment footage. One approved fact, no lists of claims.", TransitionType.SUBTLE_ZOOM),
            talking(3, ShotRole.CONCLUSION_CTA, FramingPreset.CLOSE_UP, "talking_front_close", tighter,
                "Give the practical takeaway, mention that suitability depends on assessment, end with a natural CTA.", TransitionType.CUT, delivery = warmClose),
        ),
        captionPresetId = CaptionPresetId.PREMIUM_MINIMAL, musicPresetId = "calm_premium", transitionPresetId = "subtle_zoom", brandingPresetId = "signature",
        colorLook = ColorLook.CLEAN, colorIntensity = 0.7f, talkingBeauty = BeautyPreset.NATURAL, sfxStrategy = SfxStrategy.INTRO_ONLY,
        accent = 0xFFE11D48,
        structureBrief = "Credible clinician teaches ONE concrete mechanism, wavelength/depth parameter, or biological timeline. Shot 1: direct pattern-interrupt hook challenging a common assumption (no generic filler). Shot 2 (B-roll VO): explain HOW the device/protocol works clinically with a verified fact. Shot 3: practical takeaway, qualification + calm CTA.",
    )

    val MYTH_BUSTER = ReelTemplate(
        id = "myth_buster", version = 1,
        name = LocalizedText("Myth Buster", "نصحّح فكرة غلط"),
        description = LocalizedText("Name a common myth, correct it with an approved fact, give the takeaway.", "نذكر فكرة منتشرة غلط، نصححها بمعلومة موثوقة، ونطلع بخلاصة."),
        previewAsset = TemplateArt.MYTH,
        shots = listOf(
            talking(1, ShotRole.MYTH, FramingPreset.MEDIUM_CLOSE_UP, "talking_front",
                LocalizedText("Eye level, centred. Say the myth like you've heard it a hundred times.", "بمستوى العين وبالنص. احجي الفكرة الغلط كأنك سامعها ميت مرة."),
                "State the common misconception as people say it (clearly marked as a myth, never endorsed).", TransitionType.LIGHT_FLASH, sfx = "subtle_impact"),
            talking(2, ShotRole.CORRECTION, FramingPreset.MEDIUM_CLOSE_UP, "talking_front",
                LocalizedText("Same position. Lean in slightly as you correct it.", "نفس المكان. تقرّب شوية وانت تصحح."),
                "Correct it using ONE approved fact from the treatment profile. Calm, not condescending.", TransitionType.SUBTLE_ZOOM, broll = true),
            talking(3, ShotRole.TAKEAWAY, FramingPreset.CLOSE_UP, "talking_front_close", tighter,
                "Give the one thing viewers should remember and a natural CTA.", TransitionType.CUT, delivery = warmClose),
        ),
        captionPresetId = CaptionPresetId.BOLD_ENGAGEMENT, musicPresetId = "modern_light", transitionPresetId = "light_flash", brandingPresetId = "minimal",
        colorLook = ColorLook.BRIGHT_SOCIAL, colorIntensity = 0.55f, talkingBeauty = BeautyPreset.CLEAN_STUDIO, sfxStrategy = SfxStrategy.SECTION_ACCENTS,
        accent = 0xFFF59E0B,
        structureBrief = "Myth → clinical physics correction → patient safeguard. Shot 1: voice the exact misconception clearly and challenge it immediately as FALSE. Shot 2 (B-roll VO): explain the real biology, physics, or risk with an approved fact. Shot 3: clear rule patients remember before booking + CTA.",
    )

    val TREATMENT_SHOWCASE = ReelTemplate(
        id = "treatment_showcase", version = 1,
        name = LocalizedText("Treatment Showcase", "عرض العلاج"),
        description = LocalizedText("Introduce the treatment, show the device in action, explain who it may suit.", "نعرّف بالعلاج، نعرض الجهاز وهو يشتغل، ونوضّح لمن ممكن يناسب."),
        previewAsset = TemplateArt.SHOWCASE,
        shots = listOf(
            talking(1, ShotRole.INTRODUCTION, FramingPreset.MEDIUM, "talking_front",
                LocalizedText("Medium shot with the device visible beside you if possible.", "لقطة متوسطة، والجهاز يبين يمّك إذا تكدر."),
                "Introduce the treatment by name with an inviting hook. No claims yet.", TransitionType.CROSSFADE, sfx = "gentle_shimmer"),
            voiceover(2, ShotRole.SHOWCASE, FramingPreset.TREATMENT_AREA, "broll_rear",
                LocalizedText("Rear camera on the handpiece or treatment area. Smooth and slow.", "الكاميرا الخلفية على قطعة الجهاز أو منطقة العلاج. حركة ناعمة وبطيئة."),
                "Describe what happens during a session in simple, accurate terms from the profile (sensation varies).", TransitionType.CROSSFADE),
            talking(3, ShotRole.SUITABILITY_CTA, FramingPreset.MEDIUM_CLOSE_UP, "talking_front_close",
                LocalizedText("Back to camera, slightly closer than shot 1.", "رجوع للكاميرا، أقرب شوية من اللقطة الأولى."),
                "Explain who MAY benefit and why an assessment matters first, then CTA.", TransitionType.CUT, delivery = warmClose),
        ),
        captionPresetId = CaptionPresetId.PREMIUM_MINIMAL, musicPresetId = "luxury", transitionPresetId = "soft_dissolve", brandingPresetId = "signature",
        colorLook = ColorLook.WARM, colorIntensity = 0.5f, talkingBeauty = BeautyPreset.SOFT_GLOW, sfxStrategy = SfxStrategy.INTRO_ONLY,
        lightOverlay = true, accent = 0xFFD4AF37,
        structureBrief = "Inside-the-room educational walkthrough: Shot 1: hook on what concern or prep step viewers are seeing. Shot 2 (B-roll over procedure footage): step-by-step technique, comfort, calibration, or hygiene detail. Shot 3: honest downtime/aftercare reality, candidacy + CTA.",
    )

    val THREE_THINGS = ReelTemplate(
        id = "three_things", version = 1,
        name = LocalizedText("Three Things to Know", "ثلاث شغلات لازم تعرفها"),
        description = LocalizedText("Three numbered insights, one per shot, each with its own headline.", "ثلاث معلومات مرقّمة، كل وحدة بلقطة وعنوان."),
        previewAsset = TemplateArt.THREE_THINGS,
        shots = listOf(
            talking(1, ShotRole.INSIGHT_1, FramingPreset.MEDIUM_CLOSE_UP, "talking_front", eyeLevel,
                "Hook that promises three things, then deliver insight #1 concisely.", TransitionType.SLIDE, sfx = "text_reveal"),
            talking(2, ShotRole.INSIGHT_2, FramingPreset.MEDIUM_CLOSE_UP, "talking_front",
                LocalizedText("Same framing. Hold up two fingers or glance at the number.", "نفس اللقطة. أشّر برقم اثنين أو باوع عالرقم."),
                "Insight #2. One sentence of explanation.", TransitionType.SLIDE, sfx = "text_reveal", broll = true),
            talking(3, ShotRole.INSIGHT_3_CTA, FramingPreset.CLOSE_UP, "talking_front_close", tighter,
                "Insight #3 (the most important), then a natural CTA.", TransitionType.CUT, sfx = "text_reveal", delivery = warmClose),
        ),
        captionPresetId = CaptionPresetId.BOLD_ENGAGEMENT, musicPresetId = "modern_light", transitionPresetId = "clean_cut", brandingPresetId = "minimal",
        colorLook = ColorLook.CLEAN, colorIntensity = 0.75f, talkingBeauty = BeautyPreset.CLEAN_STUDIO, sfxStrategy = SfxStrategy.SECTION_ACCENTS,
        titlePlacement = TitlePlacement.UPPER_THIRD, accent = 0xFF8B5CF6,
        structureBrief = "Exactly THREE concrete, distinct, numbered takeaways (one per shot; headlines MUST start with 1, 2, 3). Every point must teach a specific parameter, rule, timing factor, or limitation from verified facts, ending Shot 3 with point 3 + CTA.",
    )

    val PATIENT_QUESTION = ReelTemplate(
        id = "patient_question", version = 1,
        name = LocalizedText("Common Patient Question", "سؤال يسألوه المراجعين"),
        description = LocalizedText("Ask the question patients ask, answer it, add the detail that matters.", "نطرح سؤال المراجعين، نجاوب، ونضيف التفصيل المهم."),
        previewAsset = TemplateArt.QUESTION,
        shots = listOf(
            talking(1, ShotRole.QUESTION, FramingPreset.MEDIUM_CLOSE_UP, "talking_front",
                LocalizedText("Eye level. Ask the question as if a patient just asked you.", "بمستوى العين. اسأل السؤال كأن مراجع هسه سألك."),
                "Pose the patient question in their words (hook).", TransitionType.CROSSFADE, sfx = "soft_click"),
            talking(2, ShotRole.ANSWER, FramingPreset.MEDIUM_CLOSE_UP, "talking_front", conversational,
                "Answer concisely and accurately using the treatment profile.", TransitionType.CROSSFADE, broll = true),
            talking(3, ShotRole.QUALIFICATION, FramingPreset.CLOSE_UP, "talking_front_close", tighter,
                "Add the important qualification (it depends on skin/assessment) and conclude with a CTA.", TransitionType.CUT, delivery = warmClose),
        ),
        captionPresetId = CaptionPresetId.CLEAN_CLINICAL, musicPresetId = "clinical_soft", transitionPresetId = "soft_dissolve", brandingPresetId = "clinical",
        colorLook = ColorLook.COOL_CLINICAL, colorIntensity = 0.45f, talkingBeauty = BeautyPreset.CLINICAL_PREMIUM, sfxStrategy = SfxStrategy.INTRO_ONLY,
        pushIn = false, accent = 0xFF06B6D4,
        structureBrief = "Real patient Q&A: Shot 1: ask the blunt clinic question verbatim (sessions, pain, downtime, darker-skin safety). Shot 2 (B-roll VO): direct transparent answer plus the medical reason why. Shot 3: honest qualification on individual factors, when to delay + CTA.",
    )

    val PROBLEM_APPROACH = ReelTemplate(
        id = "problem_approach", version = 1,
        name = LocalizedText("Problem / Approach", "المشكلة والحل"),
        description = LocalizedText("A relatable skin concern, how the treatment may fit, realistic expectations.", "مشكلة بشرة يحسها الكل، شلون العلاج ممكن يفيد، وتوقعات واقعية."),
        previewAsset = TemplateArt.PROBLEM,
        shots = listOf(
            talking(1, ShotRole.CONCERN, FramingPreset.MEDIUM_CLOSE_UP, "talking_front",
                LocalizedText("Eye level, empathetic tone. Describe the concern viewers feel.", "بمستوى العين وبنبرة متفهمة. وصّف المشكلة اللي يحسوها."),
                "Describe a relatable skin concern with empathy (no fear-mongering).", TransitionType.SOFT_BLUR, sfx = "gentle_shimmer"),
            voiceover(2, ShotRole.APPROACH, FramingPreset.DEVICE_MACRO, "device_rear", brollSteady,
                "Explain how the selected treatment MAY fit into an assessment-based plan, using approved facts only.", TransitionType.SOFT_BLUR),
            talking(3, ShotRole.EXPECTATIONS_CTA, FramingPreset.CLOSE_UP, "talking_front_close", tighter,
                "Set realistic expectations (results and sessions vary) and invite an assessment.", TransitionType.CUT, delivery = warmClose),
        ),
        captionPresetId = CaptionPresetId.PREMIUM_MINIMAL, musicPresetId = "cinematic_warm", transitionPresetId = "soft_blur", brandingPresetId = "signature",
        colorLook = ColorLook.CINEMATIC, colorIntensity = 0.5f, talkingBeauty = BeautyPreset.NATURAL, sfxStrategy = SfxStrategy.INTRO_ONLY,
        vignette = 0.3f, accent = 0xFF9D174D,
        structureBrief = "Diagnostic concern → structured clinical approach: Shot 1: name the specific frustrating presentation and why quick fixes miss. Shot 2: explain how proper assessment guides this modality (or when another option fits better). Shot 3: realistic progressive timeline, daily prevention + CTA.",
    )

    val all: List<ReelTemplate> = listOf(EXPERT_EXPLAINS, MYTH_BUSTER, TREATMENT_SHOWCASE, THREE_THINGS, PATIENT_QUESTION, PROBLEM_APPROACH)

    fun byId(id: String?): ReelTemplate? = all.firstOrNull { it.id == id }
}
