package app.reelify.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.reelify.BuildConfig
import app.reelify.core.model.BeautySettings
import app.reelify.core.model.BrandProfile
import app.reelify.core.model.CaptionPresetId
import app.reelify.core.model.CaptionStyle
import app.reelify.core.model.ExportQuality
import app.reelify.core.model.ReelLanguage
import app.reelify.core.util.AppJson
import app.reelify.core.util.decodeOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
enum class UiLanguage { SYSTEM, ENGLISH, ARABIC }

@Serializable
enum class CaptureResolution(val label: String) { FHD("1080p"), UHD("4K") }

@Serializable
data class CameraDefaults(
    val resolution: CaptureResolution = CaptureResolution.FHD,
    val fps60: Boolean = false,
    val grid: Boolean = true,
    val level: Boolean = true,
    val safeZones: Boolean = true,
    val countdownSec: Int = 3,
    val frontCamera: Boolean = true,
    val teleprompterSize: Float = 26f,
    val teleprompterSpeed: Float = 1f,
    val teleprompterOpacity: Float = 0.55f,
    val teleprompterMirror: Boolean = false,
    val teleprompterTop: Boolean = true,
    val beauty: BeautySettings = BeautySettings.forPreset(app.reelify.core.model.BeautyPreset.NATURAL),
    val faceGuide: Boolean = true,
)

@Serializable
data class AppSettings(
    val uiLanguage: UiLanguage = UiLanguage.SYSTEM,
    val defaultReelLanguage: ReelLanguage = ReelLanguage.IRAQI_ARABIC,
    val openAiModel: String = BuildConfig.DEFAULT_OPENAI_MODEL,
    val deepgramModel: String = BuildConfig.DEFAULT_DEEPGRAM_MODEL,
    val englishWordsPerSecond: Float = 2.5f,
    val arabicWordsPerSecond: Float = 2.1f,
    val camera: CameraDefaults = CameraDefaults(),
    val exportQuality: ExportQuality = ExportQuality.STANDARD,
    val defaultCaptionPreset: CaptionPresetId = CaptionPresetId.PREMIUM_BURGUNDY,
    val saveToGallery: Boolean = true,
    val onboardingDone: Boolean = false,
    val sendKeytermsToDeepgram: Boolean = true,
    val haptics: Boolean = true,
    val proxyPreview: Boolean = false,
)

@Serializable
data class SavedCaptionPreset(val id: String, val name: String, val style: CaptionStyle)

@Serializable
data class SavedBrandTemplate(val id: String, val name: String, val profile: BrandProfile)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reelify_settings")

class SettingsRepository(private val context: Context) {
    private val settingsKey = stringPreferencesKey("app_settings")
    private val brandKey = stringPreferencesKey("brand_profile")
    private val captionPresetsKey = stringPreferencesKey("caption_presets")
    private val brandTemplatesKey = stringPreferencesKey("brand_templates")

    val settings: Flow<AppSettings> = context.dataStore.data.map { AppJson.decodeOrNull<AppSettings>(it[settingsKey]) ?: AppSettings() }
    val brand: Flow<BrandProfile> = context.dataStore.data.map { AppJson.decodeOrNull<BrandProfile>(it[brandKey]) ?: BrandProfile() }
    val captionPresets: Flow<List<SavedCaptionPreset>> = context.dataStore.data.map {
        it[captionPresetsKey]?.let { s -> runCatching { AppJson.decodeFromString(ListSerializer(SavedCaptionPreset.serializer()), s) }.getOrNull() } ?: emptyList()
    }
    val brandTemplates: Flow<List<SavedBrandTemplate>> = context.dataStore.data.map {
        it[brandTemplatesKey]?.let { s -> runCatching { AppJson.decodeFromString(ListSerializer(SavedBrandTemplate.serializer()), s) }.getOrNull() } ?: emptyList()
    }

    suspend fun current(): AppSettings = settings.first()
    suspend fun currentBrand(): BrandProfile = brand.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val cur = AppJson.decodeOrNull<AppSettings>(prefs[settingsKey]) ?: AppSettings()
            prefs[settingsKey] = AppJson.encodeToString(AppSettings.serializer(), transform(cur))
        }
    }

    suspend fun updateBrand(transform: (BrandProfile) -> BrandProfile) {
        context.dataStore.edit { prefs ->
            val cur = AppJson.decodeOrNull<BrandProfile>(prefs[brandKey]) ?: BrandProfile()
            prefs[brandKey] = AppJson.encodeToString(BrandProfile.serializer(), transform(cur))
        }
    }

    suspend fun saveCaptionPreset(p: SavedCaptionPreset) = context.dataStore.edit { prefs ->
        val list = prefs[captionPresetsKey]?.let { runCatching { AppJson.decodeFromString(ListSerializer(SavedCaptionPreset.serializer()), it) }.getOrNull() } ?: emptyList()
        prefs[captionPresetsKey] = AppJson.encodeToString(ListSerializer(SavedCaptionPreset.serializer()), list.filterNot { it.id == p.id } + p)
    }

    suspend fun deleteCaptionPreset(id: String) = context.dataStore.edit { prefs ->
        val list = prefs[captionPresetsKey]?.let { runCatching { AppJson.decodeFromString(ListSerializer(SavedCaptionPreset.serializer()), it) }.getOrNull() } ?: emptyList()
        prefs[captionPresetsKey] = AppJson.encodeToString(ListSerializer(SavedCaptionPreset.serializer()), list.filterNot { it.id == id })
    }

    suspend fun saveBrandTemplate(t: SavedBrandTemplate) = context.dataStore.edit { prefs ->
        val list = prefs[brandTemplatesKey]?.let { runCatching { AppJson.decodeFromString(ListSerializer(SavedBrandTemplate.serializer()), it) }.getOrNull() } ?: emptyList()
        prefs[brandTemplatesKey] = AppJson.encodeToString(ListSerializer(SavedBrandTemplate.serializer()), list.filterNot { it.id == t.id } + t)
    }

    suspend fun deleteBrandTemplate(id: String) = context.dataStore.edit { prefs ->
        val list = prefs[brandTemplatesKey]?.let { runCatching { AppJson.decodeFromString(ListSerializer(SavedBrandTemplate.serializer()), it) }.getOrNull() } ?: emptyList()
        prefs[brandTemplatesKey] = AppJson.encodeToString(ListSerializer(SavedBrandTemplate.serializer()), list.filterNot { it.id == id })
    }
}
