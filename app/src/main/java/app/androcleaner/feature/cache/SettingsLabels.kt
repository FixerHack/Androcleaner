package app.androcleaner.feature.cache

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Texts of the "Storage & cache" entry and the "Clear cache" button in the system Settings app.
 * Read from the Settings app's own resources, so they match the device language and OEM wording;
 * a few common translations are kept as a fallback.
 */
data class SettingsLabels(val settingsPackage: String, val storageEntry: List<String>, val clearCache: List<String>) {

    companion object {
        private val STORAGE_RES = listOf("storage_settings_for_app", "storage_label", "storage_settings")
        private val CLEAR_CACHE_RES = listOf("clear_cache_btn_text")

        private val STORAGE_FALLBACK = listOf(
            "Storage & cache", "Storage and cache", "Storage",
            "Пам'ять і кеш", "Сховище та кеш", "Сховище", "Пам'ять",
            "Хранилище и кеш", "Хранилище и кэш", "Хранилище", "Память",
        )
        private val CLEAR_CACHE_FALLBACK = listOf("Clear cache", "Очистити кеш", "Очистить кеш", "Очистить кэш")

        fun resolve(context: Context): SettingsLabels {
            val pm = context.packageManager
            val settingsPackage = pm.resolveActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                0,
            )?.activityInfo?.packageName ?: "com.android.settings"

            val resources: Resources? = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Use the system locale, not this app's per-app language.
                    pm.getResourcesForApplication(
                        pm.getApplicationInfo(settingsPackage, 0),
                        Configuration(Resources.getSystem().configuration),
                    )
                } else {
                    pm.getResourcesForApplication(settingsPackage)
                }
            }.getOrNull()

            fun lookup(names: List<String>): List<String> = names.mapNotNull { name ->
                resources?.getIdentifier(name, "string", settingsPackage)
                    ?.takeIf { it != 0 }
                    ?.let { runCatching { resources.getString(it) }.getOrNull() }
            }

            return SettingsLabels(
                settingsPackage = settingsPackage,
                storageEntry = (lookup(STORAGE_RES) + STORAGE_FALLBACK).map(::normalize).distinct(),
                clearCache = (lookup(CLEAR_CACHE_RES) + CLEAR_CACHE_FALLBACK).map(::normalize).distinct(),
            )
        }

        /** Lowercase, unify apostrophes and whitespace so OEM typography doesn't break matching. */
        fun normalize(text: CharSequence): String = text.toString()
            .replace('’', '\'').replace('ʼ', '\'').replace('`', '\'')
            .replace(' ', ' ')
            .trim()
            .lowercase()
    }
}
