package app.reelify.designsystem

import app.reelify.core.designsystem.TemplateFrameGeometry
import app.reelify.core.template.BuiltInTemplates
import app.reelify.core.template.FramingPreset
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TemplateArtworkGeometryTest {

    @Test fun `all talking head framing presets stay strictly inside normalized miniature frame`() {
        FramingPreset.entries.forEach { preset ->
            val g = TemplateFrameGeometry.talking(preset)
            assertThat(g.headCenterX).isAtLeast(0.1f)
            assertThat(g.headCenterX).isAtMost(0.9f)
            assertThat(g.headCenterY - g.headRadiusFracH).isAtLeast(0.0f)
            assertThat(g.headCenterY + g.headRadiusFracH).isAtMost(1.0f)
            assertThat(g.torso.left).isAtLeast(0.0f)
            assertThat(g.torso.left + g.torso.width).isAtMost(1.0f)
            assertThat(g.torso.top).isAtLeast(0.0f)
            assertThat(g.torso.top + g.torso.height).isAtMost(1.0f)
            assertThat(g.eyeLineY).isAtLeast(0.0f)
            assertThat(g.eyeLineY).isAtMost(1.0f)
            assertThat(g.headlineBar.top + g.headlineBar.height).isAtMost(1.0f)
            assertThat(g.headlineBar.left + g.headlineBar.width).isAtMost(1.0f)
        }
    }

    @Test fun `device B-roll geometry stays strictly bounded inside miniature frame`() {
        val d = TemplateFrameGeometry.device()
        assertThat(d.handpiece.left).isAtLeast(0.0f)
        assertThat(d.handpiece.left + d.handpiece.width).isAtMost(1.0f)
        assertThat(d.handpiece.top).isAtLeast(0.0f)
        assertThat(d.beamBottomY).isLessThan(d.headlineBar.top)
        assertThat(d.targetCenterY + d.targetRadiusFracW).isAtMost(1.0f)
        assertThat(d.micCenter.first + d.micRadiusFracW).isAtMost(1.0f)
        assertThat(d.micCenter.second - d.micRadiusFracW).isAtLeast(0.0f)
        assertThat(d.headlineBar.top + d.headlineBar.height).isAtMost(1.0f)
    }

    @Test fun `every built-in template has 3 valid shots whose geometry does not overflow frame`() {
        assertThat(BuiltInTemplates.all).hasSize(6)
        BuiltInTemplates.all.forEach { tpl ->
            assertThat(tpl.shots).hasSize(3)
            tpl.shots.forEach { shot ->
                val g = TemplateFrameGeometry.talking(shot.framing)
                assertThat(g.torso.top + g.torso.height).isLessThan(g.headlineBar.top)
            }
        }
    }
}
