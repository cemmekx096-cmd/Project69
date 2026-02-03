package eu.kanade.tachiyomi.animeextension.id.anichin

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat

object AnichinPreferences {

    // ============================= Constants ==============================

    // Network Settings
    const val PREF_BASE_URL_KEY = "base_url"
    const val PREF_BASE_URL_DEFAULT = "https://anichin.watch"

    const val PREF_USER_AGENT_KEY = "user_agent"
    const val PREF_USER_AGENT_DEFAULT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val PREF_CLOUDFLARE_KEY = "cloudflare_enabled"
    const val PREF_CLOUDFLARE_DEFAULT = true

    const val PREF_TIMEOUT_KEY = "network_timeout"
    const val PREF_TIMEOUT_DEFAULT = "90"

    // Video Settings
    const val PREF_QUALITY_KEY = "preferred_quality"
    const val PREF_QUALITY_DEFAULT = "720p"
    val PREF_QUALITY_ENTRIES = arrayOf("1080p", "720p", "480p", "360p")
    val PREF_QUALITY_VALUES = arrayOf("1080p", "720p", "480p", "360p")

    const val PREF_SKIP_ADS_KEY = "skip_ads_servers"
    const val PREF_SKIP_ADS_DEFAULT = true

    // ======================== Setup Preferences ===========================

    fun setupPreferences(screen: PreferenceScreen, preferences: SharedPreferences) {

        // Base URL
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Base URL"
            summary = "%s" // Show current value only
            setDefaultValue(PREF_BASE_URL_DEFAULT)
            dialogTitle = "Base URL"

            setOnPreferenceChangeListener { _, newValue ->
                val newUrl = newValue as String
                if (newUrl.isNotBlank() && newUrl.startsWith("http")) {
                    preferences.edit().putString(key, newUrl.trimEnd('/')).commit()
                } else {
                    false
                }
            }
        }.also(screen::addPreference)

        // User Agent
        EditTextPreference(screen.context).apply {
            key = PREF_USER_AGENT_KEY
            title = "User Agent"
            summary = "%s" // Show current value only
            setDefaultValue(PREF_USER_AGENT_DEFAULT)
            dialogTitle = "User Agent"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.also(screen::addPreference)

        // CloudFlare Bypass
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_CLOUDFLARE_KEY
            title = "CloudFlare Bypass"
            summary = null // No summary - toggle only
            setDefaultValue(PREF_CLOUDFLARE_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
        }.also(screen::addPreference)

        // Network Timeout
        EditTextPreference(screen.context).apply {
            key = PREF_TIMEOUT_KEY
            title = "Network Timeout (seconds)"
            summary = "%s" // Show current value only
            setDefaultValue(PREF_TIMEOUT_DEFAULT)
            dialogTitle = "Network Timeout"

            setOnPreferenceChangeListener { _, newValue ->
                val timeout = (newValue as String).toLongOrNull()
                if (timeout != null && timeout > 0) {
                    preferences.edit().putString(key, newValue).commit()
                } else {
                    false
                }
            }
        }.also(screen::addPreference)

        // Preferred Quality
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Quality"
            entries = PREF_QUALITY_ENTRIES
            entryValues = PREF_QUALITY_VALUES
            setDefaultValue(PREF_QUALITY_DEFAULT)
            summary = "%s" // Show current value only

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(key, entry).commit()
            }
        }.also(screen::addPreference)

        // Skip [ADS] Servers
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SKIP_ADS_KEY
            title = "Skip [ADS] Servers"
            summary = null // No summary - toggle only
            setDefaultValue(PREF_SKIP_ADS_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(key, newValue as Boolean).commit()
            }
        }.also(screen::addPreference)
    }
}
