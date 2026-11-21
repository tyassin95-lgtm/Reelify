package app.reelify.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.reelify.Fixtures.clip
import app.reelify.core.content.ContentRepository
import app.reelify.core.data.ProjectRepository
import app.reelify.core.database.ReelifyDatabase
import app.reelify.core.model.EditGroup
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelStyle
import app.reelify.core.model.ScriptMeta
import app.reelify.core.template.BuiltInTemplates
import app.reelify.core.template.LocalizedText
import app.reelify.core.template.ProductionMode
import app.reelify.core.template.SectionScript
import app.reelify.core.template.TemplateRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class TemplateProjectTest {
    private lateinit var db: ReelifyDatabase
    private lateinit var repo: ProjectRepository
    private lateinit var templates: TemplateRepository

    @Before fun setUp() {
        db = ReelifyDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = ProjectRepository(db.projectDao())
        templates = TemplateRepository(db.contentDao())
    }
    @After fun tearDown() = db.close()

    private val scripts = listOf(
        SectionScript(1, "شنو أكثر غلطة؟ خلّي أحجيلكم.", "غلطة شائعة", emptyList()),
        SectionScript(2, "الإيروليز يشتغل بنبضة قصيرة كلش.", "شلون يشتغل", listOf("نبضة")),
        SectionScript(3, "احجزوا تقييم ونشوف سوا.", "احجز تقييم", emptyList()),
    )

    @Test fun `template project stores a full snapshot and three template sections`() = runTest {
        val p = repo.createFromTemplate(ReelLanguage.IRAQI_ARABIC, "aerolase", ReelDuration.S45, BuiltInTemplates.EXPERT_EXPLAINS, "Test")
        val b = repo.getBundle(p.id)!!
        assertThat(b.project.mode).isEqualTo(ProductionMode.TEMPLATE)
        assertThat(b.project.template).isEqualTo(BuiltInTemplates.EXPERT_EXPLAINS)
        assertThat(b.sections.map { it.role }).containsExactly("hook", "explanation", "conclusion_cta").inOrder()
        assertThat(b.project.edit.captions.style.preset).isEqualTo(BuiltInTemplates.EXPERT_EXPLAINS.captionPresetId)
    }

    @Test fun `applyScript writes only script fields, records revisions, and a later one-shot rewrite keeps other shots`() = runTest {
        val p = repo.createFromTemplate(ReelLanguage.IRAQI_ARABIC, "aerolase", ReelDuration.S30, BuiltInTemplates.EXPERT_EXPLAINS, "Test")
        val before = repo.getBundle(p.id)!!.sections
        repo.applyScript(p.id, ScriptMeta(topicTitle = "x", activeHook = "شنو أكثر غلطة؟"), scripts, "AI script")
        val after = repo.getBundle(p.id)!!.sections
        after.zip(before).forEach { (a, b) -> assertThat(a.id).isEqualTo(b.id); assertThat(a.cameraDirection).isEqualTo(b.cameraDirection); assertThat(a.shotType).isEqualTo(b.shotType) }
        repo.applyScript(p.id, ScriptMeta(), listOf(SectionScript(2, "كلام جديد للقطة الثانية بس.", "عنوان", emptyList())), "rewrite", onlyIndex = 2)
        val final = repo.getBundle(p.id)!!.sections
        assertThat(final[0].spokenScript).isEqualTo(scripts[0].spokenScript)
        assertThat(final[1].spokenScript).isEqualTo("كلام جديد للقطة الثانية بس.")
        assertThat(final[2].spokenScript).isEqualTo(scripts[2].spokenScript)
        assertThat(repo.revisionCount(final[1].id)).isEqualTo(1)
        // undo restores the previous wording of that shot only
        repo.undoSection(p.id, final[1].id)
        assertThat(repo.getBundle(p.id)!!.sections[1].spokenScript).isEqualTo(scripts[1].spokenScript)
    }

    @Test fun `user edits become overrides and reset restores the template`() = runTest {
        val p = repo.createFromTemplate(ReelLanguage.ENGLISH, "aerolase", ReelDuration.S30, BuiltInTemplates.MYTH_BUSTER, "Test")
        repo.updateEdit(p.id) { it.copy(music = it.music.copy(trackId = "ambient_haze")) }
        val e = repo.getProject(p.id)!!.edit
        assertThat(e.userOverrides).containsExactly(EditGroup.MUSIC)
        // automation (byUser = false) never adds overrides
        repo.updateEdit(p.id, byUser = false) { it.copy(color = it.color.copy(intensity = 0.1f)) }
        assertThat(repo.getProject(p.id)!!.edit.userOverrides).containsExactly(EditGroup.MUSIC)
        repo.resetEditToTemplate(p.id, setOf(EditGroup.MUSIC))
        val r = repo.getProject(p.id)!!.edit
        assertThat(r.userOverrides).isEmpty()
        assertThat(r.music.trackId).isEqualTo("modern_pulse")
    }

    @Test fun `customised template versions never change existing projects`() = runTest {
        val custom1 = templates.saveCustom(BuiltInTemplates.PATIENT_QUESTION.copy(name = LocalizedText("Mine")))
        assertThat(custom1.builtIn).isFalse(); assertThat(custom1.version).isEqualTo(1); assertThat(custom1.baseTemplateId).isEqualTo("patient_question")
        val p = repo.createFromTemplate(ReelLanguage.ENGLISH, "general", ReelDuration.S30, custom1, "Old")
        val custom2 = templates.saveCustom(custom1.copy(captionPresetId = app.reelify.core.model.CaptionPresetId.BOLD_ENGAGEMENT))
        assertThat(custom2.id).isEqualTo(custom1.id); assertThat(custom2.version).isEqualTo(2)
        val stored = repo.getProject(p.id)!!.template!!
        assertThat(stored.version).isEqualTo(1)
        assertThat(stored.captionPresetId).isEqualTo(custom1.captionPresetId)
        assertThat(templates.get(custom1.id)!!.version).isEqualTo(2)
    }

    @Test fun `legacy projects keep their own section settings and LEGACY mode`() = runTest {
        val p = repo.create(ReelLanguage.ENGLISH, "aerolase", ReelDuration.S30, ReelStyle.EDUCATIONAL, "Legacy")
        val b = repo.getBundle(p.id)!!
        assertThat(b.project.mode).isEqualTo(ProductionMode.LEGACY)
        assertThat(b.project.template).isNull()
        assertThat(b.project.isTemplate).isFalse()
        repo.upsertClip(clip("c1", b.sections[0].id).copy(projectId = p.id))
        assertThat(repo.getBundle(p.id)!!.clips).hasSize(1)
    }

    @Test fun `duplicating a template project keeps template, script and overrides`() = runTest {
        val p = repo.createFromTemplate(ReelLanguage.IRAQI_ARABIC, "aerolase", ReelDuration.S30, BuiltInTemplates.THREE_THINGS, "T")
        repo.applyScript(p.id, ScriptMeta(topicTitle = "t"), scripts, "AI")
        val copy = repo.duplicateAsTemplate(p.id)!!
        val b = repo.getBundle(copy.id)!!
        assertThat(b.project.isTemplate).isTrue()
        assertThat(b.project.template!!.id).isEqualTo("three_things")
        assertThat(b.sections.map { it.spokenScript }).isEqualTo(scripts.map { it.spokenScript })
    }

    @Test fun `topic history drives recent keys`() = runTest {
        val content = ContentRepository(db.contentDao())
        content.recordTopicUse("aerolase", "expert_explains", "misconception", "hook a")
        content.recordTopicUse("aerolase", "expert_explains", "one_mistake", "hook b")
        assertThat(content.recentTopicKeys("aerolase")).containsExactly("one_mistake", "misconception")
        assertThat(content.recentHooks()).contains("hook a")
    }
}
