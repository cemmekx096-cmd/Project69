package eu.kanade.tachiyomi.animeextension.id.lk21movies

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen

object Lk21Preferences {

    // Preference Keys
    const val PREF_BASE_URL_KEY = "base_url"
    const val PREF_USER_AGENT_KEY = "user_agent"
    const val PREF_QUALITY_KEY = "preferred_quality"
    const val PREF_PLAYER_KEY = "preferred_player"

    // Default Values
    const val DEFAULT_BASE_URL_MOVIES = "https://tv7.lk21official.cc"
    const val DEFAULT_BASE_URL_DRAMA = "https://tv3.nontondrama.my"
    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    /**
     * Setup preference screen dengan opsi lengkap
     */
    fun setupPreferences(
        screen: PreferenceScreen,
        preferences: SharedPreferences,
        defaultBaseUrl: String,
        isMovieExtension: Boolean = true,
    ) {
        // Base URL Preference
        EditTextPreference(screen.context).apply {
            key = PREF_BASE_URL_KEY
            title = if (isMovieExtension) "Base URL (Movies)" else "Base URL (Drama)"
            setDefaultValue(defaultBaseUrl)
            summary = preferences.getString(PREF_BASE_URL_KEY, defaultBaseUrl)
            dialogTitle = "Enter base URL"
            dialogMessage = "Ganti jika domain LK21 berubah"

            setOnPreferenceChangeListener { _, newValue ->
                summary = newValue as String
                true
            }
        }.also(screen::addPreference)

        // User Agent Preference
        EditTextPreference(screen.context).apply {
            key = PREF_USER_AGENT_KEY
            title = "User Agent"
            setDefaultValue(DEFAULT_USER_AGENT)
            summary = "Custom user agent (opsional)"
            dialogTitle = "User Agent"

            setOnPreferenceChangeListener { _, newValue ->
                summary = if ((newValue as String).isEmpty()) {
                    "Using default user agent"
                } else {
                    "Custom: ${newValue.take(50)}..."
                }
                true
            }
        }.also(screen::addPreference)

        // Quality Preference
        ListPreference(screen.context).apply {
            key = PREF_QUALITY_KEY
            title = "Preferred Quality"
            entries = arrayOf("720p (Recommended)", "1080p", "480p", "360p", "Auto")
            entryValues = arrayOf("720", "1080", "480", "360", "auto")
            setDefaultValue("720")
            summary = "Kualitas video yang diprioritaskan: %s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = entries[entryValues.indexOf(newValue)]
                summary = "Kualitas video yang diprioritaskan: $selected"
                true
            }
        }.also(screen::addPreference)

        // Player Preference
        ListPreference(screen.context).apply {
            key = PREF_PLAYER_KEY
            title = "Preferred Player"
            entries = arrayOf("All Players", "Skip Iframe", "Direct Links Only")
            entryValues = arrayOf("all", "no_iframe", "direct_only")
            setDefaultValue("all")
            summary = "Filter tipe player: %s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = entries[entryValues.indexOf(newValue)]
                summary = "Filter tipe player: $selected"
                true
            }
        }.also(screen::addPreference)
    }

    /**
     * Get base URL dari preferences
     */
    fun getBaseUrl(preferences: SharedPreferences, defaultUrl: String): String {
        return preferences.getString(PREF_BASE_URL_KEY, defaultUrl) ?: defaultUrl
    }

    /**
     * Get user agent dari preferences
     */
    fun getUserAgent(preferences: SharedPreferences): String {
        val custom = preferences.getString(PREF_USER_AGENT_KEY, "")
        return if (custom.isNullOrEmpty()) DEFAULT_USER_AGENT else custom
    }

    /**
     * Get preferred quality dari preferences
     */
    fun getPreferredQuality(preferences: SharedPreferences): String {
        return preferences.getString(PREF_QUALITY_KEY, "720") ?: "720"
    }

    /**
     * Get player filter dari preferences
     */
    fun getPlayerFilter(preferences: SharedPreferences): String {
        return preferences.getString(PREF_PLAYER_KEY, "all") ?: "all"
    }
}
