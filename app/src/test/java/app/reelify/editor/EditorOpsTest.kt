package app.reelify.editor

import app.reelify.Fixtures.clip
import app.reelify.core.model.EditSettings
import app.reelify.feature.editor.EditorSnapshot
import app.reelify.feature.editor.TimelineOps
import app.reelify.feature.editor.UndoStack
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditorOpsTest {
    private val base = listOf(clip("a", "s1", source = 20_000, trimEnd = 12_000), clip("b", "s1", order = 1), clip("c", "s2"))

    @Test fun `split creates two consecutive clips from the same source`() {
        val r = TimelineOps.split(base, "a", 5_000)
        val a = r.filter { it.uri == base[0].uri && it.sectionId == "s1" }.sortedBy { it.order }
        assertThat(a.map { it.trimStartMs to it.trimEndMs }).containsExactly(0L to 5_000L, 5_000L to 12_000L).inOrder()
        assertThat(r.single { it.id == "b" }.order).isEqualTo(2)
    }

    @Test fun `split too close to the edge is ignored`() {
        assertThat(TimelineOps.split(base, "a", 100)).isEqualTo(base)
    }

    @Test fun `trim enforces a minimum length and source bounds`() {
        assertThat(TimelineOps.trim(base, "a", 11_900, null)).isEqualTo(base)
        assertThat(TimelineOps.trim(base, "a", -500, 99_000).single { it.id == "a" }.let { it.trimStartMs to it.trimEndMs }).isEqualTo(0L to 20_000L)
    }

    @Test fun `reorder within a section only`() {
        val r = TimelineOps.reorder(base, "b", -1)
        assertThat(r.filter { it.sectionId == "s1" }.sortedBy { it.order }.map { it.id }).containsExactly("b", "a").inOrder()
        assertThat(r.single { it.id == "c" }).isEqualTo(base[2])
    }

    @Test fun `duplicate and delete`() {
        val d = TimelineOps.duplicate(base, "a")
        assertThat(d).hasSize(4)
        assertThat(TimelineOps.delete(d, "a")).hasSize(3)
    }

    @Test fun `snapping to markers`() {
        assertThat(TimelineOps.snap(9_930, listOf(0, 10_000, 20_000), 120)).isEqualTo(10_000)
        assertThat(TimelineOps.snap(9_500, listOf(0, 10_000), 120)).isEqualTo(9_500)
    }

    @Test fun `undo and redo restore snapshots`() {
        val u = UndoStack()
        val s0 = EditorSnapshot(base, EditSettings())
        val s1 = EditorSnapshot(TimelineOps.delete(base, "a"), EditSettings())
        u.push(s0)
        val undone = u.undo(s1)
        assertThat(undone).isEqualTo(s0)
        assertThat(u.redo(s0)).isEqualTo(s1)
        assertThat(u.canRedo).isFalse()
    }

    @Test fun `sfx cues can be added, moved within bounds and removed`() {
        var e = TimelineOps.addSfx(EditSettings(), "soft_whoosh", 1_000)
        val id = e.sfx.single().id
        e = TimelineOps.moveSfx(e, id, 99_000, 30_000)
        assertThat(e.sfx.single().timeMs).isEqualTo(29_800)
        assertThat(TimelineOps.removeSfx(e, id).sfx).isEmpty()
    }
}
