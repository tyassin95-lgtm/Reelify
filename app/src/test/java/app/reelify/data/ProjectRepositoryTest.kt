package app.reelify.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.reelify.Fixtures.clip
import app.reelify.core.data.ProjectRepository
import app.reelify.core.database.ReelifyDatabase
import app.reelify.core.model.PlanMeta
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.model.ReelStyle
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProjectRepositoryTest {
    private lateinit var db: ReelifyDatabase
    private lateinit var repo: ProjectRepository
    private val deleted = mutableListOf<String>()

    @Before fun setUp() {
        db = ReelifyDatabase.inMemory(ApplicationProvider.getApplicationContext())
        repo = ProjectRepository(db.projectDao(), onProjectDeleted = { deleted += it })
    }
    @After fun tearDown() = db.close()

    private suspend fun newProject() = repo.create(ReelLanguage.IRAQI_ARABIC, "aerolase", ReelDuration.S30, ReelStyle.EDUCATIONAL, "Test")

    @Test fun `create makes three stable sections and reopens`() = runTest {
        val p = newProject()
        val b = repo.getBundle(p.id)!!
        assertThat(b.sections.map { it.index }).containsExactly(1, 2, 3).inOrder()
        assertThat(b.sections.map { it.id }.toSet()).hasSize(3)
        assertThat(repo.observeBundle(p.id).first()!!.project.language).isEqualTo(ReelLanguage.IRAQI_ARABIC)
    }

    @Test fun `apply plan keeps ids, respects locks and records revisions`() = runTest {
        val p = newProject()
        val s = repo.getBundle(p.id)!!.sections
        repo.applyPlan(p.id, PlanMeta(title = "v1"), s.map { it.copy(spokenScript = "v1-${it.index}") }, "gen")
        repo.updateSectionQuiet(p.id, s[0].id) { it.copy(locked = true) }
        repo.applyPlan(p.id, PlanMeta(title = "v2"), s.map { it.copy(id = "model-chosen-${it.index}", spokenScript = "v2-${it.index}") }, "regen")
        val after = repo.getBundle(p.id)!!
        assertThat(after.sections.map { it.id }).isEqualTo(s.map { it.id })
        assertThat(after.sections.map { it.spokenScript }).containsExactly("v1-1", "v2-2", "v2-3").inOrder()
        assertThat(after.project.title).isEqualTo("v2")
    }

    @Test fun `replacing section 2 leaves 1 and 3 and footage untouched, and can be undone`() = runTest {
        val p = newProject()
        val s = repo.getBundle(p.id)!!.sections
        repo.applyPlan(p.id, PlanMeta(), s.map { it.copy(spokenScript = "orig-${it.index}") }, "gen")
        repo.upsertClip(clip("c2", s[1].id).copy(projectId = p.id))
        val before = repo.getBundle(p.id)!!
        repo.replaceSection(p.id, before.sections[1].copy(spokenScript = "shorter Iraqi"), "AI: shorter")
        val after = repo.getBundle(p.id)!!
        assertThat(after.sections[0]).isEqualTo(before.sections[0])
        assertThat(after.sections[2]).isEqualTo(before.sections[2])
        assertThat(after.sections[1].spokenScript).isEqualTo("shorter Iraqi")
        assertThat(after.clips.single().sectionId).isEqualTo(s[1].id)
        repo.undoSection(p.id, s[1].id)
        assertThat(repo.getBundle(p.id)!!.sections[1].spokenScript).isEqualTo("orig-2")
    }

    @Test fun `clips replace atomically and edits persist`() = runTest {
        val p = newProject()
        val sid = repo.getBundle(p.id)!!.sections[0].id
        repo.replaceClips(p.id, listOf(clip("x", sid).copy(projectId = p.id), clip("y", sid, order = 1).copy(projectId = p.id)))
        repo.replaceClips(p.id, listOf(clip("y", sid).copy(projectId = p.id, trimEndMs = 5000)))
        val b = repo.getBundle(p.id)!!
        assertThat(b.clips.map { it.id }).containsExactly("y")
        assertThat(b.clips.single().trimEndMs).isEqualTo(5000)
        repo.updateEdit(p.id) { it.copy(music = it.music.copy(volume = 0.1f)) }
        val reopened = repo.getProject(p.id)!!
        assertThat(reopened.edit.music.volume).isEqualTo(0.1f)
        assertThat(reopened.edit.editVersion).isEqualTo(1)
    }

    @Test fun `delete cascades and cleans files`() = runTest {
        val p = newProject()
        repo.upsertClip(clip("x", repo.getBundle(p.id)!!.sections[0].id).copy(projectId = p.id))
        repo.delete(p.id)
        assertThat(repo.getBundle(p.id)).isNull()
        assertThat(repo.allClips()).isEmpty()
        assertThat(deleted).containsExactly(p.id)
    }
}
