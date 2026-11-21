package app.reelify.core.template

import app.reelify.core.database.ContentDao
import app.reelify.core.database.CustomTemplateEntity
import app.reelify.core.util.AppJson
import app.reelify.core.util.Clock
import app.reelify.core.util.Ids
import app.reelify.core.util.decodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Built-in templates (in code, versioned) plus owner-customised copies (Room). Customising never
 * edits a built-in: it saves a new template with [ReelTemplate.baseTemplateId] and its own version.
 */
class TemplateRepository(private val dao: ContentDao, private val clock: Clock = Clock.System) {

    fun observeAll(): Flow<List<ReelTemplate>> = dao.observeCustomTemplates().map { customs ->
        BuiltInTemplates.all + customs.mapNotNull { AppJson.decodeOrNull<ReelTemplate>(it.dataJson) }
    }

    suspend fun get(id: String): ReelTemplate? =
        BuiltInTemplates.byId(id) ?: dao.getCustomTemplate(id)?.let { AppJson.decodeOrNull<ReelTemplate>(it.dataJson) }

    /**
     * Saves a customised template. Shot count and roles are validated so a custom template can't
     * break the three-equal-sections contract.
     */
    suspend fun saveCustom(template: ReelTemplate): ReelTemplate {
        require(template.shots.size == 3 && template.shots.map { it.index }.sorted() == listOf(1, 2, 3)) { "A template needs exactly three shots" }
        val existing = if (!template.builtIn) dao.getCustomTemplate(template.id) else null
        val id = if (template.builtIn) "custom-" + Ids.new() else template.id
        val version = (existing?.version ?: 0) + 1
        val saved = template.copy(
            id = id, version = version, builtIn = false,
            baseTemplateId = template.baseTemplateId ?: if (template.builtIn) template.id else null,
            previewAsset = template.previewAsset,
        )
        dao.upsertCustomTemplate(CustomTemplateEntity(id, saved.name.en, saved.baseTemplateId, version, clock.now(), AppJson.encodeToString(ReelTemplate.serializer(), saved)))
        return saved
    }

    suspend fun deleteCustom(id: String) = dao.deleteCustomTemplate(id)
}
