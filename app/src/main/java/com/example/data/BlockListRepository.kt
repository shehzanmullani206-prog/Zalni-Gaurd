package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "zalni_guard_settings")

/**
 * Repository managing the blocked keyword list, master protection state,
 * and OEM setup guide completion statuses via Jetpack DataStore Preferences.
 */
class BlockListRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    companion object {
        private val KEY_BLOCKED_WORDS = stringSetPreferencesKey("blocked_words")
        private val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        private val KEY_HAS_SEEN_SETUP_GUIDE = booleanPreferencesKey("has_seen_setup_guide")
        private val KEY_SETUP_GUIDE_STEPS = stringSetPreferencesKey("setup_guide_completed_steps")
    }

    val blockedWordsFlow: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_BLOCKED_WORDS] ?: emptySet()
        }

    val isProtectionEnabledFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_PROTECTION_ENABLED] ?: false
        }

    val hasSeenSetupGuideFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_HAS_SEEN_SETUP_GUIDE] ?: false
        }

    val setupGuideStepsFlow: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_SETUP_GUIDE_STEPS] ?: emptySet()
        }

    suspend fun addWord(word: String) {
        dataStore.edit { preferences ->
            val currentWords = preferences[KEY_BLOCKED_WORDS]?.toMutableSet() ?: mutableSetOf()
            currentWords.add(word)
            preferences[KEY_BLOCKED_WORDS] = currentWords
        }
    }

    suspend fun removeWord(word: String) {
        dataStore.edit { preferences ->
            val currentWords = preferences[KEY_BLOCKED_WORDS]?.toMutableSet() ?: mutableSetOf()
            currentWords.remove(word)
            preferences[KEY_BLOCKED_WORDS] = currentWords
        }
    }

    suspend fun setProtectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_PROTECTION_ENABLED] = enabled
        }
    }

    suspend fun setHasSeenSetupGuide(seen: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_HAS_SEEN_SETUP_GUIDE] = seen
        }
    }

    suspend fun toggleSetupGuideStep(stepId: String, isCompleted: Boolean) {
        dataStore.edit { preferences ->
            val currentSteps = preferences[KEY_SETUP_GUIDE_STEPS]?.toMutableSet() ?: mutableSetOf()
            if (isCompleted) {
                currentSteps.add(stepId)
            } else {
                currentSteps.remove(stepId)
            }
            preferences[KEY_SETUP_GUIDE_STEPS] = currentSteps
        }
    }
}
