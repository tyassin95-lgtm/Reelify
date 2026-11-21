package app.reelify.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.reelify.feature.flow.ChooseReelScreen
import app.reelify.feature.flow.FilmScreen
import app.reelify.feature.flow.MessageScreen
import app.reelify.feature.flow.ScriptReviewScreen
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
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(240, easing = FastOutSlowInEasing),
                initialOffset = { it / 12 },
            ) + fadeIn(tween(220))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(200, easing = FastOutSlowInEasing),
                targetOffset = { it / 18 },
            ) + fadeOut(tween(180))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(220, easing = FastOutSlowInEasing),
                initialOffset = { it / 14 },
            ) + fadeIn(tween(200))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(220, easing = FastOutSlowInEasing),
                targetOffset = { it / 10 },
            ) + fadeOut(tween(180))
        },
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
        composable(Routes.RECORD, arguments = listOf(
            navArgument("role") { type = NavType.StringType; defaultValue = "PRIMARY" },
            navArgument("muted") { type = NavType.BoolType; defaultValue = false },
            navArgument("replace") { type = NavType.BoolType; defaultValue = false },
        )) { e ->
            val pid = e.arguments!!.getString("pid")!!
            RecordScreen(pid, e.arguments!!.getString("sid")!!, e.arguments!!.getString("role") ?: "PRIMARY", e.arguments!!.getBoolean("muted"), e.arguments!!.getBoolean("replace"), onBack = back,
                onAccepted = { cid, templateProject ->
                    // Template flow: straight back to the shot list. Legacy flow: trim step as before.
                    if (templateProject) nav.popBackStack() else nav.navigate(Routes.trim(cid)) { popUpTo(Routes.RECORD) { inclusive = true } }
                })
        }
        // ------------------------------------------------------------ Reelify 2.x four-stage flow
        composable(Routes.CHOOSE, arguments = listOf(navArgument("treatment") { type = NavType.StringType; defaultValue = "" }, navArgument("template") { type = NavType.StringType; defaultValue = "" })) { e ->
            ChooseReelScreen(
                presetTreatment = e.arguments?.getString("treatment"),
                presetTemplate = e.arguments?.getString("template"),
                onBack = back,
                onContinueSession = { sess ->
                    nav.navigate(Routes.messageDraft(sess.sessionId, sess.treatmentId, sess.templateId, sess.language.code, sess.duration.totalMs)) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            Routes.MESSAGE,
            arguments = listOf(
                navArgument("treatment") { type = NavType.StringType; defaultValue = "" },
                navArgument("template") { type = NavType.StringType; defaultValue = "" },
                navArgument("lang") { type = NavType.StringType; defaultValue = "" },
                navArgument("dur") { type = NavType.LongType; defaultValue = 30_000L },
            ),
        ) { e ->
            val pidOrDraft = e.arguments!!.getString("pid")!!
            MessageScreen(
                pidOrDraftKey = pidOrDraft,
                fallbackTreatmentId = e.arguments?.getString("treatment").orEmpty(),
                fallbackTemplateId = e.arguments?.getString("template").orEmpty(),
                fallbackLangCode = e.arguments?.getString("lang").orEmpty(),
                fallbackDurationMs = e.arguments?.getLong("dur") ?: 30_000L,
                onBack = back,
                onScriptReady = { realPid ->
                    projectModes[realPid] = true
                    if (pidOrDraft != realPid) {
                        nav.navigate(Routes.message(realPid)) { popUpTo(Routes.MAIN) }
                    }
                    nav.navigate(Routes.script2(realPid)) { launchSingleTop = true }
                },
                onEditTreatment = { nav.navigate(Routes.treatment(it)) },
            )
        }
        composable(Routes.SCRIPT2) { e -> val pid = e.arguments!!.getString("pid")!!
            DeletedProjectGuard(pid) { nav.popBackStack(Routes.MAIN, false) }
            ScriptReviewScreen(pid, onBack = back, onApproved = { nav.navigate(Routes.film(pid)) { launchSingleTop = true } },
                onChangeTopic = { if (!nav.popBackStack(Routes.MESSAGE, false)) nav.navigate(Routes.message(pid)) })
        }
        composable(Routes.FILM) { e -> val pid = e.arguments!!.getString("pid")!!
            DeletedProjectGuard(pid) { nav.popBackStack(Routes.MAIN, false) }
            FilmScreen(pid, onBack = back,
                onRecord = { sid, role, muted, replace -> nav.navigate(Routes.record(pid, sid, role, muted, replace)) },
                onTrim = { cid -> nav.navigate(Routes.trim(cid)) },
                onPrepare = { nav.navigate(Routes.autoEdit(pid)) },
                onScript = { nav.navigate(Routes.script2(pid)) { launchSingleTop = true } })
        }
        composable(Routes.REVIEW) { e -> val pid = e.arguments!!.getString("pid")!!
            DeletedProjectGuard(pid) { nav.popBackStack(Routes.MAIN, false) }
            FootageReviewScreen(pid, onBack = back,
                onRecord = { sid, role -> nav.navigate(Routes.record(pid, sid, role)) },
                onTrim = { cid -> nav.navigate(Routes.trim(cid)) },
                onAutoEdit = { nav.navigate(Routes.autoEdit(pid)) })
        }
        composable(Routes.TRIM) { e -> TrimScreen(e.arguments!!.getString("cid")!!, onBack = back) }
        composable(Routes.AUTOEDIT, arguments = listOf(navArgument("force") { type = NavType.BoolType; defaultValue = false })) { e ->
            val pid = e.arguments!!.getString("pid")!!
            RememberProjectMode(pid)
            AutoEditScreen(pid, force = e.arguments!!.getBoolean("force"), onBack = back,
                onDone = { nav.navigate(Routes.preview(pid)) { popUpTo(Routes.autoEdit(pid)) { inclusive = true }; launchSingleTop = true } },
                onFixFootage = { nav.navigateFootage(pid) })
        }
        composable(Routes.PREVIEW) { e -> val pid = e.arguments!!.getString("pid")!!
            RememberProjectMode(pid)
            DeletedProjectGuard(pid) { nav.popBackStack(Routes.MAIN, false) }
            PreviewScreen(pid, onBack = back,
                onAdvanced = { nav.navigate(Routes.editor(pid)) },
                onCaptions = { nav.navigate(Routes.captions(pid)) },
                onFootage = { nav.navigateFootage(pid) },
                onScript = { nav.navigateScript(pid) },
                onExport = { nav.navigate(Routes.export(pid)) },
                onRebuild = { nav.navigate(Routes.autoEdit(pid, force = true)) },
                onTrim = { cid -> nav.navigate(Routes.trim(cid)) })
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
        composable(Routes.IRAQI_STYLE) { app.reelify.feature.studio.IraqiStyleScreen(onBack = back) }
        composable(Routes.TEMPLATES) { app.reelify.feature.studio.TemplatesScreen(onBack = back) }
    }
}

/** Template projects use the Film stage; legacy projects keep the footage review screen. */
fun NavHostController.navigateFootage(pid: String) {
    val tpl = projectModes[pid] ?: false
    navigate(if (tpl) Routes.film(pid) else Routes.review(pid)) { launchSingleTop = true }
}

fun NavHostController.navigateScript(pid: String) {
    val tpl = projectModes[pid] ?: false
    navigate(if (tpl) Routes.script2(pid) else Routes.script(pid)) { launchSingleTop = true }
}

/** Cache of which projects are template-based (filled by openProject / creation; defaults to legacy). */
internal val projectModes = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

/** Opens the most relevant screen for a project given its progress. */
fun NavHostController.openProject(p: app.reelify.core.model.Project) {
    projectModes[p.id] = p.isTemplate
    if (p.isTemplate) {
        navigate(when {
            p.status == app.reelify.core.model.ProjectStatus.EXPORTED || p.status == app.reelify.core.model.ProjectStatus.EDITING -> Routes.preview(p.id)
            p.planApproved -> Routes.film(p.id)
            p.script != null -> Routes.script2(p.id)
            else -> Routes.message(p.id)
        })
        return
    }
    val route = when {
        p.status == app.reelify.core.model.ProjectStatus.EXPORTED || p.status == app.reelify.core.model.ProjectStatus.EDITING -> Routes.preview(p.id)
        p.planApproved -> Routes.checklist(p.id)
        p.plan != null -> Routes.script(p.id)
        p.feedback != null -> Routes.feedback(p.id)
        else -> Routes.idea(p.id)
    }
    navigate(route)
}

/** Keeps [projectModes] correct even after process death (routes don't carry the mode). */
@Composable
private fun RememberProjectMode(pid: String) {
    val c = app.reelify.feature.common.appContainer()
    LaunchedEffect(pid) { c.projects.getProject(pid)?.let { projectModes[pid] = it.isTemplate } }
}

/** Exits cleanly to MainScreen if a project was deleted while deep in a stale back-stack entry. */
@Composable
private fun DeletedProjectGuard(pid: String, onMissing: () -> Unit) {
    val c = app.reelify.feature.common.appContainer()
    var seenExists by remember(pid) { mutableStateOf(false) }
    LaunchedEffect(pid) {
        c.projects.observeBundle(pid).collect { bundle ->
            if (bundle != null) {
                seenExists = true
            } else if (seenExists || c.projects.getProject(pid) == null) {
                onMissing()
            }
        }
    }
}
