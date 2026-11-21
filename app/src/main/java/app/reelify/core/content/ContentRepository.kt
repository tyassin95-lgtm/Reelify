package app.reelify.core.content

import app.reelify.core.database.ContentDao
import app.reelify.core.database.StyleExampleEntity
import app.reelify.core.database.TopicHistoryEntity
import app.reelify.core.util.AppJson
import app.reelify.core.util.Clock
import app.reelify.core.util.decodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class CreativeHistoryRecord(
    val id: String,
    val treatmentId: String,
    val templateId: String,
    val topicTitle: String,
    val educationalObjective: String = "",
    val primaryFactIds: List<String> = emptyList(),
    val hook: String = "",
    val origin: TopicOrigin = TopicOrigin.CURATED,
    val approvedByUser: Boolean = true,
    val recordedAt: Long = 0L,
)

/** Local, owner-curated creative content: Iraqi style examples, topic/script history, and saved ideas. */
class ContentRepository(private val dao: ContentDao, private val clock: Clock = Clock.System) {
    private val iraqi = "ar-IQ"
    private val historyNs = "creative_history_v2"
    private val savedIdeaNs = "saved_idea_v2"

    fun observeIraqiExamples(): Flow<List<StyleExample>> = dao.observeStyleExamples(iraqi).map { rows ->
        val edits = rows.mapNotNull { AppJson.decodeOrNull<StyleExample>(it.dataJson) }
        val byId = edits.associateBy { it.id }
        IraqiStyleGuide.builtInExamples.map { byId[it.id] ?: it } + edits.filter { e -> IraqiStyleGuide.builtInExamples.none { it.id == e.id } }
    }

    suspend fun iraqiExamplesForPrompt(): List<StyleExample> =
        IraqiStyleGuide.effectiveExamples(dao.getStyleExamples(iraqi).mapNotNull { AppJson.decodeOrNull<StyleExample>(it.dataJson) })

    suspend fun saveExample(example: StyleExample) = dao.upsertStyleExample(
        StyleExampleEntity(example.id, iraqi, example.category, example.status.name, clock.now(), AppJson.encodeToString(StyleExample.serializer(), example)),
    )

    /** Resets a built-in example to its original, unreviewed text, or deletes an owner-added one. */
    suspend fun resetExample(id: String) = dao.deleteStyleExample(id)

    /** Saves an owner-edited shot line as a learned clinic voice example for future generations. */
    suspend fun saveOwnerVoiceSample(text: String, note: String = "") {
        val clean = text.trim().take(240)
        if (clean.isBlank()) return
        val id = "voice_${clean.hashCode().toUInt()}"
        saveExample(
            StyleExample(
                id = id,
                category = "owner_voice",
                better = clean,
                note = note.ifBlank { "Saved from edited script — match this cadence & phrasing" },
                status = ExampleStatus.CORRECTED,
                builtIn = false,
            ),
        )
    }

    suspend fun recordTopicUse(treatmentId: String, templateId: String, topicKey: String, hook: String) =
        dao.insertTopicUse(TopicHistoryEntity(treatmentId = treatmentId, templateId = templateId, topicKey = topicKey, hook = hook.take(300), usedAt = clock.now()))

    suspend fun recordCreativeTopicUse(topic: CreativeTopic, hook: String, approvedByUser: Boolean = true) {
        recordTopicUse(topic.treatmentId, topic.templateId, topic.id, hook)
        val now = clock.now()
        val rec = CreativeHistoryRecord(
            id = "hist_${now}_${topic.id.hashCode().toUInt()}",
            treatmentId = topic.treatmentId,
            templateId = topic.templateId,
            topicTitle = topic.title,
            educationalObjective = topic.educationalObjective,
            primaryFactIds = topic.primaryFactIds,
            hook = hook.take(300),
            origin = topic.origin,
            approvedByUser = approvedByUser,
            recordedAt = now,
        )
        dao.upsertStyleExample(
            StyleExampleEntity(
                id = rec.id,
                language = historyNs,
                category = topic.treatmentId,
                status = topic.templateId,
                updatedAt = now,
                dataJson = AppJson.encodeToString(CreativeHistoryRecord.serializer(), rec),
            ),
        )
    }

    suspend fun recentCreativeHistory(treatmentId: String, limit: Int = 25): List<CreativeHistoryRecord> =
        dao.getStyleExamples(historyNs)
            .filter { it.category == treatmentId }
            .sortedByDescending { it.updatedAt }
            .take(limit)
            .mapNotNull { AppJson.decodeOrNull<CreativeHistoryRecord>(it.dataJson) }

    fun observeSavedIdeas(treatmentId: String): Flow<List<CreativeTopic>> =
        dao.observeStyleExamples(savedIdeaNs).map { rows ->
            rows.filter { it.category == treatmentId }
                .mapNotNull { AppJson.decodeOrNull<CreativeTopic>(it.dataJson) }
        }

    suspend fun saveIdeaForLater(topic: CreativeTopic) {
        val now = clock.now()
        val rowId = "saved_${topic.treatmentId}_${topic.id}"
        dao.upsertStyleExample(
            StyleExampleEntity(
                id = rowId,
                language = savedIdeaNs,
                category = topic.treatmentId,
                status = topic.templateId,
                updatedAt = now,
                dataJson = AppJson.encodeToString(CreativeTopic.serializer(), topic),
            ),
        )
    }

    suspend fun removeSavedIdea(treatmentId: String, topicId: String) =
        dao.deleteStyleExample("saved_${treatmentId}_$topicId")

    suspend fun recentTopicKeys(treatmentId: String, limit: Int = 12): List<String> = dao.recentTopics(treatmentId, limit).map { it.topicKey }

    suspend fun recentHooks(limit: Int = 12): List<String> = dao.recentTopicsAll(limit).map { it.hook }.filter { it.isNotBlank() }
}
