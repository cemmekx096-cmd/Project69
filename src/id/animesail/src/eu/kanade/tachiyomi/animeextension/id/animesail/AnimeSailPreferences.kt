package eu.kanade.tachiyomi.animeextension.id.animesail

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

/**
 * Preferences for AnimeSail extension
 *
 * Provides settings for:
 * - Base URL (custom domain)
 * - Preferred quality
 * - Preferred server
 * - Debug logging
 */
object AnimeSailPreferences {

    // ==================== Preference Keys ====================

    const val PREF_DOMAIN_KEY = "preferred_domain"
    const val PREF_DOMAIN_DEFAULT = "https://154.26.137.28"

    const val PREF_QUALITY_KEY = "preferred_quality"
    const val PREF_QUALITY_DEFAULT = "720p"
    
    const val PREF_SERVER_KEY = "preferred_server"
    const val PREF_SERVER_DEFAULT = "All"
    
    const val PREF_DEBUG_KEY = "enable_debug"
    const val PREF_DEBUG_DEFAULT = true

    // ==================== Setup Preferences ====================

    fun setupPreferenceScreen(screen: PreferenceScreen, preferences: SharedPreferences) {
        // Base URL / Domain
        EditTextPreference(screen.context).apply {
            key = PREF_DOMAIN_KEY
            title = "Base URL"
            summary = "Domain AnimeSail (jika berubah)\nDefault: $PREF_DOMAIN_DEFAULT\nCurrent: %s"
            dialogTitle = "Masukkan Base URL"
            dialogMessage = "Contoh: https://154.26.137.28"
            setDefaultValue(PREF_DOMAIN_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                val url = newValue as String
                url.startsWith("http://") || url.startsWith("https://")
            }
        }.also(screen::addPreference)

        // Preferred Quality
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Kualitas Preferensi"
            summary = "Pilih kualitas video yang diutamakan\nCurrent: %s"
            entries = arrayOf("1080p", "720p", "480p", "360p", "Semua")
            entryValues = arrayOf("1080p", "720p", "480p", "360p", "all")
            setDefaultValue(PREF_QUALITY_DEFAULT)
        }.also(screen::addPreference)

        // Preferred Server
        ListPreference(screen.context).apply {
            key = PREF_SERVER_KEY
            title = "Server Preferensi"
            summary = "Pilih server video yang diutamakan\nCurrent: %s"
            entries = arrayOf("Semua", "Krakenfiles", "Gofile", "Acefile")
            entryValues = arrayOf("All", "Krakenfiles", "Gofile", "Acefile")
            setDefaultValue(PREF_SERVER_DEFAULT)
        }.also(screen::addPreference)

        // Debug Logging
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DEBUG_KEY
            title = "Debug Logging"
            summary = "Aktifkan log detail untuk debugging"
            setDefaultValue(PREF_DEBUG_DEFAULT)
            
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                LogConfig.setDebugMode(enabled)
                true
            }
        }.also(screen::addPreference)
    }

    // ==================== Helper Functions ====================

    fun getBaseUrl(preferences: SharedPreferences): String {
        return preferences.getString(PREF_DOMAIN_KEY, PREF_DOMAIN_DEFAULT) ?: PREF_DOMAIN_DEFAULT
    }
l
    fun getPreferredQuality(preferences: SharedPreferences): String {
        return preferences.getString(PREF_QUALITY_KEY, PREF_QUALITY_DEFAULT) ?: PREF_QUALITY_DEFAULT
    }

    fun getPreferredServer(preferences: SharedPreferences): String {
        return preferences.getString(PREF_SERVER_KEY, PREF_SERVER_DEFAULT) ?: PREF_SERVER_DEFAULT
    }

    fun isDebugEnabled(preferences: SharedPreferences): Boolean {
        return preferences.getBoolean(PREF_DEBUG_KEY, PREF_DEBUG_DEFAULT)
    }
}
