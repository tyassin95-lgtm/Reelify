package app.reelify.feature.flow

import app.reelify.core.content.CreativeTopic
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import kotlinx.serialization.Serializable

/**
 * Ephemeral in-memory creation session held while choosing treatment, template, language, duration,
 * and exploring AI/curated topic ideas BEFORE a valid script has been generated.
 *
 * No Room project row is created until [app.reelify.integration.openai.ScriptWriter.generate]
 * returns a validated script and the project + 3 sections + script are persisted atomically.
 */
@Serializable
data class CreationDraftSession(
    val sessionId: String,
    val treatmentId: String,
    val templateId: String,
    val language: ReelLanguage,
    val duration: ReelDuration,
    val concept: ReelConcept = ReelConcept(),
    val selectedTopic: CreativeTopic? = null,
    val persistedProjectId: String? = null,
)
