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
 * Repository managing the blocked keyword list and the master protection state
 * via Jetpack DataStore Preferences. Thread-safe for Phase 3 background service access.
 */
class BlockListRepository(context: Context) {

    private val dataStore = context.applicationContext.dataStore

    companion object {
        private val KEY_BLOCKED_WORDS = stringSetPreferencesKey("blocked_words")
        private val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
    }

    /**
     * Flow emitting the set of active blocked words.
     */
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

    /**
     * Flow emitting the master protection on/off toggle state (defaults to false until user turns ON).
     */
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

    /**
     * Adds a normalized word to the blocked list.
     */
    suspend fun addWord(word: String) {
        dataStore.edit { preferences ->
            val currentWords = preferences[KEY_BLOCKED_WORDS]?.toMutableSet() ?: mutableSetOf()
            currentWords.add(word)
            preferences[KEY_BLOCKED_WORDS] = currentWords
        }
    }

    /**
     * Removes a word from the blocked list.
     */
    suspend fun removeWord(word: String) {
        dataStore.edit { preferences ->
            val currentWords = preferences[KEY_BLOCKED_WORDS]?.toMutableSet() ?: mutableSetOf()
            currentWords.remove(word)
            preferences[KEY_BLOCKED_WORDS] = currentWords
        }
    }

    /**
     * Updates the protection enabled state.
     */
    suspend fun setProtectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[KEY_PROTECTION_ENABLED] = enabled
        }
    }
}
