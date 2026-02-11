package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen

object Lk21Preferences {

    // Preference Keys
    const val PREF_API_URL_KEY = "api_github_url" // Mengganti gateway
    const val PREF_BASE_URL_KEY = "base_url"
    const val PREF_USER_AGENT_KEY = "user_agent"
    const val PREF_QUALITY_KEY = "preferred_quality"

    // Default Values
    // Mengarah langsung ke repo GitHub kamu sesuai diskusi
    const val DEFAULT_API_URL = "https://raw.githubusercontent.com/Usermongkay/Usermongkay/refs/heads/main/lk21movies/extension_lk21movies.json"
    const val DEFAULT_BASE_URL = "https://tv8.lk21official.cc/"
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    fun setupPreferences(
        screen: PreferenceScreen,
        preferences: SharedPreferences,
    ) {
        // API GitHub URL (Pusat Kendali Self-Healing)
        EditTextPreference(screen.context).apply {
            key = PREF_API_URL_KEY
            title = "API Configuration URL"
            setDefaultValue(DEFAULT_API_URL)
            summary = preferences.getString(PREF_API_URL_KEY, DEFAULT_API_URL)
            dialogTitle = "GitHub Raw JSON Link"
            dialogMessage = "Link ke file extension_lk21movies.json di GitHub"

            setOnPreferenceChangeListener { _, newValue ->
                val newUrl = (newValue as String).trim()
                summary = newUrl
                true
            }
        }.also(screen::addPreference)

        // Base URL (Domain Aktif)
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = "Base URL (Domain Aktif)"
            setDefaultValue(DEFAULT_BASE_URL)
            summary = preferences.getString(PREF_BASE_URL_KEY, DEFAULT_BASE_URL)
            dialogTitle = "Manual Base URL Override"
            dialogMessage = "Ganti jika GitHub terlambat update"

            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue as String
                true
            }
        }.also(screen::addPreference)

        // User Agent
        EditTextPreference(screen.context).apply {
            key = PREF_USER_AGENT_KEY
            title = "User Agent"
            setDefaultValue(DEFAULT_USER_AGENT)
            summary = preferences.getString(PREF_USER_AGENT_KEY, DEFAULT_USER_AGENT)?.take(40) + "..."

            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue as String
                true
            }
        }.also(screen::addPreference)

        // Preferred Quality
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Quality"
            entries = arrayOf("1080p", "720p", "480p", "360p", "Auto")
            entryValues = arrayOf("1080", "720", "480", "360", "auto")
            setDefaultValue("720")
            summary = "Prioritas kualitas: %s"
        }.also(screen::addPreference)

        // Link Kontribusi / Developer
        androidx.preference.Preference(screen.context).apply {
            title = "Developer GitHub / Report Bug"
            summary = "https://github.com/Usermongkay/Usermongkay"
            key = "dev_link" // Tambahkan key agar tidak eror
            
            setOnPreferenceClickListener {
                true
            }
        }.also(screen::addPreference)
    }

    // Helper Functions untuk mempermudah pengambilan data di file Main (.kt)
    fun getApiUrl(preferences: SharedPreferences): String =
        preferences.getString(PREF_API_URL_KEY, DEFAULT_API_URL) ?: DEFAULT_API_URL

    fun getBaseUrl(preferences: SharedPreferences): String =
        preferences.getString(PREF_BASE_URL_KEY, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun getUserAgent(preferences: SharedPreferences): String =
        preferences.getString(PREF_USER_AGENT_KEY, DEFAULT_USER_AGENT) ?: DEFAULT_USER_AGENT
}
