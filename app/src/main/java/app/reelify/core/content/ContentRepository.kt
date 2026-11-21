package app.reelify.core.content

import app.reelify.core.database.ContentDao
import app.reelify.core.database.StyleExampleEntity
import app.reelify.core.database.TopicHistoryEntity
import app.reelify.core.util.AppJson
import app.reelify.core.util.Clock
import app.reelify.core.util.decodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Local, owner-curated creative content: Iraqi style examples and topic history. */
class ContentRepository(private val dao: ContentDao, private val clock: Clock = Clock.System) {
    private val iraqi = "ar-IQ"

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

    suspend fun recentTopicKeys(treatmentId: String, limit: Int = 12): List<String> = dao.recentTopics(treatmentId, limit).map { it.topicKey }

    suspend fun recentHooks(limit: Int = 12): List<String> = dao.recentTopicsAll(limit).map { it.hook }.filter { it.isNotBlank() }
}
