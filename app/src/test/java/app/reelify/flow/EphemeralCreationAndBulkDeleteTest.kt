package app.reelify.flow

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.reelify.AppContainer
import app.reelify.core.content.CreativeTopic
import app.reelify.core.content.IdeaGenerationMode
import app.reelify.core.content.TopicUiCategory
import app.reelify.core.data.TreatmentSeed
import app.reelify.core.model.EditSettings
import app.reelify.core.model.ReelConcept
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ScriptMeta
import app.reelify.core.template.BuiltInTemplates
import app.reelify.core.template.SectionScript
import app.reelify.feature.flow.ChooseReelViewModel
import app.reelify.feature.flow.MessageViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class EphemeralCreationAndBulkDeleteTest {

    private lateinit var app: Application
    private lateinit var container: AppContainer

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        container = AppContainer(app)
    }

    @Test fun `Stage 1 startSession and browsing Stage 2 never creates a persistent Room draft`() = runTest {
        container.library.seedIfNeeded()
        val beforeCount = container.projects.observeProjects().first().size

        val chooseVm = ChooseReelViewModel(container)
        val picostar = TreatmentSeed.treatments.first { it.id == "picostar" }
        val session = chooseVm.startSession(
            language = ReelLanguage.IRAQI_ARABIC,
            treatment = picostar,
            duration = ReelDuration.S30,
            template = BuiltInTemplates.EXPERT_EXPLAINS,
        )
        assertThat(container.creationSessions).containsKey(session.sessionId)

        // Exploring Stage 2 categories and editing concept stays strictly ephemeral in memory
        val msgVm = MessageViewModel(
            c = container,
            pidOrDraftKey = "draft_${session.sessionId}",
            arabicUi = false,
            fallbackTreatmentId = session.treatmentId,
            fallbackTemplateId = session.templateId,
            fallbackLangCode = session.language.code,
            fallbackDurationMs = session.duration.totalMs,
        )
        msgVm.selectCategory(TopicUiCategory.HOW_IT_WORKS)
        msgVm.editConcept { it.copy(audience = "Patients with tattoos") }
        // Failed AI generation (no OpenAI key configured) must still create ZERO Room projects
        msgVm.surpriseMe(mode = IdeaGenerationMode.FRESH)
        kotlinx.coroutines.delay(150)

        val afterCount = container.projects.observeProjects().first().size
        assertThat(afterCount).isEqualTo(beforeCount)
        assertThat(container.creationSessions[session.sessionId]?.concept?.audience).isEqualTo("Patients with tattoos")
    }

    @Test fun `atomic creation persists project three sections and full CreativeTopic together`() = runTest {
        val fullTopic = CreativeTopic(
            id = "ai_pico_1064",
            treatmentId = "picostar",
            templateId = "myth_buster",
            title = "Why 1064 nm targets black ink deeper than 532 nm",
            patientQuestion = "Why do tattoo clinics need multiple wavelengths?",
            hookAngle = "One laser wavelength can't catch every tattoo color.",
            educationalObjective = "Explain 1064 nm depth vs 532 nm superficial absorption.",
            primaryFactIds = listOf("pico_mech_wavelengths"),
            supportingFactIds = listOf("pico_app_tattoos"),
            specificTakeaway = "Wavelength matching protects pigment balance while shattering ink.",
            sourceIds = listOf("src_asclepion_picostar"),
        )
        val meta = ScriptMeta(
            topicTitle = fullTopic.title,
            topicKey = fullTopic.id,
            activeHook = fullTopic.hookAngle,
            creativeTopic = fullTopic,
            sourceIds = fullTopic.sourceIds,
            narrativeSummary = listOf("Question: ${fullTopic.patientQuestion}", "Payoff: ${fullTopic.specificTakeaway}"),
        )
        val scripts = listOf(
            SectionScript(1, "${fullTopic.hookAngle} Red and black inks sit at different skin levels.", "Ink Depth", listOf("ink")),
            SectionScript(2, "1064 nm penetrates deep dermis for black pigment while 532 nm targets red and orange inks.", "Two Wavelengths", listOf("1064 nm", "532 nm")),
            SectionScript(3, "Matching wavelength and Spot diameter clears color cleanly without unnecessary surface heat.", "Smart Plan", listOf("Wavelength")),
        )
        val created = container.projects.createFromTemplateWithScriptAtomically(
            language = ReelLanguage.ENGLISH,
            treatmentId = "picostar",
            duration = ReelDuration.S30,
            template = BuiltInTemplates.MYTH_BUSTER,
            title = fullTopic.title,
            baseEdit = EditSettings(),
            concept = ReelConcept(summary = fullTopic.title),
            meta = meta,
            scripts = scripts,
        )
        val reloaded = container.projects.getBundle(created.project.id)!!
        assertThat(reloaded.sections).hasSize(3)
        assertThat(reloaded.project.script?.creativeTopic).isEqualTo(fullTopic)
        assertThat(reloaded.project.script?.creativeTopic?.primaryFactIds).containsExactly("pico_mech_wavelengths")
        assertThat(reloaded.project.script?.sourceIds).containsExactly("src_asclepion_picostar")
    }

    @Test fun `bulk delete removes selected projects and their directories while preserving others and external media`() = runTest {
        val createdIds = mutableListOf<String>()
        repeat(4) { idx ->
            val p = container.projects.createFromTemplate(
                ReelLanguage.ENGLISH,
                "aerolase",
                ReelDuration.S30,
                BuiltInTemplates.EXPERT_EXPLAINS,
                "Draft $idx",
            )
            // Put project-owned render cache inside each project folder
            File(container.storage.cacheDir(p.id), "preview_proxy_$idx.mp4").writeText("dummy")
            createdIds += p.id
        }
        // External/shared file outside any project dir must NEVER be deleted
        val externalFile = File(app.cacheDir, "shared_gallery_keep.mp4").apply { writeText("keep me") }

        val toDelete = createdIds.take(3)
        val survivor = createdIds.last()
        val deletedCount = container.projects.deleteMany(toDelete)
        assertThat(deletedCount).isEqualTo(3)

        toDelete.forEach { id ->
            assertThat(container.projects.getProject(id)).isNull()
            assertThat(File(app.filesDir, "projects/$id").exists()).isFalse()
        }
        assertThat(container.projects.getProject(survivor)).isNotNull()
        assertThat(File(app.filesDir, "projects/$survivor").exists()).isTrue()
        assertThat(externalFile.exists()).isTrue()
        externalFile.delete()
    }

    @Test fun `findEmptyLegacyDrafts identifies only scriptless footageless drafts`() = runTest {
        val emptyDraft = container.projects.createFromTemplate(ReelLanguage.ENGLISH, "aerolase", ReelDuration.S30, BuiltInTemplates.EXPERT_EXPLAINS, "Abandoned")
        val scriptedDraft = container.projects.createFromTemplate(ReelLanguage.ENGLISH, "aerolase", ReelDuration.S30, BuiltInTemplates.EXPERT_EXPLAINS, "Scripted")
        container.projects.applyScript(
            scriptedDraft.id,
            ScriptMeta(topicTitle = "Real reel"),
            listOf(
                SectionScript(1, "Why pulse width matters.", "Pulse", emptyList()),
                SectionScript(2, "650 microseconds stays selective.", "Selective", emptyList()),
                SectionScript(3, "Protect barrier afterwards.", "Care", emptyList()),
            ),
            "test",
        )
        val found = container.projects.findEmptyLegacyDrafts().map { it.id }
        assertThat(found).contains(emptyDraft.id)
        assertThat(found).doesNotContain(scriptedDraft.id)
    }
}
