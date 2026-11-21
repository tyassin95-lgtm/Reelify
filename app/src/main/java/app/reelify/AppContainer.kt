package app.reelify

import android.content.Context
import app.reelify.core.audio.AudioAnalyzer
import app.reelify.core.data.LibraryRepository
import app.reelify.core.data.ProjectRepository
import app.reelify.core.data.SettingsRepository
import app.reelify.core.database.ReelifyDatabase
import app.reelify.core.media.MediaProbe
import app.reelify.core.media.Thumbnails
import app.reelify.core.media.engine.CompositionFactory
import app.reelify.core.media.engine.FaceAnalyzer
import app.reelify.core.media.engine.PreviewProxyManager
import app.reelify.core.media.engine.ReelFonts
import app.reelify.core.media.engine.RenderPreparer
import app.reelify.core.media.engine.TransformerRunner
import app.reelify.core.pipeline.AutoEditor
import app.reelify.core.pipeline.ExportManager
import app.reelify.core.security.AiProvider
import app.reelify.core.security.CredentialRepository
import app.reelify.core.security.KeystoreSecretStore
import app.reelify.core.storage.MediaStorage
import app.reelify.core.timing.TimingEstimator
import app.reelify.integration.common.buildHttpClient
import app.reelify.integration.deepgram.DeepgramClient
import app.reelify.integration.openai.OpenAiClient
import app.reelify.integration.openai.ReelDirector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Manual dependency container (no DI framework needed for a single-module personal app). */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = ReelifyDatabase.create(appContext)
    val storage = MediaStorage(appContext)
    val settings = SettingsRepository(appContext)
    val projects = ProjectRepository(database.projectDao(), onProjectDeleted = { storage.deleteProjectFiles(it) })
    val library = LibraryRepository(database.libraryDao())

    val credentials = CredentialRepository(
        KeystoreSecretStore(appContext),
        mapOf(AiProvider.OPENAI to BuildConfig.EMBEDDED_OPENAI_API_KEY, AiProvider.DEEPGRAM to BuildConfig.EMBEDDED_DEEPGRAM_API_KEY),
    )

    val settingsState = settings.settings.stateIn(appScope, SharingStarted.Eagerly, app.reelify.core.data.AppSettings())
    val timingState = settings.settings.map { TimingEstimator(it.englishWordsPerSecond, it.arabicWordsPerSecond) }
        .stateIn(appScope, SharingStarted.Eagerly, TimingEstimator())

    private val http = buildHttpClient()
    val openAi = OpenAiClient(http, credentials, modelProvider = { settings.current().openAiModel.ifBlank { BuildConfig.DEFAULT_OPENAI_MODEL } })
    val deepgram = DeepgramClient(http, credentials, modelProvider = { settings.current().deepgramModel.ifBlank { BuildConfig.DEFAULT_DEEPGRAM_MODEL } })
    val director = ReelDirector(openAi, timing = { timingState.value })
    val scriptWriter = app.reelify.integration.openai.ScriptWriter(openAi, timing = { timingState.value })
    val templates = app.reelify.core.template.TemplateRepository(database.contentDao())
    val content = app.reelify.core.content.ContentRepository(database.contentDao())

    val probe = MediaProbe(appContext)
    val thumbnails = Thumbnails(appContext)
    val audioAnalyzer = AudioAnalyzer(appContext)
    val fonts = ReelFonts(appContext.assets)
    val faceAnalyzer = FaceAnalyzer(appContext, thumbnails)
    val preparer = RenderPreparer(appContext, projects, settings, storage, probe, audioAnalyzer)
    val compositionFactory = CompositionFactory(appContext, fonts)
    val transformerRunner = TransformerRunner(appContext)
    val previewProxy = PreviewProxyManager(appContext, storage, compositionFactory, transformerRunner)
    val autoEditor = AutoEditor(projects, library, settings, storage, preparer, compositionFactory, transformerRunner, faceAnalyzer, deepgram, director, credentials)
    val importer = app.reelify.core.media.MediaImporter(projects, storage, probe, thumbnails, audioAnalyzer)
    val exportManager = ExportManager(appContext, projects, settings, storage, preparer, compositionFactory, transformerRunner)
}
