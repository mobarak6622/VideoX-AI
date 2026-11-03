package com.example.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.common.AppLanguage
import com.example.core.common.PreviewQuality
import com.example.core.common.ProcessingProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "videox_user_prefs")

data class UserSettingsState(
    val isDarkTheme: Boolean = true,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val onboardingCompleted: Boolean = true,
    val processingProfile: ProcessingProfile = ProcessingProfile.BALANCED,
    val defaultPreviewQuality: PreviewQuality = PreviewQuality.BALANCED
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val LANGUAGE = stringPreferencesKey("app_language")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val PROCESSING_PROFILE = stringPreferencesKey("processing_profile")
        val PREVIEW_QUALITY = stringPreferencesKey("preview_quality")
    }

    val settingsFlow: Flow<UserSettingsState> = context.dataStore.data.map { prefs ->
        val dark = prefs[Keys.DARK_THEME] ?: true
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.ENGLISH.code
        val lang = AppLanguage.entries.firstOrNull { it.code == langCode } ?: AppLanguage.ENGLISH
        val onboardingDone = prefs[Keys.ONBOARDING_DONE] ?: true
        val profileName = prefs[Keys.PROCESSING_PROFILE] ?: ProcessingProfile.BALANCED.name
        val profile = ProcessingProfile.entries.firstOrNull { it.name == profileName } ?: ProcessingProfile.BALANCED
        val previewName = prefs[Keys.PREVIEW_QUALITY] ?: PreviewQuality.BALANCED.name
        val preview = PreviewQuality.entries.firstOrNull { it.name == previewName } ?: PreviewQuality.BALANCED

        UserSettingsState(
            isDarkTheme = dark,
            language = lang,
            onboardingCompleted = onboardingDone,
            processingProfile = profile,
            defaultPreviewQuality = preview
        )
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = isDark }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    suspend fun setProcessingProfile(profile: ProcessingProfile) {
        context.dataStore.edit { it[Keys.PROCESSING_PROFILE] = profile.name }
    }

    suspend fun setPreviewQuality(quality: PreviewQuality) {
        context.dataStore.edit { it[Keys.PREVIEW_QUALITY] = quality.name }
    }
}
