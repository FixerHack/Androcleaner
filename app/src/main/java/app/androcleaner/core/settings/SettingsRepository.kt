package app.androcleaner.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val hasVirusTotalKey: Boolean = false,
    val hasMalwareBazaarKey: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private val themeKey = stringPreferencesKey("theme")
    private val vtKey = stringPreferencesKey("virustotal_key")
    private val mbKey = stringPreferencesKey("malwarebazaar_key")

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            theme = prefs[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            hasVirusTotalKey = prefs[vtKey] != null,
            hasMalwareBazaarKey = prefs[mbKey] != null,
        )
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[themeKey] = mode.name }
    }

    suspend fun setVirusTotalKey(key: String?) = setSecret(vtKey, key)

    suspend fun setMalwareBazaarKey(key: String?) = setSecret(mbKey, key)

    suspend fun virusTotalKey(): String? = secret(vtKey)

    suspend fun malwareBazaarKey(): String? = secret(mbKey)

    private suspend fun setSecret(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String?) {
        context.dataStore.edit { prefs ->
            val trimmed = value?.trim()
            if (trimmed.isNullOrEmpty()) prefs.remove(key) else prefs[key] = SecretBox.encrypt(trimmed)
        }
    }

    private suspend fun secret(key: androidx.datastore.preferences.core.Preferences.Key<String>): String? =
        context.dataStore.data.first()[key]?.let(SecretBox::decrypt)
}
