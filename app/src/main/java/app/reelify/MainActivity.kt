package app.reelify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.reelify.core.data.UiLanguage
import app.reelify.core.designsystem.ReelifyTheme
import app.reelify.core.designsystem.Rf
import app.reelify.feature.common.LocalStrings
import app.reelify.feature.common.UiStrings
import app.reelify.navigation.ReelifyNavHost
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Rf.Background.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Rf.Background.toArgb()),
        )
        super.onCreate(savedInstanceState)
        val container = (application as ReelifyApp).container
        setContent {
            val settings by container.settingsState.collectAsStateWithLifecycle()
            val arabicUi = when (settings.uiLanguage) {
                UiLanguage.ARABIC -> true
                UiLanguage.ENGLISH -> false
                UiLanguage.SYSTEM -> Locale.getDefault().language == "ar"
            }
            CompositionLocalProvider(
                LocalStrings provides if (arabicUi) UiStrings.Arabic else UiStrings.English,
                app.reelify.feature.common.LocalArabicUi provides arabicUi,
                LocalLayoutDirection provides if (arabicUi) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                ReelifyTheme { ReelifyNavHost() }
            }
        }
    }
}
