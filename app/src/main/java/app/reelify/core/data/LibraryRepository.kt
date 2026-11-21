package app.reelify.core.data

import app.reelify.core.database.LibraryDao
import app.reelify.core.database.TerminologyEntity
import app.reelify.core.database.TreatmentEntity
import app.reelify.core.model.TerminologyEntry
import app.reelify.core.model.Treatment
import app.reelify.core.util.AppJson
import app.reelify.core.util.Clock
import app.reelify.core.util.decodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(private val dao: LibraryDao, private val clock: Clock = Clock.System) {

    /**
     * Inserts new built-in treatments/terminology AND performs non-destructive versioned upgrades
     * of existing built-in treatments.
     *
     * Rules:
     *  - User-created treatments (builtIn = false) are never modified or deleted.
     *  - Any field on a built-in treatment that the user previously modified (differs from v1 seed
     *    or appears in [Treatment.userEditedFields]) is preserved intact.
     *  - Only unchanged built-in fields receive newly verified seed knowledge.
     */
    suspend fun seedIfNeeded() {
        dao.insertTreatmentsIfAbsent(TreatmentSeed.treatments.map { it.toEntity() })

        for (latest in TreatmentSeed.treatments) {
            val existingEntity = dao.getTreatment(latest.id) ?: continue
            val existing = existingEntity.toDomain() ?: continue
            if (!existing.builtIn || existing.seedVersion >= TreatmentSeed.CURRENT_SEED_VERSION) continue

            val baseline = TreatmentSeed.v1Treatments[latest.id] ?: latest
            val merged = mergeUpgrade(existing, baseline, latest)
            dao.upsertTreatment(merged.toEntity())
        }

        if (dao.terminologyCount() == 0) {
            dao.upsertTerminology(TreatmentSeed.terminology.map { it.toEntity() })
        } else {
            val existingTerms = dao.getTerminology().mapNotNull { it.toDomain() }.associateBy { it.id }
            val additions = TreatmentSeed.terminology.filter { it.id !in existingTerms }
            // Non-destructively upgrade built-in terminology rows from v2.x Arabic transliterations to v2.2 English-only policy.
            val upgrades = TreatmentSeed.terminology.mapNotNull { latest ->
                val cur = existingTerms[latest.id] ?: return@mapNotNull null
                if (cur.arabic.isNotBlank() && latest.arabic.isBlank()) {
                    latest.copy(
                        misrecognitions = (cur.misrecognitions + cur.arabic + latest.misrecognitions)
                            .filter { it.isNotBlank() }
                            .distinct(),
                    )
                } else null
            }
            val changed = additions + upgrades
            if (changed.isNotEmpty()) dao.upsertTerminology(changed.map { it.toEntity() })
        }
    }

    internal fun mergeUpgrade(existing: Treatment, baseline: Treatment, latest: Treatment): Treatment {
        val edited = existing.userEditedFields
        fun <T> pick(field: String, cur: T, base: T, next: T): T =
            if (field in edited || cur != base) cur else next

        val newNeedsConfig = if ("needsConfiguration" in edited || existing.needsConfiguration != baseline.needsConfiguration) {
            existing.needsConfiguration
        } else {
            latest.needsConfiguration
        }

        return existing.copy(
            name = pick("name", existing.name, baseline.name, latest.name),
            arabicName = pick("arabicName", existing.arabicName, baseline.arabicName, latest.arabicName),
            alternativeNames = pick("alternativeNames", existing.alternativeNames, baseline.alternativeNames, latest.alternativeNames),
            description = pick("description", existing.description, baseline.description, latest.description),
            approvedIndications = pick("approvedIndications", existing.approvedIndications, baseline.approvedIndications, latest.approvedIndications),
            talkingPoints = pick("talkingPoints", existing.talkingPoints, baseline.talkingPoints, latest.talkingPoints),
            precautions = pick("precautions", existing.precautions, baseline.precautions, latest.precautions),
            preferredTerminology = pick("preferredTerminology", existing.preferredTerminology, baseline.preferredTerminology, latest.preferredTerminology),
            preferredHooks = pick("preferredHooks", existing.preferredHooks, baseline.preferredHooks, latest.preferredHooks),
            commonQuestions = pick("commonQuestions", existing.commonQuestions, baseline.commonQuestions, latest.commonQuestions),
            forbiddenClaims = pick("forbiddenClaims", existing.forbiddenClaims, baseline.forbiddenClaims, latest.forbiddenClaims),
            brollSuggestions = pick("brollSuggestions", existing.brollSuggestions, baseline.brollSuggestions, latest.brollSuggestions),
            ctaStyle = pick("ctaStyle", existing.ctaStyle, baseline.ctaStyle, latest.ctaStyle),
            referenceNotes = pick("referenceNotes", existing.referenceNotes, baseline.referenceNotes, latest.referenceNotes),
            needsConfiguration = newNeedsConfig,
            manufacturer = pick("manufacturer", existing.manufacturer, baseline.manufacturer, latest.manufacturer),
            deviceModel = pick("deviceModel", existing.deviceModel, baseline.deviceModel, latest.deviceModel),
            technologyType = pick("technologyType", existing.technologyType, baseline.technologyType, latest.technologyType),
            identityConfirmed = pick("identityConfirmed", existing.identityConfirmed, baseline.identityConfirmed, latest.identityConfirmed),
            linkedReferenceProfileId = pick("linkedReferenceProfileId", existing.linkedReferenceProfileId, baseline.linkedReferenceProfileId, latest.linkedReferenceProfileId),
            seedVersion = latest.seedVersion,
        )
    }

    /**
     * Explicit clinic confirmation mapping the "Cleopatra" service entry to the sourced
     * DiamondGlow® 3-in-1 profile while keeping the clinic's service name "Cleopatra".
     */
    suspend fun confirmCleopatraAsDiamondGlow(): Treatment? {
        val cleo = getTreatment("cleopatra") ?: return null
        val dg = TreatmentSeed.treatments.firstOrNull { it.id == "diamond_glow" } ?: return null
        val updated = cleo.copy(
            manufacturer = dg.manufacturer,
            deviceModel = dg.deviceModel,
            technologyType = dg.technologyType,
            description = dg.description,
            approvedIndications = dg.approvedIndications,
            talkingPoints = dg.talkingPoints,
            precautions = dg.precautions,
            preferredTerminology = (cleo.preferredTerminology + dg.preferredTerminology).distinct(),
            preferredHooks = dg.preferredHooks,
            commonQuestions = dg.commonQuestions,
            brollSuggestions = dg.brollSuggestions,
            ctaStyle = dg.ctaStyle,
            referenceNotes = dg.referenceNotes,
            needsConfiguration = false,
            identityConfirmed = true,
            linkedReferenceProfileId = "diamond_glow",
            userEditedFields = cleo.userEditedFields + setOf("identityConfirmed", "linkedReferenceProfileId", "needsConfiguration"),
            updatedAt = clock.now(),
        )
        dao.upsertTreatment(updated.toEntity())
        return updated
    }

    fun observeTreatments(): Flow<List<Treatment>> = dao.observeTreatments().map { l -> l.mapNotNull { it.toDomain() } }

    suspend fun getTreatment(id: String): Treatment? = dao.getTreatment(id)?.toDomain()

    suspend fun saveTreatment(t: Treatment) {
        val seed = TreatmentSeed.treatments.firstOrNull { it.id == t.id }
        val edited = if (seed == null) t.userEditedFields else detectEditedFields(t, seed) + t.userEditedFields
        dao.upsertTreatment(t.copy(userEditedFields = edited, updatedAt = clock.now()).toEntity())
    }

    private fun detectEditedFields(t: Treatment, seed: Treatment): Set<String> = buildSet {
        if (t.name != seed.name) add("name")
        if (t.arabicName != seed.arabicName) add("arabicName")
        if (t.alternativeNames != seed.alternativeNames) add("alternativeNames")
        if (t.description != seed.description) add("description")
        if (t.approvedIndications != seed.approvedIndications) add("approvedIndications")
        if (t.talkingPoints != seed.talkingPoints) add("talkingPoints")
        if (t.precautions != seed.precautions) add("precautions")
        if (t.preferredTerminology != seed.preferredTerminology) add("preferredTerminology")
        if (t.preferredHooks != seed.preferredHooks) add("preferredHooks")
        if (t.commonQuestions != seed.commonQuestions) add("commonQuestions")
        if (t.forbiddenClaims != seed.forbiddenClaims) add("forbiddenClaims")
        if (t.brollSuggestions != seed.brollSuggestions) add("brollSuggestions")
        if (t.ctaStyle != seed.ctaStyle) add("ctaStyle")
        if (t.referenceNotes != seed.referenceNotes) add("referenceNotes")
        if (t.needsConfiguration != seed.needsConfiguration) add("needsConfiguration")
    }

    suspend fun deleteTreatment(id: String) = dao.deleteCustomTreatment(id)

    /** Restores the shipped profile for a built-in treatment. */
    suspend fun resetTreatment(id: String) {
        TreatmentSeed.treatments.firstOrNull { it.id == id }?.let { dao.upsertTreatment(it.toEntity()) }
    }

    fun observeTerminology(): Flow<List<TerminologyEntry>> = dao.observeTerminology().map { l -> l.mapNotNull { it.toDomain() } }

    suspend fun terminology(): List<TerminologyEntry> = dao.getTerminology().mapNotNull { it.toDomain() }

    suspend fun saveTerm(t: TerminologyEntry) = dao.upsertTerminology(listOf(t.toEntity()))

    suspend fun deleteTerm(id: String) = dao.deleteTerminology(id)

    private fun Treatment.toEntity() = TreatmentEntity(id, name, sortOrder, builtIn, AppJson.encodeToString(Treatment.serializer(), this))
    private fun TreatmentEntity.toDomain() = AppJson.decodeOrNull<Treatment>(dataJson)
    private fun TerminologyEntry.toEntity() = TerminologyEntity(id, english, AppJson.encodeToString(TerminologyEntry.serializer(), this))
    private fun TerminologyEntity.toDomain() = AppJson.decodeOrNull<TerminologyEntry>(dataJson)
}
