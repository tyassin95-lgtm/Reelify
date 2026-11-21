package app.reelify

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.reelify.core.designsystem.ReelifyTheme
import app.reelify.core.template.TeleprompterPosition
import app.reelify.feature.recording.CameraChip
import app.reelify.feature.recording.CameraOverlayScaffold
import app.reelify.feature.recording.CameraTool
import app.reelify.feature.recording.RecordButton
import app.reelify.feature.recording.TeleprompterPanel
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The camera overlay must never overlap at supported configurations: small, tall and large phones,
 * a landscape/foldable-like aspect, font scales 1.0–2.0, long Arabic text, and every teleprompter
 * position. Checks the real layout bounds of the four regions.
 */
@RunWith(AndroidJUnit4::class)
class CameraOverlayLayoutTest {
    @get:Rule val rule = createComposeRule()

    private data class Cfg(val wDp: Int, val hDp: Int, val fontScale: Float, val pos: TeleprompterPosition)

    private val longArabic = "عندكم تصبغات بالوجه ومحتارين شنو الليزر المناسب؟ خلّي أوضحلكم شغلة مهمة قبل لا تختارون أي جلسة، لأن مو كل بشرة يناسبها نفس العلاج."

    @Test fun overlayRegionsNeverOverlap() {
        var cfg by mutableStateOf(Cfg(360, 640, 1f, TeleprompterPosition.NEAR_LENS))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, cfg.fontScale)) {
                ReelifyTheme {
                    Box(Modifier.requiredSize(cfg.wDp.dp, cfg.hDp.dp)) {
                        CameraOverlayScaffold(
                            position = cfg.pos, desiredPrompterHeight = (26 * 1.5f * 3 + 24).dp,
                            top = { Row(Modifier.fillMaxWidth()) { Text("اللقطة 1 من 3 · الافتتاحية") } },
                            prompter = { h -> TeleprompterPanel(longArabic, 26f, 1f, 0.55f, false, false, {}, h) },
                            chips = { Column { CameraChip("الكادر ممتاز"); CameraChip("التجميل شغّال · Natural"); CameraChip(longArabic.take(60)) } },
                            bottom = { Row(Modifier.fillMaxWidth()) { CameraTool(Icons.Rounded.Face, "تجميل", true, {}); CameraTool(Icons.Rounded.Subject, "السكربت", true, {}); RecordButton(false, false, 0f) {}; CameraTool(Icons.Rounded.Tune, "الكاميرا", false, {}) } },
                        )
                    }
                }
            }
        }
        val sizes = listOf(320 to 568, 360 to 640, 393 to 852, 412 to 915, 480 to 1000, 600 to 960, 840 to 600)
        for ((w, h) in sizes) for (fs in listOf(1f, 1.3f, 2f)) for (pos in TeleprompterPosition.entries) {
            cfg = Cfg(w, h, fs, pos)
            rule.waitForIdle()
            val rects = listOf("cam_top", "cam_prompter", "cam_chips", "cam_bottom").mapNotNull { tag ->
                rule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot?.let { tag to it }
            }
            val all = rects.toMap()
            assertWithMessage("$cfg: top, chips and bottom are always laid out").that(all.keys).containsAtLeast("cam_top", "cam_chips", "cam_bottom")
            for (i in rects.indices) for (j in i + 1 until rects.size) {
                val (a, ra) = rects[i]; val (b, rb) = rects[j]
                assertWithMessage("$cfg: $a $ra overlaps $b $rb").that(overlaps(ra, rb)).isFalse()
            }
            all["cam_bottom"]?.let { assertWithMessage("$cfg: bottom controls inside the screen").that(it.bottom).isAtMost(h * rule.density.density + 1f) }
        }
    }

    private fun overlaps(a: Rect, b: Rect): Boolean {
        val eps = 0.5f
        return a.left < b.right - eps && b.left < a.right - eps && a.top < b.bottom - eps && b.top < a.bottom - eps && a.width > eps && b.width > eps && a.height > eps && b.height > eps
    }
}
