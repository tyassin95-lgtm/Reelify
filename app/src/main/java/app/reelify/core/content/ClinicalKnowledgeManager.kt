package app.reelify.core.content

import app.reelify.core.model.Treatment

/**
 * A focused clinical knowledge cluster within a [TreatmentKnowledgePack]. Used by the AI idea
 * engine and creative scriptwriter so each reel explores one specific clinical concept instead of
 * blending unrelated indications together.
 */
data class KnowledgeCluster(
    val clusterId: String,
    val treatmentId: String,
    val topicArea: String,
    val labelEn: String,
    val labelAr: String,
    val facts: List<VerifiedFact>,
    val questions: List<VerifiedQuestion>,
    val sourceIds: List<String>,
)

data class ResearchRefreshResult(
    val treatmentId: String,
    val statusNote: String,
    val latestRetrievalDate: String,
    val sourceTitles: List<String>,
)

/**
 * Owns clinical knowledge organization, provenance verification, and topic-focused cluster
 * selection — intentionally separated from creative scriptwriting.
 */
object ClinicalKnowledgeManager {

    /** Organizes a treatment pack into coherent single-subject clinical clusters. */
    fun clustersFor(pack: TreatmentKnowledgePack): List<KnowledgeCluster> {
        val grouped = pack.allFacts().groupBy { it.topicArea }
        return grouped.entries.map { (area, facts) ->
            val factIds = facts.map { it.id }.toSet()
            val questions = pack.patientQuestions.filter { q -> q.factIds.any { it in factIds } }
            val sources = facts.flatMap { it.sourceIds }.distinct()
            KnowledgeCluster(
                clusterId = "${pack.treatmentId}_cluster_$area",
                treatmentId = pack.treatmentId,
                topicArea = area,
                labelEn = humanizeArea(area),
                labelAr = humanizeArea(area),
                facts = facts,
                questions = questions,
                sourceIds = sources,
            )
        }
    }

    /**
     * Selects up to [count] diverse, underused clinical knowledge clusters for generating fresh AI
     * topic ideas, avoiding clusters dominated by recently used facts.
     */
    fun selectClustersForIdeaGeneration(
        pack: TreatmentKnowledgePack,
        recentFactIds: Set<String> = emptySet(),
        focusClusterArea: String? = null,
        excludeClusterAreas: Set<String> = emptySet(),
        count: Int = 5,
    ): List<KnowledgeCluster> {
        val clusters = clustersFor(pack)
            .filter { it.facts.isNotEmpty() }
            .filterNot { it.topicArea in excludeClusterAreas }
        if (clusters.isEmpty()) return emptyList()

        return clusters.sortedWith(
            compareBy<KnowledgeCluster>(
                { if (focusClusterArea != null && it.topicArea == focusClusterArea) 0 else 1 },
                // Clusters with fewer recently-used facts rise first
                { c -> c.facts.count { it.id in recentFactIds } },
                // Rich mechanism / clinical distinction / misconception clusters ahead of generic areas
                { areaPriority(it.topicArea) },
            ),
        ).take(count)
    }

    /** Returns true when [factOrQuestionId] exists in [pack] and resolves to real [KnowledgeSource] IDs. */
    fun verifyFactProvenance(pack: TreatmentKnowledgePack, factOrQuestionId: String): Boolean {
        val resolved = pack.resolveFactOrQuestion(factOrQuestionId) ?: return false
        if (resolved.sourceIds.any { it in pack.allFacts().map { f -> f.id }.toSet() }) return false
        return resolved.sourceIds.any { sid -> pack.sourceById(sid) != null }
    }

    /**
     * Prevents cross-contamination between distinct device indications (for instance, keeping
     * CFU hyperhidrosis or body-contouring separate from facial SMAS lifting unless the topic
     * explicitly concerns them).
     */
    fun incompatibleTopicAreas(
        treatmentId: String,
        query: String,
        selectedFacts: List<VerifiedFact>,
    ): Set<String> {
        val q = query.lowercase()
        val selectedAreas = selectedFacts.map { it.topicArea }.toSet()
        return when (treatmentId) {
            "cfu" -> buildSet {
                if ("hyperhidrosis" !in selectedAreas && !q.contains("sweat") && !q.contains("hyperhidrosis") && !q.contains("تعرق") && !q.contains("إبط")) {
                    add("hyperhidrosis")
                }
                if ("body_contouring" !in selectedAreas && !q.contains("body") && !q.contains("abdomen") && !q.contains("fat") && !q.contains("جسم") && !q.contains("نحت")) {
                    add("body_contouring")
                }
            }
            "picostar" -> buildSet {
                val tattooFocus = "tattoos_pmu" in selectedAreas || q.contains("tattoo") || q.contains("تاتو") || q.contains("وشم") || q.contains("pmu")
                val scarFocus = "scars_rejuvenation" in selectedAreas || q.contains("scar") || q.contains("ندب") || q.contains("microspot")
                if (tattooFocus && !scarFocus) add("scars_rejuvenation")
                if (scarFocus && !tattooFocus) add("tattoos_pmu")
            }
            "dermablate" -> buildSet {
                if ("benign_lesions" !in selectedAreas && !q.contains("lesion") && !q.contains("شامة") && !q.contains("ثآليل")) {
                    add("benign_lesions")
                }
            }
            else -> emptySet()
        }
    }

    /**
     * Checks local provenance state and reports verified source status honestly without fabricating
     * unperformed web scraping. Live online research requires explicit web-search grounding.
     */
    fun auditLocalSources(treatment: Treatment?): ResearchRefreshResult {
        val id = treatment?.id ?: Treatment.GENERAL_ID
        val pack = TreatmentKnowledgeCatalog.packFor(
            treatmentId = id,
            linkedReferenceId = treatment?.linkedReferenceProfileId,
            needsConfiguration = treatment?.needsConfiguration == true,
        )
        if (pack == null || pack.sourceReferences.isEmpty()) {
            return ResearchRefreshResult(
                treatmentId = id,
                statusNote = "Local profile has no external manufacturer/clinical source attached yet. Keep clinical claims conservative until configured.",
                latestRetrievalDate = "Not sourced",
                sourceTitles = emptyList(),
            )
        }
        val latest = pack.sourceReferences.maxOfOrNull { it.retrievalDate } ?: "Local catalog"
        val titles = pack.sourceReferences.map { "${it.title} (${it.type.name.lowercase().replace('_', ' ')})" }
        return ResearchRefreshResult(
            treatmentId = id,
            statusNote = "Verified from local vetted knowledge pack ($latest). Offline/client-only mode: no unverified web scraping is performed.",
            latestRetrievalDate = latest,
            sourceTitles = titles,
        )
    }

    private fun areaPriority(area: String): Int = when (area) {
        "picosecond_mechanism", "wavelengths", "650_microsecond", "chromophores", "technology",
        "water_ablation", "1927nm_fractional", "3in1_mechanism", "stratum_corneum", "myth" -> 0
        "lymphatic_clearance", "pico_vs_nano", "ablative_vs_fractional", "ampoules", "serum_infusion",
        "centerless_ace", "collagen", "face_lifting", "scars_resurfacing", "tattoos_pmu" -> 1
        "precautions", "recovery", "aftercare", "sessions" -> 2
        else -> 3
    }

    private fun humanizeArea(area: String): String =
        area.replace('_', ' ').replaceFirstChar { it.uppercase() }
}
