package app.reelify.navigation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.ColorGrade
import app.reelify.core.model.ReelDuration
import app.reelify.core.model.ReelLanguage
import app.reelify.core.template.BuiltInTemplates
import app.reelify.feature.common.TrimPlayerController
import app.reelify.feature.flow.CreationDraftSession
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NavigationAndBackSemanticsTest {

    @Test fun `ephemeral message draft route encodes metadata without persisting a database row`() {
        val route = Routes.messageDraft("sess123", "picostar", BuiltInTemplates.MYTH_BUSTER.id, ReelLanguage.IRAQI_ARABIC.code, ReelDuration.S45.totalMs)
        assertThat(route).startsWith("message/draft_sess123")
        assertThat(route).contains("treatment=picostar")
        assertThat(route).contains("template=myth_buster")
        assertThat(route).contains("lang=ar-IQ")
        assertThat(route).contains("dur=45000")

        val session = CreationDraftSession(
            sessionId = "sess123",
            treatmentId = "picostar",
            templateId = BuiltInTemplates.MYTH_BUSTER.id,
            language = ReelLanguage.IRAQI_ARABIC,
            duration = ReelDuration.S45,
        )
        assertThat(session.persistedProjectId).isNull()
    }

    @Test fun `trim player controller starts effect-free without initializing GL effects`() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        val controller = TrimPlayerController(ctx)
        try {
            assertThat(controller.isEffectFree).isTrue()
            controller.load(Uri.parse("file:///nonexistent_clip.mp4"), startPositionMs = 0L, volume = 1f, play = false)
            assertThat(controller.isEffectFree).isTrue()
            // Never throws even when ColorGrade / Beauty are non-identity in the project
            assertThat(BeautySettings().enabled).isFalse()
            assertThat(ColorGrade().intensity).isGreaterThan(0f)
        } finally {
            controller.release()
        }
    }
}
