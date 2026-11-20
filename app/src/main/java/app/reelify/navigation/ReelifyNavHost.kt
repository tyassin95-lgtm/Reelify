package app.reelify.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.reelify.core.designsystem.Rf
import app.reelify.feature.autoedit.AutoEditScreen
import app.reelify.feature.create.FeedbackScreen
import app.reelify.feature.create.IdeaScreen
import app.reelify.feature.create.SetupScreen
import app.reelify.feature.editor.CaptionEditorScreen
import app.reelify.feature.editor.TimelineEditorScreen
import app.reelify.feature.export.ExportScreen
import app.reelify.feature.home.MainScreen
import app.reelify.feature.home.SplashScreen
import app.reelify.feature.preview.PreviewScreen
import app.reelify.feature.recording.RecordScreen
import app.reelify.feature.review.FootageReviewScreen
import app.reelify.feature.review.TrimScreen
import app.reelify.feature.review.ChecklistScreen
import app.reelify.feature.script.ScriptScreen
import app.reelify.feature.settings.AiSettingsScreen
import app.reelify.feature.settings.LicensesScreen
import app.reelify.feature.settings.StorageScreen
import app.reelify.feature.studio.BrandingScreen
import app.reelify.feature.studio.SoundLibraryScreen
import app.reelify.feature.studio.TerminologyScreen
import app.reelify.feature.studio.TreatmentEditorScreen

@Composable
fun ReelifyNavHost(nav: NavHostController = rememberNavController()) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        nav, startDestination = Routes.SPLASH, modifier = Modifier.fillMaxSize().background(Rf.Background),
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(320), initialOffset = { it / 5 }) + fadeIn(tween(320)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(260)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(280), targetOffset = { it / 5 }) + fadeOut(tween(280)) },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(onDone = { nav.navigate(Routes.MAIN) { popUpTo(Routes.SPLASH) { inclusive = true } } })
        }
        composable(Routes.MAIN) { MainScreen(nav) }
        composable(
            Routes.SETUP,
            arguments = listOf(navArgument("treatment") { type = NavType.StringType; defaultValue = "" }, navArgument("style") { type = NavType.StringType; defaultValue = "" }),
        ) { e ->
            SetupScreen(
                presetTreatment = e.arguments?.getString("treatment").orEmpty(),
                presetStyle = e.arguments?.getString("style").orEmpty(),
                onBack = back,
                onCreated = { pid -> nav.navigate(Routes.idea(pid)) { popUpTo(Routes.MAIN) } },
            )
        }
        composable(Routes.IDEA) { e -> val pid = e.arguments!!.getString("pid")!!
            IdeaScreen(pid, onBack = back, onAnalyzed = { nav.navigate(Routes.feedback(pid)) }, onSkipToScript = { nav.navigate(Routes.script(pid)) })
        }
        composable(Routes.FEEDBACK) { e -> val pid = e.arguments!!.getString("pid")!!
            FeedbackScreen(pid, onBack = back, onScriptReady = { nav.navigate(Routes.script(pid)) })
        }
        composable(Routes.SCRIPT) { e -> val pid = e.arguments!!.getString("pid")!!
            ScriptScreen(pid, onBack = back, onApproved = { nav.navigate(Routes.checklist(pid)) }, onEditIdea = { nav.navigate(Routes.idea(pid)) })
        }
        composable(Routes.CHECKLIST) { e -> val pid = e.arguments!!.getString("pid")!!
            ChecklistScreen(pid, onBack = back, onRecord = { sid, role -> nav.navigate(Routes.record(pid, sid, role)) }, onReview = { nav.navigate(Routes.review(pid)) }, onTrim = { cid -> nav.navigate(Routes.trim(cid)) })
        }
        composable(Routes.RECORD, arguments = listOf(navArgument("role") { type = NavType.StringType; defaultValue = "PRIMARY" })) { e ->
            val pid = e.arguments!!.getString("pid")!!
            RecordScreen(pid, e.arguments!!.getString("sid")!!, e.arguments!!.getString("role") ?: "PRIMARY", onBack = back,
                onAccepted = { cid -> nav.navigate(Routes.trim(cid)) { popUpTo(Routes.RECORD) { inclusive = true } } })
        }
        composable(Routes.REVIEW) { e -> val pid = e.arguments!!.getString("pid")!!
            FootageReviewScreen(pid, onBack = back,
                onRecord = { sid, role -> nav.navigate(Routes.record(pid, sid, role)) },
                onTrim = { cid -> nav.navigate(Routes.trim(cid)) },
                onAutoEdit = { nav.navigate(Routes.autoEdit(pid)) })
        }
        composable(Routes.TRIM) { e -> TrimScreen(e.arguments!!.getString("cid")!!, onBack = back) }
        composable(Routes.AUTOEDIT, arguments = listOf(navArgument("force") { type = NavType.BoolType; defaultValue = false })) { e ->
            val pid = e.arguments!!.getString("pid")!!
            AutoEditScreen(pid, force = e.arguments!!.getBoolean("force"), onBack = back,
                onDone = { nav.navigate(Routes.preview(pid)) { popUpTo(Routes.autoEdit(pid)) { inclusive = true }; launchSingleTop = true } },
                onFixFootage = { nav.navigate(Routes.review(pid)) { launchSingleTop = true } })
        }
        composable(Routes.PREVIEW) { e -> val pid = e.arguments!!.getString("pid")!!
            PreviewScreen(pid, onBack = back,
                onAdvanced = { nav.navigate(Routes.editor(pid)) },
                onCaptions = { nav.navigate(Routes.captions(pid)) },
                onFootage = { nav.navigate(Routes.review(pid)) },
                onScript = { nav.navigate(Routes.script(pid)) },
                onExport = { nav.navigate(Routes.export(pid)) },
                onRebuild = { nav.navigate(Routes.autoEdit(pid, force = true)) })
        }
        composable(Routes.EDITOR) { e -> TimelineEditorScreen(e.arguments!!.getString("pid")!!, onBack = back) }
        composable(Routes.CAPTIONS) { e -> CaptionEditorScreen(e.arguments!!.getString("pid")!!, onBack = back) }
        composable(Routes.EXPORT) { e -> val pid = e.arguments!!.getString("pid")!!
            ExportScreen(pid, onBack = back, onHome = { nav.navigate(Routes.MAIN) { popUpTo(Routes.MAIN) { inclusive = true } } })
        }
        composable(Routes.TREATMENT) { e -> TreatmentEditorScreen(e.arguments!!.getString("id")!!, onBack = back) }
        composable(Routes.BRANDING) { BrandingScreen(onBack = back) }
        composable(Routes.TERMINOLOGY) { TerminologyScreen(onBack = back) }
        composable(Routes.AI_SETTINGS) { AiSettingsScreen(onBack = back) }
        composable(Routes.STORAGE) { StorageScreen(onBack = back) }
        composable(Routes.LIBRARY) { SoundLibraryScreen(onBack = back) }
        composable(Routes.LICENSES) { LicensesScreen(onBack = back) }
    }
}

/** Opens the most relevant screen for a project given its progress. */
fun NavHostController.openProject(p: app.reelify.core.model.Project) {
    val route = when {
        p.status == app.reelify.core.model.ProjectStatus.EXPORTED || p.status == app.reelify.core.model.ProjectStatus.EDITING -> Routes.preview(p.id)
        p.planApproved -> Routes.checklist(p.id)
        p.plan != null -> Routes.script(p.id)
        p.feedback != null -> Routes.feedback(p.id)
        else -> Routes.idea(p.id)
    }
    navigate(route)
}
